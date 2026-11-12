package com.sep.psp.plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.sep.psp.common.config.PaymentProperties;
import com.sep.psp.common.crypto.Hmac;
import com.sep.psp.paymentmethod.PaymentMethod;
import com.sep.psp.plugin.PluginDtos.InitiateRequest;
import com.sep.psp.plugin.PluginDtos.InitiateResponse;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP klijent ka plugin servisima. Adresa plugin-a dolazi iz tabele PAYMENT_METHOD, pa se nova metoda
 * placanja dodaje registracijom servisa, bez izmene koda Core-a.
 */
@Slf4j
@Component
public class PaymentPluginClient {

    public static final String HEADER_TIMESTAMP = "X-Psp-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Psp-Signature";
    static final String INITIATE_PATH = "/payments";

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public PaymentPluginClient(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public InitiateResponse initiate(PaymentMethod method, InitiateRequest request) {
        String body = objectMapper.writeValueAsString(request);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = Hmac.sha256Hex(properties.pluginSharedSecret(),
                Hmac.canonicalRequest(timestamp, "POST", INITIATE_PATH, body));
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl(method) + INITIATE_PATH))
                    .timeout(Duration.ofMillis(properties.pluginTimeoutMillis()))
                    .header("Content-Type", "application/json")
                    .header(HEADER_TIMESTAMP, timestamp)
                    .header(HEADER_SIGNATURE, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Plugin {} je vratio HTTP {} za transakciju {}", method.getCode(), response.statusCode(),
                        request.pspTransactionId());
                throw new PluginUnavailableException(method.getCode());
            }
            return objectMapper.readValue(response.body(), InitiateResponse.class);
        } catch (HttpTimeoutException e) {
            log.warn("Plugin {} nije odgovorio na vreme (transakcija {})", method.getCode(), request.pspTransactionId());
            throw new PluginUnavailableException(method.getCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PluginUnavailableException(method.getCode());
        } catch (PluginUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Plugin {} nije dostupan ({}): {}", method.getCode(), method.getPluginBaseUrl(), e.toString());
            throw new PluginUnavailableException(method.getCode());
        }
    }

    private static String baseUrl(PaymentMethod method) {
        String url = method.getPluginBaseUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static class PluginUnavailableException extends RuntimeException {
        public PluginUnavailableException(String code) {
            super("Plugin " + code + " nije dostupan");
        }
    }
}
