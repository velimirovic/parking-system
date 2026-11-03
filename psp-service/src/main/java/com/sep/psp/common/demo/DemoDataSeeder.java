package com.sep.psp.common.demo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.auth.AuthService;
import com.sep.psp.auth.PspUser;
import com.sep.psp.auth.PspUserRepository;
import com.sep.psp.auth.Role;
import com.sep.psp.common.config.DemoDataProperties;
import com.sep.psp.common.crypto.AesGcmEncryptor;
import com.sep.psp.common.crypto.Hashing;
import com.sep.psp.common.crypto.SecretGenerator;
import com.sep.psp.merchant.Merchant;
import com.sep.psp.merchant.MerchantRepository;
import com.sep.psp.merchant.MerchantStatus;
import com.sep.psp.paymentmethod.MerchantPaymentMethod;
import com.sep.psp.paymentmethod.MerchantPaymentMethodRepository;
import com.sep.psp.paymentmethod.PaymentMethod;
import com.sep.psp.paymentmethod.PaymentMethodRepository;
import com.sep.psp.transaction.MerchantTransactionService;
import com.sep.psp.transaction.PspTransaction;
import com.sep.psp.transaction.PspTransactionRepository;
import com.sep.psp.transaction.TransactionStatus;
import com.sep.psp.webhook.WebhookDelivery;
import com.sep.psp.webhook.WebhookDeliveryRepository;
import com.sep.psp.webhook.WebhookStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Demo klijenti i transakcije za prikaz portala dok ostali moduli (inicijalizacija transakcija,
 * plugin-ovi, webhook-ovi) nisu gotovi. Pokrece se samo nad praznom bazom; iskljucuje se sa
 * DEMO_DATA_ENABLED=false.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MerchantRepository merchantRepository;
    private final PspUserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final MerchantPaymentMethodRepository merchantMethodRepository;
    private final PspTransactionRepository transactionRepository;
    private final WebhookDeliveryRepository webhookRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final SecretGenerator secretGenerator;
    private final AesGcmEncryptor encryptor;
    private final ObjectMapper objectMapper;
    private final DemoDataProperties properties;

    private final Random random = new Random(2026);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (merchantRepository.count() > 0) {
            return;
        }
        Map<String, PaymentMethod> methods = new LinkedHashMap<>();
        paymentMethodRepository.findAll().forEach(m -> methods.put(m.getCode(), m));

        Merchant parking = merchant("Maluvufy Parking d.o.o.", 200, MerchantStatus.ACTIVE,
                properties.parkingApiKey(), properties.parkingWebhookSecret(),
                "http://parking-service:8081/psp/callback",
                "http://localhost:4200/placanje/uspeh",
                "http://localhost:4200/placanje/neuspeh",
                "http://localhost:4200/placanje/greska");
        subscribe(parking, methods.get("CARD"), true, Map.of("bankMerchantId", "BNK-PSP-007781"));
        subscribe(parking, methods.get("QR"), true,
                Map.of("accountNumber", "160-0000000123456-21", "recipientName", "Maluvufy Parking d.o.o."));
        subscribe(parking, methods.get("PAYPAL"), false, Map.of("clientId", "AXd-sandbox-parking-client-91F"));
        subscribe(parking, methods.get("CRYPTO"), true,
                Map.of("walletAddress", "tb1qparking7x9m2k4d8w3s0vq5f6n1zr2a8c3"));
        admin(parking, "ana.jovanovic@maluvufy.rs", "Ana Jovanović");

        Merchant webshop = merchant("Demo Webshop", 100, MerchantStatus.ACTIVE, null, null,
                "https://webshop.sep.rs/api/psp/callback", "https://webshop.sep.rs/checkout/uspeh",
                "https://webshop.sep.rs/checkout/neuspeh", "https://webshop.sep.rs/checkout/greska");
        subscribe(webshop, methods.get("CARD"), true, Map.of("bankMerchantId", "BNK-PSP-007782"));
        subscribe(webshop, methods.get("PAYPAL"), true, Map.of("clientId", "AXd-sandbox-webshop-client-3C7"));
        admin(webshop, "marko.petrovic@webshop.sep.rs", "Marko Petrović");

        Merchant garage = merchant("Gradska garaža", 50, MerchantStatus.ACTIVE, null, null,
                "https://garaza.sep.rs/psp/callback", null, null, null);
        subscribe(garage, methods.get("CARD"), true, Map.of());
        admin(garage, "jelena.ilic@garaza.sep.rs", "Jelena Ilić");

        Merchant market = merchant("Test Market", 50, MerchantStatus.INACTIVE, null, null,
                "https://market.sep.rs/psp/callback", null, null, null);
        subscribe(market, methods.get("QR"), true, Map.of());
        admin(market, "test@market.sep.rs", "Test Market admin");

        transactions(parking, "PRK", List.of(methods.get("CARD"), methods.get("QR"), methods.get("CRYPTO"),
                methods.get("PAYPAL")), 48);
        transactions(webshop, "WSH", List.of(methods.get("CARD"), methods.get("PAYPAL")), 18);

        auditService.record(AuditService.ACTOR_SYSTEM, "demo-data", AuditAction.MERCHANT_CREATE, "MERCHANT",
                "demo", AuditOutcome.SUCCESS, "Ucitani demo klijenti i transakcije");
        log.warn("Ucitani demo podaci za PSP portal (DEMO_DATA_ENABLED=false za iskljucivanje)");
    }

    private Merchant merchant(String name, int rateLimit, MerchantStatus status, String apiKey, String webhookSecret,
                              String webhookUrl, String successUrl, String failedUrl, String errorUrl) {
        String key = apiKey != null ? apiKey : secretGenerator.apiKey();
        String secret = webhookSecret != null ? webhookSecret : secretGenerator.webhookSecret();
        Merchant merchant = new Merchant();
        merchant.setName(name);
        merchant.setMerchantCode(String.format("MRC-%05d", merchantRepository.nextMerchantNumber()));
        merchant.setApiKeyHash(Hashing.sha256Hex(key));
        merchant.setApiKeyLast4(Hashing.last4(key));
        merchant.setWebhookSecretEncrypted(encryptor.encrypt(secret));
        merchant.setWebhookSecretLast4(Hashing.last4(secret));
        merchant.setWebhookUrl(webhookUrl);
        merchant.setSuccessUrl(successUrl);
        merchant.setFailedUrl(failedUrl);
        merchant.setErrorUrl(errorUrl);
        merchant.setRateLimitPerSec(rateLimit);
        merchant.setStatus(status);
        return merchantRepository.save(merchant);
    }

    private void subscribe(Merchant merchant, PaymentMethod method, boolean active, Map<String, String> config) {
        MerchantPaymentMethod mpm = new MerchantPaymentMethod();
        mpm.setMerchantId(merchant.getId());
        mpm.setPaymentMethod(method);
        mpm.setActive(active);
        mpm.setConfigEncrypted(config.isEmpty() ? null : encryptor.encrypt(objectMapper.writeValueAsString(config)));
        merchantMethodRepository.save(mpm);
    }

    private void admin(Merchant merchant, String email, String fullName) {
        PspUser user = new PspUser();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(Role.MERCHANT_ADMIN);
        user.setMerchantId(merchant.getId());
        user.setMustChangePassword(true);
        user.setEnabled(true);
        user.setPasswordHash("");
        userRepository.save(user);
        authService.applyNewPassword(user, properties.merchantPassword());
    }

    private void transactions(Merchant merchant, String prefix, List<PaymentMethod> methods, int count) {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(MerchantTransactionService.ZONE)
                .atStartOfDay(MerchantTransactionService.ZONE).toInstant();
        long secondsToday = Math.max(Duration.between(startOfToday, now).getSeconds(), 600);
        for (int i = 0; i < count; i++) {
            // dve trecine transakcija su danasnje, ostale iz prethodna tri dana
            Instant created = i < count * 2 / 3
                    ? now.minusSeconds(60 + (long) (random.nextDouble() * (secondsToday - 60)))
                    : startOfToday.minus(1 + random.nextInt(3 * 24 * 60), ChronoUnit.MINUTES);
            PaymentMethod method = methods.get(random.nextInt(methods.size()));
            TransactionStatus status = pickStatus(created, now);

            PspTransaction t = new PspTransaction();
            t.setMerchantId(merchant.getId());
            t.setPaymentMethod(status == TransactionStatus.INIT ? null : method);
            t.setMerchantOrderId(prefix + "-" + created.atZone(MerchantTransactionService.ZONE).format(ORDER_DATE)
                    + "-" + HexFormat.of().withUpperCase().formatHex(secretGenerator.randomBytes(3)));
            t.setMerchantTimestamp(created.minusMillis(150));
            boolean paypal = method.getCode().equals("PAYPAL");
            t.setCurrency(paypal ? "EUR" : "RSD");
            t.setAmount(paypal ? BigDecimal.valueOf(1 + random.nextInt(4)).setScale(2)
                    : BigDecimal.valueOf(60L * (1 + random.nextInt(6))).setScale(2));
            t.setStatus(status);
            t.setPspTimestamp(created);
            t.setExpiresAt(created.plus(15, ChronoUnit.MINUTES));
            if (method.getCode().equals("CARD") || method.getCode().equals("QR")) {
                t.setStan(String.format("%06d", random.nextInt(1_000_000)));
            }
            boolean settled = status == TransactionStatus.SUCCESS || status == TransactionStatus.REFUNDED;
            if (settled) {
                t.setAcquirerTimestamp(created.plusSeconds(20 + random.nextInt(40)));
                t.setGlobalTransactionId(globalId(method.getCode()));
            }
            t.setCreatedAt(created);
            t.setUpdatedAt(t.getAcquirerTimestamp() != null ? t.getAcquirerTimestamp() : created.plusSeconds(30));
            transactionRepository.save(t);

            if (status != TransactionStatus.INIT && status != TransactionStatus.PENDING) {
                delivery(t, status);
            }
        }
    }

    private TransactionStatus pickStatus(Instant created, Instant now) {
        boolean recent = Duration.between(created, now).toMinutes() < 15;
        int roll = random.nextInt(100);
        if (recent && roll < 30) {
            return roll < 10 ? TransactionStatus.INIT : TransactionStatus.PENDING;
        }
        if (roll < 74) {
            return TransactionStatus.SUCCESS;
        }
        if (roll < 86) {
            return TransactionStatus.FAILED;
        }
        if (roll < 92) {
            return TransactionStatus.EXPIRED;
        }
        if (roll < 96) {
            return TransactionStatus.REFUNDED;
        }
        return TransactionStatus.ERROR;
    }

    private String globalId(String methodCode) {
        return switch (methodCode) {
            case "PAYPAL" -> "8" + HexFormat.of().withUpperCase().formatHex(secretGenerator.randomBytes(8)).substring(1);
            case "CRYPTO" -> HexFormat.of().formatHex(secretGenerator.randomBytes(32));
            default -> String.format("GTX-%04d-%04d-%04d", random.nextInt(10_000), random.nextInt(10_000),
                    random.nextInt(10_000));
        };
    }

    private void delivery(PspTransaction t, TransactionStatus status) {
        String event = switch (status) {
            case SUCCESS -> "PAYMENT_SUCCESS";
            case REFUNDED -> "REFUND_SUCCESS";
            case ERROR -> "PAYMENT_ERROR";
            default -> "PAYMENT_FAILED";
        };
        WebhookDelivery d = new WebhookDelivery();
        d.setTransactionId(t.getId());
        d.setEventType(event);
        d.setPayload("{\"merchant_order_id\":\"" + t.getMerchantOrderId() + "\",\"status\":\"" + status + "\"}");
        d.setMaxAttempts(8);
        int roll = random.nextInt(100);
        if (roll < 8) {
            d.setStatus(WebhookStatus.PENDING);
            d.setAttemptCount(2 + random.nextInt(3));
            d.setLastHttpStatus(503);
            d.setLastDurationMs(5000);
            d.setNextAttemptAt(Instant.now().plusSeconds(30 + random.nextInt(240)));
        } else {
            d.setStatus(WebhookStatus.DELIVERED);
            d.setAttemptCount(roll < 18 ? 2 : 1);
            d.setLastHttpStatus(200);
            d.setLastDurationMs(40 + random.nextInt(160));
        }
        d.setCreatedAt(t.getUpdatedAt());
        webhookRepository.save(d);
    }
}
