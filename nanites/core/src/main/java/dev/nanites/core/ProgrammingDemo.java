package dev.nanites.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline adapter fixture: intents are printed, not applied to Minecraft. */
public final class ProgrammingDemo {
    private ProgrammingDemo() {}
    public static void main(String[] args) throws IOException {
        String source;
        try (InputStream in = ProgrammingDemo.class.getResourceAsStream("/examples/fall.nano")) {
            if (in == null) throw new IOException("Bundled fall.nano missing");
            byte[] data = in.readNBytes(ScriptLanguage.MAX_SOURCE + 1);
            if (data.length > ScriptLanguage.MAX_SOURCE) throw new IOException("Example quota"); source = new String(data, StandardCharsets.UTF_8);
        }
        var policy = new NaniteSession.Policy(Set.of("builders"), Set.of("fall"), Map.of(
                "protect", new RuleEngine.WorldSpec(Set.of("structure"), 0, 100),
                "reconfigure.insulating", new RuleEngine.WorldSpec(Set.of("structure"), 0, 1)), new RuleEngine.Limits(8, 128));
        var session = new NaniteSession("offline.owner", policy,
                List.of(new Ledger.Reservoir("reservoir", "custom.store", Ledger.NANITES_PER_BOX),
                        new Ledger.Reservoir("structure", "custom.insulating", Ledger.NANITES_PER_BOX)),
                Map.of("reservoir", Ledger.NANITES_PER_BOX, "structure", 0L), "examples/fall.nano", source);
        var fall = new RuleEngine.Event("fall", "builders", Map.of("distance", 8L, "speed", -900L));
        var preview = session.preview("offline.owner", 0, "examples/fall.nano", source, null, List.of(fall));
        require(preview.result().committed());
        System.out.println("SCRIPT_PREVIEW=" + preview.snapshot().state() + " INTENTS_ONLY=" + preview.intents());
        require(session.snapshot("offline.owner").ledger().balances().get("structure") == 0);
        session.enqueue("offline.owner", 0, fall);
        var tick = session.tick("offline.owner", 1); require(tick.result().committed());
        System.out.println("SCRIPT_TICK=" + tick);
        String candidate = source.replace("= 1000;", "= 2000;");
        session.stage("offline.owner", 2, "custom-hot.nano", candidate, null);
        session.enqueue("offline.owner", 3, fall); require(session.tick("offline.owner", 4).result().swapped());
        session.rollbackScript("offline.owner", 5, null); require(session.tick("offline.owner", 6).result().swapped());
        require(session.snapshot("offline.owner").ledger().balances().get("structure") == 3000);
        var restored = NaniteSession.restore(session.save("offline.owner"), "offline.owner", policy);
        require(restored.snapshot("offline.owner").equals(session.snapshot("offline.owner")));
        System.out.println("PROGRAMMING_DEMO=PASS; conserved=" + restored.snapshot("offline.owner").ledger().total()
                + "; script rollback kept committed transfers; world effects NOT executed");
    }
    private static void require(boolean value) { if (!value) throw new AssertionError("Demo invariant"); }
}
