package com.sep.psp.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.sep.psp.common.exception.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * Politika lozinki (PCI DSS 8.3.6 i 8.3.7): najmanje 12 karaktera, slova, brojevi i simboli,
 * razlicita od poslednje 4 lozinke.
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int HISTORY_SIZE = 4;

    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryRepository historyRepository;

    public void validate(PspUser user, String newPassword, String confirmation) {
        if (newPassword == null || !newPassword.equals(confirmation)) {
            throw ApiException.badRequest("PASSWORD_MISMATCH", "Lozinke se ne poklapaju");
        }
        List<String> violations = new ArrayList<>();
        if (newPassword.length() < MIN_LENGTH) {
            violations.add("Najmanje " + MIN_LENGTH + " karaktera");
        }
        if (newPassword.length() > 128) {
            violations.add("Najvise 128 karaktera");
        }
        boolean letters = newPassword.chars().anyMatch(Character::isLetter);
        boolean digits = newPassword.chars().anyMatch(Character::isDigit);
        boolean symbols = newPassword.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
        if (!letters || !digits || !symbols) {
            violations.add("Slova, brojevi i simboli");
        }
        String username = user.getEmail().split("@")[0].toLowerCase();
        if (username.length() >= 3 && newPassword.toLowerCase().contains(username)) {
            violations.add("Ne sme sadrzati korisnicko ime");
        }
        if (!violations.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "Lozinka ne ispunjava pravila",
                    java.util.Map.of("violations", violations));
        }
        boolean reused = passwordEncoder.matches(newPassword, user.getPasswordHash())
                || historyRepository.findTop4ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                        .anyMatch(h -> passwordEncoder.matches(newPassword, h.getPasswordHash()));
        if (reused) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "Lozinka ne ispunjava pravila",
                    java.util.Map.of("violations", List.of("Razlicita od poslednje " + HISTORY_SIZE)));
        }
    }
}
