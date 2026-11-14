package com.sep.parking.session;

import com.sep.parking.transaction.PaymentTransactionRepository;
import com.sep.parking.transaction.TransactionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodicno uskladjivanje: nalozi koji posle {@code app.reconciliation.min-age-seconds} jos nemaju
 * konacan status proveravaju se na PSP-u, svaki najvise jednom u {@code interval}. Pokriva izgubljen
 * webhook, zatvoren tab, nedostupan PSP ili parking servis u trenutku potvrde.
 */
@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private static final int BATCH = 50;

    private final PaymentTransactionRepository transactions;
    private final PaymentStatusSync sync;
    private final Clock clock;
    private final Duration minAge;
    private final Duration recheckAfter;

    public PaymentReconciliationJob(PaymentTransactionRepository transactions, PaymentStatusSync sync, Clock clock,
                                    @Value("${app.reconciliation.min-age-seconds:60}") long minAgeSeconds,
                                    @Value("${app.reconciliation.recheck-seconds:60}") long recheckSeconds) {
        this.transactions = transactions;
        this.sync = sync;
        this.clock = clock;
        this.minAge = Duration.ofSeconds(minAgeSeconds);
        this.recheckAfter = Duration.ofSeconds(recheckSeconds);
    }

    @Scheduled(fixedDelayString = "${app.reconciliation.interval-millis:30000}",
            initialDelayString = "${app.reconciliation.interval-millis:30000}")
    public void reconcile() {
        Instant now = clock.instant();
        List<String> due = transactions.findDueForCheck(
                List.of(TransactionStatus.INIT, TransactionStatus.PENDING),
                now.minus(minAge), now.minus(recheckAfter), PageRequest.of(0, BATCH));
        for (String orderId : due) {
            try {
                PaymentOutcome outcome = sync.sync(orderId, StatusActor.SYSTEM, "provera statusa");
                if (outcome != null && outcome != PaymentOutcome.STILL_OPEN) {
                    log.info("Uskladjen nalog {}: {}", orderId, outcome);
                }
            } catch (RuntimeException e) {
                log.warn("Uskladjivanje naloga {} nije uspelo: {}", orderId, e.getMessage());
            }
        }
    }
}
