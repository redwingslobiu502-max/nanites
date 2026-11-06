package dev.jevmod.net;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/** Registers the VFX payload and fans effects out to nearby players. */
public final class JevNetwork {
    /** Set by the client entrypoint; stays a no-op on dedicated servers so no client class is ever loaded there. */
    public static volatile Consumer<VfxPayload> clientHandler = p -> {
    };

    /** The last effects sent (server side), for /jev status and GameTests. */
    private static final Deque<VfxPayload> RECENT = new ArrayDeque<>();
    private static long sentCount;

    private JevNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(VfxPayload.TYPE, VfxPayload.CODEC, (payload, ctx) -> clientHandler.accept(payload));
    }

    public static void send(ServerLevel level, VfxPayload p) {
        synchronized (RECENT) {
            RECENT.addLast(p);
            while (RECENT.size() > 64) RECENT.removeFirst();
            sentCount++;
        }
        PacketDistributor.sendToPlayersNear(level, null, p.fx(), p.fy(), p.fz(), 160, p);
    }

    public static long sentCount() {
        synchronized (RECENT) {
            return sentCount;
        }
    }

    public static VfxPayload lastMatching(java.util.function.Predicate<VfxPayload> f) {
        synchronized (RECENT) {
            var it = RECENT.descendingIterator();
            while (it.hasNext()) {
                VfxPayload p = it.next();
                if (f.test(p)) return p;
            }
            return null;
        }
    }
}
