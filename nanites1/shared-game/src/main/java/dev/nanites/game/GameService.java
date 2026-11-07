package dev.nanites.game;

import dev.nanites.core.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Called only by loader server thread. No player-supplied paths, URLs or Java execution. */
public final class GameService implements AutoCloseable {
    public interface Host {
        void message(UUID player, String text);
        void particles(UUID player, String shape, int radius);
        void main(Runnable task);
        boolean online(UUID player);
        default boolean autonomyReady(UUID player){return online(player);}
        List<String> mods();
        default Map<String,Path> modFiles(){return Map.of();}
        default boolean operator(UUID player){return false;}
        default boolean runtimeAvailable(UUID player){return false;}
        default void validateOverlay(UUID player,RuntimeInterventions.Profile p){throw new UnsupportedOperationException("Forge runtime unavailable");}
        default void overlay(UUID player,UUID modifier,RuntimeInterventions.Profile p){throw new UnsupportedOperationException("Forge runtime unavailable");}
        default void removeOverlay(UUID player,UUID modifier){}
        default void heal(UUID player,double hp){}
        default long clock(){return 0;}
        default String technology(UUID player){return "UNSUPPORTED";}
        default String loader(){return "test";}
        default String minecraft(){return "test";}
        default String loaderVersion(){return "test";}
        default ConstructionQueue.World constructionWorld(UUID player,Blueprint.Origin origin){throw new UnsupportedOperationException("Construção exige Forge 1.20.1");}
        default JsonObject worldObserve(UUID player,JsonObject args){throw new UnsupportedOperationException("World observation unavailable");}
        default JsonObject blockCatalog(String query,int offset,int limit){throw new UnsupportedOperationException("Registry unavailable");}
        default JsonObject machineOperation(UUID player,String tool,JsonObject args){throw new UnsupportedOperationException("Forge capabilities unavailable");}
        default JsonObject assimilationSnapshot(UUID player){return new JsonObject();}
        default JsonObject armorStatus(UUID player){return new JsonObject();}
        default JsonObject armorRepair(UUID player,JsonObject args){throw new UnsupportedOperationException("Forge equipment repair unavailable");}
        default LocalChatClient.Transport aiTransport(){return null;}
        default void validateTechnology(UUID player,JsonObject normalizedArgs){throw new UnsupportedOperationException("Technology capability validation unavailable");}
    }
    private ConstructionQueue construction;
    private void constructionAvailable(){if(!host.loader().equals("forge"))throw new UnsupportedOperationException("Construção apenas Forge 1.20.1");}
    public void serverTick(){if(!closed){if(construction!=null)construction.tick();if(objectives!=null)objectives.tick();if(adapters!=null)adapters.tick();}}
    private LivingArmor livingArmor;
    public LivingArmor.Profile armorProfile(UUID id){return livingArmor.profile(id);}
    public void armorTick(UUID id,long tick){if(!closed&&livingArmor!=null&&host.runtimeAvailable(id))livingArmor.tick(id,tick);}
    /** One synchronous repair debit; rollback is permitted only before material commit. */
    public Runnable repairPayment(UUID id,long cost){try{var before=session(id);if(!spend(id,cost))return null;return ()->sessions.put(id,before);}catch(Exception e){return null;}}
    private MachineAdapters adapters;
    private AutonomousGoals objectives;
    private final Set<UUID> manualConstruction=new HashSet<>();
    private final Map<UUID,MultiTurnAgent> autonomousAgents=new HashMap<>();
    public JsonObject machineSnapshot(UUID id){var snapshot=adapters.snapshot(id);snapshot.add("assimilation",host.assimilationSnapshot(id));if(livingArmor!=null)try{snapshot.add("armor",livingArmor.status(id));}catch(RuntimeException invalid){var error=new JsonObject();error.addProperty("status","CONFIGURATION_ERROR");error.addProperty("error",invalid.getMessage());snapshot.add("armor",error);}return snapshot;}
    public ControlledTools.ExtensionProvider machineLiveProvider(UUID id){final long generation=lifecycle.getOrDefault(id,0L);return ()->{CompletableFuture<JsonObject> result=new MainMutation();host.main(()->{synchronized(result){if(result.isDone())return;try{if(closed||!host.online(id)||generation!=lifecycle.getOrDefault(id,0L))throw new CancellationException("Owner lifecycle changed");result.complete(machineSnapshot(id));}catch(Exception e){result.completeExceptionally(e);}}});return result;};}
    public JsonObject machineOperation(UUID id,String name,JsonObject args) throws Exception {
        if(!host.loader().equals("forge"))throw new UnsupportedOperationException("Máquinas exigem Forge 1.20.1");
        if(!host.online(id))throw new IllegalStateException("Dono offline");MachineTools.validate(name,args);
        if(LivingArmorTools.NAMES.contains(name)){forge(id);return livingArmor.command(id,name,args);}
        if(ObjectiveTools.NAMES.contains(name))return objectives.command(id,name,args);
        if(name.equals("technology_assimilate")){
            var normalized=args.deepCopy();String source=args.get("source").getAsString();
            if(source.isEmpty()){var inspect=new JsonObject();inspect.add("target",args.get("target").deepCopy());inspect.addProperty("offset",0);inspect.addProperty("limit",1);var step=new JsonObject();step.addProperty("tool","machine_inspect");step.add("args",inspect);step.addProperty("repeat",1);var steps=new com.google.gson.JsonArray();steps.add(step);var recipe=new JsonObject();recipe.addProperty("version",1);recipe.add("steps",steps);source=recipe.toString();}
            MachineAdapters.validate(source);normalized.addProperty("source",source);host.validateTechnology(id,normalized);
            // Observe/validate loaded target before any file or ledger mutation.
            var scan=new JsonObject();scan.add("target",args.get("target").deepCopy());scan.addProperty("offset",0);scan.addProperty("limit",1);host.machineOperation(id,"technology_scan",scan);
            var create=new JsonObject();create.addProperty("project",args.get("project").getAsString());create.addProperty("name",args.get("name").getAsString());create.addProperty("source",source);
            var active=adapters.command(id,"adapter_create",create);
            try{var registered=host.machineOperation(id,name,normalized);registered.add("adapter",active);return registered;}
            catch(Exception e){throw new IllegalStateException("Adapter is ACTIVE, technology record failed (not ACID); rescan/retry: "+e.getMessage(),e);}
        }
        return MachineTools.ADAPTERS.contains(name)?adapters.command(id,name,args):host.machineOperation(id,name,args);
    }
    public ControlledTools.MutationGateway machineGateway(UUID id){
        final long generation=lifecycle.getOrDefault(id,0L);
        return (name,args)->{CompletableFuture<JsonObject> result=new MainMutation();host.main(()->{synchronized(result){if(result.isDone())return;try{if(closed||!host.online(id)||generation!=lifecycle.getOrDefault(id,0L))throw new CancellationException("Owner lifecycle changed");result.complete(machineOperation(id,name,args));}catch(Exception e){result.completeExceptionally(e);}}});return result;};
    }
    private int buildLimit(String key,int fallback,int max){int n=Integer.parseInt(config.getProperty(key,Integer.toString(fallback)));if(n<1||n>max)throw new IllegalArgumentException(key+" deve estar entre 1 e "+max);return n;}
    private void stopAgent(UUID id){lifecycle.merge(id,1L,Long::sum);var a=agents.remove(id);if(a!=null)a.close();}
    private JsonObject submitBlueprint(UUID id,String source){constructionAvailable();if(objectives!=null)objectives.yield(id);var out=construction.submit(id,source,o->host.constructionWorld(id,o));manualConstruction.add(id);return out;}
    private void objectiveCommand(UUID id,String prefix,String text) throws Exception {
        String[] p=text.trim().split("\\s+",2);String op=p[0],rest=p.length>1?p[1]:"";var args=new JsonObject();
        if(!op.equals("list")){
            String[] n=rest.split("\\s+",2);if(n[0].isEmpty())throw new IllegalArgumentException("Objective name required");args.addProperty("name",n[0]);rest=n.length>1?n[1]:"";
            if(Set.of("update","pause","resume","stop").contains(op)){String[] v=rest.split("\\s+",2);args.addProperty("version",Integer.parseInt(v[0]));rest=v.length>1?v[1]:"";}
            if(Set.of("create","update").contains(op))args.addProperty("source",rest);
            else if(!rest.isEmpty())throw new IllegalArgumentException("Unexpected objective arguments");
        }else if(!rest.isEmpty())throw new IllegalArgumentException("list has no arguments");
        host.message(id,machineOperation(id,prefix+"_"+op,args).toString());
    }
    private void buildCommand(UUID id,String arg) throws Exception {
        constructionAvailable();String[] p=arg.trim().split("\\s+",2);String value=p.length>1?p[1]:"";
        switch(p[0]){
            case "submit" -> {JsonObject receipt=submitBlueprint(id,value);stopAgent(id);host.message(id,receipt.toString());}
            case "status" -> host.message(id,construction.status(id).toString());
            case "cancel" -> {stopAgent(id);host.message(id,construction.cancel(id).toString());}
            case "limits" -> host.message(id,"build.blocks.per.tick="+config.getProperty("build.blocks.per.tick","64")+" build.max.voxels="+config.getProperty("build.max.voxels","32768")+" build.tick.micros="+config.getProperty("build.tick.micros","2000"));
            case "load" -> {if(!value.matches("[a-zA-Z0-9_-]{1,48}"))throw new IllegalArgumentException("Nome simples sem caminho");Path dir=path(id,value).getParent();Path f=dir.resolve(value+".blueprint.json");if(Files.isSymbolicLink(f)||Files.size(f)>4_000_000)throw new IllegalArgumentException("Blueprint inseguro ou maior que 4MB");JsonObject receipt=submitBlueprint(id,Files.readString(f,StandardCharsets.UTF_8));stopAgent(id);host.message(id,receipt.toString());}
            default -> throw new IllegalArgumentException("build submit JSON | load nome | status | cancel | limits");
        }
    }
    public ControlledTools.SessionProvider liveProvider(UUID id){
        long generation=lifecycle.getOrDefault(id,0L);
        return ()->{var result=new CompletableFuture<NaniteSession>();host.main(()->{synchronized(result){if(result.isDone())return;try{checkGeneration(id,generation);result.complete(session(id).fork(id.toString()));}catch(Exception e){result.completeExceptionally(e);}}});return result;};
    }
    private void checkGeneration(UUID id,long generation){if(closed||!host.online(id)||generation!=lifecycle.getOrDefault(id,0L))throw new ConcurrentModificationException("Rodada substituída/cancelada; peça novamente");}
    public ControlledTools.MutationGateway constructionGateway(UUID id){
        constructionAvailable();long generation=lifecycle.getOrDefault(id,0L);long[] expected={construction.revision(id)};
        return (name,args)->{var result=new MainMutation();host.main(()->{synchronized(result){if(result.isDone())return;try{
            checkGeneration(id,generation);JsonObject receipt;
            if(Set.of("submit_blueprint","cancel_construction").contains(name)&&expected[0]!=construction.revision(id))throw new ConcurrentModificationException("Plano mudou; peça nova rodada");
            receipt=switch(name){
                case "submit_blueprint" -> submitBlueprint(id,SafeData.text(args,"source",65536));
                case "cancel_construction" -> construction.cancel(id);
                case "construction_status" -> construction.status(id);
                case "world_observe" -> host.worldObserve(id,args);
                case "block_catalog" -> {int offset=Math.toIntExact(SafeData.integer(args.get("offset"))),limit=Math.toIntExact(SafeData.integer(args.get("limit")));if(offset<0||limit<1||limit>32)throw new IllegalArgumentException("offset >= 0; limit 1..32");yield host.blockCatalog(SafeData.text(args,"query",256),offset,limit);}
                default -> throw new IllegalArgumentException("Ferramenta de construção desconhecida");
            };
            if(Set.of("submit_blueprint","cancel_construction").contains(name)){expected[0]=construction.revision(id);host.message(id,"IA "+name+": "+receipt);}
            result.complete(receipt);
        }catch(Exception e){result.completeExceptionally(e);}}});return result;};
    }
    public static final long CAPACITY = 10_000_000_000_000L;
    public static final String DEFAULT = "program shield version v1;\nstate hits = 0;\non fall group owner { if (event.damage > 0) { add hits = 1; emit protect self = 20; emit shape.ring self = 2; } }\non impact group owner { if (event.damage > 0) { add hits = 1; emit protect self = 4; emit shape.ring self = 1; } }\n";
    public static final NaniteSession.Policy POLICY = new NaniteSession.Policy(Set.of("owner"), Set.of("fall","impact","tick"), Map.of("protect",new RuleEngine.WorldSpec(Set.of("self"),0,20),"protect.insulating",new RuleEngine.WorldSpec(Set.of("self"),0,20),"shape.ring",new RuleEngine.WorldSpec(Set.of("self"),1,4),"shape.column",new RuleEngine.WorldSpec(Set.of("self"),1,4)), new RuleEngine.Limits(4,32));
    private static NaniteSession.Policy runtimePolicy(){
        var world=new HashMap<>(POLICY.world());
        for(int i=0;i<8;i++)world.put("intervene.p"+i,new RuleEngine.WorldSpec(Set.of("self"),1,60));
        return new NaniteSession.Policy(POLICY.groups(),POLICY.events(),world,POLICY.limits());
    }
    private final Host host;
    private final RuntimeInterventions runtime;
    private final ProtectionBudget protection=new ProtectionBudget();
    private float protectionLimit(UUID id,float amount){return host.loader().equals("forge")?protection.limit(id,host.clock(),amount):Math.min(20,amount);}
    public boolean insulating(UUID id){try{return materials.insulating(id);}catch(Exception failure){return false;}}
    public float paidProtection(UUID id,float requested){float n=protectionLimit(id,requested);if(n>0&&spend(id,(long)Math.ceil(n*1_000_000))){protection.consume(id,host.clock(),n);return n;}return 0;}
    private final NaniteSession.Policy policy;
    private final Map<UUID,Long> runtimeRevision=new HashMap<>();
    private final Path scripts, stateRoot;
    private final SnapshotStore store;
    private final AsyncSnapshots snapshots;
    private final int maxPlayers;
    private final SourceCache cache;
    private final SourceSupport sources;
    private final Materials materials;
    private final Properties config = new Properties();
    private final Map<UUID,NaniteSession> sessions = new HashMap<>();
    private final Map<UUID,List<ControlledTools.Proposal>> proposals = new HashMap<>();
    private final Map<UUID,MultiTurnAgent> agents = new HashMap<>();
    private final Map<UUID,String> prepared = new HashMap<>();
    private final Map<UUID,Long> preparedBase = new HashMap<>();
    private final Map<UUID,String> previous = new HashMap<>();
    private final Map<UUID,Long> lifecycle = new HashMap<>();
    private boolean closed;
    public GameService(Path world, Path configDir, Host host) throws Exception {
        this.host=host;
        policy=host.loader().equals("forge")?runtimePolicy():POLICY;
        runtime=new RuntimeInterventions(new RuntimeInterventions.Backend(){
            public boolean available(UUID id){return !closed&&host.runtimeAvailable(id);}
            public void validate(UUID id,RuntimeInterventions.Profile p){host.validateOverlay(id,p);}
            public void overlay(UUID id,UUID modifier,RuntimeInterventions.Profile p){host.overlay(id,modifier,p);}
            public void remove(UUID id,UUID modifier){host.removeOverlay(id,modifier);}
            public void heal(UUID id,double hp){host.heal(id,hp);}
            private final Map<UUID,NaniteSession> before=new HashMap<>();
            public boolean pay(UUID id,long cost){before.put(id,sessions.get(id));if(spend(id,cost))return true;before.remove(id);return false;}
            public void refund(UUID id,long cost){var s=before.remove(id);if(s==null)throw new IllegalStateException("Missing activation reservation");sessions.put(id,s);}
            public void settled(UUID id){before.remove(id);}
        });
        stateRoot=world.resolve("nanites"); store=new SnapshotStore(stateRoot);
        adapters=new MachineAdapters(stateRoot.resolve("adapters"),(id,tool,args)->host.machineOperation(id,tool,args));
        if(host.loader().equals("forge"))livingArmor=new LivingArmor(stateRoot.resolve("living-armor"),new LivingArmor.Host(){
            public JsonObject status(UUID id){return host.armorStatus(id);}
            public JsonObject repair(UUID id,JsonObject args){return host.armorRepair(id,args);}
        });
        Path root=SafeData.root(configDir.resolve("nanites"));
        scripts=root.resolve("scripts"); Files.createDirectories(scripts);
        cache=new SourceCache(root.resolve("source-cache"));
        Path conf=root.resolve("server.properties");
        if(!Files.exists(conf)) Files.writeString(conf,"# Operator-controlled. Restart server after editing. TLS always verified.\nai.model=REQUIRED\nai.address=https://127.0.0.1:8081/v1\n# Optional explicit operator token; never auto-discovered.\nai.token=\nmax.players=128\nmaterials.insulating=minecraft:white_wool\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        if(Files.isSymbolicLink(conf)||Files.size(conf)>16384) throw new SecurityException("Unsafe config");
        try(var r=Files.newBufferedReader(conf,StandardCharsets.UTF_8)){config.load(r);}
        sources=new SourceSupport(root.resolve("source-index"),cache,config,host);
        materials=new Materials(stateRoot,config);
        int limit;try{limit=Integer.parseInt(config.getProperty("max.players","128"));}catch(NumberFormatException e){limit=128;}
        maxPlayers=Math.max(1,Math.min(128,limit));
        if(host.loader().equals("forge"))construction=new ConstructionQueue(buildLimit("build.blocks.per.tick",64,65536),buildLimit("build.max.voxels",32768,1_000_000),buildLimit("build.tick.micros",2000,50000)*1000L);
        snapshots=new AsyncSnapshots(store,(id,text)->host.main(()->{host.message(id,text);System.err.println("Nanites: "+text);}));
        if(host.loader().equals("forge"))objectives=new AutonomousGoals(stateRoot.resolve("objectives"),new AutonomousGoals.Host(){
            public boolean online(UUID id){return !closed&&host.autonomyReady(id);}
            public boolean manualBusy(UUID id){return agents.containsKey(id)||(manualConstruction.contains(id)&&construction!=null&&Set.of("QUEUED","RUNNING","WAITING_CHUNK").contains(construction.status(id).get("status").getAsString()));}
            public boolean modelAvailable(){String m=config.getProperty("ai.model","REQUIRED");return !m.isBlank()&&!m.equals("REQUIRED");}
            public void main(Runnable task){host.main(task);}
            public JsonObject execute(UUID id,String tool,JsonObject args) throws Exception {return machineOperation(id,tool,args);}
            public CompletableFuture<String> round(UUID id,String prompt,JsonObject spec,int budget,java.util.function.BooleanSupplier valid,java.util.function.BiConsumer<String,JsonObject> receipt) throws Exception {return autonomousRound(id,prompt,spec,budget,valid,receipt);}
        },adapters);
    }
    private NaniteSession session(UUID id) throws Exception {
        if(closed) throw new IllegalStateException("Stopped");
        NaniteSession existing=sessions.get(id); if(existing!=null)return existing;
        if(sessions.size()>=maxPlayers)throw new IllegalStateException("Player budget");
        String owner=id.toString(); NaniteSession s=snapshots.pending(id);
        Path file=stateRoot.resolve(owner+".snapshot.json");
        if(s==null){if(Files.exists(file))s=NaniteSession.restore(store.read(owner),owner,policy);
            else s=new NaniteSession(owner,policy,List.of(new Ledger.Reservoir("storage","generic",CAPACITY),new Ledger.Reservoir("spent","consumed",CAPACITY)),Map.of("storage",0L,"spent",0L),"default.nano",DEFAULT);}
        validateGameSnapshot(s,id);
        materials.read(id); // Load once at session start; event/tick protection uses memory only.
        sessions.put(id,s); return s;
    }
    private void validateGameSnapshot(NaniteSession s,UUID id){
        var saved=s.save(id.toString());
        var required=List.of(new Ledger.Reservoir("spent","consumed",CAPACITY),new Ledger.Reservoir("storage","generic",CAPACITY));
        if(!new HashSet<>(saved.reservoirs()).equals(new HashSet<>(required))||!saved.queued().isEmpty())
            throw new SecurityException("Snapshot incompatible with real game reservoirs/event boundary");
    }
    /** Trusted intake/spend boundary. Deposits add only a consumed, server-registered box; spending moves into spent. */
    private NaniteSession balance(NaniteSession s,UUID id,long intake,long cost) {
        return s.fundedCopy(id.toString(),intake,cost);
    }
    private final Set<UUID> depositing=new HashSet<>();
    public boolean deposit(UUID id,Runnable consume) {
        if(!depositing.add(id))return false;
        try {var next=balance(session(id),id,Ledger.NANITES_PER_BOX,0); consume.run(); sessions.put(id,next);
            try{persist(id);}catch(RuntimeException e){host.message(id,"Caixa já consumida e saldo em memória; salvar pendente: "+e.getMessage());}
            host.message(id,"Caixa consumida: +500_000_000_000. /nanites status");return true;}
        catch(Exception e){host.message(id,"Depósito: "+e.getMessage());return false;}
        finally{depositing.remove(id);}
    }
    private void persist(UUID id) {snapshots.submit(id,sessions.get(id));}
    public boolean spend(UUID id,long cost){try{sessions.put(id,balance(session(id),id,0,cost));return true;}catch(Exception e){return false;}}
    public void invalidateRuntime(UUID id){if(objectives!=null)objectives.logout(id);adapters.forget(id);if(construction!=null)construction.cancel(id);runtime.forget(id);runtimeRevision.merge(id,1L,Long::sum);lifecycle.merge(id,1L,Long::sum);var a=agents.remove(id);if(a!=null)a.close();}
    private void forge(UUID id){if(!host.loader().equals("forge")||!host.runtimeAvailable(id))throw new UnsupportedOperationException("Forge living self target required");}
    private static RuntimeInterventions.Profile parseProfile(String[] parts,int offset){if(parts.length!=offset+6)throw new IllegalArgumentException("speed attack armor knockback guard regen required");return new RuntimeInterventions.Profile(Double.parseDouble(parts[offset]),Double.parseDouble(parts[offset+1]),Double.parseDouble(parts[offset+2]),Double.parseDouble(parts[offset+3]),Double.parseDouble(parts[offset+4]),Double.parseDouble(parts[offset+5]));}
    private void runtimeCommand(UUID id,String arg){forge(id);String[] p=arg.trim().split("\\s+");switch(p[0]){
        case "define" -> runtime.define(id,p[1],parseProfile(p,2));
        case "start" -> {if(p.length!=3)throw new IllegalArgumentException("start p0 seconds");runtime.start(id,p[1],Integer.parseInt(p[2]),host.clock());}
        case "cancel" -> runtime.cancel(id);
        case "list" -> {host.message(id,"Profiles="+runtime.profiles(id)+" lease="+runtime.lease(id));return;}
        default -> throw new IllegalArgumentException("runtime define p0 speed attack armor knockback guard regen | start p0 seconds | cancel | list");
    }runtimeRevision.merge(id,1L,Long::sum);host.message(id,"Runtime atualizado; custo antecipado, efeitos temporários.");}
    public void absorb(UUID id,String registry,Runnable consume){try{session(id);host.message(id,materials.absorb(id,registry,consume));}catch(Exception e){host.message(id,"Absorção rejeitada: "+e.getMessage());}}
    public float damage(UUID id,String type,float amount,double distance){return damage(id,type,amount,distance,false);}
    public float damage(UUID id,String type,float amount,double distance,boolean fire) {
        if(!Float.isFinite(amount)||amount<=0||!sessions.containsKey(id))return amount;
        try {var intents=event(id,type,Map.of("damage",(long)Math.ceil(amount*100),"distance",(long)Math.max(0,Math.min(4096,distance*100)),"fire",fire?1L:0L));
            long protect=intents.stream().filter(a->a.capability().startsWith("protect")).mapToLong(RuleEngine.WorldAction::value).sum();
            float blocked=protectionLimit(id,Math.min(amount,protect));if(host.loader().equals("forge"))protection.consume(id,host.clock(),blocked);
            return Math.max(0,amount-blocked);
        }catch(Exception e){host.message(id,"Regra rejeitada (sem proteção): "+e.getMessage());return amount;}
    }
    private List<RuleEngine.WorldAction> event(UUID id,String event,Map<String,Long> fields) throws Exception {
        var real=session(id);var copy=real.fork(id.toString());
        copy.enqueue(id.toString(),copy.revision(),new RuleEngine.Event(event,"owner",fields));
        var result=copy.tick(id.toString(),copy.revision());
        if(!result.result().committed())throw new IllegalArgumentException(result.result().error());
        if(result.intents().size()>8)throw new IllegalArgumentException("World action quota 8");
        var rb=real.snapshot(id.toString()).ledger().balances();var cb=copy.snapshot(id.toString()).ledger().balances();
        if(cb.get("spent")<rb.get("spent")||cb.get("storage")>rb.get("storage"))throw new SecurityException("Spent is irreversible; scripts cannot recycle consumed resources");
        long cost=0;
        long interventionCount=result.intents().stream().filter(a->a.capability().startsWith("intervene.")).count();
        if(interventionCount>1||interventionCount>0&&runtime.lease(id)!=null)throw new IllegalStateException("One nonstacking intervention per event/owner");
        for(var a:result.intents()){
            if(a.capability().startsWith("intervene.")){forge(id);host.validateOverlay(id,runtime.profile(id,a.capability().substring(10)));continue;}
            if(!Set.of("protect","protect.insulating","shape.ring","shape.column").contains(a.capability()))throw new SecurityException("Unsupported typed action");
            if(a.capability().equals("protect.insulating")&&(!materials.insulating(id)||fields.getOrDefault("fire",0L)!=1))throw new SecurityException("Isolante exige material absorvido mapeado e evento de fogo");
            if(host.loader().equals("forge")&&a.capability().startsWith("protect")){
                if(!Set.of("fall","impact").contains(event))throw new SecurityException("Protection requires a damage event");
                if(fields.getOrDefault("fire",0L)==1&&(!a.capability().equals("protect.insulating")||!materials.insulating(id)))throw new SecurityException("Fire shield requires explicit insulating action and absorbed material");
            }else cost=Math.addExact(cost,Math.multiplyExact(a.value(),a.capability().startsWith("protect")?1_000_000L:100_000L));
        }
        if(host.loader().equals("forge")){
            long requested=result.intents().stream().filter(a->a.capability().startsWith("protect")).mapToLong(RuleEngine.WorldAction::value).sum();
            float bounded=protectionLimit(id,Math.min(requested,fields.getOrDefault("damage",0L)/100f));
            cost=Math.addExact(cost,(long)Math.ceil(bounded*1_000_000));
        }
        if(cost>0)copy=balance(copy,id,0,cost);
        sessions.put(id,copy); // Activation transaction: restore script/accounting if an overlay cannot commit.
        try{for(var a:result.intents())if(a.capability().startsWith("intervene.")){runtime.start(id,a.capability().substring(10),(int)a.value(),host.clock());runtimeRevision.merge(id,1L,Long::sum);}}
        catch(RuntimeException failure){sessions.put(id,real);throw failure;}
        for(var a:result.intents())if(Set.of("shape.ring","shape.column").contains(a.capability()))try{host.particles(id,a.capability(),(int)a.value());}catch(RuntimeException e){System.err.println("Nanites VFX failed after accounting commit: "+e.getMessage());}
        return result.intents();
    }
    public void login(UUID id){try{session(id);}catch(Exception e){host.message(id,"Nanites desativado para este dono: "+e.getMessage());}}
    public float runtimeDamage(UUID id,float amount){return runtimeDamage(id,amount,false);}
    public float runtimeDamage(UUID id,float amount,boolean fire){runtime.tick(id,host.clock());if(!Float.isFinite(amount)||amount<=0||fire&&!insulating(id))return amount;float allowed=protectionLimit(id,amount);float blocked=allowed-runtime.damage(id,allowed);protection.consume(id,host.clock(),blocked);return Math.max(0,amount-blocked);}
    public void tick(UUID id,long tick) {
        try{armorTick(id,tick);}catch(RuntimeException invalid){if(tick%200==0)host.message(id,"Living armor stalled: "+invalid.getMessage());}
        runtime.tick(id,host.clock());
        if(tick%20!=0||!sessions.containsKey(id))return;
        try{if(sessions.get(id).handles("tick"))event(id,"tick",Map.of("age",tick));if(tick%100==0)persist(id);}catch(Exception e){host.message(id,"Tick rejeitado: "+e.getMessage());}
    }
    private Path path(UUID id,String name) throws Exception {
        if(!name.matches("[a-zA-Z0-9_-]{1,48}"))throw new SecurityException("Nome simples sem caminho");
        if(Files.isSymbolicLink(scripts))throw new SecurityException("Symlink scripts denied");
        Path dir=scripts.resolve(id.toString());Files.createDirectories(dir);
        if(Files.isSymbolicLink(dir))throw new SecurityException("Symlink owner denied");
        Path p=dir.resolve(name+".nano");if(Files.isSymbolicLink(p))throw new SecurityException("Symlink file denied");return p;
    }
    private String read(UUID id,String name) throws Exception {Path p=path(id,name);if(Files.size(p)>65536)throw new IllegalArgumentException("Script 64KiB quota");return Files.readString(p,StandardCharsets.UTF_8);}
    private RuleEngine.Migration migration(NaniteSession s,UUID id,String code) {
        String from=s.save(id.toString()).schema(),to=ScriptLanguage.compile("candidate.nano",code).schema().version();
        return new RuleEngine.Migration(from,to,Map.of(),Set.of());
    }
    private void apply(UUID id,String code) throws Exception {
        var s=session(id).fork(id.toString()); var m=migration(s,id,code);
        var preview=s.preview(id.toString(),s.revision(),"candidate.nano",code,m,List.of(new RuleEngine.Event("fall","owner",Map.of("damage",800L,"distance",1000L))));
        if(!preview.result().committed())throw new IllegalArgumentException(preview.result().error());
        String before=s.save(id.toString()).source();
        s.stage(id.toString(),s.revision(),"candidate.nano",code,m);
        var t=s.tick(id.toString(),s.revision());if(!t.result().committed())throw new IllegalArgumentException(t.result().error());
        sessions.put(id,s);previous.put(id,before);
        try{persist(id);}catch(RuntimeException failure){System.err.println("Nanites committed hot-swap pending save: "+failure.getMessage());}
        host.message(id,"Hot-swap ativo; revisão "+s.revision()+". Ativação não executa intents de preview.");
    }
    public void command(UUID id,String input) {
        try {
            var s=session(id);String[] split=input.trim().split("\\s+",2);String op=split[0],arg=split.length>1?split[1]:"";
            if(Set.of("edit","load","apply","rollback","runtime","revoke").contains(op))stopAgent(id);
            if(Set.of("armor_configure","armor_reload","armor_repair").contains(op)||op.equals("armor")&&!arg.equals("status")){stopAgent(id);if(objectives!=null)objectives.yield(id);}
            if(MachineTools.NAMES.contains(op)){
                if(objectives!=null&&!ObjectiveTools.NAMES.contains(op)&&!Set.of("machine_inspect","scan_machines","adapter_list","adapter_status","technology_list","material_stock","material_inspect","recipe_search","technology_scan","armor_status").contains(op))objectives.yield(id);
                host.message(id,machineOperation(id,op,SafeData.object(arg,80000)).toString());return;
            }
            switch(op){
                case "machine_help" -> {host.message(id,"/nanites <machine_inspect|scan_machines|transfer_items|transfer_energy|transfer_fluid|machine_control|machine_recover|adapter_create|adapter_load|adapter_execute|adapter_list|adapter_status|adapter_cancel> JSON; /nanites machine_schema ferramenta");host.message(id,MachineAdapters.contract());}
                case "machine_schema" -> host.message(id,MachineTools.schema(arg).toString());
                case "objective","factory" -> objectiveCommand(id,op,arg);
                case "adapter" -> {String[] a=arg.split("\\s+",4);if(a.length!=4||!a[0].equals("define"))throw new IllegalArgumentException("adapter define project name JSON");if(objectives!=null)objectives.yield(id);var data=new JsonObject();data.addProperty("project",a[1]);data.addProperty("name",a[2]);data.addProperty("source",a[3]);host.message(id,machineOperation(id,"adapter_create",data).toString());}
                case "build" -> buildCommand(id,arg);
                case "ai_cancel" -> {stopAgent(id);host.message(id,"Rodada IA cancelada; construção já submetida continua, use /nanites build cancel para parar blocos.");}
                case "observe" -> {constructionAvailable();host.message(id,host.worldObserve(id,SafeData.object(arg,2048)).toString());}
                case "blocks" -> {constructionAvailable();String[] a=arg.split("\\s+",3);if(a.length!=3)throw new IllegalArgumentException("blocks query offset limit (query * para todos)");host.message(id,host.blockCatalog(a[0].equals("*")?"":a[0],Integer.parseInt(a[1]),Integer.parseInt(a[2])).toString());}
                case "status" -> host.message(id,"Dono="+id+" rev="+s.revision()+" "+s.snapshot(id.toString())+(host.loader().equals("forge")?"; Forge runtime/modular/Extremis self; /nanites runtime list; sem energia criada.":"; apenas escudo + estruturas de partículas, sem energia."));
                case "export" -> {Files.writeString(path(id,arg),s.save(id.toString()).source(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);host.message(id,"Exportado scripts/"+id+"/"+arg+".nano");}
                case "example" -> {Files.writeString(path(id,arg),DEFAULT,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);host.message(id,"Exemplo criado scripts/"+id+"/"+arg+".nano");}
                case "edit" -> {ScriptLanguage.compile("command.nano",arg);prepared.put(id,arg);preparedBase.remove(id);host.message(id,"Texto validado; /nanites apply para aplicar.");}
                case "load" -> {String code=read(id,arg);apply(id,code);}
                case "apply" -> {String code=prepared.get(id);if(code==null)throw new IllegalStateException("Sem texto preparado");Long base=preparedBase.get(id);if(base!=null&&base!=s.revision())throw new ConcurrentModificationException("Proposta obsoleta; peça nova proposta");apply(id,code);prepared.remove(id);preparedBase.remove(id);}
                case "preview" -> {String code=read(id,arg);var p=s.preview(id.toString(),s.revision(),arg+".nano",code,migration(s,id,code),List.of(new RuleEngine.Event("fall","owner",Map.of("damage",800L,"distance",1000L))));host.message(id,p.result()+" intents="+p.intents()+" (não aplicados, custo conferido no evento real)");}
                case "rollback" -> {String before=previous.get(id);if(before==null)throw new IllegalStateException("Sem código anterior nesta sessão de servidor");apply(id,before);host.message(id,"Código anterior reativado; recursos não reembolsados.");}
                case "save" -> {persist(id);host.message(id,"Snapshot agendado no worker; aguarde e confira erros no log. Saída normal faz flush.");}
                case "mods" -> host.message(id,String.join(", ",host.mods()));
                case "reactor" -> host.message(id,"UNSUPPORTED: nenhum adapter de energia foi instalado/verificado. Nenhuma energia criada.");
                case "source" -> sources.read(id,arg);
                case "decompile" -> {long generation=lifecycle.getOrDefault(id,0L),revision=s.revision();sources.decompile(id,arg,()->current(id,generation,revision));}
                case "armor" -> {String[] a=arg.split("\\s+",2);String tool="armor_"+a[0];JsonObject args=a.length==2?SafeData.object(a[1],8192,512):new JsonObject();host.message(id,machineOperation(id,tool,args).toString());}
                case "runtime" -> runtimeCommand(id,arg);
                case "extremis" -> {forge(id);if(arg.equals("off")){runtime.opt(id,false);runtimeRevision.merge(id,1L,Long::sum);}else{String[] a=arg.split("\\s+");if(a.length!=2||!a[0].equals("on"))throw new IllegalArgumentException("extremis on seconds | off");runtime.opt(id,true);runtime.start(id,"extremis",Integer.parseInt(a[1]),host.clock());runtimeRevision.merge(id,1L,Long::sum);}host.message(id,"Extremis: lease opt-in; desfazer não retira HP curado.");}
                case "tech" -> {forge(id);host.message(id,host.technology(id));}
                case "grant" -> {forge(id);String[] a=arg.split("\\s+");if(a.length!=4||!a[0].equals("self"))throw new IllegalArgumentException("grant self seconds calls budget");var g=runtime.grant(id,Integer.parseInt(a[1]),Integer.parseInt(a[2]),Long.parseLong(a[3]),host.clock());runtimeRevision.merge(id,1L,Long::sum);host.message(id,"Grant delegado explícito self: "+g+"; /nanites revoke para revogar. IA não pode criar grants.");}
                case "revoke" -> {runtime.revoke(id);runtimeRevision.merge(id,1L,Long::sum);host.message(id,"Grant revogado e lease removida.");}
                case "ai" -> ai(id,arg);
                case "proposal" -> {var list=proposals.getOrDefault(id,List.of());int i=Integer.parseInt(arg);var p=list.get(i);if(p.baseRevision()!=s.revision())throw new ConcurrentModificationException("Proposta obsoleta");prepared.put(id,p.source());preparedBase.put(id,p.baseRevision());host.message(id,p.source()+"\nRevisão base="+p.baseRevision()+". Revisar; /nanites apply. Não aplicado automaticamente.");}
                case "material" -> host.message(id,"Mapa explícito isolante="+config.getProperty("materials.insulating")+"; registro="+materials.read(id)+"; segure material e /nanites absorb. IA não certifica materiais.");
                default -> host.message(id,"/nanites status|example nome|export nome|edit texto|apply|load nome|preview nome|rollback|save|mods|reactor|material|ai pergunta|proposal índice|machine_help|machine_schema ferramenta");
            }
        }catch(Exception e){host.message(id,"Nanites erro: "+e.getMessage());}
    }
    private static final class MainMutation extends CompletableFuture<JsonObject> {
        @Override public synchronized boolean cancel(boolean interrupt){return super.cancel(interrupt);}
    }
    /** Captures owner, lifecycle, grant token and optimistic revisions; main-thread receipts advance only this execution. */
    public ControlledTools.MutationGateway delegatedGateway(UUID id) throws Exception {
        forge(id);var grant=runtime.grant(id,host.clock());if(grant==null)throw new SecurityException("Explicit active owner grant required");
        final long token=grant.serial(),generation=lifecycle.getOrDefault(id,0L);
        final long[] expected={session(id).revision(),runtimeRevision.getOrDefault(id,0L)};
        return (name,args)->{
            CompletableFuture<JsonObject> result=new MainMutation();
            host.main(()->{synchronized(result){
                if(result.isDone())return;
                try{
                    forge(id);var s=session(id);
                    if(!current(id,generation,expected[0])||runtimeRevision.getOrDefault(id,0L)!=expected[1])throw new ConcurrentModificationException("Execution revision conflict; request fresh run");
                    long price=1_000_000L;
                    String profile=null,source=null;RuntimeInterventions.Profile definition=null;int seconds=0;
                    switch(name){
                        case "define_profile" -> {profile=SafeData.text(args,"profile",2);definition=new RuntimeInterventions.Profile(args.get("speed").getAsDouble(),args.get("attack").getAsDouble(),args.get("armor").getAsDouble(),args.get("knockback").getAsDouble(),args.get("guard").getAsDouble(),args.get("regen").getAsDouble());if(!profile.matches("p[0-7]")||runtime.lease(id)!=null)throw new IllegalArgumentException("Profile slot/active lease");}
                        case "start_intervention" -> {profile=SafeData.text(args,"profile",2);seconds=Math.toIntExact(SafeData.integer(args.get("seconds")));definition=runtime.profile(id,profile);price=RuntimeInterventions.cost(definition,seconds);if(runtime.lease(id)!=null)throw new IllegalStateException("Lease already active");host.validateOverlay(id,definition);}
                        case "cancel_intervention" -> price=0;
                        case "apply_script" -> {source=SafeData.text(args,"source",ScriptLanguage.MAX_SOURCE);if(source.matches("(?s).*\\b(?:emit|transfer)\\b.*"))throw new SecurityException("Delegated apply_script is state-only in this base; use start_intervention for bounded world effects");}
                        default -> throw new SecurityException("Unknown mutation");
                    }
                    if(name.equals("start_intervention")){var activeGrant=runtime.grant(id,host.clock());if(activeGrant==null||host.clock()+seconds*20L>activeGrant.end())throw new SecurityException("Lease exceeds remaining grant TTL");}
                    runtime.authorize(id,token,host.clock(),price);
                    var before=session(id);
                    try{
                        if(!name.equals("start_intervention")&&price>0&&!spend(id,price))throw new IllegalStateException("Insufficient nanites");
                        switch(name){case "define_profile" -> runtime.define(id,profile,definition);case "start_intervention" -> runtime.start(id,profile,seconds,host.clock());case "cancel_intervention" -> runtime.cancel(id);case "apply_script" -> apply(id,source);default -> throw new SecurityException();}
                    }catch(Exception failure){sessions.put(id,before);throw failure;} // Failed attempts still consume grant call/budget, but not owner resources.
                    runtimeRevision.merge(id,1L,Long::sum);expected[0]=session(id).revision();expected[1]=runtimeRevision.get(id);
                    JsonObject receipt=new JsonObject();receipt.addProperty("status","EXECUTED_SERVER_MAIN");receipt.addProperty("revision",expected[0]);receipt.addProperty("runtime_revision",expected[1]);receipt.addProperty("charged",price);receipt.add("profiles",SafeData.GSON.toJsonTree(runtime.profiles(id)));host.message(id,"IA executou "+name+"; custo="+price+" rev="+expected[0]+" runtime="+expected[1]);result.complete(receipt);
                }catch(Exception e){result.completeExceptionally(e);}
            }});return result;
        };
    }
    private LocalChatClient.Transport sharedChatTransport;
    private LocalChatClient.Transport chatTransport(){if(sharedChatTransport==null){sharedChatTransport=host.aiTransport();if(sharedChatTransport==null)sharedChatTransport=new LocalChatClient.JvmTransport(config.getProperty("ai.token",""));}return sharedChatTransport;}
    private void ai(UUID id,String prompt) throws Exception {
        String model=config.getProperty("ai.model","REQUIRED");if(model.equals("REQUIRED")||model.isBlank())throw new IllegalStateException("Operador deve configurar ai.model em config/nanites/server.properties e reiniciar");
        if(objectives!=null)objectives.yield(id);
        if(agents.size()+autonomousAgents.size()>=2&&!agents.containsKey(id))throw new IllegalStateException("AI ocupada");
        stopAgent(id); // New request supersedes old responses and queued tool callbacks, not already placed blocks.
        JsonObject catalog=new JsonObject();catalog.addProperty("mods",String.join(",",host.mods()));catalog.addProperty("world","protect self 0..20 custo=1000000/ponto; shape.ring/shape.column self 1..4 custo=100000/unidade; events fall/impact damage=centésimos HP distance=centésimos bloco; tick age=ticks; group owner; reservoirs storage,spent. Nenhuma integração externa verificada.");
        // Detached snapshot: AI tools never read mutable player state or activate code.
        var detached=session(id).fork(id.toString());
        long baseRevision=detached.revision();
        catalog.add("installed_mods",SafeData.GSON.toJsonTree(host.mods()));
        boolean delegated=host.loader().equals("forge")&&runtime.grant(id,host.clock())!=null;
        var allowed=new HashSet<>(ControlledTools.NAMES);
        if(delegated){allowed.addAll(ControlledTools.MUTATIONS);catalog.addProperty("runtime","self only; define_profile p0..p7 speed 0..0.1 attack 0..1 armor 0..2 knockback 0..0.1 guard 0..4 regen 0..1; start_intervention seconds 1..60 within grant TTL. apply_script state-only (no emit); world effects via bounded leases. Cost 1000000 + seconds*max(1,ceil((speed*100+attack+armor+knockback*100+guard+regen)*1000000)); one lease; cancel before redefining. Receipts advance execution revisions; grant cannot be created by AI.");catalog.add("profiles",SafeData.GSON.toJsonTree(runtime.profiles(id)));}
        boolean building=host.loader().equals("forge");
        if(building){allowed.addAll(ControlledTools.WORLD_TOOLS);catalog.addProperty("construction","Real setBlock server tick queue. Any registered installed mod blockstate namespace:id[prop=value], no NBT. submit_blueprint source JSON: origin {dimension,x,y,z} absolute; voxels [{x,y,z,block}] relative; optional cuboids [{from:{x,y,z},to:{x,y,z},block}] inclusive solid, spheres [{center:{x,y,z},radius,block}] solid integer radius 0..128, lines [{from:{x,y,z},to:{x,y,z},block}] rounded DDA. Process order voxels,cuboids,spheres,lines; last overlapping value wins; overlap counts toward size limit. One active job/owner; validate fully before replacing, no rollback; unloaded chunks WAITING_CHUNK; build max="+config.getProperty("build.max.voxels","32768")+". QUEUED receipt is not completion. Inspect world_observe (empty dimension=current) for player origin; block_catalog paginated; construction_status live progress. Construction does not need grant or balance; runtime grants unchanged.");catalog.add("construction_status",construction.status(id));}
        if(building){allowed.addAll(MachineTools.NAMES);catalog.addProperty("machines",MachineTools.description("machine_inspect"));catalog.addProperty("adapter_recipe",MachineAdapters.contract());catalog.addProperty("assimilation",AssimilationTools.description("assimilate_material")+" "+AssimilationTools.description("technology_assimilate")+" All eight stage2 tools have strict schemas; stock/technology/recipe pages are bounded; continue next_offset. SavedData is not crash ACID.");catalog.add("machine_live",machineSnapshot(id));}
        var machineGateway=building?machineGateway(id):null;
        var worldGateway=building?constructionGateway(id):null;
        var runtimeGateway=delegated?delegatedGateway(id):null;
        ControlledTools.MutationGateway gateway=(name,args)->MachineTools.NAMES.contains(name)?machineGateway.execute(name,args):ControlledTools.WORLD_TOOLS.contains(name)?worldGateway.execute(name,args):runtimeGateway.execute(name,args);
        var tools=new ControlledTools(cache,sources.aliases(id),catalog,detached,id.toString(),allowed,building||delegated?gateway:null).live(liveProvider(id));
        if(building)tools.extensions(machineLiveProvider(id));
        var client=new LocalChatClient(new LocalChatClient.Config(IntegrationContracts.LOCAL_AI,model,Duration.ofSeconds(45),1024,131072,1),chatTransport());
        var agent=new MultiTurnAgent(client);
        long generation=lifecycle.getOrDefault(id,0L);
        agents.put(id,agent);host.message(id,building?"IA pode construir blocos reais pelas ferramentas; /nanites build status|cancel. Nova pergunta substitui rodada antiga.":delegated?"AI worker com grant delegado: recibos de execução.":"AI worker manual: proposta não executa código.");
        agent.run(prompt,tools,new MultiTurnAgent.Limits(8,24,building?MultiTurnAgent.catalogBudget(client,tools.definitions(),8):30000,65536),trace->{if(trace.kind().equals("tool_result"))host.main(()->{if(!closed&&generation==lifecycle.getOrDefault(id,0L)&&host.online(id))host.message(id,"IA ferramenta "+trace.id()+": "+trace.data().substring(0,Math.min(1200,trace.data().length())));});}).whenComplete((out,error)->{
            agent.close();client.close();
            host.main(()->{
                agents.remove(id,agent);
                if(building||delegated?closed||!host.online(id)||generation!=lifecycle.getOrDefault(id,0L):!current(id,generation,baseRevision)){if(!closed&&host.online(id))host.message(id,"AI resultado descartado: dono/geração/revisão mudou; solicite novamente.");return;}
                if(error!=null)host.message(id,"AI falhou (TLS preservado): "+error.getMessage()+"; plano já submetido pode continuar, /nanites build status ou build cancel.");else{proposals.put(id,out.proposals());host.message(id,out.text()+"; propostas="+out.proposals().size()+(building?". Blocos confirmados pelo servidor: /nanites build status; cancelar restante: /nanites build cancel.":delegated?". Execuções confirmadas somente pelos recibos do servidor; /nanites runtime list, revoke.":". /nanites proposal 0 para revisar"));}
            });
        });
    }
    private JsonObject autonomySnapshot(UUID id){
        var out=machineSnapshot(id);
        for(String name:List.of("technology_list","material_stock","recipe_search")){
            var args=new JsonObject();args.addProperty("offset",0);args.addProperty("limit",2);
            if(name.equals("recipe_search")){args.addProperty("query","");args.addProperty("type","");}
            try{var page=host.machineOperation(id,name,args);String text=page.toString();if(text.length()>2000){var bounded=new JsonObject();bounded.addProperty("excerpt",text.substring(0,2000));bounded.addProperty("truncated",true);bounded.addProperty("paging","Use the explicit paginated tool with a relevant query");if(page.has("next_offset"))bounded.add("next_offset",page.get("next_offset").deepCopy());out.add(name,bounded);}else out.add(name,page);}catch(Exception error){var unavailable=new JsonObject();unavailable.addProperty("error",error.getClass().getSimpleName()+": "+error.getMessage());out.add(name,unavailable);}
        }
        return out;
    }
    /** Same real function protocol as manual AI, with a distinct goal generation and cancellation domain. */
    private CompletableFuture<String> autonomousRound(UUID id,String prompt,JsonObject spec,int budget,java.util.function.BooleanSupplier valid,java.util.function.BiConsumer<String,JsonObject> receipt) throws Exception {
        if(agents.size()+autonomousAgents.size()>=2)throw new IllegalStateException("AI global singleflight budget occupied");
        var catalog=new JsonObject();catalog.add("installed_mods",SafeData.GSON.toJsonTree(host.mods()));
        catalog.addProperty("adapter_recipe",MachineAdapters.contract());catalog.addProperty("objectives",ObjectiveTools.description("objective_create"));
        catalog.add("boundaries",spec.deepCopy());catalog.add("machine_live",autonomySnapshot(id));
        catalog.addProperty("assimilation","Live technology registry, materials stocks, traits and recipes in machine_live.assimilation; use paginated technology_registry/material_stock/recipe tools for remaining metadata. No private machine start API.");
        var allowed=new HashSet<>(ControlledTools.NAMES);allowed.addAll(MachineTools.NAMES);allowed.addAll(ControlledTools.WORLD_TOOLS);
        allowed.remove("adapter_execute");allowed.remove("adapter_cancel");allowed.remove("cancel_construction");
        // Factory objectives may observe/repair owner equipment, not change or rearm the owner's persistent armor policy.
        allowed.remove("armor_configure");allowed.remove("armor_reload");
        ControlledTools.MutationGateway gateway=(name,args)->{
            CompletableFuture<JsonObject> result=new MainMutation();host.main(()->{synchronized(result){if(result.isDone())return;boolean attempted=false;try{
                if(closed||!valid.getAsBoolean())throw new CancellationException("Objective lifecycle changed");
                AutonomousGoals.scopeTool(spec,name,args);
                if(name.equals("adapter_load")){var r=adapters.recipeSource(id,args.get("project").getAsString(),args.get("name").getAsString());var a=args.deepCopy();a.addProperty("source",r.toString());AutonomousGoals.scopeTool(spec,"adapter_create",a);}
                boolean mutation=Set.of("transfer_items","transfer_energy","transfer_fluid","machine_control","machine_recover","submit_blueprint","assimilate_material","recover_material","armor_repair","armor_configure","armor_reload").contains(name);
                if(mutation){receipt.accept("intent:"+name,args);attempted=true;}
                JsonObject out;
                if(MachineTools.NAMES.contains(name))out=machineOperation(id,name,args);
                else out=switch(name){case "submit_blueprint" -> {manualConstruction.remove(id);yield construction.submit(id,SafeData.text(args,"source",65536),o->host.constructionWorld(id,o));}case "construction_status" -> construction.status(id);case "world_observe" -> host.worldObserve(id,args);case "block_catalog" -> host.blockCatalog(SafeData.text(args,"query",256),Math.toIntExact(SafeData.integer(args.get("offset"))),Math.toIntExact(SafeData.integer(args.get("limit"))));default -> throw new SecurityException("Autonomous world tool denied");};
                receipt.accept(name,out);result.complete(out);
            }catch(Exception e){if(attempted){var failure=new JsonObject();failure.addProperty("error",e.getClass().getSimpleName()+": "+e.getMessage());receipt.accept("uncertain",failure);}result.completeExceptionally(e);}}});return result;
        };
        var tools=new ControlledTools(cache,sources.aliases(id),catalog,session(id).fork(id.toString()),id.toString(),allowed,gateway);
        tools.live(()->{var result=new CompletableFuture<NaniteSession>();host.main(()->{synchronized(result){if(result.isDone())return;try{if(!valid.getAsBoolean())throw new CancellationException();result.complete(session(id).fork(id.toString()));}catch(Exception e){result.completeExceptionally(e);}}});return result;});
        tools.extensions(()->{var result=new MainMutation();host.main(()->{synchronized(result){if(result.isDone())return;try{if(!valid.getAsBoolean())throw new CancellationException();result.complete(autonomySnapshot(id));}catch(Exception e){result.completeExceptionally(e);}}});return result;});
        var client=new LocalChatClient(new LocalChatClient.Config(IntegrationContracts.LOCAL_AI,config.getProperty("ai.model"),Duration.ofSeconds(45),1024,131072,1),chatTransport());
        var agent=new MultiTurnAgent(client);autonomousAgents.put(id,agent);
        var result=new CompletableFuture<String>();
        result.whenComplete((out,error)->{tools.cancelPending();agent.close();client.close();host.main(()->autonomousAgents.remove(id,agent));});
        try{
            var execution=agent.run(prompt,tools,new MultiTurnAgent.Limits(8,budget,MultiTurnAgent.catalogBudget(client,tools.definitions(),8),65536),trace->{});
            execution.whenComplete((out,error)->{if(error!=null)result.completeExceptionally(error);else result.complete(out.text());});
        }catch(Exception failure){result.completeExceptionally(failure);}
        return result;
    }
    private boolean current(UUID id,long generation,long revision){
        var s=sessions.get(id);return !closed&&host.online(id)&&generation==lifecycle.getOrDefault(id,0L)&&s!=null&&s.revision()==revision;
    }
    public void logout(UUID id){if(livingArmor!=null)livingArmor.forget(id);if(objectives!=null)objectives.logout(id);adapters.forget(id);if(construction!=null)construction.forget(id);runtime.forget(id);runtimeRevision.remove(id);lifecycle.put(id,Math.incrementExact(lifecycle.getOrDefault(id,0L)));sources.logout(id);
        boolean queued=true;try{if(sessions.containsKey(id))persist(id);}catch(Exception e){queued=false;System.err.println("Nanites save failure: "+e.getMessage());}
        var a=agents.remove(id);if(a!=null)a.close();proposals.remove(id);prepared.remove(id);preparedBase.remove(id);previous.remove(id);
        if(queued){sessions.remove(id);materials.forget(id);}
    }
    @Override public void close(){if(closed)return;if(objectives!=null)objectives.close();for(var a:List.copyOf(autonomousAgents.values()))a.close();autonomousAgents.clear();closed=true;sources.close();for(var id:List.copyOf(sessions.keySet()))logout(id);snapshots.close();sessions.clear();manualConstruction.clear();sharedChatTransport=null;}
}
