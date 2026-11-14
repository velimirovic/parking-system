package com.sep.parking.session;

import com.sep.parking.session.SessionDtos.PaymentSummary;
import com.sep.parking.session.SessionDtos.SessionResponse;
import com.sep.parking.session.SessionDtos.StatusLogEntry;
import com.sep.parking.transaction.PaymentTransaction;
import com.sep.parking.transaction.PaymentTransactionRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Pretvara sesije u odgovor API-ja; transakcije i trag statusa ucitava grupno (bez N+1). */
@Component
public class SessionMapper {

    private final PaymentTransactionRepository transactions;
    private final SessionStatusLogRepository logs;

    public SessionMapper(PaymentTransactionRepository transactions, SessionStatusLogRepository logs) {
        this.transactions = transactions;
        this.logs = logs;
    }

    /** Mora se pozvati unutar transakcije (ucitava lenje veze). */
    public List<SessionResponse> map(Collection<ParkingSession> sessions, boolean withDetails) {
        if (sessions.isEmpty()) return List.of();
        List<UUID> ids = sessions.stream().map(ParkingSession::getId).toList();
        Map<UUID, List<PaymentTransaction>> txBySession = transactions.findBySessionIds(ids).stream()
                .collect(Collectors.groupingBy(t -> t.getSession().getId()));
        Map<UUID, List<SessionStatusLog>> logBySession = withDetails
                ? logs.findBySessionIdInOrderByOccurredAtAscIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(SessionStatusLog::getSessionId))
                : Map.of();
        return sessions.stream()
                .map(s -> toResponse(s, txBySession.getOrDefault(s.getId(), List.of()),
                        withDetails ? logBySession.getOrDefault(s.getId(), List.of()) : null))
                .toList();
    }

    public SessionResponse map(ParkingSession session, boolean withDetails) {
        return map(List.of(session), withDetails).get(0);
    }

    private SessionResponse toResponse(ParkingSession s, List<PaymentTransaction> txs, List<SessionStatusLog> log) {
        PaymentTransaction last = txs.stream().max(Comparator.comparing(PaymentTransaction::getCreatedAt)).orElse(null);
        return new SessionResponse(
                s.getId(), s.getStatus(), s.getVehicle().getId(), s.getPlate(), s.getVehicle().label(),
                s.getZone().getId(), s.getZone().getCode(), s.getZone().getName(),
                s.getLocation() == null ? null : s.getLocation().getName(),
                s.getDurationMinutes(), s.getStartsAt(), s.getEndsAt(), s.getAmount(), s.getCurrency(),
                s.getExtensionCount(), s.getZone().isExtensionAllowed(), s.getZone().getMaxExtensions(),
                s.getCreatedAt(), s.getActivatedAt(), s.getClosedAt(),
                last == null ? null : payment(last),
                log == null ? null : txs.stream().map(SessionMapper::payment).toList(),
                log == null ? null : log.stream().map(SessionMapper::logEntry).toList());
    }

    public static PaymentSummary payment(PaymentTransaction t) {
        return new PaymentSummary(t.getId(), t.getMerchantOrderId(), t.getPurpose(), t.getStatus(), t.getAmount(),
                t.getCurrency(), t.getDurationMinutes(), t.getPaymentMethod(), t.getGlobalTransactionId(),
                t.getPaymentExpiresAt(), t.getStatus().isOpen() ? t.getRedirectUrl() : null, t.getCreatedAt(),
                t.getConfirmedAt(), t.getRefundStatus(), t.getRefundReason());
    }

    private static StatusLogEntry logEntry(SessionStatusLog l) {
        return new StatusLogEntry(l.getOccurredAt(), l.getFromStatus(), l.getToStatus(), l.getEvent(), l.getNote(),
                l.getActor());
    }
}
