package com.sep.cardplugin.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CardPaymentRepository extends JpaRepository<CardPayment, UUID> {

    Optional<CardPayment> findByTransactionId(UUID transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CardPayment c where c.transactionId = :transactionId")
    Optional<CardPayment> findByTransactionIdForUpdate(@Param("transactionId") UUID transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CardPayment c where c.bankPaymentId = :paymentId")
    Optional<CardPayment> findByBankPaymentIdForUpdate(@Param("paymentId") String paymentId);

    @Query(value = "select nextval('card_plugin.stan_seq')", nativeQuery = true)
    long nextStan();

    /** Konacni rezultati koje PSP Core jos nije potvrdio. */
    @Query("""
            select c.transactionId from CardPayment c
            where c.coreNotifiedAt is null and c.status in ('APPROVED', 'DECLINED', 'EXPIRED', 'ERROR')
              and c.nextCoreAttemptAt <= :now
            order by c.nextCoreAttemptAt
            """)
    List<UUID> findUnforwarded(@Param("now") Instant now, org.springframework.data.domain.Pageable page);

    /** Placanja bez obavestenja banke - plugin sam proverava status (PSP_TIMESTAMP + STAN). */
    @Query("""
            select c.transactionId from CardPayment c
            where c.status = 'PENDING' and c.nextStatusCheckAt <= :now
            order by c.nextStatusCheckAt
            """)
    List<UUID> findDueForStatusCheck(@Param("now") Instant now, org.springframework.data.domain.Pageable page);
}
