package dev.nanites.core;

import java.util.*;

/** Bounded data interpreter, not a Java sandbox. One tick is an atomic batch. */
public final class RuleEngine {
    public static final int MAX_RULES = 64, MAX_ACTIONS_PER_RULE = 32, MAX_FIELDS = 64;
    public record Event(String type, String group, Map<String, Long> values) {
        public Event { type = Ledger.id(type); group = Ledger.id(group); values = Map.copyOf(values);
            if (values.size() > MAX_FIELDS) throw new IllegalArgumentException("Event fields exceeded"); values.keySet().forEach(Ledger::id); }
        public Event(String type, String group) { this(type, group, Map.of()); }
    }
    public sealed interface Action permits Transfer, AddState, WorldAction, ScriptAction {}
    public record WorldAction(String capability, String target, long value) implements Action {
        public WorldAction { capability = Ledger.id(capability); target = Ledger.id(target); }
    }
    public record WorldSpec(Set<String> targets, long minimum, long maximum) {
        public WorldSpec { targets = Set.copyOf(targets); targets.forEach(Ledger::id);
            if (minimum > maximum) throw new IllegalArgumentException("Invalid world range"); }
    }
    public record ScriptAction(ScriptLanguage.Module module, ScriptLanguage.Handler handler) implements Action {
        public ScriptAction { Objects.requireNonNull(module); Objects.requireNonNull(handler); }
    }
    public record Transfer(String from, String to, long quantity) implements Action {
        public Transfer { from = Ledger.id(from); to = Ledger.id(to); Ledger.nonnegative(quantity); }
    }
    public record AddState(String field, long delta) implements Action {
        public AddState { field = Ledger.id(field); }
    }
    public record Rule(String event, String group, List<Action> actions) {
        public Rule {
            event = Ledger.id(event); group = Ledger.id(group); actions = List.copyOf(actions);
            if (actions.size() > MAX_ACTIONS_PER_RULE) throw new IllegalArgumentException("Too many actions");
        }
    }
    public record Schema(String version, Map<String, Long> defaults) {
        public Schema {
            version = Ledger.id(version); defaults = Map.copyOf(defaults);
            if (defaults.size() > MAX_FIELDS) throw new IllegalArgumentException("Too many state fields");
            defaults.forEach((k, v) -> { Ledger.id(k); Ledger.nonnegative(v); });
        }
    }
    public record Program(String id, Schema schema, List<Rule> rules) {
        public Program {
            id = Ledger.id(id); Objects.requireNonNull(schema); rules = List.copyOf(rules);
            if (rules.size() > MAX_RULES) throw new IllegalArgumentException("Too many rules");
        }
    }
    /** Every removed/renamed field must be explicit. New fields use the target defaults. */
    public record Migration(String fromVersion, String toVersion, Map<String, String> renames, Set<String> drops) {
        public Migration {
            fromVersion = Ledger.id(fromVersion); toVersion = Ledger.id(toVersion);
            renames = Map.copyOf(renames); drops = Set.copyOf(drops);
            renames.forEach((k, v) -> { Ledger.id(k); Ledger.id(v); }); drops.forEach(Ledger::id);
        }
    }
    public record Limits(int maxEvents, int maxActions) {
        public Limits {
            if (maxEvents < 1 || maxEvents > 256 || maxActions < 1 || maxActions > 4096)
                throw new IllegalArgumentException("Invalid execution limits");
        }
    }
    public record Result(boolean committed, boolean swapped, String program, String error) {}
    public record Snapshot(String program, Map<String, Long> state, Ledger.Snapshot ledger) {
        public Snapshot { state = Map.copyOf(state); }
    }
    private record Pending(Program program, Migration migration) {}
    private final Ledger ledger;
    private final Set<String> groups, events;
    private final Limits limits;
    private Program active;
    private Map<String, Long> state;
    private Pending pending;
    private final Map<String, WorldSpec> world;
    private List<WorldAction> outbox = List.of();

