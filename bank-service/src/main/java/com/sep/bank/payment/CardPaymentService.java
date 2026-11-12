package com.sep.bank.payment;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sep.bank.account.Account;
import com.sep.bank.account.AccountRepository;
import com.sep.bank.account.CurrencyConverter;
import com.sep.bank.account.CurrencyConverter.Conversion;
import com.sep.bank.card.Card;
import com.sep.bank.card.CardBrand;
import com.sep.bank.card.CardRepository;
import com.sep.bank.card.CardRules;
import com.sep.bank.common.crypto.CardCrypto;
import com.sep.bank.common.crypto.Hmac;
import com.sep.bank.common.exception.ApiException;
import com.sep.bank.payment.PaymentDtos.CardPaymentForm;
import com.sep.bank.payment.PaymentDtos.PaymentFormView;
import com.sep.bank.payment.PaymentDtos.PaymentResult;
import com.sep.bank.paymentrequest.AcquirerMerchantRepository;
import com.sep.bank.paymentrequest.PaymentRequest;
import com.sep.bank.paymentrequest.PaymentRequestRepository;
import com.sep.bank.paymentrequest.PaymentRequestStatus;
import com.sep.bank.paymentrequest.PspNotifier;
import com.sep.bank.transaction.BankTransaction;
import com.sep.bank.transaction.BankTransactionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Koraci 4 i 5 iz specifikacije: kupac na stranici banke unosi PAN, SECURITY_CODE, CARD_HOLDER_NAME i
 * datum isteka; banka proverava karticu i, ako ima dovoljno sredstava, rezervise ih.
 *
 * <p>Pravila forme: PAN po Lunovoj formuli, datum MM/YY, iznos se uzima iskljucivo iz zahteva PSP-a
 * (ne sa forme), forma je vremenski ogranicena i dozvoljava samo jedan pokusaj placanja.
 * Neispravan format ne trosi pokusaj; svako stvarno slanje na autorizaciju ga trosi.
 */
@Slf4j
@Service
public class CardPaymentService {

    public static final String APPROVED = "00";
    public static final String DECLINED = "05";
    public static final String INVALID_CARD = "14";
    public static final String CUSTOMER_CANCELLED = "17";
    public static final String INSUFFICIENT_FUNDS = "51";
    public static final String EXPIRED_CARD = "54";
    public static final String BLOCKED_CARD = "62";

