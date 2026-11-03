package com.sep.psp.paymentmethod;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Registrovana metoda placanja = jedan plugin servis. Code namerno nije enum,
 * pa se nova metoda (npr. PAYONEER) dodaje bez izmene i gasenja PSP Core-a.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_method")
public class PaymentMethod {

    @Id
    private UUID id;

    private String code;
    private String displayName;
    private String pluginBaseUrl;
    private boolean supportsRefund;

    @Column(name = "is_active")
    private boolean active;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
