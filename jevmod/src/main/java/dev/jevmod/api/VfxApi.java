package dev.jevmod.api;

import dev.jevmod.net.JevNetwork;
import dev.jevmod.net.VfxPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.mozilla.javascript.Context;

/**
 * {@code ctx.vfx}: keyed coloured particle effects plus particle helpers.
 * Effects are keyed: calling {@code beam({id: "main", ...})} every tick updates the same beam smoothly; when calls stop
 * it fades out after {@code duration} ticks.
 */
public final class VfxApi {
    private final ServerLevel level;
    private final Entity host;
    private final String keyPrefix;

    public VfxApi(ServerLevel level, Entity host, String keyPrefix) {
        this.level = level;
        this.host = host;
        this.keyPrefix = keyPrefix;
    }

    private String key(Opts o, String def) {
        return keyPrefix + "/" + o.str("id", def);
    }

    private static byte style(String s) {
        return switch (s == null ? "solid" : s) {
            case "pulse" -> VfxPayload.STYLE_PULSE;
            case "arc", "electric", "lightning" -> VfxPayload.STYLE_ARC;
            default -> VfxPayload.STYLE_SOLID;
        };
    }

    /**
     * A glowing beam. {@code {id, from, to, color, width: 0.15, intensity: 1, style: "solid"|"pulse"|"arc", duration: 3}}.
     * Without {@code from}, the beam starts at the host's eyes and follows them smoothly on the client.
     */
    public void beam(Object opts) {
        Opts o = Opts.of(opts);
        Vec to = o.vec("to", null);
        if (to == null) throw Context.reportRuntimeError("vfx.beam needs a 'to' position");
        Vec from = o.vec("from", null);
        EntityRef anchor = o.entity("anchor");
        int anchorId = -1;
        byte mode = VfxPayload.ANCHOR_NONE;
        if (from == null) {
            Entity a = anchor != null ? anchor.mc() : host;
            from = Vec.of(a.getEyePosition());
            anchorId = a.getId();
            mode = VfxPayload.ANCHOR_EYES;
        }
        send(new VfxPayload(VfxPayload.BEAM, key(o, "beam"), anchorId, mode, from.x, from.y, from.z, to.x, to.y, to.z,
                o.color("color", 0xFF2A1F), (float) clamp(o.num("width", 0.15), 0.01, 3), (float) clamp(o.num("intensity", 1), 0, 4),
                (int) clamp(o.integer("duration", 3), 1, 400), style(o.str("style", "solid")), 0));
    }

    /** An expanding shockwave ring. {@code {id, pos, color, radius: 4, width: 0.35, duration: 14}}. */
    public void ring(Object opts) {
        Opts o = Opts.of(opts);
        Vec p = o.vec("pos", Vec.of(host.position()).add(0, 0.1, 0));
        send(new VfxPayload(VfxPayload.RING, key(o, "ring" + level.getGameTime()), -1, VfxPayload.ANCHOR_NONE, p.x, p.y, p.z, 0, 0, 0,
                o.color("color", 0x2AF4FF), (float) clamp(o.num("width", 0.35), 0.02, 4), (float) clamp(o.num("intensity", 1), 0, 4),
                (int) clamp(o.integer("duration", 14), 1, 400), VfxPayload.STYLE_SOLID, (float) clamp(o.num("radius", 4), 0.2, 48)));
    }

    /** A glowing orb (camera-facing). {@code {id, pos | on: entity, color, radius: 0.5, intensity, duration: 6, style}}. */
    public void orb(Object opts) {
        shape(VfxPayload.ORB, Opts.of(opts), 0.5, "orb");
    }

    /** A translucent energy shell, e.g. a shield bubble. {@code {id, pos | on: entity, color, radius: 1.6, duration: 6}}. */
    public void sphere(Object opts) {
        shape(VfxPayload.SPHERE, Opts.of(opts), 1.6, "sphere");
    }

