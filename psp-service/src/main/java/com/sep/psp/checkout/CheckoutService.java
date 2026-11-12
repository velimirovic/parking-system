package com.sep.psp.checkout;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.checkout.CheckoutDtos.CheckoutView;
import com.sep.psp.checkout.CheckoutDtos.MethodOption;
import com.sep.psp.common.config.PaymentProperties;
import com.sep.psp.common.crypto.AesGcmEncryptor;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.merchant.Merchant;
import com.sep.psp.merchant.MerchantRepository;
import com.sep.psp.paymentmethod.MerchantPaymentMethod;
import com.sep.psp.paymentmethod.MerchantPaymentMethodRepository;
import com.sep.psp.plugin.PaymentPluginClient;
import com.sep.psp.plugin.PaymentPluginClient.PluginUnavailableException;
import com.sep.psp.plugin.PluginDtos.InitiateRequest;
import com.sep.psp.plugin.PluginDtos.InitiateResponse;
import com.sep.psp.transaction.PspTransaction;
import com.sep.psp.transaction.PspTransactionRepository;
import com.sep.psp.transaction.TransactionStatus;
import com.sep.psp.transaction.TransactionStatusService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Checkout: kupac bira jednu od metoda na koje je klijent pretplacen, PSP poziva odgovarajuci plugin
 * i preusmerava kupca dalje (npr. na stranicu banke).
 *
 * <p>Izbor metode se radi pod lock-om na redu transakcije: dupli klik ili refresh ne otvaraju drugo
 * placanje kod banke, nego vracaju isti URL (specijalni slucaj 4).
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final TypeReference<Map<String, String>> CONFIG_TYPE = new TypeReference<>() {
    };

    private final PspTransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantPaymentMethodRepository merchantMethodRepository;
    private final TransactionStatusService statusService;
    private final PaymentPluginClient pluginClient;
    private final AesGcmEncryptor encryptor;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final PaymentProperties properties;

    @Transactional
    public CheckoutView view(UUID id) {
        PspTransaction transaction = load(id);
        if (transaction.getStatus() == TransactionStatus.INIT && Instant.now().isAfter(transaction.getExpiresAt())) {
            transaction = locked(id);
            statusService.expireIfDue(transaction);
        }
        return toView(transaction);
    }

    @Transactional
    public String selectMethod(UUID id, String code) {
        PspTransaction transaction = locked(id);
        statusService.expireIfDue(transaction);

        if (transaction.getStatus() == TransactionStatus.PENDING) {
            boolean sameMethod = transaction.getPaymentMethod() != null
                    && transaction.getPaymentMethod().getCode().equals(code);
            if (sameMethod && transaction.getPaymentRedirectUrl() != null) {
                return transaction.getPaymentRedirectUrl();
            }
            throw ApiException.conflict("METHOD_ALREADY_SELECTED",
                    "Plaćanje je već započeto drugim načinom plaćanja");
        }
        if (transaction.getStatus() != TransactionStatus.INIT) {
            throw ApiException.conflict("TRANSACTION_CLOSED", "Transakcija je završena");
        }

        MerchantPaymentMethod subscription = usableMethods(transaction.getMerchantId()).stream()
                .filter(m -> m.getPaymentMethod().getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("METHOD_NOT_AVAILABLE",
                        "Prodavac ne podržava izabrani način plaćanja"));

        Merchant merchant = merchantRepository.findById(transaction.getMerchantId()).orElseThrow();
        InitiateResponse response;
        try {
            // Lock na transakciji se drzi tokom poziva plugin-a (kratak timeout) - paralelni izbor metode ceka.
            response = pluginClient.initiate(subscription.getPaymentMethod(), new InitiateRequest(
                    transaction.getId(),
                    merchant.getMerchantCode(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    config(subscription),
                    resultUrl(transaction),
                    transaction.getExpiresAt()));
        } catch (PluginUnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "METHOD_UNAVAILABLE",
                    "Izabrani način plaćanja trenutno nije dostupan. Pokušajte ponovo ili izaberite drugi.");
        }
        if (!response.pending()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "METHOD_REJECTED",
                    "Plaćanje ovim načinom trenutno nije moguće. Izaberite drugi način plaćanja.");
        }

        transaction.setPaymentMethod(subscription.getPaymentMethod());
        transaction.setPaymentRedirectUrl(response.redirectUrl());
        transaction.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(transaction);
        auditService.record(AuditService.ACTOR_SYSTEM, "psp-core", AuditAction.TX_STATUS_CHANGE, "TRANSACTION",
                transaction.getId().toString(), AuditOutcome.SUCCESS, "INIT -> PENDING (" + code + ")");
        return response.redirectUrl();
    }

    /** Kupac odustaje pre izbora metode - transakcija se zatvara kao FAILED i kupac se vraca klijentu. */
    @Transactional
    public CheckoutView cancel(UUID id) {
        PspTransaction transaction = locked(id);
        if (!statusService.expireIfDue(transaction) && transaction.getStatus() == TransactionStatus.INIT) {
            statusService.complete(transaction, TransactionStatus.FAILED, "kupac je odustao");
        }
        return toView(transaction);
    }

    private CheckoutView toView(PspTransaction t) {
        Merchant merchant = merchantRepository.findById(t.getMerchantId()).orElseThrow();
        boolean open = t.getStatus() == TransactionStatus.INIT;
        List<MethodOption> methods = open
                ? usableMethods(t.getMerchantId()).stream()
                        .map(m -> new MethodOption(m.getPaymentMethod().getCode(), m.getPaymentMethod().getDisplayName()))
                        .toList()
                : List.of();
        return new CheckoutView(
                t.getId().toString(),
                reference(t.getId()),
                merchant.getName(),
                host(merchant.getSuccessUrl()),
                t.getMerchantOrderId(),
                t.getAmount(),
                t.getCurrency(),
                t.getDescription(),
                t.getStatus().name(),
                t.getPaymentMethod() == null ? null : t.getPaymentMethod().getCode(),
                t.getExpiresAt(),
                Instant.now(),
                methods,
                t.getStatus() == TransactionStatus.PENDING ? t.getPaymentRedirectUrl() : null,
                TransactionStatusService.FINAL.contains(t.getStatus()) ? returnUrl(merchant, t) : null);
    }

    /** Metode koje kupac zaista moze da koristi: pretplata aktivna i plugin globalno aktivan. */
    private List<MerchantPaymentMethod> usableMethods(UUID merchantId) {
        return merchantMethodRepository.findByMerchantId(merchantId).stream()
                .filter(m -> m.isActive() && m.getPaymentMethod().isActive())
                .sorted(Comparator.comparing((MerchantPaymentMethod m) -> m.getPaymentMethod().getCreatedAt())
                        .thenComparing(m -> m.getPaymentMethod().getCode()))
                .toList();
    }

    private Map<String, String> config(MerchantPaymentMethod subscription) {
        if (subscription.getConfigEncrypted() == null) {
            return Map.of();
        }
        return objectMapper.readValue(encryptor.decrypt(subscription.getConfigEncrypted()), CONFIG_TYPE);
    }

    private String resultUrl(PspTransaction t) {
        String base = properties.checkoutBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/placanje/" + t.getId() + "/rezultat";
    }

    /** SUCCESS_URL / FAILED_URL / ERROR_URL klijenta, sa brojem naloga da bi klijent znao koji nalog prikazuje. */
    private static String returnUrl(Merchant merchant, PspTransaction t) {
        String url = switch (t.getStatus()) {
            case SUCCESS -> merchant.getSuccessUrl();
            case ERROR -> merchant.getErrorUrl();
            default -> merchant.getFailedUrl();
        };
        if (url == null || url.isBlank()) {
            return null;
        }
        return UriComponentsBuilder.fromUriString(url)
                .queryParam("merchant_order_id", t.getMerchantOrderId())
                .queryParam("status", t.getStatus().name())
                .build()
                .encode()
                .toUriString();
    }

    /** Kratka oznaka za prikaz kupcu, npr. PSP-4F21-A9C0. */
    private static String reference(UUID id) {
        String hex = id.toString().replace("-", "").toUpperCase();
        return "PSP-" + hex.substring(0, 4) + "-" + hex.substring(4, 8);
    }

    private static String host(String url) {
        if (url == null) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PspTransaction load(UUID id) {
        return transactionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plaćanje ne postoji"));
    }

    private PspTransaction locked(UUID id) {
        return transactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Plaćanje ne postoji"));
    }
}
