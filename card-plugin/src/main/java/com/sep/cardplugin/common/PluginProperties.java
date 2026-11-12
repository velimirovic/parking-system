package com.sep.cardplugin.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.plugin")
public record PluginProperties(
        String pspSharedSecret,
        String coreBaseUrl,
        String publicBaseUrl,
        String bankBaseUrl,
        String bankHmacSecret,
        int maxClockSkewSeconds,
        int statusPollAfterSeconds) {

    public static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
