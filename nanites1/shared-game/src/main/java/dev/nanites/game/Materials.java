package dev.nanites.game;
import dev.nanites.core.SafeData;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit operator mapping and consumed inventory evidence, never inferred from LLM/source names. */
public final class Materials {
    private final Path root;
    private final Properties config;
    private final Map<UUID,JsonObject> records=new HashMap<>();
    public void forget(UUID owner){records.remove(owner);}
    public Materials(Path world,Properties config)throws Exception{root=SafeData.root(world.resolve("materials"));this.config=config;}
    public JsonObject read(UUID owner)throws Exception{
        if(records.containsKey(owner))return records.get(owner).deepCopy();
        Path p=SafeData.child(root,owner+".json");JsonObject o;
        if(!Files.exists(p)){o=new JsonObject();o.addProperty("format",1);o.addProperty("owner",owner.toString());o.addProperty("count",0);}
        else o=SafeData.object(new String(SafeData.bytes(p,4096),StandardCharsets.UTF_8),4096);
        if(SafeData.integer(o.get("format"))!=1||!SafeData.text(o,"owner",96).equals(owner.toString())||SafeData.integer(o.get("count"))<0||SafeData.integer(o.get("count"))>16)throw new SecurityException("Material ownership/version/quota");
        if(SafeData.integer(o.get("count"))>0)SafeData.text(o,"registry",128);
        records.put(owner,o.deepCopy());return o;
    }
    public boolean insulating(UUID owner)throws Exception{var o=read(owner);return SafeData.integer(o.get("count"))>0&&SafeData.text(o,"registry",128).equals(config.getProperty("materials.insulating",""));}
    public String absorb(UUID owner,String registry,Runnable consume)throws Exception{
        if(!registry.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!registry.equals(config.getProperty("materials.insulating","")))throw new IllegalArgumentException("Material não mapeado explicitamente pelo operador");
        var o=read(owner);long count=SafeData.integer(o.get("count"));
        if(count>0&&!registry.equals(SafeData.text(o,"registry",128)))count=0; // Old mapping does not certify new material.
        if(count>=16)throw new IllegalStateException("Material quota 16");
        o.addProperty("registry",registry);o.addProperty("property","insulating");o.addProperty("evidence","operator-config + consumed registered inventory item");o.addProperty("count",count+1);o.addProperty("recordRevision",o.has("recordRevision")?Math.incrementExact(SafeData.integer(o.get("recordRevision"))):1);
        consume.run();SafeData.atomic(root,owner+".json",SafeData.GSON.toJson(o).getBytes(StandardCharsets.UTF_8),4096);
        records.put(owner,o.deepCopy());
        return "Material absorvido: "+registry+"; isolante explicitamente mapeado, não física inferida. protect.insulating só dano de fogo, custo por uso; sem energia.";
    }
}
