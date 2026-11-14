package com.sep.parking.history;

import com.sep.parking.session.SessionDtos.SessionResponse;
import com.sep.parking.transaction.RefundStatus;
import com.sep.parking.transaction.TransactionPurpose;
import com.sep.parking.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class HistoryDtos {

    private HistoryDtos() {
    }

    /**
     * @param spent      zbir uspesnih placanja u periodu
     * @param sessions   broj placenih sesija (aktivne i istekle)
     * @param extensions broj uspesnih produzenja
     * @param refunded   zbir refundiranih iznosa
     */
    public record Summary(BigDecimal spent, String currency, long sessions, long extensions, BigDecimal refunded) {
    }

    public record HistoryResponse(Summary summary, List<SessionResponse> items, int page, int size,
                                  long totalItems, int totalPages) {
    }

    public record PaymentRow(String merchantOrderId, Instant createdAt, String plate, String zoneName,
                             TransactionPurpose purpose, String paymentMethod, BigDecimal amount, String currency,
                             TransactionStatus status, RefundStatus refundStatus) {
    }
}