    private static final ZoneId ZONE = ZoneId.of("Europe/Belgrade");
    private static final DateTimeFormatter GTX_DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZONE);
    private static final List<String> ACCEPTED = Arrays.stream(CardBrand.values()).map(Enum::name).toList();

    private final PaymentRequestRepository requestRepository;
    private final AcquirerMerchantRepository merchantRepository;
    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final CurrencyConverter converter;
    private final CardCrypto cardCrypto;
    private final PspNotifier notifier;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public CardPaymentService(PaymentRequestRepository requestRepository,
                              AcquirerMerchantRepository merchantRepository,
                              CardRepository cardRepository,
                              AccountRepository accountRepository,
                              BankTransactionRepository transactionRepository,
                              CurrencyConverter converter,
                              CardCrypto cardCrypto,
                              PspNotifier notifier,
                              PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.merchantRepository = merchantRepository;
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.converter = converter;
        this.cardCrypto = cardCrypto;
        this.notifier = notifier;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public PaymentFormView view(String paymentId) {
        PaymentRequest r = requestRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> ApiException.notFound("Plaćanje ne postoji"));
        String merchantName = merchantRepository.findByMerchantCode(r.getMerchantCode())
                .map(m -> m.getName()).orElse(r.getMerchantCode());
        boolean expired = r.getStatus() == PaymentRequestStatus.CREATED && !Instant.now().isBefore(r.getExpiresAt());
        String status = expired ? PaymentRequestStatus.EXPIRED.name() : r.getStatus().name();
        boolean closed = !status.equals(PaymentRequestStatus.CREATED.name());
        return new PaymentFormView(r.getPaymentId(), merchantName, r.getAmount(), r.getCurrency(), status,
                r.isAttemptUsed(), r.getExpiresAt(), Instant.now(), ACCEPTED, closed ? r.getResultUrl() : null);
    }

    public PaymentResult pay(String paymentId, CardPaymentForm form) {
        ValidCard card = validateFormat(form);
        PaymentResult result = tx.execute(status -> {
            PaymentRequest request = claim(paymentId);
            return authorize(request, card);
        });
        notifier.notifyAsync(paymentId);
        return result;
    }

    /** Kupac odustaje na stranici banke - forma se zatvara i PSP dobija status DECLINED (kod 17). */
    public PaymentResult cancel(String paymentId) {
        PaymentResult result = tx.execute(status -> {
            PaymentRequest request = claim(paymentId);
            finish(request, PaymentRequestStatus.FAILED, CUSTOMER_CANCELLED);
            return new PaymentResult("DECLINED", CUSTOMER_CANCELLED, "Plaćanje je otkazano.", request.getResultUrl());
        });
        notifier.notifyAsync(paymentId);
        return result;
    }

    // ---------------------------------------------------------------------------------------------

    /** Zauzima jedini pokusaj; ako je vec iskoriscen ili je forma istekla, kupac dobija gresku. */
    private PaymentRequest claim(String paymentId) {
        Instant now = Instant.now();
        if (requestRepository.claimAttempt(paymentId, now) == 1) {
            return requestRepository.findByPaymentId(paymentId).orElseThrow();
        }
        PaymentRequest r = requestRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> ApiException.notFound("Plaćanje ne postoji"));
        Map<String, Object> details = Map.of("resultUrl", r.getResultUrl());
        if (r.getStatus() == PaymentRequestStatus.CREATED && !now.isBefore(r.getExpiresAt())
                || r.getStatus() == PaymentRequestStatus.EXPIRED) {
            throw new ApiException(HttpStatus.GONE, "PAYMENT_EXPIRED", "Vreme za plaćanje je isteklo.", details);
        }
        throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_USED",
                "Ova forma je već iskorišćena. Dozvoljen je samo jedan pokušaj plaćanja.", details);
    }

    private PaymentResult authorize(PaymentRequest request, ValidCard input) {
        String panToken = cardCrypto.panToken(input.pan());
        Optional<Card> found = cardRepository.findByPanToken(panToken);
        String code = APPROVED;
        Card card = found.orElse(null);
        if (card == null || card.getBrand() != input.brand()
                || card.getExpiryMonth() != input.expiry().getMonthValue()
                || card.getExpiryYear() != input.expiry().getYear()
                || !CardRules.normalizeHolder(card.getCardHolderName()).equals(CardRules.normalizeHolder(input.holder()))
                || !Hmac.matches(card.getCvvHash(), cardCrypto.cvvHash(panToken, input.securityCode()))) {
            code = INVALID_CARD;
        } else if (!"ACTIVE".equals(card.getStatus())) {
            code = BLOCKED_CARD;
        } else if (!CardRules.notExpired(input.expiry(), LocalDate.now(ZONE))) {
            code = EXPIRED_CARD;
        }

        Account payer = card == null ? null : accountRepository.findById(card.getAccountId()).orElse(null);
        Conversion debit = null;
        if (code.equals(APPROVED)) {
            debit = converter.convert(request.getAmount(), request.getCurrency(), payer.getCurrency());
            if (accountRepository.reserve(payer.getId(), debit.amount()) == 0) {
                code = INSUFFICIENT_FUNDS;
            }
        }
        boolean approved = code.equals(APPROVED);

        BankTransaction t = new BankTransaction();
        t.setPaymentRequestId(request.getId());
        t.setPayerAccountId(payer == null ? null : payer.getId());
        t.setPayeeAccountId(request.getMerchantAccountId());
        t.setGlobalTransactionId(globalTransactionId());
        t.setAmount(request.getAmount());
        t.setCurrency(request.getCurrency());
        if (debit != null) {
            t.setDebitAmount(debit.amount());
            t.setDebitCurrency(payer.getCurrency());
            t.setExchangeRate(debit.rate());
        }
        t.setType("CARD");
        t.setStatus(approved ? "RESERVED" : "DECLINED");
        t.setResponseCode(code);
        t.setCardLast4(input.pan().substring(input.pan().length() - 4));
        t.setAcquirerTimestamp(Instant.now());
        transactionRepository.save(t);

        // request je posle claimAttempt/reserve (clearAutomatically) odvojen od konteksta - ponovo ga ucitaj
        PaymentRequest fresh = requestRepository.findByPaymentId(request.getPaymentId()).orElseThrow();
        finish(fresh, approved ? PaymentRequestStatus.COMPLETED : PaymentRequestStatus.FAILED, code);
        log.info("Placanje {}: {} (kod {}, kartica *{})", request.getPaymentId(), approved ? "odobreno" : "odbijeno",
                code, t.getCardLast4());
        return new PaymentResult(approved ? "APPROVED" : "DECLINED", code, message(code), request.getResultUrl());
    }

    private void finish(PaymentRequest request, PaymentRequestStatus status, String code) {
        request.setStatus(status);
        request.setResponseCode(code);
        request.setCompletedAt(Instant.now());
        request.setNextNotifyAt(Instant.now());
        requestRepository.save(request);
    }

    /** Provera formata pre slanja na autorizaciju - neispravan unos ne trosi pokusaj placanja. */
    private ValidCard validateFormat(CardPaymentForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        String pan = CardRules.normalizePan(form == null ? null : form.pan());
        Optional<CardBrand> brand = CardBrand.detect(pan);
        if (!CardRules.validPan(pan)) {
            errors.put("pan", "Neispravan broj kartice");
        } else if (brand.isEmpty()) {
            errors.put("pan", "Banka ne prihvata ovu vrstu kartice");
        }
        Optional<YearMonth> expiry = CardRules.parseExpiry(form == null ? null : form.expiry());
        if (expiry.isEmpty()) {
            errors.put("expiry", "Datum mora biti u formatu MM/YY");
        } else if (!CardRules.notExpired(expiry.get(), LocalDate.now(ZONE))) {
            errors.put("expiry", "Kartica je istekla");
        }
        if (!CardRules.validSecurityCode(form == null ? null : form.securityCode())) {
            errors.put("securityCode", "Sigurnosni kod ima 3 cifre");
        }
        if (!CardRules.validHolderName(form == null ? null : form.cardHolderName())) {
            errors.put("cardHolderName", "Unesite ime vlasnika kao na kartici");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Neispravni podaci kartice",
                    Map.of("fields", errors));
        }
        return new ValidCard(pan, brand.get(), expiry.get(), form.securityCode(), form.cardHolderName().trim());
    }

    private String globalTransactionId() {
        String chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder("GTX-").append(GTX_DATE.format(Instant.now())).append('-');
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String message(String code) {
        return switch (code) {
            case APPROVED -> "Plaćanje je odobreno.";
            case INSUFFICIENT_FUNDS -> "Plaćanje je odbijeno: nema dovoljno sredstava na računu.";
            case EXPIRED_CARD -> "Plaćanje je odbijeno: kartica je istekla.";
            case BLOCKED_CARD -> "Plaćanje je odbijeno: kartica je blokirana.";
            default -> "Plaćanje je odbijeno: podaci kartice nisu ispravni.";
        };
    }

    private record ValidCard(String pan, CardBrand brand, YearMonth expiry, String securityCode, String holder) {

        @Override
        public String toString() {
            return "ValidCard[brand=" + brand + "]";
        }
    }
}
