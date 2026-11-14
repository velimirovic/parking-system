package com.sep.parking.session;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Upisuje promene statusa sesije u trag (u istoj DB transakciji kao i sama promena). */
@Component
public class SessionAudit {

    private final SessionStatusLogRepository logs;
    private final Clock clock;

    public SessionAudit(SessionStatusLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    public void record(ParkingSession session, SessionStatus from, UUID transactionId, String event,
                       String note, StatusActor actor) {
        SessionStatusLog entry = new SessionStatusLog();
        entry.setSessionId(session.getId());
        entry.setTransactionId(transactionId);
        entry.setFromStatus(from == null ? null : from.name());
        entry.setToStatus(session.getStatus().name());
        entry.setEvent(event);
        entry.setNote(note);
        entry.setActor(actor);
        entry.setOccurredAt(clock.instant());
        logs.save(entry);
    }
}
