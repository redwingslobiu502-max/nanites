package dev.jevmod.core.jev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Deterministic provider for tests and offline play: answers come from a function of (state, questions).
 * Used by the GameTests so the whole sensor -> question -> decision -> script -> VFX path runs without network.
 */
public final class FixedProvider implements JevProvider {
    private final BiFunction<JsonElement, Map<String, Question>, Map<String, Answer>> fn;
    public volatile JsonElement lastState;
    public volatile Map<String, Question> lastQuestions;

    public FixedProvider(BiFunction<JsonElement, Map<String, Question>, Map<String, Answer>> fn) {
        this.fn = fn;
    }

    public static FixedProvider of(Map<String, Answer> answers) {
        return new FixedProvider((s, q) -> answers);
    }

    @Override
    public String name() {
        return "fixed";
    }

    @Override
    public CompletableFuture<EvalResult> evaluate(JsonElement state, Map<String, Question> questions) {
        lastState = state;
        lastQuestions = questions;
        Map<String, Answer> answers = new LinkedHashMap<>(fn.apply(state, questions));
        answers.keySet().retainAll(questions.keySet());
        return CompletableFuture.completedFuture(new EvalResult(answers, "fixed", 0, new JsonObject(), "{}", 0, 0));
    }
}
