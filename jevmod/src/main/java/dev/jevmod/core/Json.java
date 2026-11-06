package dev.jevmod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/** Small null-safe helpers over Gson's tree model. No Minecraft classes, so it is unit-testable. */
public final class Json {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private Json() {
    }

    public static JsonObject parseObject(String text) {
        JsonElement e = JsonParser.parseString(text);
        if (!e.isJsonObject()) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        return e.getAsJsonObject();
    }

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsString();
    }

    public static double num(JsonObject o, String key, double def) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsDouble();
    }

    public static int integer(JsonObject o, String key, int def) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsInt();
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsBoolean();
    }

    public static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    public static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || e.isJsonNull()) {
            return out;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                out.add(x.getAsString());
            }
        } else {
            out.add(e.getAsString());
        }
        return out;
    }

    /** Text or structured value: a JSON string stays a string, anything else is kept as-is. */
    public static JsonElement textOrTree(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? null : e.deepCopy();
    }

    public static JsonPrimitive p(String s) {
        return new JsonPrimitive(s);
    }

    /** Rounds for the AI state: the model is bad at arithmetic, so we hand it short, pre-computed numbers. */
    public static JsonPrimitive round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        double r = Math.round(v * f) / f;
        if (decimals == 0 || r == Math.rint(r)) {
            return new JsonPrimitive((long) Math.rint(r));
        }
        return new JsonPrimitive(r);
    }

    /** Extracts the outermost JSON object from model output that may be wrapped in prose or ``` fences. */
    public static JsonObject extractObject(String text) {
        if (text == null) {
            throw new IllegalArgumentException("empty model output");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no JSON object in model output: " + abbreviate(text, 200));
        }
        return parseObject(text.substring(start, end + 1));
    }

    public static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }
}
