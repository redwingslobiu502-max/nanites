package dev.nanites.game;

import com.google.gson.*;
import dev.nanites.core.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-thread registry and bounded executor. Recipes persist, executions are finite and session-local. */
public final class MachineAdapters {
    @FunctionalInterface public interface Backend {JsonObject execute(UUID owner,String tool,JsonObject args);}
    public static final class WaitingChunk extends IllegalStateException {public WaitingChunk(String message){super(message);}}
    private record Key(UUID owner,String project,String name) {}
    private record Recipe(int version,JsonObject source) {}
    private static final class Job {
        final Key key;final Recipe recipe;final boolean preview;int step,repeat,executed,skipped;String state="QUEUED",error="";JsonObject last=new JsonObject();
        Job(Key k,Recipe r,boolean p){key=k;recipe=r;preview=p;}
    }
    private final Path root;private final Backend backend;
    private final Map<Key,Recipe> registry=new HashMap<>();
    private final LinkedHashMap<UUID,Job> jobs=new LinkedHashMap<>();
    public MachineAdapters(Path root,Backend backend){this.root=root;this.backend=backend;}
    private static String id(String s){if(!s.matches("[A-Za-z0-9_-]{1,48}"))throw new IllegalArgumentException("Simple project/name required; default project is default");return s;}
    private Path file(Key k) throws Exception {
        Path dir=root;Files.createDirectories(dir);if(Files.isSymbolicLink(dir))throw new SecurityException("Symlink adapters denied");
        for(String part:List.of(k.owner.toString(),k.project)){dir=dir.resolve(part);Files.createDirectories(dir);if(Files.isSymbolicLink(dir))throw new SecurityException("Symlink adapter directory denied");}
        Path p=dir.resolve(k.name+".adapter.json");if(Files.isSymbolicLink(p))throw new SecurityException("Symlink adapter denied");return p;
    }
    private static void exact(JsonObject o,String... keys){if(!o.keySet().equals(Set.of(keys)))throw new IllegalArgumentException("Expected recipe fields "+Arrays.toString(keys));}
    public static JsonObject validate(String source){
        JsonObject r=SafeData.object(source,65536);exact(r,"version","steps");long v=SafeData.integer(r.get("version"));if(v<1||v>Integer.MAX_VALUE)throw new IllegalArgumentException("Positive version");
        JsonArray steps=r.getAsJsonArray("steps");if(steps.size()<1||steps.size()>32)throw new IllegalArgumentException("1..32 steps");int total=0;
        for(JsonElement e:steps){var s=e.getAsJsonObject();if(s.has("when"))exact(s,"tool","args","repeat","when");else exact(s,"tool","args","repeat");String tool=s.get("tool").getAsString();if(!MachineTools.OPERATIONS.contains(tool)||tool.equals("machine_recover"))throw new IllegalArgumentException("Unknown/unsupported recipe operation: "+tool);MachineTools.validate(tool,s.getAsJsonObject("args"));int repeat=Math.toIntExact(SafeData.integer(s.get("repeat")));if(repeat<1||repeat>256)throw new IllegalArgumentException("repeat 1..256");total+=repeat;
            if(s.has("when")){var w=s.getAsJsonObject("when");exact(w,"tool","args","path","op","value");if(!w.get("tool").getAsString().equals("machine_inspect"))throw new IllegalArgumentException("Predicate must be live machine_inspect");MachineTools.validate("machine_inspect",w.getAsJsonObject("args"));if(!w.get("path").getAsString().matches("[A-Za-z0-9_.]{1,128}"))throw new IllegalArgumentException("Simple JSON dot path");if(!Set.of("eq","gt","gte","lt","lte","exists").contains(w.get("op").getAsString()))throw new IllegalArgumentException("Unknown predicate");if(!w.get("value").isJsonPrimitive())throw new IllegalArgumentException("Primitive predicate value");var value=w.getAsJsonPrimitive("value");String op=w.get("op").getAsString();if(op.equals("exists")&&!value.isBoolean())throw new IllegalArgumentException("exists requires boolean");if(Set.of("gt","gte","lt","lte").contains(op)&&(!value.isNumber()||!Double.isFinite(value.getAsDouble())))throw new IllegalArgumentException("Numeric predicate requires finite number");}
        }
        if(total>4096)throw new IllegalArgumentException("4096 iterations maximum");return r;
    }
    private Recipe replace(Key k,String source,boolean persist) throws Exception {
        var valid=validate(source);int version=valid.get("version").getAsInt();var old=registry.get(k);
        if(old!=null&&version<old.version)throw new IllegalArgumentException("Version regression; last-known-good retained");
        if(old!=null&&version==old.version&&!valid.equals(old.source))throw new IllegalArgumentException("Changed recipe must increase version");
        if(persist){Path p=file(k),tmp=p.resolveSibling(p.getFileName()+".tmp");if(Files.isSymbolicLink(tmp))throw new SecurityException("Symlink temporary file");Files.writeString(tmp,valid.toString(),StandardCharsets.UTF_8);try{Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}}
        var recipe=new Recipe(version,valid.deepCopy());registry.put(k,recipe);return recipe;
    }
    public JsonObject command(UUID owner,String tool,JsonObject args) throws Exception {
        MachineTools.validate(tool,args);String project=id(args.get("project").getAsString());
        Key key=args.has("name")?new Key(owner,project,id(args.get("name").getAsString())):null;
        switch(tool){
            case "adapter_create" -> {var r=replace(key,args.get("source").getAsString(),true);var out=new JsonObject();out.addProperty("status","ACTIVE");out.addProperty("version",r.version);out.addProperty("name",key.name);return out;}
            case "adapter_load" -> {Path p=file(key);if(Files.size(p)>65536)throw new IllegalArgumentException("64KiB recipe quota");var r=replace(key,Files.readString(p,StandardCharsets.UTF_8),false);var out=new JsonObject();out.addProperty("status","ACTIVE");out.addProperty("version",r.version);return out;}
            case "adapter_execute" -> {var r=registry.get(key);if(r==null){Path p=file(key);if(Files.size(p)>65536)throw new IllegalArgumentException("64KiB recipe quota");r=replace(key,Files.readString(p,StandardCharsets.UTF_8),false);}var previous=jobs.get(owner);if(previous!=null&&Set.of("QUEUED","RUNNING","WAITING_CHUNK").contains(previous.state))throw new IllegalStateException("One active adapter job/owner; cancel first");jobs.put(owner,new Job(key,r,args.get("preview").getAsBoolean()));return status(owner,project);}
            case "adapter_status" -> {return status(owner,project);}
            case "adapter_cancel" -> {var j=jobs.get(owner);if(j!=null&&j.key.project.equals(project))j.state="CANCELLED";return status(owner,project);}
            case "adapter_list" -> {var out=new JsonObject();var list=new JsonArray();for(var e:registry.entrySet().stream().filter(e->e.getKey().owner.equals(owner)&&e.getKey().project.equals(project)).sorted(Comparator.comparing(e->e.getKey().name)).toList()){var r=new JsonObject();r.addProperty("name",e.getKey().name);r.addProperty("version",e.getValue().version);list.add(r);}out.add("active",list);var saved=new JsonArray();Path directory=file(new Key(owner,project,"listing")).getParent();try(var files=Files.list(directory)){files.filter(p->p.getFileName().toString().endsWith(".adapter.json")).sorted().limit(128).forEach(p->saved.add(p.getFileName().toString()));}out.add("saved",saved);return out;}
            default -> throw new IllegalArgumentException("Unknown adapter command");
        }
    }
    public JsonObject status(UUID owner,String project){var o=new JsonObject();var j=jobs.get(owner);if(j==null||!j.key.project.equals(project)){o.addProperty("status","IDLE");return o;}o.addProperty("status",j.state);o.addProperty("name",j.key.name);o.addProperty("version",j.recipe.version);o.addProperty("preview",j.preview);o.addProperty("step",j.step);o.addProperty("repeat",j.repeat);o.addProperty("executed",j.executed);o.addProperty("skipped",j.skipped);o.addProperty("error",j.error);o.add("last",j.last.deepCopy());return o;}
    private boolean predicate(UUID owner,JsonObject when){JsonElement v=backend.execute(owner,"machine_inspect",when.getAsJsonObject("args"));for(String p:when.get("path").getAsString().split("\\.")){if(v==null)return false;if(v.isJsonObject())v=v.getAsJsonObject().get(p);else if(v.isJsonArray()&&p.matches("[0-9]+")){int index=Integer.parseInt(p);v=index<v.getAsJsonArray().size()?v.getAsJsonArray().get(index):null;}else return false;}String op=when.get("op").getAsString();if(op.equals("exists"))return (v!=null&&!v.isJsonNull())==when.get("value").getAsBoolean();if(v==null)return false;JsonElement expected=when.get("value");if(op.equals("eq"))return v.equals(expected);double a=v.getAsDouble(),b=expected.getAsDouble();return switch(op){case "gt"->a>b;case "gte"->a>=b;case "lt"->a<b;case "lte"->a<=b;default->false;};}
    public void tick(){
        int budget=8;long deadline=System.nanoTime()+2_000_000;var active=new ArrayList<>(jobs.values());
        Collections.rotate(active,active.isEmpty()?0:rotation++%active.size());
        for(var j:active){
            if(budget<=0||System.nanoTime()>deadline)return;
            if(jobs.get(j.key.owner)!=j||!Set.of("QUEUED","RUNNING","WAITING_CHUNK").contains(j.state))continue;
            budget--;var hook=checkpoints.get(j.key.owner);
            try{
                var steps=j.recipe.source.getAsJsonArray("steps");var s=steps.get(j.step).getAsJsonObject();
                if(hook!=null)hook.before(j.key.owner,checkpoint(j),s.deepCopy());
                j.state="RUNNING";
                if(!s.has("when")||predicate(j.key.owner,s.getAsJsonObject("when"))){var a=s.getAsJsonObject("args").deepCopy();if(j.preview&&a.has("preview"))a.addProperty("preview",true);j.last=backend.execute(j.key.owner,s.get("tool").getAsString(),a);j.executed++;}else j.skipped++;
                j.error="";if(++j.repeat>=s.get("repeat").getAsInt()){j.repeat=0;j.step++;}if(j.step>=steps.size())j.state="COMPLETED";
            }catch(WaitingChunk wait){j.state="WAITING_CHUNK";j.error=wait.getMessage();}
            catch(Exception e){j.state="ERROR";j.error=e.getClass().getSimpleName()+": "+e.getMessage();}
            if(hook!=null)try{hook.after(j.key.owner,checkpoint(j));}catch(Exception failure){j.state="ERROR";j.error="Checkpoint failed: "+failure.getMessage();}
        }
    }
    /** Durable daemon hooks execute on the server thread, before effects and after receipts. */
    public interface Checkpoints {
        void before(UUID owner,JsonObject checkpoint,JsonObject step);
        void after(UUID owner,JsonObject checkpoint);
    }
    private final Map<UUID,Checkpoints> checkpoints=new HashMap<>();
    public JsonObject recipeSource(UUID owner,String project,String name) throws Exception {
        Key k=new Key(owner,id(project),id(name));var r=registry.get(k);
        if(r==null){Path p=file(k);if(Files.size(p)>65536)throw new IllegalArgumentException("64KiB recipe quota");r=replace(k,Files.readString(p,StandardCharsets.UTF_8),false);}
        return r.source.deepCopy();
    }
    public boolean active(UUID owner){var j=jobs.get(owner);return j!=null&&Set.of("QUEUED","RUNNING","WAITING_CHUNK").contains(j.state);}
    private JsonObject checkpoint(Job j){var o=status(j.key.owner,j.key.project);o.addProperty("project",j.key.project);o.add("source",j.recipe.source.deepCopy());return o;}
    public void daemon(UUID owner,String project,String name,JsonObject saved,Checkpoints listener) throws Exception {
        if(active(owner))throw new IllegalStateException("Adapter busy");
        JsonObject source=saved==null?recipeSource(owner,project,name):validate(saved.getAsJsonObject("source").toString());
        Job j=new Job(new Key(owner,id(project),id(name)),new Recipe(source.get("version").getAsInt(),source),false);
        if(saved!=null){j.step=saved.get("step").getAsInt();j.repeat=saved.get("repeat").getAsInt();j.executed=saved.get("executed").getAsInt();j.skipped=saved.get("skipped").getAsInt();
            if(j.step<0||j.step>=source.getAsJsonArray("steps").size()||j.repeat<0||j.repeat>=source.getAsJsonArray("steps").get(j.step).getAsJsonObject().get("repeat").getAsInt())throw new IllegalArgumentException("Invalid durable cursor");}
        jobs.put(owner,j);checkpoints.put(owner,listener);
    }
    public void detachDaemon(UUID owner){checkpoints.remove(owner);jobs.remove(owner);}
    private int rotation;
    public void forget(UUID owner){checkpoints.remove(owner);jobs.remove(owner);}
    public JsonObject snapshot(UUID owner){var out=new JsonObject();var a=new JsonArray();for(var e:registry.entrySet())if(e.getKey().owner.equals(owner)){var x=new JsonObject();x.addProperty("project",e.getKey().project);x.addProperty("name",e.getKey().name);x.addProperty("version",e.getValue().version);a.add(x);}out.add("adapters",a);var j=jobs.get(owner);if(j!=null)out.add("job",status(owner,j.key.project));return out;}
    public static String contract(){return "Recipe source JSON {version:positive integer,steps:[{tool:machine operation,args:exact real tool args,repeat:1..256,when?:{tool:machine_inspect,args:inspect args,path:dot JSON path including array indices,op:eq|gt|gte|lt|lte|exists,value:primitive}}]}; 1..32 steps, <=4096 iterations; no nested adapters/reflection/Java. project default, simple project/name. create validates then persists/activates; load validates before replacing last good; changed source requires increasing version. execute preview=true forces simulation for writes; repeats re-evaluate live predicates, not a virtual simulation. 8 iterations global and 2ms soft tick budget, one iteration per owner per tick. WAITING_CHUNK resumes only after external chunk loading, no force-load. Jobs finite, session-local; persisted recipes reload without restart.";}
}
