package dev.jevmod.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jevmod.JevMod;
import dev.jevmod.core.Json;
import dev.jevmod.core.def.BrainDef;
import dev.jevmod.core.def.ParamDef;
import dev.jevmod.core.def.PowerDef;
import dev.jevmod.core.jev.Answer;
import dev.jevmod.core.jev.EvalResult;
import dev.jevmod.core.jev.JevProvider;
import dev.jevmod.core.jev.QuestionPlan;
import dev.jevmod.core.script.ScriptHost;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * One brain running on one entity.
 *
 * <p>The loop is deliberately non-blocking: a tick gathers sensors and fires one async request, later ticks run the
 * power scripts, and the answers are applied on the first tick after the provider answers. The game thread never
 * waits for the network.
 */
public final class BrainInstance {
    /** A failed call backs off this long before trying again. */
    private static final int ERROR_BACKOFF_TICKS = 100;
    /** Chat asks for an immediate think, but never faster than this. */
    private static final int MIN_THINK_GAP_TICKS = 10;

    private final JevRuntime runtime;
    private final Entity host;
    private final BrainDef brain;
    private final List<PowerDef> powers;
    private final Map<String, PowerState> states = new LinkedHashMap<>();
    /** Live script-sensor instances, keyed by the sensor's state name. */
    private final Map<String, ScriptHost.Instance> sensors = new LinkedHashMap<>();

    private JevProvider provider;
    private String providerError;
    private boolean warned;

    private int thinkTimer;
    private int backoff;
    private boolean forceThink;
    private boolean thinking;

    private CompletableFuture<EvalResult> pending;
    private QuestionPlan pendingPlan;
    private JsonObject pendingState;

    private EvalResult lastResult;
    private List<QuestionPlan.Decision> lastDecisions = List.of();
    private String lastError;
    private int thinkCount;

    BrainInstance(JevRuntime runtime, Entity host, BrainDef brain) {
        this.runtime = runtime;
        this.host = host;
        this.brain = brain;
        this.powers = runtime.defs() == null ? List.of() : runtime.defs().powersOf(brain);
        for (PowerDef p : powers) {
            states.put(p.id, new PowerState(p));
        }
    }

    // ---- accessors ----------------------------------------------------------------------------------------------

    public BrainDef brain() {
        return brain;
    }

    public Entity host() {
        return host;
    }

    public JevRuntime runtime() {
        return runtime;
    }

    public List<PowerDef> powers() {
        return powers;
    }

    public Map<String, PowerState> states() {
        return states;
    }

    public PowerState state(String powerId) {
        return states.get(powerId);
    }

    Map<String, ScriptHost.Instance> sensorInstances() {
        return sensors;
    }

    public String lastError() {
        return lastError;
    }

    public String providerError() {
        return providerError;
    }

    public int thinkCount() {
        return thinkCount;
    }

    public List<QuestionPlan.Decision> lastDecisions() {
        return lastDecisions;
    }

    public EvalResult lastResult() {
        return lastResult;
    }

    public String providerName() {
        return provider == null ? null : provider.name();
    }

    public boolean valid() {
        return !host.isRemoved() && !host.level().isClientSide() && host.isAlive();
    }

    public QuestionPlan.PowerStatus status(String powerId) {
        PowerState s = states.get(powerId);
        return s == null ? null : new QuestionPlan.PowerStatus(s.active, s.params);
    }

    // ---- the loop -----------------------------------------------------------------------------------------------

    public void tick() {
        if (!(host.level() instanceof ServerLevel)) {
            return;
        }
        if (backoff > 0) {
            backoff--;
        }
        for (PowerState s : states.values()) {
            s.tick(this);
        }
        if (thinking) {
            if (pending != null && pending.isDone()) {
                applyPending();
            }
            return;
        }
        thinkTimer++;
        if (backoff > 0) {
            return;
        }
        if (forceThink && thinkTimer >= MIN_THINK_GAP_TICKS) {
            think();
        } else if (thinkTimer >= brain.thinkEveryTicks) {
            think();
        }
    }

    /** Fires one batched request. Never blocks; the answers land in {@link #applyPending()}. */
    public void think() {
        if (thinking || !(host.level() instanceof ServerLevel)) {
            return;
        }
        if (provider == null) {
            try {
                provider = runtime.config().provider(brain.provider, brain.model, System::getenv);
                providerError = null;
            } catch (RuntimeException e) {
                fail(e.getMessage(), ERROR_BACKOFF_TICKS);
                return;
            }
        }
        thinkTimer = 0;
        forceThink = false;
        QuestionPlan plan = QuestionPlan.build(brain, powers, this::status);
        JsonObject state = Sensors.build(this);
        pendingPlan = plan;
        pendingState = state;
        try {
            pending = provider.evaluate(state, plan.questions);
            thinking = true;
        } catch (RuntimeException e) {
            fail("provider rejected the request: " + e.getMessage(), ERROR_BACKOFF_TICKS);
        }
    }

