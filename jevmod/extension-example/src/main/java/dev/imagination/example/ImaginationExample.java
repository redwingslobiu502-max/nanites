package dev.imagination.example;

import com.google.gson.JsonPrimitive;
import dev.jevmod.api.mind.MindContext;
import dev.jevmod.mind.*;
import net.neoforged.fml.common.Mod;

/** A separate mod, not a special case in JevMod. Replace this with your own Java API and systems. */
@Mod("imagination_example")
public final class ImaginationExample {
    public ImaginationExample(){
        MindExtensions.registerService("imagination_example:math",(context,operation,args)->{
            if(!operation.equals("double"))throw new IllegalArgumentException("Operação: double");
            return new JsonPrimitive(args.get("value").getAsDouble()*2);
        });
        MindExtensions.registerModule("imagination_example:counter",definition->new MindModule(){
            @Override public void tick(MindContext context)throws Exception {
                double previous=((Number)context.memory.get("count",0)).doubleValue();
                // Same extension service can be called from Java, JavaScript or JSON rules.
                var args=new com.google.gson.JsonObject();args.addProperty("value",previous+1);
                var result=MindExtensions.call("imagination_example:math",context,"double",args);
                context.memory.set("count",previous+1);context.memory.set("doubled",result);
                context.redstone.output("east",(int)(previous+1)%16);
                // Full native API is available to Java systems: context.level(), context.core().
            }
        });
    }
}
