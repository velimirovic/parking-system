package com.sep.parking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sep.parking.session.PaymentOutcomeService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PspWebhookServiceTest {

    private static final String SECRET = "whsec_parking_demo_00000000000000000";
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private final PspProperties properties = new PspProperties("http://psp", "MRC-00042", "key", SECRET, 0, 0,
            "/psp/webhook", 300);
    private final PspWebhookService service = new PspWebhookService(properties, mock(PaymentOutcomeService.class),
            mock(PspWebhookEventRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));

    private static final String BODY = "{\"merchant_order_id\":\"PRK-1\",\"status\":\"SUCCESS\"}";

    @Test
    void acceptsValidSignature() {
        String ts = String.valueOf(NOW.getEpochSecond());
        String signature = PspSigner.sign(SECRET, ts, "POST", "/psp/webhook", BODY);
        assertThat(service.verify(BODY, ts, signature)).isNull();
    }

    @Test
    void rejectsTamperedBody() {
        String ts = String.valueOf(NOW.getEpochSecond());
        String signature = PspSigner.sign(SECRET, ts, "POST", "/psp/webhook", BODY);
        assertThat(service.verify(BODY.replace("PRK-1", "PRK-2"), ts, signature)).isNotNull();
    }

    @Test
    void rejectsStaleTimestamp() {
        String ts = String.valueOf(NOW.minusSeconds(600).getEpochSecond());
        String signature = PspSigner.sign(SECRET, ts, "POST", "/psp/webhook", BODY);
        assertThat(service.verify(BODY, ts, signature)).isNotNull();
    }

    @Test
    void rejectsMissingHeaders() {
        assertThat(service.verify(BODY, null, null)).isNotNull();
    }
}
