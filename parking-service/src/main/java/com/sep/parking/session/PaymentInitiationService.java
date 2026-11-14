package com.sep.parking.session;

import com.sep.parking.common.exception.ApiException;
import com.sep.parking.payment.PspClient;
import com.sep.parking.payment.PspException;
import com.sep.parking.payment.PspTransaction;
import com.sep.parking.session.SessionDtos.CreateSessionResponse;
import com.sep.parking.transaction.PaymentTransaction;
import com.sep.parking.transaction.PaymentTransactionRepository;
import com.sep.parking.transaction.TransactionStatus;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Salje nalog PSP-u (inicijalizacija transakcije) i cuva redirect_url na checkout PSP-a.
 * Poziv PSP-u je van DB transakcije; PSP je idempotentan po merchant_order_id, pa se isti
 * nalog moze bezbedno poslati vise puta (ponovni pokusaj posle timeout-a).
 */
@Service
public class PaymentInitiationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentInitiationService.class);

    private final PaymentTransactionRepository transactions;
    private final PspClient psp;
    private final SessionAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentInitiationService(PaymentTransactionRepository transactions, PspClient psp, SessionAudit audit,
                                    TransactionTemplate tx, Clock clock) {
        this.transactions = transactions;
        this.psp = psp;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    /** Podaci naloga potrebni za poziv PSP-u (citaju se u transakciji, PSP se poziva van nje). */
    private record InitData(boolean alreadySent, String merchantOrderId, java.time.Instant createdAt,
                            java.math.BigDecimal amount, String currency, String description) {
    }

    public CreateSessionResponse initiate(UUID transactionId) {
        InitData data = tx.execute(s -> {
            PaymentTransaction t = load(transactionId);
            if (t == null) return null;
            boolean sent = !t.getStatus().isOpen() || t.getRedirectUrl() != null;
            return new InitData(sent, t.getMerchantOrderId(), t.getCreatedAt(), t.getAmount(), t.getCurrency(),
                    description(t));
        });
        if (data == null) {
            throw ApiException.notFound("ORDER_NOT_FOUND", "Nalog nije pronađen.");
        }
        // vec poslat PSP-u, ili je placanje vec zavrseno - vrati postojece stanje
        if (data.alreadySent()) {
            return tx.execute(s -> response(load(transactionId)));
        }

        PspTransaction result;
        try {
            result = psp.initTransaction(data.merchantOrderId(), data.createdAt(), data.amount(), data.currency(),
                    data.description());
        } catch (PspException.Unavailable e) {
            // nalog ostaje INIT; isti zahtev (isti requestId) ce ga ponovo poslati
            throw ApiException.unavailable("PSP_UNAVAILABLE",
                    "Servis za plaćanje trenutno nije dostupan. Nalog je sačuvan — pokušajte ponovo.");
        } catch (PspException.Rejected e) {
            tx.executeWithoutResult(s -> markFailed(load(transactionId), TransactionStatus.ERROR, "PSP: " + e.code()));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PSP_REJECTED", rejectionMessage(e));
        }

        // PSP mora potvrditi isti iznos i valutu koje je parking sistem poslao
        boolean mismatch = result.amount() != null && (result.amount().compareTo(data.amount()) != 0
                || !data.currency().equalsIgnoreCase(result.currency()));
        if (mismatch) {
            log.error("PSP je vratio drugi iznos za {}: {} {} umesto {} {}", data.merchantOrderId(),
                    result.amount(), result.currency(), data.amount(), data.currency());
            tx.executeWithoutResult(s -> markFailed(load(transactionId), TransactionStatus.ERROR,
                    "Iznos ili valuta se ne poklapaju sa PSP-om"));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AMOUNT_MISMATCH",
                    "Servis za plaćanje je vratio pogrešan iznos. Nalog je otkazan.");
        }

        final PspTransaction accepted = result;
        return tx.execute(s -> {
            PaymentTransaction t = load(transactionId);
            if (!t.getStatus().isOpen() || t.getRedirectUrl() != null) {
                return response(t); // paralelni zahtev je vec upisao rezultat
            }
            t.setPspTransactionId(accepted.pspTransactionId());
            t.setRedirectUrl(accepted.redirectUrl());
            t.setPaymentExpiresAt(accepted.expiresAt());
            TransactionStatus pspStatus = TransactionStatus.fromPsp(accepted.status());
            if (pspStatus.isOpen()) {
                t.setStatus(pspStatus);
            }
            ParkingSession session = t.getSession();
            audit.record(session, session.getStatus(), t.getId(), "PAYMENT_INITIATED",
                    "Transakcija inicijalizovana na PSP-u, korisnik preusmeren na plaćanje", StatusActor.SYSTEM);
            return response(t);
        });
    }

    private PaymentTransaction load(UUID id) {
        PaymentTransaction t = transactions.findById(id).orElse(null);
        if (t != null) {
            t.getSession().getStatus(); // inicijalizacija veze unutar transakcije
        }
        return t;
    }

    /** Nalog koji PSP nije prihvatio ne moze aktivirati sesiju - sesija se otkazuje. */
    private void markFailed(PaymentTransaction t, TransactionStatus status, String reason) {
        t.setStatus(status);
        t.setFailureReason(reason.length() > 200 ? reason.substring(0, 200) : reason);
        ParkingSession session = t.getSession();
        if (session.getStatus() == SessionStatus.PENDING_PAYMENT) {
            SessionStatus from = session.getStatus();
            session.setStatus(SessionStatus.CANCELLED);
            session.setClosedAt(clock.instant());
            audit.record(session, from, t.getId(), "PAYMENT_INIT_REJECTED", reason, StatusActor.PSP);
        }
    }

    private static String description(PaymentTransaction t) {
        ParkingSession s = t.getSession();
        String text = "Parking · " + s.getZone().getName() + " · " + s.getPlate() + " · " + t.getDurationMinutes() + " min";
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private static String rejectionMessage(PspException.Rejected e) {
        return switch (e.code()) {
            case "NO_PAYMENT_METHODS" -> "Trenutno nema dostupnih načina plaćanja. Pokušajte kasnije.";
            case "MERCHANT_INACTIVE" -> "Parking sistem trenutno ne može da prima plaćanja.";
            default -> "Servis za plaćanje je odbio zahtev. Nalog je otkazan — pokušajte ponovo.";
        };
    }

    static CreateSessionResponse response(PaymentTransaction t) {
        ParkingSession s = t.getSession();
        return new CreateSessionResponse(s.getId(), t.getMerchantOrderId(), s.getStatus(), t.getStatus(),
                t.getAmount(), t.getCurrency(), t.getStatus().isOpen() ? t.getRedirectUrl() : null,
                t.getPaymentExpiresAt());
    }
}
