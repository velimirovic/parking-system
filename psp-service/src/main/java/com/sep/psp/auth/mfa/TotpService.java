package com.sep.psp.auth.mfa;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.sep.psp.common.crypto.SecretGenerator;

import lombok.RequiredArgsConstructor;

/**
 * TOTP prema RFC 6238 (HMAC-SHA1, 30 s, 6 cifara) - kompatibilno sa Google i Microsoft Authenticator.
 */
@Service
@RequiredArgsConstructor
public class TotpService {

    private static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    /** Dozvoljeno odstupanje sata: jedan korak pre i posle. */
    private static final int WINDOW = 1;

    private final SecretGenerator secretGenerator;

    public String generateSecret() {
        return Base32.encode(secretGenerator.randomBytes(20));
    }

    public String otpauthUri(String issuer, String account, String secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    public long currentStep() {
        return Instant.now().getEpochSecond() / PERIOD_SECONDS;
    }

    public int secondsRemaining() {
        return (int) (PERIOD_SECONDS - Instant.now().getEpochSecond() % PERIOD_SECONDS);
    }

    /**
     * Proverava kod i vraca vremenski korak kojem pripada. Kod iz koraka koji je vec iskoriscen
     * (lastUsedStep) se odbija, pa presretnut kod ne moze ponovo da se upotrebi.
     */
    public OptionalLong verify(String secret, String code, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = Base32.decode(secret);
        long now = currentStep();
        for (long step = now - WINDOW; step <= now + WINDOW; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            String expected = generate(key, step);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    static String generate(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
