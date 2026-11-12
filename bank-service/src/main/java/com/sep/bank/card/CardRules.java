package com.sep.bank.card;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pravila forme za placanje karticom (specifikacija, korak 4). Ista pravila proverava i frontend banke,
 * ali server ih uvek ponavlja jer se klijentu ne veruje.
 */
public final class CardRules {

    private static final Pattern EXPIRY = Pattern.compile("^(\\d{2})/(\\d{2})$");
    private static final Pattern HOLDER = Pattern.compile("^[\\p{L}][\\p{L} .'-]{1,99}$");

    private CardRules() {
    }

    /** Uklanja razmake i crtice iz unetog broja kartice. */
    public static String normalizePan(String pan) {
        return pan == null ? "" : pan.replaceAll("[\\s-]", "");
    }

    /** PAN: 13-19 cifara i ispravan kontrolni broj po Lunovoj formuli. */
    public static boolean validPan(String pan) {
        return pan != null && pan.matches("\\d{13,19}") && luhn(pan);
    }

    public static boolean luhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Datum isteka u formatu MM/YY; empty ako format ili mesec nisu ispravni. */
    public static Optional<YearMonth> parseExpiry(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher m = EXPIRY.matcher(value.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        int month = Integer.parseInt(m.group(1));
        if (month < 1 || month > 12) {
            return Optional.empty();
        }
        return Optional.of(YearMonth.of(2000 + Integer.parseInt(m.group(2)), month));
    }

    /** Kartica vazi do kraja navedenog meseca. */
    public static boolean notExpired(YearMonth expiry, LocalDate today) {
        return !expiry.isBefore(YearMonth.from(today));
    }

    /** CVV/CVC: 3 cifre (sve prihvacene kartice - Visa, Mastercard, DinaCard). */
    public static boolean validSecurityCode(String code) {
        return code != null && code.matches("\\d{3}");
    }

    public static boolean validHolderName(String name) {
        return name != null && HOLDER.matcher(name.trim()).matches();
    }

    /** Ime vlasnika za poredjenje: velika slova, jedan razmak. */
    public static String normalizeHolder(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
    }
}
