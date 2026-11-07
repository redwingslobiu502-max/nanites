package dev.nanites.game;

import com.google.gson.*;
import dev.nanites.core.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** All mutable state and world access belong to Host.main/server tick. No scheduler threads. */
public final class AutonomousGoals implements AutoCloseable {
    public interface Host {
        boolean online(UUID owner);
        boolean manualBusy(UUID owner);
        boolean modelAvailable();
        void main(Runnable task);
        JsonObject execute(UUID owner,String tool,JsonObject args) throws Exception;
        CompletableFuture<String> round(UUID owner,String prompt,JsonObject spec,int budget,BooleanSupplier valid,
                                        BiConsumer<String,JsonObject> receipt) throws Exception;
    }
    private record Key(UUID owner,String name) {}
    private static final class Goal {
        final Key key;JsonObject spec;int version=1,failures;long generation=1,cycles,next;
        String state="running",error="",summary="";boolean pending,measured,cycleStalled;
        JsonObject checkpoint,telemetry=new JsonObject();JsonArray history=new JsonArray();
        CompletableFuture<String> future;
        Goal(Key key,JsonObject spec){this.key=key;this.spec=spec;}
    }
    private final Path root;private final Host host;private final MachineAdapters adapters;
    private final LinkedHashMap<Key,Goal> goals=new LinkedHashMap<>();
    private final Map<UUID,Goal> active=new HashMap<>();
    private long tick,rotation;private boolean closed,storageFailed;
    public AutonomousGoals(Path root,Host host,MachineAdapters adapters) throws Exception {
        this.root=root;this.host=host;this.adapters=adapters;Files.createDirectories(root);safe(root);
        try(var files=Files.list(root)){
            for(Path p:files.filter(f->f.getFileName().toString().endsWith(".goal.json")).sorted().limit(129).toList()){
                if(goals.size()>=128)throw new IllegalStateException("128 durable goals maximum");
                safe(p);if(Files.size(p)>262144)throw new IllegalArgumentException("Goal file quota");
                var o=SafeData.object(Files.readString(p,StandardCharsets.UTF_8),262144);
                var key=new Key(UUID.fromString(o.get("owner").getAsString()),id(o.get("name").getAsString()));
                if(!p.getFileName().toString().equals(key.owner+"_"+key.name+".goal.json"))throw new SecurityException("Goal identity mismatch");
                Goal g=new Goal(key,validate(o.getAsJsonObject("spec").toString()));
                g.version=o.get("version").getAsInt();g.generation=o.get("generation").getAsLong()+1;
                g.cycles=o.get("cycles").getAsLong();g.failures=o.get("failures").getAsInt();
                g.state=o.get("state").getAsString();g.error=o.get("error").getAsString();g.summary=o.get("summary").getAsString();
                g.pending=o.get("pending").getAsBoolean();g.measured=o.get("measured").getAsBoolean();g.cycleStalled=o.has("cycleStalled")&&o.get("cycleStalled").getAsBoolean();
                g.telemetry=o.getAsJsonObject("telemetry").deepCopy();g.history=o.getAsJsonArray("history").deepCopy();
                if(g.version<1||g.generation<1||g.cycles<0||g.failures<0||g.history.size()>16||g.summary.length()>2048)throw new IllegalArgumentException("Invalid durable goal counters");
                if(o.has("checkpoint"))g.checkpoint=o.getAsJsonObject("checkpoint").deepCopy();
                if(g.pending){g.state="failed";g.error="Uncertain interrupted action: inspect telemetry and update objective before retry. Never replayed.";}
                else if(!Set.of("paused","failed","done").contains(g.state))g.state="waiting_player";
                g.next=Math.max(20,integer(o,"nextInTicks",0,72000));goals.put(key,g);
            }
        }
    }
    private static void safe(Path p){if(Files.isSymbolicLink(p))throw new SecurityException("Symlink goal storage denied");}
    private static String id(String s){if(!s.matches("[A-Za-z0-9_-]{1,48}"))throw new IllegalArgumentException("Simple name 1..48 required");return s;}
    private static void exact(JsonObject o,String... fields){if(!o.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("Exact fields "+Arrays.toString(fields));}
    private static int integer(JsonObject o,String name,int min,int max){long n=SafeData.integer(o.get(name));if(n<min||n>max)throw new IllegalArgumentException(name+" "+min+".."+max);return (int)n;}
    public static JsonObject validate(String source){
        var s=SafeData.object(source,8192);exact(s,"mode","text","scope","intervalTicks","budget","recipe","monitor");
        if(!Set.of("factory","language").contains(SafeData.text(s,"mode",16)))throw new IllegalArgumentException("mode factory|language");
        if(SafeData.text(s,"text",2048).isBlank())throw new IllegalArgumentException("Goal text required");
        integer(s,"intervalTicks",20,72000);integer(s,"budget",1,24);
        var scope=s.getAsJsonObject("scope");exact(scope,"dimension","min","max","machines");
        if(!SafeData.text(scope,"dimension",240).matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Explicit dimension required");
        for(String end:List.of("min","max")){var p=scope.getAsJsonObject(end);exact(p,"x","y","z");for(String axis:List.of("x","y","z"))integer(p,axis,-30000000,30000000);}
        for(String axis:List.of("x","y","z"))if(scope.getAsJsonObject("min").get(axis).getAsInt()>scope.getAsJsonObject("max").get(axis).getAsInt())throw new IllegalArgumentException("Invalid region");
        var machines=scope.getAsJsonArray("machines");if(machines.size()<1||machines.size()>8)throw new IllegalArgumentException("1..8 loaded scoped machines");
        for(var e:machines){var a=new JsonObject();a.add("target",e.deepCopy());a.addProperty("offset",0);a.addProperty("limit",1);MachineTools.validate("machine_inspect",a);target(scope,e.getAsJsonObject());}
        var recipe=s.getAsJsonObject("recipe");exact(recipe,"project","name");
        String project=SafeData.text(recipe,"project",48),name=SafeData.text(recipe,"name",48);
        if(s.get("mode").getAsString().equals("factory")||!name.isEmpty()){id(project);id(name);}else if(!project.isEmpty())throw new IllegalArgumentException("Empty recipe needs empty project");
        var m=s.getAsJsonObject("monitor");exact(m,"args","path","op","value");MachineTools.validate("machine_inspect",m.getAsJsonObject("args"));target(scope,m.getAsJsonObject("args").getAsJsonObject("target"));
        // Reuse adapter predicate validation, including primitive types and finite numeric thresholds.
        var when=m.deepCopy();when.addProperty("tool","machine_inspect");var step=new JsonObject();step.addProperty("tool","machine_inspect");step.add("args",m.get("args").deepCopy());step.addProperty("repeat",1);step.add("when",when);
        var r=new JsonObject();r.addProperty("version",1);var steps=new JsonArray();steps.add(step);r.add("steps",steps);MachineAdapters.validate(r.toString());return s;
    }
    private static void point(JsonObject scope,String dimension,int x,int y,int z){
        if(!scope.get("dimension").getAsString().equals(dimension))throw new SecurityException("Outside goal dimension");
        int[] p={x,y,z};int i=0;for(String a:List.of("x","y","z")){if(p[i]<scope.getAsJsonObject("min").get(a).getAsInt()||p[i]>scope.getAsJsonObject("max").get(a).getAsInt())throw new SecurityException("Outside goal region");i++;}
    }
    private static void target(JsonObject scope,JsonObject t){
        if(!t.get("type").getAsString().equals("block"))throw new SecurityException("Autonomy restricted to explicit block targets, no player inventory");
        point(scope,t.get("dimension").getAsString(),t.get("x").getAsInt(),t.get("y").getAsInt(),t.get("z").getAsInt());
    }
    public static void scopeTool(JsonObject spec,String tool,JsonObject args){
        var scope=spec.getAsJsonObject("scope");
        for(String k:List.of("target","from","to"))if(args.has(k)&&args.get(k).isJsonObject())target(scope,args.getAsJsonObject(k));
        if(tool.equals("world_observe")){
            int r=integer(args,"radius",0,16);int x=args.get("x").getAsInt(),y=args.get("y").getAsInt(),z=args.get("z").getAsInt();
            point(scope,args.get("dimension").getAsString(),x-r,y-r,z-r);point(scope,args.get("dimension").getAsString(),x+r,y+r,z+r);
        }
        if(tool.equals("scan_machines")){var t=args.getAsJsonObject("target");int r=integer(args,"radius",0,16);point(scope,t.get("dimension").getAsString(),t.get("x").getAsInt()-r,t.get("y").getAsInt()-r,t.get("z").getAsInt()-r);point(scope,t.get("dimension").getAsString(),t.get("x").getAsInt()+r,t.get("y").getAsInt()+r,t.get("z").getAsInt()+r);}
        if(tool.equals("submit_blueprint")){var b=Blueprint.parse(SafeData.text(args,"source",65536),32768);for(var v:b.voxels()){var p=b.absolute(v);point(scope,b.origin().dimension(),p.x(),p.y(),p.z());}}
        if(tool.equals("adapter_create")){var r=MachineAdapters.validate(args.get("source").getAsString());recipeScope(spec,r);}
    }
    private static void recipeScope(JsonObject spec,JsonObject r){
        int work=0;for(var e:r.getAsJsonArray("steps")){var step=e.getAsJsonObject();work+=step.get("repeat").getAsInt();if(work>spec.get("budget").getAsInt())throw new IllegalArgumentException("Recipe iterations exceed objective budget per cycle");scopeTool(spec,step.get("tool").getAsString(),step.getAsJsonObject("args"));if(step.has("when"))scopeTool(spec,"machine_inspect",step.getAsJsonObject("when").getAsJsonObject("args"));
            if(!Set.of("machine_inspect","scan_machines").contains(step.get("tool").getAsString())&&!step.has("when"))throw new IllegalArgumentException("Daemon writes require live when predicate for restart revalidation");}
    }
    private JsonObject data(Goal g,boolean detail){
        var o=new JsonObject();o.addProperty("owner",g.key.owner.toString());o.addProperty("name",g.key.name);o.addProperty("version",g.version);o.addProperty("generation",g.generation);o.addProperty("state",g.state);o.addProperty("cycles",g.cycles);o.addProperty("failures",g.failures);o.addProperty("error",g.error);o.addProperty("summary",g.summary);o.addProperty("pending",g.pending);o.addProperty("measured",g.measured);o.addProperty("cycleStalled",g.cycleStalled);o.addProperty("nextInTicks",Math.max(0,g.next-tick));
        if(detail){o.add("spec",g.spec.deepCopy());o.add("telemetry",g.telemetry.deepCopy());o.add("history",g.history.deepCopy());if(g.checkpoint!=null)o.add("checkpoint",g.checkpoint.deepCopy());}return o;
    }
    /** Bounded public/model view; full pinned recipe and telemetry remain in durable storage. */
    private JsonObject view(Goal g){
        var out=data(g,true);if(out.has("checkpoint")){var cp=out.getAsJsonObject("checkpoint");cp.remove("source");if(cp.has("last")){String last=cp.get("last").toString();if(last.length()>1500){var snippet=new JsonObject();snippet.addProperty("receipt",last.substring(0,1500));snippet.addProperty("truncated",true);cp.add("last",snippet);}}}
        var history=new JsonArray();for(int i=Math.max(0,g.history.size()-4);i<g.history.size();i++){var e=g.history.get(i).getAsJsonObject().deepCopy();String r=e.get("receipt").getAsString();e.addProperty("receipt",r.substring(0,Math.min(512,r.length())));history.add(e);}out.add("history",history);
        var telemetry=new JsonObject();String observed=g.telemetry.toString();telemetry.addProperty("observation",observed.substring(0,Math.min(2000,observed.length())));telemetry.addProperty("truncated",observed.length()>2000);telemetry.addProperty("predicate_met",g.measured);out.add("telemetry",telemetry);return out;
    }
    private Path file(Goal g){return root.resolve(g.key.owner+"_"+g.key.name+".goal.json");}
    private void save(Goal g){
        if(storageFailed)throw new IllegalStateException("Goal storage disabled after error; restart after fixing disk");
        try{
            Path p=file(g),tmp=p.resolveSibling(p.getFileName()+".tmp");safe(root);safe(p);safe(tmp);
            byte[] bytes=data(g,true).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>262144)throw new IllegalArgumentException("Goal persistence quota");
            try(var channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){var b=java.nio.ByteBuffer.wrap(bytes);while(b.hasRemaining())channel.write(b);channel.force(true);}
            Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){storageFailed=true;g.state="failed";g.error="Persistence failure, effects disabled: "+e.getMessage();throw new IllegalStateException(g.error,e);}
    }
    private void record(Goal g,String tool,JsonObject receipt){
        var e=new JsonObject();e.addProperty("cycle",g.cycles);e.addProperty("tick",tick);e.addProperty("version",g.version);e.addProperty("tool",tool);
        String text=receipt.toString();e.addProperty("receipt",text.substring(0,Math.min(1500,text.length())));
        g.history.add(e);while(g.history.size()>16)g.history.remove(0);
        g.summary="cycles="+g.cycles+", lifecycle="+g.state+", measured="+g.measured+", last="+tool+" "+text.substring(0,Math.min(800,text.length()))+", error="+g.error;
    }
    private void invalidate(Goal g){
        g.generation++;var f=g.future;g.future=null;if(f!=null)f.cancel(false);
        if(active.remove(g.key.owner,g))adapters.detachDaemon(g.key.owner);
    }
    public JsonObject command(UUID owner,String tool,JsonObject args) throws Exception {
        MachineTools.validate(tool,args);String op=tool.substring(tool.indexOf('_')+1);
        if((closed||storageFailed)&&!Set.of("list","status").contains(op))throw new IllegalStateException("Autonomy stopped/storage failed");
        if(op.equals("list")){var out=new JsonObject();var a=new JsonArray();for(var g:goals.values())if(g.key.owner.equals(owner))a.add(data(g,false));out.add("goals",a);return out;}
        Key key=new Key(owner,id(args.get("name").getAsString()));Goal g=goals.get(key);
        if(op.equals("create")){
            if(g!=null)throw new IllegalStateException("Goal exists; update current version");
            if(goals.size()>=128||goals.keySet().stream().filter(k->k.owner.equals(owner)).count()>=16)throw new IllegalStateException("16 goals/owner, 128/world maximum");
            var spec=validate(args.get("source").getAsString());validateRecipe(owner,spec);g=new Goal(key,spec);save(g);goals.put(key,g);return view(g);
        }
        if(g==null)throw new IllegalArgumentException("Unknown owner goal");
        if(op.equals("status"))return view(g);
        if(args.get("version").getAsInt()!=g.version)throw new ConcurrentModificationException("Goal version changed");
        if(g.version==Integer.MAX_VALUE)throw new IllegalStateException("Version exhausted");
        JsonObject replacement=null;if(op.equals("update")){replacement=validate(args.get("source").getAsString());validateRecipe(owner,replacement);}
        if(op.equals("resume")&&g.pending)throw new IllegalStateException("Uncertain action; inspect and update before resuming");
        invalidate(g);g.version++;
        switch(op){
            case "update" -> {g.spec=replacement;g.checkpoint=null;g.pending=false;g.failures=0;g.error="";g.state="running";}
            case "pause" -> g.state="paused";
            case "resume" -> {g.state="running";g.failures=0;g.error="";}
            case "stop" -> {g.state="done";g.checkpoint=null;}
            default -> throw new IllegalArgumentException("Unknown goal control");
        }
        g.next=tick;save(g);return view(g);
    }
    private void validateRecipe(UUID owner,JsonObject spec) throws Exception {var r=spec.getAsJsonObject("recipe");if(!r.get("name").getAsString().isEmpty())recipeScope(spec,adapters.recipeSource(owner,r.get("project").getAsString(),r.get("name").getAsString()));}
    public boolean valid(UUID owner,String name,long generation){var g=goals.get(new Key(owner,name));return !closed&&!storageFailed&&g!=null&&g.generation==generation&&!Set.of("paused","done","failed").contains(g.state)&&host.online(owner)&&!host.manualBusy(owner);}
    private int interval(Goal g){return g.spec.get("intervalTicks").getAsInt();}
    private void transition(Goal g,String state){if(!g.state.equals(state)){g.state=state;save(g);}}
    private JsonObject observe(Goal g) throws Exception {
        // Presence/chunk validation is read-only and never force-loads.
        for(var e:g.spec.getAsJsonObject("scope").getAsJsonArray("machines")){var a=new JsonObject();a.add("target",e.deepCopy());a.addProperty("offset",0);a.addProperty("limit",1);host.execute(g.key.owner,"machine_inspect",a);}
        var m=g.spec.getAsJsonObject("monitor");var out=host.execute(g.key.owner,"machine_inspect",m.getAsJsonObject("args").deepCopy());g.telemetry=out.deepCopy();g.measured=predicate(out,m);record(g,"machine_inspect",out);return out;
    }
    public static boolean predicate(JsonElement data,JsonObject m){
        JsonElement v=data;for(String p:m.get("path").getAsString().split("\\.")){if(v==null)return false;if(v.isJsonObject())v=v.getAsJsonObject().get(p);else if(v.isJsonArray()&&p.matches("[0-9]+")){int i=Integer.parseInt(p);v=i<v.getAsJsonArray().size()?v.getAsJsonArray().get(i):null;}else return false;}
        String op=m.get("op").getAsString();JsonElement expected=m.get("value");if(op.equals("exists"))return (v!=null&&!v.isJsonNull())==expected.getAsBoolean();if(v==null||v.isJsonNull())return false;if(op.equals("eq"))return v.equals(expected);
        if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())return false;double a=v.getAsDouble(),b=expected.getAsDouble();return switch(op){case "gt"->a>b;case "gte"->a>=b;case "lt"->a<b;case "lte"->a<=b;default->false;};
    }
    private void failure(Goal g,Throwable error){
        invalidate(g);g.failures++;g.error=String.valueOf(error.getMessage());if(g.error.length()>512)g.error=g.error.substring(0,512);
        if(error instanceof MachineAdapters.WaitingChunk){g.state="waiting_chunks";g.pending=false;}else g.state=g.pending||g.failures>=8?"failed":"retrying";
        g.next=tick+Math.min(72000L,Math.max(interval(g),20L)*(1L<<Math.min(8,g.failures)));record(g,"error",SafeData.object("{\"error\":"+SafeData.GSON.toJson(g.error)+"}",2048));save(g);
    }
    public void tick(){
        if(closed||storageFailed)return;tick++;
        var list=new ArrayList<>(goals.values());Collections.rotate(list,list.isEmpty()?0:(int)(rotation++%list.size()));
        int budget=4;long deadline=System.nanoTime()+2_000_000;
        for(var g:list){
            if(budget<=0||System.nanoTime()>deadline)return;
            if(Set.of("paused","failed","done").contains(g.state))continue;
            if(!host.online(g.key.owner)){if(active.get(g.key.owner)==g)invalidate(g);transition(g,"waiting_player");continue;}
            if(host.manualBusy(g.key.owner)){if(active.get(g.key.owner)==g)invalidate(g);g.next=tick+20;transition(g,"retrying");continue;}
            if(g.future!=null||active.containsKey(g.key.owner)||tick<g.next)continue;
            if(adapters.active(g.key.owner)){g.next=tick+20;transition(g,"retrying");continue;}
            budget--;
            try{
                observe(g);
                var recipe=g.spec.getAsJsonObject("recipe");boolean daemon=!recipe.get("name").getAsString().isEmpty();
                if(daemon&&g.measured&&!g.cycleStalled){g.failures=0;g.error="";}
                if(daemon&&!(g.spec.get("mode").getAsString().equals("language")&&g.failures>0&&host.modelAvailable())){startFactory(g,recipe);continue;}
                if(!host.modelAvailable()){g.next=tick+interval(g);transition(g,"waiting_model");continue;}
                startRound(g);
            }catch(Exception e){try{failure(g,e);}catch(Exception storage){System.err.println(storage.getMessage());return;}}
        }
    }
    private void startFactory(Goal g,JsonObject recipe) throws Exception {
        recipeScope(g.spec,g.checkpoint==null?adapters.recipeSource(g.key.owner,recipe.get("project").getAsString(),recipe.get("name").getAsString()):g.checkpoint.getAsJsonObject("source"));
        long gen=g.generation;if(g.checkpoint==null)g.cycleStalled=false;active.put(g.key.owner,g);transition(g,"running");
        adapters.daemon(g.key.owner,recipe.get("project").getAsString(),recipe.get("name").getAsString(),g.checkpoint,new MachineAdapters.Checkpoints(){
            public void before(UUID owner,JsonObject checkpoint,JsonObject step){
                if(!valid(owner,g.key.name,gen))throw new CancellationException("Objective generation/lifecycle changed");
                if(tick<g.next)throw new MachineAdapters.WaitingChunk("Backoff before next live check");
                scopeTool(g.spec,step.get("tool").getAsString(),step.getAsJsonObject("args"));
                g.checkpoint=checkpoint;g.pending=true;save(g);
            }
            public void after(UUID owner,JsonObject checkpoint){
                if(!valid(owner,g.key.name,gen))return;
                String status=checkpoint.get("status").getAsString();
                if(g.checkpoint!=null){
                    if(checkpoint.get("executed").getAsInt()>g.checkpoint.get("executed").getAsInt()){var last=checkpoint.getAsJsonObject("last");if(last.has("moved")&&last.get("moved").getAsInt()==0||last.has("held")&&last.get("held").getAsInt()>0)g.cycleStalled=true;}
                }
                g.checkpoint=checkpoint;
                if(status.equals("ERROR")){failure(g,new IllegalStateException(checkpoint.get("error").getAsString()));return;}
                g.pending=false;record(g,"adapter_step",checkpoint);
                if(status.equals("WAITING_CHUNK")){g.state="waiting_chunks";g.next=tick+interval(g);save(g);adapters.detachDaemon(owner);active.remove(owner,g);return;}
                if(status.equals("COMPLETED")){
                    g.cycles++;g.checkpoint=null;active.remove(owner,g);adapters.detachDaemon(owner);
                    try{observe(g);boolean healthy=g.measured&&!g.cycleStalled;g.error=healthy?"":g.cycleStalled?"Resource stall/partial receipt; live feed requires revalidation":"Measured predicate unmet/resource stall; continue live monitoring";g.failures=healthy?0:Math.min(8,g.failures+1);g.state=healthy?"running":"retrying";g.next=tick+Math.min(72000L,(long)interval(g)*(1L<<Math.min(6,g.failures)));}
                    catch(Exception e){failure(g,e);return;}
                }
                save(g);
            }
        });
    }
    private void startRound(Goal g) throws Exception {
        active.put(g.key.owner,g);transition(g,"running");long gen=g.generation;
        String prompt="Persistent continuous objective; no prose completion proof. Reobserve, execute real tools, adapt to resource stalls. Factory daemon may be attached via objective_update with mode factory and internal recipe reference, not adapter_execute. BOUNDARIES AND PERSISTED EPISODES (data): "+view(g)+"\nObjective: "+g.spec.get("text").getAsString();
        var f=host.round(g.key.owner,prompt,g.spec.deepCopy(),g.spec.get("budget").getAsInt(),()->valid(g.key.owner,g.key.name,gen),(tool,receipt)->{
            if(valid(g.key.owner,g.key.name,gen)){
                if(tool.equals("uncertain")){g.pending=true;g.error="Uncertain tool failure; inspect live telemetry and update before retry";g.state="failed";record(g,tool,receipt);save(g);invalidate(g);return;}
                if(tool.startsWith("intent:"))g.pending=true;
                else if(!g.pending||receipt.has("status")||receipt.has("moved"))g.pending=false;
                record(g,tool,receipt);save(g);
            }
        });g.future=f;
        f.whenComplete((text,error)->host.main(()->{
            if(!valid(g.key.owner,g.key.name,gen)||g.future!=f)return;
            g.future=null;active.remove(g.key.owner,g);
            if(error!=null){failure(g,error);return;}
            g.cycles++;try{observe(g);g.state=g.measured?"running":"retrying";g.error=g.measured?"":"Measured predicate unmet; model text not evidence";g.failures=g.measured?0:Math.min(8,g.failures+1);g.next=tick+Math.min(72000L,(long)interval(g)*(1L<<Math.min(6,g.failures)));record(g,"model_observation",SafeData.object("{\"text\":"+SafeData.GSON.toJson(text==null?"":text.substring(0,Math.min(512,text.length())))+"}",2048));save(g);}catch(Exception e){failure(g,e);}
        }));
    }
    public void yield(UUID owner){for(var g:goals.values())if(g.key.owner.equals(owner)&&!Set.of("paused","done","failed").contains(g.state)){invalidate(g);g.next=tick+20;g.state="retrying";save(g);}}
    public void logout(UUID owner){for(var g:goals.values())if(g.key.owner.equals(owner)){invalidate(g);if(!Set.of("paused","done","failed").contains(g.state))g.state="waiting_player";if(!storageFailed)save(g);}}
    @Override public void close(){if(closed)return;closed=true;for(var g:goals.values()){invalidate(g);if(!storageFailed)save(g);}active.clear();}
}
