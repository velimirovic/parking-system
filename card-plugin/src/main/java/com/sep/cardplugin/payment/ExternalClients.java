package com.sep.cardplugin.payment;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.sep.cardplugin.common.PluginProperties;
import com.sep.cardplugin.common.SignedRequests;
import com.sep.cardplugin.common.SignedRequests.Signed;
import com.sep.cardplugin.payment.Dtos.BankCreateRequest;
import com.sep.cardplugin.payment.Dtos.BankCreateResponse;
import com.sep.cardplugin.payment.Dtos.BankStatus;
import com.sep.cardplugin.payment.Dtos.PaymentResult;

import tools.jackson.databind.ObjectMapper;

/**
 * Pozivi ka banci (HMAC kljuc PSP-banka, zaglavlja X-Timestamp / X-Signature) i ka PSP Core-u
 * (deljena tajna plugin-ova, zaglavlja X-Psp-Timestamp / X-Psp-Signature). Kratki timeout-i:
 * spora banka ili Core ne smeju da zadrze nit plugin-a.
 */
@Component
public class ExternalClients {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    public static final String CORE_RESULT_PATH = "/internal/plugins/CARD/results";

    private final PluginProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ExternalClients(PluginProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Korak 2-3: zahtev banci za PAYMENT_URL i PAYMENT_ID. */
    public Response<BankCreateResponse> createBankPayment(BankCreateRequest request) throws Exception {
        String body = objectMapper.writeValueAsString(request);
        Signed signed = SignedRequests.sign(properties.bankHmacSecret(), "POST", "/payment-requests", body);
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                        URI.create(PluginProperties.trimSlash(properties.bankBaseUrl()) + "/payment-requests"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Timestamp", signed.timestamp())
                .header("X-Signature", signed.signature())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        return new Response<>(response.statusCode(), response.statusCode() / 100 == 2
                ? objectMapper.readValue(response.body(), BankCreateResponse.class) : null, response.body());
    }

    /** Provera statusa kod banke po MERCHANT_ID + STAN + PSP_TIMESTAMP. */
    public Response<BankStatus> bankStatus(String bankMerchantId, String stan, Instant pspTimestamp) throws Exception {
        Signed signed = SignedRequests.sign(properties.bankHmacSecret(), "GET", "/payment-requests/status", "");
        String query = "merchant_id=" + enc(bankMerchantId) + "&stan=" + enc(stan) + "&psp_timestamp="
                + enc(pspTimestamp.toString());
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(
                        PluginProperties.trimSlash(properties.bankBaseUrl()) + "/payment-requests/status?" + query))
                .timeout(TIMEOUT)
                .header("X-Timestamp", signed.timestamp())
                .header("X-Signature", signed.signature())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        return new Response<>(response.statusCode(), response.statusCode() / 100 == 2
                ? objectMapper.readValue(response.body(), BankStatus.class) : null, response.body());
    }

    /** Konacan rezultat ka PSP Core-u. */
    public int sendResult(PaymentResult result) throws Exception {
        String body = objectMapper.writeValueAsString(result);
        Signed signed = SignedRequests.sign(properties.pspSharedSecret(), "POST", CORE_RESULT_PATH, body);
        return client.send(HttpRequest.newBuilder(
                        URI.create(PluginProperties.trimSlash(properties.coreBaseUrl()) + CORE_RESULT_PATH))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Psp-Timestamp", signed.timestamp())
                .header("X-Psp-Signature", signed.signature())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record Response<T>(int status, T body, String raw) {
    }
}
