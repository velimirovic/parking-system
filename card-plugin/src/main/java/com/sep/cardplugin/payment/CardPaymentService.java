package com.sep.cardplugin.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sep.cardplugin.common.PluginProperties;
import com.sep.cardplugin.payment.Dtos.BankCreateRequest;
import com.sep.cardplugin.payment.Dtos.BankCreateResponse;
import com.sep.cardplugin.payment.Dtos.BankStatus;
import com.sep.cardplugin.payment.Dtos.InitiateRequest;
import com.sep.cardplugin.payment.Dtos.InitiateResponse;
import com.sep.cardplugin.payment.Dtos.PaymentResult;
import com.sep.cardplugin.payment.ExternalClients.Response;

import lombok.extern.slf4j.Slf4j;

/**
 * Tok kartičnog plaćanja na strani PSP-a (dizajn 5.1 i 5.9):
 * <ol>
 *   <li>Core trazi placanje: plugin generise STAN i PSP_TIMESTAMP, upisuje ih PRE poziva banke i trazi
 *       PAYMENT_URL. Ako odgovor banke izostane, ponovljen poziv salje isti STAN + PSP_TIMESTAMP, pa banka
 *       vraca isti zahtev umesto da otvori novi.</li>
 *   <li>Banka javlja rezultat (STAN, GLOBAL_TRANSACTION_ID, ACQUIRER_TIMESTAMP) - plugin ga cuva i
 *       prosledjuje Core-u.</li>
 *   <li>Rezervni mehanizmi: ako obavestenje banke ne stigne, plugin sam proverava status kod banke;
 *       ako Core nije dostupan, rezultat se prosledjuje ponovo dok ga Core ne potvrdi.</li>
 * </ol>
 */
@Slf4j
@Service
public class CardPaymentService {

    private static final String BANK_NOTIFY_PATH = "/bank/notifications";

    private final CardPaymentRepository repository;
    private final ExternalClients clients;
    private final PluginProperties properties;
    private final TransactionTemplate tx;

