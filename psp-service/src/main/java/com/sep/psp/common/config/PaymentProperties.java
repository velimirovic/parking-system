package com.sep.psp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Podesavanja toka placanja.
 *
 * @param checkoutBaseUrl      javna adresa PSP frontenda (kupac se preusmerava na {base}/placanje/{id})
 * @param checkoutTtlMinutes   koliko dugo kupac moze da zavrsi placanje posle inicijalizacije
 * @param maxClockSkewSeconds  dozvoljeno odstupanje X-Timestamp zaglavlja (zastita od ponovljenih zahteva)
 * @param pluginSharedSecret   HMAC kljuc za komunikaciju PSP Core - plugin servisi
 * @param pluginTimeoutMillis  timeout poziva plugin-a
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        String checkoutBaseUrl,
        int checkoutTtlMinutes,
        int maxClockSkewSeconds,
        String pluginSharedSecret,
        int pluginTimeoutMillis) {
}
