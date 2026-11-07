package dev.nanites.game;

import com.google.gson.*;
import dev.nanites.core.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic policy and reloadable per-owner profiles. Loader owns real equipment and stock. */
public final class LivingArmor {
    public record Profile(int version,boolean enabled,boolean adaptive,boolean autoRepair,String repairKey,String repairSlot,int intervalTicks){
        public Profile {if(version<1||version>1000000||intervalTicks<20||intervalTicks>72000||!Set.of("head","chest","legs","feet","mainhand","offhand").contains(repairSlot)||(!repairKey.isEmpty()&&!repairKey.matches("[0-9a-f]{64}"))||(autoRepair&&repairKey.isEmpty()))throw new IllegalArgumentException("Invalid living armor profile/key");}
        public static Profile defaults(){return new Profile(1,true,true,false,"","chest",100);}
        public JsonObject json(){var o=new JsonObject();o.addProperty("version",version);o.addProperty("enabled",enabled);o.addProperty("adaptive",adaptive);o.addProperty("autoRepair",autoRepair);o.addProperty("repairKey",repairKey);o.addProperty("repairSlot",repairSlot);o.addProperty("intervalTicks",intervalTicks);return o;}
        public static Profile parse(JsonObject o){MachineTools.validate("armor_configure",o);return new Profile(o.get("version").getAsInt(),o.get("enabled").getAsBoolean(),o.get("adaptive").getAsBoolean(),o.get("autoRepair").getAsBoolean(),o.get("repairKey").getAsString(),o.get("repairSlot").getAsString(),o.get("intervalTicks").getAsInt());}
    }
    public interface Host {JsonObject status(UUID owner);JsonObject repair(UUID owner,JsonObject args);}
    private final Path root;private final Host host;
    private final Map<UUID,Profile> profiles=new HashMap<>();private final Map<UUID,Long> next=new HashMap<>();private final Map<UUID,JsonObject> receipts=new HashMap<>();
    public LivingArmor(Path root,Host host)throws Exception {this.root=SafeData.root(root);this.host=host;}
    private Path file(UUID id){return root.resolve(id+".json");}
    public Profile profile(UUID id){var p=profiles.get(id);if(p!=null)return p;try{p=Files.exists(file(id))?read(id):Profile.defaults();profiles.put(id,p);return p;}catch(Exception e){throw new IllegalStateException("Armor configuration invalid; refusing default reset",e);}}
    private Profile read(UUID id)throws Exception {var f=file(id);if(Files.isSymbolicLink(f)||Files.size(f)>8192)throw new IllegalArgumentException("Unsafe armor profile");return Profile.parse(SafeData.object(Files.readString(f,StandardCharsets.UTF_8),8192,512));}
    private void candidate(UUID id,Profile p){var old=profiles.get(id);if(old!=null&&(p.version()<old.version()||(p.version()==old.version()&&!p.equals(old))))throw new IllegalArgumentException("Changed armor profile requires increasing version");}
    public JsonObject command(UUID id,String tool,JsonObject args)throws Exception {
        MachineTools.validate(tool,args);
        if(tool.equals("armor_configure")){var old=profile(id);var p=Profile.parse(args);candidate(id,p);if(Files.exists(pending(id))&&p.version()<=old.version())throw new IllegalArgumentException("Rearming an ambiguous repair requires a higher profile version after inspection");var f=file(id);if(Files.isSymbolicLink(f))throw new SecurityException("Symlink armor profile");var tmp=Files.createTempFile(root,"armor-",".tmp");try{Files.writeString(tmp,p.json().toString(),StandardCharsets.UTF_8);Files.move(tmp,f,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}profiles.put(id,p);Files.deleteIfExists(pending(id));next.remove(id);}
        else if(tool.equals("armor_reload")){var p=read(id);candidate(id,p);profiles.put(id,p);next.remove(id);}
        else if(tool.equals("armor_repair")){var receipt=repairOwner(id,args);if(!args.get("preview").getAsBoolean())receipts.put(id,receipt.deepCopy());return receipt;}
        else if(!tool.equals("armor_status"))throw new IllegalArgumentException("Unknown armor tool");
        return status(id);
    }
    private Path pending(UUID id){return root.resolve(id+".repair-pending");}
    private JsonObject repairOwner(UUID id,JsonObject args){
        boolean preview=args.get("preview").getAsBoolean();
        if(!preview&&Files.exists(pending(id)))throw new IllegalStateException("AMBIGUOUS repair pending: inspect equipment/stock, then configure a higher profile version to rearm");
        try{
            if(!preview)Files.writeString(pending(id),args.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            try{var result=host.repair(id,args);if(!preview)Files.delete(pending(id));return result;}
            catch(RuntimeException failure){if(!preview&&!(failure instanceof AmbiguousRepairException))Files.deleteIfExists(pending(id));throw failure;}
        }catch(java.io.IOException e){throw new IllegalStateException("Armor repair journal I/O failure; inspect before retry",e);}
    }
    public JsonObject status(UUID id){var o=host.status(id);o.add("configuration",profile(id).json());o.addProperty("pendingRecovery",Files.exists(pending(id)));if(receipts.containsKey(id))o.add("lastRepair",receipts.get(id).deepCopy());return o;}
    public static final class AmbiguousRepairException extends IllegalStateException {public AmbiguousRepairException(Throwable cause){super("AMBIGUOUS: material and nanites committed; inspect equipment before retry",cause);}}
    public void tick(UUID id,long tick){var p=profile(id);if(!p.enabled()||!p.autoRepair()||tick<next.getOrDefault(id,Long.MIN_VALUE))return;next.put(id,tick+p.intervalTicks());var a=new JsonObject();a.addProperty("slot",p.repairSlot());a.addProperty("key",p.repairKey());a.addProperty("preview",false);try{receipts.put(id,repairOwner(id,a).deepCopy());}catch(RuntimeException e){var o=new JsonObject();o.addProperty("status","STALLED");o.addProperty("error",e.getClass().getSimpleName()+": "+e.getMessage());receipts.put(id,o);}}
    public void forget(UUID id){next.remove(id);receipts.remove(id);profiles.remove(id);}
    /** Exposure is bounded, family-specific and persisted by the equipment adapter. */
    public static float reduction(int pieces,int exposure,float amount){if(pieces<1||pieces>4||exposure<0||exposure>8||!Float.isFinite(amount)||amount<=0)return 0;return Math.min(2,amount*Math.min(.4f,pieces*.025f+exposure*.025f));}
    /** Module absorbs first, adaptation responds to remaining damage; the entire result must be paid through the shared budget. */
    public static float response(float module,int pieces,int exposure,float amount){if(!Float.isFinite(module)||module<0||!Float.isFinite(amount)||amount<=0)return 0;float base=Math.min(module,amount);return Math.min(amount,base+reduction(pieces,exposure,amount-base));}
    public static int repairPoints(int damage,int maxDamage){return damage<=0||maxDamage<=0?0:Math.min(damage,Math.max(1,maxDamage/4));}
    public interface RepairPort {
        int damage();int maxDamage();boolean compatible();String reserve();void release(String token);
        boolean pay(long cost);void refund(long cost);void commit(String token);void apply(int points);
    }
    /** Validate/preflight before debit. Commit failure refunds nanites and releases reservation; post-commit failure is ambiguous, never replayed here. */
    public static JsonObject repair(RepairPort port,boolean preview){
        int points=repairPoints(port.damage(),port.maxDamage());var o=new JsonObject();o.addProperty("repaired",0);o.addProperty("cost",points*100000L);o.addProperty("materials",points==0?0:1);
        if(points==0){o.addProperty("status","UNDAMAGED");return o;}
        if(!port.compatible())throw new IllegalArgumentException("Actual assimilated item is not a valid repair ingredient");
        if(preview){o.addProperty("status","PREVIEW");o.addProperty("wouldRepair",points);return o;}
        String token=port.reserve();long cost=points*100000L;
        if(!port.pay(cost)){port.release(token);o.addProperty("status","INSUFFICIENT_NANITES");return o;}
        try{port.commit(token);}catch(RuntimeException e){port.refund(cost);port.release(token);throw e;}
        try{port.apply(points);}catch(RuntimeException e){throw new AmbiguousRepairException(e);}
        o.addProperty("status","REPAIRED");o.addProperty("repaired",points);return o;
    }
}
