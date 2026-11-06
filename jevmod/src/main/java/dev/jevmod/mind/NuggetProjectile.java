package dev.jevmod.mind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** A real colliding projectile rendered as the consumed ammunition item. */
public final class NuggetProjectile extends ThrowableItemProjectile {
    private float damage=4;
    public NuggetProjectile(EntityType<? extends ThrowableItemProjectile> type,Level level){super(type,level);}
    @Override protected Item getDefaultItem(){return Items.IRON_NUGGET;}
    public void damage(float value){damage=value;}
    @Override protected double getDefaultGravity(){return .01;}
    @Override protected void onHitEntity(EntityHitResult hit){super.onHitEntity(hit);if(!level().isClientSide)hit.getEntity().hurt(damageSources().thrown(this,getOwner()),damage);}
    @Override protected void onHit(HitResult hit){super.onHit(hit);if(!level().isClientSide)discard();}
    @Override public void tick(){super.tick();if(tickCount>100&&!level().isClientSide)discard();}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("JevDamage",damage);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);if(tag.contains("JevDamage"))damage=Math.max(0,Math.min(100,tag.getFloat("JevDamage")));}
}
