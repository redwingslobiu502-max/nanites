package dev.jevmod.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.jevmod.JevMod;
import dev.jevmod.api.EntityRef;
import dev.jevmod.api.Hit;
import dev.jevmod.api.PowerCtx;
import dev.jevmod.api.Vec;
import dev.jevmod.api.VfxApi;
import dev.jevmod.api.WorldApi;
import dev.jevmod.core.Json;
import dev.jevmod.core.def.SensorDef;
import dev.jevmod.core.script.ScriptError;
import dev.jevmod.core.script.ScriptHost;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Turns the world into the JSON state a brain sends with its questions.
 *
 * <p>The model is bad at arithmetic and at reading long documents, so every sensor pre-computes: distances are in
 * blocks, bearings are degrees to the right of where the host is facing, and lists are capped and sorted.
 */
public final class Sensors {
    private Sensors() {
    }

    /** The whole state object: one key per sensor, named by the sensor's {@code as}. */
    public static JsonObject build(BrainInstance brain) {
        JsonObject state = new JsonObject();
        ServerLevel level = (ServerLevel) brain.host().level();
        Entity host = brain.host();
        for (SensorDef s : brain.brain().sensors) {
            try {
                JsonElement value = read(brain, level, host, s);
                if (value != null) {
                    state.add(s.as, value);
                }
            } catch (RuntimeException e) {
                state.addProperty(s.as + "_error", String.valueOf(e.getMessage()));
            }
        }
        return state;
    }

    private static JsonElement read(BrainInstance brain, ServerLevel level, Entity host, SensorDef s) {
        return switch (s.type) {
            case "self" -> self(host);
            case "nearby_entities" -> nearby(host, s.config);
            case "looking_at" -> lookingAt(level, host, s.config);
            case "environment" -> environment(level, host);
            case "chat" -> chat(brain, level, s.config);
            case "powers" -> powers(brain);
            case "script" -> script(brain, level, host, s);
            default -> null;
        };
    }

    // ---- built-in sensors --------------------------------------------------------------------------------------

