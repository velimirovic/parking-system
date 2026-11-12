package com.sep.psp.plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ugovor PSP Core - plugin servis (isti za karticu, QR, PayPal, kripto...).
 *
 * <p>Core poziva {@code POST {plugin_base_url}/payments} sa {@link InitiateRequest}; plugin otvara placanje
 * kod spoljnog sistema (banka, PayPal, testnet) i vraca adresu na koju se kupac preusmerava.
 * Zahtev je potpisan zaglavljima {@code X-Psp-Timestamp} i {@code X-Psp-Signature}
 * (HMAC-SHA256 kanonskog zahteva deljenom tajnom PSP-a i plugin-ova).
 */
public final class PluginDtos {

    private PluginDtos() {
    }

    /**
     * @param merchantConfig desifrovana konfiguracija pretplate (npr. bankMerchantId za karticu)
     * @param returnUrl      PSP stranica na koju se kupac vraca posle placanja (prikaz rezultata)
     * @param expiresAt      rok PSP transakcije - plugin ne sme otvoriti placanje koje traje duze
     */
    public record InitiateRequest(
            UUID pspTransactionId,
            String merchantId,
            BigDecimal amount,
            String currency,
            Map<String, String> merchantConfig,
            String returnUrl,
            Instant expiresAt) {
    }

    /**
     * @param status      PENDING kada je kupac upucen na redirectUrl, FAILED kada spoljni sistem odbije zahtev
     * @param redirectUrl adresa za kupca (npr. PAYMENT_URL banke)
     * @param message     razlog odbijanja (bez osetljivih podataka)
     */
    public record InitiateResponse(String status, String redirectUrl, String message) {

        public boolean pending() {
            return "PENDING".equals(status) && redirectUrl != null && !redirectUrl.isBlank();
        }
    }
}
