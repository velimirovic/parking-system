package com.sep.bank.paymentrequest;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.sep.bank.common.config.BankProperties;
import com.sep.bank.common.crypto.Hmac;
import com.sep.bank.common.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Autentifikacija PSP-a prema banci HMAC kljucem (specifikacija, korak 3a). Zaglavlja:
 * {@code X-Timestamp} (Unix sekunde) i {@code X-Signature} = hex HMAC-SHA256 kanonskog zahteva
 * {@code timestamp \n METHOD \n path \n sha256(body)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PspAuthenticator {

    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Signature";

    private final BankProperties properties;

    public void authenticate(HttpServletRequest request, String path, String body) {
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String signature = request.getHeader(HEADER_SIGNATURE);
        if (timestamp == null || signature == null || !fresh(timestamp)) {
            throw unauthorized(request);
        }
        String expected = Hmac.sha256Hex(properties.pspHmacSecret(),
                Hmac.canonicalRequest(timestamp, request.getMethod(), path, body));
        if (!Hmac.matches(expected, signature)) {
            throw unauthorized(request);
        }
    }

    /** Potpis za poruke koje banka salje PSP-u (obavestenje o rezultatu). */
    public SignedHeaders sign(String method, String path, String body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        return new SignedHeaders(timestamp, Hmac.sha256Hex(properties.pspHmacSecret(),
                Hmac.canonicalRequest(timestamp, method, path, body)));
    }

    private boolean fresh(String value) {
        try {
            return Math.abs(Instant.now().getEpochSecond() - Long.parseLong(value.trim()))
                    <= properties.maxClockSkewSeconds();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private ApiException unauthorized(HttpServletRequest request) {
        log.warn("Odbijen neautentifikovan zahtev PSP-a: {} {} sa {}", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr());
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autentifikacija PSP-a nije uspela");
    }

    public record SignedHeaders(String timestamp, String signature) {
    }
}
