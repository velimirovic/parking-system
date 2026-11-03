package com.sep.psp.paymentmethod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Provera dostupnosti plugin servisa (GET {plugin_base_url}/actuator/health), paralelno i sa kratkim timeout-om.
 */
@Component
public class PluginHealthChecker {

    private static final Duration TIMEOUT = Duration.ofMillis(1500);
    private static final long SLOW_THRESHOLD_MS = 1000;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public Map<String, Health> check(List<PaymentMethod> methods) {
        Map<String, CompletableFuture<Health>> futures = methods.stream()
                .collect(Collectors.toMap(PaymentMethod::getCode, this::checkAsync));
        return futures.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join()));
    }

    private CompletableFuture<Health> checkAsync(PaymentMethod method) {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(stripSlash(method.getPluginBaseUrl()) + "/actuator/health"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenApply(response -> {
                        long ms = (System.nanoTime() - start) / 1_000_000;
                        if (response.statusCode() / 100 != 2) {
                            return new Health(HealthStatus.DOWN, ms);
                        }
                        return new Health(ms > SLOW_THRESHOLD_MS ? HealthStatus.SLOW : HealthStatus.UP, ms);
                    })
                    .exceptionally(error -> new Health(HealthStatus.DOWN, null));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(new Health(HealthStatus.DOWN, null));
        }
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public enum HealthStatus {
        UP,
        SLOW,
        DOWN
    }

    public record Health(HealthStatus status, Long latencyMs) {
    }
}
