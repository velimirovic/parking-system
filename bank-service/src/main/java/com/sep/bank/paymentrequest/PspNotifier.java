package com.sep.bank.paymentrequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sep.bank.paymentrequest.PspAuthenticator.SignedHeaders;
import com.sep.bank.transaction.BankTransactionRepository;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Korak 6 iz specifikacije: banka javlja PSP-u (card plugin-u) status transakcije sa STAN-om,
 * GLOBAL_TRANSACTION_ID-jem i ACQUIRER_TIMESTAMP-om. Ako PSP nije dostupan, obavestenje se ponavlja
 * sa rastucim razmakom; PSP u svakom trenutku moze i sam da proveri status (GET /payment-requests/status).
 */
@Slf4j
@Component
public class PspNotifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_BACKOFF_SECONDS = 600;

    private final PaymentRequestRepository repository;
    private final BankTransactionRepository transactionRepository;
    private final PspAuthenticator authenticator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public PspNotifier(PaymentRequestRepository repository, BankTransactionRepository transactionRepository,
                       PspAuthenticator authenticator, ObjectMapper objectMapper,
                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionRepository = transactionRepository;
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Poziva se posle commit-a rezultata - kupac ne ceka na isporuku obavestenja. */
    public void notifyAsync(String paymentId) {
        executor.submit(() -> deliver(paymentId));
    }

    /** Zatvara forme kojima je istekao rok i javlja PSP-u da placanje nije izvrseno. */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void expireForms() {
        for (String paymentId : repository.findExpired(Instant.now())) {
            Boolean expired = tx.execute(status -> repository.findByPaymentIdForUpdate(paymentId)
                    .filter(r -> r.getStatus() == PaymentRequestStatus.CREATED && !Instant.now().isBefore(r.getExpiresAt()))
                    .map(r -> {
                        r.setStatus(PaymentRequestStatus.EXPIRED);
                        r.setCompletedAt(Instant.now());
                        r.setNextNotifyAt(Instant.now());
                        return true;
                    }).orElse(false));
            if (Boolean.TRUE.equals(expired)) {
                log.info("Forma {} je istekla", paymentId);
                deliver(paymentId);
            }
        }
    }

    /** Ponavlja obavestenja koja PSP nije potvrdio. */
    @Scheduled(fixedDelay = 5_000, initialDelay = 15_000)
    public void retryPending() {
        repository.findPendingNotifications(Instant.now()).forEach(this::deliver);
    }

    void deliver(String paymentId) {
        PaymentRequest request = repository.findByPaymentId(paymentId).orElse(null);
        if (request == null || request.getPspNotifiedAt() != null || request.getStatus() == PaymentRequestStatus.CREATED) {
            return;
        }
        boolean delivered = false;
        try {
            String body = objectMapper.writeValueAsString(PaymentRequestService.toStatus(request,
                    transactionRepository.findPayment(request.getId()).orElse(null)));
            URI uri = URI.create(request.getNotifyUrl());
            SignedHeaders signed = authenticator.sign("POST", uri.getPath(), body);
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header(PspAuthenticator.HEADER_TIMESTAMP, signed.timestamp())
                    .header(PspAuthenticator.HEADER_SIGNATURE, signed.signature())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            int code = client.send(httpRequest, HttpResponse.BodyHandlers.discarding()).statusCode();
            delivered = code / 100 == 2;
            if (!delivered) {
                log.warn("PSP je odbio obavestenje za {} (HTTP {})", paymentId, code);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Obavestenje PSP-u za {} nije isporuceno: {}", paymentId, e.toString());
        }
        boolean ok = delivered;
        tx.executeWithoutResult(status -> repository.findByPaymentIdForUpdate(paymentId).ifPresent(r -> {
            if (r.getPspNotifiedAt() != null) {
                return;
            }
            r.setNotifyAttempts(r.getNotifyAttempts() + 1);
            if (ok) {
                r.setPspNotifiedAt(Instant.now());
                r.setNextNotifyAt(null);
            } else {
                long backoff = Math.min(MAX_BACKOFF_SECONDS, 5L << Math.min(r.getNotifyAttempts(), 7));
                r.setNextNotifyAt(Instant.now().plusSeconds(backoff));
            }
        }));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
