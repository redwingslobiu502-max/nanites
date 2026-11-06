package dev.jevmod.core.def;

import com.google.gson.JsonObject;
import dev.jevmod.core.Json;

import java.util.List;
import java.util.Set;

/**
 * One sensor in a brain: a named reading added to the state sent to the AI. Built-in types are implemented in
 * {@code dev.jevmod.game.Sensors}; type {@code script} runs a JavaScript function that returns any JSON-able object.
 */
public final class SensorDef {
    public static final Set<String> BUILTIN = Set.of("self", "nearby_entities", "looking_at", "environment", "chat", "powers", "script");
    public static final List<String> DEFAULT_SET = List.of("self", "nearby_entities", "looking_at", "environment", "chat", "powers");

    public final String type;
    /** Key in the state object (defaults to the type, or the script id). */
    public final String as;
    /** The raw definition, so each sensor can read its own options (radius, max, file...). */
    public final JsonObject config;

    private SensorDef(String type, String as, JsonObject config) {
        this.type = type;
        this.as = as;
        this.config = config;
    }

    public static SensorDef parse(JsonObject o) {
        String type = Json.str(o, "type", null);
        if (type == null || !BUILTIN.contains(type)) {
            throw new DefinitionException("unknown sensor type '" + type + "', expected one of " + BUILTIN);
        }
        String as = Json.str(o, "as", type.equals("script") ? Json.str(o, "id", "script") : type);
        if (type.equals("script") && Json.str(o, "file", null) == null) {
            throw new DefinitionException("script sensor '" + as + "' needs a \"file\" (relative to config/jevmod/sensors)");
        }
        return new SensorDef(type, as, o.deepCopy());
    }
}
