package com.sep.psp.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sep.psp.common.config.SecurityProperties;
import com.sep.psp.common.web.UrlRules;
import com.sep.psp.common.exception.ApiException;

class AesGcmEncryptorTest {

    private final AesGcmEncryptor encryptor = new AesGcmEncryptor(new SecurityProperties(
            "x".repeat(32), 15, 5, 5, 30, "ZGV2LW9ubHktYWVzLWtleS1jaGFuZ2UtbWUtMzJieXQ=", "SEP Pay"));

    @Test
    void encryptsWithRandomIvAndDecrypts() {
        String a = encryptor.encrypt("whsec_tajna");
        String b = encryptor.encrypt("whsec_tajna");
        assertTrue(a.startsWith("v1:"));
        assertNotEquals(a, b);
        assertEquals("whsec_tajna", encryptor.decrypt(a));
    }

    @Test
    void detectsTampering() {
        String encrypted = encryptor.encrypt("tajna");
        char last = encrypted.charAt(encrypted.length() - 3);
        String tampered = encrypted.substring(0, encrypted.length() - 3) + (last == 'A' ? 'B' : 'A')
                + encrypted.substring(encrypted.length() - 2);
        assertThrows(IllegalStateException.class, () -> encryptor.decrypt(tampered));
    }

    @Test
    void merchantUrlsMustUseHttpsOutsideLocalDevelopment() {
        assertEquals("https://parking.sep.rs/cb", UrlRules.validate("URL", "https://parking.sep.rs/cb", true));
        assertEquals("http://localhost:4200/ok", UrlRules.validate("URL", "http://localhost:4200/ok", true));
        assertEquals("http://parking-service:8081/cb", UrlRules.validate("URL", "http://parking-service:8081/cb", true));
        assertThrows(ApiException.class, () -> UrlRules.validate("URL", "http://parking.sep.rs/cb", true));
        assertThrows(ApiException.class, () -> UrlRules.validate("URL", "javascript:alert(1)", true));
    }

    @Test
    void temporaryPasswordSatisfiesPolicyCharacterClasses() {
        String password = new SecretGenerator().temporaryPassword();
        assertEquals(16, password.length());
        assertTrue(password.chars().anyMatch(Character::isUpperCase));
        assertTrue(password.chars().anyMatch(Character::isLowerCase));
        assertTrue(password.chars().anyMatch(Character::isDigit));
        assertTrue(password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)));
    }
}
