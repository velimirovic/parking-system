package com.sep.psp.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.auth.AuthDtos.ChangePasswordRequest;
import com.sep.psp.auth.AuthDtos.FirstPasswordRequest;
import com.sep.psp.auth.AuthDtos.LoginRequest;
import com.sep.psp.auth.AuthDtos.LoginResponse;
import com.sep.psp.auth.AuthDtos.MfaCodeRequest;
import com.sep.psp.auth.AuthDtos.MfaSetupResponse;
import com.sep.psp.auth.AuthDtos.PreAuthRequest;
import com.sep.psp.auth.AuthDtos.TokenResponse;
import com.sep.psp.auth.AuthDtos.UserResponse;
import com.sep.psp.auth.mfa.QrCodeService;
import com.sep.psp.auth.mfa.TotpService;
import com.sep.psp.common.config.SecurityProperties;
import com.sep.psp.common.crypto.AesGcmEncryptor;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.common.web.RequestContext;
import com.sep.psp.merchant.Merchant;
import com.sep.psp.merchant.MerchantRepository;
import com.sep.psp.merchant.MerchantStatus;

/**
 * Prijava na PSP portal (PCI DSS zahtev 8): lozinka + TOTP MFA, zakljucavanje posle
 * neuspelih pokusaja, obavezna promena lozinke pri prvoj prijavi.
 * Neuspesi se ne rollback-uju, da bi brojac pokusaja i zakljucavanje ostali sacuvani.
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
public class AuthService {

    private final PspUserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenService tokenService;
    private final TotpService totpService;
    private final QrCodeService qrCodeService;
    private final AesGcmEncryptor encryptor;
    private final AuditService auditService;
    private final SecurityProperties properties;
    /** Poredi se sa lozinkom i kad korisnik ne postoji, da vreme odgovora ne otkriva postojanje naloga. */
    private final String dummyHash;

    public AuthService(PspUserRepository userRepository, MerchantRepository merchantRepository,
                       PasswordHistoryRepository historyRepository, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, TokenService tokenService, TotpService totpService,
                       QrCodeService qrCodeService, AesGcmEncryptor encryptor, AuditService auditService,
                       SecurityProperties properties) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenService = tokenService;
        this.totpService = totpService;
        this.qrCodeService = qrCodeService;
        this.encryptor = encryptor;
        this.auditService = auditService;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim();
        PspUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            audit(email, AuditAction.LOGIN_FAILED, null, AuditOutcome.FAILURE, "Nepoznat korisnik");
            throw invalidCredentials(null);
        }
        ensureNotLocked(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(user, AuditAction.LOGIN_FAILED, "Pogresna lozinka");
        }
        ensureAccountActive(user);
        LoginStage stage = nextStage(user);
        return LoginResponse.next(stage, user.getEmail(), tokenService.issuePreAuthToken(user, stage));
    }

    public LoginResponse changeFirstPassword(FirstPasswordRequest request) {
        PspUser user = loadForStage(request.preAuthToken(), LoginStage.PASSWORD_CHANGE);
        if (!user.isMustChangePassword()) {
            throw ApiException.conflict("PASSWORD_ALREADY_CHANGED", "Lozinka je vec promenjena");
        }
        passwordPolicy.validate(user, request.newPassword(), request.confirmPassword());
        applyNewPassword(user, request.newPassword());
        user.setMustChangePassword(false);
        audit(user.getEmail(), AuditAction.PASSWORD_CHANGED, user.getId(), AuditOutcome.SUCCESS, "Prva prijava");
        LoginStage stage = nextStage(user);
        return LoginResponse.next(stage, user.getEmail(), tokenService.issuePreAuthToken(user, stage));
    }

    public MfaSetupResponse startMfaSetup(PreAuthRequest request) {
        PspUser user = loadForStage(request.preAuthToken(), LoginStage.MFA_SETUP);
        if (user.isMfaEnabled()) {
            throw ApiException.conflict("MFA_ALREADY_ENABLED", "MFA je vec ukljucen");
        }
        String secret = totpService.generateSecret();
        user.setMfaSecretEncrypted(encryptor.encrypt(secret));
        user.setMfaLastUsedStep(null);
        String uri = totpService.otpauthUri(properties.mfaIssuer(), user.getEmail(), secret);
        return new MfaSetupResponse(secret, uri, qrCodeService.svgDataUrl(uri), properties.mfaIssuer());
    }

    public LoginResponse activateMfa(MfaCodeRequest request) {
        PspUser user = loadForStage(request.preAuthToken(), LoginStage.MFA_SETUP);
        if (user.getMfaSecretEncrypted() == null) {
            throw ApiException.badRequest("MFA_SETUP_NOT_STARTED", "Prvo skenirajte QR kod");
        }
        long step = verifyCode(user, request.code());
        user.setMfaEnabled(true);
        audit(user.getEmail(), AuditAction.MFA_ENROLLED, user.getId(), AuditOutcome.SUCCESS, "TOTP upareno");
        return completeLogin(user, step);
    }

    public LoginResponse verifyMfa(MfaCodeRequest request) {
        PspUser user = loadForStage(request.preAuthToken(), LoginStage.MFA_VERIFY);
        long step = verifyCode(user, request.code());
        return completeLogin(user, step);
    }

    /** Produzenje sesije dok je korisnik aktivan; posle 15 min neaktivnosti token istice. */
    public TokenResponse refresh() {
        PspUser user = currentUser();
        Instant issuedAt = RequestContext.jwt().getIssuedAt();
        if (user.getPasswordChangedAt() != null && issuedAt != null
                && issuedAt.isBefore(user.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "Sesija je opozvana");
        }
        ensureNotLocked(user);
        ensureAccountActive(user);
        TokenService.IssuedToken token = tokenService.issueAccessToken(user);
        return new TokenResponse(token.value(), token.expiresAt());
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return toResponse(currentUser());
    }

    /** Posle promene lozinke stari tokeni se vise ne mogu produziti, pa se vraca novi token. */
    public TokenResponse changePassword(ChangePasswordRequest request) {
        PspUser user = currentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            audit(user.getEmail(), AuditAction.PASSWORD_CHANGED, user.getId(), AuditOutcome.FAILURE,
                    "Pogresna trenutna lozinka");
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Trenutna lozinka nije ispravna");
        }
        passwordPolicy.validate(user, request.newPassword(), request.confirmPassword());
        applyNewPassword(user, request.newPassword());
        audit(user.getEmail(), AuditAction.PASSWORD_CHANGED, user.getId(), AuditOutcome.SUCCESS, null);
        TokenService.IssuedToken token = tokenService.issueAccessToken(user);
        return new TokenResponse(token.value(), token.expiresAt());
    }

    public void logout() {
        audit(RequestContext.userEmail(), AuditAction.LOGOUT, RequestContext.userId(), AuditOutcome.SUCCESS, null);
    }

    /** Postavlja novu lozinku i cuva je u istoriji (za proveru "razlicita od poslednje 4"). */
    public void applyNewPassword(PspUser user, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        user.setPasswordHash(hash);
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        historyRepository.save(new PasswordHistory(user.getId(), hash));
    }

    public UserResponse toResponse(PspUser user) {
        Merchant merchant = user.getMerchantId() == null ? null
                : merchantRepository.findById(user.getMerchantId()).orElse(null);
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getMerchantId(), merchant == null ? null : merchant.getName(),
                merchant == null ? null : merchant.getMerchantCode(), user.isMfaEnabled(),
                user.getLastLoginAt(), user.getPasswordChangedAt());
    }

    // ---------------------------------------------------------------------------------------------

    private LoginResponse completeLogin(PspUser user, long totpStep) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setMfaLastUsedStep(totpStep);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        audit(user.getEmail(), AuditAction.LOGIN, user.getId(), AuditOutcome.SUCCESS, "Lozinka + TOTP");
        return LoginResponse.done(tokenService.issueAccessToken(user), toResponse(user));
    }

    private long verifyCode(PspUser user, String code) {
        String secret = encryptor.decrypt(user.getMfaSecretEncrypted());
        OptionalLong step = totpService.verify(secret, code == null ? null : code.trim(), user.getMfaLastUsedStep());
        if (step.isEmpty()) {
            registerFailure(user, AuditAction.MFA_FAILED, "Neispravan TOTP kod");
        }
        return step.getAsLong();
    }

    private PspUser loadForStage(String preAuthToken, LoginStage stage) {
        UUID userId = tokenService.verifyPreAuthToken(preAuthToken, stage);
        PspUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_SESSION_EXPIRED",
                        "Sesija prijave je istekla. Prijavite se ponovo."));
        ensureNotLocked(user);
        ensureAccountActive(user);
        if (nextStage(user) != stage) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_SESSION_EXPIRED",
                    "Sesija prijave je istekla. Prijavite se ponovo.");
        }
        return user;
    }

    private LoginStage nextStage(PspUser user) {
        if (user.isMustChangePassword()) {
            return LoginStage.PASSWORD_CHANGE;
        }
        return user.isMfaEnabled() ? LoginStage.MFA_VERIFY : LoginStage.MFA_SETUP;
    }

    /** Broji neuspeh (lozinka ili MFA kod) i zakljucava nalog kada se dostigne limit (PCI DSS 8.3.4). */
    private void registerFailure(PspUser user, String action, String reason) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= properties.maxFailedAttempts()) {
            Instant lockedUntil = Instant.now().plus(properties.lockMinutes(), ChronoUnit.MINUTES);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(lockedUntil);
            userRepository.save(user);
            audit(user.getEmail(), action, user.getId(), AuditOutcome.FAILURE, reason);
            audit(user.getEmail(), AuditAction.ACCOUNT_LOCKED, user.getId(), AuditOutcome.FAILURE,
                    "Zakljucan na " + properties.lockMinutes() + " min");
            throw locked(lockedUntil);
        }
        user.setFailedLoginAttempts(attempts);
        userRepository.save(user);
        audit(user.getEmail(), action, user.getId(), AuditOutcome.FAILURE, reason);
        int remaining = properties.maxFailedAttempts() - attempts;
        if (AuditAction.MFA_FAILED.equals(action)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_MFA_CODE", "Kod nije ispravan ili je istekao.",
                    Map.of("remainingAttempts", remaining, "lockMinutes", properties.lockMinutes()));
        }
        throw invalidCredentials(remaining);
    }

    private ApiException invalidCredentials(Integer remaining) {
        Map<String, Object> details = remaining == null ? Map.of()
                : Map.of("remainingAttempts", remaining, "lockMinutes", properties.lockMinutes());
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Pogrešna e-pošta ili lozinka.",
                details);
    }

    private void ensureNotLocked(PspUser user) {
        if (user.isLocked(Instant.now())) {
            audit(user.getEmail(), AuditAction.LOGIN_FAILED, user.getId(), AuditOutcome.FAILURE, "Nalog zakljucan");
            throw locked(user.getLockedUntil());
        }
    }

    private ApiException locked(Instant until) {
        return new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Nalog je privremeno zaključan zbog previše neuspelih pokušaja.", Map.of("lockedUntil", until));
    }

    private void ensureAccountActive(PspUser user) {
        boolean merchantInactive = user.getMerchantId() != null && merchantRepository.findById(user.getMerchantId())
                .map(m -> m.getStatus() != MerchantStatus.ACTIVE).orElse(true);
        if (!user.isEnabled() || merchantInactive) {
            audit(user.getEmail(), AuditAction.LOGIN_FAILED, user.getId(), AuditOutcome.FAILURE, "Nalog deaktiviran");
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "Nalog je deaktiviran. Obratite se administratoru PSP-a.");
        }
    }

    private PspUser currentUser() {
        return userRepository.findById(RequestContext.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Niste prijavljeni"));
    }

    private void audit(String actor, String action, UUID userId, AuditOutcome outcome, String details) {
        auditService.record(AuditService.ACTOR_USER, actor, action, "PSP_USER",
                userId == null ? null : userId.toString(), outcome, details);
    }
}
