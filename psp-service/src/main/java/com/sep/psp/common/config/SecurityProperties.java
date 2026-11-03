package com.sep.psp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        int accessTokenMinutes,
        int preAuthMinutes,
        int maxFailedAttempts,
        int lockMinutes,
        String encryptionKey,
        String mfaIssuer) {
}
