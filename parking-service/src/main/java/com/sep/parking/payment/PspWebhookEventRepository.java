package com.sep.parking.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PspWebhookEventRepository extends JpaRepository<PspWebhookEvent, Long> {
}
