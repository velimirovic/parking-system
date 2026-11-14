package com.sep.parking.transaction;

/** Statusi uskladjeni sa PSP-om (INIT, PENDING, SUCCESS, FAILED, ERROR, EXPIRED) + REFUNDED. */
public enum TransactionStatus {
    INIT,
    PENDING,
    SUCCESS,
    FAILED,
    ERROR,
    EXPIRED,
    REFUNDED;

    public boolean isOpen() {
        return this == INIT || this == PENDING;
    }

    public static TransactionStatus fromPsp(String value) {
        if (value == null) return INIT;
        try {
            return TransactionStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ERROR;
        }
    }
}
