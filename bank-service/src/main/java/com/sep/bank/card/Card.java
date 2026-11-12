package com.sep.bank.card;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Platna kartica izdata od banke. PAN i CVV se ne cuvaju u citljivom obliku (vidi CardCrypto). */
@Getter
@Setter
@Entity
@Table(name = "card")
public class Card {

    @Id
    private UUID id;

    private UUID accountId;
    private String panToken;
    private String panEncrypted;
    private String panLast4;

    @Enumerated(EnumType.STRING)
    private CardBrand brand;

    private short expiryMonth;
    private short expiryYear;
    private String cardHolderName;
    private String cvvHash;
    private String status;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
