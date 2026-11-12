package com.sep.bank.paymentrequest;

public enum PaymentRequestStatus {
    /** Forma je otvorena i ceka unos kartice. */
    CREATED,
    /** Placanje odobreno, sredstva rezervisana. */
    COMPLETED,
    /** Placanje odbijeno ili je kupac odustao. */
    FAILED,
    /** Isteklo vreme za placanje. */
    EXPIRED
}
