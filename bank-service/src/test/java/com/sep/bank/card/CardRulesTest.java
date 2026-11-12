package com.sep.bank.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CardRulesTest {

    @Test
    void luhnAcceptsValidTestCards() {
        assertTrue(CardRules.validPan("4111111111111111"));
        assertTrue(CardRules.validPan("5555555555554444"));
        assertTrue(CardRules.validPan("9891000000000011"));
        assertTrue(CardRules.validPan("4222222222222")); // 13 cifara
    }

    @Test
    void luhnRejectsWrongCheckDigitAndBadLength() {
        assertFalse(CardRules.validPan("4111111111111112"));
        assertFalse(CardRules.validPan("411111111111"));          // 12 cifara
        assertFalse(CardRules.validPan("41111111111111111111"));  // 20 cifara
        assertFalse(CardRules.validPan("4111a11111111111"));
        assertFalse(CardRules.validPan(null));
    }

    @Test
    void panIsNormalizedFromSpacesAndDashes() {
        assertEquals("4111111111111111", CardRules.normalizePan("4111 1111-1111 1111"));
    }

    @Test
    void expiryMustBeMmSlashYy() {
        assertEquals(Optional.of(YearMonth.of(2028, 8)), CardRules.parseExpiry("08/28"));
        assertTrue(CardRules.parseExpiry("13/28").isEmpty());
        assertTrue(CardRules.parseExpiry("00/28").isEmpty());
        assertTrue(CardRules.parseExpiry("8/28").isEmpty());
        assertTrue(CardRules.parseExpiry("08/2028").isEmpty());
        assertTrue(CardRules.parseExpiry("0828").isEmpty());
    }

    @Test
    void cardIsValidUntilEndOfExpiryMonth() {
        LocalDate today = LocalDate.of(2026, 11, 30);
        assertTrue(CardRules.notExpired(YearMonth.of(2026, 11), today));
        assertFalse(CardRules.notExpired(YearMonth.of(2026, 10), today));
    }

    @Test
    void securityCodeAndHolderName() {
        assertTrue(CardRules.validSecurityCode("123"));
        assertFalse(CardRules.validSecurityCode("12"));
        assertFalse(CardRules.validSecurityCode("12a"));
        assertTrue(CardRules.validHolderName("Petar Petrović"));
        assertFalse(CardRules.validHolderName("1234"));
        assertEquals("PETAR PETROVIC", CardRules.normalizeHolder("  petar   petrovic "));
    }

    @Test
    void brandIsDetectedFromBin() {
        assertEquals(Optional.of(CardBrand.VISA), CardBrand.detect("4111111111111111"));
        assertEquals(Optional.of(CardBrand.MASTERCARD), CardBrand.detect("5555555555554444"));
        assertEquals(Optional.of(CardBrand.MASTERCARD), CardBrand.detect("2221000000000009"));
        assertEquals(Optional.of(CardBrand.DINACARD), CardBrand.detect("9891000000000011"));
        assertTrue(CardBrand.detect("378282246310005").isEmpty()); // Amex se ne prihvata
    }
}
