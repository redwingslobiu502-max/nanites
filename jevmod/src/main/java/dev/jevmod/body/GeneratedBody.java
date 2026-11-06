package dev.jevmod.body;

import dev.jevmod.JevMod;
import dev.jevmod.core.Json;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;

/** Persistent physical host, with fixed player dimensions and independently selected appearance. */
public final class GeneratedBody extends PathfinderMob {
    private DamageRecord lastDamage;
    public DamageRecord lastDamage(){return lastDamage;}
    private static final EntityDataAccessor<String> RECIPE=SynchedEntityData.defineId(GeneratedBody.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> HALTED=SynchedEntityData.defineId(GeneratedBody.class,EntityDataSerializers.BOOLEAN);
    public boolean mindHalted(){return entityData.get(HALTED);}
    public void setMindHalted(boolean value){entityData.set(HALTED,value);if(value)setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
    public GeneratedBody(EntityType<? extends PathfinderMob> type,Level level){super(type,level);setNoAi(true);setPersistenceRequired();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(RECIPE,BodySpec.defaults().json().toString());builder.define(HALTED,false);}
    public BodySpec spec(){try{return BodySpec.parse(Json.parseObject(entityData.get(RECIPE)));}catch(RuntimeException e){return BodySpec.defaults();}}
    public void setSpec(BodySpec spec){entityData.set(RECIPE,spec.json().toString());}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
        String typeId=source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse("");
        float adjusted=amount*spec().damageMultiplier(source.getMsgId(),typeId);
        boolean accepted=super.hurt(source,adjusted);
        if(accepted&&!level().isClientSide){var attacker=source.getEntity();lastDamage=new DamageRecord(source.getMsgId(),typeId,attacker==null?"environment":net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString(),attacker==null?null:attacker.getUUID(),adjusted,amount,level().getGameTime(),getX(),getY(),getZ(),!isAlive());
            if(JevMod.minds()!=null)JevMod.minds().onBodyDamage(this,lastDamage);}
        return accepted;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putString("JevBody",entityData.get(RECIPE));tag.putBoolean("MindHalted",mindHalted());if(lastDamage!=null)tag.putString("LastDamage",lastDamage.json().toString());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setMindHalted(tag.getBoolean("MindHalted"));if(tag.contains("JevBody"))try{setSpec(BodySpec.parse(Json.parseObject(tag.getString("JevBody"))));}catch(RuntimeException e){JevMod.LOGGER.warn("Invalid saved body",e);}lastDamage=null;if(tag.contains("LastDamage"))try{lastDamage=DamageRecord.parse(Json.parseObject(tag.getString("LastDamage")));}catch(RuntimeException e){JevMod.LOGGER.warn("Invalid saved damage record",e);}}
    @Override public void tick(){var before=position();super.tick();if(mindHalted()){setPos(before);setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}if(!level().isClientSide&&tickCount%40==0&&JevMod.runtime()!=null&&JevMod.runtime().defs()!=null&&JevMod.runtime().brain(this)==null){var brain=JevMod.runtime().defs().brains.get(spec().brain());if(brain!=null)JevMod.runtime().attach(this,brain);}}
}
