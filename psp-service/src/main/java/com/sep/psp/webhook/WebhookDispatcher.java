package com.sep.psp.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sep.psp.common.crypto.AesGcmEncryptor;
import com.sep.psp.common.crypto.Hmac;
import com.sep.psp.merchant.Merchant;
import com.sep.psp.merchant.MerchantRepository;
import com.sep.psp.transaction.PspTransactionRepository;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Isporuka webhook-ova iz outbox-a (WEBHOOK_DELIVERY) klijentima PSP-a.
 *
 * <p>Zapisi se preuzimaju sa {@code FOR UPDATE SKIP LOCKED} i "zakupljuju" pomeranjem next_attempt_at,
 * pa vise instanci PSP-a moze da radi istovremeno bez duplog slanja istog zapisa. Ako instanca padne
 * usred slanja, zakup istekne i druga instanca ponovi isporuku (klijent obradjuje webhook idempotentno).
 * Neuspesno slanje se ponavlja sa eksponencijalnim razmakom do max_attempts.
 *
 * <p>Zahtev je potpisan zaglavljima {@code X-Psp-Timestamp} i {@code X-Psp-Signature}
 * (HMAC-SHA256 kanonskog zahteva webhook tajnom klijenta, path = putanja webhook URL-a).
 */
@Slf4j
@Component
public class WebhookDispatcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int BATCH = 20;
    private static final int LEASE_SECONDS = 60;
    private static final long MAX_BACKOFF_SECONDS = 3600;

    private final JdbcTemplate jdbc;
    private final WebhookDeliveryRepository repository;
    private final PspTransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final AesGcmEncryptor encryptor;
    private final TransactionTemplate tx;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    // slanje ka razlicitim klijentima ide paralelno - jedan spor klijent ne zadrzava ostale u istoj seriji
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public WebhookDispatcher(JdbcTemplate jdbc, WebhookDeliveryRepository repository,
                             PspTransactionRepository transactionRepository, MerchantRepository merchantRepository,
                             AesGcmEncryptor encryptor, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.encryptor = encryptor;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 2_000, initialDelay = 10_000)
    public void dispatch() {
        List<UUID> leased = tx.execute(status -> jdbc.queryForList("""
                update webhook_delivery
                   set next_attempt_at = now() + make_interval(secs => ?)
                 where id in (select id from webhook_delivery
                               where status = 'PENDING' and not simulated and next_attempt_at <= now()
                               order by next_attempt_at
                               limit ?
                               for update skip locked)
                returning id
                """, UUID.class, LEASE_SECONDS, BATCH));
        if (leased == null || leased.isEmpty()) {
            return;
        }
        List<java.util.concurrent.Callable<Void>> jobs = leased.stream()
                .<java.util.concurrent.Callable<Void>>map(id -> () -> {
                    deliver(id);
                    return null;
                }).toList();
        try {
            executor.invokeAll(jobs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void deliver(UUID deliveryId) {
        WebhookDelivery delivery = repository.findById(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != WebhookStatus.PENDING) {
            return;
        }
        Merchant merchant = transactionRepository.findById(delivery.getTransactionId())
                .flatMap(t -> merchantRepository.findById(t.getMerchantId())).orElse(null);
        Integer httpStatus = null;
        long started = System.nanoTime();
        if (merchant != null && merchant.getWebhookUrl() != null) {
            try {
                URI uri = URI.create(merchant.getWebhookUrl());
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                String signature = Hmac.sha256Hex(encryptor.decrypt(merchant.getWebhookSecretEncrypted()),
                        Hmac.canonicalRequest(timestamp, "POST", path, delivery.getPayload()));
                httpStatus = client.send(HttpRequest.newBuilder(uri)
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .header("X-Psp-Event", delivery.getEventType())
                        .header("X-Psp-Delivery", delivery.getId().toString())
                        .header("X-Psp-Timestamp", timestamp)
                        .header("X-Psp-Signature", signature)
                        .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
                        .build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("Webhook {} nije isporucen: {}", deliveryId, e.toString());
            }
        }
        int durationMs = (int) ((System.nanoTime() - started) / 1_000_000);
        Integer code = httpStatus;
        tx.executeWithoutResult(status -> repository.findById(deliveryId).ifPresent(d -> {
            if (d.getStatus() != WebhookStatus.PENDING) {
                return;
            }
            d.setAttemptCount(d.getAttemptCount() + 1);
            d.setLastHttpStatus(code);
            d.setLastDurationMs(durationMs);
            if (code != null && code / 100 == 2) {
                d.setStatus(WebhookStatus.DELIVERED);
                d.setNextAttemptAt(null);
            } else if (d.getAttemptCount() >= d.getMaxAttempts()) {
                d.setStatus(WebhookStatus.FAILED);
                d.setNextAttemptAt(null);
                log.warn("Webhook {} za transakciju {} nije isporucen posle {} pokusaja", d.getId(),
                        d.getTransactionId(), d.getAttemptCount());
            } else {
                long backoff = Math.min(MAX_BACKOFF_SECONDS, 5L << Math.min(d.getAttemptCount(), 10));
                d.setNextAttemptAt(Instant.now().plusSeconds(backoff));
            }
            repository.save(d);
        }));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
