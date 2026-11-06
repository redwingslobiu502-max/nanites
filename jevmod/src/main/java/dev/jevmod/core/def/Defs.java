package dev.jevmod.core.def;

import com.google.gson.JsonObject;
import dev.jevmod.core.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads every brain and power definition from the content folder (by default {@code config/jevmod}):
 * <pre>
 *   brains/*.json        one brain per file
 *   powers/*.json        one power per file, script next to it (powers/laser.json + powers/laser.js)
 *   sensors/*.js         script sensors
 * </pre>
 * Broken files are reported in {@link #errors} and skipped, so one bad file never disables the rest.
 */
public final class Defs {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,47}");

    public final Map<String, BrainDef> brains = new LinkedHashMap<>();
    public final Map<String, PowerDef> powers = new LinkedHashMap<>();
    public final List<String> errors = new ArrayList<>();
    public final Path root;

    private Defs(Path root) {
        this.root = root;
    }

    static void requireId(String id, String what) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new DefinitionException(what + " id '" + id + "' must match [a-z][a-z0-9_]* (max 48 chars)");
        }
        if (id.contains("__")) {
            throw new DefinitionException(what + " id '" + id + "' must not contain '__' (reserved as a separator)");
        }
    }

    public static Defs load(Path root) {
        Defs d = new Defs(root);
        for (Path f : jsonFiles(root.resolve("powers"), d)) {
            try {
                JsonObject o = Json.parseObject(Files.readString(f, StandardCharsets.UTF_8));
                PowerDef p = PowerDef.parse(o, baseName(f), f.getParent());
                if (!Files.isRegularFile(p.scriptPath())) {
                    throw new DefinitionException("power '" + p.id + "': script file not found: " + root.relativize(p.scriptPath()));
                }
                if (d.powers.containsKey(p.id)) {
                    throw new DefinitionException("duplicate power id '" + p.id + "'");
                }
                d.powers.put(p.id, p);
            } catch (Exception e) {
                d.errors.add(root.relativize(f) + ": " + e.getMessage());
            }
        }
        for (Path f : jsonFiles(root.resolve("brains"), d)) {
            try {
                BrainDef b = BrainDef.parse(Json.parseObject(Files.readString(f, StandardCharsets.UTF_8)), baseName(f));
                for (String pid : b.powers) {
                    if (!pid.equals("*") && !d.powers.containsKey(pid)) {
                        d.errors.add(root.relativize(f) + ": brain '" + b.id + "' references unknown power '" + pid + "' (ignored)");
                    }
                }
                if (d.brains.containsKey(b.id)) {
                    throw new DefinitionException("duplicate brain id '" + b.id + "'");
                }
                d.brains.put(b.id, b);
            } catch (Exception e) {
                d.errors.add(root.relativize(f) + ": " + e.getMessage());
            }
        }
        return d;
    }

    /** The powers a brain actually controls, in a stable order. */
    public List<PowerDef> powersOf(BrainDef b) {
        List<PowerDef> out = new ArrayList<>();
        if (b.powers.contains("*")) {
            for (PowerDef p : powers.values()) {
                if (p.ai) out.add(p);
            }
        } else {
            for (String pid : b.powers) {
                PowerDef p = powers.get(pid);
                if (p != null) out.add(p);
            }
        }
        return out;
    }

    private static List<Path> jsonFiles(Path dir, Defs d) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            d.errors.add(dir + ": " + e.getMessage());
            return List.of();
        }
    }

    private static String baseName(Path f) {
        String n = f.getFileName().toString();
        return n.substring(0, n.length() - ".json".length());
    }
}
