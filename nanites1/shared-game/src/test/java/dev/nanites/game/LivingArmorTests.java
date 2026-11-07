package dev.nanites.game;

import com.google.gson.*;
import dev.nanites.core.*;
import java.nio.file.*;
import java.util.*;

/** Real shared controller, filesystem, payment and gateway, deterministic ports; no model/network/game. */
public final class LivingArmorTests {
    static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    interface Action {void run()throws Exception;}
    static void denied(Action a,String why)throws Exception {try{a.run();throw new AssertionError(why);}catch(RuntimeException expected){checks++;}}
    static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000004");
    static final String KEY="a".repeat(64);
    static class Port implements LivingArmor.RepairPort {
        int damage=80,stock=3,reserved,applied;long balance=10000000;boolean compatible=true,failCommit,failApply;
        public int damage(){return damage;}public int maxDamage(){return 100;}public boolean compatible(){return compatible;}
        public String reserve(){if(stock-reserved<1)throw new IllegalStateException("No stock");reserved++;return "token";}
        public void release(String t){reserved--;}
        public boolean pay(long cost){if(balance<cost)return false;balance-=cost;return true;}
        public void refund(long cost){balance+=cost;}
        public void commit(String t){if(failCommit)throw new IllegalStateException("decode failure");stock--;reserved--;}
        public void apply(int points){if(failApply)throw new IllegalStateException("equipment failure");damage-=points;applied++;}
    }
    public static void main(String[] args)throws Exception {
        check(LivingArmor.reduction(4,8,10)>LivingArmor.reduction(4,0,10),"adaptive hits increase real reduction");
        check(LivingArmor.reduction(0,8,10)==0&&LivingArmor.reduction(4,9,10)==0&&LivingArmor.reduction(4,8,Float.NaN)==0,"invalid/no armor never shields");
        check(LivingArmor.reduction(4,8,100)==2&&LivingArmor.reduction(4,8,.1f)<=.1f,"guard capped and nonnegative remaining damage");
        check(LivingArmor.response(2,4,8,5)>LivingArmor.response(2,4,0,5),"installed modules still benefit from real adaptation on remaining damage");
        check(LivingArmor.response(4,4,8,1)==1&&LivingArmor.response(2,0,8,5)==2,"combined response cannot overblock or grant adaptation without armor");
        var p=new Port();var preview=LivingArmor.repair(p,true);check(preview.get("wouldRepair").getAsInt()==25&&p.stock==3&&p.balance==10000000&&p.damage==80&&p.reserved==0,"preview wholly inert");
        var receipt=LivingArmor.repair(p,false);check(receipt.get("repaired").getAsInt()==25&&p.damage==55&&p.stock==2&&p.balance==7500000&&p.reserved==0,"one material and exact nanites paid for actual durability");
        p.balance=0;check(LivingArmor.repair(p,false).get("status").getAsString().equals("INSUFFICIENT_NANITES")&&p.stock==2&&p.reserved==0&&p.damage==55,"insufficient balance releases stock");
        p.compatible=false;denied(()->LivingArmor.repair(p,false),"incompatible ingredient denied");check(p.stock==2&&p.damage==55,"wrong material cannot debit");
        p.compatible=true;p.balance=10000000;p.failCommit=true;denied(()->LivingArmor.repair(p,false),"commit failure visible");check(p.stock==2&&p.balance==10000000&&p.reserved==0,"precommit failure refunds/releases");
        p.failCommit=false;p.failApply=true;denied(()->LivingArmor.repair(p,false),"postcommit ambiguity visible");check(p.stock==1&&p.balance==7500000&&p.damage==55,"ambiguous result not falsely refunded or marked repaired");
        p.failApply=false;p.damage=0;check(LivingArmor.repair(p,false).get("status").getAsString().equals("UNDAMAGED")&&p.stock==1,"undamaged equipment does not spend");
        Path dir=Files.createTempDirectory("nanites-living-armor-");int[] calls={0};var host=new LivingArmor.Host(){public JsonObject status(UUID id){return new JsonObject();}public JsonObject repair(UUID id,JsonObject a){calls[0]++;var o=new JsonObject();o.addProperty("status","REPAIRED");return o;}};
        var controller=new LivingArmor(dir,host);var profile=new LivingArmor.Profile(2,true,true,true,KEY,"chest",20);controller.command(OWNER,"armor_configure",profile.json());controller.tick(OWNER,0);controller.tick(OWNER,19);check(calls[0]==1,"auto repair interval enforced");controller.tick(OWNER,20);check(calls[0]==2,"bounded next automatic cycle");
        var restart=new LivingArmor(dir,host);check(restart.profile(OWNER).equals(profile),"owner config survives restart");
        var repairArgs=new JsonObject();repairArgs.addProperty("slot","chest");repairArgs.addProperty("key",KEY);repairArgs.addProperty("preview",false);
        Files.writeString(dir.resolve(OWNER+".repair-pending"),"interrupted intent");denied(()->restart.command(OWNER,"armor_repair",repairArgs),"interrupted repair never replayed after restart");
        check(restart.status(OWNER).get("pendingRecovery").getAsBoolean(),"durable ambiguity exposed");denied(()->restart.command(OWNER,"armor_configure",profile.json()),"same version cannot silently rearm");
        var rearmed=new LivingArmor.Profile(3,true,true,true,KEY,"chest",20);restart.command(OWNER,"armor_configure",rearmed.json());check(!restart.status(OWNER).get("pendingRecovery").getAsBoolean(),"explicit higher configuration rearms after inspection");
        restart.command(OWNER,"armor_repair",repairArgs);check(!Files.exists(dir.resolve(OWNER+".repair-pending")),"successful repair clears intent");
        var sample=AssimilationTests.sample("{id:\"example:metal\",Count:1b,tag:{original:1}}",3);var ledger=new AssimilationLedger(()->{});ledger.deposit(OWNER,sample,3);String occupied=ledger.reserve(OWNER,sample.key(),2,"machine-stage3");
        var paidPort=new Port(){public String reserve(){return ledger.reserve(OWNER,sample.key(),1,"armor");}public void release(String t){ledger.release(OWNER,t);}public void commit(String t){check(ledger.commit(OWNER,t).payload().equals(sample.payload()),"repair consumes exact preserved assimilation payload");}};
        LivingArmor.repair(paidPort,false);check(ledger.available(OWNER,sample.key())==0&&ledger.reserved(OWNER,occupied).amount()==2&&paidPort.damage==55,"armor cannot spend machine reservations; real ledger conservation");
        denied(()->LivingArmor.repair(paidPort,false),"exhausted actual stock cannot repair");var ledgerRestart=AssimilationLedger.load(ledger.save(),()->{});check(ledgerRestart.reserved(OWNER,occupied).amount()==2&&ledgerRestart.available(OWNER,sample.key())==0,"repair debit and other reservations survive ledger restart");
        var file=dir.resolve(OWNER+".json");Files.writeString(file,"{}");denied(()->restart.command(OWNER,"armor_reload",new JsonObject()),"malformed reload rejected");check(restart.profile(OWNER).equals(rearmed),"last known good retained");
        var changed=new LivingArmor.Profile(3,false,false,false,"","head",20);denied(()->restart.command(OWNER,"armor_configure",changed.json()),"same version cannot replace");
        Files.writeString(file,"{}");var invalidRestart=new LivingArmor(dir,host);denied(()->invalidRestart.profile(OWNER),"startup does not replace corrupt profile with defaults");
        var bad=profile.json();bad.addProperty("owner","someone_else");denied(()->MachineTools.validate("armor_configure",bad),"owner injection denied by exact schema");
        for(String tool:LivingArmorTools.NAMES){check(MachineTools.NAMES.contains(tool)&&MachineTools.schema(tool).has("properties")&&!MachineTools.description(tool).isEmpty(),"armor tool fully catalogued "+tool);}
        Path world=Files.createTempDirectory("nanites-armor-service-");List<Runnable> queue=new ArrayList<>();boolean[] online={true};long[] clock={0};int[] repairs={0};
        var gameHost=new GameService.Host(){public void message(UUID id,String t){}public void particles(UUID id,String s,int r){}public void main(Runnable r){queue.add(r);}public boolean online(UUID id){return online[0];}public boolean runtimeAvailable(UUID id){return online[0];}public List<String> mods(){return List.of();}public String loader(){return "forge";}public long clock(){return clock[0];}public JsonObject armorRepair(UUID id,JsonObject a){repairs[0]++;var o=new JsonObject();o.addProperty("status","REPAIRED");return o;}};
        try(var game=new GameService(world,world.resolve("config"),gameHost)){
            game.login(OWNER);game.machineOperation(OWNER,"armor_configure",profile.json());check(game.machineSnapshot(OWNER).has("armor"),"AI live snapshot exposes armor");
            game.tick(OWNER,0);game.tick(OWNER,1);check(repairs[0]==1,"GameService ticks armor once per interval");
            var gateway=game.machineGateway(OWNER);var pending=gateway.execute("armor_status",new JsonObject());game.invalidateRuntime(OWNER);queue.remove(0).run();check(pending.isCompletedExceptionally(),"stale armor callback denied after lifecycle change");
            check(game.deposit(OWNER,()->{}),"real game balance funded by trusted intake");var rollback=game.repairPayment(OWNER,1000000);check(rollback!=null,"real repair debit succeeds");rollback.run();
            var budget=new ProtectionBudget();float n=budget.limit(OWNER,0,8);budget.consume(OWNER,0,n);check(budget.limit(OWNER,0,LivingArmor.reduction(4,8,10))==0,"adaptive/modules/runtime share exhausted budget");check(budget.limit(OWNER,20,2)==2,"next budget window resets");
            online[0]=false;denied(()->game.machineOperation(OWNER,"armor_repair",new JsonObject()),"offline owner cannot mutate armor");
            online[0]=true;game.logout(OWNER);Files.writeString(world.resolve("nanites/living-armor/"+OWNER+".json"),"{}");game.login(OWNER);
            check(game.machineSnapshot(OWNER).getAsJsonObject("armor").get("status").getAsString().equals("CONFIGURATION_ERROR"),"corrupt armor does not disable machine/autonomy snapshots");
            denied(()->game.armorProfile(OWNER),"corrupt armor remains fail closed without default reset");
        }
        System.out.println("LivingArmorTests: "+checks+" deterministic checks passed; no Minecraft/model/network");
    }
}
