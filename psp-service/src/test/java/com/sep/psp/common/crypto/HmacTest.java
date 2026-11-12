package com.sep.psp.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HmacTest {

    @Test
    void matchesRfc4231TestVector() {
        // RFC 4231, test case 2
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                Hmac.sha256Hex("Jefe", "what do ya want for nothing?"));
    }

    @Test
    void canonicalRequestBindsTimestampMethodPathAndBody() {
        String canonical = Hmac.canonicalRequest("1700000000", "post", "/transactions", "{\"amount\":\"120.00\"}");
        assertEquals("1700000000\nPOST\n/transactions\n" + Hashing.sha256Hex("{\"amount\":\"120.00\"}"), canonical);
    }

    @Test
    void changedAmountInvalidatesSignature() {
        String secret = "whsec_test";
        String original = Hmac.sha256Hex(secret,
                Hmac.canonicalRequest("1700000000", "POST", "/transactions", "{\"amount\":\"120.00\"}"));
        String tampered = Hmac.sha256Hex(secret,
                Hmac.canonicalRequest("1700000000", "POST", "/transactions", "{\"amount\":\"1.00\"}"));
        assertNotEquals(original, tampered);
        assertFalse(Hmac.matches(original, tampered));
    }

    @Test
    void comparisonIgnoresHexCaseAndRejectsNull() {
        String signature = Hmac.sha256Hex("k", "data");
        assertTrue(Hmac.matches(signature, signature.toUpperCase()));
        assertFalse(Hmac.matches(signature, null));
        assertFalse(Hmac.matches(null, signature));
    }
}
