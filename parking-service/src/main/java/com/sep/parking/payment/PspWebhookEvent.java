package com.sep.parking.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Zapis svakog primljenog webhook-a PSP-a (i onih sa neispravnim potpisom) - trag za reviziju. */
@Entity
@Table(name = "psp_webhook_event")
@Getter
@Setter
@NoArgsConstructor
public class PspWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id")
    private String deliveryId;

    private String event;

    @Column(name = "merchant_order_id")
    private String merchantOrderId;

    @Column(name = "psp_status")
    private String pspStatus;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(nullable = false)
    private String outcome;

    private String detail;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
