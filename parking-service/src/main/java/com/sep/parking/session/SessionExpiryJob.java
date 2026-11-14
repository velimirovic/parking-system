package com.sep.parking.session;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aktivne sesije kojima je proslo vreme isteka prelaze u EXPIRED. Svaka sesija se obradjuje u
 * svojoj transakciji sa optimistickim zakljucavanjem (@Version), pa paralelna izmena (npr.
 * produzenje) ne moze biti pregazena.
 */
@Component
public class SessionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SessionExpiryJob.class);

    private final ParkingSessionRepository sessions;
    private final SessionAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SessionExpiryJob(ParkingSessionRepository sessions, SessionAudit audit, TransactionTemplate tx,
                            Clock clock) {
        this.sessions = sessions;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.sessions.expiry-check-millis:30000}",
            initialDelayString = "${app.sessions.expiry-check-millis:30000}")
    public void expireDueSessions() {
        Instant now = clock.instant();
        List<UUID> due = sessions.findIdsDue(SessionStatus.ACTIVE, now);
        for (UUID id : due) {
            try {
                tx.executeWithoutResult(s -> sessions.findById(id).ifPresent(session -> {
                    if (session.getStatus() == SessionStatus.ACTIVE && !session.getEndsAt().isAfter(clock.instant())) {
                        session.setStatus(SessionStatus.EXPIRED);
                        session.setClosedAt(session.getEndsAt());
                        audit.record(session, SessionStatus.ACTIVE, null, "SESSION_EXPIRED",
                                "Isteklo plaćeno vreme parkiranja", StatusActor.SYSTEM);
                    }
                }));
            } catch (RuntimeException e) {
                log.warn("Sesija {} nije oznacena kao istekla (pokusace se ponovo): {}", id, e.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("Isteklo {} parking sesija", due.size());
        }
    }
}
