package com.sep.psp.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Unesite e-poštu") @Email(message = "Neispravna e-pošta") @Size(max = 254) String email,
            @NotBlank(message = "Unesite lozinku") @Size(max = 128) String password) {
    }

    public record PreAuthRequest(@NotBlank String preAuthToken) {
    }

    public record FirstPasswordRequest(
            @NotBlank String preAuthToken,
            @NotBlank(message = "Unesite novu lozinku") String newPassword,
            @NotBlank(message = "Ponovite lozinku") String confirmPassword) {
    }

    public record MfaCodeRequest(
            @NotBlank String preAuthToken,
            @NotBlank(message = "Unesite kod") String code) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Unesite trenutnu lozinku") String currentPassword,
            @NotBlank(message = "Unesite novu lozinku") String newPassword,
            @NotBlank(message = "Ponovite lozinku") String confirmPassword) {
    }

    /**
     * Odgovor svakog koraka prijave. Dok step nije DONE, klijent dobija samo pre-auth token za sledeci korak.
     */
    public record LoginResponse(
            LoginStage step,
            String email,
            String preAuthToken,
            String accessToken,
            Instant expiresAt,
            UserResponse user) {

        static LoginResponse next(LoginStage step, String email, String preAuthToken) {
            return new LoginResponse(step, email, preAuthToken, null, null, null);
        }

        static LoginResponse done(TokenService.IssuedToken token, UserResponse user) {
            return new LoginResponse(LoginStage.DONE, user.email(), null, token.value(), token.expiresAt(), user);
        }
    }

    public record TokenResponse(String accessToken, Instant expiresAt) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri, String qrCode, String issuer) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            Role role,
            UUID merchantId,
            String merchantName,
            String merchantCode,
            boolean mfaEnabled,
            Instant lastLoginAt,
            Instant passwordChangedAt) {
    }
}
