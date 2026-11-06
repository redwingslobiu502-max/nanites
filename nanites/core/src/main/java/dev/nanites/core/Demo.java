package dev.nanites.core;

import java.util.*;

public final class Demo {
    private Demo() {}
    public static void main(String[] args) {
        Ledger ledger = new Ledger(List.of(
                new Ledger.Reservoir("supply", "storage.custom", Long.MAX_VALUE),
                new Ledger.Reservoir("bridge", "structure.custom", Ledger.NANITES_PER_BOX)),
                Map.of("supply", Ledger.NANITES_PER_BOX, "bridge", 0L));
        RuleEngine.Schema schema = new RuleEngine.Schema("v1", Map.of("activations", 0L));
        RuleEngine.Program initial = new RuleEngine.Program("idle", schema, List.of());
        RuleEngine engine = new RuleEngine(ledger, Set.of("builders"), Set.of("assemble"),
                new RuleEngine.Limits(16, 128), initial);
        RuleEngine.Program replacement = new RuleEngine.Program("build.bridge", schema, List.of(
                new RuleEngine.Rule("assemble", "builders", List.of(
                        new RuleEngine.Transfer("supply", "bridge", Ledger.NANITES_PER_BOX),
                        new RuleEngine.AddState("activations", 1)))));
        engine.stage(replacement, null);
        System.out.println("Before tick: " + engine.snapshot());
        System.out.println("Tick: " + engine.tick(List.of(new RuleEngine.Event("assemble", "builders"))));
        System.out.println("After tick: " + engine.snapshot());
        System.out.println("Aggregate simulation only; no energy generation or Minecraft entities.");
    }
}
