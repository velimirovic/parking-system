package com.sep.psp.plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.common.config.PaymentProperties;
import com.sep.psp.common.crypto.Hmac;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.transaction.PspTransaction;
import com.sep.psp.transaction.PspTransactionRepository;
import com.sep.psp.transaction.TransactionStatus;
import com.sep.psp.transaction.TransactionStatusService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Plugin javlja konacan rezultat placanja. Core cuva STAN, GLOBAL_TRANSACTION_ID i ACQUIRER_TIMESTAMP,
 * zatvara transakciju i u istoj DB transakciji upisuje webhook za klijenta (outbox).
 * Ponovljen rezultat ne menja nista (idempotentno), pa plugin sme da ga salje dok ne dobije 2xx.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PluginResultController {

    private final PspTransactionRepository repository;
    private final TransactionStatusService statusService;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    @PostMapping(path = "/internal/plugins/{code}/results", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> result(@PathVariable String code, @RequestBody String body, HttpServletRequest request) {
        verify(request, "/internal/plugins/" + code + "/results", body);
        PluginResult result;
        try {
            result = objectMapper.readValue(body, PluginResult.class);
        } catch (JacksonException e) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Telo zahteva nije ispravan JSON");
        }
        PspTransaction t = repository.findByIdForUpdate(result.pspTransactionId())
                .orElseThrow(() -> ApiException.notFound("Transakcija ne postoji"));
        if (t.getPaymentMethod() == null || !t.getPaymentMethod().getCode().equals(code)) {
            throw ApiException.conflict("METHOD_MISMATCH", "Transakcija nije placena ovom metodom");
        }
        if (TransactionStatusService.FINAL.contains(t.getStatus())) {
            return ResponseEntity.noContent().build();
        }

        TransactionStatus status = switch (result.status() == null ? "" : result.status()) {
            case "SUCCESS" -> TransactionStatus.SUCCESS;
            case "EXPIRED" -> TransactionStatus.EXPIRED;
            case "FAILED" -> TransactionStatus.FAILED;
            default -> TransactionStatus.ERROR;
        };
        String reason = "plugin " + code + (result.responseCode() == null ? "" : ", kod " + result.responseCode());
        // iznos i valuta moraju biti isti kao pri inicijalizaciji (specijalni slucaj 1 - promena iznosa)
        if (status == TransactionStatus.SUCCESS && (result.amount() == null
                || t.getAmount().compareTo(result.amount()) != 0 || !t.getCurrency().equals(result.currency()))) {
            log.error("Plugin {} je potvrdio drugi iznos/valutu za transakciju {}", code, t.getId());
            status = TransactionStatus.ERROR;
            reason = "iznos ili valuta se ne poklapaju";
        }
        t.setStan(result.stan());
        t.setPspTimestamp(result.pspTimestamp());
        t.setGlobalTransactionId(result.globalTransactionId());
        t.setAcquirerTimestamp(result.acquirerTimestamp());
        statusService.complete(t, status, reason);
        return ResponseEntity.noContent().build();
    }

    private void verify(HttpServletRequest request, String path, String body) {
        String timestamp = request.getHeader(PaymentPluginClient.HEADER_TIMESTAMP);
        String signature = request.getHeader(PaymentPluginClient.HEADER_SIGNATURE);
        boolean fresh;
        try {
            fresh = timestamp != null
                    && Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) <= properties.maxClockSkewSeconds();
        } catch (NumberFormatException e) {
            fresh = false;
        }
        if (!fresh || !Hmac.matches(Hmac.sha256Hex(properties.pluginSharedSecret(),
                Hmac.canonicalRequest(timestamp, request.getMethod(), path, body)), signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Neispravan potpis plugin-a");
        }
    }

    public record PluginResult(
            UUID pspTransactionId,
            String status,
            BigDecimal amount,
            String currency,
            String stan,
            Instant pspTimestamp,
            String globalTransactionId,
            Instant acquirerTimestamp,
            String responseCode) {
    }
}
