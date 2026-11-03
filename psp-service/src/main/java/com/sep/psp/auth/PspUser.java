package com.sep.psp.auth;

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
 * Korisnik PSP portala: super admin ili administrator klijenta (merchanta).
 */
@Getter
@Setter
@Entity
@Table(name = "psp_user")
public class PspUser {

    @Id
    private UUID id;

    private String email;
    private String fullName;
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    /** null za SUPER_ADMIN. */
    @Column(name = "merchant_id")
    private UUID merchantId;

    private boolean mustChangePassword;
    private boolean mfaEnabled;
    private String mfaSecretEncrypted;
    private Long mfaLastUsedStep;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant passwordChangedAt;
    private Instant lastLoginAt;
    private boolean enabled;
    private Instant createdAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
