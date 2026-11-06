package dev.jevmod.mind;

import com.google.gson.JsonObject;
import dev.jevmod.JevMod;
import dev.jevmod.core.script.ScriptHost;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.nio.file.Path;
import java.util.*;

/** Sessions only register loaded cores. Wireless delivery is queued, never recursively executed. */
public final class MindRuntime {
    public record Event(String type,JsonObject payload){}
    private final Path root;
    private MindCatalog catalog=new MindCatalog();
    private final Set<MindCoreEntity> cores=new LinkedHashSet<>();
    private long generation;
    private ScriptHost scripts=new ScriptHost(1_000_000);
    public MindRuntime(Path root){this.root=root;}
    public Path root(){return root;}public MindCatalog catalog(){return catalog;}public long generation(){return generation;}public ScriptHost scripts(){return scripts;}
    public List<String> reload(){for(var core:List.copyOf(cores))core.closeSession();catalog=MindCatalog.load(root);scripts=new ScriptHost(JevMod.runtime().config().scriptInstructionLimit);generation++;return List.copyOf(catalog.errors);}
    public void register(MindCoreEntity core){cores.add(core);}public void unregister(MindCoreEntity core){cores.remove(core);}
    public void onBodyDamage(dev.jevmod.body.GeneratedBody body,dev.jevmod.body.DamageRecord record){
        for(var core:List.copyOf(cores))if(!core.isRemoved()&&core.getLevel()==body.level()&&body.getUUID().equals(core.bodyId()))core.recordBodyDamage(record);
    }
    public void close(){for(var core:List.copyOf(cores))core.closeSession();cores.clear();}
    /** Explicit @persona chat reaches the nearest owned, loaded core rather than broadcasting paid requests. */
    public void onChat(net.minecraft.server.level.ServerPlayer player,String text){
        cores.stream().filter(core->core.enabled()&&!core.isRemoved()&&core.getLevel()==player.level()&&player.getUUID().equals(core.owner())&&player.distanceToSqr(core.getBlockPos().getCenter())<=4096&&text.toLowerCase(java.util.Locale.ROOT).startsWith("@"+core.profile()+" ")).min(java.util.Comparator.comparingDouble(core->player.distanceToSqr(core.getBlockPos().getCenter()))).ifPresent(core->{var data=new JsonObject();data.addProperty("text",text.substring(core.profile().length()+2));core.enqueue(new Event("chat",data));});
    }
    public int transmit(MindCoreEntity source,String channel,JsonObject data,double range){
        if(!channel.matches("[a-zA-Z0-9_.:/-]{1,64}")||!Double.isFinite(range)||range<0||range>128)throw new IllegalArgumentException("Canal até 64 caracteres; alcance 0..128");
        int count=0;
        for(var destination:List.copyOf(cores))if(destination!=source&&!destination.isRemoved()&&destination.listens(channel)&&destination.getLevel()==source.getLevel()&&destination.getBlockPos().distSqr(source.getBlockPos())<=range*range){
            var payload=data.deepCopy();payload.addProperty("channel",channel);payload.addProperty("sender",source.nodeId().toString());
            if(destination.enqueue(new Event("wireless",payload)))count++;
        }
        return count;
    }
}
