package com.sep.bank.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.sep.bank.common.config.BankProperties;

/**
 * Sifrovanje PAN-a u bazi banke (AES-256-GCM) - PCI DSS zahtev 3.5.
 * Format: "v1:" + base64(IV[12] || ciphertext || tag). Prefiks verzije omogucava rotaciju kljuca.
 */
@Component
public class AesGcmEncryptor {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(BankProperties properties) {
        byte[] raw = Base64.getDecoder().decode(properties.cardEncryptionKey());
        if (raw.length != 32) {
            throw new IllegalStateException("app.security.card-encryption-key mora biti 32 bajta (AES-256) u base64 formatu");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Sifrovanje nije uspelo", e);
        }
    }

    public String decrypt(String value) {
        if (value == null) {
            return null;
        }
        if (!value.startsWith(PREFIX)) {
            throw new IllegalStateException("Nepoznata verzija sifrata");
        }
        try {
            byte[] data = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Desifrovanje nije uspelo", e);
        }
    }
}
