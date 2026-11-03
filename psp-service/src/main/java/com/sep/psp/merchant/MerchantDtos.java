package com.sep.psp.merchant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MerchantDtos {

    private MerchantDtos() {
    }

    // ---------- super admin ----------

    public record CreateMerchantRequest(
            @NotBlank(message = "Unesite naziv") @Size(max = 200) String name,
            @NotBlank(message = "Unesite e-poštu administratora") @Email(message = "Neispravna e-pošta")
            @Size(max = 254) String adminEmail,
            @Size(max = 200) String adminFullName,
            @NotBlank(message = "Unesite webhook URL") String webhookUrl,
            String successUrl,
            String failedUrl,
            String errorUrl,
            @NotNull @Min(value = 1, message = "Najmanje 1") @Max(value = 10000, message = "Najviše 10000")
            Integer rateLimitPerSec,
            @NotEmpty(message = "Izaberite bar jednu metodu plaćanja") List<String> methods) {
    }

    public record UpdateStatusRequest(@NotNull MerchantStatus status) {
    }

    public record UpdateRateLimitRequest(
            @NotNull @Min(value = 1, message = "Najmanje 1") @Max(value = 10000, message = "Najviše 10000")
            Integer rateLimitPerSec) {
    }

    public record MerchantSummary(
            UUID id,
            String name,
            String merchantCode,
            String adminEmail,
            List<String> activeMethods,
            int rateLimitPerSec,
            MerchantStatus status,
            boolean pendingFirstLogin,
            Instant createdAt) {
    }

    /** Tajne se prikazuju samo jednom - pri kreiranju ili resetu; posle toga samo maskirane. */
    public record MerchantCredentials(
            MerchantSummary merchant,
            String adminEmail,
            String temporaryPassword,
            String apiKey,
            String webhookSecret) {
    }

    // ---------- administrator klijenta ----------

    public record MerchantProfile(
            String name,
            String merchantCode,
            MerchantStatus status,
            int rateLimitPerSec,
            String apiKeyMasked,
            String webhookSecretMasked,
            String webhookUrl,
            String successUrl,
            String failedUrl,
            String errorUrl) {
    }

    public record UpdateUrlsRequest(
            @NotBlank(message = "Unesite webhook URL") String webhookUrl,
            String successUrl,
            String failedUrl,
            String errorUrl) {
    }

    public record RotatedSecret(String secret, String masked) {
    }
}
