package com.sep.parking.payment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Potpis zahteva prema PSP-u (i provera webhook-a od PSP-a):
 * {@code hex(HMAC-SHA256(tajna, timestamp + "\n" + METHOD + "\n" + path + "\n" + sha256_hex(body)))}.
 */
public final class PspSigner {

    private PspSigner() {
    }

    public static String sign(String secret, String timestamp, String method, String path, String body) {
        String canonical = timestamp + "\n" + method + "\n" + path + "\n" + sha256Hex(body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 nije dostupan", e);
        }
    }

    /** Poredjenje u konstantnom vremenu (provera potpisa webhook-a). */
    public static boolean matches(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) return false;
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
                actualHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Hex(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nije dostupan", e);
        }
    }
}
