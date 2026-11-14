package com.sep.parking.session;

import com.sep.parking.common.exception.ApiException;
import com.sep.parking.session.SessionDtos.CreateSessionRequest;
import com.sep.parking.session.SessionDtos.CreateSessionResponse;
import com.sep.parking.session.SessionDtos.OrderResponse;
import com.sep.parking.session.SessionDtos.QuoteRequest;
import com.sep.parking.session.SessionDtos.QuoteResponse;
import com.sep.parking.session.SessionDtos.SessionResponse;
import com.sep.parking.transaction.PaymentTransaction;
import com.sep.parking.transaction.PaymentTransactionRepository;
import com.sep.parking.vehicle.Vehicle;
import com.sep.parking.vehicle.VehicleRepository;
import com.sep.parking.zone.ParkingZone;
import com.sep.parking.zone.ParkingZoneRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    /** Koliko dugo se nalog koji ceka placanje prikazuje na pregledu. */
    private static final Duration PENDING_VISIBLE = Duration.ofHours(24);

    private final ParkingZoneRepository zones;
    private final VehicleRepository vehicles;
    private final ParkingSessionRepository sessions;
    private final PaymentTransactionRepository transactions;
    private final SessionRules rules;
    private final SessionCreationService creation;
    private final PaymentInitiationService initiation;
    private final SessionMapper mapper;
    private final Clock clock;

    public SessionService(ParkingZoneRepository zones, VehicleRepository vehicles, ParkingSessionRepository sessions,
                          PaymentTransactionRepository transactions, SessionRules rules,
                          SessionCreationService creation, PaymentInitiationService initiation,
                          SessionMapper mapper, Clock clock) {
        this.zones = zones;
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.transactions = transactions;
        this.rules = rules;
        this.creation = creation;
        this.initiation = initiation;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Obracun cene i provera pravila pre pokretanja placanja (ne kreira nista). */
    @Transactional(readOnly = true)
    public QuoteResponse quote(UUID userId, QuoteRequest request) {
        ParkingZone zone = zones.findWithTariffs(request.zoneId())
                .orElseThrow(() -> ApiException.notFound("ZONE_NOT_FOUND", "Zona nije pronađena."));
        Vehicle vehicle = request.vehicleId() == null ? null
                : vehicles.findActiveByIdAndUser(request.vehicleId(), userId)
                        .orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "Vozilo nije pronađeno."));
        return rules.evaluate(zone, vehicle, request.locationId(), request.durationMinutes(), clock.instant());
    }

    /**
     * Kreira parking nalog i inicijalizuje transakciju na PSP-u. Vraca redirect_url na checkout.
     * Sesija ostaje PENDING_PAYMENT dok PSP ne potvrdi uspesno placanje.
     */
    public CreateSessionResponse create(UUID userId, CreateSessionRequest request) {
        UUID transactionId;
        try {
            transactionId = creation.createOrReuse(userId, request);
        } catch (DataIntegrityViolationException e) {
            // paralelan zahtev sa istim requestId je upravo upisao nalog
            transactionId = transactions.findByUserIdAndIdempotencyKey(userId, request.requestId())
                    .map(PaymentTransaction::getId)
                    .orElseThrow(() -> e);
        }
        return initiation.initiate(transactionId);
    }

    /** Aktivne sesije i nalozi koji cekaju potvrdu placanja (pregled). */
    @Transactional(readOnly = true)
    public List<SessionResponse> current(UUID userId) {
        Instant now = clock.instant();
        List<ParkingSession> list = sessions.findCurrent(userId, now, now.minus(PENDING_VISIBLE),
                SessionStatus.ACTIVE, SessionStatus.PENDING_PAYMENT);
        List<ParkingSession> sorted = list.stream()
                .sorted(Comparator.comparing((ParkingSession s) -> s.getStatus() == SessionStatus.ACTIVE ? 0 : 1)
                        .thenComparing(ParkingSession::getEndsAt))
                .toList();
        return mapper.map(sorted, false);
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID userId, UUID sessionId) {
        ParkingSession session = sessions.findForUser(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Sesija nije pronađena."));
        return mapper.map(session, true);
    }

    /** Podaci za stranicu statusa placanja (povratak sa PSP-a na SUCCESS/FAILED/ERROR URL). */
    @Transactional(readOnly = true)
    public OrderResponse order(UUID userId, String merchantOrderId) {
        PaymentTransaction t = transactions.findByMerchantOrderId(merchantOrderId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Nalog nije pronađen."));
        ParkingSession session = sessions.findForUser(t.getSession().getId(), userId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Nalog nije pronađen."));
        return new OrderResponse(t.getMerchantOrderId(), t.getPurpose(), t.getStatus(), t.getAmount(),
                t.getCurrency(), t.getPaymentMethod(), t.getCreatedAt(), mapper.map(session, true));
    }
}
