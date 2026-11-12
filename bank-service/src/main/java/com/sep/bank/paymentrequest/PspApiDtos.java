package com.sep.bank.paymentrequest;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API banke za PSP. Polja prate tabelu 2 i korake 5-6 iz specifikacije. */
public final class PspApiDtos {

    private PspApiDtos() {
    }

    /** Tabela 2 + adrese za povratak kupca i obavestenje PSP-a o rezultatu. */
    public record CreatePaymentRequest(
            @JsonProperty("merchant_id") @NotBlank @Size(max = 32) String merchantId,
            @JsonProperty("amount") @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @JsonProperty("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @JsonProperty("stan") @NotBlank @Pattern(regexp = "\\d{6}", message = "STAN mora imati 6 cifara") String stan,
            @JsonProperty("psp_timestamp") @NotNull Instant pspTimestamp,
            @JsonProperty("result_url") @NotBlank @Size(max = 1000) String resultUrl,
            @JsonProperty("notify_url") @NotBlank @Size(max = 1000) String notifyUrl) {
    }

    public record CreatePaymentResponse(
            @JsonProperty("payment_id") String paymentId,
            @JsonProperty("payment_url") String paymentUrl,
            @JsonProperty("expires_at") Instant expiresAt) {
    }

    /**
     * Status placanja (odgovor na proveru statusa i telo obavestenja PSP-u).
     *
     * @param status        CREATED (ceka kupca), APPROVED, DECLINED, EXPIRED
     * @param responseCode  00 odobreno, 05 odbijeno, 14 neispravna kartica, 17 kupac odustao,
     *                      51 nedovoljno sredstava, 54 istekla kartica, 62 kartica blokirana
     */
    public record PaymentStatus(
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
