package com.sep.psp.merchantapi;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.psp.transaction.PspTransaction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Javni API za klijente PSP-a. Polja su u snake_case obliku, po uzoru na parametre iz tabele 1.
 * MERCHANT_ID i API_KEY idu u zaglavljima, a SUCCESS/FAILED/ERROR URL-ovi su sacuvani kod klijenta u PSP-u.
 */
public final class MerchantApiDtos {

    private MerchantApiDtos() {
    }

    public record InitTransactionRequest(
            @JsonProperty("merchant_order_id")
            @NotBlank(message = "merchant_order_id je obavezan")
            @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "merchant_order_id: 1-64 znaka (slova, cifre, - i _)")
            String merchantOrderId,

            @JsonProperty("merchant_timestamp")
            @NotNull(message = "merchant_timestamp je obavezan (ISO-8601)")
            Instant merchantTimestamp,

            @JsonProperty("amount")
            @NotNull(message = "amount je obavezan")
            @DecimalMin(value = "0.01", message = "amount mora biti veci od 0")
            @DecimalMax(value = "10000000", message = "amount je prevelik")
            @Digits(integer = 8, fraction = 2, message = "amount moze imati najvise 2 decimale")
            BigDecimal amount,

            @JsonProperty("currency")
            @NotBlank(message = "currency je obavezan")
            @Pattern(regexp = "[A-Z]{3}", message = "currency mora biti ISO 4217 kod (npr. RSD, EUR)")
            String currency,

            @JsonProperty("description")
            @Size(max = 200, message = "description moze imati najvise 200 znakova")
            String description) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TransactionResponse(
            @JsonProperty("psp_transaction_id") String pspTransactionId,
            @JsonProperty("merchant_order_id") String merchantOrderId,
            @JsonProperty("status") String status,
            @JsonProperty("amount") String amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("payment_method") String paymentMethod,
            @JsonProperty("redirect_url") String redirectUrl,
            @JsonProperty("expires_at") Instant expiresAt,
            @JsonProperty("global_transaction_id") String globalTransactionId,
            @JsonProperty("acquirer_timestamp") Instant acquirerTimestamp,
            @JsonProperty("created_at") Instant createdAt) {

        static TransactionResponse from(PspTransaction t, String redirectUrl) {
            return new TransactionResponse(
                    t.getId().toString(),
                    t.getMerchantOrderId(),
                    t.getStatus().name(),
                    t.getAmount().toPlainString(),
                    t.getCurrency(),
                    t.getPaymentMethod() == null ? null : t.getPaymentMethod().getCode(),
                    redirectUrl,
                    t.getExpiresAt(),
                    t.getGlobalTransactionId(),
                    t.getAcquirerTimestamp(),
                    t.getCreatedAt());
        }
    }
}
