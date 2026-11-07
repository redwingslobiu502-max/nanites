package dev.nanites.core;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Bounded strict JSON using Gson's tokenizer; controlled paths and fail-closed atomic publication. */
public final class SafeData {
    private SafeData() {}
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static JsonObject object(String json, int maxBytes) {return object(json,maxBytes,16384);}
    /** Explicit structural budget for large voxel lists; default callers retain their existing bound. */
    public static JsonObject object(String json, int maxBytes,int maxNodes) {
        if(maxNodes<1||maxNodes>8_000_000)throw new IllegalArgumentException("JSON node budget");
        if (json.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw new IllegalArgumentException("JSON quota");
        try (JsonReader r = new JsonReader(new StringReader(json))) {
            r.setLenient(false); JsonElement result = read(r, 0, new int[]{maxNodes});
            if (r.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT || !result.isJsonObject()) throw new IllegalArgumentException("JSON object required");
            return result.getAsJsonObject();
        } catch (IOException | IllegalStateException e) { throw new IllegalArgumentException("Malformed JSON", e); }
    }
    private static JsonElement read(JsonReader r, int depth, int[] nodes) throws IOException {
        if (depth > 32 || --nodes[0] < 0) throw new IllegalArgumentException("JSON structure quota");
        return switch (r.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject o = new JsonObject(); r.beginObject();
                while (r.hasNext()) { String key = r.nextName(); if (key.length() > 256 || o.has(key)) throw new IllegalArgumentException("Duplicate/large JSON key"); o.add(key, read(r, depth + 1, nodes)); }
                r.endObject(); yield o;
            }
            case BEGIN_ARRAY -> { JsonArray a = new JsonArray(); r.beginArray(); while (r.hasNext()) a.add(read(r, depth + 1, nodes)); r.endArray(); yield a; }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> { r.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Unexpected JSON token");
        };
    }
    public static String text(JsonObject o, String key, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("String required: " + key);
        String s = e.getAsString(); if (s.length() > max) throw new IllegalArgumentException("Text quota: " + key); return s;
    }
    public static long integer(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Integer required");
        return e.getAsBigDecimal().longValueExact();
    }
    public static Map<String, Long> longs(JsonObject o, int max) {
        if (o.size() > max) throw new IllegalArgumentException("Map quota"); Map<String, Long> result = new LinkedHashMap<>();
        o.entrySet().forEach(e -> result.put(Ledger.id(e.getKey()), integer(e.getValue()))); return Map.copyOf(result);
    }
    public static Path root(Path path) throws IOException {
        Files.createDirectories(path); Path real = path.toRealPath();
        if (!real.equals(path.toAbsolutePath().normalize())) throw new IOException("Root must not be a link"); return real;
    }
    public static Path child(Path root, String relative) throws IOException {
        if (relative.isEmpty() || relative.length() > 512 || relative.contains("\\") || relative.contains(":") || relative.startsWith("/")) throw new IOException("Unsafe path");
        for (String part : relative.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Traversal denied");
        Path dest = root.resolve(relative).normalize();
        if (!dest.startsWith(root)) throw new IOException("Path escaped root");
        Path p = root;
        for (Path part : root.relativize(dest)) {
            p = p.resolve(part);
            if (Files.isSymbolicLink(p) || Files.exists(p, LinkOption.NOFOLLOW_LINKS) && !p.toRealPath().equals(p)) throw new IOException("Link denied");
        }
        return dest;
    }
    public static byte[] bytes(Path p, int max) throws IOException {
        try (InputStream in = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS)) {
            byte[] data = in.readNBytes(max + 1); if (data.length > max) throw new IOException("File quota"); return data;
        }
    }
    public static void atomic(Path root, String name, byte[] bytes, int max) throws IOException {
        if (bytes.length > max) throw new IOException("Write quota"); Path dest = child(root, name);
        Files.createDirectories(dest.getParent()); child(root, name);
        Path temp = Files.createTempFile(dest.getParent(), ".nanites-", ".tmp");
        try {
            try (FileChannel c = FileChannel.open(temp, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer b = ByteBuffer.wrap(bytes); while (b.hasRemaining()) c.write(b); c.force(true);
            }
            child(root, name); Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    public static String hash(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String hashFile(Path path, long max) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); long count = 0;
            try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) >= 0) { count = Math.addExact(count, n); if (count > max) throw new IOException("JAR quota"); digest.update(b, 0, n); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
