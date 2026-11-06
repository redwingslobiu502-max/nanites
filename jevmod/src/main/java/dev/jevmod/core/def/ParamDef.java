package dev.jevmod.core.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.jevmod.core.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One typed parameter of a power. The type decides which Jev question the brain asks about it:
 * <ul>
 *   <li>{@code bool}   -> noul   (yes/no probability)</li>
 *   <li>{@code choice} -> choice (one of up to 255 named options)</li>
 *   <li>{@code range}  -> score  (2..10 ordered levels mapped linearly onto min..max)</li>
 * </ul>
 */
public final class ParamDef {
    public enum Type { BOOL, CHOICE, RANGE }

    public final String id;
    public final Type type;
    public final String description;
    /** Choice options in declaration order: option -> rubric (string/object) or JsonNull. */
    public final Map<String, JsonElement> options;
    public final double min;
    public final double max;
    public final boolean integer;
    /** Ordered level descriptions for range params (2..10). */
    public final List<String> levelLabels;
    public final Object defaultValue;
    /** When false the AI never sets this parameter; only defaults, commands and scripts do. */
    public final boolean ai;
    /** Minimum Jev confidence to accept a new value; negative means "use the brain default". */
    public final double minConfidence;

    private ParamDef(String id, Type type, String description, Map<String, JsonElement> options, double min, double max,
                     boolean integer, List<String> levelLabels, Object defaultValue, boolean ai, double minConfidence) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.options = options;
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.levelLabels = levelLabels;
        this.defaultValue = defaultValue;
        this.ai = ai;
        this.minConfidence = minConfidence;
    }

    public static ParamDef parse(String id, JsonObject o) {
        Defs.requireId(id, "param");
        String typeName = Json.str(o, "type", null);
        if (typeName == null) {
            throw new DefinitionException("param '" + id + "': missing \"type\" (bool | choice | range)");
        }
        Type type;
        switch (typeName.toLowerCase()) {
            case "bool", "boolean", "toggle" -> type = Type.BOOL;
            case "choice", "enum" -> type = Type.CHOICE;
            case "range", "number", "score" -> type = Type.RANGE;
            default -> throw new DefinitionException("param '" + id + "': unknown type '" + typeName + "' (bool | choice | range)");
        }
        String description = Json.str(o, "description", id);
        boolean ai = Json.bool(o, "ai", true);
        double minConfidence = Json.num(o, "min_confidence", -1);
        Map<String, JsonElement> options = Collections.emptyMap();
        double min = 0, max = 1;
        boolean integer = false;
        List<String> labels = Collections.emptyList();
        Object def;
        JsonElement d = o.get("default");
        switch (type) {
            case BOOL -> def = d == null || d.isJsonNull() ? Boolean.FALSE : d.getAsBoolean();
            case CHOICE -> {
                options = new LinkedHashMap<>();
                JsonElement opts = o.get("options");
                if (opts != null && opts.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : opts.getAsJsonObject().entrySet()) {
                        options.put(e.getKey(), e.getValue());
                    }
                } else if (opts != null && opts.isJsonArray()) {
                    for (JsonElement e : opts.getAsJsonArray()) {
                        options.put(e.getAsString(), com.google.gson.JsonNull.INSTANCE);
                    }
                }
                if (options.size() < 2 || options.size() > 255) {
                    throw new DefinitionException("param '" + id + "': a choice needs 2..255 options, got " + options.size());
                }
                options = Collections.unmodifiableMap(options);
                String ds = d == null || d.isJsonNull() ? options.keySet().iterator().next() : d.getAsString();
                if (!options.containsKey(ds)) {
                    throw new DefinitionException("param '" + id + "': default '" + ds + "' is not one of " + options.keySet());
                }
                def = ds;
            }
            default -> {
                min = Json.num(o, "min", 0);
                max = Json.num(o, "max", 1);
                integer = Json.bool(o, "integer", false);
                if (!(max > min)) {
                    throw new DefinitionException("param '" + id + "': range needs max > min");
                }
                JsonArray la = Json.arr(o, "levels");
                if (la != null) {
                    labels = new ArrayList<>();
                    for (JsonElement e : la) {
                        labels.add(e.isJsonPrimitive() ? e.getAsString() : e.toString());
                    }
                } else {
                    int n = o.has("levels") ? o.get("levels").getAsInt() : 5;
                    labels = autoLevels(n, min, max, integer);
                }
                if (labels.size() < 2 || labels.size() > 10) {
                    throw new DefinitionException("param '" + id + "': a range needs 2..10 levels, got " + labels.size());
                }
                labels = Collections.unmodifiableList(labels);
                double dv = d == null || d.isJsonNull() ? min : d.getAsDouble();
                def = clampRange(dv, min, max, integer);
            }
        }
        return new ParamDef(id, type, description, options, min, max, integer, labels, def, ai, minConfidence);
    }

    static List<String> autoLevels(int n, double min, double max, boolean integer) {
        if (n < 2 || n > 10) {
            throw new DefinitionException("levels must be between 2 and 10, got " + n);
        }
        List<String> out = new ArrayList<>();
        String[] words = {"minimum", "very low", "low", "medium-low", "medium", "medium-high", "high", "very high", "near maximum", "maximum"};
        for (int i = 0; i < n; i++) {
            double v = min + (max - min) * i / (n - 1);
            String word = i == 0 ? "minimum" : i == n - 1 ? "maximum" : words[Math.round((float) i * 9 / (n - 1))];
            out.add(word + " (" + fmt(integer ? Math.round(v) : v) + ")");
        }
        return out;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    public static Object clampRange(double v, double min, double max, boolean integer) {
        double c = Math.max(min, Math.min(max, v));
        return integer ? (Object) (double) Math.round(c) : (Object) c;
    }

    /** Maps a (possibly fractional) Jev score level index onto this range param's value. */
    public double levelToValue(double level) {
        int n = levelLabels.size();
        double t = Math.max(0, Math.min(n - 1, level)) / (n - 1);
        double v = min + (max - min) * t;
        return integer ? Math.round(v) : v;
    }

    /** Coerces a user/script supplied value into this param's domain or throws. */
    public Object coerce(Object raw) {
        switch (type) {
            case BOOL -> {
                if (raw instanceof Boolean b) return b;
                String s = String.valueOf(raw).trim().toLowerCase();
                if (s.equals("true") || s.equals("on") || s.equals("yes") || s.equals("1")) return Boolean.TRUE;
                if (s.equals("false") || s.equals("off") || s.equals("no") || s.equals("0")) return Boolean.FALSE;
                throw new DefinitionException("param '" + id + "' expects true/false, got " + raw);
            }
            case CHOICE -> {
                String s = String.valueOf(raw).trim();
                if (!options.containsKey(s)) throw new DefinitionException("param '" + id + "' expects one of " + options.keySet() + ", got " + raw);
                return s;
            }
            default -> {
                double v;
                if (raw instanceof Number n) v = n.doubleValue();
                else {
                    try {
                        v = Double.parseDouble(String.valueOf(raw).trim());
                    } catch (NumberFormatException e) {
                        throw new DefinitionException("param '" + id + "' expects a number in " + min + ".." + max + ", got " + raw);
                    }
                }
                return clampRange(v, min, max, integer);
            }
        }
    }

    public JsonElement toJsonValue(Object v) {
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Number n) return Json.round(n.doubleValue(), 2);
        return new JsonPrimitive(String.valueOf(v));
    }
}
