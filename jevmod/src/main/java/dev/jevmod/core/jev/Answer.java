package dev.jevmod.core.jev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.jevmod.core.Json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One answer, in the same shape TypeSafe returns (noul / choice / score + confidence). */
public final class Answer {
    public final Question.Type type;
    /** noul: probability of "yes" (0..1). */
    public final double noul;
    /** choice: the selected option. */
    public final String choice;
    /** score: probability-weighted level index (0-based, can land between levels). */
    public final double score;
    public final Map<String, Double> probabilities;
    public final double confidence;

    private Answer(Question.Type type, double noul, String choice, double score, Map<String, Double> probabilities, double confidence) {
        this.type = type;
        this.noul = noul;
        this.choice = choice;
        this.score = score;
        this.probabilities = probabilities;
        this.confidence = confidence;
    }

    public static Answer noul(double p) {
        double c = clamp01(p);
        return new Answer(Question.Type.NOUL, c, null, 0, Map.of("yes", c, "no", 1 - c), Math.abs(2 * c - 1));
    }

    public static Answer choice(String option, Map<String, Double> probabilities, double confidence) {
        return new Answer(Question.Type.CHOICE, 0, option, 0, Collections.unmodifiableMap(new LinkedHashMap<>(probabilities)), clamp01(confidence));
    }

    public static Answer score(double level, Map<String, Double> probabilities, double confidence) {
        return new Answer(Question.Type.SCORE, 0, null, level, Collections.unmodifiableMap(new LinkedHashMap<>(probabilities)), clamp01(confidence));
    }

    static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    /** Parses one native TypeSafe answer object. */
    public static Answer fromTypeSafe(JsonObject a) {
        String t = Json.str(a, "type", "");
        Map<String, Double> probs = new LinkedHashMap<>();
        JsonObject po = Json.obj(a, "probabilities");
        if (po != null) {
            for (Map.Entry<String, JsonElement> e : po.entrySet()) {
                probs.put(e.getKey(), e.getValue().getAsDouble());
            }
        }
        return switch (t) {
            case "noul" -> noul(Json.num(a, "noul", 0));
            case "choice" -> choice(Json.str(a, "choice", null), probs, Json.num(a, "confidence", 0));
            case "score" -> score(Json.num(a, "score", 0), probs, Json.num(a, "confidence", 0));
            default -> throw new IllegalArgumentException("unknown answer type '" + t + "'");
        };
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type.name().toLowerCase());
        switch (type) {
            case NOUL -> o.add("noul", Json.round(noul, 3));
            case CHOICE -> o.addProperty("choice", choice);
            case SCORE -> o.add("score", Json.round(score, 3));
        }
        o.add("confidence", Json.round(confidence, 3));
        return o;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }
}
