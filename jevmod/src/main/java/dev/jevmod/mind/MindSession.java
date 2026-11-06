package dev.jevmod.mind;

import dev.jevmod.api.mind.MindContext;
import dev.jevmod.body.LocalContent;
import dev.jevmod.core.script.ScriptHost;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.*;

/** Each system gets its own scope/state. A failing system is disabled without stopping unrelated systems. */
public final class MindSession {
    private record Running(SystemDef definition,MindContext context,MindModule module){}
    private final MindCoreEntity core;
    private final List<Running> modules=new ArrayList<>();
    private int previousSignal=-1;
    public MindSession(MindCoreEntity core,MindRuntime runtime)throws Exception {
        this.core=core;
        if(!runtime.catalog().minds.containsKey(core.profile()))throw new IllegalArgumentException("Mente ausente: "+core.profile());
        for(String id:core.systems())try{
            var definition=runtime.catalog().systems.get(id);if(definition==null)throw new IllegalArgumentException("Sistema ausente: "+id);
            if(!definition.enabled())continue;
            var context=new MindContext(core,definition);MindModule module;
            if(definition.engine().equals("javascript")){
                StringBuilder source=new StringBuilder();var files=new LocalContent(runtime.root());
                for(String api:definition.apis())source.append(Files.readString(files.path("apis/"+api+".js"))).append('\n');
                source.append(Files.readString(files.path("systems/"+definition.script())));
                var script=runtime.scripts().instantiate(runtime.scripts().compile(source.toString(),"systems/"+definition.script()),Map.of("ctx",context,"config",definition.config()));
                module=new MindModule(){@Override public void start(MindContext c){script.call("onStart",c,definition.config());}@Override public void tick(MindContext c){script.call("onTick",c,definition.config());}@Override public void event(MindContext c,String type,JsonObject payload){script.call("onEvent",c,type,payload,definition.config());}@Override public void stop(MindContext c){script.call("onStop",c,definition.config());}};
            }else if(definition.engine().equals("rules"))module=new RuleModule(definition);
            else module=MindExtensions.create(definition);
            var running=new Running(definition,context,module);try{module.start(context);modules.add(running);}catch(Exception e){try{context.resetBudget();module.stop(context);}catch(Exception ignored){}throw e;}
        }catch(Exception e){core.fail(id+": "+e.getMessage());}
    }
    public void tick(){
        int signal=core.getLevel().getBestNeighborSignal(core.getBlockPos());
        if(signal!=previousSignal){var data=new JsonObject();data.addProperty("previous",previousSignal);data.addProperty("power",signal);previousSignal=signal;event(new MindRuntime.Event("redstone",data));}
        for(var running:List.copyOf(modules))if(core.getLevel().getGameTime()%running.definition.interval()==0)invoke(running,()->running.module.tick(running.context));
    }
    public void event(MindRuntime.Event event){for(var running:List.copyOf(modules))invoke(running,()->running.module.event(running.context,event.type(),event.payload()));}
    @FunctionalInterface private interface Action {void run()throws Exception;}
    private void invoke(Running running,Action action){running.context.resetBudget();try{action.run();}catch(Exception e){modules.remove(running);try{running.context.resetBudget();running.module.stop(running.context);}catch(Exception ignored){}core.fail(running.definition.id()+": "+e.getMessage());}}
    public void close(){for(var running:List.copyOf(modules)){running.context.resetBudget();try{running.module.stop(running.context);}catch(Exception e){core.fail(running.definition.id()+": "+e.getMessage());}}modules.clear();}
}
