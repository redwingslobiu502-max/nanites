package dev.jevmod.game;

import dev.jevmod.JevMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Installs the example content into {@code config/jevmod} the first time the game runs.
 *
 * <p>Files are only written when missing: existing edits are preserved; deleted examples return on server start.
 * A {@code /jev reload} picks edits up without reinstalling examples.
 */
public final class DefaultContent {
    private static final List<String> FILES = List.of(
            "README.md",
            "POWERS.md",
            "UPDATE2.md",
            "UPDATE3.md",
            "UPDATE035.md",
            "bodies/humanoid.json",
            "brains/jarvis.json",
            "brains/sentry.json",
            "powers/dash.js",
            "powers/dash.json",
            "powers/laser.js",
            "powers/laser.json",
            "powers/lightning.js",
            "powers/lightning.json",
            "powers/shield.js",
            "powers/shield.json",
            "sensors/threat_scan.js",
            "apis/engineering.js",
            "minds/friday.json",
            "minds/jarvis.json",
            "minds/ultron.json",
            "systems/assistant.js",
            "systems/assistant.json",
            "systems/body_factory.js",
            "systems/body_factory.json",
            "systems/energy.js",
            "systems/energy.json",
            "systems/home_defense.js",
            "systems/home_defense.json",
            "systems/java_counter.json",
            "systems/java_api_client.json",
            "systems/java_api_client.js",
            "systems/java_api_rules.json",
            "systems/reactor.js",
            "systems/reactor.json",
            "systems/redstone_rules.json",
            "systems/wireless_switch.js",
            "systems/wireless_switch.json");

    private DefaultContent() {
    }

    public static void install(Path root) {
        int written = 0;
        for (String rel : FILES) {
            Path target = root.resolve(rel);
            if (Files.exists(target)||dev.jevmod.body.LocalContent.isDeleted(root,rel)) {
                continue;
            }
            try (InputStream in = DefaultContent.class.getResourceAsStream("/jevmod-defaults/" + rel)) {
                if (in == null) {
                    JevMod.LOGGER.warn("[jevmod] default content is missing from the jar: {}", rel);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(in, target);
                written++;
            } catch (IOException e) {
                JevMod.LOGGER.warn("[jevmod] could not install {}: {}", rel, e.toString());
            }
        }
        if (written > 0) {
            JevMod.LOGGER.info("[jevmod] installed {} default file(s) into {}", written, root);
        }
    }
}
