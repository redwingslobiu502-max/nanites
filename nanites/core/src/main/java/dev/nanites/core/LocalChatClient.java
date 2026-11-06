package dev.nanites.core;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Flow;

/** Async OpenAI chat completions. Default TLS/hostname verification, no redirects, fixed local URL. */
public final class LocalChatClient implements AutoCloseable {
    public record Config(URI base, String model, Duration timeout, int maxTokens, int maxResponseBytes, int maxConcurrent) {
        public Config {
            IntegrationContracts.validateAiEndpoint(base);
            if (model == null || model.isBlank() || model.length() > 128 || timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(60)) > 0
                    || maxTokens < 1 || maxTokens > 8192 || maxResponseBytes < 256 || maxResponseBytes > 262144 || maxConcurrent < 1 || maxConcurrent > 4) throw new IllegalArgumentException("Client configuration quota");
        }
    }
    public record Response(int status, byte[] body) { public Response { body = body.clone(); } @Override public byte[] body() { return body.clone(); } }
    @FunctionalInterface public interface Transport {
        CompletableFuture<Response> exchange(URI uri, byte[] body, Duration timeout, int maxBytes);
    }
    /** Standard JVM TLS; direct loopback (no proxy), never follows redirects. */
    public static final class JvmTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new java.net.ProxySelector() {
                    @Override public List<java.net.Proxy> select(URI uri) { return List.of(java.net.Proxy.NO_PROXY); }
                    @Override public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException error) { /* No proxy is used. */ }
                }).build();
        private final String token;
        public JvmTransport() { this(""); }
        /** Explicit operator-provided token only; no environment or filesystem secret lookup. */
        public JvmTransport(String token) {
            if (token == null || token.length() > 4096 || token.chars().anyMatch(c -> c < 32 || c > 126)) throw new IllegalArgumentException("Invalid token");
            this.token = token;
        }
        @Override public CompletableFuture<Response> exchange(URI uri, byte[] body, Duration timeout, int maxBytes) {
            if (!uri.toString().equals(IntegrationContracts.LOCAL_AI + "/chat/completions")) throw new IllegalArgumentException("Route denied");
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type", "application/json");
            if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            CompletableFuture<HttpResponse<byte[]>> raw = client.sendAsync(request, info -> new LimitedBody(maxBytes));
            CompletableFuture<Response> result = new CompletableFuture<>();
            raw.whenComplete((r, e) -> { if (e != null) result.completeExceptionally(e); else result.complete(new Response(r.statusCode(), r.body())); });
            result.whenComplete((r, e) -> { if (result.isCancelled()) raw.cancel(true); }); return result;
        }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> body = new CompletableFuture<>(); final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); final int max;
        Flow.Subscription subscription;
        LimitedBody(int max) { this.max = max; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer b : buffers) {
                if ((long)out.size() + b.remaining() > max) { subscription.cancel(); body.completeExceptionally(new IllegalArgumentException("HTTP response quota")); return; }
                byte[] bytes = new byte[b.remaining()]; b.get(bytes); out.writeBytes(bytes);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable e) { body.completeExceptionally(e); }
        @Override public void onComplete() { body.complete(out.toByteArray()); }
    }
    private final Config config;
    private final Transport transport;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Set<CompletableFuture<JsonObject>> requests = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "nanites-ai-timeouts"); t.setDaemon(true); return t; });
    private volatile boolean closed;
    public LocalChatClient(Config config, Transport transport) { this.config = Objects.requireNonNull(config); this.transport = Objects.requireNonNull(transport); }
    /** Conservative UTF-8-byte reservation for input plus server max_tokens; not a model-specific tokenizer. */
    public long reservation(JsonArray messages, JsonArray tools) {
        return (long)SafeData.GSON.toJson(messages).getBytes(StandardCharsets.UTF_8).length
                + SafeData.GSON.toJson(tools).getBytes(StandardCharsets.UTF_8).length + 1024 + config.maxTokens();
    }
    public synchronized CompletableFuture<JsonObject> complete(JsonArray messages, JsonArray tools) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Client closed"));
        IntegrationContracts.validateAiEndpoint(config.base());
        if (messages.size() > 64 || tools.size() > 16) return CompletableFuture.failedFuture(new IllegalArgumentException("Conversation quota"));
        JsonObject request = new JsonObject(); request.addProperty("model", config.model()); request.addProperty("max_tokens", config.maxTokens()); request.addProperty("stream", false);
        request.add("messages", messages.deepCopy()); if (!tools.isEmpty()) { request.add("tools", tools.deepCopy()); request.addProperty("tool_choice", "auto"); }
        byte[] bytes = SafeData.GSON.toJson(request).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 262144) return CompletableFuture.failedFuture(new IllegalArgumentException("Request quota"));
        if (inFlight.incrementAndGet() > config.maxConcurrent()) { inFlight.decrementAndGet(); return CompletableFuture.failedFuture(new IllegalStateException("Client busy")); }
        CompletableFuture<JsonObject> result = new CompletableFuture<>(); requests.add(result);
        CompletableFuture<Response> raw;
        try { raw = transport.exchange(URI.create(config.base() + "/chat/completions"), bytes, config.timeout(), config.maxResponseBytes()); }
        catch (RuntimeException e) { requests.remove(result); inFlight.decrementAndGet(); return CompletableFuture.failedFuture(e); }
        ScheduledFuture<?> timeout = timer.schedule(() -> { if (result.completeExceptionally(new TimeoutException("Local model timeout"))) raw.cancel(true); }, config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        result.whenComplete((r, e) -> { timeout.cancel(false); requests.remove(result); inFlight.decrementAndGet(); if (result.isCancelled()) raw.cancel(true); });
        raw.whenComplete((r, e) -> {
            if (e != null) { result.completeExceptionally(e); return; }
            try {
                if (r.status() < 200 || r.status() >= 300) throw new IllegalStateException("Local model HTTP " + r.status() + " (no redirect/retry)");
                JsonObject data = SafeData.object(new String(r.body(), StandardCharsets.UTF_8), config.maxResponseBytes());
                result.complete(data);
            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
    @Override public synchronized void close() { closed = true; requests.forEach(r -> r.cancel(true)); timer.shutdownNow(); }
}
