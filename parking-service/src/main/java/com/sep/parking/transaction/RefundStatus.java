package com.sep.parking.transaction;

/**
 * REQUIRED - placanje je uspelo, a parking sesija nije mogla biti aktivirana; iznos treba vratiti
 * istim nacinom placanja. REFUNDED - povracaj je izvrsen.
 */
public enum RefundStatus {
    REQUIRED,
    REFUNDED
}
