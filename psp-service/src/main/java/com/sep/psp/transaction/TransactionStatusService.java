package com.sep.psp.transaction;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.webhook.WebhookDelivery;
import com.sep.psp.webhook.WebhookDeliveryRepository;
import com.sep.psp.webhook.WebhookStatus;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Jedino mesto gde transakcija prelazi u konacan status. Promena statusa i zapis za webhook (outbox)
 * upisuju se u ISTOJ DB transakciji, pa se obavestenje klijentu ne moze izgubiti ni pri otkazu instance.
 */
@Service
@RequiredArgsConstructor
public class TransactionStatusService {

    public static final Set<TransactionStatus> FINAL = EnumSet.of(TransactionStatus.SUCCESS,
            TransactionStatus.FAILED, TransactionStatus.ERROR, TransactionStatus.EXPIRED, TransactionStatus.REFUNDED);

    private static final int MAX_WEBHOOK_ATTEMPTS = 8;

    private final PspTransactionRepository repository;
    private final WebhookDeliveryRepository webhookRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Prevodi otvorenu transakciju (INIT/PENDING) u konacan status. Poziva se nad redom zakljucanim
     * sa {@link PspTransactionRepository#findByIdForUpdate}. Vec zavrsena transakcija se ne menja (idempotentno).
     *
     * @return true ako je status promenjen
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean complete(PspTransaction transaction, TransactionStatus status, String reason) {
        if (!FINAL.contains(status) || status == TransactionStatus.REFUNDED) {
            throw new IllegalArgumentException("Nije konacan status placanja: " + status);
        }
        if (FINAL.contains(transaction.getStatus())) {
            return false;
        }
        TransactionStatus previous = transaction.getStatus();
        transaction.setStatus(status);
        repository.save(transaction);

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setTransactionId(transaction.getId());
        delivery.setEventType(eventType(status));
        delivery.setPayload(objectMapper.writeValueAsString(payload(transaction, delivery.getEventType())));
        delivery.setAttemptCount(0);
        delivery.setMaxAttempts(MAX_WEBHOOK_ATTEMPTS);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setStatus(WebhookStatus.PENDING);
        webhookRepository.save(delivery);

        auditService.record(AuditService.ACTOR_SYSTEM, "psp-core", AuditAction.TX_STATUS_CHANGE, "TRANSACTION",
                transaction.getId().toString(), AuditOutcome.SUCCESS,
                previous + " -> " + status + (reason == null ? "" : " (" + reason + ")"));
        return true;
    }

    /**
     * Ako kupac nije izabrao metodu do isteka roka, transakcija u INIT postaje EXPIRED.
     * Transakciju u PENDING (kupac je kod banke / PayPal-a...) zatvara plugin na osnovu konacnog statusa.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expireIfDue(PspTransaction transaction) {
        if (transaction.getStatus() == TransactionStatus.INIT && transaction.getExpiresAt() != null
                && Instant.now().isAfter(transaction.getExpiresAt())) {
            return complete(transaction, TransactionStatus.EXPIRED, "istekao rok za izbor metode");
        }
        return false;
    }

    /** Tipovi dogadjaja iz modela WEBHOOK_DELIVERY; EXPIRED ide kao PAYMENT_FAILED sa statusom EXPIRED. */
    private static String eventType(TransactionStatus status) {
        return switch (status) {
            case SUCCESS -> "PAYMENT_SUCCESS";
            case ERROR -> "PAYMENT_ERROR";
            default -> "PAYMENT_FAILED";
        };
    }

    /** Telo webhook-a ka klijentu. Iznos i valuta se salju da bi klijent proverio da nisu menjani. */
    static Map<String, Object> payload(PspTransaction t, String eventType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", eventType);
        body.put("psp_transaction_id", t.getId().toString());
        body.put("merchant_order_id", t.getMerchantOrderId());
        body.put("status", t.getStatus().name());
        body.put("amount", t.getAmount().toPlainString());
        body.put("currency", t.getCurrency());
        body.put("payment_method", t.getPaymentMethod() == null ? null : t.getPaymentMethod().getCode());
        body.put("global_transaction_id", t.getGlobalTransactionId());
        body.put("acquirer_timestamp", t.getAcquirerTimestamp() == null ? null : t.getAcquirerTimestamp().toString());
        body.put("occurred_at", Instant.now().toString());
        return body;
    }
}
