package com.sep.parking.session;

import com.sep.parking.payment.PspClient;
import com.sep.parking.payment.PspException;
import com.sep.parking.payment.PspTransaction;
import com.sep.parking.transaction.PaymentTransaction;
import com.sep.parking.transaction.PaymentTransactionRepository;
import com.sep.parking.transaction.TransactionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Provera konacnog statusa naloga na PSP-u (GET /transactions/{merchant_order_id}) - parking sistem
 * ne zavisi samo od webhook-a ni od povratka korisnika (specijalni slucajevi 2, 3 i 5).
 * Poziv PSP-u je van DB transakcije; rezultat se primenjuje kroz {@link PaymentOutcomeService}.
 */
@Service
public class PaymentStatusSync {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusSync.class);

    /** Nalog koji PSP ne poznaje (nikad nije primio inicijalizaciju) zatvara se posle ovoliko vremena. */
    static final Duration UNKNOWN_AT_PSP_TTL = Duration.ofMinutes(15);

    private final PaymentTransactionRepository transactions;
    private final PaymentOutcomeService outcomes;
    private final PspClient psp;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentStatusSync(PaymentTransactionRepository transactions, PaymentOutcomeService outcomes,
                             PspClient psp, TransactionTemplate tx, Clock clock) {
        this.transactions = transactions;
        this.outcomes = outcomes;
        this.psp = psp;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Proverava jedan nalog. Vraca ishod, ili {@code null} ako PSP trenutno nije dostupan
     * (pokusava se ponovo pri sledecoj proveri).
     */
    public PaymentOutcome sync(String merchantOrderId, StatusActor actor, String source) {
        PaymentTransaction current = transactions.findByMerchantOrderId(merchantOrderId).orElse(null);
        if (current == null) return PaymentOutcome.UNKNOWN_ORDER;
        if (!current.getStatus().isOpen()) return PaymentOutcome.DUPLICATE;

        PaymentOutcome outcome;
        try {
            PspTransaction remote = psp.getTransaction(merchantOrderId);
            outcome = outcomes.apply(merchantOrderId, toResult(remote), actor, source);
        } catch (PspException.Rejected e) {
            outcome = e.status() == 404 ? unknownAtPsp(current, actor, source) : null;
            if (e.status() != 404) {
                log.warn("PSP je odbio proveru statusa za {}: {} {}", merchantOrderId, e.status(), e.code());
            }
        } catch (PspException.Unavailable e) {
            log.info("Provera statusa za {} odlozena - PSP nije dostupan", merchantOrderId);
            outcome = null;
        }
        markChecked(merchantOrderId);
        return outcome;
    }

    /**
     * PSP ne poznaje nalog: inicijalizacija nikad nije stigla do PSP-a (npr. PSP nije bio dostupan, a
     * korisnik nije ponovio pokusaj). Posle roka checkout-a nalog se zatvara bez naplate.
     */
    private PaymentOutcome unknownAtPsp(PaymentTransaction current, StatusActor actor, String source) {
        Instant now = clock.instant();
        if (current.getCreatedAt().plus(UNKNOWN_AT_PSP_TTL).isAfter(now)) {
            return PaymentOutcome.STILL_OPEN;
        }
        PaymentResult expired = new PaymentResult(TransactionStatus.EXPIRED, null, null, null, null, null, null);
        return outcomes.apply(current.getMerchantOrderId(), expired, actor, source + " · PSP nema nalog");
    }

    private void markChecked(String merchantOrderId) {
        tx.executeWithoutResult(s -> transactions.findByMerchantOrderId(merchantOrderId).ifPresent(t -> {
            t.setLastCheckedAt(clock.instant());
            t.setCheckAttempts(t.getCheckAttempts() + 1);
        }));
    }

    static PaymentResult toResult(PspTransaction r) {
        return new PaymentResult(TransactionStatus.fromPsp(r.status()), r.amount(), r.currency(), r.paymentMethod(),
                r.pspTransactionId(), r.globalTransactionId(), r.acquirerTimestamp());
    }
}
