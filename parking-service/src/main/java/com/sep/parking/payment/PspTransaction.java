package com.sep.parking.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/** Odgovor PSP-a za POST /transactions i GET /transactions/{merchant_order_id}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PspTransaction(
        @JsonProperty("psp_transaction_id") String pspTransactionId,
        @JsonProperty("merchant_order_id") String merchantOrderId,
        @JsonProperty("status") String status,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("redirect_url") String redirectUrl,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("global_transaction_id") String globalTransactionId,
        @JsonProperty("acquirer_timestamp") Instant acquirerTimestamp,
        @JsonProperty("created_at") Instant createdAt) {
}
