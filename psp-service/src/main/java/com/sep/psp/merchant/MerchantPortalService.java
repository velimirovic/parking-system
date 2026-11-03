package com.sep.psp.merchant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.common.crypto.AesGcmEncryptor;
import com.sep.psp.common.crypto.Hashing;
import com.sep.psp.common.crypto.SecretGenerator;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.common.web.Masking;
import com.sep.psp.common.web.RequestContext;
import com.sep.psp.common.web.UrlRules;
import com.sep.psp.merchant.MerchantDtos.MerchantProfile;
import com.sep.psp.merchant.MerchantDtos.RotatedSecret;
import com.sep.psp.merchant.MerchantDtos.UpdateUrlsRequest;

import lombok.RequiredArgsConstructor;

/**
 * Podesavanja klijenta iz klijentskog portala. Merchant se uvek odredjuje iz tokena prijavljenog korisnika.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MerchantPortalService {

    private final MerchantRepository merchantRepository;
    private final SecretGenerator secretGenerator;
    private final AesGcmEncryptor encryptor;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public MerchantProfile profile() {
        return toProfile(currentMerchant(false));
    }

    public MerchantProfile updateUrls(UpdateUrlsRequest request) {
        Merchant merchant = currentMerchant(true);
        List<String> changed = new ArrayList<>();
        merchant.setWebhookUrl(track(changed, "webhook_url", merchant.getWebhookUrl(),
                UrlRules.validate("Webhook URL", request.webhookUrl(), true)));
        merchant.setSuccessUrl(track(changed, "success_url", merchant.getSuccessUrl(),
                UrlRules.validate("SUCCESS_URL", request.successUrl(), false)));
        merchant.setFailedUrl(track(changed, "failed_url", merchant.getFailedUrl(),
                UrlRules.validate("FAILED_URL", request.failedUrl(), false)));
        merchant.setErrorUrl(track(changed, "error_url", merchant.getErrorUrl(),
                UrlRules.validate("ERROR_URL", request.errorUrl(), false)));
        if (!changed.isEmpty()) {
            auditService.userEvent(AuditAction.CONFIG_CHANGE, "MERCHANT", merchant.getMerchantCode(),
                    AuditOutcome.SUCCESS, "Izmenjeno: " + String.join(", ", changed));
        }
        return toProfile(merchant);
    }

    /** Novi API kljuc odmah zamenjuje stari; u bazi ostaje samo SHA-256 hash (PCI DSS 8.3.2). */
    public RotatedSecret rotateApiKey() {
        Merchant merchant = currentMerchant(true);
        String apiKey = secretGenerator.apiKey();
        merchant.setApiKeyHash(Hashing.sha256Hex(apiKey));
        merchant.setApiKeyLast4(Hashing.last4(apiKey));
        auditService.userEvent(AuditAction.KEY_ROTATION, "MERCHANT", merchant.getMerchantCode(),
                AuditOutcome.SUCCESS, "api_key");
        return new RotatedSecret(apiKey, Masking.maskSecret("sk_test_", merchant.getApiKeyLast4()));
    }

    public RotatedSecret rotateWebhookSecret() {
        Merchant merchant = currentMerchant(true);
        String secret = secretGenerator.webhookSecret();
        merchant.setWebhookSecretEncrypted(encryptor.encrypt(secret));
        merchant.setWebhookSecretLast4(Hashing.last4(secret));
        auditService.userEvent(AuditAction.KEY_ROTATION, "MERCHANT", merchant.getMerchantCode(),
                AuditOutcome.SUCCESS, "webhook_secret");
        return new RotatedSecret(secret, Masking.maskSecret("whsec_", merchant.getWebhookSecretLast4()));
    }

    private Merchant currentMerchant(boolean forUpdate) {
        var id = RequestContext.merchantId();
        return (forUpdate ? merchantRepository.findByIdForUpdate(id) : merchantRepository.findById(id))
                .orElseThrow(() -> ApiException.notFound("Klijent ne postoji"));
    }

    private static String track(List<String> changed, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changed.add(field);
        }
        return newValue;
    }

    private static MerchantProfile toProfile(Merchant m) {
        return new MerchantProfile(m.getName(), m.getMerchantCode(), m.getStatus(), m.getRateLimitPerSec(),
                Masking.maskSecret("sk_test_", m.getApiKeyLast4()),
                Masking.maskSecret("whsec_", m.getWebhookSecretLast4()),
                m.getWebhookUrl(), m.getSuccessUrl(), m.getFailedUrl(), m.getErrorUrl());
    }
}
