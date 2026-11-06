package dev.jevmod;

import com.mojang.logging.LogUtils;
import dev.jevmod.client.JevClient;
import dev.jevmod.command.JevCommands;
import dev.jevmod.game.JevRuntime;
import dev.jevmod.net.JevNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * JevMod - a neural sandbox. Everything the mod does is data: brains, powers and sensors live as files in
 * {@code config/jevmod}, so a {@code /jev reload} (or a file edit) is the whole content pipeline.
 *
 * <p>Nothing here touches game classes: this class only wires events to {@link JevRuntime}.
 */
@Mod(JevMod.MODID)
public class JevMod {
    public static final String MODID = "jevmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** {@code config/jevmod} - brains/, powers/, sensors/ and jevmod.json. */
    public static final Path CONTENT_DIR = FMLPaths.CONFIGDIR.get().resolve(MODID);

    private static JevRuntime runtime;
    private static dev.jevmod.mind.MindRuntime minds;
    public static dev.jevmod.mind.MindRuntime minds(){return minds;}

    public JevMod(IEventBus modBus, ModContainer container) {
        runtime = new JevRuntime(CONTENT_DIR);
        minds=new dev.jevmod.mind.MindRuntime(CONTENT_DIR);
        dev.jevmod.mind.MindContent.init();
        dev.jevmod.mind.BuiltinMindServices.register();
        dev.jevmod.body.BodyContent.register(modBus);
        modBus.addListener(this::onRegisterPayloads);
        NeoForge.EVENT_BUS.register(this);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Inside a guard so a dedicated server never loads a client class.
            JevClient.init();
            dev.jevmod.client.WorkbenchClient.init(modBus);
        }
        LOGGER.info("[jevmod] ready; content folder {}", CONTENT_DIR);
    }

    /** The runtime, or null before the mod constructor has run (it always has by the time events fire). */
    public static JevRuntime runtime() {
        return runtime;
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        JevNetwork.register(event);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        runtime.serverStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        runtime.serverStopping();
        minds.close();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        runtime.tick(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        JevCommands.register(event.getDispatcher(), runtime);
        dev.jevmod.command.MindCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        runtime.onChat(event.getPlayer(), event.getRawText());
        minds.onChat(event.getPlayer(),event.getRawText());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        runtime.onLogin(event.getEntity());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        runtime.onLogout(event.getEntity());
    }
}