    private void shape(byte kind, Opts o, double defRadius, String defId) {
        Object on = o.raw("on");
        int anchorId = -1;
        byte mode = VfxPayload.ANCHOR_NONE;
        Vec p;
        if (on instanceof EntityRef e) {
            anchorId = e.mc().getId();
            mode = VfxPayload.ANCHOR_CENTER;
            p = e.center();
        } else {
            p = o.vec("pos", null);
            if (p == null) {
                anchorId = host.getId();
                mode = VfxPayload.ANCHOR_CENTER;
                p = Vec.of(host.getBoundingBox().getCenter());
            }
        }
        send(new VfxPayload(kind, key(o, defId), anchorId, mode, p.x, p.y, p.z, 0, 0, 0,
                o.color("color", 0x2AF4FF), 0, (float) clamp(o.num("intensity", 1), 0, 4),
                (int) clamp(o.integer("duration", 6), 1, 400), style(o.str("style", "solid")), (float) clamp(o.num("radius", defRadius), 0.05, 32)));
    }

    /** Removes a keyed effect immediately (e.g. in onStop). */
    public void stop(String id) {
        VfxPayload p = VfxPayload.stop(keyPrefix + "/" + id);
        Vec3Like pos = new Vec3Like(host.getX(), host.getY(), host.getZ());
        JevNetwork.send(level, new VfxPayload(p.kind(), p.key(), -1, VfxPayload.ANCHOR_NONE, pos.x, pos.y, pos.z, 0, 0, 0, 0, 0, 0, 0, (byte) 0, 0));
    }

    /** Stops every keyed effect belonging to this power instance. */
    public void stopAll() {
        stop("*");
    }

    private record Vec3Like(double x, double y, double z) {
    }

    /** Coloured dust particles: {@code dust(pos, "red", 1.5, 20, 0.3)}. */
    public void dust(Object pos, Object color, double size, int count, double spread) {
        Vec p = Opts.requireVec(pos, "dust pos");
        int c = dev.jevmod.core.Colors.parse(ScriptHostBridge.plain(color));
        Vector3f col = new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        level.sendParticles(new DustParticleOptions(col, (float) clamp(size, 0.1, 4)), p.x, p.y, p.z,
                (int) clamp(count, 0, 500), spread, spread, spread, 0);
    }

    /** Particles of any simple type along a line, e.g. {@code trail(a, b, "minecraft:end_rod", 0.4)}. */
    public void trail(Object from, Object to, String type, double step) {
        Vec a = Opts.requireVec(from, "trail from");
        Vec b = Opts.requireVec(to, "trail to");
        ParticleOptions opt = particle(type);
        double len = a.distance(b);
        step = Math.max(0.1, step);
        int n = (int) Math.min(400, Math.ceil(len / step));
        for (int i = 0; i <= n; i++) {
            Vec p = a.lerp(b, n == 0 ? 0 : (double) i / n);
            level.sendParticles(opt, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** A burst of particles flying outwards: {@code burst(pos, "minecraft:firework", 40, 0.25)}. */
    public void burst(Object pos, String type, int count, double speed) {
        Vec p = Opts.requireVec(pos, "burst pos");
        level.sendParticles(particle(type), p.x, p.y, p.z, (int) clamp(count, 0, 500), 0.1, 0.1, 0.1, speed);
    }

    static ParticleOptions particle(String type) {
        ResourceLocation rl = ResourceLocation.tryParse(type.contains(":") ? type : "minecraft:" + type);
        ParticleType<?> t = rl == null ? null : BuiltInRegistries.PARTICLE_TYPE.get(rl);
        if (t instanceof SimpleParticleType s) return s;
        throw Context.reportRuntimeError("particle '" + type + "' is unknown or needs options (use vfx.dust for coloured particles)");
    }

    private void send(VfxPayload p) {
        JevNetwork.send(level, p);
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    /** Converts JS values passed as plain Objects. */
    private static final class ScriptHostBridge {
        static Object plain(Object v) {
            return dev.jevmod.core.script.ScriptHost.toJava(v);
        }
    }
}
