package com.sep.cardplugin.common;

import java.time.Instant;

import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;

/** Provera i izrada HMAC potpisa zahteva (zaglavlja timestamp + potpis kanonskog zahteva). */
public final class SignedRequests {

    private SignedRequests() {
    }

    public static void verify(HttpServletRequest request, String secret, String timestampHeader,
                              String signatureHeader, String path, String body, int maxSkewSeconds) {
        String timestamp = request.getHeader(timestampHeader);
        String signature = request.getHeader(signatureHeader);
        boolean ok = timestamp != null && signature != null && fresh(timestamp, maxSkewSeconds)
                && Hmac.matches(Hmac.sha256Hex(secret,
                        Hmac.canonicalRequest(timestamp, request.getMethod(), path, body)), signature);
        if (!ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Neispravan potpis zahteva");
        }
    }

    public static Signed sign(String secret, String method, String path, String body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        return new Signed(timestamp, Hmac.sha256Hex(secret, Hmac.canonicalRequest(timestamp, method, path, body)));
    }

    private static boolean fresh(String value, int maxSkewSeconds) {
        try {
            return Math.abs(Instant.now().getEpochSecond() - Long.parseLong(value.trim())) <= maxSkewSeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public record Signed(String timestamp, String signature) {
    }
}
