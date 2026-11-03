package com.sep.psp.webhook;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

    List<WebhookDelivery> findByTransactionIdIn(Collection<UUID> transactionIds);

    long countByStatus(WebhookStatus status);

    @Query("""
            select count(w) from WebhookDelivery w, com.sep.psp.transaction.PspTransaction t
            where w.transactionId = t.id and t.merchantId = :merchantId and w.status = :status
            """)
    long countByMerchantAndStatus(@Param("merchantId") UUID merchantId, @Param("status") WebhookStatus status);
}
