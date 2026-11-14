package com.sep.parking.history;

import com.sep.parking.common.config.ParkingTime;
import com.sep.parking.common.exception.ApiException;
import com.sep.parking.history.HistoryDtos.HistoryResponse;
import com.sep.parking.history.HistoryDtos.PaymentRow;
import com.sep.parking.history.HistoryDtos.Summary;
import com.sep.parking.session.ParkingSession;
import com.sep.parking.session.ParkingSessionRepository;
import com.sep.parking.session.SessionDtos.PaymentSummary;
import com.sep.parking.session.SessionDtos.SessionResponse;
import com.sep.parking.session.SessionMapper;
import com.sep.parking.session.SessionSpecifications;
import com.sep.parking.session.SessionStatus;
import com.sep.parking.transaction.PaymentTransactionRepository;
import com.sep.parking.transaction.TransactionPurpose;
import com.sep.parking.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ParkingSessionRepository sessions;
    private final PaymentTransactionRepository transactions;
    private final SessionMapper mapper;

    public HistoryService(ParkingSessionRepository sessions, PaymentTransactionRepository transactions,
                          SessionMapper mapper) {
        this.sessions = sessions;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    /**
     * Istorija sesija za mesec (podrazumevano tekuci) sa filterima vozila i statusa. Zbirni podaci
     * se racunaju nad svim sesijama koje odgovaraju filteru, a lista se vraca po stranicama.
     */
    @Transactional(readOnly = true)
    public HistoryResponse history(UUID userId, String month, UUID vehicleId, SessionStatus status, int page,
                                   int size) {
        YearMonth ym = parseMonth(month);
        Instant from = ym.atDay(1).atStartOfDay(ParkingTime.ZONE).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(ParkingTime.ZONE).toInstant();
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);

        Specification<ParkingSession> spec = SessionSpecifications.ofUser(userId)
                .and(SessionSpecifications.createdBetween(from, to));
        if (vehicleId != null) spec = spec.and(SessionSpecifications.vehicle(vehicleId));
        if (status != null) spec = spec.and(SessionSpecifications.status(status));

        List<ParkingSession> all = sessions.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<SessionResponse> mapped = mapper.map(all, true);

        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        long paidSessions = 0;
        long extensions = 0;
        String currency = "RSD";
        for (SessionResponse s : mapped) {
            currency = s.currency();
            if (s.status() == SessionStatus.ACTIVE || s.status() == SessionStatus.EXPIRED) paidSessions++;
            for (PaymentSummary p : s.payments()) {
                if (p.status() == TransactionStatus.SUCCESS) {
                    spent = spent.add(p.amount());
                    if (p.purpose() == TransactionPurpose.EXTENSION) extensions++;
                } else if (p.status() == TransactionStatus.REFUNDED) {
                    refunded = refunded.add(p.amount());
                }
            }
        }

        int total = mapped.size();
        int fromIndex = Math.min(pageIndex * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        int totalPages = (total + pageSize - 1) / pageSize;
        return new HistoryResponse(new Summary(spent, currency, paidSessions, extensions, refunded),
                mapped.subList(fromIndex, toIndex), pageIndex, pageSize, total, totalPages);
    }

    /** Poslednja placanja (pregled). */
    @Transactional(readOnly = true)
    public List<PaymentRow> recentPayments(UUID userId, int limit) {
        int n = Math.min(Math.max(limit, 1), 20);
        return transactions.findRecentByUser(userId, PageRequest.of(0, n)).stream()
                .map(t -> new PaymentRow(t.getMerchantOrderId(), t.getCreatedAt(), t.getSession().getPlate(),
                        t.getSession().getZone().getName(), t.getPurpose(), t.getPaymentMethod(), t.getAmount(),
                        t.getCurrency(), t.getStatus()))
                .toList();
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(ParkingTime.ZONE);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("INVALID_MONTH", "Period mora biti u formatu GGGG-MM.");
        }
    }
}
