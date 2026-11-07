package dev.nanites.game;
import java.nio.file.*;
import java.util.*;
public final class GameTests {
    private static int passed;
    private static void check(boolean b,String name){if(!b)throw new AssertionError(name);passed++;}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("nanites-game-tests-");UUID id=UUID.randomUUID();List<String> log=new java.util.concurrent.CopyOnWriteArrayList<>();int[] particles={0};
        var host=new GameService.Host(){public void message(UUID p,String t){log.add(t);}public void particles(UUID p,String shape,int radius){particles[0]++;}public void main(Runnable r){r.run();}public boolean online(UUID p){return true;}public List<String> mods(){return List.of("test@1");}};
        var g=new GameService(root.resolve("world"),root.resolve("config"),host);
        check(g.damage(id,"fall",8,10)==8,"unfunded no shield");check(particles[0]==0,"unfunded no particles");
        int[] consumed={0};check(g.deposit(id,()->consumed[0]++),"deposit "+log);check(consumed[0]==1,"consume once");
        check(g.damage(id,"fall",8,10)==0,"funded fall shield");check(particles[0]==1,"visible effect called");
        check(g.damage(id,"impact",8,0)==4,"impact capped 4");
        g.command(id,"status");check(log.get(log.size()-1).contains("spent=24300000"),"exact spend conservation");
        g.command(id,"example test");g.command(id,"preview test");check(log.get(log.size()-1).contains("committed=true"),"preview");
        g.command(id,"edit program inert version v1; state hits = 0;");g.command(id,"apply");check(g.damage(id,"fall",8,10)==8,"hot swap inert");
        g.command(id,"rollback");check(g.damage(id,"fall",8,10)==0,"rollback restores code not balance");
        g.command(id,"edit program theft version v1; state hits = 0; on fall group owner { transfer spent storage = 100; emit protect self = 20; }");g.command(id,"apply");check(g.damage(id,"fall",8,10)==8,"spent cannot be reclaimed");
        g.command(id,"edit program evil version v1; state hits = 0; on fall group owner { emit explode other = 20; }");g.command(id,"apply");check(log.get(log.size()-1).startsWith("Nanites erro:"),"unsafe world actions rejected");
        g.command(id,"load ../escape");check(log.get(log.size()-1).contains("Nome simples"),"traversal denied");
        g.command(id,"ai hello");check(log.get(log.size()-1).contains("ai.model"),"AI model required no network");
        g.command(id,"edit program insulation version v1; state hits = 0; on impact group owner { if (event.fire == 1) { emit protect.insulating self = 3; } }");g.command(id,"apply");
        check(g.damage(id,"impact",4,0,true)==4,"fire protection requires material");
        int before=consumed[0];g.absorb(id,"minecraft:stone",()->consumed[0]++);check(consumed[0]==before,"unmapped material not consumed");
        g.absorb(id,"minecraft:white_wool",()->consumed[0]++);check(consumed[0]==before+1,"mapped material consumed");
        check(g.damage(id,"impact",4,0,true)==1,"verified mapped fire shield real reduction");
        check(g.damage(id,"impact",4,0,false)==4,"insulation only fire");
        g.command(id,"decompile random package/Class.class");check(log.get(log.size()-1).contains("aprovar modId"),"decompiler denied by default");
        g.command(id,"save");g.close();
        var restored=new GameService(root.resolve("world"),root.resolve("config"),host);restored.command(id,"status");check(log.get(log.size()-1).contains("insulation"),"world owner script restored");restored.close();
        review(root,host,log);
        System.out.println("GameTests: "+passed+" checks passed. "+root);
    }
    @SuppressWarnings("unchecked")
    private static <T> T field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(target);}
    private static void review(Path root,GameService.Host host,List<String> log)throws Exception{
        UUID id=UUID.randomUUID();Path world=root.resolve("review-world"),config=root.resolve("review-config");
        var g=new GameService(world,config,host);g.login(id);
        Map<UUID,dev.nanites.core.NaniteSession> sessions=field(g,"sessions");
        var initial=sessions.get(id).save(id.toString());g.tick(id,20);
        check(sessions.get(id).revision()==initial.revision(),"idle tick does not stale revision");
        check(!g.deposit(id,()->{throw new IllegalStateException("Empty stack");}),"failed consume rejected");
        check(sessions.get(id).save(id.toString()).equals(initial),"failed consume no credit");
        boolean[] nested={true};check(g.deposit(id,()->nested[0]=g.deposit(id,()->{})),"outer deposit committed");
        check(!nested[0]&&sessions.get(id).snapshot(id.toString()).ledger().total()==dev.nanites.core.Ledger.NANITES_PER_BOX,"reentrant deposit cannot duplicate");
        g.command(id,"edit program timer version v1; state hits = 0; on tick group owner { emit protect self = 20; }");g.command(id,"apply");
        for(int i=1;i<=10;i++)g.tick(id,i*20);
        check(g.damage(id,"fall",8,8)==8,"tick protection not accumulated for later fall");
        check(sessions.get(id).snapshot(id.toString()).ledger().balances().get("spent")==200_000_000L,"tick protection charged every event");
        g.command(id,"edit program budget version v1; state hits = 0; on fall group owner { repeat(256) { repeat(256) { if(0) {} } } emit protect self = 20; }");g.command(id,"apply");
        check(log.get(log.size()-1).contains("budget"),"game fuel failure cannot activate");
        var prop=new dev.nanites.core.ControlledTools.Proposal("p.nano","program inert version v1; state hits = 0;",sessions.get(id).revision(),"review");
        Map<UUID,List<dev.nanites.core.ControlledTools.Proposal>> proposals=field(g,"proposals");proposals.put(id,List.of(prop));
        g.command(id,"proposal 0");g.tick(id,220);var before=sessions.get(id).save(id.toString());g.command(id,"apply");
        check(log.get(log.size()-1).contains("obsoleta")&&sessions.get(id).save(id.toString()).equals(before),"stale prepared proposal blocked at apply");
        g.command(id,"proposal 0");check(log.get(log.size()-1).contains("obsoleta"),"stale proposal cannot prepare");
        var current=GameService.class.getDeclaredMethod("current",UUID.class,long.class,long.class);current.setAccessible(true);
        Map<UUID,Long> generations=field(g,"lifecycle");long generation=generations.getOrDefault(id,0L);
        check((Boolean)current.invoke(g,id,generation,sessions.get(id).revision()),"current owner revision and generation accepted after manual reload");
        check(!(Boolean)current.invoke(g,id,generation,sessions.get(id).revision()-1),"stale worker result revision rejected");
        check(!(Boolean)current.invoke(g,id,generation-1,sessions.get(id).revision()),"manual changes invalidate old worker generation");
        g.logout(id);check(!sessions.containsKey(id),"logout evicts resident session");g.login(id);
        check(sessions.get(id).save(id.toString()).equals(before),"immediate relog restores latest queued/durable state");
        check(!(Boolean)current.invoke(g,id,generation,sessions.get(id).revision()),"old owner generation rejected after relog");
        g.command(id,"rollback");check(log.get(log.size()-1).contains("Sem código anterior"),"logout clears rollback memory");g.close();
        var restored=new GameService(world,config,host);restored.login(id);
        Map<UUID,dev.nanites.core.NaniteSession> restoredSessions=field(restored,"sessions");
        check(restoredSessions.get(id).save(id.toString()).equals(before),"normal restart exact state and ledger");restored.close();
        Properties props=new Properties();props.setProperty("materials.insulating","minecraft:white_wool");
        var material=new Materials(root.resolve("review-material"),props);material.absorb(id,"minecraft:white_wool",()->{});
        Files.delete(root.resolve("review-material/materials/"+id+".json"));
        check(material.insulating(id),"material events use cached evidence not per-hit IO");
        var copy=material.read(id);copy.addProperty("count",0);check(material.insulating(id),"material caller cannot mutate cached evidence");
        props.setProperty("materials.insulating","minecraft:black_wool");check(!material.insulating(id),"mapping change invalidates old evidence");
        material.absorb(id,"minecraft:black_wool",()->{});check(material.read(id).get("count").getAsInt()==1,"new material does not inherit old count");
        var store=new dev.nanites.core.SnapshotStore(root.resolve("review-async"));var writer=new AsyncSnapshots(store,(p,t)->{throw new AssertionError(t);});
        var s=new dev.nanites.core.NaniteSession(id.toString(),GameService.POLICY,List.of(new dev.nanites.core.Ledger.Reservoir("storage","generic",GameService.CAPACITY),new dev.nanites.core.Ledger.Reservoir("spent","consumed",GameService.CAPACITY)),Map.of("storage",0L,"spent",0L),"default.nano",GameService.DEFAULT);
        synchronized(store){writer.submit(id,s);s=s.fundedCopy(id.toString(),100,10);writer.submit(id,s);
            check(writer.pending(id).snapshot(id.toString()).ledger().total()==100,"nonblocking submit/coalesce while disk store locked");}
        writer.close();check(store.read(id.toString()).equals(s.save(id.toString())),"ordered writer flushes newest snapshot");
        Files.writeString(config.resolve("nanites/server.properties"),"max.players=not-a-number\n");
        var defaults=new GameService(root.resolve("review-defaults"),config,host);defaults.login(id);defaults.command(id,"ai hello");
        check(log.get(log.size()-1).contains("ai.model"),"missing AI address/model and invalid player config fail safe");defaults.close();
        var failingHost=new GameService.Host(){public void message(UUID p,String t){}public void particles(UUID p,String shape,int radius){throw new IllegalStateException("VFX failure");}public void main(Runnable r){r.run();}public boolean online(UUID p){return true;}public List<String> mods(){return List.of();}};
        var vfx=new GameService(root.resolve("review-vfx"),config,failingHost);vfx.deposit(id,()->{});
        check(vfx.damage(id,"fall",8,8)==0,"VFX exception cannot remove committed damage protection");vfx.close();
        for(String example:List.of("game-shield","game-structure","game-insulating")){
            var module=dev.nanites.core.ScriptLanguage.compile(example+".nano",Files.readString(Path.of("../examples/"+example+".nano")));
            new dev.nanites.core.NaniteSession(id.toString(),GameService.POLICY,s.save(id.toString()).reservoirs(),s.save(id.toString()).balances(),module.file(),module.source());
            check(true,"example compiles against game policy "+example);
        }
    }
}
