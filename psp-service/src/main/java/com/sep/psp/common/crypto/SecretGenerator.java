package com.sep.psp.common.crypto;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Generisanje API kljuceva, webhook tajni i privremenih lozinki.
 */
@Component
public class SecretGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#%*-_+=?";

    private final SecureRandom random = new SecureRandom();

    public String apiKey() {
        return "sk_test_" + randomToken(32);
    }

    public String webhookSecret() {
        return "whsec_" + randomToken(32);
    }

    public byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    /** Privremena lozinka koja zadovoljava politiku lozinki (16 karaktera, sve klase znakova). */
    public String temporaryPassword() {
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        char[] chars = new char[16];
        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        chars[3] = pick(SYMBOLS);
        for (int i = 4; i < chars.length; i++) {
            chars[i] = pick(all);
        }
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private String randomToken(int bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(bytes));
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }
}
