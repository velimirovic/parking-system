package com.sep.parking.vehicle;

import com.sep.parking.common.exception.ApiException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registarske oznake u formatu Srbije: dva slova grada, 3-5 cifara i 1-2 slova
 * (npr. "BG 123-AB", "NS1234CD"). Cuva se normalizovano (BG123AB) i za prikaz (BG 123-AB).
 */
public final class PlateFormat {

    private static final String LETTER = "A-ZČĆŽŠĐ";
    private static final Pattern PLATE = Pattern.compile(
            "^([" + LETTER + "]{2})[\\s-]*(\\d{3,5})[\\s-]*([" + LETTER + "]{1,2})$");

    private PlateFormat() {
    }

    public record Plate(String normalized, String display) {
    }

    public static Plate parse(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        Matcher m = PLATE.matcher(value);
        if (!m.matches()) {
            throw ApiException.badRequest("INVALID_PLATE",
                    "Neispravna registarska oznaka. Primer: BG 123-AB.");
        }
        return new Plate(m.group(1) + m.group(2) + m.group(3), m.group(1) + " " + m.group(2) + "-" + m.group(3));
    }
}
