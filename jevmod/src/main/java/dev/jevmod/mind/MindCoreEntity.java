package dev.jevmod.mind;

import com.google.gson.*;
import dev.jevmod.JevMod;
import dev.jevmod.core.Json;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Physical connector for a persistent, portable logical Mind. Systems and memory are data, not body code. */
public final class MindCoreEntity extends BlockEntity {
    private UUID node=UUID.randomUUID(),owner,body;
    private String profile="jarvis",error="";
    private List<String> overrideSystems;
    private JsonObject memory=new JsonObject();
    private dev.jevmod.body.DamageRecord lastBoundDamage;
    private final Map<String,BlockPos> ports=new LinkedHashMap<>();
    private final EnumMap<Direction,Integer> outputs=new EnumMap<>(Direction.class);
    private final Set<String> channels=new HashSet<>();
    private final ArrayDeque<MindRuntime.Event> inbox=new ArrayDeque<>();
    private MindSession session;private long generation=-1;private boolean enabled=true;
    private long epoch;
    public long epoch(){return epoch;}
    public MindCoreEntity(BlockPos pos,BlockState state){super(MindContent.CORE_ENTITY.get(),pos,state);}
    public UUID nodeId(){return node;}public UUID owner(){return owner;}public UUID bodyId(){return body;}public String profile(){return profile;}public String error(){return error;}public boolean enabled(){return enabled;}
    public JsonObject memory(){return memory;}public Map<String,BlockPos> ports(){return Collections.unmodifiableMap(ports);}
    public com.google.gson.JsonElement lastBodyDamage(){
        if(body() instanceof dev.jevmod.body.GeneratedBody b&&b.lastDamage()!=null)return b.lastDamage().json();
        return lastBoundDamage==null?com.google.gson.JsonNull.INSTANCE:lastBoundDamage.json();
    }
    public void recordBodyDamage(dev.jevmod.body.DamageRecord record){
        lastBoundDamage=record;setChanged();
        if(enabled){var payload=record.json();payload.addProperty("body_id",body.toString());enqueue(new MindRuntime.Event("body_damage",payload));if(record.lethal())enqueue(new MindRuntime.Event("body_death",payload.deepCopy()));}
    }
    public List<String> systems(){var def=JevMod.minds().catalog().minds.get(profile);return overrideSystems!=null?overrideSystems:def==null?List.of():def.systems();}
    public void claim(UUID player){if(owner==null){owner=player;sync();}}
    public void configure(String id){if(!JevMod.minds().catalog().minds.containsKey(id))throw new IllegalArgumentException("Mente não encontrada: "+id);closeSession();profile=id;overrideSystems=null;error="";generation=-1;sync();}
    public void useSystems(List<String> ids){if(ids.size()>32)throw new IllegalArgumentException("Máximo: 32 sistemas");for(String id:ids)if(!JevMod.minds().catalog().systems.containsKey(id))throw new IllegalArgumentException("Sistema ausente: "+id);closeSession();overrideSystems=List.copyOf(new LinkedHashSet<>(ids));generation=-1;sync();}
    public void bindBody(UUID id){if(!Objects.equals(body,id)){if(body!=null)haltBody(false);lastBoundDamage=null;}body=id;if(body() instanceof dev.jevmod.body.GeneratedBody b&&b.lastDamage()!=null)lastBoundDamage=b.lastDamage();sync();}
    public net.minecraft.world.entity.Entity body(){return body!=null&&level instanceof ServerLevel server?server.getEntity(body):null;}
    public void haltBody(boolean halt){if(body() instanceof dev.jevmod.body.GeneratedBody generated)generated.setMindHalted(halt);}
    public void setEnabled(boolean value){enabled=value;closeSession();generation=-1;if(!value)haltBody(true);sync();}
    public void bindPort(String name,BlockPos pos){if(!name.matches("[a-z][a-z0-9_]{0,47}")||ports.size()>=64&&!ports.containsKey(name))throw new IllegalArgumentException("Porta inválida/limite 64");checkPosition(pos);ports.put(name,pos.immutable());sync();}
    public void checkPosition(BlockPos pos){if(pos.distSqr(worldPosition)>64*64||level==null||!level.hasChunkAt(pos)||!level.isInWorldBounds(pos))throw new IllegalArgumentException("Bloco fora do alcance (64) ou chunk descarregado");}
    public int output(Direction direction){return outputs.getOrDefault(direction,0);}
    public void output(Direction direction,int value){int power=Math.max(0,Math.min(15,value));if(output(direction)==power)return;outputs.put(direction,power);setChanged();if(level!=null){level.updateNeighborsAt(worldPosition,getBlockState().getBlock());level.updateNeighborsAt(worldPosition.relative(direction),getBlockState().getBlock());level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}}
    public void listen(String channel){if(!channel.matches("[a-zA-Z0-9_.:/-]{1,64}")||channels.size()>=64&&!channels.contains(channel))throw new IllegalArgumentException("Canal inválido/limite 64");channels.add(channel);}
    public boolean listens(String channel){return channels.contains(channel);}
    public boolean enqueue(MindRuntime.Event event){if(inbox.size()>=64||event.payload().toString().length()>8192)return false;inbox.addLast(event);return true;}
    public void fail(String message){error=message==null?"Erro desconhecido":message;sync();}
    public void closeSession(){epoch++;if(session!=null){session.close();session=null;}channels.clear();inbox.clear();for(Direction side:Direction.values())output(side,0);}
    public static void tick(Level level,BlockPos pos,BlockState state,MindCoreEntity core){
        JevMod.minds().register(core);
        if(!core.enabled)return;
        var runtime=JevMod.minds();
        if(core.generation!=runtime.generation()){
            core.closeSession();core.generation=runtime.generation();
            try{core.error="";core.session=new MindSession(core,runtime);runtime.register(core);}catch(Exception e){core.fail(e.getMessage());}
        }
        if(core.session!=null){try{core.session.tick();int count=Math.min(16,core.inbox.size());for(int i=0;i<count;i++)core.session.event(core.inbox.removeFirst());core.setChanged();if(level.getGameTime()%20==0)core.sync();}catch(Exception e){core.fail(e.getMessage());core.closeSession();core.haltBody(true);}}
    }
    @Override public void setRemoved(){closeSession();haltBody(true);if(JevMod.minds()!=null)JevMod.minds().unregister(this);super.setRemoved();}
    private void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);tag.putUUID("Node",node);if(owner!=null)tag.putUUID("Owner",owner);if(body!=null)tag.putUUID("Body",body);tag.putString("Profile",profile);tag.putBoolean("Enabled",enabled);tag.putString("Memory",memory.toString());if(overrideSystems!=null)tag.putString("Systems",Json.PRETTY.toJson(overrideSystems));var p=new JsonObject();ports.forEach((name,pos)->{var a=new JsonArray();a.add(pos.getX());a.add(pos.getY());a.add(pos.getZ());p.add(name,a);});tag.putString("Ports",p.toString());tag.putString("Error",error);if(lastBoundDamage!=null)tag.putString("BodyDamage",lastBoundDamage.json().toString());}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);try{node=tag.hasUUID("Node")?tag.getUUID("Node"):UUID.randomUUID();owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;body=tag.hasUUID("Body")?tag.getUUID("Body"):null;profile=tag.getString("Profile");enabled=!tag.contains("Enabled")||tag.getBoolean("Enabled");if(tag.getString("Memory").length()>65536)throw new IllegalArgumentException("Memória excede 64 KB");memory=Json.parseObject(tag.getString("Memory"));overrideSystems=tag.contains("Systems")?Json.strings(Json.parseObject("{\"ids\":"+tag.getString("Systems")+"}"),"ids"):null;ports.clear();Json.parseObject(tag.getString("Ports")).entrySet().stream().limit(64).forEach(e->{var a=e.getValue().getAsJsonArray();ports.put(e.getKey(),new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));});error=tag.getString("Error");lastBoundDamage=tag.contains("BodyDamage")?dev.jevmod.body.DamageRecord.parse(Json.parseObject(tag.getString("BodyDamage"))):null;generation=-1;}catch(RuntimeException e){memory=new JsonObject();error="Estado de Mente inválido: "+e.getMessage();enabled=false;}}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider){return saveWithoutMetadata(provider);}
}
