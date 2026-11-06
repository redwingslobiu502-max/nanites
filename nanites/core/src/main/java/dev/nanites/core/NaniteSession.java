package dev.nanites.core;

import java.util.*;

/** Server-owned authority boundary. Caller identity must come from the authenticated adapter, never packet fields. */
public final class NaniteSession {
    public record Policy(Set<String> groups, Set<String> events, Map<String, RuleEngine.WorldSpec> world, RuleEngine.Limits limits) {
        public Policy { groups = Set.copyOf(groups); events = Set.copyOf(events); world = Map.copyOf(world); Objects.requireNonNull(limits); }
    }
    public record Save(int format, String owner, long revision, String file, String source, String schema,
                       List<Ledger.Reservoir> reservoirs, Map<String, Long> balances, long total,
                       Map<String, Long> state, List<RuleEngine.Event> queued, long processed) {
        public Save {
            owner = Ledger.id(owner); Ledger.nonnegative(revision); Ledger.nonnegative(processed); Ledger.nonnegative(total);
            if (format != 1 || reservoirs.size() > 256 || queued.size() > 256 || state.size() > 64) throw new IllegalArgumentException("Snapshot limits/version");
            reservoirs = List.copyOf(reservoirs); balances = Map.copyOf(balances); state = Map.copyOf(state); queued = List.copyOf(queued);
            ScriptLanguage.Module m = ScriptLanguage.compile(file, source);
            if (!m.schema().version().equals(schema) || !m.schema().defaults().keySet().equals(state.keySet())) throw new IllegalArgumentException("Snapshot script/schema");
            state.values().forEach(Ledger::nonnegative);
            Ledger l = new Ledger(reservoirs, balances); if (l.snapshot().total() != total) throw new IllegalArgumentException("Snapshot conservation");
        }
    }
    public record Preview(RuleEngine.Result result, RuleEngine.Snapshot snapshot, List<RuleEngine.WorldAction> intents) {
        public Preview { intents = List.copyOf(intents); }
    }
    public record Tick(long revision, RuleEngine.Result result, List<RuleEngine.WorldAction> intents) {
        public Tick { intents = List.copyOf(intents); }
    }
    private final String owner;
    private final Policy policy;
    private final Ledger ledger;
    private RuleEngine engine;
    private ScriptLanguage.Module active, pending, previous;
    private long revision, processed;
    private final List<RuleEngine.Event> queue = new ArrayList<>();
    public NaniteSession(String owner, Policy policy, Collection<Ledger.Reservoir> reservoirs,
                         Map<String, Long> balances, String file, String source) {
        this.owner = Ledger.id(owner); this.policy = Objects.requireNonNull(policy);
        if (reservoirs.size() > 256) throw new IllegalArgumentException("Reservoir quota");
        ledger = new Ledger(reservoirs, balances); active = ScriptLanguage.compile(file, source); engine = makeEngine(ledger, active);
    }
    /** Detached runtime copy: immutable compiled module is reused; no parser or IO on event dispatch. */
    private NaniteSession(NaniteSession source, Map<String, Long> balances, long revision) {
        owner = source.owner; policy = source.policy; active = source.active;
        ledger = new Ledger(source.definitions(), balances); engine = makeEngine(ledger, active);
        engine.restoreState(source.engine.snapshot().state()); this.revision = revision;
        processed = source.processed; queue.addAll(source.queue);
    }
    public synchronized NaniteSession fork(String actor) {
        authorize(actor, revision);
        if (pending != null) throw new IllegalStateException("Pending candidate");
        return new NaniteSession(this, ledger.snapshot().balances(), revision);
    }
    /** Adapter-owned inventory boundary; caller is authenticated, never script/LLM controlled. */
    public synchronized NaniteSession fundedCopy(String actor, long intake, long cost) {
        authorize(actor, revision); Ledger.nonnegative(intake); Ledger.nonnegative(cost);
        if (pending != null) throw new IllegalStateException("Pending candidate");
        var balances = new HashMap<>(ledger.snapshot().balances());
        balances.put("storage", Math.subtractExact(Math.addExact(balances.get("storage"), intake), cost));
        balances.put("spent", Math.addExact(balances.get("spent"), cost));
        return new NaniteSession(this, balances, Math.incrementExact(revision));
    }
    private RuleEngine makeEngine(Ledger l, ScriptLanguage.Module m) { return new RuleEngine(l, policy.groups(), policy.events(), policy.limits(), m.program(), policy.world()); }
    private void authorize(String actor, long expected) {
        if (!owner.equals(actor)) throw new SecurityException("Owner mismatch");
        if (revision != expected) throw new ConcurrentModificationException("Stale revision");
    }
    public synchronized long revision() { return revision; }
    public synchronized boolean handles(String event) { return active.handlers().stream().anyMatch(h -> h.event().equals(event)); }
    public synchronized RuleEngine.Snapshot snapshot(String actor) {
        if (!owner.equals(actor)) throw new SecurityException("Owner mismatch"); return engine.snapshot();
    }
    public record ScriptSlice(String file, String schema, long revision, String text, int next, boolean truncated) {}
    public synchronized ScriptSlice readScript(String actor, int offset, int limit) {
        if (!owner.equals(actor)) throw new SecurityException("Owner mismatch");
        if (offset < 0 || offset > active.source().length() || limit < 1 || limit > 8192) throw new IllegalArgumentException("Script slice quota");
        int end = (int)Math.min(active.source().length(), (long)offset + limit);
        return new ScriptSlice(active.file(), active.schema().version(), revision, active.source().substring(offset, end), end, end < active.source().length());
    }
    public synchronized void enqueue(String actor, long expected, RuleEngine.Event event) {
        authorize(actor, expected);
        if (!policy.events().contains(event.type()) || !policy.groups().contains(event.group()) || queue.size() >= policy.limits().maxEvents()) throw new IllegalArgumentException("Event policy/quota");
        long next = Math.incrementExact(revision); queue.add(event); revision = next;
    }
    public synchronized Preview preview(String actor, long expected, String file, String source, RuleEngine.Migration migration, List<RuleEngine.Event> events) {
        authorize(actor, expected); ScriptLanguage.Module candidate = ScriptLanguage.compile(file, source);
        RuleEngine copy = makeEngine(new Ledger(definitions(), ledger.snapshot().balances()), active); copy.restoreState(engine.snapshot().state());
        copy.stage(candidate.program(), migration); RuleEngine.Result result = copy.tick(events);
        return new Preview(result, copy.snapshot(), copy.drainWorldActions());
    }
    public synchronized void stage(String actor, long expected, String file, String source, RuleEngine.Migration migration) {
        authorize(actor, expected); if (pending != null) throw new IllegalStateException("Pending candidate");
        ScriptLanguage.Module candidate = ScriptLanguage.compile(file, source); long next = Math.incrementExact(revision);
        engine.stage(candidate.program(), migration); pending = candidate; revision = next;
    }
    /** Tick produces validated intents only. Adapter rechecks world revision/ownership and executes them separately. */
    public synchronized Tick tick(String actor, long expected) {
        authorize(actor, expected); long next = Math.incrementExact(revision), nextProcessed = Math.addExact(processed, queue.size());
        RuleEngine.Result result = engine.tick(queue);
        if (result.committed()) { if (result.swapped()) { previous = active; active = pending; } queue.clear(); processed = nextProcessed; }
        pending = null; revision = next;
        return new Tick(revision, result, engine.drainWorldActions());
    }
    /** Re-stage the last script with current state. Never restores committed balances or external world effects. */
    public synchronized void rollbackScript(String actor, long expected, RuleEngine.Migration migration) {
        authorize(actor, expected); if (previous == null) throw new IllegalStateException("No prior script in this session");
        stage(actor, expected, previous.file(), previous.source(), migration);
    }
    public synchronized void discardEvents(String actor, long expected) { authorize(actor, expected); long next = Math.incrementExact(revision); queue.clear(); revision = next; }
    private List<Ledger.Reservoir> definitions() { return ledger.snapshot().balances().keySet().stream().sorted().map(ledger::reservoir).toList(); }
    public synchronized Save save(String actor) {
        if (!owner.equals(actor)) throw new SecurityException("Owner mismatch");
        if (pending != null) throw new IllegalStateException("Save only after activation boundary; pending proposals are not durable");
        RuleEngine.Snapshot s = engine.snapshot();
        return new Save(1, owner, revision, active.file(), active.source(), active.schema().version(), definitions(), s.ledger().balances(), s.ledger().total(), s.state(), queue, processed);
    }
    public static NaniteSession restore(Save save, String authenticatedOwner, Policy policy) {
        if (!save.owner().equals(authenticatedOwner)) throw new SecurityException("Snapshot owner mismatch");
        NaniteSession session = new NaniteSession(save.owner(), policy, save.reservoirs(), save.balances(), save.file(), save.source());
        if (save.queued().size() > policy.limits().maxEvents()) throw new IllegalArgumentException("Saved queue quota");
        for (RuleEngine.Event e : save.queued()) if (!policy.events().contains(e.type()) || !policy.groups().contains(e.group())) throw new IllegalArgumentException("Saved event policy");
        session.engine.restoreState(save.state()); session.revision = save.revision(); session.processed = save.processed(); session.queue.addAll(save.queued()); return session;
    }
}
