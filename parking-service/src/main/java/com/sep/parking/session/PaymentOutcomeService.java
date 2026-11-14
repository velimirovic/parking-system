package com.sep.parking.session;

import com.sep.parking.transaction.PaymentTransaction;
import com.sep.parking.transaction.PaymentTransactionRepository;
import com.sep.parking.transaction.RefundStatus;
import com.sep.parking.transaction.TransactionPurpose;
import com.sep.parking.transaction.TransactionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jedino mesto gde se konacan ishod placanja primenjuje na parking sesiju - koriste ga webhook PSP-a,
 * periodicna provera statusa i provera na zahtev korisnika.
 *
 * <p>Pravila:
 * <ul>
 *   <li>sesija se aktivira iskljucivo posle potvrde uspesnog placanja, i to samo ako se iznos i valuta
 *       poklapaju sa nalogom;</li>
 *   <li>placeno vreme tece od trenutka odobrenja placanja (ACQUIRER_TIMESTAMP), a ne od kreiranja naloga;</li>
 *   <li>neuspesno, isteklo ili pogresno placanje nikad ne aktivira sesiju;</li>
 *   <li>uspesno placanje koje ne moze da aktivira sesiju (sesija je vec otkazana, vozilo je u medjuvremenu
 *       dobilo drugu aktivnu sesiju, iznos se ne poklapa) oznacava se za refundaciju;</li>
 *   <li>prvi konacan status pobedjuje; ponovljena potvrda istog ishoda se ignorise (idempotentnost).</li>
 * </ul>
 * Red transakcije se zakljucava, pa webhook i provera statusa ne mogu istovremeno obraditi isti nalog.
 */
