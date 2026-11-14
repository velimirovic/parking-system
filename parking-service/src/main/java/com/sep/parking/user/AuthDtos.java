package com.sep.parking.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Ime je obavezno.") @Size(max = 60, message = "Ime je predugačko.") String firstName,
            @NotBlank(message = "Prezime je obavezno.") @Size(max = 60, message = "Prezime je predugačko.") String lastName,
            @NotBlank(message = "E-pošta je obavezna.") @Email(message = "Neispravna e-pošta.") @Size(max = 254) String email,
            @Pattern(regexp = "^$|^\\+?[0-9 ()/-]{6,25}$", message = "Neispravan broj telefona.") String phone,
            @NotBlank(message = "Lozinka je obavezna.") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "E-pošta je obavezna.") String email,
            @NotBlank(message = "Lozinka je obavezna.") String password) {
    }

    public record PreferencesRequest(
            @Min(value = 1, message = "Najmanje 1 minut.") @Max(value = 60, message = "Najviše 60 minuta.") int notifyBeforeMinutes) {
    }

    public record UserResponse(UUID id, String email, String firstName, String lastName, String phone,
                               String initials, int notifyBeforeMinutes) {

        static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                    user.getPhone(), user.initials().toUpperCase(), user.getNotifyBeforeMinutes());
        }
    }

    public record AuthResponse(String accessToken, Instant expiresAt, UserResponse user) {
    }
}
