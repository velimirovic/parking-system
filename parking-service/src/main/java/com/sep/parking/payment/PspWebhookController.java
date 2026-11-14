package com.sep.parking.payment;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook PSP -> parking sistem (konacan status placanja). Putanja je javna (PSP nema JWT korisnika),
 * a svaki zahtev mora imati ispravan HMAC potpis. Telo se prima kao bajtovi jer se potpis racuna nad
 * tacno primljenim telom.
 */
@RestController
public class PspWebhookController {

    private final PspWebhookService service;

    public PspWebhookController(PspWebhookService service) {
        this.service = service;
    }

    @PostMapping("${app.psp.webhook-path:/psp/webhook}")
    public ResponseEntity<Map<String, String>> receive(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "X-Psp-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Psp-Signature", required = false) String signature,
            @RequestHeader(value = "X-Psp-Event", required = false) String event,
            @RequestHeader(value = "X-Psp-Delivery", required = false) String deliveryId) {
        PspWebhookService.Result result = service.handle(body == null ? new byte[0] : body, timestamp, signature,
                event, deliveryId);
        return ResponseEntity.status(result.httpStatus()).body(Map.of("result", result.outcome()));
    }
}
