package com.sep.bank.account;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Kurs banke: koliko dinara vredi jedna jedinica valute. */
@Getter
@Setter
@Entity
@Table(name = "exchange_rate")
public class ExchangeRate {

    @Id
    private String currency;

    private BigDecimal rateToRsd;
    private Instant updatedAt;
}
