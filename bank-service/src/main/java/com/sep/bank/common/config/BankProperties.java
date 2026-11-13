package com.sep.bank.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param paymentPageBaseUrl  javna adresa stranice banke (PAYMENT_URL = {base}/placanje/{PAYMENT_ID})
 * @param paymentTtlMinutes   koliko dugo forma za placanje vazi
 * @param pspHmacSecret       HMAC kljuc za autentifikaciju PSP-a i potpis obavestenja ka PSP-u
 * @param maxClockSkewSeconds dozvoljeno odstupanje X-Timestamp zaglavlja
 * @param cardEncryptionKey   AES-256 kljuc (base64) za sifrovanje PAN-a
 * @param cardTokenKey        HMAC kljuc za token PAN-a i hash CVV-a
 */
@ConfigurationProperties(prefix = "app.security")
public record BankProperties(
        String paymentPageBaseUrl,
        int paymentTtlMinutes,
        String pspHmacSecret,
        int maxClockSkewSeconds,
        String cardEncryptionKey,
        String cardTokenKey) {
}