    public RuleEngine(Ledger ledger, Set<String> groups, Set<String> events, Limits limits, Program initial) {
        this(ledger, groups, events, limits, initial, Map.of());
    }
    public RuleEngine(Ledger ledger, Set<String> groups, Set<String> events, Limits limits, Program initial, Map<String, WorldSpec> world) {
        this.world = Map.copyOf(world); this.world.keySet().forEach(Ledger::id);
        this.ledger = Objects.requireNonNull(ledger); this.groups = Set.copyOf(groups);
        this.events = Set.copyOf(events); this.limits = Objects.requireNonNull(limits);
        this.groups.forEach(Ledger::id); this.events.forEach(Ledger::id);
        validate(initial); active = initial; state = initial.schema().defaults();
    }
    public synchronized Snapshot snapshot() { return new Snapshot(active.id(), state, ledger.snapshot()); }
    public synchronized Program activeProgram() { return active; }
    public synchronized void restoreState(Map<String, Long> saved) {
        if (pending != null || !saved.keySet().equals(active.schema().defaults().keySet())) throw new IllegalArgumentException("Snapshot schema mismatch");
        saved.values().forEach(Ledger::nonnegative); state = Map.copyOf(saved);
    }
    public synchronized List<WorldAction> drainWorldActions() { List<WorldAction> result = outbox; outbox = List.of(); return result; }
    private void validateWorld(WorldAction action) {
        WorldSpec spec = world.get(action.capability());
        if (spec == null || !spec.targets().contains(action.target()) || action.value() < spec.minimum() || action.value() > spec.maximum())
            throw new IllegalArgumentException("Unapproved world action");
    }
    /** Validation at submission; never activates immediately. A second pending candidate is rejected. */
    public synchronized void stage(Program candidate, Migration migration) {
        if (pending != null) throw new IllegalStateException("A candidate is already pending");
        validate(candidate); migrate(candidate.schema(), migration); pending = new Pending(candidate, migration);
    }
    public synchronized Result tick(List<Event> incoming) {
        boolean swapped = pending != null;
        Program candidate = swapped ? pending.program() : active;
        Migration migration = swapped ? pending.migration() : null;
        // A failed tick discards the candidate, keeping the last good program and all state/balances.
        pending = null;
        try {
            List<Event> batch = List.copyOf(incoming);
            if (batch.size() > limits.maxEvents()) throw new IllegalArgumentException("Event budget exceeded");
            Map<String, Long> next = new LinkedHashMap<>(swapped ? migrate(candidate.schema(), migration) : state);
            if (!outbox.isEmpty()) throw new IllegalStateException("World outbox must be acknowledged before tick");
            List<WorldAction> intents = new ArrayList<>();
            List<Ledger.Move> moves = new ArrayList<>();
            int operations = 0;
            ScriptLanguage.Fuel fuel = new ScriptLanguage.Fuel(new ScriptLanguage.Budget(16384));
            for (Event event : batch) {
                if (!events.contains(event.type()) || !groups.contains(event.group()))
                    throw new IllegalArgumentException("Unknown event or group");
                for (Rule rule : candidate.rules()) {
                    if (!rule.event().equals(event.type()) || !rule.group().equals(event.group())) continue;
                    for (Action original : rule.actions()) {
                        List<Action> expanded = original instanceof ScriptAction script ?
                                ScriptLanguage.evaluate(script.module(), script.handler(), next, event.values(), fuel) : List.of(original);
                        for (Action action : expanded) {
                            if (++operations > limits.maxActions()) throw new IllegalArgumentException("Action budget exceeded");
                            if (action instanceof Transfer t) moves.add(new Ledger.Move(t.from(), t.to(), t.quantity()));
                            else if (action instanceof AddState add) {
                                long value = Math.addExact(next.get(add.field()), add.delta());
                                Ledger.nonnegative(value); next.put(add.field(), value);
                            } else if (action instanceof WorldAction intent) { validateWorld(intent); intents.add(intent); }
                            else throw new IllegalArgumentException("Nested scripts forbidden");
                        }
                    }
                }
            }
            Map<String, Long> immutable = Map.copyOf(next); // Build all potentially failing objects BEFORE commit.
            List<WorldAction> immutableIntents = List.copyOf(intents);
            ledger.transact(moves); // Either commits the complete batch, or does not change anything.
            state = immutable; active = candidate; outbox = immutableIntents;
            return new Result(true, swapped, active.id(), "");
        } catch (RuntimeException error) {
            return new Result(false, false, active.id(), error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }
    private void validate(Program program) {
        Objects.requireNonNull(program);
        for (Rule rule : program.rules()) {
            if (!events.contains(rule.event()) || !groups.contains(rule.group()))
                throw new IllegalArgumentException("Unknown rule event or group");
            for (Action action : rule.actions()) {
                if (action instanceof Transfer t) { ledger.reservoir(t.from()); ledger.reservoir(t.to()); }
                else if (action instanceof AddState add && !program.schema().defaults().containsKey(add.field()))
                    throw new IllegalArgumentException("Unknown state field");
                else if (action instanceof WorldAction intent) validateWorld(intent);
                else if (action instanceof ScriptAction script) {
                    if (!script.module().schema().equals(program.schema()) || !script.handler().event().equals(rule.event()) || !script.handler().group().equals(rule.group()))
                        throw new IllegalArgumentException("Script context mismatch");
                    ScriptLanguage.validate(script.module(), script.handler(), ledger, world);
                }
            }
        }
    }
    private Map<String, Long> migrate(Schema target, Migration migration) {
        Schema source = active.schema();
        boolean compatible = source.version().equals(target.version()) && source.defaults().keySet().equals(target.defaults().keySet());
        if (compatible && migration == null) return state; // defaults do not reset existing state
        if (migration == null || !source.version().equals(migration.fromVersion()) || !target.version().equals(migration.toVersion()))
            throw new IllegalArgumentException("Explicit migration required");
        if (!state.keySet().containsAll(migration.renames().keySet()) || !state.keySet().containsAll(migration.drops()))
            throw new IllegalArgumentException("Migration references missing fields");
        Map<String, Long> next = new LinkedHashMap<>(target.defaults()); Set<String> written = new HashSet<>();
        for (var entry : state.entrySet()) {
            String key = entry.getKey();
            if (migration.drops().contains(key)) {
                if (migration.renames().containsKey(key)) throw new IllegalArgumentException("Cannot rename and drop");
                continue;
            }
            String dest = migration.renames().getOrDefault(key, key);
            if (!target.defaults().containsKey(dest) || !written.add(dest))
                throw new IllegalArgumentException("Migration loses or collides state");
            next.put(dest, entry.getValue());
        }
        return Map.copyOf(next);
    }
}
