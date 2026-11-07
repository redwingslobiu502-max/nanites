package dev.nanites.core;

import java.util.*;

/** Aggregate ownership. No per-nanite objects; every mutation is checked and transactional. */
public final class Ledger {
    public static final long NANITES_PER_BOX = 500_000_000_000L;
    public record Reservoir(String id, String definition, long capacity) {
        public Reservoir { id = Ledger.id(id); definition = Ledger.id(definition); Ledger.nonnegative(capacity); }
    }
    public record Snapshot(Map<String, Long> balances, long total) {
        public Snapshot { balances = Map.copyOf(balances); }
    }
    private final Map<String, Reservoir> reservoirs;
    private Map<String, Long> balances;
    private final long conservedTotal;

    public Ledger(Collection<Reservoir> definitions, Map<String, Long> initial) {
        Map<String, Reservoir> defs = new LinkedHashMap<>();
        for (Reservoir r : definitions) if (defs.putIfAbsent(r.id(), r) != null)
            throw new IllegalArgumentException("Duplicate reservoir: " + r.id());
        if (!defs.keySet().equals(initial.keySet())) throw new IllegalArgumentException("Initial accounts must match");
        reservoirs = Map.copyOf(defs);
        balances = new LinkedHashMap<>(initial);
        conservedTotal = validate(balances);
    }
    public static long boxes(long count) { nonnegative(count); return Math.multiplyExact(count, NANITES_PER_BOX); }
    public synchronized Snapshot snapshot() { return new Snapshot(balances, conservedTotal); }
    public boolean contains(String account) { return reservoirs.containsKey(account); }
    public Reservoir reservoir(String account) {
        Reservoir r = reservoirs.get(account);
        if (r == null) throw new IllegalArgumentException("Unknown reservoir: " + account);
        return r;
    }
    public synchronized void transfer(String from, String to, long quantity) {
        transact(List.of(new Move(from, to, quantity)));
    }
    public record Move(String from, String to, long quantity) {
        public Move { from = id(from); to = id(to); nonnegative(quantity); }
    }
    public synchronized void transact(List<Move> moves) {
        Map<String, Long> next = simulate(moves);
        balances = next;
    }
    /** Validation-only simulation for a whole batch, on the current balances. */
    public synchronized Snapshot preview(List<Move> moves) { return new Snapshot(simulate(moves), conservedTotal); }
    private Map<String, Long> simulate(List<Move> moves) {
        Map<String, Long> next = new LinkedHashMap<>(balances);
        for (Move move : List.copyOf(moves)) {
            reservoir(move.from()); reservoir(move.to());
            long source = next.get(move.from());
            if (source < move.quantity()) throw new IllegalArgumentException("Insufficient nanites");
            if (move.from().equals(move.to())) continue;
            long target = Math.addExact(next.get(move.to()), move.quantity());
            if (target > reservoir(move.to()).capacity()) throw new IllegalArgumentException("Capacity exceeded");
            next.put(move.from(), Math.subtractExact(source, move.quantity())); next.put(move.to(), target);
        }
        if (validate(next) != conservedTotal) throw new IllegalStateException("Conservation violated");
        return next;
    }
    private long validate(Map<String, Long> values) {
        long total = 0;
        for (var entry : values.entrySet()) {
            long value = entry.getValue(); nonnegative(value);
            if (value > reservoir(entry.getKey()).capacity()) throw new IllegalArgumentException("Capacity exceeded");
            total = Math.addExact(total, value);
        }
        return total;
    }
    static void nonnegative(long value) { if (value < 0) throw new IllegalArgumentException("Negative value"); }
    static String id(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_.:-]{1,96}")) throw new IllegalArgumentException("Invalid identifier");
        return value;
    }
}
