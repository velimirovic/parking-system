package com.sep.parking.session;

import com.sep.parking.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ishod placanja kako ga javlja PSP (webhook ili provera statusa).
 *
 * @param acquirerTimestamp trenutak kada je banka odobrila placanje (ACQUIRER_TIMESTAMP), ako ga PSP posalje
 */
public record PaymentResult(TransactionStatus status, BigDecimal amount, String currency, String paymentMethod,
                            String pspTransactionId, String globalTransactionId, Instant acquirerTimestamp) {
}
