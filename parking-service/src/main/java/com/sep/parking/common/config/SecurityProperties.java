package com.sep.parking.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwtSecret          HMAC kljuc za potpis JWT-a (min. 32 bajta)
 * @param accessTokenMinutes trajanje tokena korisnika
 * @param maxFailedAttempts  broj neuspesnih prijava posle kog se nalog zakljucava
 * @param lockMinutes        trajanje zakljucavanja naloga
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(String jwtSecret, int accessTokenMinutes, int maxFailedAttempts, int lockMinutes) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.security.jwt-secret mora imati najmanje 32 bajta");
        }
        if (accessTokenMinutes <= 0) accessTokenMinutes = 60;
        if (maxFailedAttempts <= 0) maxFailedAttempts = 5;
        if (lockMinutes <= 0) lockMinutes = 15;
    }
}
