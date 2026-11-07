package dev.nanites.game;

import com.google.gson.*;
import dev.nanites.core.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Real queue + service + tool protocol with deterministic fake worlds/transport, not an in-game test. */
public final class ConstructionTests {
    private static int passed;
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ORIGIN="\"origin\":{\"dimension\":\"minecraft:overworld\",\"x\":10,\"y\":64,\"z\":20}";
    private static final String PLAN="{"+ORIGIN+",\"voxels\":[{\"x\":0,\"y\":0,\"z\":0,\"block\":\"minecraft:stone\"},{\"x\":1,\"y\":2,\"z\":-1,\"block\":\"test:metal[axis=x]\"},{\"x\":2,\"y\":0,\"z\":0,\"block\":\"minecraft:glass\"}]}";
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;}
    static void denied(Runnable task,String label){try{task.run();throw new AssertionError(label);}catch(RuntimeException expected){passed++;}}
    static JsonObject json(String source){return JsonParser.parseString(source).getAsJsonObject();}
    static final class World implements ConstructionQueue.World {
        final Thread main=Thread.currentThread();final Map<Blueprint.Pos,String> blocks=new HashMap<>();boolean loaded=true,fail=false;int writes,reads;
        void thread(){check(Thread.currentThread()==main,"world is server-thread confined");}
        public Object resolve(String state){thread();if(state.contains("missing")||state.contains("axis=q"))throw new IllegalArgumentException("invalid registry/state");return state;}
        public boolean valid(Blueprint.Pos p){thread();return p.y()>=-64&&p.y()<320;}
        public boolean loaded(Blueprint.Pos p){thread();return loaded;}
        public String observe(Blueprint.Pos p){thread();reads++;return blocks.getOrDefault(p,"minecraft:air");}
        public String describe(Object state){thread();return (String)state;}
        public boolean place(Blueprint.Pos p,Object state){thread();writes++;if(fail)return false;blocks.put(p,(String)state);return true;}
    }
    static void geometry(){
        var b=Blueprint.parse(PLAN,100);check(b.voxels().size()==3,"mixed arbitrary list");check(b.absolute(b.voxels().get(1)).equals(new Blueprint.Pos(11,66,19)),"explicit relative origin with negative offset");
        var cuboid=Blueprint.parse("{"+ORIGIN+",\"cuboids\":[{\"from\":{\"x\":1,\"y\":1,\"z\":1},\"to\":{\"x\":0,\"y\":0,\"z\":0},\"block\":\"minecraft:stone\"}]}",100);check(cuboid.voxels().size()==8,"inclusive reversed cuboid");
        var sphere=Blueprint.parse("{"+ORIGIN+",\"spheres\":[{\"center\":{\"x\":0,\"y\":0,\"z\":0},\"radius\":1,\"block\":\"minecraft:glass\"}]}",100);check(sphere.voxels().size()==7,"integer solid sphere");
        var line=Blueprint.parse("{"+ORIGIN+",\"lines\":[{\"from\":{\"x\":0,\"y\":0,\"z\":0},\"to\":{\"x\":3,\"y\":2,\"z\":-1},\"block\":\"minecraft:stone\"}]}",100);check(line.voxels().size()==4&&line.voxels().get(3).relative().equals(new Blueprint.Pos(3,2,-1)),"DDA line endpoint");
        denied(()->Blueprint.parse(PLAN,2),"size budget");denied(()->Blueprint.parse(PLAN.replace("\"x\":10","\"x\":1.5"),100),"fractional origin");denied(()->Blueprint.parse(PLAN.replace("\"x\":10","\"x\":2147483647"),100),"absolute overflow");denied(()->Blueprint.parse("{}",100),"missing origin");denied(()->Blueprint.parse("{"+ORIGIN+"}",100),"empty plan");denied(()->Blueprint.parse(PLAN.replace("minecraft:stone","stone"),100),"namespace required");denied(()->Blueprint.parse(PLAN.replace("minecraft:stone","minecraft:stone{foo:1}"),100),"NBT unsupported");denied(()->Blueprint.parse(PLAN.replace("\"x\":10","\"x\":10,\"x\":11"),100),"duplicate JSON keys");
        JsonObject overlap=json(PLAN);overlap.getAsJsonArray("voxels").add(json("{\"x\":0,\"y\":0,\"z\":0,\"block\":\"minecraft:glass\"}"));var mixed=Blueprint.parse(overlap.toString(),100);check(mixed.voxels().size()==3&&mixed.voxels().get(0).block().equals("minecraft:glass"),"overlap last wins stable order");
        denied(()->Blueprint.parse("{"+ORIGIN+",\"cuboids\":[{\"from\":{\"x\":-2147483648,\"y\":0,\"z\":0},\"to\":{\"x\":2147483647,\"y\":0,\"z\":0},\"block\":\"minecraft:stone\"}]}",100),"huge geometry rejects without looping");
        JsonObject large=json("{"+ORIGIN+",\"voxels\":[]}");for(int i=0;i<4000;i++)large.getAsJsonArray("voxels").add(json("{\"x\":"+i+",\"y\":0,\"z\":0,\"block\":\"minecraft:stone\"}"));check(Blueprint.parse(large.toString(),5000).voxels().size()==4000,"large list exceeds old default JSON node budget");
    }
    static void queue()throws Exception {
        World w=new World();var q=new ConstructionQueue(1,100,TimeUnit.SECONDS.toNanos(1));q.submit(OWNER,PLAN,o->w);check(w.writes==0,"submit only queues");q.tick();check(w.writes==1&&q.status(OWNER).get("processed").getAsInt()==1,"incremental one work/tick");
        w.blocks.put(new Blueprint.Pos(10,64,20),"minecraft:glass");check(q.status(OWNER).get("observed").getAsString().equals("minecraft:glass"),"status rereads live world not receipt snapshot");
        long rev=q.revision(OWNER);denied(()->q.submit(OWNER,PLAN.replace("minecraft:glass","missing:block"),o->w),"resolve before replacement");check(q.revision(OWNER)==rev&&q.status(OWNER).get("processed").getAsInt()==1,"last known good job preserved");denied(()->q.submit(OWNER,PLAN.replace("axis=x","axis=q"),o->w),"invalid blockstate rejected");denied(()->q.submit(OWNER,PLAN.replace("\"y\":64","\"y\":400"),o->w),"invalid world height");
        q.cancel(OWNER);q.tick();check(w.writes==1&&q.status(OWNER).get("status").getAsString().equals("CANCELLED"),"cancel leaves placed effects");check(q.revision(OWNER)>rev,"cancel revision advances");
        q.submit(OWNER,PLAN,o->w);q.tick();q.submit(OWNER,PLAN.replace("minecraft:glass","minecraft:stone"),o->w);q.tick();q.tick();q.tick();check(q.status(OWNER).get("status").getAsString().equals("COMPLETED"),"replacement completes only new plan");check(w.blocks.get(new Blueprint.Pos(12,64,20)).equals("minecraft:stone"),"new material actual fake host effect");check(q.status(OWNER).get("already_matched").getAsInt()>0,"matched blocks count");
        w.loaded=false;q.submit(OWNER,PLAN,o->w);int before=w.writes;q.tick();check(w.writes==before&&q.status(OWNER).get("status").getAsString().equals("WAITING_CHUNK"),"does not load chunks");w.loaded=true;q.tick();check(q.status(OWNER).get("processed").getAsInt()==1,"resumes loaded chunk");
        w.fail=true;q.submit(OWNER,PLAN.replace("minecraft:stone","minecraft:dirt"),o->w);q.tick();check(q.status(OWNER).get("status").getAsString().equals("FAILED")&&q.status(OWNER).get("processed").getAsInt()==0,"failed setBlock has observed failure and no false progress");
        var executor=Executors.newSingleThreadExecutor();try{check(executor.submit(()->{try{q.tick();return false;}catch(IllegalStateException e){return true;}}).get(),"worker cannot mutate queue/world");}finally{executor.shutdownNow();}
        World fair=new World();var global=new ConstructionQueue(2,100,TimeUnit.SECONDS.toNanos(1));UUID other=UUID.randomUUID();global.submit(OWNER,PLAN,o->fair);global.submit(other,PLAN,o->fair);global.tick();check(global.status(OWNER).get("processed").getAsInt()==1&&global.status(other).get("processed").getAsInt()==1,"global fair per-tick budget");
    }
    static final class Host implements GameService.Host {
        final World world=new World();final Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();final List<String> messages=new ArrayList<>();
        public void main(Runnable r){tasks.add(r);}public void message(UUID id,String text){messages.add(text);}public void particles(UUID id,String shape,int radius){throw new AssertionError("construction must not use particles");}public boolean online(UUID id){return true;}public List<String> mods(){return List.of("test@1");}public String loader(){return "forge";}public boolean runtimeAvailable(UUID id){return true;}
        public ConstructionQueue.World constructionWorld(UUID id,Blueprint.Origin o){world.thread();return world;}
        public JsonObject worldObserve(UUID id,JsonObject args){world.thread();JsonObject o=new JsonObject();o.addProperty("block",world.observe(new Blueprint.Pos(10,64,20)));return o;}
        public JsonObject blockCatalog(String query,int offset,int limit){world.thread();return json("{\"blocks\":[\"test:metal\"]}");}
        void drain(){Runnable r;while((r=tasks.poll())!=null)r.run();}
    }
    static NaniteSession detached(){return new NaniteSession(OWNER.toString(),GameService.POLICY,List.of(new Ledger.Reservoir("storage","generic",100)),Map.of("storage",0L),"old.nano","program old version v1; state hits = 0;");}
    static void service(Path root)throws Exception {
        Host h=new Host();try(var s=new GameService(root.resolve("world"),root.resolve("config"),h)){
            s.login(OWNER);var gateway=s.constructionGateway(OWNER);JsonObject args=new JsonObject();args.addProperty("source",PLAN);var f=gateway.execute("submit_blueprint",args);check(!f.isDone()&&h.world.writes==0,"async gateway marshals");h.drain();check(f.join().get("status").getAsString().equals("QUEUED"),"real queued receipt");s.serverTick();check(h.world.writes>0,"server global tick actual execution");
            var old=s.constructionGateway(OWNER);var late=old.execute("submit_blueprint",args);s.command(OWNER,"build cancel");h.drain();check(late.isCompletedExceptionally(),"late generation cannot overwrite manual cancellation");
            var invalid=s.constructionGateway(OWNER).execute("submit_blueprint",json("{\"source\":\"bad json\"}"));h.drain();check(invalid.isCompletedExceptionally(),"parse errors propagated");
            var canceled=s.constructionGateway(OWNER).execute("submit_blueprint",args);canceled.cancel(false);h.drain();check(canceled.isCancelled(),"future cancel prevents execution");
            var tools=new ControlledTools(new SourceCache(root.resolve("cache")),Map.of(),new JsonObject(),detached(),OWNER.toString(),Set.of("state","read_script"),null).live(s.liveProvider(OWNER));
            var worker=Executors.newSingleThreadExecutor();try{
                var first=worker.submit(()->tools.invoke("read_script","{\"offset\":0,\"limit\":8192}"));pump(h,first);check(first.get().contains("shield")&&!first.get().contains("program old"),"live read_script ignores static supplied session");
                s.command(OWNER,"edit program revised version v1; state hits = 7; on tick group owner { add hits = 1; }");s.command(OWNER,"apply");
                var fresh=new ControlledTools(new SourceCache(root.resolve("cache2")),Map.of(),new JsonObject(),detached(),OWNER.toString(),Set.of("state","read_script"),null).live(s.liveProvider(OWNER));
                var second=worker.submit(()->fresh.invoke("read_script","{\"offset\":0,\"limit\":8192}"));pump(h,second);check(second.get().contains("revised"),"hot script current provider");
                s.tick(OWNER,20);var third=worker.submit(()->fresh.invoke("state","{}"));pump(h,third);check(json(third.get()).getAsJsonObject("data").getAsJsonObject("state").get("hits").getAsLong()==1,"live next-round state sees event effect");
                var stale=worker.submit(()->tools.invoke("state","{}"));pump(h,stale);check(stale.get().contains("error"),"old generation provider rejected");
                s.deposit(OWNER,()->{});s.command(OWNER,"grant self 60 8 100000000");var runtimeGateway=s.delegatedGateway(OWNER);
                var sameRound=new ControlledTools(new SourceCache(root.resolve("same-round-cache")),Map.of(),new JsonObject(),detached(),OWNER.toString(),Set.of("read_script"),null).live(s.liveProvider(OWNER));
                var swap=runtimeGateway.execute("apply_script",json("{\"source\":\"program live_swap version v1; state hits = 0; on tick group owner { add hits = 2; }\"}"));h.drain();check(!swap.isCompletedExceptionally(),"delegated same-generation swap executed");
                var updated=worker.submit(()->sameRound.invoke("read_script","{\"offset\":0,\"limit\":8192}"));pump(h,updated);check(updated.get().contains("live_swap"),"same tool instance reads script updated during round");
            }finally{worker.shutdownNow();}
            var agentTools=new ControlledTools(new SourceCache(root.resolve("agent-cache")),Map.of(),new JsonObject(),detached(),OWNER.toString(),ControlledTools.WORLD_TOOLS,s.constructionGateway(OWNER)).live(s.liveProvider(OWNER));
            int[] requests={0};LocalChatClient.Transport transport=(uri,body,timeout,max)->{
                int n=requests[0]++;if(n==1)check(new String(body,StandardCharsets.UTF_8).contains("tool_call_id")&&new String(body,StandardCharsets.UTF_8).contains("QUEUED"),"tool receipt returned in next model turn");
                JsonObject message=new JsonObject();message.addProperty("role","assistant");message.addProperty("content",n==0?"":"fake queued, not certified complete");JsonObject choice=new JsonObject();choice.addProperty("finish_reason",n==0?"tool_calls":"stop");
                if(n==0){JsonObject fn=new JsonObject();fn.addProperty("name","submit_blueprint");fn.addProperty("arguments",args.toString());JsonObject call=new JsonObject();call.addProperty("id","build1");call.addProperty("type","function");call.add("function",fn);JsonArray calls=new JsonArray();calls.add(call);message.add("tool_calls",calls);}choice.add("message",message);JsonArray choices=new JsonArray();choices.add(choice);JsonObject response=new JsonObject();response.add("choices",choices);return CompletableFuture.completedFuture(new LocalChatClient.Response(200,response.toString().getBytes(StandardCharsets.UTF_8)));
            };
            try(var client=new LocalChatClient(new LocalChatClient.Config(IntegrationContracts.LOCAL_AI,"fake",Duration.ofSeconds(5),64,131072,1),transport);var agent=new MultiTurnAgent(client)){
                var run=agent.run("build mixed structure",agentTools,new MultiTurnAgent.Limits(3,4,30000,65536),t->{});pump(h,run);check(run.get().calls()==1&&requests[0]==2,"agent output really routes construction protocol");s.serverTick();check(h.world.blocks.get(new Blueprint.Pos(11,66,19)).equals("test:metal[axis=x]"),"AI-triggered real queue fake world effect");
            }
            s.command(OWNER,"build load absent");check(h.messages.get(h.messages.size()-1).contains("Nanites erro"),"manual blueprint missing file readable error");
            Path folder=root.resolve("config/nanites/scripts/"+OWNER);Files.createDirectories(folder);Path file=folder.resolve("manual.blueprint.json");Files.writeString(file,PLAN,StandardCharsets.UTF_8);s.command(OWNER,"build load manual");s.serverTick();
            Files.writeString(file,PLAN.replace("minecraft:stone","minecraft:dirt"),StandardCharsets.UTF_8);s.command(OWNER,"build load manual");s.serverTick();check(h.world.blocks.get(new Blueprint.Pos(10,64,20)).equals("minecraft:dirt"),"manual file reload replaces plan without restart");
            Files.writeString(file,"{}",StandardCharsets.UTF_8);s.command(OWNER,"build load manual");check(h.messages.get(h.messages.size()-1).contains("Nanites erro"),"invalid reload error");s.command(OWNER,"build status");check(h.messages.get(h.messages.size()-1).contains("COMPLETED"),"invalid file preserves last known good job");
            s.logout(OWNER);
        }
    }
    static void pump(Host host,Future<?> future)throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!future.isDone()&&System.nanoTime()<until){host.drain();Thread.yield();}check(future.isDone(),"async operation finished without world worker access");}
    public static void main(String[] args)throws Exception {geometry();queue();service(Files.createTempDirectory("nanites-construction-test-"));System.out.println("ConstructionTests: "+passed+" checks passed (fake world/AI; Minecraft NOT launched).");}
}
