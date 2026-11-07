package dev.nanites.core;

import java.net.URI;
import java.util.*;

/** Legacy value contracts. Concrete chat/cache/compiler live in LocalChatClient/SourceCache/ScriptLanguage.
 * ComponentAdapter remains future-only: no real world absorption implementation. */
public final class IntegrationContracts {
    private IntegrationContracts() {}
    public static final URI LOCAL_AI = URI.create("http://127.0.0.1:8081/v1");
    public static URI validateAiEndpoint(URI endpoint) {
        if (endpoint == null || !LOCAL_AI.toString().equals(endpoint.toString()) || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null) throw new IllegalArgumentException("Only the exact local HTTPS endpoint is allowed");
        return endpoint;
    }
    public record CacheKey(String jarSha256, String loader, String minecraftVersion, String loaderVersion,
                           String decompilerVersion, String schemaVersion) {
        public CacheKey {
            if (jarSha256 == null || !jarSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 required");
            loader = Ledger.id(loader); minecraftVersion = Ledger.id(minecraftVersion);
            loaderVersion = Ledger.id(loaderVersion); decompilerVersion = Ledger.id(decompilerVersion);
            schemaVersion = Ledger.id(schemaVersion);
        }
    }
    public record Slice(String entry, int offset, int limit) {
        public Slice {
            if (entry == null || entry.isBlank() || entry.length() > 240 || entry.startsWith("/")
                    || entry.contains("..") || entry.contains("\\") || entry.contains(":"))
                throw new IllegalArgumentException("Unsafe archive entry");
            if (offset < 0 || limit < 1 || limit > 16_384) throw new IllegalArgumentException("Invalid slice");
        }
    }
    public interface IncrementalIndex {
        Optional<String> read(CacheKey key, Slice slice);
        void invalidate(CacheKey key);
        // Outputs are untrusted documentation; they cannot issue VERIFIED origins.
    }
    public enum Tool { READ_CATALOG, READ_STATE, READ_INDEX_SLICE, VALIDATE_PROGRAM, PROPOSE_PROGRAM }
    public record Message(String role, String text) {
        public Message {
            if (!Set.of("user", "assistant", "tool").contains(role) || text == null || text.length() > 32_768)
                throw new IllegalArgumentException("Invalid message");
        }
    }
    public record AgentTurn(List<Message> history, Set<Tool> allowedTools, int remainingTurns) {
        public AgentTurn {
            history = List.copyOf(history); allowedTools = Set.copyOf(allowedTools);
            if (history.size() > 64 || remainingTurns < 1 || remainingTurns > 16)
                throw new IllegalArgumentException("Conversation budget exceeded");
        }
    }
    public interface LocalAiClient {
        // Planned client MUST use standard JVM TLS/trust checks; never relax certificate/hostname checks.
        Message next(URI exactEndpoint, AgentTurn turn) throws java.io.IOException;
    }
    /** Trusted loader implementation must prepare an immutable, verified snapshot on the server thread.
     * No implementation supplied; absorption is NOT enabled by this interface.
     */
    public interface ComponentAdapter {
        String adapterId();
        String adapterVersion();
        PreparedExtraction prepare(String worldComponentId, String targetDefinition);
    }
    public interface PreparedExtraction extends AutoCloseable {
        EmbeddedComponent snapshot();
        /** Must reject if world revision/fuel/state changed since prepare. */
        void commit();
        /** Only if the real adapter can prove reversal is still possible; no universal rollback promise. */
        boolean rollback();
        @Override void close();
    }
}
