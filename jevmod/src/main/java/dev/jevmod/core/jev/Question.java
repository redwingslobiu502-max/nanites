package dev.jevmod.core.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A typed Jev question, mirroring TypeSafe's System One API (https://docs.typesafe.ai/api):
 * noul (yes/no probability), choice (one of N options) and score (ordered levels).
 */
public final class Question {
    public enum Type { NOUL, CHOICE, SCORE }

    public final String id;
    public final Type type;
    public final JsonElement instructions;
    /** noul: optional {"true": .., "false": ..}; choice: {option: rubric|null}; score: [level, ...]. */
    public final JsonElement criteria;

    public Question(String id, Type type, JsonElement instructions, JsonElement criteria) {
        this.id = id;
        this.type = type;
        this.instructions = instructions;
        this.criteria = criteria;
    }

    /** Wire format of the native TypeSafe API. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type.name().toLowerCase());
        o.add("instructions", instructions);
        if (criteria != null) {
            o.add("criteria", criteria);
        }
        return o;
    }

    public List<String> options() {
        List<String> out = new ArrayList<>();
        if (type == Type.CHOICE && criteria != null && criteria.isJsonObject()) {
            out.addAll(criteria.getAsJsonObject().keySet());
        }
        return out;
    }

    public int levels() {
        return type == Type.SCORE && criteria instanceof JsonArray a ? a.size() : 0;
    }
}