@Service
public class PaymentOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeService.class);

    private final PaymentTransactionRepository transactions;
    private final ParkingSessionRepository sessions;
    private final SessionAudit audit;
    private final Clock clock;

    public PaymentOutcomeService(PaymentTransactionRepository transactions, ParkingSessionRepository sessions,
                                 SessionAudit audit, Clock clock) {
        this.transactions = transactions;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * @param actor  ko javlja ishod: PSP (webhook) ili SYSTEM (provera statusa)
     * @param source kratak opis izvora za trag ("webhook", "provera statusa")
     */
    @Transactional
    public PaymentOutcome apply(String merchantOrderId, PaymentResult result, StatusActor actor, String source) {
        PaymentTransaction tx = transactions.lockByMerchantOrderId(merchantOrderId).orElse(null);
        if (tx == null) {
            log.warn("Ishod placanja za nepoznat nalog {} ({})", merchantOrderId, source);
            return PaymentOutcome.UNKNOWN_ORDER;
        }
        ParkingSession session = tx.getSession();
        Instant now = clock.instant();
        fillDetails(tx, result);

        // ---------- konacan status je vec upisan ----------
        if (!tx.getStatus().isOpen()) {
            if (result.status() == TransactionStatus.SUCCESS && tx.getStatus() != TransactionStatus.SUCCESS
                    && tx.getRefundStatus() == null) {
                // npr. nalog je oznacen kao istekao, a PSP kasnije javi uspesnu naplatu
                log.warn("Kasna potvrda uspesnog placanja za {} (status je bio {})", merchantOrderId, tx.getStatus());
                tx.setStatus(TransactionStatus.SUCCESS);
                tx.setConfirmedAt(confirmationTime(tx, result, now));
                requestRefund(tx, session, "Uplata potvrđena posle zatvaranja naloga", actor, source);
                return PaymentOutcome.REFUND_REQUIRED;
            }
            return PaymentOutcome.DUPLICATE;
        }
        if (result.status().isOpen()) {
            return PaymentOutcome.STILL_OPEN;
        }

        // ---------- iznos i valuta moraju odgovarati nalogu ----------
        boolean mismatch = result.amount() != null && (result.amount().compareTo(tx.getAmount()) != 0
                || result.currency() == null || !tx.getCurrency().equalsIgnoreCase(result.currency()));
        if (mismatch) {
            log.error("PSP je javio {} {} za nalog {} ({} {}) - sesija se ne aktivira", result.amount(),
                    result.currency(), merchantOrderId, tx.getAmount(), tx.getCurrency());
            boolean charged = result.status() == TransactionStatus.SUCCESS;
            tx.setStatus(TransactionStatus.ERROR);
            tx.setFailureReason("Iznos ili valuta se ne poklapaju sa nalogom");
            cancel(session, tx, "PAYMENT_AMOUNT_MISMATCH",
                    "PSP je javio " + result.amount() + " " + result.currency() + " · " + source, actor, now);
            if (charged) {
                requestRefund(tx, session, "Naplaćen iznos se ne poklapa sa nalogom", actor, source);
                return PaymentOutcome.REFUND_REQUIRED;
            }
            return PaymentOutcome.AMOUNT_MISMATCH;
        }

        // ---------- neuspesno / isteklo / greska ----------
        if (result.status() != TransactionStatus.SUCCESS) {
            tx.setStatus(result.status());
            tx.setFailureReason(failureText(result.status()));
            cancel(session, tx, "PAYMENT_" + result.status().name(), failureText(result.status()) + " · " + source,
                    actor, now);
            log.info("Nalog {} zavrsen bez placanja ({})", merchantOrderId, result.status());
            return PaymentOutcome.CANCELLED;
        }

        // ---------- uspesno placanje ----------
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setConfirmedAt(confirmationTime(tx, result, now));
        if (tx.getPurpose() == TransactionPurpose.EXTENSION) {
            // produzenje (novo vreme isteka) primenjuje modul produzenja
            audit.record(session, session.getStatus(), tx.getId(), "EXTENSION_PAYMENT_CONFIRMED",
                    "Plaćanje produženja potvrđeno · " + source, actor);
            return PaymentOutcome.EXTENSION_RECORDED;
        }
        if (session.getStatus() != SessionStatus.PENDING_PAYMENT) {
            requestRefund(tx, session, "Sesija je već zatvorena (" + session.getStatus() + ")", actor, source);
            return PaymentOutcome.REFUND_REQUIRED;
        }
        if (sessions.existsOtherActive(session.getVehicle().getId(), session.getId(), now, SessionStatus.ACTIVE)) {
            cancel(session, tx, "ACTIVATION_CONFLICT", "Vozilo već ima drugu aktivnu sesiju", StatusActor.SYSTEM, now);
            requestRefund(tx, session, "Vozilo već ima drugu aktivnu sesiju", actor, source);
            return PaymentOutcome.REFUND_REQUIRED;
        }

        // placeno vreme tece od trenutka odobrenja placanja
        Instant start = tx.getConfirmedAt().truncatedTo(ChronoUnit.SECONDS);
        SessionStatus from = session.getStatus();
        session.setStartsAt(start);
        session.setEndsAt(start.plus(session.getDurationMinutes(), ChronoUnit.MINUTES));
        session.setStatus(SessionStatus.ACTIVE);
        session.setActivatedAt(now);
        audit.record(session, from, tx.getId(), "SESSION_ACTIVATED",
                "Plaćanje potvrđeno" + (tx.getPaymentMethod() == null ? "" : " (" + tx.getPaymentMethod() + ")")
                        + (tx.getGlobalTransactionId() == null ? "" : " · " + tx.getGlobalTransactionId())
                        + " · " + source,
                actor);
        log.info("Aktivirana sesija {} (nalog {}) do {}", session.getId(), merchantOrderId, session.getEndsAt());
        return PaymentOutcome.ACTIVATED;
    }

    /** Podaci koje PSP salje uz ishod (ne menjaju iznos ni status). */
    private static void fillDetails(PaymentTransaction tx, PaymentResult r) {
        if (r.pspTransactionId() != null && tx.getPspTransactionId() == null) tx.setPspTransactionId(r.pspTransactionId());
        if (r.paymentMethod() != null) tx.setPaymentMethod(r.paymentMethod());
        if (r.globalTransactionId() != null) tx.setGlobalTransactionId(r.globalTransactionId());
        if (r.acquirerTimestamp() != null) tx.setAcquirerTimestamp(r.acquirerTimestamp());
    }

    /**
     * Trenutak odobrenja: ACQUIRER_TIMESTAMP ako postoji, inace trenutak prijema potvrde. Nikad pre
     * kreiranja naloga ni posle trenutnog vremena (zastita od pogresnog sata na drugoj strani).
     */
    static Instant confirmationTime(PaymentTransaction tx, PaymentResult r, Instant now) {
        Instant t = r.acquirerTimestamp() != null ? r.acquirerTimestamp() : now;
        if (t.isBefore(tx.getCreatedAt())) t = tx.getCreatedAt();
        if (t.isAfter(now)) t = now;
        return t;
    }

    private void cancel(ParkingSession session, PaymentTransaction tx, String event, String note, StatusActor actor,
                        Instant now) {
        if (session.getStatus() != SessionStatus.PENDING_PAYMENT) return;
        session.setStatus(SessionStatus.CANCELLED);
        session.setClosedAt(now);
        audit.record(session, SessionStatus.PENDING_PAYMENT, tx.getId(), event, note, actor);
    }

    private void requestRefund(PaymentTransaction tx, ParkingSession session, String reason, StatusActor actor,
                               String source) {
        tx.setRefundStatus(RefundStatus.REQUIRED);
        tx.setRefundReason(reason.length() > 200 ? reason.substring(0, 200) : reason);
        tx.setRefundRequestedAt(clock.instant());
        audit.record(session, session.getStatus(), tx.getId(), "REFUND_REQUIRED",
                reason + " — iznos se vraća istim načinom plaćanja · " + source, actor);
        log.warn("Nalog {} oznacen za refundaciju: {}", tx.getMerchantOrderId(), reason);
    }

    private static String failureText(TransactionStatus status) {
        return switch (status) {
            case FAILED -> "Plaćanje odbijeno";
            case EXPIRED -> "Plaćanje nije završeno na vreme";
            case ERROR -> "Greška u obradi plaćanja";
            default -> "Plaćanje nije uspelo";
        };
    }
}