    public CardPaymentService(CardPaymentRepository repository, ExternalClients clients, PluginProperties properties,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clients = clients;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- Core -> plugin

    public InitiateResponse initiate(InitiateRequest request) {
        String bankMerchantId = request.merchantConfig() == null ? null : request.merchantConfig().get("bankMerchantId");
        if (bankMerchantId == null || bankMerchantId.isBlank()) {
            return InitiateResponse.failed("Klijent nema podesen bankMerchantId za placanje karticom");
        }
        CardPayment payment = tx.execute(status -> repository.findByTransactionIdForUpdate(request.pspTransactionId())
                .orElseGet(() -> repository.saveAndFlush(newPayment(request, bankMerchantId))));
        if (payment.getBankPaymentUrl() != null) {
            return payment.isFinal() ? InitiateResponse.failed("Placanje je vec zavrseno")
                    : InitiateResponse.pending(payment.getBankPaymentUrl());
        }

        Response<BankCreateResponse> response;
        try {
            response = clients.createBankPayment(new BankCreateRequest(payment.getBankMerchantId(), payment.getAmount(),
                    payment.getCurrency(), payment.getStan(), payment.getPspTimestamp(), payment.getReturnUrl(),
                    PluginProperties.trimSlash(properties.publicBaseUrl()) + BANK_NOTIFY_PATH));
        } catch (Exception e) {
            log.warn("Banka nije dostupna za transakciju {}: {}", request.pspTransactionId(), e.toString());
            throw new BankUnavailableException();
        }
        if (response.status() >= 500) {
            log.warn("Banka je vratila HTTP {} za transakciju {}", response.status(), request.pspTransactionId());
            throw new BankUnavailableException();
        }
        if (response.body() == null) {
            log.warn("Banka je odbila zahtev za transakciju {}: HTTP {} {}", request.pspTransactionId(),
                    response.status(), response.raw());
            markError(payment.getTransactionId());
            return InitiateResponse.failed("Banka je odbila zahtev za placanje");
        }
        BankCreateResponse created = response.body();
        tx.executeWithoutResult(status -> repository.findByTransactionIdForUpdate(payment.getTransactionId())
                .ifPresent(p -> {
                    p.setBankPaymentId(created.paymentId());
                    p.setBankPaymentUrl(created.paymentUrl());
                    p.setStatus("PENDING");
                    // ako banka ne javi rezultat, plugin proverava status malo posle isteka forme
                    p.setNextStatusCheckAt(created.expiresAt().plusSeconds(30));
                }));
        log.info("Transakcija {}: banka je otvorila placanje {} (STAN {})", payment.getTransactionId(),
                created.paymentId(), payment.getStan());
        return InitiateResponse.pending(created.paymentUrl());
    }

    // ---------------------------------------------------------------- banka -> plugin

    /** Obavestenje banke o rezultatu (korak 6). Idempotentno - ponovljeno obavestenje ne menja nista. */
    public void onBankStatus(BankStatus status) {
        UUID transactionId = tx.execute(s -> repository.findByBankPaymentIdForUpdate(status.paymentId())
                .map(p -> apply(p, status) ? p.getTransactionId() : null)
                .orElseThrow(() -> new UnknownPaymentException(status.paymentId())));
        if (transactionId != null) {
            forward(transactionId);
        }
    }

    /** @return true ako je status promenjen u konacan */
    private boolean apply(CardPayment p, BankStatus status) {
        if (p.isFinal() || "CREATED".equals(status.status())) {
            return false;
        }
        boolean matches = p.getStan().equals(status.stan())
                && p.getPspTimestamp().truncatedTo(ChronoUnit.MICROS).equals(status.pspTimestamp().truncatedTo(ChronoUnit.MICROS))
                && status.amount() != null && p.getAmount().compareTo(new BigDecimal(status.amount())) == 0
                && p.getCurrency().equals(status.currency());
        if (!matches) {
            // iznos, valuta ili STAN se ne poklapaju sa onim sto je PSP poslao - ne sme se tretirati kao uspeh
            log.error("Rezultat banke za {} se ne poklapa sa zahtevom (STAN/iznos/valuta)", status.paymentId());
            p.setStatus("ERROR");
        } else {
            p.setStatus(switch (status.status()) {
                case "APPROVED" -> "APPROVED";
                case "EXPIRED" -> "EXPIRED";
                default -> "DECLINED";
            });
        }
        p.setResponseCode(status.responseCode());
        p.setGlobalTransactionId(status.globalTransactionId());
        p.setAcquirerTimestamp(status.acquirerTimestamp());
        p.setNextCoreAttemptAt(Instant.now());
        p.setNextStatusCheckAt(null);
        return true;
    }

    // ---------------------------------------------------------------- plugin -> Core

    private void forward(UUID transactionId) {
        CardPayment p = repository.findByTransactionId(transactionId).orElse(null);
        if (p == null || !p.isFinal() || p.getCoreNotifiedAt() != null) {
            return;
        }
        String coreStatus = switch (p.getStatus()) {
            case "APPROVED" -> "SUCCESS";
            case "EXPIRED" -> "EXPIRED";
            case "ERROR" -> "ERROR";
            default -> "FAILED";
        };
        boolean ok = false;
        try {
            int code = clients.sendResult(new PaymentResult(p.getTransactionId(), coreStatus, p.getAmount(),
                    p.getCurrency(), p.getStan(), p.getPspTimestamp(), p.getGlobalTransactionId(),
                    p.getAcquirerTimestamp(), p.getResponseCode()));
            ok = code / 100 == 2;
            if (!ok) {
                log.warn("PSP Core je odbio rezultat za {} (HTTP {})", transactionId, code);
            }
        } catch (Exception e) {
            log.warn("PSP Core nije dostupan za rezultat {}: {}", transactionId, e.toString());
        }
        boolean delivered = ok;
        tx.executeWithoutResult(s -> repository.findByTransactionIdForUpdate(transactionId).ifPresent(locked -> {
            locked.setCoreAttempts(locked.getCoreAttempts() + 1);
            if (delivered) {
                locked.setCoreNotifiedAt(Instant.now());
                locked.setNextCoreAttemptAt(null);
            } else {
                locked.setNextCoreAttemptAt(Instant.now().plusSeconds(Math.min(600, 5L << Math.min(locked.getCoreAttempts(), 7))));
            }
        }));
    }

    // ---------------------------------------------------------------- rezervni mehanizmi

    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    public void retryForwarding() {
        repository.findUnforwarded(Instant.now(), PageRequest.of(0, 50)).forEach(this::forward);
    }

    /** Status placanja za koje banka nije poslala obavestenje (PSP ne zavisi samo od callback-a banke). */
    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void pollBankStatus() {
        List<UUID> due = repository.findDueForStatusCheck(Instant.now(), PageRequest.of(0, 50));
        for (UUID transactionId : due) {
            Optional<CardPayment> found = repository.findByTransactionId(transactionId);
            if (found.isEmpty()) {
                continue;
            }
            CardPayment p = found.get();
            try {
                Response<BankStatus> response = clients.bankStatus(p.getBankMerchantId(), p.getStan(), p.getPspTimestamp());
                if (response.body() != null && !"CREATED".equals(response.body().status())) {
                    UUID changed = tx.execute(s -> repository.findByTransactionIdForUpdate(transactionId)
                            .map(locked -> apply(locked, response.body()) ? locked.getTransactionId() : null).orElse(null));
                    if (changed != null) {
                        log.info("Status transakcije {} preuzet proverom kod banke: {}", transactionId,
                                response.body().status());
                        forward(changed);
                    }
                    continue;
                }
            } catch (Exception e) {
                log.warn("Provera statusa kod banke za {} nije uspela: {}", transactionId, e.toString());
            }
            tx.executeWithoutResult(s -> repository.findByTransactionIdForUpdate(transactionId)
                    .filter(locked -> "PENDING".equals(locked.getStatus()))
                    .ifPresent(locked -> locked.setNextStatusCheckAt(Instant.now().plusSeconds(properties.statusPollAfterSeconds()))));
        }
    }

    // ----------------------------------------------------------------

    private CardPayment newPayment(InitiateRequest request, String bankMerchantId) {
        CardPayment p = new CardPayment();
        p.setTransactionId(request.pspTransactionId());
        p.setMerchantId(request.merchantId());
        p.setBankMerchantId(bankMerchantId);
        p.setStan(String.format("%06d", repository.nextStan()));
        p.setPspTimestamp(Instant.now().truncatedTo(ChronoUnit.MICROS));
        p.setAmount(request.amount());
        p.setCurrency(request.currency());
        p.setReturnUrl(request.returnUrl());
        p.setStatus("INITIATING");
        return p;
    }

    private void markError(UUID transactionId) {
        tx.executeWithoutResult(s -> repository.findByTransactionIdForUpdate(transactionId).ifPresent(p -> {
            if ("INITIATING".equals(p.getStatus())) {
                // Core ostaje u INIT (kupac bira drugu metodu), pa se ovaj ishod ne prosledjuje Core-u
                p.setStatus("ERROR");
                p.setCoreNotifiedAt(Instant.now());
            }
        }));
    }

    public static class BankUnavailableException extends RuntimeException {
    }

    public static class UnknownPaymentException extends RuntimeException {
        public UnknownPaymentException(String paymentId) {
            super("Nepoznat PAYMENT_ID " + paymentId);
        }
    }
}
