package com.sep.psp.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 potpisi zahteva izmedju sistema (merchant API, plugin-ovi, webhook-ovi) - PCI DSS zahtev 4
 * i zastita od izmene iznosa, valute i statusa u prenosu.
 */
public final class Hmac {

    private static final String ALGORITHM = "HmacSHA256";

    private Hmac() {
    }

    /** Hex (mala slova) HMAC-SHA256 vrednosti {@code data} kljucem {@code secret}. */
    public static String sha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC nije uspeo", e);
        }
    }

    /** Poredjenje u konstantnom vremenu (bez curenja informacije kroz trajanje poredjenja). */
    public static boolean matches(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.US_ASCII),
                actualHex.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Kanonski zapis potpisanog zahteva: {@code timestamp \n METHOD \n path \n sha256(body)}.
     * Path je putanja PSP API-ja bez prefiksa proxy-ja (npr. /transactions), bez query stringa.
     */
    public static String canonicalRequest(String timestamp, String method, String path, String body) {
        return timestamp + "\n" + method.toUpperCase() + "\n" + path + "\n" + Hashing.sha256Hex(body == null ? "" : body);
    }
}