    private static JsonObject self(Entity host) {
        JsonObject o = new JsonObject();
        o.addProperty("type", typeId(host));
        o.addProperty("name", host.getName().getString());
        if (host instanceof LivingEntity l) {
            o.add("health", Json.round(l.getHealth(), 1));
            o.add("max_health", Json.round(l.getMaxHealth(), 1));
            JsonArray effects = new JsonArray();
            for (MobEffectInstance e : l.getActiveEffects()) {
                JsonObject j = new JsonObject();
                j.addProperty("id", BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()).toString());
                j.add("amplifier", Json.round(e.getAmplifier(), 0));
                j.add("seconds_left", Json.round(e.getDuration() / 20.0, 0));
                effects.add(j);
            }
            o.add("effects", effects);
        }
        if (host instanceof Player p) {
            o.add("food", Json.round(p.getFoodData().getFoodLevel(), 0));
            o.addProperty("held", BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString());
            o.add("xp_level", Json.round(p.experienceLevel, 0));
            o.addProperty("sneaking", p.isCrouching());
            o.addProperty("sprinting", p.isSprinting());
        }
        o.add("air", Json.round(host.getAirSupply(), 0));
        o.addProperty("on_ground", host.onGround());
        o.addProperty("in_water", host.isInWater());
        o.addProperty("on_fire", host.isOnFire());
        o.add("pos", Vec.of(host.position()).toJson());
        o.add("velocity", Vec.of(host.getDeltaMovement()).toJson());
        o.add("look", Vec.of(host.getLookAngle()).toJson());
        o.add("yaw", Json.round(host.getYRot(), 0));
        o.add("pitch", Json.round(host.getXRot(), 0));
        return o;
    }

    private static JsonArray nearby(Entity host, JsonObject cfg) {
        double radius = clamp(Json.num(cfg, "radius", 16), 1, 64);
        int max = (int) clamp(Json.integer(cfg, "max", 12), 1, 64);
        String filter = Json.str(cfg, "filter", "living");
        Predicate<Entity> pred = switch (filter) {
            case "hostile" -> e -> e instanceof Enemy;
            case "players" -> e -> e instanceof Player;
            case "all" -> e -> true;
            default -> e -> e instanceof LivingEntity;
        };
        Vec3 c = host.position();
        double r2 = radius * radius;
        List<Entity> found = new ArrayList<>(host.level().getEntities((Entity) null, new AABB(c, c).inflate(radius),
                e -> e != host && e.isAlive() && pred.test(e) && e.position().distanceToSqr(c) <= r2));
        found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(c)));
        JsonArray out = new JsonArray();
        for (int i = 0; i < found.size() && i < max; i++) {
            out.add(summary(host, found.get(i)));
        }
        return out;
    }

    private static JsonObject lookingAt(ServerLevel level, Entity host, JsonObject cfg) {
        double range = clamp(Json.num(cfg, "range", 32), 1, 128);
        Hit hit = new WorldApi(level, host, false).raycast(Vec.of(host.getEyePosition()), Vec.of(host.getLookAngle()), range, null);
        JsonObject o = new JsonObject();
        o.addProperty("type", hit.type);
        o.add("distance", Json.round(hit.distance, 1));
        o.add("pos", hit.pos.toJson());
        if (hit.block != null) {
            o.addProperty("block", hit.block);
        }
        if (hit.entity != null) {
            o.add("entity", summary(host, hit.entity.mc()));
        }
        return o;
    }

    private static JsonObject environment(ServerLevel level, Entity host) {
        JsonObject o = new JsonObject();
        long t = level.getDayTime() % 24000L;
        long hours = (t / 1000L + 6L) % 24L;
        long minutes = (t % 1000L) * 60L / 1000L;
        o.addProperty("time", String.format(Locale.ROOT, "%02d:%02d", hours, minutes));
        o.addProperty("is_day", level.isDay());
        o.addProperty("is_night", level.isNight());
        o.addProperty("raining", level.isRaining());
        o.addProperty("thundering", level.isThundering());
        o.addProperty("dimension", level.dimension().location().toString());
        o.add("y", Json.round(host.getY(), 0));
        o.add("light", Json.round(level.getMaxLocalRawBrightness(host.blockPosition()), 0));
        o.addProperty("biome", level.getBiome(host.blockPosition()).unwrapKey()
                .map(k -> k.location().toString()).orElse("unknown"));
        return o;
    }

    private static JsonArray chat(BrainInstance brain, ServerLevel level, JsonObject cfg) {
        int max = (int) clamp(Json.integer(cfg, "max", 5), 1, 16);
        JsonArray out = new JsonArray();
        for (ChatLog.Line l : brain.runtime().chatLines(brain.host().getUUID(), max)) {
            JsonObject j = new JsonObject();
            j.addProperty("text", l.text());
            j.add("seconds_ago", Json.round(Math.max(0, level.getGameTime() - l.tick()) / 20.0, 0));
            out.add(j);
        }
        return out;
    }

    private static JsonObject powers(BrainInstance brain) {
        JsonObject o = new JsonObject();
        for (PowerState s : brain.states().values()) {
            JsonObject p = new JsonObject();
            p.addProperty("active", s.active());
            if (s.def.cooldownTicks > 0) {
                p.add("cooldown_ticks", Json.round(s.cooldown(), 0));
            }
            JsonObject params = new JsonObject();
            for (Map.Entry<String, Object> e : s.params().entrySet()) {
                params.add(e.getKey(), value(e.getValue()));
            }
            p.add("params", params);
            o.add(s.def.id, p);
        }
        return o;
    }

    private static JsonElement script(BrainInstance brain, ServerLevel level, Entity host, SensorDef s) {
        String file = Json.str(s.config, "file", null);
        if (file == null) {
            throw new IllegalStateException("script sensor '" + s.as + "' has no \"file\"");
        }
        Path path = brain.runtime().root().resolve("sensors").resolve(file);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("sensor script not found: " + path);
        }
        ScriptHost.Instance inst = brain.sensorInstances().get(s.as);
        if (inst == null) {
            PowerCtx ctx = new PowerCtx(new EntityRef(host, host),
                    new WorldApi(level, host, false),
                    new VfxApi(level, host, brain.brain().id + "/" + s.as),
                    brain.brain().id, "sensor:" + s.as, JevMod.LOGGER);
            Map<String, Object> globals = new java.util.LinkedHashMap<>();
            globals.put("ctx", ctx);
            globals.put("params", ScriptHost.fromJson(s.config));
            try {
                inst = brain.runtime().scripts().instantiate(brain.runtime().script(path), globals);
            } catch (IOException | ScriptError e) {
                throw new IllegalStateException("sensor '" + s.as + "': " + e.getMessage(), e);
            }
            brain.sensorInstances().put(s.as, inst);
        }
        Object out = inst.call("sense");
        return out == null ? null : ScriptHost.toJson(out);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private static JsonObject summary(Entity host, Entity e) {
        JsonObject o = new JsonObject();
        o.addProperty("type", typeId(e));
        o.addProperty("name", e.getName().getString());
        o.add("distance", Json.round(host.position().distanceTo(e.position()), 1));
        o.add("bearing", Json.round(bearing(host, e), 0));
        o.addProperty("hostile", e instanceof Enemy);
        if (e instanceof LivingEntity l) {
            o.add("health", Json.round(l.getHealth(), 1));
        }
        o.add("pos", Vec.of(e.position()).toJson());
        return o;
    }

    /** Degrees to the right of the way the host is facing: 0 ahead, 90 right, +/-180 behind. */
    private static double bearing(Entity host, Entity e) {
        Vec3 d = e.position().subtract(host.position());
        double target = Math.toDegrees(Math.atan2(-d.x, d.z));
        return Mth.wrapDegrees(target - host.getYRot());
    }

    private static String typeId(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    static JsonElement value(Object v) {
        if (v == null) {
            return new JsonPrimitive("");
        }
        if (v instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (v instanceof Number n) {
            return Json.round(n.doubleValue(), 2);
        }
        return new JsonPrimitive(String.valueOf(v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
