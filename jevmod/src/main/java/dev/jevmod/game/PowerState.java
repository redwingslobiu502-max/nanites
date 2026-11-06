package dev.jevmod.game;

import dev.jevmod.JevMod;
import dev.jevmod.api.EntityRef;
import dev.jevmod.api.PowerCtx;
import dev.jevmod.api.VfxApi;
import dev.jevmod.api.WorldApi;
import dev.jevmod.core.def.PowerDef;
import dev.jevmod.core.script.ScriptError;
import dev.jevmod.core.script.ScriptHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One power on one brain: the script instance, its parameters, and the small state machine around them.
 *
 * <p>Toggle powers run {@code onStart -> onTick... -> onStop}; instant powers run {@code run} once. A script that
 * throws is disabled until the next reload instead of spamming the log every tick.
 */
public final class PowerState {
    final PowerDef def;
    final Map<String, Object> params;

    boolean active;
    long tick;
    int cooldown;
    boolean broken;

    private ScriptHost.Instance instance;
    private PowerCtx ctx;

    PowerState(PowerDef def) {
        this.def = def;
        this.params = new LinkedHashMap<>(def.defaults());
    }

    public boolean active() {
        return active;
    }

    public boolean broken() {
        return broken;
    }

    public int cooldown() {
        return cooldown;
    }

    public Map<String, Object> params() {
        return params;
    }

    public PowerDef definition() {
        return def;
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------

    void tick(BrainInstance brain) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (!active || broken) {
            return;
        }
        ctx.tick = tick;
        try {
            if (instance.has("onTick")) {
                instance.call("onTick", ctx, params);
            }
        } catch (ScriptError e) {
            error(brain, e);
            stop(brain);
            return;
        }
        if (ctx.consumeStop()) {
            stop(brain);
            return;
        }
        tick++;
        if (def.maxActiveTicks > 0 && tick >= def.maxActiveTicks) {
            stop(brain);
        }
    }

    void start(BrainInstance brain) {
        if (active || broken) {
            return;
        }
        ctx = newContext(brain);
        ctx.tick = 0;
        tick = 0;
        instance = instantiate(brain, ctx);
        if (instance == null) {
            return;
        }
        active = true;
        try {
            if (instance.has("onStart")) {
                instance.call("onStart", ctx, params);
            }
        } catch (ScriptError e) {
            error(brain, e);
        }
    }

    void stop(BrainInstance brain) {
        if (!active) {
            return;
        }
        active = false;
        try {
            if (instance != null && instance.has("onStop")) {
                instance.call("onStop", ctx, params);
            }
        } catch (ScriptError e) {
            error(brain, e);
        }
        if (ctx != null && ctx.vfx != null) {
            try {
                ctx.vfx.stopAll();
            } catch (RuntimeException e) {
                JevMod.LOGGER.debug("[jevmod] could not clear vfx for {}", def.id, e);
            }
        }
        instance = null;
        ctx = null;
        tick = 0;
    }

    void fire(BrainInstance brain) {
        if (broken || cooldown > 0) {
            return;
        }
        cooldown = def.cooldownTicks;
        PowerCtx c = newContext(brain);
        c.tick = 0;
        ScriptHost.Instance inst = instantiate(brain, c);
        if (inst == null) {
            return;
        }
        try {
            if (inst.has("run")) {
                inst.call("run", c, params);
            }
        } catch (ScriptError e) {
            error(brain, e);
        }
    }

    /** Applies whatever the AI changed; only real changes reach {@code onChange}. */
    void applyParams(Map<String, Object> next, BrainInstance brain) {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : next.entrySet()) {
            if (!Objects.equals(params.get(e.getKey()), e.getValue())) {
                changed.put(e.getKey(), e.getValue());
            }
        }
        if (changed.isEmpty()) {
            return;
        }
        Map<String, Object> old = new LinkedHashMap<>(params);
        params.putAll(changed);
        if (instance == null) {
            return;
        }
        instance.set("params", params);
        try {
            if (instance.has("onChange")) {
                instance.call("onChange", ctx, params, changed, old);
            }
        } catch (ScriptError e) {
            error(brain, e);
        }
    }

    // ---- scripts ------------------------------------------------------------------------------------------------

    private PowerCtx newContext(BrainInstance brain) {
        ServerLevel level = (ServerLevel) brain.host().level();
        Entity host = brain.host();
        String prefix = host.getStringUUID() + "/" + brain.brain().id + "/" + def.id;
        WorldApi world = new WorldApi(level, host, brain.runtime().config().allowWorldEdits);
        VfxApi vfx = new VfxApi(level, host, prefix);
        return new PowerCtx(new EntityRef(host, host), world, vfx, brain.brain().id, def.id, JevMod.LOGGER);
    }

    private ScriptHost.Instance instantiate(BrainInstance brain, PowerCtx context) {
        ScriptHost scriptHost = brain.runtime().scripts();
        try {
            Map<String, Object> globals = new LinkedHashMap<>();
            globals.put("ctx", context);
            globals.put("params", params);
            return scriptHost.instantiate(brain.runtime().script(def.scriptPath()), globals);
        } catch (ScriptError | IOException e) {
            broken = true;
            brain.say("power '" + def.id + "' disabled: " + e.getMessage());
            JevMod.LOGGER.error("[jevmod] power '{}' failed to load", def.id, e);
            return null;
        }
    }

    private void error(BrainInstance brain, ScriptError e) {
        broken = true;
        brain.say("power '" + def.id + "' threw and is now disabled: " + e.getMessage());
        JevMod.LOGGER.error("[jevmod] power '{}' on brain '{}' threw", def.id, brain.brain().id, e);
        stop(brain);
    }
}
