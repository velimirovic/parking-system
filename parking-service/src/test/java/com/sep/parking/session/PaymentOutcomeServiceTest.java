package com.sep.parking.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sep.parking.transaction.PaymentTransaction;
import com.sep.parking.transaction.PaymentTransactionRepository;
import com.sep.parking.transaction.RefundStatus;
import com.sep.parking.transaction.TransactionPurpose;
import com.sep.parking.transaction.TransactionStatus;
import com.sep.parking.vehicle.Vehicle;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentOutcomeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String ORDER = "PRK-20261114-ABC123";

    private final PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    private final ParkingSessionRepository sessions = mock(ParkingSessionRepository.class);
    private final SessionAudit audit = mock(SessionAudit.class);
    private final PaymentOutcomeService service = new PaymentOutcomeService(transactions, sessions, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private ParkingSession session;
    private PaymentTransaction tx;

    @BeforeEach
    void setUp() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        session = new ParkingSession();
        session.setId(UUID.randomUUID());
        session.setVehicle(vehicle);
        session.setStatus(SessionStatus.PENDING_PAYMENT);
        session.setDurationMinutes(60);
        session.setStartsAt(NOW.minusSeconds(300));
        session.setEndsAt(NOW.plusSeconds(3300));
        tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());
        tx.setSession(session);
        tx.setMerchantOrderId(ORDER);
        tx.setPurpose(TransactionPurpose.PURCHASE);
        tx.setAmount(new BigDecimal("60.00"));
        tx.setCurrency("RSD");
        tx.setStatus(TransactionStatus.PENDING);
        tx.setCreatedAt(NOW.minusSeconds(300));
        when(transactions.lockByMerchantOrderId(ORDER)).thenReturn(Optional.of(tx));
        when(sessions.existsOtherActive(any(), any(), any(), eq(SessionStatus.ACTIVE))).thenReturn(false);
    }

    private static PaymentResult result(TransactionStatus status, String amount, Instant approvedAt) {
        return new PaymentResult(status, amount == null ? null : new BigDecimal(amount), "RSD", "CARD", "psp-1",
                "GTX-1", approvedAt);
    }

    @Test
    void successActivatesSessionFromApprovalTime() {
        Instant approved = NOW.minusSeconds(60);
        PaymentOutcome outcome = service.apply(ORDER, result(TransactionStatus.SUCCESS, "60.00", approved),
                StatusActor.PSP, "webhook");

        assertThat(outcome).isEqualTo(PaymentOutcome.ACTIVATED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getStartsAt()).isEqualTo(approved);
        assertThat(session.getEndsAt()).isEqualTo(approved.plusSeconds(3600));
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(tx.getGlobalTransactionId()).isEqualTo("GTX-1");
    }

    @Test
    void repeatedConfirmationIsIgnored() {
        service.apply(ORDER, result(TransactionStatus.SUCCESS, "60.00", NOW), StatusActor.PSP, "webhook");
        Instant end = session.getEndsAt();

        PaymentOutcome second = service.apply(ORDER, result(TransactionStatus.SUCCESS, "60.00", NOW.plusSeconds(30)),
                StatusActor.SYSTEM, "provera statusa");

        assertThat(second).isEqualTo(PaymentOutcome.DUPLICATE);
        assertThat(session.getEndsAt()).isEqualTo(end);
    }

    @Test
    void failedPaymentNeverActivates() {
        PaymentOutcome outcome = service.apply(ORDER, result(TransactionStatus.FAILED, "60.00", null),
                StatusActor.PSP, "webhook");

        assertThat(outcome).isEqualTo(PaymentOutcome.CANCELLED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(tx.getRefundStatus()).isNull();
    }

    @Test
    void amountMismatchIsNotActivatedAndChargedAmountIsRefunded() {
        PaymentOutcome outcome = service.apply(ORDER, result(TransactionStatus.SUCCESS, "6.00", NOW),
                StatusActor.PSP, "webhook");

        assertThat(outcome).isEqualTo(PaymentOutcome.REFUND_REQUIRED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.ERROR);
        assertThat(tx.getRefundStatus()).isEqualTo(RefundStatus.REQUIRED);
    }

    @Test
    void successWhenVehicleAlreadyParkedRequiresRefund() {
        when(sessions.existsOtherActive(any(), any(), any(), eq(SessionStatus.ACTIVE))).thenReturn(true);

        PaymentOutcome outcome = service.apply(ORDER, result(TransactionStatus.SUCCESS, "60.00", NOW),
                StatusActor.PSP, "webhook");

        assertThat(outcome).isEqualTo(PaymentOutcome.REFUND_REQUIRED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(tx.getRefundStatus()).isEqualTo(RefundStatus.REQUIRED);
    }

    @Test
    void lateSuccessAfterExpiryRequiresRefund() {
        service.apply(ORDER, result(TransactionStatus.EXPIRED, null, null), StatusActor.SYSTEM, "provera statusa");

        PaymentOutcome outcome = service.apply(ORDER, result(TransactionStatus.SUCCESS, "60.00", NOW),
                StatusActor.PSP, "webhook");

        assertThat(outcome).isEqualTo(PaymentOutcome.REFUND_REQUIRED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(tx.getRefundStatus()).isEqualTo(RefundStatus.REQUIRED);
    }

    @Test
    void approvalTimeIsClampedToOrderLifetime() {
        Instant future = NOW.plusSeconds(600);
        assertThat(PaymentOutcomeService.confirmationTime(tx, result(TransactionStatus.SUCCESS, "60.00", future), NOW))
                .isEqualTo(NOW);
        Instant beforeOrder = tx.getCreatedAt().minusSeconds(600);
        assertThat(PaymentOutcomeService.confirmationTime(tx,
                result(TransactionStatus.SUCCESS, "60.00", beforeOrder), NOW)).isEqualTo(tx.getCreatedAt());
    }
}
