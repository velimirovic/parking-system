package com.sep.parking.transaction;

import com.sep.parking.common.config.ParkingTime;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * MERCHANT_ORDER_ID: "PRK-" + datum + "-" + 6 nasumicnih heks znakova (npr. PRK-20261101-7F3A92).
 * Generise se nasumicno (SecureRandom) da se ne bi mogao pogoditi.
 */
@Component
public class OrderIdGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom random = new SecureRandom();
    private final PaymentTransactionRepository transactions;
    private final Clock clock;

    public OrderIdGenerator(PaymentTransactionRepository transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    public String next() {
        String day = LocalDate.now(clock.withZone(ParkingTime.ZONE)).format(DAY);
        for (int attempt = 0; attempt < 5; attempt++) {
            byte[] bytes = new byte[3];
            random.nextBytes(bytes);
            String id = "PRK-" + day + "-" + HexFormat.of().withUpperCase().formatHex(bytes);
            if (!transactions.existsByMerchantOrderId(id)) {
                return id;
            }
        }
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        return "PRK-" + day + "-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
