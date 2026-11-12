package com.sep.psp.merchantapi;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.common.config.PaymentProperties;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.merchant.Merchant;
import com.sep.psp.merchantapi.MerchantApiDtos.InitTransactionRequest;
import com.sep.psp.merchantapi.MerchantApiDtos.TransactionResponse;
import com.sep.psp.paymentmethod.MerchantPaymentMethodRepository;
import com.sep.psp.transaction.PspTransaction;
import com.sep.psp.transaction.PspTransactionRepository;
import com.sep.psp.transaction.TransactionStatus;
import com.sep.psp.transaction.TransactionStatusService;

/**
 * Korak 1 iz specifikacije: klijent (parking sistem) trazi inicijalizaciju transakcije, PSP kreira
 * internu transakciju u statusu INIT i vraca adresu checkout stranice na koju klijent preusmerava kupca.
 *
 * <p>Isti MERCHANT_ORDER_ID se ne moze iskoristiti dvaput (unique u bazi): ponovljen zahtev sa istim
 * iznosom i valutom vraca postojecu transakciju (dupli klik, ponovljen zahtev posle timeout-a), a sa
 * drugacijim iznosom ili valutom se odbija.
 */
@Service
public class TransactionInitService {

    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final PspTransactionRepository repository;
    private final MerchantPaymentMethodRepository merchantMethodRepository;
    private final TransactionStatusService statusService;
    private final AuditService auditService;
    private final PaymentProperties properties;
    private final TransactionTemplate tx;

    public TransactionInitService(PspTransactionRepository repository,
                                  MerchantPaymentMethodRepository merchantMethodRepository,
                                  TransactionStatusService statusService,
                                  AuditService auditService,
                                  PaymentProperties properties,
                                  PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.merchantMethodRepository = merchantMethodRepository;
        this.statusService = statusService;
        this.auditService = auditService;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public InitResult initiate(Merchant merchant, InitTransactionRequest request) {
        validate(request);
        if (merchantMethodRepository.countUsable(merchant.getId()) == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_PAYMENT_METHODS",
                    "Klijent nema nijednu aktivnu metodu placanja");
        }

        Optional<PspTransaction> existing = repository.findByMerchantIdAndMerchantOrderId(
                merchant.getId(), request.merchantOrderId());
        if (existing.isPresent()) {
            return new InitResult(sameOrder(existing.get(), request), false);
        }

        try {
            PspTransaction created = tx.execute(status -> repository.saveAndFlush(newTransaction(merchant, request)));
            auditService.record("MERCHANT_API", merchant.getMerchantCode(), AuditAction.TX_INIT, "TRANSACTION",
                    created.getId().toString(), AuditOutcome.SUCCESS,
                    "order " + created.getMerchantOrderId() + ", " + created.getAmount().toPlainString() + " "
                            + created.getCurrency());
            return new InitResult(response(created), true);
        } catch (DataIntegrityViolationException race) {
            // paralelan zahtev sa istim MERCHANT_ORDER_ID je stigao prvi
            PspTransaction winner = repository.findByMerchantIdAndMerchantOrderId(merchant.getId(),
                    request.merchantOrderId()).orElseThrow(() -> race);
            return new InitResult(sameOrder(winner, request), false);
        }
    }

    /** Provera statusa od strane klijenta (pull) - ne zavisi od redirect-a ni od webhook-a. */
    public TransactionResponse status(Merchant merchant, String merchantOrderId) {
        PspTransaction transaction = repository.findByMerchantIdAndMerchantOrderId(merchant.getId(), merchantOrderId)
                .orElseThrow(() -> ApiException.notFound("Transakcija ne postoji"));
        if (transaction.getStatus() == TransactionStatus.INIT && Instant.now().isAfter(transaction.getExpiresAt())) {
            java.util.UUID id = transaction.getId();
            transaction = tx.execute(status -> {
                PspTransaction locked = repository.findByIdForUpdate(id).orElseThrow();
                statusService.expireIfDue(locked);
                return locked;
            });
        }
        return response(transaction);
    }

    private TransactionResponse sameOrder(PspTransaction existing, InitTransactionRequest request) {
        boolean same = existing.getAmount().compareTo(request.amount()) == 0
                && existing.getCurrency().equals(request.currency());
        if (!same) {
            throw ApiException.conflict("ORDER_ID_CONFLICT",
                    "merchant_order_id je vec iskoriscen za drugi iznos ili valutu");
        }
        return response(existing);
    }

    private TransactionResponse response(PspTransaction t) {
        boolean open = t.getStatus() == TransactionStatus.INIT || t.getStatus() == TransactionStatus.PENDING;
        return TransactionResponse.from(t, open ? checkoutUrl(t) : null);
    }

    private String checkoutUrl(PspTransaction t) {
        String base = properties.checkoutBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/placanje/" + t.getId();
    }

    private PspTransaction newTransaction(Merchant merchant, InitTransactionRequest request) {
        Instant now = Instant.now();
        PspTransaction t = new PspTransaction();
        t.setMerchantId(merchant.getId());
        t.setMerchantOrderId(request.merchantOrderId());
        t.setMerchantTimestamp(request.merchantTimestamp());
        // iznos u skali valute (120 -> 120.00), isto kako ga cuva baza i kako se vraca u svakom odgovoru
        t.setAmount(request.amount().setScale(
                Math.max(Currency.getInstance(request.currency()).getDefaultFractionDigits(), 0)));
        t.setCurrency(request.currency());
        t.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().strip());
        t.setStatus(TransactionStatus.INIT);
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(properties.checkoutTtlMinutes(), ChronoUnit.MINUTES));
        return t;
    }

    private void validate(InitTransactionRequest request) {
        try {
            Currency currency = Currency.getInstance(request.currency());
            if (request.amount().stripTrailingZeros().scale() > Math.max(currency.getDefaultFractionDigits(), 0)) {
                throw ApiException.badRequest("INVALID_AMOUNT",
                        "Valuta " + request.currency() + " ne dozvoljava toliko decimala");
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CURRENCY", "Nepoznata valuta: " + request.currency());
        }
        if (request.merchantTimestamp().isAfter(Instant.now().plus(MAX_FUTURE_SKEW))) {
            throw ApiException.badRequest("INVALID_TIMESTAMP", "merchant_timestamp je u buducnosti");
        }
    }

    public record InitResult(TransactionResponse transaction, boolean created) {
    }
}
