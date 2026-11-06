package dev.jevmod.core.jev;

import com.google.gson.JsonObject;
import dev.jevmod.core.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Async JSON POST on a shared JDK HttpClient (no extra dependencies, never blocks the game thread). */
final class HttpJson {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private HttpJson() {
    }

    static CompletableFuture<String> post(String url, String bearer, JsonObject body, long timeoutMs, Map<String, String> extraHeaders) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(body), StandardCharsets.UTF_8));
        extraHeaders.forEach(b::header);
        return CLIENT.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
            if (r.statusCode() / 100 != 2) {
                throw new ProviderException("HTTP " + r.statusCode() + " from " + url + ": " + Json.abbreviate(r.body(), 400));
            }
            return r.body();
        });
    }
}
