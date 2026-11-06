package dev.jevmod.mind;

import com.google.gson.*;
import dev.jevmod.api.mind.MindContext;
import dev.jevmod.core.Json;
import java.util.Objects;

/** JSON-only systems: tick/event conditions and extensible service operations. */
public final class RuleModule implements MindModule {
    private final SystemDef definition;
    public RuleModule(SystemDef definition){this.definition=definition;}
    @Override public void start(MindContext context){for(String channel:Json.strings(definition.config(),"channels"))context.wireless.listen(channel);}
    @Override public void tick(MindContext context)throws Exception {run(context,"tick",new JsonObject());}
    @Override public void event(MindContext context,String event,JsonObject payload)throws Exception {run(context,event,payload);}
    private void run(MindContext context,String event,JsonObject payload)throws Exception {
        for(var element:definition.rules()){
            var rule=element.getAsJsonObject();var when=rule.has("when")?rule.getAsJsonObject("when"):new JsonObject();
            if(!Json.str(when,"event","tick").equals(event))continue;
            int every=Json.integer(when,"every_ticks",1);if(every<1)throw new IllegalArgumentException("every_ticks precisa ser >=1");if(event.equals("tick")&&context.tick()%every!=0)continue;
            if(when.has("channel")&&!Json.str(when,"channel","").equals(Json.str(payload,"channel","")))continue;
            if(when.has("redstone_min")&&context.redstone.input()<Json.integer(when,"redstone_min",0))continue;
            if(when.has("redstone_max")&&context.redstone.input()>Json.integer(when,"redstone_max",15))continue;
            if(when.has("state")){var condition=when.getAsJsonObject("state");Object value=context.memory.get(Json.str(condition,"key",""),null);if(condition.has("equals")&&!Objects.equals(dev.jevmod.core.script.ScriptHost.toJson(value),condition.get("equals")))continue;if(condition.has("lte")&&(!(value instanceof Number n)||n.doubleValue()>condition.get("lte").getAsDouble()))continue;}
            for(var action:rule.getAsJsonArray("actions")){var object=action.getAsJsonObject();Object result=context.call(Json.str(object,"service","jev:core"),Json.str(object,"op",""),dev.jevmod.core.script.ScriptHost.fromJson(object.has("args")?object.get("args"):new JsonObject()));if(object.has("save_as"))context.memory.set(object.get("save_as").getAsString(),result);}
        }
    }
}
