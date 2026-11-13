package com.sep.bank.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Rezultat autorizacije: rezervacija sredstava (RESERVED) ili odbijanje (DECLINED). */
@Getter
@Setter
@Entity
@Table(name = "bank_transaction")
public class BankTransaction {

    @Id
    private UUID id;

    private UUID paymentRequestId;
    private UUID payerAccountId;
    private UUID payeeAccountId;
    private String globalTransactionId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal debitAmount;
    private String debitCurrency;
    private BigDecimal exchangeRate;
    private String type;
    private String status;
    private String responseCode;
    private String cardLast4;
    private Instant acquirerTimestamp;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
