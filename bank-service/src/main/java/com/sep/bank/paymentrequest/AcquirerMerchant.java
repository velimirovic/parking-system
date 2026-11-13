package com.sep.bank.paymentrequest;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Trgovac ugovoren sa bankom preko PSP-a. MERCHANT_ID (tabela 2) banka dodeljuje PSP-u za svakog
 * njegovog klijenta i nije isti kao MERCHANT_ID koji PSP dodeljuje klijentu (tabela 1).
 */
@Getter
@Setter
@Entity
@Table(name = "acquirer_merchant")
public class AcquirerMerchant {

    @Id
    private UUID id;

    @Column(name = "merchant_id")
    private String merchantCode;

    private String name;
    private UUID accountId;
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
