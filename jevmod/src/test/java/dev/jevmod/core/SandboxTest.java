package dev.jevmod.core;

import dev.jevmod.core.script.ScriptError;
import dev.jevmod.core.script.ScriptHost;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SandboxTest {
    @Test void cyclicScriptResultsAreRejectedWithoutCrashingTheServer() {
        var host = new ScriptHost(100_000);
        var instance = host.instantiate(host.compile(
                "function objectCycle() { var x = {}; x.self = x; return x; } " +
                "function arrayCycle() { var x = []; x.push(x); return x; }", "cycles.js"), Map.of());
        assertThrows(ScriptError.class, () -> ScriptHost.toJson(instance.call("objectCycle")));
        assertThrows(ScriptError.class, () -> ScriptHost.toJson(instance.call("arrayCycle")));
    }

    @Test void deeplyNestedScriptResultsAreRejectedWithoutStackOverflow() {
        var host = new ScriptHost(1_000_000);
        var instance = host.instantiate(host.compile(
                "function run() { var x = {}; for (var i = 0; i < 2000; i++) x = {next: x}; return x; }",
                "depth.js"), Map.of());
        assertThrows(ScriptError.class, () -> ScriptHost.toJson(instance.call("run")));
    }

    @Test void cyclicJavaApiDataIsRejectedWithoutStackOverflow() {
        var value = new java.util.LinkedHashMap<String, Object>();
        value.put("self", value);
        assertThrows(ScriptError.class, () -> ScriptHost.toJson(value));
    }

    @Test void conversionPreservesAliasesAndSnapshotsLiveScriptObjects() {
        var host = new ScriptHost(100_000);
        var instance = host.instantiate(host.compile(
                "var data = {value: 7}; function read() { return {a: data, b: data, values: [true, null, 'ok']}; } " +
                "function change() { data.value = 9; }", "snapshot.js"), Map.of());
        var result = instance.call("read");
        assertFalse(result instanceof org.mozilla.javascript.Scriptable);
        instance.call("change");
        assertEquals(Json.parseObject("{\"a\":{\"value\":7},\"b\":{\"value\":7},\"values\":[true,null,\"ok\"]}"), ScriptHost.toJson(result));
    }

    @Test void cyclicJsonAndJavaArgumentsAreRejected() {
        var json = new com.google.gson.JsonObject();
        json.add("self", json);
        assertThrows(ScriptError.class, () -> ScriptHost.fromJson(json));
        assertThrows(ScriptError.class, () -> ScriptHost.toJson(json));
        var host = new ScriptHost(100_000);
        var instance = host.instantiate(host.compile("function run(x) { return x; }", "args.js"), Map.of());
        var value = new java.util.ArrayList<Object>();
        value.add(value);
        assertThrows(ScriptError.class, () -> instance.call("run", value));
        assertThrows(ScriptError.class, () -> instance.set("value", value));
    }

    @Test void apiObjectsKeepTheirJsonContractInsidePlainContainers() {
        dev.jevmod.core.script.JsonSerializableApi value = () -> Json.parseObject("{\"x\":1}");
        var expected = Json.parseObject("{\"value\":{\"x\":1}}");
        assertEquals(expected, ScriptHost.toJson(ScriptHost.toJava(Map.of("value", value))));
    }

    @Test void sparseArraysCannotAllocateUnboundedConversionOutput() {
        var host = new ScriptHost(100_000);
        var instance = host.instantiate(host.compile("function run() { return new Array(1000000000); }", "sparse.js"), Map.of());
        assertThrows(ScriptError.class, () -> instance.call("run"));
    }

    @Test void runawayScriptCannotFreezeTheServer() {
        var host = new ScriptHost(20_000);
        var instance = host.instantiate(host.compile("function run() { while (true) {} }", "loop.js"), Map.of());
        assertThrows(ScriptError.class, () -> instance.call("run"));
        assertThrows(ScriptError.class, () -> host.instantiate(host.compile("while (true) {}", "init-loop.js"), Map.of()));
    }

    @Test void parameterChangesReachBothGlobalAndHookArguments() {
        var host = new ScriptHost(100_000);
        var instance = host.instantiate(host.compile("function read(p) { return [params.color, p.color]; }", "params.js"),
                Map.of("params", Map.of("color", "red")));
        instance.set("params", Map.of("color", "purple"));
        assertEquals(java.util.List.of("purple", "purple"), instance.call("read", Map.of("color", "purple")));
    }

    @Test void javaPackagesAreUnavailable() {
        var host = new ScriptHost(100_000);
        var instance = host.instantiate(host.compile("function check() { return [typeof java, typeof Packages]; }", "isolation.js"), Map.of());
        assertEquals(java.util.List.of("undefined", "undefined"), instance.call("check"));
        assertFalse(ScriptHost.visibleToScripts("java.lang.Runtime"));
        assertFalse(ScriptHost.visibleToScripts("java.lang.Class"));
    }
}
