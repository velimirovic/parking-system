package com.sep.parking.user;

import com.sep.parking.common.config.SecurityProperties;
import com.sep.parking.common.exception.ApiException;
import com.sep.parking.user.AuthDtos.AuthResponse;
import com.sep.parking.user.AuthDtos.LoginRequest;
import com.sep.parking.user.AuthDtos.RegisterRequest;
import com.sep.parking.user.AuthDtos.UserResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String BAD_CREDENTIALS = "Pogrešna e-pošta ili lozinka.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final SecurityProperties properties;
    private final Clock clock;
    /** Hash koji se proverava kada korisnik ne postoji - isto vreme odgovora (nema otkrivanja naloga). */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       SecurityProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        PasswordPolicy.validate(request.password());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Nalog sa ovom e-poštom već postoji.");
        }
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        Instant now = clock.instant();
        user.setCreatedAt(now);
        user.setLastLoginAt(now);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "Nalog sa ovom e-poštom već postoji.");
        }
        log.info("Registrovan korisnik {}", user.getId());
        return response(user);
    }

    /**
     * Posle {@code maxFailedAttempts} uzastopnih neuspesnih pokusaja nalog se zakljucava na
     * {@code lockMinutes} minuta. noRollbackFor - brojac neuspesnih pokusaja mora ostati upisan.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw ApiException.unauthorized("BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Nalog je privremeno zaključan zbog više neuspešnih pokušaja. Pokušajte ponovo za " + minutes + " min.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= properties.maxFailedAttempts()) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(now.plus(Duration.ofMinutes(properties.lockMinutes())));
                log.warn("Nalog {} zakljucan posle {} neuspesnih prijava", user.getId(), attempts);
            } else {
                user.setFailedLoginAttempts(attempts);
            }
            throw ApiException.unauthorized("BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return response(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.of(find(userId));
    }

    @Transactional
    public UserResponse updatePreferences(UUID userId, int notifyBeforeMinutes) {
        User user = find(userId);
        user.setNotifyBeforeMinutes(notifyBeforeMinutes);
        return UserResponse.of(user);
    }

    private User find(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "Korisnik ne postoji."));
    }

    private AuthResponse response(User user) {
        TokenService.IssuedToken token = tokens.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.of(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
