package com.sep.psp.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "password_history")
public class PasswordHistory {

    @Id
    private UUID id;

    private UUID userId;
    private String passwordHash;
    private Instant createdAt;

    public PasswordHistory(UUID userId, String passwordHash) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }
}
