package com.sep.parking.session;

import com.sep.parking.transaction.TransactionPurpose;
import com.sep.parking.transaction.TransactionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SessionDtos {

    private SessionDtos() {
    }

    // ---------- obracun ----------

    public record QuoteRequest(
            @NotNull(message = "Izaberite zonu.") Long zoneId,
            UUID vehicleId,
            Long locationId,
            @NotNull(message = "Izaberite trajanje.") @Min(value = 1, message = "Neispravno trajanje.")
            @Max(value = 1440, message = "Neispravno trajanje.") Integer durationMinutes) {
    }

    public enum CheckState { OK, FAIL, PENDING }

    /** Jedno pravilo zone i da li je ispunjeno (prikazuje se u obracunu). */
    public record RuleCheck(String code, String label, CheckState state, String message) {
    }

    public record DurationOption(int minutes, boolean available) {
    }

    /**
     * @param amount              iznos koji ce biti poslat na placanje (null ako sesija nije dozvoljena)
     * @param maxAvailableMinutes najduze trajanje koje se trenutno moze kupiti u zoni
     */
    public record QuoteResponse(Long zoneId, String zoneName, UUID vehicleId, String plate, Long locationId,
                                int durationMinutes, Instant startsAt, Instant endsAt, BigDecimal ratePerHour,
                                BigDecimal amount, String currency, boolean valid, int maxAvailableMinutes,
                                boolean chargingNow, Instant chargingEndsAt, List<RuleCheck> checks,
                                List<DurationOption> durations) {
    }

    // ---------- kreiranje naloga ----------

    /**
     * @param expectedAmount iznos koji je korisnik video u obracunu - mora se poklopiti sa iznosom
     *                       koji server izracuna, inace se nalog ne kreira (PRICE_CHANGED)
     * @param requestId      jedinstven ID zahteva sa forme; ponovljen zahtev (dvostruki klik,
     *                       ponovni pokusaj) vraca isti nalog umesto da kreira novi
     */
    public record CreateSessionRequest(
            @NotNull(message = "Izaberite zonu.") Long zoneId,
            @NotNull(message = "Izaberite vozilo.") UUID vehicleId,
            Long locationId,
            @NotNull(message = "Izaberite trajanje.") @Min(value = 1, message = "Neispravno trajanje.")
            @Max(value = 1440, message = "Neispravno trajanje.") Integer durationMinutes,
            @NotNull(message = "Nedostaje obračunati iznos.") @DecimalMin(value = "0.01") BigDecimal expectedAmount,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$", message = "Neispravan ID zahteva.") String requestId) {
    }

    public record CreateSessionResponse(UUID sessionId, String merchantOrderId, SessionStatus sessionStatus,
                                        TransactionStatus paymentStatus, BigDecimal amount, String currency,
                                        String redirectUrl, Instant paymentExpiresAt) {
    }

    // ---------- prikaz ----------

    public record PaymentSummary(UUID id, String merchantOrderId, TransactionPurpose purpose,
                                 TransactionStatus status, BigDecimal amount, String currency,
                                 int durationMinutes, String paymentMethod, String globalTransactionId,
                                 Instant paymentExpiresAt, String redirectUrl, Instant createdAt) {
    }

    public record StatusLogEntry(Instant occurredAt, String fromStatus, String toStatus, String event,
                                 String note, StatusActor actor) {
    }

    public record SessionResponse(UUID id, SessionStatus status, UUID vehicleId, String plate, String vehicleLabel,
                                  Long zoneId, String zoneCode, String zoneName, String locationName,
                                  int durationMinutes, Instant startsAt, Instant endsAt, BigDecimal amount,
                                  String currency, int extensionCount, boolean extensionAllowed,
                                  Integer maxExtensions, Instant createdAt, Instant activatedAt,
                                  Instant closedAt, PaymentSummary lastPayment,
                                  List<PaymentSummary> payments, List<StatusLogEntry> statusLog) {
    }

    /** Stranica statusa placanja (SUCCESS_URL / FAILED_URL / ERROR_URL). */
    public record OrderResponse(String merchantOrderId, TransactionPurpose purpose, TransactionStatus paymentStatus,
                                BigDecimal amount, String currency, String paymentMethod, Instant createdAt,
                                SessionResponse session) {
    }
}
