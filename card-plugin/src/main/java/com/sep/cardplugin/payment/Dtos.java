package com.sep.cardplugin.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Poruke plugin-a: od/ka PSP Core-u (camelCase, interni ugovor) i od/ka banci (snake_case, tabela 2). */
public final class Dtos {

    private Dtos() {
    }

    // ---------- PSP Core -> plugin ----------

    public record InitiateRequest(
            UUID pspTransactionId,
            String merchantId,
            BigDecimal amount,
            String currency,
            Map<String, String> merchantConfig,
            String returnUrl,
            Instant expiresAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InitiateResponse(String status, String redirectUrl, String message) {

        static InitiateResponse pending(String url) {
            return new InitiateResponse("PENDING", url, null);
        }

        static InitiateResponse failed(String message) {
            return new InitiateResponse("FAILED", null, message);
        }
    }

    // ---------- plugin -> PSP Core ----------

    /** Konacan rezultat placanja. status: SUCCESS, FAILED, EXPIRED ili ERROR. */
    public record PaymentResult(
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

    // ---------- plugin <-> banka ----------

    public record BankCreateRequest(
            @JsonProperty("merchant_id") String merchantId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("stan") String stan,
            @JsonProperty("psp_timestamp") Instant pspTimestamp,
            @JsonProperty("result_url") String resultUrl,
            @JsonProperty("notify_url") String notifyUrl) {
    }

    public record BankCreateResponse(
            @JsonProperty("payment_id") String paymentId,
            @JsonProperty("payment_url") String paymentUrl,
            @JsonProperty("expires_at") Instant expiresAt) {
    }

    /** Status od banke (obavestenje ili odgovor na proveru). status: CREATED, APPROVED, DECLINED, EXPIRED. */
    public record BankStatus(
            @JsonProperty("payment_id") String paymentId,
            @JsonProperty("merchant_id") String merchantId,
            @JsonProperty("stan") String stan,
            @JsonProperty("psp_timestamp") Instant pspTimestamp,
            @JsonProperty("status") String status,
            @JsonProperty("response_code") String responseCode,
            @JsonProperty("amount") String amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("global_transaction_id") String globalTransactionId,
            @JsonProperty("acquirer_timestamp") Instant acquirerTimestamp) {
    }
}
