package com.sep.parking.transaction;

import com.sep.parking.session.ParkingSession;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Jedna platna transakcija prema PSP-u (kupovina ili produzenje). Iznos i valuta se upisuju
 * pre slanja PSP-u i vise se ne menjaju - potvrda PSP-a se proverava prema njima.
 */
@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private ParkingSession session;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "merchant_order_id", nullable = false, unique = true, updatable = false)
    private String merchantOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionPurpose purpose;

    @Column(name = "duration_minutes", nullable = false, updatable = false)
    private int durationMinutes;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "psp_transaction_id")
    private String pspTransactionId;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "global_transaction_id")
    private String globalTransactionId;

    @Column(name = "acquirer_timestamp")
    private Instant acquirerTimestamp;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Trenutak odobrenja placanja (ACQUIRER_TIMESTAMP) ili prijema potvrde. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** Poslednja provera statusa na PSP-u (reconciliation). */
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "check_attempts", nullable = false)
    private int checkAttempts;

    /** Placanje je uspelo, a sesija nije aktivirana - povracaj istim nacinom placanja. */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status")
    private RefundStatus refundStatus;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refund_requested_at")
    private Instant refundRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
