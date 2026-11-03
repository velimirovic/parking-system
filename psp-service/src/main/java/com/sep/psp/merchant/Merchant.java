package com.sep.psp.merchant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Klijent PSP-a (parking sistem, webshop...). URL-ovi za uspeh/neuspeh/gresku se cuvaju ovde
 * i ne salju se uz svaki zahtev (tacka 1a/1b specifikacije).
 */
@Getter
@Setter
@Entity
@Table(name = "merchant")
public class Merchant {

    @Id
    private UUID id;

    private String name;

    /** MERCHANT_ID iz tabele 1, npr. MRC-00042. */
    @Column(name = "merchant_id")
    private String merchantCode;

    private String apiKeyHash;

    @Column(name = "api_key_last4")
    private String apiKeyLast4;

    private String webhookSecretEncrypted;

    @Column(name = "webhook_secret_last4")
    private String webhookSecretLast4;

    private String webhookUrl;
    private String successUrl;
    private String failedUrl;
    private String errorUrl;

    private int rateLimitPerSec;

    @Enumerated(EnumType.STRING)
    private MerchantStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private long version;

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
