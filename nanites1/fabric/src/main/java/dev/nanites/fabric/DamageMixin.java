package dev.nanites.fabric;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class DamageMixin {
    // The later HEAD callback reads the modified argument; no second hurt call or shield accumulation.
    @Inject(method="actuallyHurt",at=@At("HEAD"),cancellable=true,order=1100)
    private void nanites$zero(DamageSource source,float amount,CallbackInfo ci){
        if((Object)this instanceof ServerPlayer && amount<=0)ci.cancel();
    }
    @ModifyVariable(method="actuallyHurt",at=@At("HEAD"),argsOnly=true,ordinal=0,order=1000)
    private float nanites$damage(float amount, DamageSource source, float original){
        if((Object)this instanceof ServerPlayer p && NanitesFabric.service!=null && !p.isInvulnerableTo(source))
            return NanitesFabric.service.damage(p.getUUID(),source.is(DamageTypeTags.IS_FALL)?"fall":"impact",amount,p.fallDistance,source.is(DamageTypeTags.IS_FIRE));
        return amount;
    }
}
