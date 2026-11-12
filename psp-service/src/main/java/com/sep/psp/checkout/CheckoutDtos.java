package com.sep.psp.checkout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Podaci za checkout stranicu PSP-a (javna, bez prijave - pristup je vezan za nepogodiv ID transakcije).
 * Namerno ne sadrze nista o klijentu osim naziva, niti tajne i konfiguraciju metoda.
 */
public final class CheckoutDtos {

    private CheckoutDtos() {
    }

    public record CheckoutView(
            String id,
            String reference,
            String merchantName,
            String merchantHost,
            String merchantOrderId,
            BigDecimal amount,
            String currency,
            String description,
            String status,
            String paymentMethod,
            Instant expiresAt,
            Instant serverTime,
            List<MethodOption> methods,
            /* adresa za nastavak placanja kada je metoda vec izabrana (PENDING) */
            String continueUrl,
            /* adresa klijenta za povratak kupca kada je transakcija zavrsena */
            String returnUrl) {
    }

    public record MethodOption(String code, String displayName) {
    }

    public record SelectMethodRequest(
            @NotBlank(message = "Izaberite način plaćanja")
            @Pattern(regexp = "[A-Z0-9_]{2,32}", message = "Neispravna metoda plaćanja")
            String code) {
    }

    public record SelectMethodResponse(String redirectUrl) {
    }
}
