package com.sep.psp.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.sep.psp.common.crypto.SecretGenerator;

class TotpServiceTest {

    private final TotpService totp = new TotpService(new SecretGenerator());

    /** RFC 6238, dodatak B (SHA1, kljuc "12345678901234567890") - poslednjih 6 cifara 8-cifrenog koda. */
    @Test
    void generatesRfc6238TestVectors() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertEquals("287082", TotpService.generate(key, 59L / 30));
        assertEquals("081804", TotpService.generate(key, 1111111109L / 30));
        assertEquals("050471", TotpService.generate(key, 1111111111L / 30));
        assertEquals("005924", TotpService.generate(key, 1234567890L / 30));
        assertEquals("279037", TotpService.generate(key, 2000000000L / 30));
    }

    @Test
    void acceptsCurrentCodeOnlyOnce() {
        String secret = totp.generateSecret();
        long step = totp.currentStep();
        String code = TotpService.generate(Base32.decode(secret), step);

        var first = totp.verify(secret, code, null);
        assertTrue(first.isPresent());
        // isti kod (ili stariji korak) se odbija - zastita od ponovne upotrebe
        assertFalse(totp.verify(secret, code, first.getAsLong()).isPresent());
    }

    @Test
    void rejectsMalformedCodes() {
        String secret = totp.generateSecret();
        assertFalse(totp.verify(secret, "12345", null).isPresent());
        assertFalse(totp.verify(secret, "abcdef", null).isPresent());
        assertFalse(totp.verify(secret, null, null).isPresent());
    }

    @Test
    void base32RoundTrip() {
        byte[] data = new SecretGenerator().randomBytes(20);
        String encoded = Base32.encode(data);
        assertEquals(32, encoded.length());
        assertArrayEquals(data, Base32.decode(encoded));
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void otpauthUriContainsIssuerAndSecret() {
        String uri = totp.otpauthUri("SEP Pay", "ana@parking.sep.rs", "JBSWY3DPEHPK3PXP");
        assertTrue(uri.startsWith("otpauth://totp/SEP%20Pay:ana%40parking.sep.rs?secret=JBSWY3DPEHPK3PXP"));
        assertTrue(uri.contains("issuer=SEP%20Pay"));
    }
}
