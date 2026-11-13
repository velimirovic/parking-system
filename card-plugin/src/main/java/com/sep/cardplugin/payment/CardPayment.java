package com.sep.cardplugin.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Kartično plaćanje jedne PSP transakcije: veza PSP transakcije sa zahtevom kod banke
 * (STAN, PAYMENT_ID) i rezultat (GLOBAL_TRANSACTION_ID, ACQUIRER_TIMESTAMP).
 */
@Getter
@Setter
@Entity
@Table(name = "card_payment")
public class CardPayment {

    @Id
    private UUID id;

    private UUID transactionId;
    private String merchantId;
    private String bankMerchantId;
    private String stan;
    private Instant pspTimestamp;
    private BigDecimal amount;
    private String currency;
    private String returnUrl;
    private String bankPaymentId;
    private String bankPaymentUrl;
    private String status;
    private String responseCode;
    private String globalTransactionId;
    private Instant acquirerTimestamp;
    private Instant coreNotifiedAt;
    private int coreAttempts;
    private Instant nextCoreAttemptAt;
    private Instant nextStatusCheckAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private long version;

    public boolean isFinal() {
        return switch (status) {
            case "APPROVED", "DECLINED", "EXPIRED", "ERROR" -> true;
            default -> false;
        };
    }

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
