package com.sep.psp.paymentmethod;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PaymentMethodDtos {

    private PaymentMethodDtos() {
    }

    // ---------- administrator klijenta ----------

    public record MerchantMethodView(
            String code,
            String displayName,
            boolean subscribed,
            boolean active,
            boolean availableOnPsp,
            boolean supportsRefund,
            boolean lastActive,
            List<ConfigEntry> config) {
    }

    /** Vrednosti konfiguracije se vracaju samo maskirane. */
    public record ConfigEntry(String key, String maskedValue) {
    }

    public record MerchantMethodsResponse(List<MerchantMethodView> methods, long activeCount) {
    }

    public record ToggleMethodRequest(@NotNull Boolean active) {
    }

    public record UpdateConfigRequest(@NotNull @Size(max = 10) Map<String, String> values) {
    }

    // ---------- super admin ----------

    public record PluginView(
            UUID id,
            String code,
            String displayName,
            String pluginBaseUrl,
            boolean supportsRefund,
            boolean active,
            long subscribedMerchants,
            PluginHealthChecker.Health health,
            Instant createdAt) {
    }

    public record RegisterPluginRequest(
            @NotBlank(message = "Unesite kod") @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,31}",
                    message = "Kod: slova, brojevi i _ (2-32 znaka)") String code,
            @NotBlank(message = "Unesite naziv") @Size(max = 100) String displayName,
            @NotBlank(message = "Unesite adresu plugin servisa") @Size(max = 500) String pluginBaseUrl,
            boolean supportsRefund) {
    }

    public record UpdatePluginRequest(
            @Size(max = 100) String displayName,
            @Size(max = 500) String pluginBaseUrl,
            Boolean supportsRefund,
            Boolean active) {
    }

    public record SystemStatus(
            String instanceId,
            Instant startedAt,
            String database,
            long pendingWebhooks,
            long activeMerchants,
            long totalMerchants) {
    }
}
