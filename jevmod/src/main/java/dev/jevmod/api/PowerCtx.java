package dev.jevmod.api;

import dev.jevmod.core.Colors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Random;

/**
 * The {@code ctx} object every power and sensor script receives.
 * <pre>
 *   ctx.host    EntityRef  the entity the brain runs on (the player in V1)
 *   ctx.world   WorldApi   raycasts, entities, blocks, explosions, sounds, particles
 *   ctx.vfx     VfxApi     beams, rings, orbs, spheres, dust, trails, bursts
 *   ctx.brain   string     brain id;  ctx.power: power id
 *   ctx.tick    number     ticks since this power turned on (toggle) / 0 (instant)
 *   ctx.vec(x,y,z), ctx.color("red"), ctx.random(), ctx.log(msg), ctx.say(msg), ctx.stop()
 * </pre>
 */
public final class PowerCtx {
    public final EntityRef host;
    public final WorldApi world;
    public final VfxApi vfx;
    public final String brain;
    public final String power;
    public long tick;

    private final Logger log;
    private final Random random = new Random();
    private boolean stopRequested;

    public PowerCtx(EntityRef host, WorldApi world, VfxApi vfx, String brain, String power, Logger log) {
        this.host = host;
        this.world = world;
        this.vfx = vfx;
        this.brain = brain;
        this.power = power;
        this.log = log;
    }

    public Vec vec(double x, double y, double z) {
        return new Vec(x, y, z);
    }

    public int color(Object c) {
        return Colors.parse(dev.jevmod.core.script.ScriptHost.toJava(c));
    }

    public double random() {
        return random.nextDouble();
    }

    public void log(Object msg) {
        log.info("[{}:{}] {}", brain, power, msg);
    }

    /** Sends a chat line to the host (if it is a player). */
    public void say(Object msg) {
        if (host.mc() instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.literal("\u00a7b[" + brain + "]\u00a7r " + msg));
        }
    }

    /** Asks the runtime to switch this (toggle) power off after the current call. */
    public void stop() {
        stopRequested = true;
    }

    public boolean consumeStop() {
        boolean s = stopRequested;
        stopRequested = false;
        return s;
    }
}
