package com.sep.psp.webhook;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Outbox zapis o isporuci statusa klijentu (webhook). Portal ga samo prikazuje,
 * slanje i retry su deo webhook modula.
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_delivery")
public class WebhookDelivery {

    @Id
    private UUID id;

    private UUID transactionId;
    private String eventType;
    private String payload;
    private int attemptCount;
    private int maxAttempts;
    private Instant nextAttemptAt;

    @Enumerated(EnumType.STRING)
    private WebhookStatus status;

    private Integer lastHttpStatus;
    private Integer lastDurationMs;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
