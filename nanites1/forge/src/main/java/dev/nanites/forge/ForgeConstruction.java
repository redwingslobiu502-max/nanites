package dev.nanites.forge;

import com.google.gson.*;
import dev.nanites.core.SafeData;
import dev.nanites.game.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Loader boundary: registry, world reads and writes are exclusively on server main. */
public final class ForgeConstruction {
    private ForgeConstruction() {}
    private static void thread(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Forge world access requires server thread");}
    private static ServerLevel level(MinecraftServer server,String dimension){thread(server);ResourceLocation key=ResourceLocation.tryParse(dimension);if(key==null)throw new IllegalArgumentException("Dimension inválida: "+dimension);ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,key));if(level==null)throw new IllegalArgumentException("Dimension não carregada: "+dimension);return level;}
    public static BlockState resolve(String text){
        int bracket=text.indexOf('[');String id=bracket<0?text:text.substring(0,bracket);ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null||!ForgeRegistries.BLOCKS.containsKey(key))throw new IllegalArgumentException("Bloco não registrado: "+id);
        Block block=ForgeRegistries.BLOCKS.getValue(key);BlockState state=Objects.requireNonNull(block).defaultBlockState();
        if(bracket>=0){if(!text.endsWith("]"))throw new IllegalArgumentException("Blockstate sem ]");String attributes=text.substring(bracket+1,text.length()-1);if(attributes.isEmpty())throw new IllegalArgumentException("Blockstate vazio");Set<String> seen=new HashSet<>();
            for(String pair:attributes.split(",",-1)){String[] p=pair.split("=",-1);if(p.length!=2||!seen.add(p[0]))throw new IllegalArgumentException("Propriedade inválida/duplicada: "+pair);Property<?> property=block.getStateDefinition().getProperty(p[0]);if(property==null)throw new IllegalArgumentException("Propriedade inexistente em "+id+": "+p[0]);state=set(state,property,p[1]);}
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state,Property<T> property,String value){return state.setValue(property,property.getValue(value).orElseThrow(()->new IllegalArgumentException("Valor inválido "+property.getName()+"="+value+"; possíveis="+property.getPossibleValues())));}
    private static <T extends Comparable<T>> String value(BlockState state,Property<T> property){return property.getName(state.getValue(property));}
    private static <T extends Comparable<T>> List<String> values(Property<T> property){return property.getPossibleValues().stream().map(property::getName).toList();}
    public static String describe(BlockState state){String id=Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(state.getBlock())).toString();var props=state.getProperties().stream().sorted(Comparator.comparing(Property::getName)).map(p->p.getName()+"="+value(state,p)).toList();return props.isEmpty()?id:id+"["+String.join(",",props)+"]";}
    public static ConstructionQueue.World bind(MinecraftServer server,UUID owner,Blueprint.Origin origin){
        thread(server);if(server.getPlayerList().getPlayer(owner)==null)throw new IllegalStateException("Dono offline");ServerLevel level=level(server,origin.dimension());
        return new ConstructionQueue.World(){
            private void check(){thread(server);if(server.getLevel(level.dimension())!=level)throw new IllegalStateException("Dimension descarregada");}
            private BlockPos pos(Blueprint.Pos p){return new BlockPos(p.x(),p.y(),p.z());}
            public Object resolve(String block){check();return ForgeConstruction.resolve(block);}
            public boolean valid(Blueprint.Pos p){check();return p.x()>-30_000_000&&p.x()<30_000_000&&p.z()>-30_000_000&&p.z()<30_000_000&&!level.isOutsideBuildHeight(p.y())&&level.getWorldBorder().isWithinBounds(pos(p));}
            public boolean loaded(Blueprint.Pos p){check();return level.getChunkSource().getChunkNow(p.x()>>4,p.z()>>4)!=null;}
            public String observe(Blueprint.Pos p){check();if(!valid(p))return "OUT_OF_WORLD";if(!loaded(p))return "CHUNK_UNLOADED";return ForgeConstruction.describe(level.getBlockState(pos(p)));}
            public String describe(Object state){check();return ForgeConstruction.describe((BlockState)state);}
            public boolean place(Blueprint.Pos p,Object state){check();if(!valid(p)||!loaded(p))throw new IllegalStateException("Chunk/posição indisponível");return level.setBlock(pos(p),(BlockState)state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
        };
    }
    public static JsonObject observe(MinecraftServer server,UUID owner,JsonObject args){
        thread(server);if(!args.keySet().equals(Set.of("dimension","x","y","z","radius")))throw new IllegalArgumentException("observe exige dimension,x,y,z,radius");
        var player=server.getPlayerList().getPlayer(owner);if(player==null)throw new IllegalStateException("Offline");String dimension=SafeData.text(args,"dimension",240);if(dimension.isEmpty())dimension=player.serverLevel().dimension().location().toString();
        int x=Math.toIntExact(SafeData.integer(args.get("x"))),y=Math.toIntExact(SafeData.integer(args.get("y"))),z=Math.toIntExact(SafeData.integer(args.get("z"))),r=Math.toIntExact(SafeData.integer(args.get("radius")));if(r<0||r>2)throw new IllegalArgumentException("radius 0..2");
        var w=bind(server,owner,new Blueprint.Origin(dimension,x,y,z));JsonObject result=new JsonObject();JsonObject p=new JsonObject();p.addProperty("dimension",player.serverLevel().dimension().location().toString());p.addProperty("x",player.blockPosition().getX());p.addProperty("y",player.blockPosition().getY());p.addProperty("z",player.blockPosition().getZ());result.add("player",p);result.addProperty("dimension",dimension);result.addProperty("game_tick",level(server,dimension).getGameTime());JsonArray blocks=new JsonArray();
        for(int dx=-r;dx<=r;dx++)for(int dy=-r;dy<=r;dy++)for(int dz=-r;dz<=r;dz++){var pos=new Blueprint.Pos(Math.addExact(x,dx),Math.addExact(y,dy),Math.addExact(z,dz));JsonObject b=new JsonObject();b.addProperty("x",pos.x());b.addProperty("y",pos.y());b.addProperty("z",pos.z());b.addProperty("block",w.observe(pos));blocks.add(b);}
        result.add("blocks",blocks);return result;
    }
    public static JsonObject catalog(MinecraftServer server,String query,int offset,int limit){
        thread(server);if(offset<0||limit<1||limit>32||query.length()>256)throw new IllegalArgumentException("query <= 256; offset >= 0; limit 1..32");
        var ids=ForgeRegistries.BLOCKS.getKeys().stream().map(ResourceLocation::toString).filter(s->s.contains(query.toLowerCase(Locale.ROOT))).sorted().toList();JsonObject result=new JsonObject();JsonArray entries=new JsonArray();int i=Math.min(offset,ids.size()),bytes=0;
        for(;i<ids.size()&&entries.size()<limit;i++){String id=ids.get(i);Block block=ForgeRegistries.BLOCKS.getValue(Objects.requireNonNull(ResourceLocation.tryParse(id)));JsonObject entry=new JsonObject();entry.addProperty("id",id);entry.addProperty("default_state",describe(Objects.requireNonNull(block).defaultBlockState()));JsonObject props=new JsonObject();for(Property<?> property:block.getStateDefinition().getProperties())props.add(property.getName(),SafeData.GSON.toJsonTree(values(property)));entry.add("properties",props);int size=entry.toString().length();if(bytes+size>12000){if(entries.isEmpty()){entry.remove("properties");entry.addProperty("properties_truncated",true);}else break;}bytes+=size;entries.add(entry);}
        result.add("blocks",entries);result.addProperty("total",ids.size());result.addProperty("offset",offset);result.addProperty("next_offset",i);result.addProperty("has_more",i<ids.size());return result;
    }
}
