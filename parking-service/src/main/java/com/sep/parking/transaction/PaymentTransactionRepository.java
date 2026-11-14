package com.sep.parking.transaction;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<PaymentTransaction> findByMerchantOrderId(String merchantOrderId);

    boolean existsByMerchantOrderId(String merchantOrderId);

    /**
     * Zakljucava transakciju do kraja DB transakcije: webhook, periodicna provera statusa i provera
     * na zahtev korisnika ne mogu istovremeno da obrade isti nalog.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.merchantOrderId = :merchantOrderId")
    Optional<PaymentTransaction> lockByMerchantOrderId(@Param("merchantOrderId") String merchantOrderId);

    @Query("select t from PaymentTransaction t where t.session.id in :sessionIds order by t.createdAt asc")
    List<PaymentTransaction> findBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);

    @EntityGraph(attributePaths = {"session", "session.zone"})
    @Query("select t from PaymentTransaction t where t.userId = :userId order by t.createdAt desc")
    List<PaymentTransaction> findRecentByUser(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Nalozi bez konacnog statusa koje treba proveriti na PSP-u: stariji od {@code createdBefore}
     * i neprovereni od {@code checkedBefore}. Najstariji prvo.
     */
    @Query("""
            select t.merchantOrderId from PaymentTransaction t
            where t.status in :openStatuses
              and t.createdAt < :createdBefore
              and (t.lastCheckedAt is null or t.lastCheckedAt < :checkedBefore)
            order by t.createdAt asc
            """)
    List<String> findDueForCheck(@Param("openStatuses") Collection<TransactionStatus> openStatuses,
                                 @Param("createdBefore") Instant createdBefore,
                                 @Param("checkedBefore") Instant checkedBefore,
                                 Pageable pageable);
}
