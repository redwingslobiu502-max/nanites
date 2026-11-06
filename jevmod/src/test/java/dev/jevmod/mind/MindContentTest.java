package dev.jevmod.mind;

import dev.jevmod.core.Json;
import dev.jevmod.core.script.ScriptHost;
import dev.jevmod.body.LocalContent;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MindContentTest {
    @Test void profilesAndSystemApiSourcesAreCompleteAndCompile()throws Exception{
        var root=Path.of(getClass().getResource("/jevmod-defaults/README.md").toURI()).getParent();var catalog=MindCatalog.load(root);assertEquals(List.of(),catalog.errors);assertEquals(Set.of("jarvis","friday","ultron"),catalog.minds.keySet());assertTrue(catalog.systems.containsKey("energy"));assertTrue(catalog.systems.containsKey("reactor"));assertTrue(catalog.systems.containsKey("home_defense"));
        var host=new ScriptHost(100000);for(var def:catalog.systems.values())if(def.engine().equals("javascript")){var source=new StringBuilder();for(String api:def.apis())source.append(Files.readString(root.resolve("apis/"+api+".js"))).append('\n');source.append(Files.readString(root.resolve("systems/"+def.script())));var script=host.instantiate(host.compile(source.toString(),def.id()),Map.of());assertTrue(script.has("onTick")||script.has("onEvent"),def.id());}
    }
    @Test void editorAcceptsMindAndSystemsButRejectsEscapingScriptPaths(){
        LocalContent.validate("minds/own.json","{\"systems\":[\"energy\"]}");LocalContent.validate("systems/own.json","{\"engine\":\"java\",\"module\":\"example:own\"}");
        assertThrows(IllegalArgumentException.class,()->SystemDef.parse(Json.parseObject("{\"script\":\"../../outside.js\"}"),"own"));assertThrows(IllegalArgumentException.class,()->MindDef.parse(Json.parseObject("{\"systems\":[\"invalid/path\"]}"),"own"));
    }
    @Test void jsonValuesRemainStructuredAcrossJavaScriptAndJavaServiceBoundary(){
        var object=Json.parseObject("{\"value\":4,\"data\":[1,2]}");assertEquals(object,ScriptHost.toJson(ScriptHost.toJava(object)));var map=Map.of("value",4,"data",List.of(1,2));assertEquals(object,ScriptHost.toJson(ScriptHost.toJava(map)));
    }
}
