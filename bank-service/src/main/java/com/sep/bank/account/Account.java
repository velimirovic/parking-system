package com.sep.bank.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Racun u banci. Raspolozivo stanje = balance - reserved. Kupovina se zavrsava rezervacijom sredstava
 * (bez clearing & settlement-a, po napomeni iz specifikacije).
 */
@Getter
@Setter
@Entity
@Table(name = "account")
public class Account {

    @Id
    private UUID id;

    private UUID customerId;
    private String accountNumber;
    private String holderName;
    private BigDecimal balance;
    private BigDecimal reserved;
    private String currency;

    @Version
    private long version;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (reserved == null) {
            reserved = BigDecimal.ZERO;
        }
        createdAt = Instant.now();
    }
}
