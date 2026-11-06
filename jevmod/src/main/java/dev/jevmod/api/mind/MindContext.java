package dev.jevmod.api.mind;

import com.google.gson.*;
import dev.jevmod.JevMod;
import dev.jevmod.core.Json;
import dev.jevmod.core.jev.*;
import dev.jevmod.core.script.ScriptHost;
import dev.jevmod.mind.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.mozilla.javascript.*;
import java.util.*;

/** Default Mind API: block IO and networking exceed body powers; Java addons can extend it freely. */
public final class MindContext {
    public final String system;
    public final Memory memory=new Memory();public final Blocks blocks=new Blocks();public final Redstone redstone=new Redstone();public final Wireless wireless=new Wireless();public final Body body=new Body();public final Entities entities=new Entities();public final Projectiles projectiles=new Projectiles();public final Ai ai=new Ai();
    private final MindCoreEntity core;private final SystemDef definition;private final JevProvider decisionProvider;private int remaining=256;
    public MindContext(MindCoreEntity core,SystemDef definition){this(core,definition,null);}
    /** Java systems may provide their own typed decision backend. Null keeps the user's configured provider. */
    public MindContext(MindCoreEntity core,SystemDef definition,JevProvider provider){this.core=core;this.definition=definition;decisionProvider=provider;system=definition.id();}
    public MindCoreEntity core(){return core;}public ServerLevel level(){return (ServerLevel)core.getLevel();}public SystemDef definition(){return definition;}
    public long tick(){return level().getGameTime();}
    public String persona(){return core.profile();}
    public void resetBudget(){remaining=256;}
    public void cost(){if(--remaining<0)throw new IllegalStateException("Limite de 256 operações de mundo por chamada");}
    public void log(String text){cost();JevMod.LOGGER.info("[mind {}:{}] {}",core.nodeId(),system,text);}
    public void say(String text){cost();if(core.owner()!=null){var player=level().getServer().getPlayerList().getPlayer(core.owner());if(player!=null)player.sendSystemMessage(Component.literal("§b["+core.profile()+"]§r "+text));}}
    public Object config(){return expose(definition.config());}
    public Object call(String service,String operation,Object arguments)throws Exception{cost();return expose(MindExtensions.call(service,this,operation,object(arguments)));}
    public void emit(String event,Object arguments){cost();if(!event.matches("[a-zA-Z0-9_.:/-]{1,64}"))throw new IllegalArgumentException("Evento inválido");core.enqueue(new MindRuntime.Event(event,object(arguments)));}
    public static JsonObject object(Object value){var json=ScriptHost.toJson(ScriptHost.toJava(value));if(!json.isJsonObject()||json.toString().length()>8192)throw new IllegalArgumentException("Objeto JSON até 8 KB");return json.getAsJsonObject();}
    public static Object expose(JsonElement value){var plain=ScriptHost.fromJson(value);var cx=Context.getCurrentContext();return cx==null?plain:ScriptHost.toJs(cx,ScriptRuntime.getTopCallScope(cx),plain);}
    public final class Memory {
        private JsonObject state(){var root=core.memory();if(!root.has(system))root.add(system,new JsonObject());return root.getAsJsonObject(system);}
        public Object get(String key,Object fallback){cost();return state().has(key)?expose(state().get(key)):expose(ScriptHost.toJson(ScriptHost.toJava(fallback)));}
        public void set(String key,Object value){cost();if(key.length()>64)throw new IllegalArgumentException("Chave até 64 caracteres");var json=ScriptHost.toJson(ScriptHost.toJava(value));var previous=state().get(key);state().add(key,json);try{if(core.memory().toString().length()>65536)throw new IllegalArgumentException("Memória até 64 KB por Mente");}catch(RuntimeException e){if(previous==null)state().remove(key);else state().add(key,previous);throw e;}core.setChanged();}
        public void remove(String key){cost();state().remove(key);core.setChanged();}
        public Object snapshot(){cost();return expose(state().deepCopy());}
        public Object readSystem(String id,String key,Object fallback){cost();var root=core.memory();return root.has(id)&&root.getAsJsonObject(id).has(key)?expose(root.getAsJsonObject(id).get(key)):expose(ScriptHost.toJson(ScriptHost.toJava(fallback)));}
        public void writeSystem(String id,String key,Object value){MindCatalog.requireId(id);new MindContext(core,new SystemDef(id,"rules","unused.js","",List.of(),1,new JsonObject(),new JsonArray())).memory.set(key,value);cost();}
    }
    public final class Blocks {
        public void bind(String name,int x,int y,int z){cost();core.bindPort(name,new BlockPos(x,y,z));}
        public BlockPort port(String name){cost();var pos=core.ports().get(name);if(pos==null)throw new IllegalArgumentException("Porta não conectada: "+name);core.checkPosition(pos);return new BlockPort(pos);}
        public boolean connected(String name){cost();return core.ports().containsKey(name)&&level().hasChunkAt(core.ports().get(name));}
        public BlockPort relative(int x,int y,int z){cost();var pos=core.getBlockPos().offset(x,y,z);core.checkPosition(pos);return new BlockPort(pos);}
        public Object connections(){cost();var json=new JsonObject();core.ports().forEach((name,pos)->{var a=new JsonArray();a.add(pos.getX());a.add(pos.getY());a.add(pos.getZ());json.add(name,a);});return expose(json);}
    }
    public final class BlockPort {
        private final BlockPos pos;
        public BlockPort(BlockPos pos){this.pos=pos.immutable();}
        private void check(){cost();core.checkPosition(pos);}
        public String block(){check();return BuiltInRegistries.BLOCK.getKey(level().getBlockState(pos).getBlock()).toString();}
        public int signal(){check();return level().getBestNeighborSignal(pos);}
        public Object state(){check();var out=new JsonObject();level().getBlockState(pos).getValues().forEach((property,value)->out.addProperty(property.getName(),value.toString()));return expose(out);}
        @SuppressWarnings({"rawtypes","unchecked"}) public void setState(String property,String value){check();var state=level().getBlockState(pos);Property p=state.getBlock().getStateDefinition().getProperty(property);if(p==null)throw new IllegalArgumentException("Propriedade ausente: "+property);var parsed=p.getValue(value);if(parsed.isEmpty())throw new IllegalArgumentException("Valor de bloco inválido");level().setBlock(pos,state.setValue(p,(Comparable)parsed.get()),3);}
        public void setBlock(String id){check();var location=ResourceLocation.tryParse(id);if(location==null||!BuiltInRegistries.BLOCK.containsKey(location))throw new IllegalArgumentException("Bloco ausente");level().setBlock(pos,BuiltInRegistries.BLOCK.get(location).defaultBlockState(),3);}
        private net.neoforged.neoforge.items.IItemHandler inventory(){check();var handler=level().getCapability(Capabilities.ItemHandler.BLOCK,pos,(Direction)null);if(handler==null)throw new IllegalArgumentException("Bloco não fornece inventário");return handler;}
        public int count(String id){var handler=inventory();int total=0;for(int i=0;i<handler.getSlots();i++){cost();var stack=handler.getStackInSlot(i);if(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id))total+=stack.getCount();}return total;}
        public int consume(String id,int amount){if(amount<0||amount>4096)throw new IllegalArgumentException("Quantidade: 0..4096");var handler=inventory();int removed=0;for(int i=0;i<handler.getSlots()&&removed<amount;i++){cost();if(BuiltInRegistries.ITEM.getKey(handler.getStackInSlot(i).getItem()).toString().equals(id))removed+=handler.extractItem(i,amount-removed,false).getCount();}return removed;}
        public int insert(String id,int amount){if(amount<0||amount>4096)throw new IllegalArgumentException("Quantidade: 0..4096");var location=ResourceLocation.tryParse(id);if(location==null||!BuiltInRegistries.ITEM.containsKey(location))throw new IllegalArgumentException("Item ausente");var handler=inventory();var left=new ItemStack(BuiltInRegistries.ITEM.get(location),amount);for(int i=0;i<handler.getSlots()&&!left.isEmpty();i++){cost();left=handler.insertItem(i,left,false);}return amount-left.getCount();}
        public int energy(){check();var storage=level().getCapability(Capabilities.EnergyStorage.BLOCK,pos,(Direction)null);return storage==null?0:storage.getEnergyStored();}
        public int extractEnergy(int amount){check();if(amount<0||amount>1_000_000)throw new IllegalArgumentException("Energia: 0..1000000");var storage=level().getCapability(Capabilities.EnergyStorage.BLOCK,pos,(Direction)null);return storage==null?0:storage.extractEnergy(amount,false);}
        public int receiveEnergy(int amount){check();if(amount<0||amount>1_000_000)throw new IllegalArgumentException("Energia: 0..1000000");var storage=level().getCapability(Capabilities.EnergyStorage.BLOCK,pos,(Direction)null);return storage==null?0:storage.receiveEnergy(amount,false);}
    }
    public final class Redstone {
        public int input(){cost();return level().getBestNeighborSignal(core.getBlockPos());}
        public int read(String side){cost();var direction=Direction.byName(side);if(direction==null)throw new IllegalArgumentException("Lado inválido");return level().getSignal(core.getBlockPos().relative(direction),direction);}
        public void output(String side,int value){cost();var direction=Direction.byName(side);if(direction==null)throw new IllegalArgumentException("Lado inválido");core.output(direction,value);}
    }
    public final class Wireless {
        public void listen(String channel){cost();core.listen(channel);}
        public int send(String channel,Object data,double range){cost();return JevMod.minds().transmit(core,channel,object(data),range);}
    }
    public final class Body {
        public dev.jevmod.api.EntityRef ref(){cost();var entity=core.body();return entity==null||entity.isRemoved()?null:new dev.jevmod.api.EntityRef(entity,entity);}
        public dev.jevmod.api.Vec pos(){var reference=ref();return reference==null?null:reference.pos();}
        public double health(){var reference=ref();return reference==null?0:reference.health();}
        public boolean isAlive(){cost();var entity=core.body();return entity!=null&&!entity.isRemoved()&&entity.isAlive();}
        public Object lastDamage(){cost();return expose(core.lastBodyDamage());}
        public boolean exists(){cost();return core.body()!=null&&core.body().isAlive();}
        public void halt(){cost();core.haltBody(true);}
        public void resume(){cost();core.haltBody(false);}
        public boolean halted(){cost();return core.body() instanceof dev.jevmod.body.GeneratedBody body&&body.mindHalted();}
        public String id(){cost();return core.bodyId()==null?"":core.bodyId().toString();}
        public void move(double x,double y,double z){cost();if(!Double.isFinite(x+y+z)||Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))>4)throw new IllegalArgumentException("Movimento finito até 4");if(core.body() instanceof dev.jevmod.body.GeneratedBody body&&!body.mindHalted())body.setDeltaMovement(x,y,z);}
        public void bind(String uuid){cost();var id=UUID.fromString(uuid);var entity=level().getEntity(id);if(!(entity instanceof dev.jevmod.body.GeneratedBody)||entity.distanceToSqr(core.getBlockPos().getCenter())>64*64)throw new IllegalArgumentException("Corpo Jev não encontrado no alcance");core.bindBody(id);}
    }
    public final class Entities {
        /** Scan loaded entities around an arbitrary world position. Never loads chunks. */
        public Object scan(double x,double y,double z,double radius,String filter){
            cost();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(radius)||radius<1||radius>512||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000)throw new IllegalArgumentException("Scan: posição finita; raio 1..512");
            if(!Set.of("all","hostile","player","passive").contains(filter))throw new IllegalArgumentException("Filtro: all, hostile, player ou passive");
            var center=new Vec3(x,y,z);var box=new AABB(x-radius,y-radius,z-radius,x+radius,y+radius,z+radius);var array=new JsonArray();
            var found=level().getEntitiesOfClass(LivingEntity.class,box,e->e.isAlive()&&e.position().distanceToSqr(center)<=radius*radius&&switch(filter){case "hostile"->e instanceof net.minecraft.world.entity.monster.Enemy;case "player"->e instanceof net.minecraft.world.entity.player.Player;case "passive"->!(e instanceof net.minecraft.world.entity.monster.Enemy)&&!(e instanceof net.minecraft.world.entity.player.Player);default->true;}).stream().sorted(Comparator.comparingDouble(e->e.position().distanceToSqr(center))).limit(256).toList();
            for(var entity:found){var data=new JsonObject();data.addProperty("id",entity.getUUID().toString());data.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());data.addProperty("x",entity.getX());data.addProperty("y",entity.getY());data.addProperty("z",entity.getZ());data.addProperty("health",entity.getHealth());data.addProperty("distance",Math.round(entity.position().distanceTo(center)*10)/10.0);data.addProperty("hostile",entity instanceof net.minecraft.world.entity.monster.Enemy);array.add(data);}return expose(array);
        }
        public Object nearby(double radius,boolean hostilesOnly){cost();if(!Double.isFinite(radius)||radius<0||radius>64)throw new IllegalArgumentException("Raio: 0..64");var array=new JsonArray();for(var entity:level().getEntitiesOfClass(LivingEntity.class,new AABB(core.getBlockPos()).inflate(radius),e->e.isAlive()&&(!hostilesOnly||e instanceof net.minecraft.world.entity.monster.Enemy)).stream().limit(64).toList()){var out=new JsonObject();out.addProperty("id",entity.getUUID().toString());out.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());out.addProperty("x",entity.getX());out.addProperty("y",entity.getY());out.addProperty("z",entity.getZ());out.addProperty("health",entity.getHealth());array.add(out);}return expose(array);}
    }
    public final class Projectiles {
        public boolean fire(String ammunitionPort,String targetId,String itemId,double speed,double damage){cost();if(!Double.isFinite(speed+damage)||speed<=0||speed>4||damage<0||damage>100)throw new IllegalArgumentException("Velocidade: 0..4; dano: 0..100");var target=level().getEntity(UUID.fromString(targetId));if(!(target instanceof LivingEntity)||!target.isAlive()||target.distanceToSqr(core.getBlockPos().getCenter())>64*64)return false;
            var origin=core.getBlockPos().getCenter().add(0,.65,0);var aim=target.getBoundingBox().getCenter();var direction=aim.subtract(origin).normalize();var muzzle=origin.add(direction.scale(.8));
            var collision=level().clip(new net.minecraft.world.level.ClipContext(muzzle,aim,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,target));if(collision.getType()!=HitResult.Type.MISS)return false;
            if(blocks.port(ammunitionPort).consume(itemId,1)!=1)return false;var bullet=MindContent.NUGGET.get().create(level());if(bullet==null)throw new IllegalStateException("Projétil não registrado");bullet.setItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))));bullet.damage((float)damage);bullet.setOwner(core.body());bullet.moveTo(muzzle.x,muzzle.y,muzzle.z);bullet.shoot(direction.x,direction.y,direction.z,(float)speed,0);return level().addFreshEntity(bullet);
        }
    }
    public final class Ai {
        private boolean pending;private long lastRequest=-1000;
        public boolean pending(){return pending;}
        public boolean decide(Object state,Object questions){cost();if(pending||tick()-lastRequest<20)return false;var raw=object(questions);if(raw.size()==0||raw.size()>32)throw new IllegalArgumentException("1..32 perguntas");var plan=new LinkedHashMap<String,Question>();raw.entrySet().forEach(entry->{var q=entry.getValue().getAsJsonObject();var type=Question.Type.valueOf(Json.str(q,"type","noul").toUpperCase(Locale.ROOT));plan.put(entry.getKey(),new Question(entry.getKey(),type,q.has("instructions")?q.get("instructions"):Json.p(entry.getKey()),q.get("criteria")));});var input=object(state);var def=JevMod.minds().catalog().minds.get(core.profile());input.addProperty("instructions",def==null?"":def.instructions());input.addProperty("mind",core.profile());var provider=decisionProvider==null?JevMod.runtime().config().provider(null,null,System::getenv):decisionProvider;long generation=JevMod.minds().generation(),epoch=core.epoch();var server=level().getServer();pending=true;lastRequest=tick();provider.evaluate(input,plan).whenComplete((result,error)->server.execute(()->{pending=false;if(core.isRemoved()||epoch!=core.epoch()||generation!=JevMod.minds().generation())return;var payload=new JsonObject();payload.addProperty("system",system);if(error!=null)payload.addProperty("error",error.getMessage());else{var answers=new JsonObject();result.answers.forEach((id,answer)->answers.add(id,answer.toJson()));payload.add("answers",answers);payload.addProperty("model",result.model);}core.enqueue(new MindRuntime.Event("decision",payload));}));return true;}
    }
}
