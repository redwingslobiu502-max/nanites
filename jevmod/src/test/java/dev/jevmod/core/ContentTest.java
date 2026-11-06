package dev.jevmod.core;

import dev.jevmod.core.def.Defs;
import dev.jevmod.core.jev.Answer;
import dev.jevmod.core.jev.QuestionPlan;
import dev.jevmod.core.script.ScriptHost;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ContentTest {
    private Path root() throws Exception {
        return Path.of(getClass().getResource("/jevmod-defaults/README.md").toURI()).getParent();
    }

    @Test void bundledBrainsAndScriptsAreComplete() throws Exception {
        var defs = Defs.load(root());
        assertEquals(List.of(), defs.errors);
        assertEquals(2, defs.brains.size());
        assertEquals(4, defs.powers.size());
        var host = new ScriptHost(100_000);
        for (var power : defs.powers.values()) {
            var script = host.instantiate(host.compile(Files.readString(power.scriptPath()), power.script), Map.of());
            assertTrue(script.has(power.kind == dev.jevmod.core.def.PowerDef.Kind.TOGGLE ? "onTick" : "run"));
        }
        for (var brain : defs.brains.values()) {
            for (var sensor : brain.sensors) {
                if (!sensor.type.equals("script")) continue;
                Path path = root().resolve("sensors").resolve(sensor.config.get("file").getAsString());
                assertTrue(host.instantiate(host.compile(Files.readString(path), path.toString()), Map.of()).has("sense"));
            }
        }
    }

    @Test void uncertainAndMissingAnswersPreserveLiveState() throws Exception {
        var defs = Defs.load(root());
        var brain = defs.brains.get("jarvis");
        var laser = defs.powers.get("laser");
        Function<String, QuestionPlan.PowerStatus> live = id -> new QuestionPlan.PowerStatus(true,
                Map.of("color", "purple", "range", 24.0, "damage", 3.0, "width", 0.15));
        var plan = QuestionPlan.build(brain, List.of(laser), live);
        var decision = plan.decide(brain, List.of(laser), Map.of("laser__active", Answer.noul(0.5),
                "laser__color", Answer.choice("blue", Map.of(), 0.1)), live).getFirst();
        assertNull(decision.turnOn());
        assertEquals("purple", decision.params().get("color"));
        assertEquals(24.0, decision.params().get("range"));
        assertEquals(Boolean.FALSE, plan.decide(brain, List.of(laser),
                Map.of("laser__active", Answer.noul(0.1)), live).getFirst().turnOn());
    }

    @Test void offensiveScriptsDoNotDamagePeacefulTargets() throws Exception {
        String mock = """
                var hits = 0, hostile = false, lightningVisual = false;
                var point = {scale: function(n) { return this; }};
                var target = {isHostile: function() {return hostile;}, damage: function(n) {hits++;}};
                var ctx = {tick: 0, host: {eyePos: function(){return point;}, look: function(){return point;}},
                  world: {raycast: function(){return {entity: target, pos: point};}, lightning: function(p, visual){lightningVisual = visual;}},
                  vfx: {beam: function(){}, ring: function(){}, stop: function(){}}};
                function exercise(p, h) { hostile = h; hits = 0;
                  if (typeof onTick === 'function') onTick(ctx, p); else run(ctx, p);
                  return hits; }
                function visualOnly() {return lightningVisual;}
                """;
        var defs = Defs.load(root());
        for (String id : List.of("laser", "lightning")) {
            var power = defs.powers.get(id);
            var host = new ScriptHost(100_000);
            var script = host.instantiate(host.compile(mock + Files.readString(power.scriptPath()), id + ".js"), Map.of());
            assertEquals(0.0, script.call("exercise", power.defaults(), false));
            assertEquals(1.0, script.call("exercise", power.defaults(), true));
            if (id.equals("lightning")) assertEquals(true, script.call("visualOnly"));
        }
    }
}
