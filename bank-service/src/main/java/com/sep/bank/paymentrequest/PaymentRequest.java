package com.sep.bank.paymentrequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Zahtev PSP-a za placanje (tabela 2). Jedan PAYMENT_ID = jedna forma = jedna transakcija,
 * vremenski ogranicena i sa samo jednim pokusajem placanja.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_request")
public class PaymentRequest {

    @Id
    private UUID id;

    private String paymentId;

    @Column(name = "merchant_id")
    private String merchantCode;

    private String stan;
    private Instant pspTimestamp;
    private BigDecimal amount;
    private String currency;
    private UUID merchantAccountId;
    private String resultUrl;
    private String notifyUrl;
    private Instant expiresAt;
    private boolean attemptUsed;

    @Enumerated(EnumType.STRING)
    private PaymentRequestStatus status;

    private String responseCode;
    private Instant pspNotifiedAt;
    private int notifyAttempts;
    private Instant nextNotifyAt;
    private Instant createdAt;
    private Instant completedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
