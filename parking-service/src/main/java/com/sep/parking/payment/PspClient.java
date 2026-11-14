package com.sep.parking.payment;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Klijent PSP merchant API-ja (docs/psp-merchant-api.md). Svaki zahtev nosi X-Merchant-Id,
 * X-Api-Key, X-Timestamp i X-Signature (HMAC-SHA256 nad tacnim telom zahteva).
 */
@Component
public class PspClient {

    private static final Logger log = LoggerFactory.getLogger(PspClient.class);
    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z0-9_]+)\"");
    private static final Pattern DETAIL = Pattern.compile("\"detail\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final PspProperties properties;
    private final RestClient rest;
    private final Clock clock;

    public PspClient(PspProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    /** Korak 1 iz specifikacije - inicijalizacija transakcije. Idempotentno po merchant_order_id. */
    public PspTransaction initTransaction(String merchantOrderId, Instant merchantTimestamp, BigDecimal amount,
                                          String currency, String description) {
        String body = "{"
                + "\"merchant_order_id\":" + json(merchantOrderId) + ","
                + "\"merchant_timestamp\":" + json(DateTimeFormatter.ISO_INSTANT.format(
                        merchantTimestamp.truncatedTo(ChronoUnit.MILLIS))) + ","
                + "\"amount\":" + json(amount.setScale(2, RoundingMode.HALF_UP).toPlainString()) + ","
                + "\"currency\":" + json(currency)
                + (description == null ? "" : ",\"description\":" + json(description))
                + "}";
        return exchange("POST", "/transactions", body);
    }

    /** Provera statusa (pull) - za uskladjivanje kada potvrda kasni ili redirect izostane. */
    public PspTransaction getTransaction(String merchantOrderId) {
        return exchange("GET", "/transactions/" + merchantOrderId, "");
    }

    private PspTransaction exchange(String method, String path, String body) {
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String signature = PspSigner.sign(properties.signingSecret(), timestamp, method, path, body);
        URI uri = URI.create(stripSlash(properties.baseUrl()) + path);
        try {
            RestClient.RequestBodySpec request = rest.method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(uri)
                    .header("X-Merchant-Id", properties.merchantId())
                    .header("X-Api-Key", properties.apiKey())
                    .header("X-Timestamp", timestamp)
                    .header("X-Signature", signature)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            if (!body.isEmpty()) {
                // salju se tacno oni UTF-8 bajtovi nad kojima je izracunat potpis
                request = request.contentType(MediaType.APPLICATION_JSON).body(body.getBytes(StandardCharsets.UTF_8));
            }
            return request.retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new PspException.Unavailable("PSP je vratio " + res.getStatusCode().value(), null);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String error = read(res.getBody());
                        String code = match(CODE, error, "PSP_REJECTED");
                        log.warn("PSP je odbio {} {}: {} {}", method, path, res.getStatusCode().value(), code);
                        throw new PspException.Rejected(res.getStatusCode().value(), code,
                                match(DETAIL, error, "PSP je odbio zahtev."));
                    })
                    .body(PspTransaction.class);
        } catch (PspException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("PSP nije dostupan ({} {}): {}", method, path, e.getMessage());
            throw new PspException.Unavailable("PSP nije dostupan", e);
        } catch (RestClientException e) {
            log.warn("Greska u komunikaciji sa PSP-om ({} {}): {}", method, path, e.getMessage());
            throw new PspException.Unavailable("Greska u komunikaciji sa PSP-om", e);
        }
    }

    private static String read(InputStream in) {
        try (in) {
            byte[] bytes = in.readNBytes(8192);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String match(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : fallback;
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** JSON string literal (telo se gradi rucno da bi potpis bio nad tacno poslatim bajtovima). */
    static String json(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
