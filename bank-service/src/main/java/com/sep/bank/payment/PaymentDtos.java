package com.sep.bank.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Podaci za stranicu banke na kojoj kupac unosi karticu (B01 na dizajnu). */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentFormView(
            String paymentId,
            String merchantName,
            BigDecimal amount,
            String currency,
            String status,
            boolean attemptUsed,
            Instant expiresAt,
            Instant serverTime,
            List<String> acceptedBrands,
            /* povratak na PSP - samo kada je placanje zavrseno */
            String resultUrl) {
    }

    /**
     * Podaci kartice sa forme. Nikad se ne loguju niti cuvaju u ovom obliku (PCI DSS 3.3) -
     * toString namerno ne prikazuje PAN ni sigurnosni kod.
     */
    public record CardPaymentForm(String pan, String securityCode, String cardHolderName, String expiry) {

        @Override
        public String toString() {
            return "CardPaymentForm[pan=****, securityCode=***, cardHolderName=***, expiry=**/**]";
        }
    }

    /**
     * @param status       APPROVED ili DECLINED
     * @param responseCode kod odgovora banke (00 = odobreno)
     * @param message      poruka za kupca (ne otkriva koji podatak kartice je pogresan)
     * @param redirectUrl  povratak kupca na PSP
     */
    public record PaymentResult(String status, String responseCode, String message, String redirectUrl) {
    }
}
