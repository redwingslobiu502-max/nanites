package dev.jevmod.core.def;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.jevmod.core.Json;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A power: what a brain can do. The JSON describes it for the AI (name, description, use_when, typed params);
 * the JavaScript file next to it implements the behaviour.
 */
public final class PowerDef {
    public enum Kind {
        /** Stays on until the brain (or a command) turns it off. Script hooks: onStart, onTick, onChange, onStop. */
        TOGGLE,
        /** Fires once per decision, then cools down. Script hook: run. */
        INSTANT
    }

    public final String id;
    public final String name;
    public final String description;
    public final String useWhen;
    public final Kind kind;
    public final String script;
    public final int cooldownTicks;
    public final int maxActiveTicks;
    public final boolean ai;
    public final Map<String, ParamDef> params;
    /** Folder the definition was loaded from (the script path is resolved against it). */
    public final Path dir;

    private PowerDef(String id, String name, String description, String useWhen, Kind kind, String script, int cooldownTicks,
                     int maxActiveTicks, boolean ai, Map<String, ParamDef> params, Path dir) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.useWhen = useWhen;
        this.kind = kind;
        this.script = script;
        this.cooldownTicks = cooldownTicks;
        this.maxActiveTicks = maxActiveTicks;
        this.ai = ai;
        this.params = params;
        this.dir = dir;
    }

    public static PowerDef parse(JsonObject o, String fallbackId, Path dir) {
        String id = Json.str(o, "id", fallbackId);
        Defs.requireId(id, "power");
        String kindName = Json.str(o, "kind", "toggle").toLowerCase();
        Kind kind = switch (kindName) {
            case "toggle", "continuous" -> Kind.TOGGLE;
            case "instant", "action", "pulse" -> Kind.INSTANT;
            default -> throw new DefinitionException("power '" + id + "': unknown kind '" + kindName + "' (toggle | instant)");
        };
        Map<String, ParamDef> params = new LinkedHashMap<>();
        JsonObject po = Json.obj(o, "params");
        if (po != null) {
            for (Map.Entry<String, JsonElement> e : po.entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    throw new DefinitionException("power '" + id + "': param '" + e.getKey() + "' must be an object");
                }
                try {
                    params.put(e.getKey(), ParamDef.parse(e.getKey(), e.getValue().getAsJsonObject()));
                } catch (DefinitionException ex) {
                    throw new DefinitionException("power '" + id + "': " + ex.getMessage());
                }
            }
        }
        return new PowerDef(id,
                Json.str(o, "name", id),
                Json.str(o, "description", ""),
                Json.str(o, "use_when", ""),
                kind,
                Json.str(o, "script", id + ".js"),
                Math.max(0, Json.integer(o, "cooldown_ticks", kind == Kind.INSTANT ? 20 : 0)),
                Math.max(0, Json.integer(o, "max_active_ticks", 0)),
                Json.bool(o, "ai", true),
                Collections.unmodifiableMap(params),
                dir);
    }

    public Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ParamDef p : params.values()) {
            m.put(p.id, p.defaultValue);
        }
        return m;
    }

    public Path scriptPath() {
        return dir.resolve(script);
    }
}
