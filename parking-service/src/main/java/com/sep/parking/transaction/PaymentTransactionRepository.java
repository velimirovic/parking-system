package com.sep.parking.transaction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<PaymentTransaction> findByMerchantOrderId(String merchantOrderId);

    boolean existsByMerchantOrderId(String merchantOrderId);

    @Query("select t from PaymentTransaction t where t.session.id in :sessionIds order by t.createdAt asc")
    List<PaymentTransaction> findBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);

    @EntityGraph(attributePaths = {"session", "session.zone"})
    @Query("select t from PaymentTransaction t where t.userId = :userId order by t.createdAt desc")
    List<PaymentTransaction> findRecentByUser(@Param("userId") UUID userId, Pageable pageable);
}
