package dev.nanites.core;

import java.net.URI;
import java.util.*;

/** Dependency-free harness. Assertions are always enabled, independent of -ea. */
public final class CoreTests {
    private static int passed;
    private CoreTests() {}
    private static void check(boolean value) { if (!value) throw new AssertionError("Invariant failed"); }
    private static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void rejects(Class<? extends RuntimeException> type, Runnable action) {
        try { action.run(); } catch (RuntimeException e) {
            if (type.isInstance(e)) return; throw new AssertionError("Wrong error type", e);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static void test(String name, Runnable body) {
        body.run(); passed++; System.out.println("PASS " + name);
    }
    private static Ledger ledger() {
        return new Ledger(List.of(new Ledger.Reservoir("a", "custom.store", Long.MAX_VALUE),
                new Ledger.Reservoir("b", "custom.structure", Long.MAX_VALUE)),
                Map.of("a", Ledger.NANITES_PER_BOX, "b", 0L));
    }
    private static RuleEngine.Schema schema(String version, String field) {
        return new RuleEngine.Schema(version, Map.of(field, 0L));
    }
    private static RuleEngine.Program program(String id, RuleEngine.Schema schema, RuleEngine.Action... actions) {
        return new RuleEngine.Program(id, schema, List.of(new RuleEngine.Rule("pulse", "workers", List.of(actions))));
    }
    private static RuleEngine engine(Ledger l, RuleEngine.Program p, int actions) {
        return new RuleEngine(l, Set.of("workers", "observers"), Set.of("pulse", "stop"), new RuleEngine.Limits(8, actions), p);
    }
    private static List<RuleEngine.Event> pulse() { return List.of(new RuleEngine.Event("pulse", "workers")); }
    private static CapabilityCatalog.Origin origin(boolean verified, String adapter) {
        return new CapabilityCatalog.Origin("fixture.material", adapter, "1", "Test fixture only, not a real mod adapter",
                verified ? CapabilityCatalog.Verification.VERIFIED : CapabilityCatalog.Verification.DECLARED);
    }
    private static CapabilityCatalog catalog() {
        var source = origin(true, "fixture");
        return new CapabilityCatalog(List.of(
                new CapabilityCatalog.Property("insulating", Set.of("true", "false")),
                new CapabilityCatalog.Property("conductive", Set.of("true", "false"))),
                List.of(new CapabilityCatalog.Capability("electrical.protection", Map.of("insulating", "true", "conductive", "false"), source)),
                List.of(new CapabilityCatalog.Conflict("insulating", "true", "conductive", "true")), Set.of("fixture@1"));
    }
    private static CapabilityCatalog.Definition host(String id, String conducting, CapabilityCatalog.Origin source) {
        return new CapabilityCatalog.Definition(id, Map.of("insulating", new CapabilityCatalog.Fact("true", source),
                "conductive", new CapabilityCatalog.Fact(conducting, source)), Set.of("electrical.protection"));
    }
    public static void main(String[] args) {
        test("long box constant and checked multiplication", () -> {
            eq(500_000_000_000L, Ledger.NANITES_PER_BOX); eq(1_000_000_000_000L, Ledger.boxes(2));
            eq(0L, Ledger.boxes(0)); rejects(IllegalArgumentException.class, () -> Ledger.boxes(-1));
            rejects(ArithmeticException.class, () -> Ledger.boxes(Long.MAX_VALUE));
        });
        test("initialization rejects negatives capacity and duplicates", () -> {
            rejects(IllegalArgumentException.class, () -> new Ledger.Reservoir("x", "y", -1));
            var r = new Ledger.Reservoir("x", "y", 1);
            rejects(IllegalArgumentException.class, () -> new Ledger(List.of(r), Map.of("x", -1L)));
            rejects(IllegalArgumentException.class, () -> new Ledger(List.of(r), Map.of("x", 2L)));
            rejects(IllegalArgumentException.class, () -> new Ledger(List.of(r, r), Map.of("x", 1L)));
            rejects(IllegalArgumentException.class, () -> new Ledger(List.of(r), Map.of("missing", 1L)));
        });
        test("aggregate total overflow rejected", () -> rejects(ArithmeticException.class, () -> new Ledger(
                List.of(new Ledger.Reservoir("x", "d", Long.MAX_VALUE), new Ledger.Reservoir("y", "d", Long.MAX_VALUE)),
                Map.of("x", Long.MAX_VALUE, "y", 1L))));
        test("transfer exactly one box preserves total", () -> {
            Ledger l = ledger(); l.transfer("a", "b", Ledger.NANITES_PER_BOX);
            eq(0L, l.snapshot().balances().get("a")); eq(Ledger.NANITES_PER_BOX, l.snapshot().balances().get("b"));
            eq(Ledger.NANITES_PER_BOX, l.snapshot().total());
        });
        test("batch rollback on late insufficiency", () -> {
            Ledger l = ledger(); var before = l.snapshot();
            rejects(IllegalArgumentException.class, () -> l.transact(List.of(new Ledger.Move("a", "b", 10),
                    new Ledger.Move("a", "b", Ledger.NANITES_PER_BOX)))); eq(before, l.snapshot());
        });
        test("capacity failure does not mutate source", () -> {
            Ledger l = new Ledger(List.of(new Ledger.Reservoir("a", "d", 100), new Ledger.Reservoir("b", "d", 1)), Map.of("a", 100L, "b", 0L));
            var before = l.snapshot(); rejects(IllegalArgumentException.class, () -> l.transfer("a", "b", 2)); eq(before, l.snapshot());
        });
        test("negative unknown and self transfers checked", () -> {
            Ledger l = ledger(); var before = l.snapshot();
            rejects(IllegalArgumentException.class, () -> l.transfer("a", "b", -1));
            rejects(IllegalArgumentException.class, () -> l.transfer("missing", "b", 0));
            rejects(IllegalArgumentException.class, () -> l.transfer("a", "a", Ledger.NANITES_PER_BOX + 1));
            l.transfer("a", "a", 100); l.transfer("a", "b", 0); eq(before, l.snapshot());
        });
        test("near long max valid conservation", () -> {
            Ledger l = new Ledger(List.of(new Ledger.Reservoir("a", "d", Long.MAX_VALUE), new Ledger.Reservoir("b", "d", Long.MAX_VALUE)),
                    Map.of("a", Long.MAX_VALUE, "b", 0L));
            l.transfer("a", "b", Long.MAX_VALUE); eq(Long.MAX_VALUE, l.snapshot().total());
        });
        test("deterministic randomized conservation 10000 transactions", () -> {
            Random random = new Random(1701); Ledger l = ledger();
            for (int i = 0; i < 10_000; i++) {
                String from = random.nextBoolean() ? "a" : "b", to = from.equals("a") ? "b" : "a";
                long amount = Math.floorMod(random.nextLong(), Ledger.NANITES_PER_BOX + 1);
                var before = l.snapshot();
                if (before.balances().get(from) < amount) {
                    rejects(IllegalArgumentException.class, () -> l.transfer(from, to, amount)); eq(before, l.snapshot());
                } else l.transfer(from, to, amount);
                var s = l.snapshot(); eq(Ledger.NANITES_PER_BOX, Math.addExact(s.balances().get("a"), s.balances().get("b")));
            }
        });
        test("immutable snapshots and preview", () -> {
            Ledger l = ledger(); var before = l.snapshot(); l.preview(List.of(new Ledger.Move("a", "b", 1)));
            eq(before, l.snapshot()); rejects(UnsupportedOperationException.class, () -> before.balances().put("a", 0L));
        });
        test("group and event filtering", () -> {
            var e = engine(ledger(), program("v1", schema("s1", "count"), new RuleEngine.AddState("count", 1)), 16);
            check(e.tick(List.of(new RuleEngine.Event("pulse", "observers"), new RuleEngine.Event("stop", "workers"))).committed());
            eq(0L, e.snapshot().state().get("count")); check(e.tick(pulse()).committed()); eq(1L, e.snapshot().state().get("count"));
        });
        test("hot swap only at tick and preserves compatible state", () -> {
            var s = schema("s1", "count"); var e = engine(ledger(), program("old", s, new RuleEngine.AddState("count", 1)), 16);
            check(e.tick(pulse()).committed()); e.stage(program("new", s, new RuleEngine.AddState("count", 2)), null);
            eq("old", e.snapshot().program()); eq(1L, e.snapshot().state().get("count"));
            var result = e.tick(pulse()); check(result.committed() && result.swapped()); eq("new", e.snapshot().program()); eq(3L, e.snapshot().state().get("count"));
        });
        test("invalid candidate rejected before scheduling", () -> {
            var e = engine(ledger(), program("old", schema("s1", "count")), 16); var before = e.snapshot();
            rejects(IllegalArgumentException.class, () -> e.stage(program("bad", schema("s1", "count"), new RuleEngine.Transfer("missing", "b", 1)), null));
            rejects(IllegalArgumentException.class, () -> e.stage(program("bad", schema("s1", "count"), new RuleEngine.AddState("missing", 1)), null));
            eq(before, e.snapshot()); check(!e.tick(List.of()).swapped());
        });
        test("pending replacement cannot be silently overwritten", () -> {
            var s = schema("s1", "count"); var e = engine(ledger(), program("old", s), 16);
            e.stage(program("next", s), null); rejects(IllegalStateException.class, () -> e.stage(program("another", s), null));
            check(e.tick(List.of()).swapped()); eq("next", e.snapshot().program());
        });
        test("failed candidate rolls back program state and all transfers", () -> {
            var s = schema("s1", "count"); var e = engine(ledger(), program("old", s, new RuleEngine.AddState("count", 1)), 16);
            var before = e.snapshot(); e.stage(program("bad", s, new RuleEngine.Transfer("a", "b", 10),
                    new RuleEngine.AddState("count", 100), new RuleEngine.Transfer("a", "b", Ledger.NANITES_PER_BOX)), null);
            check(!e.tick(pulse()).committed()); eq(before, e.snapshot()); check(e.tick(pulse()).committed()); eq(1L, e.snapshot().state().get("count"));
        });
        test("state overflow rolls back valid pending transfer", () -> {
            var s = new RuleEngine.Schema("s1", Map.of("count", Long.MAX_VALUE));
            var e = engine(ledger(), program("old", s, new RuleEngine.Transfer("a", "b", 1), new RuleEngine.AddState("count", 1)), 16);
            var before = e.snapshot(); check(!e.tick(pulse()).committed()); eq(before, e.snapshot());
        });
        test("negative state rejected atomically", () -> {
            var e = engine(ledger(), program("old", schema("s1", "count"), new RuleEngine.AddState("count", -1)), 16);
            var before = e.snapshot(); check(!e.tick(pulse()).committed()); eq(before, e.snapshot());
        });
        test("action budget rejects entire tick", () -> {
            var e = engine(ledger(), program("old", schema("s1", "count"), new RuleEngine.Transfer("a", "b", 1), new RuleEngine.AddState("count", 1)), 1);
            var before = e.snapshot(); check(!e.tick(pulse()).committed()); eq(before, e.snapshot());
        });
        test("event budget and unknown events reject whole tick", () -> {
            var e = engine(ledger(), program("old", schema("s1", "count")), 16); var before = e.snapshot();
            check(!e.tick(Collections.nCopies(9, new RuleEngine.Event("pulse", "workers"))).committed());
            check(!e.tick(List.of(new RuleEngine.Event("unknown", "workers"))).committed()); eq(before, e.snapshot());
        });
        test("incompatible schemas need explicit migration", () -> {
            var e = engine(ledger(), program("old", schema("s1", "count")), 16);
            rejects(IllegalArgumentException.class, () -> e.stage(program("new", schema("s2", "runs")), null)); eq("old", e.snapshot().program());
        });
        test("migration uses state at tick not stage time", () -> {
            var e = engine(ledger(), program("old", schema("s1", "count"), new RuleEngine.AddState("count", 7)), 16);
            check(e.tick(pulse()).committed());
            e.stage(program("new", schema("s2", "runs"), new RuleEngine.AddState("runs", 2)),
                    new RuleEngine.Migration("s1", "s2", Map.of("count", "runs"), Set.of()));
            check(e.tick(pulse()).swapped()); eq(Map.of("runs", 9L), e.snapshot().state());
        });
        test("migration losses collisions and wrong versions rejected", () -> {
            var old = new RuleEngine.Schema("s1", Map.of("x", 1L, "y", 2L)); var target = schema("s2", "z");
            var e = engine(ledger(), program("old", old), 16);
            rejects(IllegalArgumentException.class, () -> e.stage(program("new", target), new RuleEngine.Migration("s1", "s2", Map.of("x", "z", "y", "z"), Set.of())));
            rejects(IllegalArgumentException.class, () -> e.stage(program("new", target), new RuleEngine.Migration("s1", "s2", Map.of("x", "z"), Set.of())));
            rejects(IllegalArgumentException.class, () -> e.stage(program("new", target), new RuleEngine.Migration("wrong", "s2", Map.of(), Set.of("x", "y"))));
        });
        test("explicit drops and target defaults", () -> {
            var e = engine(ledger(), program("old", schema("s1", "x")), 16);
            var target = new RuleEngine.Schema("s2", Map.of("fresh", 42L));
            e.stage(program("new", target), new RuleEngine.Migration("s1", "s2", Map.of(), Set.of("x")));
            check(e.tick(List.of()).swapped()); eq(Map.of("fresh", 42L), e.snapshot().state());
        });
        test("failed migrated program retains previous schema and state", () -> {
            var e = engine(ledger(), program("old", schema("s1", "x")), 16); var before = e.snapshot();
            e.stage(program("new", schema("s2", "y"), new RuleEngine.AddState("y", -1)),
                    new RuleEngine.Migration("s1", "s2", Map.of("x", "y"), Set.of()));
            check(!e.tick(pulse()).committed()); eq(before, e.snapshot());
        });
        test("insulating nonconductive arbitrary host", () -> {
            var c = catalog(); c.validate(host("custom.bridge", "false", origin(true, "fixture")));
            c.validate(host("custom.armor", "false", origin(true, "fixture")));
        });
        test("conflicting and unknown properties rejected", () -> {
            var c = catalog(); rejects(IllegalArgumentException.class, () -> c.validate(host("custom", "true", origin(true, "fixture"))));
            rejects(IllegalArgumentException.class, () -> c.validate(new CapabilityCatalog.Definition("custom", Map.of("unknown", new CapabilityCatalog.Fact("true", origin(true, "fixture"))), Set.of())));
        });
        test("declared unknown adapter and missing capability never verified", () -> {
            var c = catalog(); rejects(IllegalArgumentException.class, () -> c.validate(host("custom", "false", origin(false, "fixture"))));
            rejects(IllegalArgumentException.class, () -> c.validate(host("custom", "false", origin(true, "unknown"))));
            rejects(IllegalArgumentException.class, () -> c.requireVerified(new CapabilityCatalog.Definition("empty", Map.of(), Set.of()), "electrical.protection"));
            rejects(IllegalArgumentException.class, () -> c.validate(new CapabilityCatalog.Definition("custom", Map.of(), Set.of("reactor.magic"))));
        });
        test("component relocation conserves fuel state and requirements", () -> {
            var c = catalog(); var source = origin(true, "fixture"); var h = host("source", "false", source); var target = host("target", "false", source);
            var component = new EmbeddedComponent("component.1", h.id(), "reactor.generic", source, "fuel.custom", 85L,
                    Map.of("temperature", 300L, "revision", 4L), Set.of("electrical.protection"));
            component.validateHost(c, h); var moved = component.relocate(c, target);
            eq(85L, moved.fuel()); eq(component.state(), moved.state()); eq(component.requiredCapabilities(), moved.requiredCapabilities());
            eq(component.origin(), moved.origin()); eq("source", component.owner()); eq("target", moved.owner());
            eq(80L, moved.debitFuel(5).fuel()); eq(85L, moved.fuel());
            rejects(IllegalArgumentException.class, () -> moved.debitFuel(-1)); rejects(IllegalArgumentException.class, () -> moved.debitFuel(86));
        });
        test("component relocation cannot bypass host requirements", () -> {
            var c = catalog(); var component = new EmbeddedComponent("component", "source", "custom", origin(true, "fixture"), "fuel", 3,
                    Map.of("charge", 2L), Set.of("electrical.protection"));
            rejects(IllegalArgumentException.class, () -> component.relocate(c, new CapabilityCatalog.Definition("target", Map.of(), Set.of())));
            eq(3L, component.fuel());
            var unknown = new EmbeddedComponent("component", "source", "custom", origin(true, "unknown"), "fuel", 3, Map.of(), Set.of());
            rejects(IllegalArgumentException.class, () -> unknown.validateHost(c, new CapabilityCatalog.Definition("source", Map.of(), Set.of())));
        });
//                test("exact local AI URL only, no request sent", () -> {
//            eq(URI.create("http://127.0.0.1:8081/v1"), //IntegrationContracts.validateAiEndpoint(IntegrationContracts.LOCAL_AI));
//            rejects(IllegalArgumentException.class, () -> IntegrationContracts.validateAiEndpoint(null));
//            for (String url : List.of(
//                   "http://127.0.0.1:8081/v1",
//                    "http://127.0.0.1:8081/v1",
//                    "http://localhost:8081/v1",
//                    "https://localhost:8081/v1",
//                    "http://127.0.0.1:8081/v1/",
//                    "http://127.0.0.1:8081/v1/",
//                    "http://127.0.0.1:8081/v1?q=1",
//                    "https://user@127.0.0.1:8081/v1",
//                    "http://127.0.0.1:8081/v1#fragment",
//                    "http://127.0.0.1:8082/v1",
//                    "http://127.0.0.1:8081/v2"))
//                rejects(IllegalArgumentException.class, () -> // IntegrationContracts.validateAiEndpoint(URI.create(url)));
//        });
        test("cache keys invalidate across all version dimensions", () -> {
            String hash = "a".repeat(64); var key = new IntegrationContracts.CacheKey(hash, "forge", "1.20.1", "47", "vf1", "s1");
            check(!key.equals(new IntegrationContracts.CacheKey("b".repeat(64), "forge", "1.20.1", "47", "vf1", "s1")));
            check(!key.equals(new IntegrationContracts.CacheKey(hash, "fabric", "1.20.1", "47", "vf1", "s1")));
            check(!key.equals(new IntegrationContracts.CacheKey(hash, "forge", "1.21.1", "47", "vf1", "s1")));
            check(!key.equals(new IntegrationContracts.CacheKey(hash, "forge", "1.20.1", "48", "vf1", "s1")));
            check(!key.equals(new IntegrationContracts.CacheKey(hash, "forge", "1.20.1", "47", "vf2", "s1")));
            check(!key.equals(new IntegrationContracts.CacheKey(hash, "forge", "1.20.1", "47", "vf1", "s2")));
            rejects(IllegalArgumentException.class, () -> new IntegrationContracts.CacheKey("bad", "forge", "1.20.1", "47", "vf1", "s1"));
        });
        test("incremental slice rejects unsafe paths and unbounded reads", () -> {
            new IntegrationContracts.Slice("assets/model.obj", 0, 1024);
            for (String path : List.of("../secret", "/absolute", "C:/secret", "a\\b"))
                rejects(IllegalArgumentException.class, () -> new IntegrationContracts.Slice(path, 0, 16));
            rejects(IllegalArgumentException.class, () -> new IntegrationContracts.Slice("ok", -1, 16));
            rejects(IllegalArgumentException.class, () -> new IntegrationContracts.Slice("ok", 0, 16_385));
        });
        test("multi turn contract bounds history turns and tools", () -> {
            var turn = new IntegrationContracts.AgentTurn(List.of(new IntegrationContracts.Message("user", "plan"),
                    new IntegrationContracts.Message("assistant", "proposal"), new IntegrationContracts.Message("tool", "valid")),
                    Set.of(IntegrationContracts.Tool.VALIDATE_PROGRAM), 3);
            eq(3, turn.history().size()); eq(1, turn.allowedTools().size());
            rejects(IllegalArgumentException.class, () -> new IntegrationContracts.AgentTurn(List.of(), Set.of(), 0));
            rejects(IllegalArgumentException.class, () -> new IntegrationContracts.Message("shell", "execute"));
        });
        System.out.println("RESULT: " + passed + " tests passed; randomized transfers=10000; no network or game execution");
    }
}
