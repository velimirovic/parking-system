package com.sep.parking.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pristupni podaci parking sistema kao klijenta PSP-a (dobijaju se u PSP portalu).
 *
 * @param baseUrl          adresa PSP merchant API-ja (npr. http://psp-service:8082)
 * @param merchantId       MERCHANT_ID (X-Merchant-Id)
 * @param apiKey           API kljuc (X-Api-Key)
 * @param signingSecret    tajna za HMAC potpis zahteva (ista kojom PSP potpisuje webhook-ove)
 * @param connectTimeoutMs vreme za uspostavljanje veze
 * @param readTimeoutMs    vreme cekanja odgovora
 */
@ConfigurationProperties("app.psp")
public record PspProperties(String baseUrl, String merchantId, String apiKey, String signingSecret,
                            int connectTimeoutMs, int readTimeoutMs) {

    public PspProperties {
        if (connectTimeoutMs <= 0) connectTimeoutMs = 3000;
        if (readTimeoutMs <= 0) readTimeoutMs = 10000;
    }
}
