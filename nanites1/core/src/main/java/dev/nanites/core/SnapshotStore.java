package dev.nanites.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Format 1; exact integer checks before constructors; no reflective deserialization. */
public final class SnapshotStore {
    public static final int MAX_BYTES = 1_048_576;
    private final Path root;
    public SnapshotStore(Path root) throws IOException { this.root = SafeData.root(root); }
    public synchronized void write(String slot, NaniteSession.Save save) throws IOException {
        String json = SafeData.GSON.toJson(save);
        // Revalidate serialization as well as the in-memory value before publication.
        decode(json);
        Path existing = SafeData.child(root, slot(slot));
        if (java.nio.file.Files.exists(existing)) {
            NaniteSession.Save previous = read(slot);
            if (!previous.owner().equals(save.owner()) || save.revision() < previous.revision() || save.revision() == previous.revision() && !save.equals(previous))
                throw new IllegalArgumentException("Snapshot owner/stale revision conflict");
        }
        SafeData.atomic(root, slot(slot), json.getBytes(StandardCharsets.UTF_8), MAX_BYTES);
    }
    public synchronized NaniteSession.Save read(String slot) throws IOException {
        return decode(new String(SafeData.bytes(SafeData.child(root, slot(slot)), MAX_BYTES), StandardCharsets.UTF_8));
    }
    private static String slot(String slot) { if (!slot.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid slot"); return slot + ".snapshot.json"; }
    public static NaniteSession.Save decode(String json) {
        JsonObject o = SafeData.object(json, MAX_BYTES);
        if (!o.keySet().equals(Set.of("format", "owner", "revision", "file", "source", "schema", "reservoirs", "balances", "total", "state", "queued", "processed"))) throw new IllegalArgumentException("Snapshot fields");
        List<Ledger.Reservoir> defs = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("reservoirs")) {
            if (defs.size() >= 256) throw new IllegalArgumentException("Reservoir quota"); JsonObject d = e.getAsJsonObject();
            defs.add(new Ledger.Reservoir(SafeData.text(d, "id", 96), SafeData.text(d, "definition", 96), SafeData.integer(d.get("capacity"))));
        }
        List<RuleEngine.Event> events = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("queued")) {
            if (events.size() >= 256) throw new IllegalArgumentException("Queue quota"); JsonObject d = e.getAsJsonObject();
            events.add(new RuleEngine.Event(SafeData.text(d, "type", 96), SafeData.text(d, "group", 96), SafeData.longs(d.getAsJsonObject("values"), 64)));
        }
        return new NaniteSession.Save(Math.toIntExact(SafeData.integer(o.get("format"))), SafeData.text(o, "owner", 96), SafeData.integer(o.get("revision")),
                SafeData.text(o, "file", 256), SafeData.text(o, "source", ScriptLanguage.MAX_SOURCE), SafeData.text(o, "schema", 96),
                defs, SafeData.longs(o.getAsJsonObject("balances"), 256), SafeData.integer(o.get("total")), SafeData.longs(o.getAsJsonObject("state"), 64), events, SafeData.integer(o.get("processed")));
    }
}
