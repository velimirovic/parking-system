package com.sep.psp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-data")
public record DemoDataProperties(
        boolean enabled,
        String merchantPassword,
        String parkingApiKey,
        String parkingWebhookSecret) {
}
