package dev.jevmod.api;

import com.google.gson.JsonObject;
import dev.jevmod.core.Json;
import dev.jevmod.core.script.JsonSerializableApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.mozilla.javascript.Context;

import java.util.Optional;

/**
 * A handle to an entity for scripts. All methods are safe on dead/unloaded entities (they become no-ops).
 * Damage dealt through a handle is attributed to the brain's host.
 */
public final class EntityRef implements JsonSerializableApi {
    private final Entity e;
    private final Entity attacker;

    public EntityRef(Entity e, Entity attacker) {
        this.e = e;
        this.attacker = attacker;
    }

    public Entity mc() {
        return e;
    }

    public int id() {
        return e.getId();
    }

    public String uuid() {
        return e.getStringUUID();
    }

    public String type() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    public String name() {
        return e.getName().getString();
    }

    public Vec pos() {
        return Vec.of(e.position());
    }

    public Vec center() {
        return Vec.of(e.getBoundingBox().getCenter());
    }

    public Vec eyePos() {
        return Vec.of(e.getEyePosition());
    }

    /** Unit vector the entity is looking along. */
    public Vec look() {
        return Vec.of(e.getLookAngle());
    }

    public double yaw() {
        return e.getYRot();
    }

    public double pitch() {
        return e.getXRot();
    }

    public double height() {
        return e.getBbHeight();
    }

    public double width() {
        return e.getBbWidth();
    }

    public boolean isAlive() {
        return e.isAlive();
    }

    public boolean isPlayer() {
        return e instanceof Player;
    }

    public boolean isHostile() {
        return e instanceof Enemy;
    }

    public boolean isLiving() {
        return e instanceof LivingEntity;
    }

    public boolean isOnGround() {
        return e.onGround();
    }

    public boolean is(EntityRef other) {
        return other != null && other.e == e;
    }

    public double health() {
        return e instanceof LivingEntity l ? l.getHealth() : 0;
    }

    public double maxHealth() {
        return e instanceof LivingEntity l ? l.getMaxHealth() : 0;
    }
    /** Read effect state without applying a new effect. */
    public boolean hasEffect(String id){var location=ResourceLocation.tryParse(id.contains(":")?id:"minecraft:"+id);if(location==null)throw Context.reportRuntimeError("bad effect id");var holder=BuiltInRegistries.MOB_EFFECT.getHolder(location);return e instanceof LivingEntity living&&holder.isPresent()&&living.hasEffect(holder.get());}

    public double distanceTo(Object target) {
        Vec v = Opts.requireVec(target, "distanceTo target");
        return e.position().distanceTo(v.mc());
    }

    public Vec velocity() {
        return Vec.of(e.getDeltaMovement());
    }

    public void setVelocity(Object v) {
        Vec d = Opts.requireVec(v, "velocity");
        e.setDeltaMovement(clampVel(d.mc()));
        e.hurtMarked = true;
    }

    /** Adds to the current velocity (knockback, dashes, levitation...). */
    public void push(Object v) {
        Vec d = Opts.requireVec(v, "push");
        e.setDeltaMovement(clampVel(e.getDeltaMovement().add(d.mc())));
        e.hurtMarked = true;
    }

    private static Vec3 clampVel(Vec3 v) {
        double l = v.length();
        return l > 10 ? v.scale(10 / l) : v;
    }

    public void teleport(Object to) {
        Vec v = Opts.requireVec(to, "teleport target");
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(sp.serverLevel(), v.x, v.y, v.z, sp.getYRot(), sp.getXRot());
        } else {
            e.teleportTo(v.x, v.y, v.z);
        }
        e.resetFallDistance();
    }

    /** Hurts the entity; credited to the brain's host so mobs retaliate and kills count. Returns true if it took damage. */
    public boolean damage(double amount) {
        if (!(e.level() instanceof ServerLevel level) || amount <= 0) return false;
        DamageSource src = attacker == null ? level.damageSources().magic()
                : attacker instanceof Player p ? level.damageSources().playerAttack(p)
                : attacker instanceof LivingEntity l ? level.damageSources().mobAttack(l)
                : level.damageSources().magic();
        if (e == attacker) src = level.damageSources().magic();
        return e.hurt(src, (float) amount);
    }

    public void heal(double amount) {
        if (e instanceof LivingEntity l) l.heal((float) amount);
    }

    public void setFire(double seconds) {
        e.igniteForSeconds((float) seconds);
    }

    public void extinguish() {
        e.clearFire();
    }

    /** Applies a status effect, e.g. effect("minecraft:speed", 5, 1). */
    public boolean effect(String id, double seconds, int amplifier) {
        if (!(e instanceof LivingEntity l)) return false;
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (rl == null) throw Context.reportRuntimeError("bad effect id '" + id + "'");
        Optional<net.minecraft.core.Holder.Reference<MobEffect>> h = BuiltInRegistries.MOB_EFFECT.getHolder(rl);
        if (h.isEmpty()) throw Context.reportRuntimeError("unknown effect '" + id + "'");
        return l.addEffect(new MobEffectInstance(h.get(), (int) Math.round(seconds * 20), Math.max(0, amplifier)));
    }

    public boolean hasTag(String tag) {
        return e.getTags().contains(tag);
    }

    public void addTag(String tag) {
        e.addTag(tag);
    }

    @Override
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type());
        o.addProperty("name", name());
        if (e instanceof LivingEntity l) o.add("health", Json.round(l.getHealth(), 1));
        o.add("pos", pos().toJson());
        return o;
    }

    @Override
    public String toString() {
        return type() + "#" + e.getId();
    }
}
