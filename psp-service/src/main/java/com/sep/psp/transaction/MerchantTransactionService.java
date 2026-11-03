package com.sep.psp.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.common.web.PageResponse;
import com.sep.psp.common.web.RequestContext;
import com.sep.psp.webhook.WebhookDelivery;
import com.sep.psp.webhook.WebhookDeliveryRepository;
import com.sep.psp.webhook.WebhookStatus;

import lombok.RequiredArgsConstructor;

/**
 * Read-only pregled transakcija za klijentski portal. Svaki upit je ogranicen na merchanta iz tokena.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MerchantTransactionService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Belgrade");
    private static final int EXPORT_LIMIT = 10_000;

    private final PspTransactionRepository repository;
    private final WebhookDeliveryRepository webhookRepository;
    private final AuditService auditService;

    public PageResponse<TransactionRow> search(String query, TransactionStatus status, int page, int size) {
        UUID merchantId = RequestContext.merchantId();
        Page<PspTransaction> result = repository.search(merchantId, status, normalize(query),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<UUID, WebhookDelivery> lastDelivery = latestDeliveries(result.getContent());
        return PageResponse.of(result, t -> TransactionRow.from(t, lastDelivery.get(t.getId())));
    }

    public TransactionStats todayStats() {
        UUID merchantId = RequestContext.merchantId();
        Instant from = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        List<PspTransactionRepository.StatusTotal> totals = repository.totalsSince(merchantId, from);
        long count = totals.stream().mapToLong(PspTransactionRepository.StatusTotal::getCount).sum();
        long success = totals.stream()
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS || t.getStatus() == TransactionStatus.REFUNDED)
                .mapToLong(PspTransactionRepository.StatusTotal::getCount).sum();
        long finished = totals.stream()
                .filter(t -> t.getStatus() != TransactionStatus.INIT && t.getStatus() != TransactionStatus.PENDING)
                .mapToLong(PspTransactionRepository.StatusTotal::getCount).sum();
        Map<String, BigDecimal> turnover = new TreeMap<>();
        totals.stream()
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .forEach(t -> turnover.merge(t.getCurrency(), t.getTotal(), BigDecimal::add));
        BigDecimal successRate = finished == 0 ? null
                : BigDecimal.valueOf(success * 100).divide(BigDecimal.valueOf(finished), 1, RoundingMode.HALF_UP);
        return new TransactionStats(from, turnover.entrySet().stream()
                .map(e -> new Amount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing((Amount a) -> !a.currency().equals("RSD")).thenComparing(Amount::currency))
                .toList(),
                count, successRate, webhookRepository.countByMerchantAndStatus(merchantId, WebhookStatus.PENDING));
    }

    public TransactionDetail detail(UUID id) {
        PspTransaction t = repository.findByIdAndMerchantId(id, RequestContext.merchantId())
                .orElseThrow(() -> ApiException.notFound("Transakcija ne postoji"));
        List<DeliveryView> deliveries = webhookRepository.findByTransactionIdOrderByCreatedAtAsc(t.getId()).stream()
                .map(DeliveryView::from)
                .toList();
        return new TransactionDetail(t.getId(), t.getMerchantOrderId(), t.getMerchantTimestamp(),
                t.getPaymentMethod() == null ? null : t.getPaymentMethod().getCode(),
                t.getPaymentMethod() == null ? null : t.getPaymentMethod().getDisplayName(),
                t.getAmount(), t.getCurrency(), t.getStatus(), t.getStan(), t.getPspTimestamp(),
                t.getGlobalTransactionId(), t.getAcquirerTimestamp(), t.getExpiresAt(), t.getCreatedAt(),
                t.getUpdatedAt(), deliveries);
    }

    /** CSV izvoz - bez osetljivih podataka (PSP ionako nema podatke kartice). */
    public String exportCsv(String query, TransactionStatus status) {
        UUID merchantId = RequestContext.merchantId();
        List<PspTransaction> rows = repository.search(merchantId, status, normalize(query),
                PageRequest.of(0, EXPORT_LIMIT)).getContent();
        StringBuilder csv = new StringBuilder(
                "created_at;merchant_order_id;psp_transaction_id;payment_method;amount;currency;status;stan;global_transaction_id\n");
        for (PspTransaction t : rows) {
            csv.append(String.join(";",
                    t.getCreatedAt().toString(),
                    csvSafe(t.getMerchantOrderId()),
                    t.getId().toString(),
                    t.getPaymentMethod() == null ? "" : t.getPaymentMethod().getCode(),
                    t.getAmount().toPlainString(),
                    t.getCurrency(),
                    t.getStatus().name(),
                    csvSafe(t.getStan()),
                    csvSafe(t.getGlobalTransactionId()))).append('\n');
        }
        auditService.userEvent(AuditAction.DATA_EXPORT, "PSP_TRANSACTION", merchantId.toString(),
                AuditOutcome.SUCCESS, "CSV, redova: " + rows.size());
        return csv.toString();
    }

    private Map<UUID, WebhookDelivery> latestDeliveries(List<PspTransaction> transactions) {
        if (transactions.isEmpty()) {
            return Map.of();
        }
        return webhookRepository.findByTransactionIdIn(transactions.stream().map(PspTransaction::getId).toList())
                .stream()
                .collect(Collectors.toMap(WebhookDelivery::getTransactionId, d -> d,
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
    }

    private static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }

    /** Sprecava CSV/formula injection pri otvaranju u Excel-u. */
    private static String csvSafe(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replace(";", ",").replace("\n", " ");
        return clean.matches("^[=+\\-@].*") ? "'" + clean : clean;
    }

    // ---------- DTO ----------

    public record WebhookSummary(WebhookStatus status, Integer lastHttpStatus, int attempts, int maxAttempts) {
    }

    public record TransactionRow(UUID id, Instant createdAt, String merchantOrderId, String methodCode,
                                 String methodName, BigDecimal amount, String currency, TransactionStatus status,
                                 WebhookSummary webhook) {
        static TransactionRow from(PspTransaction t, WebhookDelivery d) {
            return new TransactionRow(t.getId(), t.getCreatedAt(), t.getMerchantOrderId(),
                    t.getPaymentMethod() == null ? null : t.getPaymentMethod().getCode(),
                    t.getPaymentMethod() == null ? null : t.getPaymentMethod().getDisplayName(),
                    t.getAmount(), t.getCurrency(), t.getStatus(),
                    d == null ? null : new WebhookSummary(d.getStatus(), d.getLastHttpStatus(), d.getAttemptCount(),
                            d.getMaxAttempts()));
        }
    }

    public record Amount(String currency, BigDecimal amount) {
    }

    public record TransactionStats(Instant since, List<Amount> turnover, long count, BigDecimal successRate,
                                   long pendingWebhooks) {
    }

    public record DeliveryView(String eventType, WebhookStatus status, Integer lastHttpStatus, Integer durationMs,
                               int attempts, int maxAttempts, Instant nextAttemptAt, Instant createdAt) {
        static DeliveryView from(WebhookDelivery d) {
            return new DeliveryView(d.getEventType(), d.getStatus(), d.getLastHttpStatus(), d.getLastDurationMs(),
                    d.getAttemptCount(), d.getMaxAttempts(), d.getNextAttemptAt(), d.getCreatedAt());
        }
    }

    public record TransactionDetail(UUID id, String merchantOrderId, Instant merchantTimestamp, String methodCode,
                                    String methodName, BigDecimal amount, String currency, TransactionStatus status,
                                    String stan, Instant pspTimestamp, String globalTransactionId,
                                    Instant acquirerTimestamp, Instant expiresAt, Instant createdAt,
                                    Instant updatedAt, List<DeliveryView> deliveries) {
    }
}
