package com.sep.parking.session;

/**
 * PENDING_PAYMENT -> ACTIVE -> EXPIRED; PENDING_PAYMENT -> CANCELLED.
 * Sesija postaje ACTIVE iskljucivo posle potvrde uspesnog placanja od PSP-a.
 */
public enum SessionStatus {
    PENDING_PAYMENT,
    ACTIVE,
    EXPIRED,
    CANCELLED
}
