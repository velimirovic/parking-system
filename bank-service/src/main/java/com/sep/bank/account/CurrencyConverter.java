package com.sep.bank.account;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.sep.bank.common.exception.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * Preracun iznosa iz valute placanja u valutu racuna po kursu banke (preko RSD).
 */
@Component
@RequiredArgsConstructor
public class CurrencyConverter {

    private final ExchangeRateRepository repository;

    public boolean supports(String currency) {
        return repository.existsById(currency);
    }

    public Conversion convert(BigDecimal amount, String from, String to) {
        if (from.equals(to)) {
            return new Conversion(amount.setScale(2, RoundingMode.HALF_UP), BigDecimal.ONE.setScale(6));
        }
        BigDecimal fromRate = rate(from);
        BigDecimal toRate = rate(to);
        BigDecimal rate = fromRate.divide(toRate, 6, RoundingMode.HALF_UP);
        return new Conversion(amount.multiply(fromRate).divide(toRate, 2, RoundingMode.HALF_UP), rate);
    }

    private BigDecimal rate(String currency) {
        return repository.findById(currency).map(ExchangeRate::getRateToRsd)
                .orElseThrow(() -> ApiException.badRequest("UNSUPPORTED_CURRENCY", "Banka ne podrzava valutu " + currency));
    }

    public record Conversion(BigDecimal amount, BigDecimal rate) {
    }
}
