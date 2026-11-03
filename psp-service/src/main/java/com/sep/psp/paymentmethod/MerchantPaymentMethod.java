package com.sep.psp.paymentmethod;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Pretplata merchanta na metodu placanja. Konfiguracija (npr. bank MERCHANT_ID, racun,
 * PayPal client_id, wallet adresa) se cuva sifrovano.
 */
@Getter
@Setter
@Entity
@Table(name = "merchant_payment_method")
public class MerchantPaymentMethod {

    @Id
    private UUID id;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    private String configEncrypted;

    @Column(name = "is_active")
    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
