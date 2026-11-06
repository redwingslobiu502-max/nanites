package dev.jevmod.core;

import com.google.gson.JsonObject;
import dev.jevmod.core.jev.JevProvider;
import dev.jevmod.core.jev.OpenRouterProvider;
import dev.jevmod.core.jev.TypeSafeProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Global settings, read from {@code config/jevmod/jevmod.json}. API keys are never stored here: the file names the
 * environment variable that holds the key ({@code OPENROUTER_API_KEY} / {@code TYPESAFE_API_KEY} by default).
 */
public final class JevConfig {
    public static final String DEFAULT_JSON = """
            {
              "provider": "openrouter",
              "openrouter": {
                "model": "~typesafe/jev-latest",
                "api_key_env": "OPENROUTER_API_KEY",
                "base_url": "https://openrouter.ai/api/v1",
                "structured_outputs": true
              },
              "typesafe": {
                "model": "jev-latest",
                "api_key_env": "TYPESAFE_API_KEY",
                "base_url": "https://api.typesafe.ai/v1"
              },
              "timeout_ms": 10000,
              "script_instruction_limit": 2000000,
              "allow_world_edits": true,
              "show_thoughts": false
            }
            """;

    public final JsonObject raw;
    public final String provider;
    public final long timeoutMs;
    public final int scriptInstructionLimit;
    public final boolean allowWorldEdits;
    public final boolean showThoughts;

    private JevConfig(JsonObject raw) {
        this.raw = raw;
        this.provider = Json.str(raw, "provider", "openrouter");
        this.timeoutMs = (long) Json.num(raw, "timeout_ms", 10000);
        this.scriptInstructionLimit = Json.integer(raw, "script_instruction_limit", 2_000_000);
        this.allowWorldEdits = Json.bool(raw, "allow_world_edits", true);
        this.showThoughts = Json.bool(raw, "show_thoughts", false);
    }

    public static JevConfig defaults() {
        return new JevConfig(Json.parseObject(DEFAULT_JSON));
    }

    /** Loads the config, writing the default file first if it does not exist yet. */
    public static JevConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DEFAULT_JSON, StandardCharsets.UTF_8);
        }
        JsonObject o = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        JsonObject merged = Json.parseObject(DEFAULT_JSON);
        for (var e : o.entrySet()) merged.add(e.getKey(), e.getValue());
        return new JevConfig(merged);
    }

    /**
     * Builds the provider for a brain. {@code override}/{@code modelOverride} come from the brain file and may be null.
     * {@code env} is usually {@code System::getenv}. Throws with a player-readable message when a key is missing.
     */
    public JevProvider provider(String override, String modelOverride, Function<String, String> env) {
        String name = override != null ? override : provider;
        switch (name) {
            case "openrouter" -> {
                JsonObject o = Json.obj(raw, "openrouter");
                String keyEnv = Json.str(o, "api_key_env", "OPENROUTER_API_KEY");
                String key = env.apply(keyEnv);
                if (key == null || key.isBlank()) {
                    throw new IllegalStateException("environment variable " + keyEnv + " is not set (needed by the openrouter provider)");
                }
                return new OpenRouterProvider(Json.str(o, "base_url", "https://openrouter.ai/api/v1"), key,
                        modelOverride != null ? modelOverride : Json.str(o, "model", "~typesafe/jev-latest"),
                        timeoutMs, Json.bool(o, "structured_outputs", true));
            }
            case "typesafe" -> {
                JsonObject o = Json.obj(raw, "typesafe");
                String keyEnv = Json.str(o, "api_key_env", "TYPESAFE_API_KEY");
                String key = env.apply(keyEnv);
                if (key == null || key.isBlank()) {
                    throw new IllegalStateException("environment variable " + keyEnv + " is not set (needed by the typesafe provider)");
                }
                return new TypeSafeProvider(Json.str(o, "base_url", "https://api.typesafe.ai/v1"), key,
                        modelOverride != null ? modelOverride : Json.str(o, "model", "jev-latest"), timeoutMs);
            }
            default -> throw new IllegalStateException("unknown provider '" + name + "' (openrouter | typesafe)");
        }
    }
}
