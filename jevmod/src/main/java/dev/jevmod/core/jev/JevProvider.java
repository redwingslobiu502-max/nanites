package dev.jevmod.core.jev;

import com.google.gson.JsonElement;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Something that answers typed questions about a state. Implementations must never block the caller:
 * the game calls {@link #evaluate} on the server thread and applies the result on a later tick.
 */
public interface JevProvider {
    String name();

    CompletableFuture<EvalResult> evaluate(JsonElement state, Map<String, Question> questions);
}
