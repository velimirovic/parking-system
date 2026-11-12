package com.sep.bank.paymentrequest;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sep.bank.account.CurrencyConverter;
import com.sep.bank.common.config.BankProperties;
import com.sep.bank.common.exception.ApiException;
import com.sep.bank.paymentrequest.PspApiDtos.CreatePaymentRequest;
import com.sep.bank.paymentrequest.PspApiDtos.CreatePaymentResponse;
import com.sep.bank.paymentrequest.PspApiDtos.PaymentStatus;
import com.sep.bank.transaction.BankTransaction;
import com.sep.bank.transaction.BankTransactionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Korak 3 iz specifikacije: banka proverava zahtev PSP-a (MERCHANT_ID, iznos, valuta) i generise
 * PAYMENT_ID i PAYMENT_URL. Isti MERCHANT_ID + STAN + PSP_TIMESTAMP vraca vec kreiran zahtev.
 */
@Slf4j
@Service
public class PaymentRequestService {

    private static final Duration MAX_TIMESTAMP_SKEW = Duration.ofMinutes(10);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final PaymentRequestRepository repository;
    private final AcquirerMerchantRepository merchantRepository;
    private final BankTransactionRepository transactionRepository;
    private final CurrencyConverter converter;
    private final BankProperties properties;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public PaymentRequestService(PaymentRequestRepository repository,
                                 AcquirerMerchantRepository merchantRepository,
                                 BankTransactionRepository transactionRepository,
                                 CurrencyConverter converter,
                                 BankProperties properties,
                                 PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.converter = converter;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public CreatePaymentResponse create(CreatePaymentRequest request) {
        AcquirerMerchant merchant = merchantRepository.findByMerchantCode(request.merchantId())
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_MERCHANT", "Nepoznat ili neaktivan MERCHANT_ID"));
        if (!converter.supports(request.currency())) {
            throw ApiException.badRequest("UNSUPPORTED_CURRENCY", "Banka ne podrzava valutu " + request.currency());
        }
        Instant now = Instant.now();
        if (Duration.between(request.pspTimestamp(), now).abs().compareTo(MAX_TIMESTAMP_SKEW) > 0) {
            throw ApiException.badRequest("INVALID_TIMESTAMP", "PSP_TIMESTAMP je van dozvoljenog opsega");
        }
        validateUrl("result_url", request.resultUrl());
        validateUrl("notify_url", request.notifyUrl());

        Instant pspTimestamp = request.pspTimestamp().truncatedTo(ChronoUnit.MICROS);
        Optional<PaymentRequest> existing = repository.findByMerchantCodeAndStanAndPspTimestamp(
                merchant.getMerchantCode(), request.stan(), pspTimestamp);
        if (existing.isPresent()) {
            return sameRequest(existing.get(), request);
        }
        try {
            PaymentRequest saved = tx.execute(status -> repository.saveAndFlush(newRequest(merchant, request,
                    pspTimestamp, now)));
            log.info("Kreiran zahtev za placanje {} (merchant {}, STAN {})", saved.getPaymentId(),
                    saved.getMerchantCode(), saved.getStan());
            return response(saved);
        } catch (DataIntegrityViolationException race) {
            PaymentRequest winner = repository.findByMerchantCodeAndStanAndPspTimestamp(merchant.getMerchantCode(),
                    request.stan(), pspTimestamp).orElseThrow(() -> race);
            return sameRequest(winner, request);
        }
    }

    /** Provera statusa po MERCHANT_ID + STAN + PSP_TIMESTAMP (kada PSP ne dobije odgovor banke). */
    public PaymentStatus status(String merchantId, String stan, Instant pspTimestamp) {
        PaymentRequest request = repository.findByMerchantCodeAndStanAndPspTimestamp(merchantId, stan,
                        pspTimestamp.truncatedTo(ChronoUnit.MICROS))
                .orElseThrow(() -> ApiException.notFound("Zahtev za placanje ne postoji"));
        return toStatus(request, transactionRepository.findPayment(request.getId()).orElse(null));
    }

    public static PaymentStatus toStatus(PaymentRequest r, BankTransaction t) {
        String status = switch (r.getStatus()) {
            case CREATED -> "CREATED";
            case COMPLETED -> "APPROVED";
            case FAILED -> "DECLINED";
            case EXPIRED -> "EXPIRED";
        };
        return new PaymentStatus(r.getPaymentId(), r.getMerchantCode(), r.getStan(), r.getPspTimestamp(), status,
                r.getResponseCode(), r.getAmount().toPlainString(), r.getCurrency(),
                t == null ? null : t.getGlobalTransactionId(), t == null ? null : t.getAcquirerTimestamp());
    }

    private CreatePaymentResponse sameRequest(PaymentRequest existing, CreatePaymentRequest request) {
        if (existing.getAmount().compareTo(request.amount()) != 0 || !existing.getCurrency().equals(request.currency())) {
            throw ApiException.conflict("DUPLICATE_STAN", "STAN je vec iskoriscen za drugi iznos ili valutu");
        }
        return response(existing);
    }

    private PaymentRequest newRequest(AcquirerMerchant merchant, CreatePaymentRequest request, Instant pspTimestamp,
                                      Instant now) {
        PaymentRequest r = new PaymentRequest();
        r.setPaymentId(newPaymentId());
        r.setMerchantCode(merchant.getMerchantCode());
        r.setStan(request.stan());
        r.setPspTimestamp(pspTimestamp);
        r.setAmount(request.amount().setScale(2));
        r.setCurrency(request.currency());
        r.setMerchantAccountId(merchant.getAccountId());
        r.setResultUrl(request.resultUrl());
        r.setNotifyUrl(request.notifyUrl());
        r.setExpiresAt(now.plus(properties.paymentTtlMinutes(), ChronoUnit.MINUTES));
        r.setAttemptUsed(false);
        r.setStatus(PaymentRequestStatus.CREATED);
        return r;
    }

    private CreatePaymentResponse response(PaymentRequest r) {
        String base = properties.paymentPageBaseUrl();
        String url = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/placanje/" + r.getPaymentId();
        return new CreatePaymentResponse(r.getPaymentId(), url, r.getExpiresAt());
    }

    /** PAY-XXXX-XXXX-XXXX-XXXX: 80 bita slucajnosti, jer PAYMENT_URL sam po sebi daje pristup formi. */
    private String newPaymentId() {
        StringBuilder sb = new StringBuilder("PAY");
        for (int group = 0; group < 4; group++) {
            sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
        }
        return sb.toString();
    }

    private static void validateUrl(String field, String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (uri.getHost() == null || !(scheme.equals("https") || scheme.equals("http"))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_URL", field + " nije ispravan URL");
        }
    }
}
