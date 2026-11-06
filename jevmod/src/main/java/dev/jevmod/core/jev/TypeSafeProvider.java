package dev.jevmod.core.jev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.jevmod.core.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The real Jev: TypeSafe's System One endpoint ({@code POST /v1/systemone}). Answers carry true probability
 * distributions and confidences. Needs a TypeSafe API key (env TYPESAFE_API_KEY by default).
 */
public final class TypeSafeProvider implements JevProvider {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final long timeoutMs;

    public TypeSafeProvider(String baseUrl, String apiKey, String model, long timeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String name() {
        return "typesafe:" + model;
    }

    public JsonObject buildRequest(JsonElement state, Map<String, Question> questions) {
        JsonObject body = new JsonObject();
        body.add("state", state);
        body.addProperty("model", model);
        JsonObject qs = new JsonObject();
        for (Question q : questions.values()) qs.add(q.id, q.toJson());
        body.add("questions", qs);
        return body;
    }

    public static Map<String, Answer> parseResponse(String raw, Map<String, Question> questions) {
        JsonObject r = Json.parseObject(raw);
        JsonObject answers = Json.obj(r, "answers");
        if (answers == null) {
            throw new ProviderException("TypeSafe response has no 'answers': " + Json.abbreviate(raw, 300));
        }
        Map<String, Answer> out = new LinkedHashMap<>();
        for (String id : questions.keySet()) {
            JsonObject a = Json.obj(answers, id);
            if (a != null) out.put(id, Answer.fromTypeSafe(a));
        }
        return out;
    }

    @Override
    public CompletableFuture<EvalResult> evaluate(JsonElement state, Map<String, Question> questions) {
        JsonObject body = buildRequest(state, questions);
        long t0 = System.nanoTime();
        return HttpJson.post(baseUrl + "/systemone", apiKey, body, timeoutMs, Map.of()).thenApply(raw -> {
            Map<String, Answer> answers = parseResponse(raw, questions);
            JsonObject r = Json.parseObject(raw);
            JsonObject usage = Json.obj(r, "usage");
            return new EvalResult(answers, Json.str(r, "model", model), (System.nanoTime() - t0) / 1_000_000, body, raw,
                    Json.integer(usage, "input_tokens", 0), -1);
        });
    }
}
