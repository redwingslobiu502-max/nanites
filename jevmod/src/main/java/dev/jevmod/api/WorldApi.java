package dev.jevmod.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** {@code ctx.world}: queries and (guarded) edits of the world the brain's host is in. */
public final class WorldApi {
    private final ServerLevel level;
    private final Entity host;
    private final boolean allowEdits;

    public WorldApi(ServerLevel level, Entity host, boolean allowEdits) {
        this.level = level;
        this.host = host;
        this.allowEdits = allowEdits;
    }

    public ServerLevel mc() {
        return level;
    }

    EntityRef ref(Entity e) {
        return new EntityRef(e, host);
    }

    /**
     * Casts a ray. {@code opts}: {@code {entities: true, fluids: false, ignoreHost: true}}.
     * Returns a {@link Hit} with {@code type} "entity", "block" or "miss" (pos = the end of the ray).
     */
    public Hit raycast(Object from, Object dir, double range, Object opts) {
        Opts o = Opts.of(opts);
        Vec f = Opts.requireVec(from, "raycast from");
        Vec d = Opts.requireVec(dir, "raycast dir").normalize();
        range = Math.max(0.5, Math.min(256, range));
        Vec3 start = f.mc();
        Vec3 end = start.add(d.mc().scale(range));
        ClipContext.Fluid fluid = o.bool("fluids", false) ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
        BlockHitResult bh = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, fluid, host));
        Vec3 blockEnd = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();

        if (o.bool("entities", true)) {
            boolean ignoreHost = o.bool("ignoreHost", true);
            Entity best = null;
            Vec3 bestPos = null;
            double bestD = Double.MAX_VALUE;
            AABB box = new AABB(start, blockEnd).inflate(1.0);
            for (Entity e : level.getEntities((Entity) null, box, x -> x.isPickable() && x.isAlive() && !(ignoreHost && x == host) && !x.isSpectator())) {
                Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(start, blockEnd);
                if (hit.isPresent()) {
                    double dd = start.distanceToSqr(hit.get());
                    if (dd < bestD) {
                        bestD = dd;
                        best = e;
                        bestPos = hit.get();
                    }
                }
            }
            if (best != null) {
                return new Hit("entity", Vec.of(bestPos), ref(best), null, Math.sqrt(bestD));
            }
        }
        if (bh.getType() == HitResult.Type.BLOCK) {
            String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(bh.getBlockPos()).getBlock()).toString();
            return new Hit("block", Vec.of(blockEnd), null, block, start.distanceTo(blockEnd));
        }
        return new Hit("miss", Vec.of(end), null, null, range);
    }

    public Hit raycast(Object from, Object dir, double range) {
        return raycast(from, dir, range, null);
    }

    /** Entities around {@code pos}, nearest first. {@code filter}: "living" (default), "hostile", "players", "all". */
    public Object entitiesNear(Object pos, double radius, String filter) {
        Vec p = Opts.requireVec(pos, "entitiesNear pos");
        radius = Math.max(0.5, Math.min(64, radius));
        String f = filter == null ? "living" : filter;
        Predicate<Entity> pred = switch (f) {
            case "hostile" -> e -> e instanceof Enemy;
            case "players" -> e -> e instanceof Player;
            case "all" -> e -> true;
            default -> e -> e instanceof LivingEntity;
        };
        Vec3 c = p.mc();
        double r2 = radius * radius;
        List<Entity> found = new ArrayList<>(level.getEntities((Entity) null, new AABB(c, c).inflate(radius),
                e -> e != host && e.isAlive() && pred.test(e) && e.position().distanceToSqr(c) <= r2));
        found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(c)));
        List<EntityRef> refs = new ArrayList<>();
        for (Entity e : found) refs.add(ref(e));
        return Js.array(refs);
    }

    public Object entitiesNear(Object pos, double radius) {
        return entitiesNear(pos, radius, "living");
    }

    public String block(Object pos) {
        BlockPos bp = BlockPos.containing(Opts.requireVec(pos, "block pos").mc());
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(bp).getBlock()).toString();
    }

    public boolean isAir(Object pos) {
        return level.getBlockState(BlockPos.containing(Opts.requireVec(pos, "pos").mc())).isAir();
    }

    /** Places a block (e.g. "minecraft:glass"). Disabled when allow_world_edits is false. */
    public boolean setBlock(Object pos, String id) {
        requireEdits("setBlock");
        BlockPos bp = BlockPos.containing(Opts.requireVec(pos, "setBlock pos").mc());
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) throw Context.reportRuntimeError("unknown block '" + id + "'");
        if (!level.isLoaded(bp)) return false;
        Block b = BuiltInRegistries.BLOCK.get(rl);
        BlockState s = b.defaultBlockState();
        return level.setBlockAndUpdate(bp, s);
    }

    public boolean breakBlock(Object pos, boolean drop) {
        requireEdits("breakBlock");
        BlockPos bp = BlockPos.containing(Opts.requireVec(pos, "breakBlock pos").mc());
        if (!level.isLoaded(bp) || level.getBlockState(bp).getDestroySpeed(level, bp) < 0) return false; // never bedrock
        return level.destroyBlock(bp, drop, host, 512);
    }

    /** Explosion. {@code opts}: {@code {fire: false, breakBlocks: false}}. Power is clamped to 0..8. */
    public void explode(Object pos, double power, Object opts) {
        Opts o = Opts.of(opts);
        Vec p = Opts.requireVec(pos, "explode pos");
        boolean breakBlocks = o.bool("breakBlocks", false);
        if (breakBlocks) requireEdits("explode with breakBlocks");
        level.explode(host, p.x, p.y, p.z, (float) Math.max(0, Math.min(8, power)), o.bool("fire", false),
                breakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    public void explode(Object pos, double power) {
        explode(pos, power, null);
    }

    /** Lightning bolt. {@code visualOnly} = true makes it harmless. */
    public void lightning(Object pos, boolean visualOnly) {
        Vec p = Opts.requireVec(pos, "lightning pos");
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(p.x, p.y, p.z);
        bolt.setVisualOnly(visualOnly);
        level.addFreshEntity(bolt);
    }

    /** Spawns an entity type (e.g. "minecraft:iron_golem") and returns its handle (or null). */
    public EntityRef spawn(String type, Object pos) {
        Vec p = Opts.requireVec(pos, "spawn pos");
        ResourceLocation rl = ResourceLocation.tryParse(type.contains(":") ? type : "minecraft:" + type);
        if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) throw Context.reportRuntimeError("unknown entity type '" + type + "'");
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(rl);
        Entity e = t.spawn(level, BlockPos.containing(p.mc()), MobSpawnType.COMMAND);
        return e == null ? null : ref(e);
    }

    /** Server-side particles visible to everyone nearby, e.g. particles("minecraft:flame", pos, 20, 0.3, 0.02). */
    public void particles(String type, Object pos, int count, double spread, double speed) {
        Vec p = Opts.requireVec(pos, "particles pos");
        ParticleOptions opt = VfxApi.particle(type);
        level.sendParticles(opt, p.x, p.y, p.z, Math.max(0, Math.min(500, count)), spread, spread, spread, speed);
    }

    public void sound(String id, Object pos, double volume, double pitch) {
        Vec p = Opts.requireVec(pos, "sound pos");
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        SoundEvent ev = rl == null ? null : BuiltInRegistries.SOUND_EVENT.get(rl);
        if (ev == null) throw Context.reportRuntimeError("unknown sound '" + id + "'");
        level.playSound(null, p.x, p.y, p.z, ev, SoundSource.PLAYERS, (float) volume, (float) pitch);
    }

    public boolean isNight() {
        return level.isNight();
    }

    public boolean isRaining() {
        return level.isRaining();
    }

    public long time() {
        return level.getDayTime() % 24000L;
    }

    public long gameTime() {
        return level.getGameTime();
    }

    public String dimension() {
        return level.dimension().location().toString();
    }

    private void requireEdits(String what) {
        if (!allowEdits) throw Context.reportRuntimeError(what + " is disabled (allow_world_edits = false in config/jevmod/jevmod.json)");
    }
}
