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
        List<String> mods();
        default Map<String,Path> modFiles(){return Map.of();}
        default boolean operator(UUID player){return false;}
        default String loader(){return "test";}
        default String minecraft(){return "test";}
        default String loaderVersion(){return "test";}
    }
    public static final long CAPACITY = 10_000_000_000_000L;
    public static final String DEFAULT = "program shield version v1;\nstate hits = 0;\non fall group owner { if (event.damage > 0) { add hits = 1; emit protect self = 20; emit shape.ring self = 2; } }\non impact group owner { if (event.damage > 0) { add hits = 1; emit protect self = 4; emit shape.ring self = 1; } }\n";
    public static final NaniteSession.Policy POLICY = new NaniteSession.Policy(Set.of("owner"), Set.of("fall","impact","tick"), Map.of("protect",new RuleEngine.WorldSpec(Set.of("self"),0,20),"protect.insulating",new RuleEngine.WorldSpec(Set.of("self"),0,20),"shape.ring",new RuleEngine.WorldSpec(Set.of("self"),1,4),"shape.column",new RuleEngine.WorldSpec(Set.of("self"),1,4)), new RuleEngine.Limits(4,32));
    private final Host host;
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
        stateRoot=world.resolve("nanites"); store=new SnapshotStore(stateRoot);
        Path root=SafeData.root(configDir.resolve("nanites"));
        scripts=root.resolve("scripts"); Files.createDirectories(scripts);
        cache=new SourceCache(root.resolve("source-cache"));
        Path conf=root.resolve("server.properties");
        if(!Files.exists(conf)) Files.writeString(conf,"# Operator-controlled. Restart server after editing. TLS always verified.\nai.model=REQUIRED\nai.address=https://127.0.0.1:8081/v1\n# Optional explicit operator token; never auto-discovered.\nai.token=\nmax.players=128\nmaterials.insulating=minecraft:white_wool\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        if(Files.isSymbolicLink(conf)||Files.size(conf)>16384) throw new SecurityException("Unsafe config");
        try(var r=Files.newBufferedReader(conf,StandardCharsets.UTF_8)){config.load(r);}
        if(!config.getProperty("ai.address",IntegrationContracts.LOCAL_AI.toString()).equals(IntegrationContracts.LOCAL_AI.toString())) throw new SecurityException("Only exact loopback HTTPS address permitted");
        sources=new SourceSupport(root.resolve("source-index"),cache,config,host);
        materials=new Materials(stateRoot,config);
        int limit;try{limit=Integer.parseInt(config.getProperty("max.players","128"));}catch(NumberFormatException e){limit=128;}
        maxPlayers=Math.max(1,Math.min(128,limit));
        snapshots=new AsyncSnapshots(store,(id,text)->host.main(()->{host.message(id,text);System.err.println("Nanites: "+text);}));
    }
    private NaniteSession session(UUID id) throws Exception {
        if(closed) throw new IllegalStateException("Stopped");
        NaniteSession existing=sessions.get(id); if(existing!=null)return existing;
        if(sessions.size()>=maxPlayers)throw new IllegalStateException("Player budget");
        String owner=id.toString(); NaniteSession s=snapshots.pending(id);
        Path file=stateRoot.resolve(owner+".snapshot.json");
        if(s==null){if(Files.exists(file))s=NaniteSession.restore(store.read(owner),owner,POLICY);
            else s=new NaniteSession(owner,POLICY,List.of(new Ledger.Reservoir("storage","generic",CAPACITY),new Ledger.Reservoir("spent","consumed",CAPACITY)),Map.of("storage",0L,"spent",0L),"default.nano",DEFAULT);}
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
    public void absorb(UUID id,String registry,Runnable consume){try{session(id);host.message(id,materials.absorb(id,registry,consume));}catch(Exception e){host.message(id,"Absorção rejeitada: "+e.getMessage());}}
    public float damage(UUID id,String type,float amount,double distance){return damage(id,type,amount,distance,false);}
    public float damage(UUID id,String type,float amount,double distance,boolean fire) {
        if(!Float.isFinite(amount)||amount<=0||!sessions.containsKey(id))return amount;
        try {var intents=event(id,type,Map.of("damage",(long)Math.ceil(amount*100),"distance",(long)Math.max(0,Math.min(4096,distance*100)),"fire",fire?1L:0L));
            long protect=intents.stream().filter(a->a.capability().startsWith("protect")).mapToLong(RuleEngine.WorldAction::value).sum();
            return Math.max(0,amount-Math.min(20,protect));
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
        for(var a:result.intents()){
            if(a.capability().equals("protect.insulating")&&(!materials.insulating(id)||fields.getOrDefault("fire",0L)!=1))throw new SecurityException("Isolante exige material absorvido mapeado e evento de fogo");
            cost=Math.addExact(cost,Math.multiplyExact(a.value(),a.capability().startsWith("protect")?1_000_000L:100_000L));
        }
        if(cost>0)copy=balance(copy,id,0,cost);
        sessions.put(id,copy); // Main-thread accounting commit precedes bounded non-block-changing effects.
        for(var a:result.intents())if(!a.capability().startsWith("protect"))try{host.particles(id,a.capability(),(int)a.value());}catch(RuntimeException e){System.err.println("Nanites VFX failed after accounting commit: "+e.getMessage());}
        return result.intents();
    }
    public void login(UUID id){try{session(id);}catch(Exception e){host.message(id,"Nanites desativado para este dono: "+e.getMessage());}}
    public void tick(UUID id,long tick) {
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
        var s=session(id); var m=migration(s,id,code);
        var preview=s.preview(id.toString(),s.revision(),"candidate.nano",code,m,List.of(new RuleEngine.Event("fall","owner",Map.of("damage",800L,"distance",1000L))));
        if(!preview.result().committed())throw new IllegalArgumentException(preview.result().error());
        String before=s.save(id.toString()).source();
        s.stage(id.toString(),s.revision(),"candidate.nano",code,m);
        var t=s.tick(id.toString(),s.revision());if(!t.result().committed())throw new IllegalArgumentException(t.result().error());
        previous.put(id,before);
        persist(id);host.message(id,"Hot-swap ativo; revisão "+s.revision()+". Ativação não executa intents de preview.");
    }
    public void command(UUID id,String input) {
        try {
            var s=session(id);String[] split=input.trim().split("\\s+",2);String op=split[0],arg=split.length>1?split[1]:"";
            switch(op){
                case "status" -> host.message(id,"Dono="+id+" rev="+s.revision()+" "+s.snapshot(id.toString())+"; apenas escudo + estruturas de partículas, sem energia.");
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
                case "ai" -> ai(id,arg);
                case "proposal" -> {var list=proposals.getOrDefault(id,List.of());int i=Integer.parseInt(arg);var p=list.get(i);if(p.baseRevision()!=s.revision())throw new ConcurrentModificationException("Proposta obsoleta");prepared.put(id,p.source());preparedBase.put(id,p.baseRevision());host.message(id,p.source()+"\nRevisão base="+p.baseRevision()+". Revisar; /nanites apply. Não aplicado automaticamente.");}
                case "material" -> host.message(id,"Mapa explícito isolante="+config.getProperty("materials.insulating")+"; registro="+materials.read(id)+"; segure material e /nanites absorb. IA não certifica materiais.");
                default -> host.message(id,"/nanites status|example nome|export nome|edit texto|apply|load nome|preview nome|rollback|save|mods|reactor|material|ai pergunta|proposal índice");
            }
        }catch(Exception e){host.message(id,"Nanites erro: "+e.getMessage());}
    }
    private void ai(UUID id,String prompt) throws Exception {
        String model=config.getProperty("ai.model","REQUIRED");if(model.equals("REQUIRED")||model.isBlank())throw new IllegalStateException("Operador deve configurar ai.model em config/nanites/server.properties e reiniciar");
        if(agents.size()>=2||agents.containsKey(id))throw new IllegalStateException("AI ocupada");
        JsonObject catalog=new JsonObject();catalog.addProperty("mods",String.join(",",host.mods()));catalog.addProperty("world","protect self 0..20 custo=1000000/ponto; shape.ring/shape.column self 1..4 custo=100000/unidade; events fall/impact damage=centésimos HP distance=centésimos bloco; tick age=ticks; group owner; reservoirs storage,spent. Nenhuma integração externa verificada.");
        // Detached snapshot: AI tools never read mutable player state or activate code.
        var detached=session(id).fork(id.toString());
        long baseRevision=detached.revision();
        catalog.add("installed_mods",SafeData.GSON.toJsonTree(host.mods()));
        var tools=new ControlledTools(cache,sources.aliases(id),catalog,detached,id.toString(),ControlledTools.NAMES);
        var client=new LocalChatClient(new LocalChatClient.Config(IntegrationContracts.LOCAL_AI,model,Duration.ofSeconds(45),1024,131072,1),new LocalChatClient.JvmTransport(config.getProperty("ai.token","")));
        var agent=new MultiTurnAgent(client);
        long generation=lifecycle.getOrDefault(id,0L);
        agents.put(id,agent);host.message(id,"AI worker iniciado; proposta não executa código.");
        agent.run(prompt,tools,new MultiTurnAgent.Limits(6,16,30000,65536),trace->{}).whenComplete((out,error)->{
            agent.close();client.close();
            host.main(()->{
                agents.remove(id,agent);
                if(!current(id,generation,baseRevision)){if(!closed&&host.online(id))host.message(id,"AI resultado descartado: dono/geração/revisão mudou; solicite novamente.");return;}
                if(error!=null)host.message(id,"AI falhou (TLS preservado): "+error.getMessage());else{proposals.put(id,out.proposals());host.message(id,out.text()+"; propostas="+out.proposals().size()+". /nanites proposal 0 para revisar");}
            });
        });
    }
    private boolean current(UUID id,long generation,long revision){
        var s=sessions.get(id);return !closed&&host.online(id)&&generation==lifecycle.getOrDefault(id,0L)&&s!=null&&s.revision()==revision;
    }
    public void logout(UUID id){lifecycle.put(id,Math.incrementExact(lifecycle.getOrDefault(id,0L)));sources.logout(id);
        boolean queued=true;try{if(sessions.containsKey(id))persist(id);}catch(Exception e){queued=false;System.err.println("Nanites save failure: "+e.getMessage());}
        var a=agents.remove(id);if(a!=null)a.close();proposals.remove(id);prepared.remove(id);preparedBase.remove(id);previous.remove(id);
        if(queued){sessions.remove(id);materials.forget(id);}
    }
    @Override public void close(){closed=true;sources.close();for(var id:List.copyOf(sessions.keySet()))logout(id);snapshots.close();sessions.clear();}
}
