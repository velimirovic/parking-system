package com.sep.parking.payment;

/** Greska u komunikaciji sa PSP-om. */
public class PspException extends RuntimeException {

    /** PSP nije dostupan / timeout / 5xx - zahtev se moze ponoviti (PSP je idempotentan po nalogu). */
    public static class Unavailable extends PspException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** PSP je odbio zahtev (4xx) - ponavljanje istog zahteva nema smisla. */
    public static class Rejected extends PspException {
        private final int status;
        private final String code;

        public Rejected(int status, String code, String message) {
            super(message, null);
            this.status = status;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }

    protected PspException(String message, Throwable cause) {
        super(message, cause);
    }
}
