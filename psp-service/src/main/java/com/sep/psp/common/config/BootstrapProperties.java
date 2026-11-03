package com.sep.psp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String superAdminEmail, String superAdminPassword) {
}
