package dev.jevmod.core.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.jevmod.core.Json;
import dev.jevmod.core.def.BrainDef;
import dev.jevmod.core.def.ParamDef;
import dev.jevmod.core.def.PowerDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns a brain + its powers into one batch of typed questions, and maps the answers back into gated decisions.
 * This is the "function calling" pattern from TypeSafe's cookbook: one noul per power ("use it now?") plus one
 * question per AI-controlled parameter, all asked in a single request.
 *
 * <p>Question ids are {@code <power>__active} / {@code <power>__use} and {@code <power>__<param>}.
 */
public final class QuestionPlan {
    /** What the game currently knows about a power, so questions can say "currently on, color red". */
    public record PowerStatus(boolean active, Map<String, Object> params) {
    }

    /** Which power (and optionally which parameter) a question id belongs to. */
    public record Target(String powerId, String paramId) {
    }

    /** One gated decision for a power. {@code turnOn == null} means "no change". */
    public record Decision(String powerId, PowerDef.Kind kind, Boolean turnOn, boolean fire, double pYes,
                           Map<String, Object> params, List<String> notes) {
        public String summary() {
            String what = kind == PowerDef.Kind.INSTANT
                    ? (fire ? "FIRE" : "hold")
                    : (turnOn == null ? "keep" : turnOn ? "ON" : "OFF");
            return powerId + ": " + what + " (p=" + String.format(java.util.Locale.ROOT, "%.2f", pYes) + ") " + params;
        }
    }

    public final Map<String, Question> questions;
    public final Map<String, Target> targets;

    private QuestionPlan(Map<String, Question> questions, Map<String, Target> targets) {
        this.questions = questions;
        this.targets = targets;
    }

    public static String activeId(PowerDef p) {
        return p.id + (p.kind == PowerDef.Kind.TOGGLE ? "__active" : "__use");
    }

    public static String paramId(PowerDef p, ParamDef param) {
        return p.id + "__" + param.id;
    }

    public static QuestionPlan build(BrainDef brain, List<PowerDef> powers, Function<String, PowerStatus> status) {
        Map<String, Question> qs = new LinkedHashMap<>();
        Map<String, Target> targets = new LinkedHashMap<>();
        for (PowerDef p : powers) {
            PowerStatus st = status.apply(p.id);
            JsonObject power = new JsonObject();
            power.addProperty("name", p.name);
            power.addProperty("description", p.description);
            if (!p.useWhen.isEmpty()) {
                power.addProperty("use_when", p.useWhen);
            }
            if (p.kind == PowerDef.Kind.TOGGLE) {
                power.addProperty("currently", st != null && st.active() ? "on" : "off");
            }

            JsonObject ins = new JsonObject();
            ins.add("brain_rules", brain.instructions);
            ins.add("power", power);
            JsonObject crit = new JsonObject();
            if (p.kind == PowerDef.Kind.TOGGLE) {
                ins.addProperty("question", "Should the power `power` be ON right now? Decide from `brain_rules`, `power.use_when` "
                        + "and the state. The owner's newest chat message in the state overrides older ones.");
                crit.addProperty("true", "Turn it on, or keep it on");
                crit.addProperty("false", "Turn it off, or keep it off");
            } else {
                ins.addProperty("question", "Should the brain use the power `power` right now (a single shot)? Decide from "
                        + "`brain_rules`, `power.use_when` and the state. The owner's newest chat message in the state overrides older ones.");
                crit.addProperty("true", "Use it now");
                crit.addProperty("false", "Do not use it now");
            }
            String aid = activeId(p);
            qs.put(aid, new Question(aid, Question.Type.NOUL, ins, crit));
            targets.put(aid, new Target(p.id, null));

            for (ParamDef param : p.params.values()) {
                if (!param.ai) continue;
                JsonObject pi = new JsonObject();
                pi.add("brain_rules", brain.instructions);
                pi.add("power", power);
                JsonObject pd = new JsonObject();
                pd.addProperty("name", param.id);
                pd.addProperty("description", param.description);
                Object cur = st != null && st.params() != null && st.params().containsKey(param.id) ? st.params().get(param.id) : param.defaultValue;
                pd.add("current", param.toJsonValue(cur));
                pi.add("parameter", pd);
                String qid = paramId(p, param);
                Question q;
                switch (param.type) {
                    case BOOL -> {
                        pi.addProperty("question", "If `power` is used now, should `parameter` be enabled? If neither the owner nor the "
                                + "situation asks for a change, answer according to `parameter.current`.");
                        q = new Question(qid, Question.Type.NOUL, pi, null);
                    }
                    case CHOICE -> {
                        pi.addProperty("question", "If `power` is used now, which option should `parameter` be? If neither the owner nor "
                                + "the situation asks for a change, pick `parameter.current`.");
                        JsonObject opts = new JsonObject();
                        for (Map.Entry<String, JsonElement> e : param.options.entrySet()) {
                            opts.add(e.getKey(), e.getValue() == null ? JsonNull.INSTANCE : e.getValue());
                        }
                        q = new Question(qid, Question.Type.CHOICE, pi, opts);
                    }
                    default -> {
                        pi.addProperty("question", "If `power` is used now, what level should `parameter` have? If neither the owner nor "
                                + "the situation asks for a change, pick the level closest to `parameter.current`.");
                        JsonArray levels = new JsonArray();
                        for (String l : param.levelLabels) levels.add(l);
                        q = new Question(qid, Question.Type.SCORE, pi, levels);
                    }
                }
                qs.put(qid, q);
                targets.put(qid, new Target(p.id, param.id));
            }
        }
        return new QuestionPlan(Collections.unmodifiableMap(qs), Collections.unmodifiableMap(targets));
    }

