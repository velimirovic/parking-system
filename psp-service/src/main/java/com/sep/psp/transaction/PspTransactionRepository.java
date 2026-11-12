package com.sep.psp.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PspTransactionRepository extends JpaRepository<PspTransaction, UUID> {

    Optional<PspTransaction> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<PspTransaction> findByMerchantIdAndMerchantOrderId(UUID merchantId, String merchantOrderId);

    /** Lock na redu transakcije - serijalizuje izbor metode, otkazivanje i promene statusa. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PspTransaction t where t.id = :id")
    Optional<PspTransaction> findByIdForUpdate(@Param("id") UUID id);

    /** Pretraga po MERCHANT_ORDER_ID, PSP ID ili GLOBAL_TRANSACTION_ID - uvek ogranicena na jednog merchanta. */
    @Query("""
            select t from PspTransaction t
            where t.merchantId = :merchantId
              and (:status is null or t.status = :status)
              and (:query = ''
                   or lower(t.merchantOrderId) like concat('%', :query, '%')
                   or lower(cast(t.id as string)) like concat('%', :query, '%')
                   or lower(coalesce(t.globalTransactionId, '')) like concat('%', :query, '%'))
            order by t.createdAt desc
            """)
    Page<PspTransaction> search(@Param("merchantId") UUID merchantId,
                                @Param("status") TransactionStatus status,
                                @Param("query") String query,
                                Pageable pageable);

    @Query("""
            select t.status as status, t.currency as currency, count(t) as count, sum(t.amount) as total
            from PspTransaction t
            where t.merchantId = :merchantId and t.createdAt >= :from
            group by t.status, t.currency
            """)
    List<StatusTotal> totalsSince(@Param("merchantId") UUID merchantId, @Param("from") Instant from);

    long countByMerchantId(UUID merchantId);

    interface StatusTotal {
        TransactionStatus getStatus();

        String getCurrency();

        long getCount();

        BigDecimal getTotal();
    }
}
