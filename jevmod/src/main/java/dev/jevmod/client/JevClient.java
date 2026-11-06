package dev.jevmod.client;

import dev.jevmod.net.JevNetwork;
import dev.jevmod.net.VfxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-only keyed effects, drawn with vanilla coloured particles. */
public final class JevClient {
    private static final Map<String, Effect> EFFECTS = new LinkedHashMap<>();
    private static Object lastLevel;

    private JevClient() {}

    public static void init() {
        JevNetwork.clientHandler = p -> Minecraft.getInstance().execute(() -> receive(p));
        NeoForge.EVENT_BUS.addListener(JevClient::tick);
    }

    private static void receive(VfxPayload p) {
        syncLevel();
        if (p.kind() == VfxPayload.STOP) {
            if (p.key().endsWith("/*")) {
                String prefix = p.key().substring(0, p.key().length() - 1);
                EFFECTS.keySet().removeIf(key -> key.startsWith(prefix));
            } else {
                EFFECTS.remove(p.key());
            }
            return;
        }
        if (Minecraft.getInstance().level == null) return;
        if (EFFECTS.size() >= 256 && !EFFECTS.containsKey(p.key())) {
            EFFECTS.remove(EFFECTS.keySet().iterator().next());
        }
        EFFECTS.put(p.key(), new Effect(p));
    }

    private static void syncLevel() {
        Object level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            EFFECTS.clear();
            lastLevel = level;
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        syncLevel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        int budget = 512;
        var it = EFFECTS.values().iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            if (e.age >= e.packet.duration()) {
                it.remove();
                continue;
            }
            if (budget >= 32) {
                draw(e);
                budget -= 32;
            }
            e.age++;
        }
    }

    private static void draw(Effect e) {
        var level = Minecraft.getInstance().level;
        VfxPayload p = e.packet;
        Vec3 start = new Vec3(p.fx(), p.fy(), p.fz());
        if (p.anchor() >= 0) {
            Entity anchor = level.getEntity(p.anchor());
            if (anchor == null) return;
            start = p.anchorMode() == VfxPayload.ANCHOR_EYES
                    ? anchor.getEyePosition() : anchor.getBoundingBox().getCenter();
        }
        int c = p.color();
        var dust = new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255f,
                (c >> 8 & 255) / 255f, (c & 255) / 255f),
                Math.max(0.1f, Math.min(4f, p.width() > 0 ? p.width() * 8 : p.extra())));
        Vec3 end = new Vec3(p.tx(), p.ty(), p.tz());
        for (int i = 0; i < 32; i++) {
            double t = i / 31.0;
            double angle = Math.PI * 2 * t;
            Vec3 pos;
            if (p.kind() == VfxPayload.BEAM) {
                pos = start.lerp(end, t);
                if (p.style() == VfxPayload.STYLE_ARC && i > 0 && i < 31) {
                    pos = pos.add(0, Math.sin(i * 2.4 + e.age) * 0.15, Math.cos(i * 1.7 + e.age) * 0.15);
                }
            } else if (p.kind() == VfxPayload.RING) {
                double radius = p.extra() * (e.age + 1.0) / p.duration();
                pos = start.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            } else {
                double y = 1 - 2 * t;
                double radius = p.extra() * (p.kind() == VfxPayload.ORB ? 0.5 : 1);
                double a = i * 2.399963 + e.age * 0.1;
                double r = Math.sqrt(Math.max(0, 1 - y * y)) * radius;
                pos = start.add(Math.cos(a) * r, y * radius, Math.sin(a) * r);
            }
            if (p.intensity() > 0 && (p.style() != VfxPayload.STYLE_PULSE || e.age % 4 < 2)) {
                level.addParticle(dust, true, pos.x, pos.y, pos.z, 0, 0, 0);
            }
        }
    }

    private static final class Effect {
        final VfxPayload packet;
        int age;
        Effect(VfxPayload packet) { this.packet = packet; }
    }
}
