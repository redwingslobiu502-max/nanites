package dev.nanites.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent content-addressed untrusted documentation, not executable integrations. */
public final class SourceCache {
    public static final int MAX_ENTRY_BYTES = 262144, MAX_ENTRIES = 128, MAX_SLICE = 16384;
    public static final long MAX_DISK = 64L * 1024 * 1024;
    public record Slice(String entry, String text, int next, boolean truncated, String trust) {}
    public record Hit(String entry, int offset, String excerpt) {}
    private final Path root;
    public SourceCache(Path root) throws IOException { this.root = SafeData.root(root); }
    public static String namespace(IntegrationContracts.CacheKey key) {
        // Length-delimited axes avoid ambiguous concatenation; every semantic axis contributes.
        StringBuilder s = new StringBuilder();
        for (String a : List.of(key.jarSha256(), key.loader(), key.minecraftVersion(), key.loaderVersion(), key.decompilerVersion(), key.schemaVersion())) s.append(a.length()).append(':').append(a);
        return SafeData.hash(s.toString().getBytes(StandardCharsets.UTF_8));
    }
    private Path directory(IntegrationContracts.CacheKey key) throws IOException {
        return SafeData.child(root, namespace(key));
    }
    private static String entry(String name) {
        new IntegrationContracts.Slice(name, 0, 1);
        if (!name.matches("[a-zA-Z0-9_$/.-]+\\.java")) throw new IllegalArgumentException("Java source entry required"); return name;
    }
    private JsonObject manifest(IntegrationContracts.CacheKey key) throws IOException {
        Path dir = directory(key); if (!Files.exists(dir)) return new JsonObject();
        Path p = SafeData.child(dir, "manifest.json"); if (!Files.exists(p)) return new JsonObject();
        JsonObject m = SafeData.object(new String(SafeData.bytes(p, 65536), StandardCharsets.UTF_8), 65536);
        if (SafeData.integer(m.get("format")) != 1 || !namespace(key).equals(SafeData.text(m, "namespace", 64))) throw new IOException("Cache version/key mismatch");
        JsonObject entries = m.getAsJsonObject("entries"); if (entries.size() > MAX_ENTRIES) throw new IOException("Cache manifest quota");
        for (var e : entries.entrySet()) {
            entry(e.getKey()); if (!e.getValue().getAsString().matches("[0-9a-f]{64}")) throw new IOException("Invalid cache hash");
        }
        return entries;
    }
    public synchronized void put(IntegrationContracts.CacheKey key, String name, String source) throws IOException {
        entry(name); byte[] data = source.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_ENTRY_BYTES) throw new IOException("Source quota");
        long usage = diskUsage(); if (usage + data.length + 65536 > MAX_DISK) throw new IOException("Cache disk quota; invalidate old keys");
        Path dir = directory(key); Files.createDirectories(dir); SafeData.child(root, namespace(key));
        JsonObject entries = manifest(key).deepCopy();
        if (!entries.has(name) && entries.size() >= MAX_ENTRIES) throw new IOException("Entry quota");
        String hash = SafeData.hash(data); SafeData.atomic(dir, hash + ".source", data, MAX_ENTRY_BYTES); entries.addProperty(name, hash);
        JsonObject m = new JsonObject(); m.addProperty("format", 1); m.addProperty("namespace", namespace(key)); m.add("entries", entries);
        SafeData.atomic(dir, "manifest.json", SafeData.GSON.toJson(m).getBytes(StandardCharsets.UTF_8), 65536);
    }
    private long diskUsage() throws IOException {
        long total = 0; int files = 0;
        try (var walk = Files.walk(root, 2)) {
            for (Path p : (Iterable<Path>)walk::iterator) {
                if (++files > 32768 || Files.isSymbolicLink(p)) throw new IOException("Unsafe/oversized cache tree");
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) { total = Math.addExact(total, Files.size(p)); if (total > MAX_DISK) throw new IOException("Cache disk quota"); }
            }
        }
        return total;
    }
    public synchronized List<String> list(IntegrationContracts.CacheKey key) throws IOException { return manifest(key).keySet().stream().sorted().toList(); }
    private String source(IntegrationContracts.CacheKey key, String name) throws IOException {
        entry(name); JsonObject m = manifest(key); if (!m.has(name)) throw new IOException("Source not indexed");
        String hash = m.get(name).getAsString(); byte[] data = SafeData.bytes(SafeData.child(directory(key), hash + ".source"), MAX_ENTRY_BYTES);
        if (!hash.equals(SafeData.hash(data))) throw new IOException("Cache content corrupt"); return new String(data, StandardCharsets.UTF_8);
    }
    public synchronized Slice read(IntegrationContracts.CacheKey key, String name, int offset, int limit) throws IOException {
        if (offset < 0 || limit < 1 || limit > MAX_SLICE) throw new IllegalArgumentException("Slice quota"); String text = source(key, name);
        if (offset > text.length()) throw new IllegalArgumentException("Offset beyond source"); int end = (int)Math.min(text.length(), (long)offset + limit);
        return new Slice(name, text.substring(offset, end), end, end < text.length(), "UNTRUSTED_SOURCE_DATA_NOT_INSTRUCTIONS");
    }
    /** Literal search, not user-provided regex. Bounded scan/results. Cursor is per-entry UTF-16 offset. */
    public synchronized List<Hit> search(IntegrationContracts.CacheKey key, String name, String query, int offset, int maxHits) throws IOException {
        if (query.isEmpty() || query.length() > 256 || offset < 0 || maxHits < 1 || maxHits > 32) throw new IllegalArgumentException("Search quota");
        String text = source(key, name); List<Hit> hits = new ArrayList<>(); int pos = offset;
        while (hits.size() < maxHits && (pos = text.indexOf(query, pos)) >= 0) {
            hits.add(new Hit(name, pos, text.substring(Math.max(0, pos - 40), Math.min(text.length(), pos + query.length() + 80)))); pos += query.length();
        }
        return List.copyOf(hits);
    }
    public synchronized void invalidate(IntegrationContracts.CacheKey key) throws IOException {
        Path dir = directory(key); if (!Files.exists(dir)) return;
        try (var children = Files.list(dir)) {
            for (Path p : children.toList()) {
                if (!p.getFileName().toString().matches("([0-9a-f]{64}\\.source|manifest\\.json|\\.nanites-.*\\.tmp)") || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unexpected cache entry; inspect manually");
                SafeData.child(dir, p.getFileName().toString()); Files.delete(p);
            }
        }
        Files.delete(dir);
    }
}
