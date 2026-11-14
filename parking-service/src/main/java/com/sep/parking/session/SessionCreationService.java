package com.sep.parking.session;

import com.sep.parking.common.exception.ApiException;
import com.sep.parking.session.SessionDtos.CreateSessionRequest;
import com.sep.parking.session.SessionDtos.QuoteResponse;
import com.sep.parking.session.SessionDtos.RuleCheck;
import com.sep.parking.transaction.OrderIdGenerator;
import com.sep.parking.transaction.PaymentTransaction;
import com.sep.parking.transaction.PaymentTransactionRepository;
import com.sep.parking.transaction.TransactionPurpose;
import com.sep.parking.transaction.TransactionStatus;
import com.sep.parking.user.UserRepository;
import com.sep.parking.vehicle.Vehicle;
import com.sep.parking.vehicle.VehicleRepository;
import com.sep.parking.zone.ParkingLocation;
import com.sep.parking.zone.ParkingLocationRepository;
import com.sep.parking.zone.ParkingZone;
import com.sep.parking.zone.ParkingZoneRepository;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kreira parking nalog (sesija u statusu PENDING_PAYMENT) i platnu transakciju u jednoj DB
 * transakciji. PSP se poziva tek posle commit-a (PaymentInitiationService), tako da nalog
 * postoji i ako PSP nije dostupan, a ponovljen zahtev ga samo ponovo salje PSP-u.
 */
@Service
public class SessionCreationService {

    private static final Logger log = LoggerFactory.getLogger(SessionCreationService.class);

    private final PaymentTransactionRepository transactions;
    private final ParkingSessionRepository sessions;
    private final VehicleRepository vehicles;
    private final ParkingZoneRepository zones;
    private final ParkingLocationRepository locations;
    private final UserRepository users;
    private final SessionRules rules;
    private final OrderIdGenerator orderIds;
    private final SessionAudit audit;
    private final Clock clock;

    public SessionCreationService(PaymentTransactionRepository transactions, ParkingSessionRepository sessions,
                                  VehicleRepository vehicles, ParkingZoneRepository zones,
                                  ParkingLocationRepository locations, UserRepository users, SessionRules rules,
                                  OrderIdGenerator orderIds, SessionAudit audit, Clock clock) {
        this.transactions = transactions;
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.zones = zones;
        this.locations = locations;
        this.users = users;
        this.rules = rules;
        this.orderIds = orderIds;
        this.audit = audit;
        this.clock = clock;
    }

    /** Vraca postojecu transakciju za isti requestId ili kreira novi nalog. */
    @Transactional
    public UUID createOrReuse(UUID userId, CreateSessionRequest request) {
        Optional<PaymentTransaction> existing = transactions.findByUserIdAndIdempotencyKey(userId, request.requestId());
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        // Zakljucavanje vozila serijalizuje paralelne zahteve za isto vozilo (dvostruki klik,
        // dva taba): drugi zahtev ceka da se prvi zavrsi i onda vidi njegov nalog.
        Vehicle vehicle = vehicles.lockActiveByIdAndUser(request.vehicleId(), userId)
                .orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "Vozilo nije pronađeno."));
        existing = transactions.findByUserIdAndIdempotencyKey(userId, request.requestId());
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        ParkingZone zone = zones.findWithTariffs(request.zoneId())
                .orElseThrow(() -> ApiException.notFound("ZONE_NOT_FOUND", "Zona nije pronađena."));
        ParkingLocation location = resolveLocation(zone, request.locationId());

        Instant now = clock.instant();
        QuoteResponse quote = rules.evaluate(zone, vehicle, request.locationId(), request.durationMinutes(), now);
        if (!quote.valid()) {
            RuleCheck failed = SessionRules.firstFailure(quote);
            throw ApiException.unprocessable("RULE_VIOLATION",
                    failed == null || failed.message() == null ? "Sesija nije dozvoljena po pravilima zone."
                            : failed.message());
        }
        // Cena koja je prikazana korisniku mora biti tacno iznos koji se salje na placanje.
        if (quote.amount().compareTo(request.expectedAmount()) != 0) {
            throw ApiException.conflict("PRICE_CHANGED",
                    "Cena je u međuvremenu promenjena na " + money(quote.amount()) + " " + quote.currency()
                            + ". Proverite obračun i potvrdite ponovo.");
        }

        ParkingSession session = new ParkingSession();
        session.setId(UUID.randomUUID());
        session.setUser(users.getReferenceById(userId));
        session.setVehicle(vehicle);
        session.setZone(zone);
        session.setLocation(location);
        session.setPlate(vehicle.getDisplayPlate());
        session.setStatus(SessionStatus.PENDING_PAYMENT);
        session.setDurationMinutes(request.durationMinutes());
        session.setStartsAt(quote.startsAt());
        session.setEndsAt(quote.endsAt());
        session.setAmount(quote.amount());
        session.setCurrency(quote.currency());
        session.setCreatedAt(now);
        sessions.save(session);

        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());
        tx.setSession(session);
        tx.setUserId(userId);
        tx.setMerchantOrderId(orderIds.next());
        tx.setPurpose(TransactionPurpose.PURCHASE);
        tx.setDurationMinutes(request.durationMinutes());
        tx.setAmount(quote.amount());
        tx.setCurrency(quote.currency());
        tx.setStatus(TransactionStatus.INIT);
        tx.setIdempotencyKey(request.requestId());
        tx.setCreatedAt(now);
        transactions.save(tx);

        audit.record(session, null, tx.getId(), "ORDER_CREATED",
                "Nalog " + tx.getMerchantOrderId() + " · " + money(tx.getAmount()) + " " + tx.getCurrency(),
                StatusActor.USER);
        log.info("Kreiran parking nalog {} (sesija {}, {} {})", tx.getMerchantOrderId(), session.getId(),
                tx.getAmount(), tx.getCurrency());
        return tx.getId();
    }

    private ParkingLocation resolveLocation(ParkingZone zone, Long locationId) {
        if (locationId == null) return null;
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> ApiException.notFound("LOCATION_NOT_FOUND", "Lokacija nije pronađena."));
        if (!location.getZone().getId().equals(zone.getId())) {
            throw ApiException.badRequest("LOCATION_NOT_IN_ZONE", "Lokacija ne pripada izabranoj zoni.");
        }
        return location;
    }

    static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0.00", symbols).format(amount);
    }
}
