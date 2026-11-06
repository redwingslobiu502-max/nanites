package dev.jevmod.mind;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jevmod.JevMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.List;

/** A bad editable script must not take down the world or unrelated Mind systems. */
@GameTestHolder("jevmod")
@PrefixGameTestTemplate(false)
public final class SandboxGameTests {
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void cyclicMemoryDisablesOnlyTheFailingSystem(GameTestHelper h) throws Exception {
        // Wrapped Minecraft-facing APIs belong in GameTests, not the game-free JUnit classpath.
        var host = new dev.jevmod.core.script.ScriptHost(100_000);
        var vector = new dev.jevmod.api.Vec(1, 2, 3);
        var instance = host.instantiate(host.compile(
                "function direct() { return vector; } function nested() { return {vector: vector}; }",
                "audit-api.js"), java.util.Map.of("vector", vector));
        h.assertTrue(instance.call("direct") == vector, "Wrapped API identity must survive conversion");
        var expected = new JsonObject();
        expected.add("vector", vector.toJson());
        h.assertTrue(expected.equals(dev.jevmod.core.script.ScriptHost.toJson(instance.call("nested"))),
                "Wrapped APIs nested inside script results must retain JSON serialization");
        // systems/ is installed by startup; LocalContent.folder is intentionally limited to asset/history folders.
        var folder = JevMod.CONTENT_DIR.resolve("systems");
        var bad = Files.createTempFile(folder, "audit-cycle-", ".js");
        var good = Files.createTempFile(folder, "audit-healthy-", ".js");
        Files.writeString(bad, "function onStart(ctx) { var x = {}; x.self = x; ctx.memory.set('bad', x); }");
        Files.writeString(good, "function onTick(ctx) { ctx.memory.set('ticks', ctx.memory.get('ticks', 0) + 1); }");
        var catalog = JevMod.minds().catalog();
        catalog.systems.put("audit_cycle_test", new SystemDef("audit_cycle_test", "javascript",
                bad.getFileName().toString(), "", List.of(), 1, new JsonObject(), new JsonArray()));
        catalog.systems.put("audit_healthy_test", new SystemDef("audit_healthy_test", "javascript",
                good.getFileName().toString(), "", List.of(), 1, new JsonObject(), new JsonArray()));
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlock(pos, MindContent.CORE.get().defaultBlockState(), 3);
        var core = (MindCoreEntity) h.getLevel().getBlockEntity(pos);
        core.configure("jarvis");
        core.useSystems(List.of("audit_cycle_test", "audit_healthy_test"));
        h.runAtTickTime(10, () -> {
            h.assertTrue(core.error().contains("cyclic script data"), "Cyclic memory must produce a contained script error: " + core.error());
            h.assertTrue(!core.memory().has("audit_cycle_test"), "Invalid data must never enter persisted memory");
            h.assertTrue(core.memory().getAsJsonObject("audit_healthy_test").get("ticks").getAsInt() > 2,
                    "Unrelated systems and the server must keep ticking");
            core.setEnabled(false);
            catalog.systems.remove("audit_cycle_test");
            catalog.systems.remove("audit_healthy_test");
            try { Files.delete(bad); Files.delete(good); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            h.succeed();
        });
    }
}
