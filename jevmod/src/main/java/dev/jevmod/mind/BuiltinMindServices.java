package dev.jevmod.mind;

import com.google.gson.*;
import dev.jevmod.core.Json;

/** The same operations are callable from JSON rules, JavaScript and Java extensions. */
public final class BuiltinMindServices {
    private BuiltinMindServices(){}
    public static void register(){MindExtensions.registerService("jev:core",(ctx,operation,args)->{
        switch(operation){
            case "berco.begin","berco.cancel","berco.release","berco.status"->{
                var pos=new net.minecraft.core.BlockPos(Json.integer(args,"x",0),Json.integer(args,"y",0),Json.integer(args,"z",0));ctx.core().checkPosition(pos);
                var state=ctx.level().getBlockState(pos);if(state.is(dev.jevmod.body.BodyContent.BERCO.get())&&state.getValue(dev.jevmod.body.BercoBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)pos=pos.below();ctx.core().checkPosition(pos);
                if(!(ctx.level().getBlockEntity(pos) instanceof dev.jevmod.body.BercoEntity berco))throw new IllegalArgumentException("Sem berço em "+pos);
                dev.jevmod.body.GeneratedBody released=null;
                switch(operation){case "berco.begin"->berco.begin(dev.jevmod.body.BodySpec.parse(args.getAsJsonObject("spec")));case "berco.cancel"->berco.cancel();case "berco.release"->{released=berco.releaseBody();if(released!=null&&Json.bool(args,"bind",false))ctx.body.bind(released.getUUID().toString());}}
                var result=new JsonObject();result.addProperty("running",berco.running());result.addProperty("ready",berco.ready());result.addProperty("progress",berco.progress());result.addProperty("elapsed",berco.elapsed());result.add("spec",berco.spec().json());result.addProperty("error",berco.failure());
                if(operation.equals("berco.release")){result.addProperty("released",released!=null);if(released!=null)result.addProperty("body_id",released.getUUID().toString());}return result;
            }
            case "body.last_damage"->{return ctx.core().lastBodyDamage();}
            case "body.info"->{var reference=ctx.body.ref();if(reference==null)return JsonNull.INSTANCE;var data=reference.toJson();data.addProperty("id",reference.uuid());data.addProperty("alive",reference.isAlive());data.addProperty("max_health",reference.maxHealth());if(ctx.core().body() instanceof dev.jevmod.body.GeneratedBody b)data.add("spec",b.spec().json());return data;}
            case "entities.scan"->{return dev.jevmod.core.script.ScriptHost.toJson(dev.jevmod.core.script.ScriptHost.toJava(ctx.entities.scan(Json.num(args,"x",0),Json.num(args,"y",0),Json.num(args,"z",0),Json.num(args,"radius",64),Json.str(args,"filter","all"))));}
            case "memory.set"->ctx.memory.set(Json.str(args,"key",""),dev.jevmod.core.script.ScriptHost.fromJson(args.get("value")));
            case "body.halt"->ctx.body.halt();
            case "body.resume"->ctx.body.resume();
            case "body.move"->ctx.body.move(Json.num(args,"x",0),Json.num(args,"y",0),Json.num(args,"z",0));
            case "redstone.output"->ctx.redstone.output(Json.str(args,"side","north"),Json.integer(args,"power",0));
            case "wireless.send"->{var data=args.has("data")?args.getAsJsonObject("data"):new JsonObject();return new JsonPrimitive(ctx.wireless.send(Json.str(args,"channel","home"),dev.jevmod.core.script.ScriptHost.fromJson(data),Json.num(args,"range",32)));}
            case "block.set"->ctx.blocks.port(Json.str(args,"port","")).setBlock(Json.str(args,"block","minecraft:air"));
            case "block.state"->ctx.blocks.port(Json.str(args,"port","")).setState(Json.str(args,"property",""),Json.str(args,"value",""));
            case "inventory.consume"->{return new JsonPrimitive(ctx.blocks.port(Json.str(args,"port","")).consume(Json.str(args,"item",""),Json.integer(args,"count",1)));}
            case "say"->ctx.say(Json.str(args,"text",""));
            case "emit"->ctx.emit(Json.str(args,"event","custom"),dev.jevmod.core.script.ScriptHost.fromJson(args.has("data")?args.get("data"):new JsonObject()));
            default->throw new IllegalArgumentException("Operação desconhecida: "+operation);
        }
        return JsonNull.INSTANCE;
    });}
}
