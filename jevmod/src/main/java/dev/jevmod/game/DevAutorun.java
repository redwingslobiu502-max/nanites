package dev.jevmod.game;

import dev.jevmod.JevMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Dev-only convenience for the {@code demo} run config: after a player joins, run a command for them a couple of
 * seconds later (the client is not ready for chat output the instant the join event fires).
 */
final class DevAutorun {
    private static final List<Pending> PENDING = new ArrayList<>();

    private static final class Pending {
        final UUID player;
        final String command;
        int ticksLeft;

        Pending(UUID player, String command, int ticksLeft) {
            this.player = player;
            this.command = command;
            this.ticksLeft = ticksLeft;
        }
    }

    private DevAutorun() {
    }

    static void schedule(ServerPlayer player, String command) {
        PENDING.add(new Pending(player.getUUID(), command, 40));
    }

    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (--p.ticksLeft > 0) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(p.player);
            if (player == null) {
                continue;
            }
            JevMod.LOGGER.info("[jevmod] dev autorun: {}", p.command);
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), p.command);
        }
    }
}
