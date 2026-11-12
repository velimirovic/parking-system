package com.sep.bank.common.crypto;

import org.springframework.stereotype.Component;

import com.sep.bank.common.config.BankProperties;

/**
 * Zastita podataka kartice u bazi banke (PCI DSS zahtev 3):
 * <ul>
 *   <li>PAN token - HMAC-SHA256 PAN-a tajnim kljucem; omogucava pronalazenje kartice bez citljivog PAN-a
 *       (obican hash PAN-a bi se mogao probiti isprobavanjem svih brojeva za poznat BIN)</li>
 *   <li>PAN se dodatno cuva sifrovan (AES-GCM), a prikazuju se samo poslednje 4 cifre</li>
 *   <li>CVV se nikad ne cuva; izdavalac cuva samo HMAC vezan za token kartice da bi proverio uneti kod</li>
 * </ul>
 */
@Component
public class CardCrypto {

    private final String tokenKey;
    private final AesGcmEncryptor encryptor;

    public CardCrypto(BankProperties properties, AesGcmEncryptor encryptor) {
        if (properties.cardTokenKey() == null || properties.cardTokenKey().length() < 32) {
            throw new IllegalStateException("app.security.card-token-key mora imati najmanje 32 znaka");
        }
        this.tokenKey = properties.cardTokenKey();
        this.encryptor = encryptor;
    }

    public String panToken(String pan) {
        return Hmac.sha256Hex(tokenKey, "PAN|" + pan);
    }

    public String cvvHash(String panToken, String cvv) {
        return Hmac.sha256Hex(tokenKey, "CVV|" + panToken + "|" + cvv);
    }

    public String encryptPan(String pan) {
        return encryptor.encrypt(pan);
    }
}
