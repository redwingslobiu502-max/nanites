package dev.jevmod.core.jev;

import com.google.gson.JsonObject;

import java.util.Map;

/** The outcome of one batched evaluation call. Raw request/response are kept for {@code last_think.json}. */
public final class EvalResult {
    public final Map<String, Answer> answers;
    public final String model;
    public final long latencyMs;
    public final JsonObject request;
    public final String rawResponse;
    public final int inputTokens;
    public final double cost;

    public EvalResult(Map<String, Answer> answers, String model, long latencyMs, JsonObject request, String rawResponse,
                      int inputTokens, double cost) {
        this.answers = answers;
        this.model = model;
        this.latencyMs = latencyMs;
        this.request = request;
        this.rawResponse = rawResponse;
        this.inputTokens = inputTokens;
        this.cost = cost;
    }
}
