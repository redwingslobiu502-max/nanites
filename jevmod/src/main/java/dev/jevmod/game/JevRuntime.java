package dev.jevmod.game;

import dev.jevmod.JevMod;
import dev.jevmod.core.JevConfig;
import dev.jevmod.core.def.BrainDef;
import dev.jevmod.core.def.Defs;
import dev.jevmod.core.script.ScriptHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.mozilla.javascript.Script;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the content (config + definitions + script engine) and the brains attached to entities.
 * Everything here runs on the server thread: events, commands and the tick all come in single-file.
 */
public final class JevRuntime {
    private static final Logger LOG = JevMod.LOGGER;

    private final Path root;
    private final Map<UUID, BrainInstance> brains = new LinkedHashMap<>();
    private final Map<UUID, ChatLog> chats = new LinkedHashMap<>();
    /** Compiled scripts, keyed by file. Cleared with the rest of the content on reload. */
    private final Map<Path, Script> compiled = new LinkedHashMap<>();

    private JevConfig config = JevConfig.defaults();
    private Defs defs;
    private ScriptHost scripts;
    private MinecraftServer server;

    public JevRuntime(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public JevConfig config() {
        return config;
    }

    public Defs defs() {
        return defs;
    }

    public ScriptHost scripts() {
        return scripts;
    }

    /** Compiles a script file once per reload and hands out the cached result. */
    public Script script(Path file) throws IOException {
        Script s = compiled.get(file);
        if (s == null) {
            s = scripts.compile(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString());
            compiled.put(file, s);
        }
        return s;
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------

    public void serverStarted(MinecraftServer server) {
        this.server = server;
        DefaultContent.install(root);
        reload();
    }

    public void serverStopping() {
        for (BrainInstance b : brains.values()) {
            b.stopAll();
        }
        brains.clear();
        chats.clear();
        server = null;
    }

    /** Re-reads jevmod.json, the definitions and every script. Brains stay attached to the same entities. */
    public List<String> reload() {
        List<String> problems = new ArrayList<>();
        try {
            config = JevConfig.load(root.resolve("jevmod.json"));
        } catch (IOException | RuntimeException e) {
            problems.add("jevmod.json: " + e.getMessage());
            config = JevConfig.defaults();
        }
        scripts = new ScriptHost(config.scriptInstructionLimit);
        compiled.clear();
        defs = Defs.load(root);
        problems.addAll(defs.errors);
        if(JevMod.minds()!=null)problems.addAll(JevMod.minds().reload());

        Map<UUID, String> attached = new LinkedHashMap<>();
        for (Map.Entry<UUID, BrainInstance> e : brains.entrySet()) {
            attached.put(e.getKey(), e.getValue().brain().id);
        }
        for (BrainInstance b : brains.values()) {
            b.stopAll();
        }
        brains.clear();
        for (Map.Entry<UUID, String> e : attached.entrySet()) {
            BrainDef def = defs.brains.get(e.getValue());
            Entity entity = findEntity(e.getKey());
            if (def != null && entity != null) {
                brains.put(e.getKey(), new BrainInstance(this, entity, def));
            }
        }
        LOG.info("[jevmod] reloaded: {} brain(s), {} power(s), {} problem(s)", defs.brains.size(), defs.powers.size(), problems.size());
        for (String p : problems) {
            LOG.warn("[jevmod] {}", p);
        }
        return problems;
    }

    private Entity findEntity(UUID id) {
        if (server == null) {
            return null;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(id)) {
                return p;
            }
        }
        for (var level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    public void tick(MinecraftServer server) {
        if (this.server == null) {
            this.server = server;
        }
        DevAutorun.tick(server);
        if (brains.isEmpty()) {
            return;
        }
        List<UUID> dead = new ArrayList<>();
        for (Map.Entry<UUID, BrainInstance> e : brains.entrySet()) {
            BrainInstance b = e.getValue();
            Entity host=findEntity(e.getKey());
            if(host instanceof dev.jevmod.body.GeneratedBody body && body.mindHalted()){b.stopAll();continue;}
            if (!b.valid()) {
                b.stopAll();
                dead.add(e.getKey());
                continue;
            }
            try {
                b.tick();
            } catch (RuntimeException ex) {
                LOG.error("[jevmod] brain '{}' failed a tick", b.brain().id, ex);
                b.noteError("internal error: " + ex);
            }
        }
        for (UUID id : dead) {
            brains.remove(id);
        }
    }

    // ---- attachment ---------------------------------------------------------------------------------------------

    public Map<UUID, BrainInstance> brains() {
        return brains;
    }

    public BrainInstance brain(Entity entity) {
        return brains.get(entity.getUUID());
    }

    /** Attaches {@code def} to {@code entity}, replacing whatever was there. */
    public BrainInstance attach(Entity entity, BrainDef def) {
        BrainInstance old = brains.get(entity.getUUID());
        if (old != null) {
            old.stopAll();
        }
        BrainInstance b = new BrainInstance(this, entity, def);
        brains.put(entity.getUUID(), b);
        return b;
    }

    public boolean detach(Entity entity) {
        BrainInstance old = brains.remove(entity.getUUID());
        if (old == null) {
            return false;
        }
        old.stopAll();
        return true;
    }

    public void onLogin(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            String autorun = System.getProperty("jevmod.devAutorun");
            if (autorun != null && !autorun.isBlank()) {
                DevAutorun.schedule(player, autorun);
            }
        }
    }

    public void onLogout(Entity entity) {
        chats.remove(entity.getUUID());
    }

    // ---- chat ---------------------------------------------------------------------------------------------------

    public void onChat(ServerPlayer player, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        chats.computeIfAbsent(player.getUUID(), k -> new ChatLog()).add(raw, player.level().getGameTime());
        BrainInstance b = brains.get(player.getUUID());
        if (b != null) {
            b.onChat(raw);
        }
    }

    /** Injects a chat line as if the player had typed it (used by {@code /jev say} and the GameTests). */
    public void injectChat(ServerPlayer player, String text) {
        onChat(player, text);
    }

    /** The newest {@code max} chat lines from that player, newest first. */
    public List<ChatLog.Line> chatLines(UUID player, int max) {
        ChatLog log = chats.get(player);
        return log == null ? List.of() : log.newest(max);
    }

    public long gameTime() {
        return server == null ? 0 : server.overworld().getGameTime();
    }

    public MinecraftServer server() {
        return server;
    }
}
