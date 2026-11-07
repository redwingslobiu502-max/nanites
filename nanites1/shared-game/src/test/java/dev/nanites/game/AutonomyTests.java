package dev.nanites.game;

import com.google.gson.*;
import dev.nanites.core.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Offline fake server tick/clock and real capability-transfer, adapter and function protocol. */
public final class AutonomyTests {
    static int passed;
    static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000003");
    static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);passed++;}
    interface Attempt {void run() throws Exception;}
    static void denied(Attempt action,String text) throws Exception {try{action.run();throw new AssertionError(text);}catch(Exception expected){passed++;}}
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    static JsonObject target(int x){return json("{\"type\":\"block\",\"dimension\":\"minecraft:overworld\",\"x\":"+x+",\"y\":64,\"z\":0,\"side\":\"none\"}");}
    static JsonObject inspect(int x){var o=new JsonObject();o.add("target",target(x));o.addProperty("offset",0);o.addProperty("limit",1);return o;}
    static JsonObject transfer(String tool){var o=new JsonObject();o.add("from",target(0));o.add("to",target(1));o.addProperty("amount",1);o.addProperty("preview",false);if(!tool.equals("transfer_energy")){o.addProperty("from_slot",0);o.addProperty("to_slot",0);}return o;}
    static JsonObject monitor(){return json("{\"args\":"+inspect(1)+",\"path\":\"output\",\"op\":\"gte\",\"value\":1}");}
    static JsonObject recipe(int version,int repeats){
        var r=new JsonObject();r.addProperty("version",version);var steps=new JsonArray();
        for(String tool:List.of("transfer_items","transfer_energy","transfer_fluid")){
            var s=new JsonObject();s.addProperty("tool",tool);s.add("args",transfer(tool));s.addProperty("repeat",repeats);
            var w=new JsonObject();w.addProperty("tool","machine_inspect");w.add("args",inspect(0));w.addProperty("path","stock");w.addProperty("op","gt");w.addProperty("value",0);s.add("when",w);steps.add(s);
        }r.add("steps",steps);return r;
    }
    static JsonObject spec(boolean factory){return json("{\"mode\":\""+(factory?"factory":"language")+"\",\"text\":\"Maintain loaded factory input, FE and fluid; monitor output.\",\"scope\":{\"dimension\":\"minecraft:overworld\",\"min\":{\"x\":0,\"y\":64,\"z\":0},\"max\":{\"x\":1,\"y\":64,\"z\":0},\"machines\":["+target(0)+","+target(1)+"]},\"intervalTicks\":20,\"budget\":12,\"recipe\":{\"project\":\""+(factory?"default":"")+"\",\"name\":\""+(factory?"feed":"")+"\"},\"monitor\":"+monitor()+"}");}
    static JsonObject named(String name){var o=new JsonObject();o.addProperty("name",name);return o;}
    static final class Port implements CapabilityTransfer.Port {
        int amount;Port(int n){amount=n;}
        public int extract(int n,boolean simulation){int moved=Math.min(n,amount);if(!simulation)amount-=moved;return moved;}
        public int receive(int n,boolean simulation){int moved=Math.min(n,1000-amount);if(!simulation)amount+=moved;return moved;}
    }
    static final class Fake implements AutonomousGoals.Host {
        final Queue<Runnable> main=new ConcurrentLinkedQueue<>();final List<CompletableFuture<String>> rounds=new ArrayList<>();
        final List<BooleanSupplier> guards=new ArrayList<>();final List<BiConsumer<String,JsonObject>> receipts=new ArrayList<>();final List<String> prompts=new ArrayList<>();
        final Port[] from={new Port(100),new Port(100),new Port(100)},to={new Port(0),new Port(0),new Port(0)};
        boolean online=true,loaded=true,manual,model;int output,moves,inspections;long clock;
        public boolean online(UUID id){return online;}
        public boolean manualBusy(UUID id){return manual;}
        public boolean modelAvailable(){return model;}
        public void main(Runnable r){main.add(r);}
        void drain(){Runnable r;while((r=main.poll())!=null)r.run();}
        public JsonObject execute(UUID id,String tool,JsonObject a){
            if(!online)throw new IllegalStateException("Offline");if(!loaded)throw new MachineAdapters.WaitingChunk("Unloaded chunk");MachineTools.validate(tool,a);
            if(tool.equals("machine_inspect")){inspections++;return json("{\"stock\":"+from[0].amount+",\"output\":"+output+",\"items\":"+to[0].amount+",\"energy\":"+to[1].amount+",\"fluid\":"+to[2].amount+",\"clock\":"+clock+"}");}
            int i=List.of("transfer_items","transfer_energy","transfer_fluid").indexOf(tool);if(i<0)throw new IllegalArgumentException(tool);
            var r=CapabilityTransfer.move(from[i],to[i],a.get("amount").getAsInt(),a.get("preview").getAsBoolean(),held->{throw new AssertionError("Unexpected fake escrow");});moves+=r.moved();return json("{\"moved\":"+r.moved()+",\"extracted\":"+r.extracted()+",\"held\":"+r.held()+"}");
        }
        public CompletableFuture<String> round(UUID id,String prompt,JsonObject spec,int budget,BooleanSupplier valid,BiConsumer<String,JsonObject> receipt){
            check(budget<=24,"bounded model budget");prompts.add(prompt);guards.add(valid);receipts.add(receipt);var f=new CompletableFuture<String>();rounds.add(f);return f;
        }
        void machineTick(){clock++;if(to[0].amount>0&&to[1].amount>0&&to[2].amount>0){to[0].amount--;to[1].amount--;to[2].amount--;output++;}}
    }
    static final class Fixture implements AutoCloseable {
        final Path root;final Fake host;MachineAdapters adapters;AutonomousGoals goals;
        Fixture() throws Exception {this(Files.createTempDirectory("nanites-autonomy-"),new Fake());}
        Fixture(Path p,Fake h) throws Exception {root=p;host=h;adapters=new MachineAdapters(root.resolve("adapters"),host::execute);goals=new AutonomousGoals(root.resolve("objectives"),host,adapters);}
        void create(String name,boolean factory) throws Exception {var a=named(name);a.addProperty("source",spec(factory).toString());goals.command(OWNER,"objective_create",a);}
        void recipe(int repeats) throws Exception {var a=json("{\"project\":\"default\",\"name\":\"feed\"}");a.addProperty("source",AutonomyTests.recipe(1,repeats).toString());adapters.command(OWNER,"adapter_create",a);}
        JsonObject status(String name) throws Exception {return goals.command(OWNER,"objective_status",named(name));}
        void control(String name,String op) throws Exception {var a=named(name);a.addProperty("version",status(name).get("version").getAsInt());goals.command(OWNER,"objective_"+op,a);}
        void ticks(int n){for(int i=0;i<n;i++){host.drain();goals.tick();adapters.tick();host.machineTick();host.drain();}}
        void reload() throws Exception {goals.close();adapters=new MachineAdapters(root.resolve("adapters"),host::execute);goals=new AutonomousGoals(root.resolve("objectives"),host,adapters);}
        public void close(){goals.close();}
    }
    static void recurring() throws Exception {
        try(var f=new Fixture()){
            f.recipe(1);f.create("factory",true);f.ticks(100);
            check(f.host.output>=4,"real recurring item FE fluid consumption");check(f.host.rounds.isEmpty(),"factory works without model");
            check(f.status("factory").get("cycles").getAsLong()>=4,"multiple completed cycles");check(!f.status("factory").get("state").getAsString().equals("done"),"measured factory remains continuous");
            check(f.status("factory").getAsJsonArray("history").size()<=16,"bounded episodic receipts");
            f.control("factory","pause");int n=f.host.moves;f.ticks(100);check(f.host.moves==n,"paused no transfer");
            f.reload();check(f.status("factory").get("state").getAsString().equals("paused"),"paused persists across restart");f.ticks(40);check(f.host.moves==n,"reload does not resume paused");
            f.control("factory","resume");f.ticks(50);check(f.host.moves>n,"resume executes daemon");
            f.host.online=false;f.goals.logout(OWNER);n=f.host.moves;f.ticks(80);check(f.host.moves==n,"logout stops transfers");check(f.status("factory").get("state").getAsString().equals("waiting_player"),"waiting owner visible");
            f.reload();f.host.online=true;f.ticks(100);check(f.host.moves>n,"restart owner online resumes");
            f.control("factory","stop");n=f.host.moves;f.reload();f.ticks(60);check(f.host.moves==n,"stopped remains done after restart");
        }
    }
    static void checkpointAndRevalidate() throws Exception {
        try(var f=new Fixture()){
            f.recipe(1);f.create("factory",true);f.ticks(1);
            check(f.host.from[0].amount==99&&f.host.from[1].amount==100,"one action in tick");
            check(f.status("factory").getAsJsonObject("checkpoint").get("step").getAsInt()==1,"next step durable");
            f.reload();f.host.loaded=false;f.ticks(50);check(f.host.moves==1,"unloaded no checkpoint replay");check(f.status("factory").get("state").getAsString().equals("waiting_chunks"),"chunk waiting lifecycle");
            f.host.loaded=true;f.ticks(20);check(f.host.from[0].amount==99,"restart does not replay already transferred input");check(f.host.from[1].amount==99&&f.host.from[2].amount==99,"remaining cursor resumed after live inspection");
        }
        try(var f=new Fixture()){
            f.recipe(1);f.create("factory",true);f.ticks(1);f.goals.close();
            Path p=f.root.resolve("objectives").resolve(OWNER+"_factory.goal.json");var o=json(Files.readString(p));o.addProperty("pending",true);Files.writeString(p,o.toString(),StandardCharsets.UTF_8);
            f.goals=new AutonomousGoals(f.root.resolve("objectives"),f.host,f.adapters);int n=f.host.moves;f.ticks(100);
            check(f.host.moves==n,"uncertain action never replayed");check(f.status("factory").get("state").getAsString().equals("failed"),"ambiguous crash visible failed");denied(()->f.control("factory","resume"),"uncertain resume rejected");
        }
    }
    static void lifecycleAndLanguage() throws Exception {
        try(var f=new Fixture()){
            f.create("goal",false);f.ticks(30);check(f.host.rounds.isEmpty(),"model absent never contacted");check(f.status("goal").get("state").getAsString().equals("waiting_model"),"waiting_model observable");
            f.host.model=true;f.ticks(30);check(f.host.rounds.size()==1,"one model round");f.ticks(100);check(f.host.rounds.size()==1,"singleflight delayed future");
            var old=f.host.rounds.get(0);var guard=f.host.guards.get(0);f.control("goal","pause");check(old.isCancelled()&&!guard.getAsBoolean(),"pause invalidates generation and future");
            old.complete("feito");f.host.receipts.get(0).accept("old",json("{\"moved\":999}"));f.host.drain();check(f.status("goal").get("cycles").getAsLong()==0,"stale output/receipts cannot overwrite");
            f.control("goal","resume");f.ticks(1);check(f.host.rounds.size()==2,"new generation resumes");check(f.host.prompts.get(1).contains("PERSISTED")&&f.host.prompts.get(1).contains("scope")&&f.host.prompts.get(1).contains("machine_inspect"),"fresh state boundaries episodes in next round");
            f.host.rounds.get(1).complete("feito");f.host.drain();check(!f.status("goal").get("state").getAsString().equals("done"),"prose not completion evidence");check(!f.status("goal").get("measured").getAsBoolean(),"unmet telemetry measured false");
            int n=f.host.rounds.size();f.ticks(20);check(f.host.rounds.size()==n,"failed predicate backs off no each-tick model");f.ticks(30);check(f.host.rounds.size()==n+1,"periodic replan after backoff");
            var pending=f.host.rounds.get(n);f.host.online=false;f.goals.logout(OWNER);check(pending.isCancelled(),"logout cancels round");f.host.online=true;f.ticks(25);var current=f.host.rounds.get(f.host.rounds.size()-1);
            f.goals.close();check(current.isCancelled(),"server close cancels workers");f.host.drain();
        }
    }
    static void validationAndArbitration() throws Exception {
        try(var f=new Fixture()){
            f.recipe(1);f.create("factory",true);var original=f.status("factory");
            var a=named("factory");a.addProperty("version",1);var invalid=spec(true);invalid.addProperty("intervalTicks",0);a.addProperty("source",invalid.toString());denied(()->f.goals.command(OWNER,"objective_update",a),"invalid candidate denied");check(f.status("factory").get("version").getAsInt()==1,"last known good update retained");
            a.addProperty("source",spec(true).toString());a.addProperty("version",2);denied(()->f.goals.command(OWNER,"objective_update",a),"stale control version denied");
            denied(()->f.goals.command(UUID.randomUUID(),"objective_status",named("factory")),"cross owner denied");denied(()->f.create("factory",true),"duplicate goal denied");
            var out=transfer("transfer_items");out.add("to",target(2));denied(()->AutonomousGoals.scopeTool(spec(true),"transfer_items",out),"out of scope denied");
            f.host.manual=true;f.ticks(100);check(f.host.moves==0,"manual priority explicit");f.host.manual=false;f.ticks(50);check(f.host.output>=1,"manual released auto resumes");
            int moves=f.host.moves;f.goals.yield(OWNER);f.host.manual=true;f.ticks(50);check(f.host.moves==moves,"manual preemption no auto step leaked");
            f.host.manual=false;f.host.from[0].amount=0;f.host.output=0;f.ticks(300);check(f.status("factory").get("state").getAsString().equals("retrying"),"resource stall observed not false done");
            check(f.status("factory").get("error").getAsString().contains("stall"),"stall error visible");f.host.from[0].amount=50;f.ticks(2000);check(f.host.output>0,"recurring live feed recovers after resources replenished");
            f.control("factory","pause");f.reload();check(f.status("factory").getAsJsonObject("spec").equals(original.getAsJsonObject("spec")),"reload last good config retained");
        }
        for(String name:ObjectiveTools.NAMES){var schema=MachineTools.schema(name);check(schema.has("required"),"strict objective schemas "+name);}
        var outside=spec(true);outside.getAsJsonObject("scope").getAsJsonObject("max").addProperty("x",0);denied(()->AutonomousGoals.validate(outside.toString()),"machine boundary validated");
        var huge=spec(true);huge.addProperty("budget",25);denied(()->AutonomousGoals.validate(huge.toString()),"unbounded budget rejected");
    }
    static void realAgentFactoryProtocol() throws Exception {
        try(var f=new Fixture()){
            f.recipe(1);f.host.model=true;f.create("goal",false);f.ticks(1);
            // Fake transport still goes through actual MultiTurnAgent + ControlledTools + real daemon creation.
            var scope=spec(false);var cache=new SourceCache(f.root.resolve("cache"));var session=new NaniteSession("owner",GameService.POLICY,List.of(new Ledger.Reservoir("storage","generic",100),new Ledger.Reservoir("spent","consumed",100)),Map.of("storage",0L,"spent",0L),"test.nano",GameService.DEFAULT);
            var allowed=new HashSet<>(ControlledTools.NAMES);allowed.addAll(MachineTools.NAMES);
            var tools=new ControlledTools(cache,Map.of(),json("{}"),session,"owner",allowed,(name,args)->{
                var result=new CompletableFuture<JsonObject>();f.host.main(()->{try{if(ObjectiveTools.NAMES.contains(name))result.complete(f.goals.command(OWNER,name,args));else if(MachineTools.ADAPTERS.contains(name))result.complete(f.adapters.command(OWNER,name,args));else result.complete(f.host.execute(OWNER,name,args));}catch(Exception e){result.completeExceptionally(e);}});return result;
            });
            final int[] calls={0};JsonObject attached=spec(true);attached.addProperty("mode","language");var update=named("goal");update.addProperty("version",1);update.addProperty("source",attached.toString());
            LocalChatClient.Transport transport=(uri,bytes,timeout,max)->{
                String body=new String(bytes,StandardCharsets.UTF_8);int call=calls[0]++;if(call==0){var function=new JsonObject();function.addProperty("name","machine_inspect");function.addProperty("arguments",inspect(0).toString());return response(function,"observe");}
                if(call==1){check(body.contains("stock"),"actual observation receipt fed to model");var function=new JsonObject();function.addProperty("name","objective_update");function.addProperty("arguments",update.toString());return response(function,"attach");}
                return CompletableFuture.completedFuture(new LocalChatClient.Response(200,"{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Factory attached; monitor receipts\"}}]}".getBytes(StandardCharsets.UTF_8)));
            };
            var client=new LocalChatClient(new LocalChatClient.Config(IntegrationContracts.LOCAL_AI,"fake",Duration.ofSeconds(1),1024,131072,1),transport);
            try(var agent=new MultiTurnAgent(client)){
                var run=agent.run("Create persistent factory via tools",tools,new MultiTurnAgent.Limits(4,8,MultiTurnAgent.catalogBudget(client,tools.definitions(),4),65536),t->{});
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!run.isDone()&&System.nanoTime()<deadline){f.host.drain();Thread.yield();}
                run.get(1,TimeUnit.SECONDS);check(calls[0]>=3,"multi-turn actual tool protocol");
            }finally{client.close();}
            int models=f.host.rounds.size();f.ticks(100);check(f.host.output>=3,"LLM attached recurring factory drives multiple cycles");check(f.host.rounds.size()==models,"healthy daemon not model per transfer");
        }
    }
    static void serviceAutomaticProtocol() throws Exception {
        Path root=Files.createTempDirectory("nanites-auto-service-");var fake=new Fake();var messages=new ArrayList<String>();final int[] turns={0};
        JsonObject attached=spec(true);attached.addProperty("mode","language");var update=named("goal");update.addProperty("version",1);update.addProperty("source",attached.toString());
        LocalChatClient.Transport transport=(uri,bytes,timeout,max)->{
            String body=new String(bytes,StandardCharsets.UTF_8);int turn=turns[0]++;var fn=new JsonObject();
            if(turn==0){check(body.contains("PERSISTED")&&body.contains("assimilation")&&body.contains("boundaries")&&body.contains("fake-catalog-recipe"),"service automatic model gets memory and registry context");fn.addProperty("name","adapter_create");var a=json("{\"project\":\"default\",\"name\":\"feed\"}");a.addProperty("source",recipe(1,1).toString());fn.addProperty("arguments",a.toString());return response(fn,"recipe");}
            if(turn==1){check(body.contains("ACTIVE"),"automatic tool receipt propagated");fn.addProperty("name","objective_update");fn.addProperty("arguments",update.toString());return response(fn,"attach");}
            return CompletableFuture.completedFuture(new LocalChatClient.Response(200,"{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"done\"}}]}".getBytes(StandardCharsets.UTF_8)));
        };
        var host=new GameService.Host(){
            public void main(Runnable task){fake.main(task);}public boolean online(UUID id){return fake.online;}public void message(UUID id,String text){messages.add(text);}
            public void particles(UUID id,String shape,int radius){}public List<String> mods(){return List.of("fake@1");}public String loader(){return "forge";}public JsonObject machineOperation(UUID id,String tool,JsonObject args){if(Set.of("technology_list","material_stock","recipe_search").contains(tool))return json("{\"source\":\"fake-catalog-recipe\",\"owner_units\":100,\"next_offset\":2}");return fake.execute(id,tool,args);}public LocalChatClient.Transport aiTransport(){return transport;}
        };
        Path config=root.resolve("config/nanites");Files.createDirectories(config);Files.writeString(config.resolve("server.properties"),"ai.model=fake\nai.address="+IntegrationContracts.LOCAL_AI+"\nai.token=\n");
        try(var service=new GameService(root.resolve("world"),root.resolve("config"),host)){
            service.login(OWNER);service.command(OWNER,"objective create goal "+spec(false));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);JsonObject status=null;
            while(System.nanoTime()<deadline){fake.drain();service.serverTick();fake.machineTick();fake.drain();status=service.machineOperation(OWNER,"objective_status",named("goal"));if(status.get("version").getAsInt()==2&&status.get("cycles").getAsLong()>=3)break;Thread.yield();}
            check(status!=null&&status.get("version").getAsInt()==2,"real automatic GameService agent attaches factory");check(fake.output>=2,"Forge-linked serverTick repeatedly moves consumes and produces");check(turns[0]==2,"healthy factory no further model rounds");
            service.command(OWNER,"ai_cancel");int moved=fake.moves;for(int i=0;i<80;i++){service.serverTick();fake.machineTick();fake.drain();}check(fake.moves>moved,"manual ai_cancel preserves durable objective");
            service.command(OWNER,"factory pause goal 2");moved=fake.moves;for(int i=0;i<60;i++){service.serverTick();fake.drain();}check(fake.moves==moved,"raw pause command stops daemon");
            service.command(OWNER,"adapter define default extra "+recipe(1,1));check(messages.get(messages.size()-1).contains("ACTIVE"),"raw recipe command uses validated adapters");
        }
        fake.drain();check(fake.main.isEmpty(),"stopped service drains callbacks without leaked tasks");
    }
    static CompletableFuture<LocalChatClient.Response> response(JsonObject function,String id){var call=new JsonObject();call.addProperty("id",id);call.addProperty("type","function");call.add("function",function);var message=new JsonObject();message.addProperty("role","assistant");message.add("content",JsonNull.INSTANCE);var tools=new JsonArray();tools.add(call);message.add("tool_calls",tools);var choice=new JsonObject();choice.addProperty("finish_reason","tool_calls");choice.add("message",message);var choices=new JsonArray();choices.add(choice);var root=new JsonObject();root.add("choices",choices);return CompletableFuture.completedFuture(new LocalChatClient.Response(200,root.toString().getBytes(StandardCharsets.UTF_8)));}
    public static void main(String[] args) throws Exception {recurring();checkpointAndRevalidate();lifecycleAndLanguage();validationAndArbitration();realAgentFactoryProtocol();serviceAutomaticProtocol();System.out.println("AutonomyTests "+passed+" passed");}
}
