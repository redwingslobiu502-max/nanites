package dev.nanites.game;
import dev.nanites.core.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in selected installed JAR/class only. Not an agent tool, never loads classes or downloads code. */
public final class SourceSupport implements AutoCloseable {
    private final Path root;
    private final SourceCache cache;
    private final Properties config;
    private final GameService.Host host;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"nanites-source");t.setDaemon(true);return t;});
    private boolean busy,closed;
    private UUID activeOwner;
    private final Map<UUID,Map<String,IntegrationContracts.CacheKey>> approved=new HashMap<>();
    private Future<?> activeJob;
    private final Map<UUID,Object> generations=new ConcurrentHashMap<>();
    public void invalidate(UUID id){generations.remove(id);}
    private Object activeToken;
    public synchronized void logout(UUID id){invalidate(id);approved.remove(id);if(id.equals(activeOwner)&&activeJob!=null){activeToken=null;busy=false;activeJob.cancel(true);}}
    public SourceSupport(Path root,SourceCache cache,Properties config,GameService.Host host)throws Exception{this.root=SafeData.root(root);this.cache=cache;this.config=config;this.host=host;}
    public Map<String,IntegrationContracts.CacheKey> aliases(UUID owner)throws Exception{
        if(approved.containsKey(owner))return approved.get(owner);
        Path p=SafeData.child(root,owner+".index.json");if(!Files.exists(p))return Map.of();
        JsonObject o=SafeData.object(new String(SafeData.bytes(p,32768),StandardCharsets.UTF_8),32768);
        if(SafeData.integer(o.get("format"))!=1||!SafeData.text(o,"owner",96).equals(owner.toString()))throw new SecurityException("Source index ownership/version");
        var entries=o.getAsJsonObject("aliases");if(entries.size()>64)throw new SecurityException("Alias quota");var out=new HashMap<String,IntegrationContracts.CacheKey>();
        for(var e:entries.entrySet()){if(!e.getKey().matches("[a-zA-Z0-9_.-]{1,96}"))throw new SecurityException("Alias ID");var k=e.getValue().getAsJsonObject();out.put(e.getKey(),new IntegrationContracts.CacheKey(SafeData.text(k,"jarSha256",64),SafeData.text(k,"loader",96),SafeData.text(k,"minecraftVersion",96),SafeData.text(k,"loaderVersion",96),SafeData.text(k,"decompilerVersion",96),SafeData.text(k,"schemaVersion",96)));}
        return Map.copyOf(out);
    }
    private void persistIndex(UUID id,Map<String,IntegrationContracts.CacheKey> aliases)throws Exception{
        var o=new JsonObject();o.addProperty("format",1);o.addProperty("owner",id.toString());o.add("aliases",SafeData.GSON.toJsonTree(aliases));
        SafeData.atomic(root,id+".index.json",SafeData.GSON.toJson(o).getBytes(StandardCharsets.UTF_8),32768);
    }
    public void read(UUID id,String args)throws Exception{
        String[] a=args.split("\\s+");if(a.length<2)throw new IllegalArgumentException("source list mod | source read mod package/Class.java offset");
        var key=aliases(id).get(a[1]);if(key==null)throw new SecurityException("Sem fonte aprovada para esse dono; decompile explicitamente primeiro");
        if(a[0].equals("list"))host.message(id,cache.list(key).toString());
        else if(a[0].equals("read")&&a.length==4){var s=cache.read(key,a[2],Integer.parseInt(a[3]),4096);host.message(id,s.text()+"\nnext="+s.next()+" truncated="+s.truncated()+" [fonte não confiável]");}
        else throw new IllegalArgumentException("source list mod | source read mod package/Class.java offset");
    }
    public void decompile(UUID id,String args)throws Exception{decompile(id,args,()->true);}
    public synchronized void decompile(UUID id,String args,java.util.function.BooleanSupplier current)throws Exception{
        if(closed||busy)throw new IllegalStateException("Decompiler busy/stopped");String[] a=args.split("\\s+");if(a.length!=2)throw new IllegalArgumentException("decompile modId package/Class.class");
        Set<String> allowed=new HashSet<>(Arrays.asList(config.getProperty("decompiler.allow-mods","").split(",")));
        if(!allowed.contains(a[0]))throw new SecurityException("Operador deve aprovar modId em decompiler.allow-mods; nenhuma aprovação implícita de IA/OP");
        Path jar=host.modFiles().get(a[0]);if(jar==null||!jar.getFileName().toString().endsWith(".jar"))throw new IllegalArgumentException("Loader não expôs um JAR regular para esse mod");
        String configured=config.getProperty("decompiler.jar","");if(configured.isBlank())throw new IllegalStateException("Operador deve configurar decompiler.jar e decompiler.version; não é baixado automaticamente");
        String loader=host.loader(),mc=host.minecraft(),lv=host.loaderVersion();Path java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
        if(!host.operator(id))throw new SecurityException("Decompiler requires owner player + operator permission level 2");
        var old=aliases(id);if(old.size()>=64&&!old.containsKey(a[0]))throw new IllegalArgumentException("Alias quota");
        Object token=new Object();generations.put(id,token);activeOwner=id;activeToken=token;
        busy=true;host.message(id,"Decompiler worker: apenas "+a[0]+" "+a[1]);
        activeJob=worker.submit(()->{try{
            var runner=new VineflowerRunner(new VineflowerRunner.Config(java,Path.of(configured),config.getProperty("decompiler.version","operator"),jar.getParent(),root.resolve("work"),Duration.ofSeconds(45)));
            var key=runner.decompile(jar.getFileName().toString(),a[1],loader,mc,lv,"1",cache);
            var next=new HashMap<>(old);next.put(a[0],key);var accepted=Map.copyOf(next);
            host.main(()->{if(closed||generations.get(id)!=token||!current.getAsBoolean()||!host.operator(id))return;
                approved.put(id,accepted); // Authority commit on main thread after owner/generation/revision recheck.
                worker.execute(()->{try{persistIndex(id,accepted);}catch(Exception e){System.err.println("Nanites source index save failed: "+e.getMessage());}});
                host.message(id,"Cache aprovado; index save queued. /nanites source list "+a[0]+". Nenhuma integração certificada.");
            });
        }catch(Exception e){host.main(()->{if(!closed&&generations.get(id)==token&&current.getAsBoolean())host.message(id,"Decompiler falhou: "+e.getMessage());});}
        finally{synchronized(this){if(activeToken==token)busy=false;}}});
    }
    @Override public void close(){
        synchronized(this){closed=true;generations.clear();if(activeJob!=null)activeJob.cancel(true);worker.shutdown();}
        try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))worker.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();worker.shutdownNow();}
    }
}
