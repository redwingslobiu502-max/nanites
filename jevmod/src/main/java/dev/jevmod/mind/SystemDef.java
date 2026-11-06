package dev.jevmod.mind;

import com.google.gson.*;
import dev.jevmod.core.Json;
import java.util.List;

/** JSON chooses an engine; Java addons may register more engines without changing the core. */
public record SystemDef(String id,String engine,String script,String module,List<String> apis,int interval,JsonObject config,JsonArray rules,boolean enabled) {
    public SystemDef(String id,String engine,String script,String module,List<String> apis,int interval,JsonObject config,JsonArray rules){this(id,engine,script,module,apis,interval,config,rules,true);}
    public static SystemDef parse(JsonObject json,String fallback){
        String id=Json.str(json,"id",fallback);MindCatalog.requireId(id);String engine=Json.str(json,"engine","javascript");
        String script=Json.str(json,"script",id+".js");if(engine.equals("javascript")?!script.matches("[a-zA-Z0-9_-]+\\.js"):!script.matches("[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]{1,12}"))throw new IllegalArgumentException("Script de sistema inválido");
        var apis=Json.strings(json,"apis");apis.forEach(MindCatalog::requireId);if(apis.size()>16)throw new IllegalArgumentException("Máximo: 16 APIs");
        int interval=Json.integer(json,"interval_ticks",1);if(interval<1||interval>72000)throw new IllegalArgumentException("Intervalo: 1..72000 ticks");
        var config=json.has("config")?json.getAsJsonObject("config"):new JsonObject();var rules=json.has("rules")?json.getAsJsonArray("rules"):new JsonArray();
        if(rules.size()>64)throw new IllegalArgumentException("Máximo: 64 regras");
        return new SystemDef(id,engine,script,Json.str(json,"module",""),List.copyOf(apis),interval,config.deepCopy(),rules.deepCopy(),Json.bool(json,"enabled",true));
    }
}
