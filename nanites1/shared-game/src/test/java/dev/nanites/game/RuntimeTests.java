package dev.nanites.game;

import dev.nanites.core.*;
import com.google.gson.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

/** Deterministic fake backend/transport checks, NOT a live Forge/LLM certification. */
public final class RuntimeTests {
    private static int passed;
    static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000001"),OTHER=UUID.fromString("00000000-0000-0000-0000-000000000002"),FOREIGN=UUID.fromString("00000000-0000-0000-0000-000000000003");
    static void check(boolean b,String label){if(!b)throw new AssertionError(label);passed++;}
    static void denied(Runnable r,String label){try{r.run();throw new AssertionError(label);}catch(RuntimeException expected){passed++;}}
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    static final class Backend implements RuntimeInterventions.Backend {
        long money=1_000_000_000L;double base=10,healed;boolean online=true,valid=true,failApply;int payments;
        final Map<UUID,Double> modifiers=new HashMap<>(Map.of(FOREIGN,2.0));
        public boolean available(UUID id){return online;}
        public void validate(UUID id,RuntimeInterventions.Profile p){if(!valid)throw new IllegalArgumentException("missing attribute");}
        public void overlay(UUID id,UUID mod,RuntimeInterventions.Profile p){modifiers.put(mod,p.attack());if(failApply)throw new IllegalStateException("backend failed after partial overlay");}
        public void remove(UUID id,UUID mod){modifiers.remove(mod);}
        public void heal(UUID id,double hp){healed+=hp;}
        public boolean pay(UUID id,long cost){if(cost>money)return false;money-=cost;payments++;return true;}
        public void refund(UUID id,long cost){money+=cost;}
        double value(){return base+modifiers.values().stream().mapToDouble(d->d).sum();}
    }
    static void leases(){Backend b=new Backend();var r=new RuntimeInterventions(b);var p=new RuntimeInterventions.Profile(.1,1,2,.1,4,1);
        denied(()->new RuntimeInterventions.Profile(Double.NaN,0,0,0,0,0),"NaN denied");denied(()->new RuntimeInterventions.Profile(.11,0,0,0,0,0),"speed cap");denied(()->new RuntimeInterventions.Profile(0,2,0,0,0,0),"attack cap");denied(()->new RuntimeInterventions.Profile(0,0,0,0,0,1.1),"regen cap");
        denied(()->r.define(OWNER,"arbitrary",p),"closed slots");r.define(OWNER,"p0",p);denied(()->r.profile(OTHER,"p0"),"owner separation");denied(()->r.start(OWNER,"extremis",3,0),"opt-in required");
        check(RuntimeInterventions.cost(p,3)==85_000_000L,"exact upfront+maintenance price");denied(()->RuntimeInterventions.cost(p,61),"TTL cap");
        b.valid=false;denied(()->r.start(OWNER,"p0",3,0),"attributes checked before charge");check(b.payments==0,"validation has no debit");b.valid=true;
        r.start(OWNER,"p0",3,0);check(b.value()==13,"additive owned overlay");b.base=20;b.modifiers.put(FOREIGN,5.0);
        denied(()->r.start(OWNER,"p0",3,0),"no stacking");denied(()->r.define(OWNER,"p0",p),"cannot redefine live");check(b.payments==1,"no reemit cost/effect");
        r.tick(OWNER,19);check(b.healed==0,"no instant regen");r.tick(OWNER,20);r.tick(OWNER,20);check(b.healed==1,"one HP per second repeated tick safe");
        check(r.damage(OWNER,10)==6,"guard per hit cap");check(r.damage(OWNER,10)==6,"guard second budget");check(r.damage(OWNER,10)==6,"guard third budget");check(r.damage(OWNER,10)==10,"finite total guard");
        r.tick(OWNER,60);check(r.lease(OWNER)==null&&b.value()==25,"expiry preserves concurrent base+foreign modifier");check(b.healed==1,"healing consequence not undone");
        b.money=0;denied(()->r.start(OWNER,"p0",1,70),"unfunded denied");check(r.lease(OWNER)==null,"no effect on failed cost");b.money=1_000_000_000L;b.failApply=true;denied(()->r.start(OWNER,"p0",1,70),"partial backend failure");check(b.modifiers.size()==1,"partial overlay cleaned");check(b.money==1_000_000_000L&&r.lease(OWNER)==null,"failed activation compensated exactly");b.failApply=false;
        r.opt(OWNER,true);r.start(OWNER,"extremis",3,80);r.opt(OWNER,false);check(r.lease(OWNER)==null,"opt-out removes lease");
        var g=r.grant(OWNER,2,2,10,100);denied(()->r.authorize(OWNER,g.serial()+1,100,1),"wrong token");denied(()->r.authorize(OTHER,g.serial(),100,1),"wrong actor");denied(()->r.authorize(OWNER,g.serial(),100,11),"grant budget");r.authorize(OWNER,g.serial(),100,6);r.authorize(OWNER,g.serial(),100,4);denied(()->r.authorize(OWNER,g.serial(),100,0),"call exhaustion");
        var replacement=r.grant(OWNER,2,2,10,100);denied(()->r.authorize(OWNER,g.serial(),100,0),"replacement invalidates old token");denied(()->r.authorize(OWNER,replacement.serial(),140,0),"expiry exact");r.revoke(OWNER);denied(()->r.authorize(OWNER,replacement.serial(),101,0),"revoke");
        r.start(OWNER,"p0",1,150);b.online=false;r.tick(OWNER,151);check(r.lease(OWNER)==null,"unload cleanup");r.forget(OWNER);check(r.profiles(OWNER).isEmpty(),"logout definitions forgotten");r.close();
    }
    static final class Host implements GameService.Host {
        long clock;boolean online=true;final Queue<Runnable> main=new ConcurrentLinkedQueue<>();final List<String> log=new CopyOnWriteArrayList<>();final Backend backend=new Backend();int particles;
        public String loader(){return "forge";}public boolean runtimeAvailable(UUID id){return online&&id.equals(OWNER);}
        public void validateOverlay(UUID id,RuntimeInterventions.Profile p){backend.validate(id,p);}public void overlay(UUID id,UUID mod,RuntimeInterventions.Profile p){backend.overlay(id,mod,p);}public void removeOverlay(UUID id,UUID mod){backend.remove(id,mod);}public void heal(UUID id,double hp){backend.heal(id,hp);}
        public void message(UUID id,String s){log.add(s);}public void particles(UUID id,String shape,int radius){particles++;}public void main(Runnable r){main.add(r);}public boolean online(UUID id){return online;}public List<String> mods(){return List.of();}public long clock(){return clock;}
        void drain(){Runnable r;while((r=main.poll())!=null)r.run();}
    }
    static final String DEFINE="{\"profile\":\"p0\",\"speed\":0.1,\"attack\":1,\"armor\":0,\"knockback\":0,\"guard\":1,\"regen\":1}";
    static void gateway(Path root)throws Exception{Host h=new Host();try(var service=new GameService(root.resolve("world"),root.resolve("config"),h)){
        service.login(OWNER);try{service.delegatedGateway(OWNER);throw new AssertionError("no grant");}catch(SecurityException e){passed++;}
        service.deposit(OWNER,()->{});service.command(OWNER,"grant self 60 8 100000000");var gateway=service.delegatedGateway(OWNER);
        var def=gateway.execute("define_profile",json(DEFINE));check(!def.isDone(),"mutations queued on host.main");h.drain();check(def.join().get("status").getAsString().equals("EXECUTED_SERVER_MAIN"),"real mutation receipt");
        var start=gateway.execute("start_intervention",json("{\"profile\":\"p0\",\"seconds\":2}"));h.drain();check(start.join().get("charged").getAsLong()==27_000_000L,"own receipt avoids stale self conflict");check(h.backend.modifiers.size()==2,"actual fake backend mutation");
        var cancel=gateway.execute("cancel_intervention",json("{}"));h.drain();cancel.join();check(h.backend.modifiers.size()==1,"cancel cleanup");
        var swap=gateway.execute("apply_script",json("{\"source\":\"program ai version v1; state hits = 0; on tick group owner { add hits = 1; }\"}"));h.drain();check(swap.join().get("status").getAsString().contains("EXECUTED"),"state-only script applied not proposed");
        var world=gateway.execute("apply_script",json("{\"source\":\"program ai version v1; state hits = 0; on tick group owner { emit protect self = 1; }\"}"));h.drain();check(world.isCompletedExceptionally(),"unbounded delegated scripts rejected");
        var transfer=gateway.execute("apply_script",json("{\"source\":\"program ai version v1; state hits = 0; on tick group owner { transfer storage spent = 100; }\"}"));h.drain();check(transfer.isCompletedExceptionally(),"delegated persistent transfer budget bypass denied");
        var canceled=gateway.execute("define_profile",json(DEFINE));canceled.cancel(false);h.drain();check(canceled.isCancelled(),"canceled queued future skips execution");
        h.online=false;var unavailable=gateway.execute("define_profile",json(DEFINE));h.drain();check(unavailable.isCompletedExceptionally(),"online permission revalidated on main");h.online=true;
        service.command(OWNER,"revoke");var after=gateway.execute("define_profile",json(DEFINE));h.drain();check(after.isCompletedExceptionally(),"queued revalidation after revoke");
        service.command(OWNER,"grant self 60 8 100000000");var stale=service.delegatedGateway(OWNER);service.deposit(OWNER,()->{});var conflict=stale.execute("define_profile",json(DEFINE));h.drain();check(conflict.isCompletedExceptionally(),"external accounting revision conflict");
        service.command(OWNER,"runtime define p0 0.1 1 0 0 1 1");service.command(OWNER,"edit program rt version v1; state hits = 0; on tick group owner { emit intervene.p0 self = 2; }");service.command(OWNER,"apply");h.clock=20;service.tick(OWNER,20);check(h.backend.modifiers.size()==2&&h.particles==0,"typed script intervention not particles");service.tick(OWNER,40);check(h.backend.modifiers.size()==2,"reemit rejects stacking");service.invalidateRuntime(OWNER);check(h.backend.modifiers.size()==1,"death/dimension lifecycle cleanup");
        fakeAgent(service,h,root);
    }}
    static void fakeAgent(GameService service,Host h,Path root)throws Exception{
        service.command(OWNER,"grant self 60 8 100000000");var gateway=service.delegatedGateway(OWNER);var session=new NaniteSession(OWNER.toString(),GameService.POLICY,List.of(new Ledger.Reservoir("storage","generic",100)),Map.of("storage",0L),"fake.nano","program fake version v1; state hits = 0;");
        var tools=new ControlledTools(new SourceCache(root.resolve("cache")),Map.of(),new JsonObject(),session,OWNER.toString(),ControlledTools.MUTATIONS,gateway);
        check(!ControlledTools.NAMES.contains("define_profile"),"manual allowlist default unchanged");check(tools.invoke("grant","{}").contains("error"),"tools cannot self grant");
        int[] requests={0};LocalChatClient.Transport fake=(uri,body,timeout,max)->{int n=requests[0]++;JsonObject message=new JsonObject();message.addProperty("role","assistant");message.addProperty("content",n==0?"":"done fake");JsonObject choice=new JsonObject();choice.addProperty("finish_reason",n==0?"tool_calls":"stop");if(n==0){JsonArray calls=new JsonArray();JsonObject fn=new JsonObject();fn.addProperty("name","define_profile");fn.addProperty("arguments",DEFINE);JsonObject call=new JsonObject();call.addProperty("id","fake1");call.addProperty("type","function");call.add("function",fn);calls.add(call);message.add("tool_calls",calls);}choice.add("message",message);JsonArray choices=new JsonArray();choices.add(choice);JsonObject response=new JsonObject();response.add("choices",choices);return CompletableFuture.completedFuture(new LocalChatClient.Response(200,response.toString().getBytes(StandardCharsets.UTF_8)));};
        try(var client=new LocalChatClient(new LocalChatClient.Config(IntegrationContracts.LOCAL_AI,"fake",Duration.ofSeconds(5),64,131072,1),fake);var agent=new MultiTurnAgent(client)){
            var run=agent.run("define profile",tools,new MultiTurnAgent.Limits(3,4,30000,65536),t->{});
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!run.isDone()&&System.nanoTime()<end){h.drain();Thread.onSpinWait();}
            var out=run.get(1,TimeUnit.SECONDS);check(out.calls()==1&&requests[0]==2,"fake multi-turn actual autoexec routed");service.command(OWNER,"runtime list");check(h.log.get(h.log.size()-1).contains("p0=Profile"),"fake agent defined profile in real service");
        }
    }
    static void armor(){
        denied(()->ArmorModules.validate(List.of("thermal","thermal")),"duplicate NBT denied");denied(()->ArmorModules.validate(List.of("foreign")),"NBT whitelist");denied(()->ArmorModules.validate(List.of("impact","thermal","cushioning","impact")),"NBT slot quota");
        var slots=ArmorModules.install(Set.of(),"cushioning","cushioning",1);final var original=slots;check(slots.equals(Set.of("cushioning")),"validated module install");denied(()->ArmorModules.install(original,"cushioning","cushioning",1),"no duplicate installation");denied(()->ArmorModules.install(original,"thermal","impact",1),"held resource validated");denied(()->ArmorModules.install(original,"thermal","thermal",0),"empty resource rejected");
        check(ArmorModules.reduction(slots,"fall",false,10,0)==4,"cushioning actual reduction");check(ArmorModules.reduction(slots,"impact",false,10,0)==0,"damage typed no universal protection");slots=ArmorModules.install(slots,"thermal","thermal",1);check(ArmorModules.reduction(slots,"impact",true,5,0)==2,"thermal fire reduction");check(ArmorModules.reduction(slots,"impact",true,5,8)==0,"total second armor budget exhausted");check(ArmorModules.reduction(slots,"fall",false,Float.NaN,0)==0,"nonfinite damage no spend");
    }
    static String status(GameService s,Host h){s.command(OWNER,"status");return h.log.get(h.log.size()-1);}
    static void auditRegression(Path root)throws Exception{
        var budget=new ProtectionBudget();budget.consume(OWNER,0,6);check(budget.limit(OWNER,19,5)==2,"shared cap clamps stack");budget.consume(OWNER,19,2);check(budget.available(OWNER,19)==0,"same bucket exhausted");check(budget.available(OWNER,20)==8,"server bucket renews");check(budget.available(OTHER,0)==8,"owner budget isolated");
        check(ArmorModules.limit(Set.of("impact"),"impact",true)==0,"impact cannot bypass thermal module");
        Host h=new Host();try(var s=new GameService(root.resolve("world"),root.resolve("config"),h)){
            s.login(OWNER);s.deposit(OWNER,()->{});s.command(OWNER,"runtime define p0 0 0 0 0 4 0");s.command(OWNER,"runtime start p0 2");
            check(s.damage(OWNER,"fall",30,5)==22,"legacy shield shares Forge 8 HP cap");check(s.paidProtection(OWNER,4)==0,"modules cannot stack after shield budget");check(s.runtimeDamage(OWNER,10)==10,"guard cannot stack after shield budget");
            h.clock=20;check(s.runtimeDamage(OWNER,10)==6,"guard uses next server bucket");check(s.damage(OWNER,"impact",10,0)==6,"shield uses only remaining global cap");check(s.paidProtection(OWNER,2)==0,"all three paths share one budget");
            s.command(OWNER,"runtime cancel");s.command(OWNER,"grant self 60 8 100000000");var gateway=s.delegatedGateway(OWNER);String before=status(s,h);
            var bad=gateway.execute("apply_script",json("{\"source\":\"not a program\"}"));h.drain();check(bad.isCompletedExceptionally(),"invalid delegated apply fails");check(status(s,h).equals(before),"failed apply leaves revision/state/balance unchanged");
            h.backend.failApply=true;var failed=gateway.execute("start_intervention",json("{\"profile\":\"p0\",\"seconds\":1}"));h.drain();check(failed.isCompletedExceptionally(),"delegated partial overlay fails");check(status(s,h).equals(before)&&h.backend.modifiers.size()==1,"overlay failure compensates service ledger and own UUID");h.backend.failApply=false;
            h.clock=40;check(s.damage(OWNER,"impact",10,0,true)==10,"generic fire shield cannot bypass material restriction");check(!s.insulating(OWNER),"thermal material not inferred");s.absorb(OWNER,"minecraft:white_wool",()->{});check(s.insulating(OWNER),"explicit mapped material consumed");
            s.command(OWNER,"edit program thermal version v1; state hits = 0; on impact group owner { emit protect.insulating self = 2; }");s.command(OWNER,"apply");check(s.damage(OWNER,"impact",5,0,true)==3,"explicit insulating fire shield allowed");
            s.command(OWNER,"grant self 60 8 100000000");var cancelGateway=s.delegatedGateway(OWNER);var detached=new NaniteSession(OWNER.toString(),GameService.POLICY,List.of(new Ledger.Reservoir("storage","generic",100)),Map.of("storage",0L),"fake.nano","program fake version v1; state hits = 0;");
            var tools=new ControlledTools(new SourceCache(root.resolve("cache")),Map.of(),new JsonObject(),detached,OWNER.toString(),ControlledTools.MUTATIONS,cancelGateway);
            var executor=Executors.newSingleThreadExecutor();try{var invoke=executor.submit(()->tools.invoke("define_profile",DEFINE));long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(h.main.isEmpty()&&System.nanoTime()<end)Thread.onSpinWait();check(!h.main.isEmpty(),"tool future queued before cancellation");tools.cancelPending();h.drain();check(invoke.get(2,TimeUnit.SECONDS).contains("error"),"agent cancellation cancels queued mutation immediately");}finally{executor.shutdownNow();}
        }
    }
    public static void main(String[] args)throws Exception{leases();armor();gateway(Files.createTempDirectory("nanites-runtime-tests-"));auditRegression(Files.createTempDirectory("nanites-audit-tests-"));System.out.println("RuntimeTests: "+passed+" deterministic checks passed (FAKE backend/transport; no live Forge or AI).");}
}
