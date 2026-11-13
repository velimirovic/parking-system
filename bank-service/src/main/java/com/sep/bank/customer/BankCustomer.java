package com.sep.bank.customer;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Klijent banke (kupac). Nalog se koristi i za prijavu u mobilnu aplikaciju banke (IPS placanje). */
@Getter
@Setter
@Entity
@Table(name = "bank_customer")
public class BankCustomer {

    @Id
    private UUID id;

    private String username;
    private String passwordHash;
    private String fullName;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
