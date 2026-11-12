package com.sep.bank.paymentrequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {

    Optional<PaymentRequest> findByPaymentId(String paymentId);

    Optional<PaymentRequest> findByMerchantCodeAndStanAndPspTimestamp(String merchantCode, String stan,
                                                                      Instant pspTimestamp);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentRequest p where p.paymentId = :paymentId")
    Optional<PaymentRequest> findByPaymentIdForUpdate(@Param("paymentId") String paymentId);

    /**
     * Zauzima jedini pokusaj placanja. Atomski UPDATE: od dva istovremena slanja forme (dupli klik,
     * dva taba) samo jedno dobija 1, drugo 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update payment_request
               set attempt_used = true, version = version + 1
             where payment_id = :paymentId and attempt_used = false and status = 'CREATED' and expires_at > :now
            """, nativeQuery = true)
    int claimAttempt(@Param("paymentId") String paymentId, @Param("now") Instant now);

    /** Forme kojima je istekao rok, a nisu iskoriscene. */
    @Query(value = """
            select payment_id from payment_request
             where status = 'CREATED' and expires_at <= :now
             order by expires_at
             limit 50
            """, nativeQuery = true)
    List<String> findExpired(@Param("now") Instant now);

    /** Zavrseni zahtevi cije obavestenje PSP-u jos nije isporuceno. */
    @Query(value = """
            select payment_id from payment_request
             where status <> 'CREATED' and psp_notified_at is null and next_notify_at <= :now
             order by next_notify_at
             limit 50
            """, nativeQuery = true)
    List<String> findPendingNotifications(@Param("now") Instant now);
}
