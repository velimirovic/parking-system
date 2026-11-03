package com.sep.psp.common.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import com.sep.psp.common.exception.ApiException;

/**
 * URL-ovi klijenta (webhook, success/failed/error) moraju biti HTTPS (PCI DSS zahtev 4).
 * HTTP je dozvoljen samo za lokalni razvoj (localhost i imena Docker servisa bez tacke).
 */
public final class UrlRules {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private UrlRules() {
    }

    /** Vraca normalizovan URL ili null ako je prazan i nije obavezan. */
    public static String validate(String field, String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw ApiException.badRequest("INVALID_URL", field + " je obavezan");
            }
            return null;
        }
        String trimmed = value.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost();
            if (host == null || trimmed.length() > 500 || uri.getUserInfo() != null) {
                throw invalid(field);
            }
            boolean local = LOCAL_HOSTS.contains(host.toLowerCase()) || !host.contains(".");
            if (!scheme.equals("https") && !(scheme.equals("http") && local)) {
                throw ApiException.badRequest("INVALID_URL", field + " mora koristiti HTTPS");
            }
            return trimmed;
        } catch (URISyntaxException e) {
            throw invalid(field);
        }
    }

    private static ApiException invalid(String field) {
        return ApiException.badRequest("INVALID_URL", field + " nije ispravan URL");
    }
}
