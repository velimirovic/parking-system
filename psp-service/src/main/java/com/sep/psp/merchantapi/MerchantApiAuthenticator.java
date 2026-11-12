package com.sep.psp.merchantapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.common.config.PaymentProperties;
import com.sep.psp.common.crypto.AesGcmEncryptor;
import com.sep.psp.common.crypto.Hashing;
import com.sep.psp.common.crypto.Hmac;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.merchant.Merchant;
import com.sep.psp.merchant.MerchantRepository;
import com.sep.psp.merchant.MerchantStatus;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Autentifikacija klijenta PSP-a (parking sistem, webshop...) na merchant API-ju.
 *
 * <p>Svaki zahtev nosi:
 * <ul>
 *   <li>{@code X-Merchant-Id} - MERCHANT_ID iz tabele 1 (npr. MRC-00042)</li>
 *   <li>{@code X-Api-Key} - API kljuc; PSP cuva samo njegov SHA-256 hash (PCI DSS 8.3.2)</li>
 *   <li>{@code X-Timestamp} - Unix vreme u sekundama; stariji zahtevi od dozvoljenog odstupanja se odbijaju</li>
 *   <li>{@code X-Signature} - hex HMAC-SHA256 kanonskog zahteva ({@link Hmac#canonicalRequest}) potpisan
 *       tajnom za potpis klijenta (webhook tajna) - izmena iznosa ili valute u prenosu ponistava potpis</li>
 * </ul>
 * Sve greske vracaju istu poruku da napadac ne bi saznao koji deo nije prosao.
 */
@Component
@RequiredArgsConstructor
public class MerchantApiAuthenticator {

    public static final String HEADER_MERCHANT = "X-Merchant-Id";
    public static final String HEADER_API_KEY = "X-Api-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Signature";

    private final MerchantRepository merchantRepository;
    private final AesGcmEncryptor encryptor;
    private final AuditService auditService;
    private final PaymentProperties properties;

    /**
     * @param path putanja PSP API-ja bez prefiksa proxy-ja, npr. {@code /transactions}
     * @param body sirovo telo zahteva tacno kako je potpisano (prazno za GET)
     */
    public Merchant authenticate(HttpServletRequest request, String path, String body) {
        String merchantCode = request.getHeader(HEADER_MERCHANT);
        String apiKey = request.getHeader(HEADER_API_KEY);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (isBlank(merchantCode) || isBlank(apiKey) || isBlank(timestamp) || isBlank(signature)) {
            throw fail(merchantCode, "nedostaju zaglavlja za autentifikaciju");
        }
        if (!freshTimestamp(timestamp)) {
            throw fail(merchantCode, "X-Timestamp van dozvoljenog opsega");
        }
        Merchant merchant = merchantRepository.findByMerchantCode(merchantCode.trim())
                .orElseThrow(() -> fail(merchantCode, "nepoznat MERCHANT_ID"));
        if (!MessageDigest.isEqual(Hashing.sha256Hex(apiKey).getBytes(StandardCharsets.US_ASCII),
                merchant.getApiKeyHash().getBytes(StandardCharsets.US_ASCII))) {
            throw fail(merchantCode, "neispravan API kljuc");
        }
        String secret = encryptor.decrypt(merchant.getWebhookSecretEncrypted());
        String expected = Hmac.sha256Hex(secret, Hmac.canonicalRequest(timestamp, request.getMethod(), path, body));
        if (!Hmac.matches(expected, signature)) {
            throw fail(merchantCode, "neispravan potpis zahteva");
        }
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            auditService.record("MERCHANT_API", merchantCode, AuditAction.MERCHANT_API_AUTH_FAILED, "MERCHANT",
                    merchantCode, AuditOutcome.FAILURE, "klijent nije aktivan");
            throw new ApiException(HttpStatus.FORBIDDEN, "MERCHANT_INACTIVE", "Klijent nije aktivan");
        }
        return merchant;
    }

    private boolean freshTimestamp(String value) {
        try {
            long seconds = Long.parseLong(value.trim());
            long now = Instant.now().getEpochSecond();
            return Math.abs(now - seconds) <= properties.maxClockSkewSeconds();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private ApiException fail(String merchantCode, String reason) {
        String actor = isBlank(merchantCode) ? null : merchantCode.trim().substring(0, Math.min(merchantCode.trim().length(), 32));
        auditService.record("MERCHANT_API", actor, AuditAction.MERCHANT_API_AUTH_FAILED, "MERCHANT", actor,
                AuditOutcome.FAILURE, reason);
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autentifikacija klijenta nije uspela");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