    /**
     * Gates the raw answers: toggles use hysteresis (on >= activate_threshold, off <= deactivate_threshold),
     * parameters only change when the answer's confidence clears min_confidence. Missing answers change nothing.
     */
    public List<Decision> decide(BrainDef brain, List<PowerDef> powers, Map<String, Answer> answers, Function<String, PowerStatus> status) {
        List<Decision> out = new ArrayList<>();
        for (PowerDef p : powers) {
            PowerStatus st = status.apply(p.id);
            boolean active = st != null && st.active();
            List<String> notes = new ArrayList<>();
            Map<String, Object> params = new LinkedHashMap<>(p.defaults());
            if (st != null && st.params() != null) params.putAll(st.params());

            Answer a = answers.get(activeId(p));
            double pYes = a == null ? (active ? 1 : 0) : a.noul;
            if (a == null) notes.add("no answer for " + activeId(p));

            for (ParamDef param : p.params.values()) {
                if (!param.ai) continue;
                Answer pa = answers.get(paramId(p, param));
                if (pa == null) {
                    notes.add(param.id + ": no answer, kept " + params.get(param.id));
                    continue;
                }
                double need = param.minConfidence >= 0 ? param.minConfidence : brain.minConfidence;
                switch (param.type) {
                    case BOOL -> {
                        // a noul's own confidence is |2p-1|; require the same bar as choices/scores.
                        if (pa.confidence >= need) params.put(param.id, pa.noul >= 0.5);
                        else notes.add(param.id + ": unsure (p=" + fmt(pa.noul) + "), kept " + params.get(param.id));
                    }
                    case CHOICE -> {
                        if (pa.choice != null && param.options.containsKey(pa.choice) && pa.confidence >= need) params.put(param.id, pa.choice);
                        else notes.add(param.id + ": rejected '" + pa.choice + "' (confidence " + fmt(pa.confidence) + "), kept " + params.get(param.id));
                    }
                    default -> {
                        if (pa.confidence >= need) params.put(param.id, param.levelToValue(pa.score));
                        else notes.add(param.id + ": unsure (confidence " + fmt(pa.confidence) + "), kept " + params.get(param.id));
                    }
                }
            }

            if (p.kind == PowerDef.Kind.TOGGLE) {
                Boolean turnOn = null;
                if (a != null) {
                    if (!active && pYes >= brain.activateThreshold) turnOn = Boolean.TRUE;
                    else if (active && pYes <= brain.deactivateThreshold) turnOn = Boolean.FALSE;
                }
                out.add(new Decision(p.id, p.kind, turnOn, false, pYes, params, notes));
            } else {
                out.add(new Decision(p.id, p.kind, null, a != null && pYes >= brain.activateThreshold, pYes, params, notes));
            }
        }
        return out;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** The batch as the native TypeSafe "questions" map. */
    public JsonObject questionsJson() {
        JsonObject o = new JsonObject();
        for (Question q : questions.values()) o.add(q.id, q.toJson());
        return o;
    }

    @Override
    public String toString() {
        return Json.GSON.toJson(questionsJson());
    }
}
