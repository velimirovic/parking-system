package com.sep.parking.session;

/** Sta je parking sistem uradio sa javljenim ishodom placanja. */
public enum PaymentOutcome {
    /** placanje potvrdjeno, sesija aktivirana */
    ACTIVATED,
    /** placanje neuspesno / isteklo / greska, sesija otkazana */
    CANCELLED,
    /** placanje uspelo, a sesija nije mogla biti aktivirana - oznaceno za refundaciju */
    REFUND_REQUIRED,
    /** PSP je potvrdio drugi iznos ili valutu - sesija se ne aktivira */
    AMOUNT_MISMATCH,
    /** placanje jos nije zavrseno */
    STILL_OPEN,
    /** konacan status je vec obradjen (ponovljen webhook, paralelna provera) */
    DUPLICATE,
    /** nalog ne postoji u parking sistemu */
    UNKNOWN_ORDER,
    /** potvrda placanja produzenja - obradjuje je modul produzenja */
    EXTENSION_RECORDED
}
