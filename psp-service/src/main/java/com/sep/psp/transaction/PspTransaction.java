package com.sep.psp.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.sep.psp.paymentmethod.PaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "psp_transaction")
public class PspTransaction {

    @Id
    private UUID id;

    @Column(name = "merchant_id")
    private UUID merchantId;

    /** Bira se tek posle INIT, na stranici za odabir metode. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    private String merchantOrderId;
    private Instant merchantTimestamp;
    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String stan;
    private Instant pspTimestamp;
    private String globalTransactionId;
    private Instant acquirerTimestamp;
    private Instant expiresAt;

    @Version
    private long version;

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
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
