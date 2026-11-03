package com.sep.psp.merchant;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.auth.AuthService;
import com.sep.psp.auth.PspUser;
import com.sep.psp.auth.PspUserRepository;
import com.sep.psp.auth.Role;
import com.sep.psp.common.crypto.AesGcmEncryptor;
import com.sep.psp.common.crypto.Hashing;
import com.sep.psp.common.crypto.SecretGenerator;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.common.web.UrlRules;
import com.sep.psp.merchant.MerchantDtos.CreateMerchantRequest;
import com.sep.psp.merchant.MerchantDtos.MerchantCredentials;
import com.sep.psp.merchant.MerchantDtos.MerchantSummary;
import com.sep.psp.paymentmethod.MerchantPaymentMethod;
import com.sep.psp.paymentmethod.MerchantPaymentMethodRepository;
import com.sep.psp.paymentmethod.PaymentMethod;
import com.sep.psp.paymentmethod.PaymentMethodRepository;

import lombok.RequiredArgsConstructor;

/**
 * Upravljanje klijentima PSP-a (super admin). Novi klijent se dodaje u toku rada, bez restarta PSP-a.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MerchantAdminService {

    private final MerchantRepository merchantRepository;
    private final PspUserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final MerchantPaymentMethodRepository merchantMethodRepository;
    private final SecretGenerator secretGenerator;
    private final AesGcmEncryptor encryptor;
    private final AuthService authService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MerchantSummary> list() {
        List<Merchant> merchants = merchantRepository.findAllByOrderByCreatedAtAsc();
        List<UUID> ids = merchants.stream().map(Merchant::getId).toList();
        Map<UUID, List<MerchantPaymentMethod>> methods = merchantMethodRepository.findByMerchantIdIn(ids).stream()
                .collect(Collectors.groupingBy(MerchantPaymentMethod::getMerchantId));
        Map<UUID, PspUser> admins = userRepository.findByMerchantIdIn(ids).stream()
                .collect(Collectors.toMap(PspUser::getMerchantId, Function.identity(), (a, b) -> a));
        return merchants.stream()
                .map(m -> toSummary(m, methods.getOrDefault(m.getId(), List.of()), admins.get(m.getId())))
                .toList();
    }

    public MerchantCredentials create(CreateMerchantRequest request) {
        String name = request.name().trim();
        String email = request.adminEmail().trim().toLowerCase();
        if (merchantRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("MERCHANT_EXISTS", "Klijent sa tim nazivom vec postoji");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_EXISTS", "Korisnik sa tom e-poštom vec postoji");
        }
        List<PaymentMethod> selected = resolveMethods(request.methods());
        String webhookUrl = UrlRules.validate("Webhook URL", request.webhookUrl(), true);
        String successUrl = UrlRules.validate("SUCCESS_URL", request.successUrl(), false);
        String failedUrl = UrlRules.validate("FAILED_URL", request.failedUrl(), false);
        String errorUrl = UrlRules.validate("ERROR_URL", request.errorUrl(), false);

        String apiKey = secretGenerator.apiKey();
        String webhookSecret = secretGenerator.webhookSecret();

        Merchant merchant = new Merchant();
        merchant.setName(name);
        merchant.setMerchantCode(String.format("MRC-%05d", merchantRepository.nextMerchantNumber()));
        merchant.setApiKeyHash(Hashing.sha256Hex(apiKey));
        merchant.setApiKeyLast4(Hashing.last4(apiKey));
        merchant.setWebhookSecretEncrypted(encryptor.encrypt(webhookSecret));
        merchant.setWebhookSecretLast4(Hashing.last4(webhookSecret));
        merchant.setWebhookUrl(webhookUrl);
        merchant.setSuccessUrl(successUrl);
        merchant.setFailedUrl(failedUrl);
        merchant.setErrorUrl(errorUrl);
        merchant.setRateLimitPerSec(request.rateLimitPerSec());
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchantRepository.save(merchant);

        List<MerchantPaymentMethod> subscriptions = selected.stream().map(method -> {
            MerchantPaymentMethod mpm = new MerchantPaymentMethod();
            mpm.setMerchantId(merchant.getId());
            mpm.setPaymentMethod(method);
            mpm.setActive(true);
            return merchantMethodRepository.save(mpm);
        }).toList();

        String temporaryPassword = secretGenerator.temporaryPassword();
        PspUser admin = new PspUser();
        admin.setEmail(email);
        admin.setFullName(request.adminFullName() == null || request.adminFullName().isBlank()
                ? email : request.adminFullName().trim());
        admin.setRole(Role.MERCHANT_ADMIN);
        admin.setMerchantId(merchant.getId());
        admin.setMustChangePassword(true);
        admin.setEnabled(true);
        admin.setPasswordHash("");
        userRepository.save(admin);
        authService.applyNewPassword(admin, temporaryPassword);

        auditService.userEvent(AuditAction.MERCHANT_CREATE, "MERCHANT", merchant.getMerchantCode(),
                AuditOutcome.SUCCESS, "Metode: " + String.join(", ", selected.stream().map(PaymentMethod::getCode).toList()));

        return new MerchantCredentials(toSummary(merchant, subscriptions, admin), email, temporaryPassword,
                apiKey, webhookSecret);
    }

    public MerchantSummary updateStatus(UUID id, MerchantStatus status) {
        Merchant merchant = merchantRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Klijent ne postoji"));
        MerchantStatus previous = merchant.getStatus();
        merchant.setStatus(status);
        auditService.userEvent(AuditAction.MERCHANT_STATUS_CHANGE, "MERCHANT", merchant.getMerchantCode(),
                AuditOutcome.SUCCESS, previous + " -> " + status);
        return summaryOf(merchant);
    }

    public MerchantSummary updateRateLimit(UUID id, int rateLimit) {
        Merchant merchant = merchantRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Klijent ne postoji"));
        int previous = merchant.getRateLimitPerSec();
        merchant.setRateLimitPerSec(rateLimit);
        auditService.userEvent(AuditAction.CONFIG_CHANGE, "MERCHANT", merchant.getMerchantCode(),
                AuditOutcome.SUCCESS, "rate_limit_per_sec " + previous + " -> " + rateLimit);
        return summaryOf(merchant);
    }

    /**
     * Izgubljen uredjaj ili zaboravljena lozinka: nova privremena lozinka, MFA se ponovo uparuje,
     * a postojece sesije se ne mogu produziti (password_changed_at).
     */
    public MerchantCredentials resetAccess(UUID id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Klijent ne postoji"));
        PspUser admin = userRepository.findByMerchantId(id).stream()
                .min(Comparator.comparing(PspUser::getCreatedAt))
                .orElseThrow(() -> ApiException.notFound("Klijent nema administratora"));
        String temporaryPassword = secretGenerator.temporaryPassword();
        authService.applyNewPassword(admin, temporaryPassword);
        admin.setMustChangePassword(true);
        admin.setMfaEnabled(false);
        admin.setMfaSecretEncrypted(null);
        admin.setMfaLastUsedStep(null);
        admin.setFailedLoginAttempts(0);
        admin.setLockedUntil(null);
        admin.setPasswordChangedAt(Instant.now());
        auditService.userEvent(AuditAction.MERCHANT_ACCESS_RESET, "PSP_USER", admin.getId().toString(),
                AuditOutcome.SUCCESS, merchant.getMerchantCode() + " - nova privremena lozinka, MFA resetovan");
        return new MerchantCredentials(summaryOf(merchant), admin.getEmail(), temporaryPassword, null, null);
    }

    private List<PaymentMethod> resolveMethods(List<String> codes) {
        Set<String> unique = codes.stream().map(c -> c.trim().toUpperCase()).collect(Collectors.toSet());
        return unique.stream().map(code -> paymentMethodRepository.findByCode(code)
                        .filter(PaymentMethod::isActive)
                        .orElseThrow(() -> ApiException.badRequest("UNKNOWN_METHOD",
                                "Metoda " + code + " ne postoji ili nije aktivna")))
                .sorted(Comparator.comparing(PaymentMethod::getCreatedAt).thenComparing(PaymentMethod::getCode))
                .toList();
    }

    private MerchantSummary summaryOf(Merchant merchant) {
        PspUser admin = userRepository.findByMerchantId(merchant.getId()).stream().findFirst().orElse(null);
        return toSummary(merchant, merchantMethodRepository.findByMerchantId(merchant.getId()), admin);
    }

    private MerchantSummary toSummary(Merchant m, List<MerchantPaymentMethod> methods, PspUser admin) {
        List<String> active = methods.stream()
                .filter(MerchantPaymentMethod::isActive)
                .map(mpm -> mpm.getPaymentMethod().getCode())
                .toList();
        return new MerchantSummary(m.getId(), m.getName(), m.getMerchantCode(),
                admin == null ? null : admin.getEmail(), active, m.getRateLimitPerSec(), m.getStatus(),
                admin != null && admin.isMustChangePassword(), m.getCreatedAt());
    }
}