    private void applyPending() {
        CompletableFuture<EvalResult> future = pending;
        pending = null;
        thinking = false;
        EvalResult result;
        try {
            result = future.join();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            fail(cause.getMessage(), ERROR_BACKOFF_TICKS);
            return;
        }
        lastResult = result;
        lastError = null;
        thinkCount++;
        List<QuestionPlan.Decision> decisions = pendingPlan.decide(brain, powers, result.answers, this::status);
        lastDecisions = decisions;
        for (QuestionPlan.Decision d : decisions) {
            apply(d);
        }
        if (runtime.config().showThoughts) {
            for (QuestionPlan.Decision d : decisions) {
                say(d.summary());
            }
        }
        writeLastThink(result, decisions);
    }

    private void apply(QuestionPlan.Decision d) {
        PowerState s = states.get(d.powerId());
        if (s == null) {
            return;
        }
        if (d.kind() == PowerDef.Kind.INSTANT) {
            s.applyParams(d.params(), this);
            if (d.fire()) {
                s.fire(this);
            }
            return;
        }
        if (Boolean.TRUE.equals(d.turnOn())) {
            s.start(this);
        } else if (Boolean.FALSE.equals(d.turnOn())) {
            s.stop(this);
        }
        s.applyParams(d.params(), this);
    }

    private void writeLastThink(EvalResult result, List<QuestionPlan.Decision> decisions) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("brain", brain.id);
            o.addProperty("host", host.getStringUUID());
            o.addProperty("provider", result.model);
            o.addProperty("latency_ms", result.latencyMs);
            o.add("input_tokens", Json.round(result.inputTokens, 0));
            o.add("state", pendingState);
            o.add("questions", pendingPlan.questionsJson());
            JsonObject answers = new JsonObject();
            for (Map.Entry<String, Answer> e : result.answers.entrySet()) {
                answers.add(e.getKey(), e.getValue().toJson());
            }
            o.add("answers", answers);
            JsonArray ds = new JsonArray();
            for (QuestionPlan.Decision d : decisions) {
                JsonObject j = new JsonObject();
                j.addProperty("power", d.powerId());
                j.addProperty("summary", d.summary());
                JsonArray notes = new JsonArray();
                for (String n : d.notes()) {
                    notes.add(n);
                }
                j.add("notes", notes);
                ds.add(j);
            }
            o.add("decisions", ds);
            Path file = runtime.root().resolve("last_think.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.PRETTY.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            JevMod.LOGGER.warn("[jevmod] could not write last_think.json: {}", e.toString());
        }
    }

    private void fail(String message, int backoffTicks) {
        thinking = false;
        pending = null;
        lastError = message;
        backoff = Math.max(backoff, backoffTicks);
        JevMod.LOGGER.warn("[jevmod] brain '{}': {}", brain.id, message);
        if (!warned) {
            warned = true;
            say("cannot think: " + message);
        }
    }

    public void noteError(String message) {
        lastError = message;
    }

    public void say(String message) {
        if (host instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.literal("\u00a7b[" + brain.id + "]\u00a7r " + message));
        }
    }

    /** A chat line arrived for this host. */
    public void onChat(String raw) {
        if (!brain.listenChat) {
            return;
        }
        if (brain.chatPrefix != null && !raw.startsWith(brain.chatPrefix)) {
            return;
        }
        forceThink = true;
    }

    // ---- direct control (commands and GameTests) -----------------------------------------------------------------

    /** Sets a power parameter by hand; the value is coerced into the parameter's own domain. */
    public String setParam(String powerId, String paramId, Object raw) {
        PowerState s = states.get(powerId);
        if (s == null) {
            return "unknown power '" + powerId + "' for this brain";
        }
        ParamDef def = s.def.params.get(paramId);
        if (def == null) {
            return "power '" + powerId + "' has no parameter '" + paramId + "'";
        }
        try {
            Object value = def.coerce(ScriptHost.toJava(raw));
            Map<String, Object> one = new LinkedHashMap<>();
            one.put(paramId, value);
            s.applyParams(one, this);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    public String setPower(String powerId, boolean on) {
        PowerState s = states.get(powerId);
        if (s == null) {
            return "unknown power '" + powerId + "' for this brain";
        }
        if (on) {
            if (s.broken()) return "power '" + powerId + "' is disabled; fix its script and /jev reload";
            if (s.def.kind == PowerDef.Kind.INSTANT) {
                if (s.cooldown() > 0) return "power '" + powerId + "' is cooling down (" + s.cooldown() + " ticks)";
                s.fire(this);
            } else {
                s.start(this);
            }
            if (s.broken()) return "power '" + powerId + "' failed; check the log and /jev reload";
        } else {
            s.stop(this);
        }
        return null;
    }

    public void stopAll() {
        for (PowerState s : states.values()) {
            s.stop(this);
        }
        sensors.clear();
    }
}
