package com.sep.bank.card;

import java.util.Optional;

/** Kartice koje banka prihvata (logo-i na formi za placanje). */
public enum CardBrand {
    VISA,
    MASTERCARD,
    DINACARD;

    /** Brend po BIN opsegu: Visa 4, Mastercard 51-55 i 2221-2720, DinaCard 9891. */
    public static Optional<CardBrand> detect(String pan) {
        if (pan == null || pan.length() < 4) {
            return Optional.empty();
        }
        if (pan.startsWith("9891")) {
            return Optional.of(DINACARD);
        }
        if (pan.startsWith("4")) {
            return Optional.of(VISA);
        }
        int two = Integer.parseInt(pan.substring(0, 2));
        int four = Integer.parseInt(pan.substring(0, 4));
        if ((two >= 51 && two <= 55) || (four >= 2221 && four <= 2720)) {
            return Optional.of(MASTERCARD);
        }
        return Optional.empty();
    }
}
