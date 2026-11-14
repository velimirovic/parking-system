package com.sep.parking.payment;

import com.sep.parking.session.PaymentOutcome;
import com.sep.parking.session.PaymentOutcomeService;
import com.sep.parking.session.PaymentResult;
import com.sep.parking.session.StatusActor;
import com.sep.parking.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;

/**
 * Obrada webhook-a PSP-a: provera vremena i HMAC potpisa, parsiranje tela, primena ishoda placanja
 * (iznos i valuta se proveravaju u {@link PaymentOutcomeService}) i zapis u psp_webhook_event.
 *
 * <p>Odgovori: 200 kada je webhook obradjen ili je ponovljen (PSP prestaje da salje), 401 za los potpis
 * ili zastareo zahtev, 400 za neispravno telo; greska u obradi daje 500, pa PSP ponavlja slanje.
 */
@Service
public class PspWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PspWebhookService.class);

    public record Result(int httpStatus, String outcome) {
    }

    private final PspProperties properties;
    private final PaymentOutcomeService outcomes;
    private final PspWebhookEventRepository events;
    private final Clock clock;

    public PspWebhookService(PspProperties properties, PaymentOutcomeService outcomes,
                             PspWebhookEventRepository events, Clock clock) {
        this.properties = properties;
        this.outcomes = outcomes;
        this.events = events;
        this.clock = clock;
    }

    public Result handle(byte[] rawBody, String timestamp, String signature, String eventHeader, String deliveryId) {
        String body = new String(rawBody, StandardCharsets.UTF_8);

        // ---------- autentifikacija ----------
        String authError = verify(body, timestamp, signature);
        if (authError != null) {
            log.warn("Odbijen webhook PSP-a (isporuka {}): {}", deliveryId, authError);
            record(deliveryId, eventHeader, null, null, false, "REJECTED", authError);
            return new Result(401, "REJECTED");
        }

        // ---------- telo ----------
        Map<String, Object> json;
        try {
            json = JsonParserFactory.getJsonParser().parseMap(body);
        } catch (IllegalArgumentException e) { // ukljucuje JsonParseException
            record(deliveryId, eventHeader, null, null, true, "MALFORMED", "Neispravan JSON");
            return new Result(400, "MALFORMED");
        }
        String orderId = text(json, "merchant_order_id");
        String status = text(json, "status");
        String event = text(json, "event") != null ? text(json, "event") : eventHeader;
        if (orderId == null || status == null) {
            record(deliveryId, event, orderId, status, true, "MALFORMED", "Nedostaje merchant_order_id ili status");
            return new Result(400, "MALFORMED");
        }

        PaymentResult result;
        try {
            result = new PaymentResult(TransactionStatus.fromPsp(status), decimal(json.get("amount")),
                    text(json, "currency"), text(json, "payment_method"), text(json, "psp_transaction_id"),
                    text(json, "global_transaction_id"), instant(text(json, "acquirer_timestamp")));
        } catch (NumberFormatException | DateTimeParseException e) {
            record(deliveryId, event, orderId, status, true, "MALFORMED", "Neispravan iznos ili vreme");
            return new Result(400, "MALFORMED");
        }

        // ---------- primena ishoda ----------
        PaymentOutcome outcome;
        try {
            outcome = outcomes.apply(orderId, result, StatusActor.PSP, "webhook");
        } catch (RuntimeException e) {
            log.error("Obrada webhook-a za {} nije uspela - PSP ce ponoviti slanje", orderId, e);
            record(deliveryId, event, orderId, status, true, "ERROR", truncate(e.getClass().getSimpleName()));
            return new Result(500, "ERROR");
        }
        record(deliveryId, event, orderId, status, true, outcome.name(), null);
        log.info("Webhook PSP-a za {} ({}): {}", orderId, status, outcome);
        return new Result(200, outcome.name());
    }

    /** null = ispravno; inace razlog odbijanja. */
    String verify(String body, String timestamp, String signature) {
        if (timestamp == null || signature == null) {
            return "Nedostaje X-Psp-Timestamp ili X-Psp-Signature";
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return "Neispravan X-Psp-Timestamp";
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - ts) > properties.webhookToleranceSeconds()) {
            return "X-Psp-Timestamp je van dozvoljenog vremena";
        }
        String expected = PspSigner.sign(properties.signingSecret(), timestamp.trim(), "POST",
                properties.webhookPath(), body);
        return PspSigner.matches(expected, signature) ? null : "Neispravan potpis";
    }

    private void record(String deliveryId, String event, String orderId, String status, boolean signatureValid,
                        String outcome, String detail) {
        try {
            PspWebhookEvent e = new PspWebhookEvent();
            e.setDeliveryId(truncate(deliveryId, 100));
            e.setEvent(truncate(event, 40));
            e.setMerchantOrderId(truncate(orderId, 64));
            e.setPspStatus(truncate(status, 20));
            e.setSignatureValid(signatureValid);
            e.setOutcome(outcome);
            e.setDetail(truncate(detail, 300));
            e.setReceivedAt(clock.instant());
            events.save(e);
        } catch (RuntimeException ex) {
            log.warn("Webhook dogadjaj nije zapisan: {}", ex.getMessage());
        }
    }

    private static String text(Map<String, Object> json, String key) {
        Object v = json.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static BigDecimal decimal(Object v) {
        return v == null ? null : new BigDecimal(String.valueOf(v));
    }

    private static Instant instant(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            return java.time.OffsetDateTime.parse(v).toInstant();
        }
    }

    private static String truncate(String v) {
        return truncate(v, 300);
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
