package dev.nanites.forge;

import dev.nanites.game.RuntimeInterventions.Profile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.*;

/** Standard vanilla attributes and read-only loaded capability presence. No machine writes. */
final class ForgeRuntime {
    private static Attribute[] attributes(){return new Attribute[]{Attributes.MOVEMENT_SPEED,Attributes.ATTACK_DAMAGE,Attributes.ARMOR,Attributes.KNOCKBACK_RESISTANCE};}
    static void validate(ServerPlayer p,Profile profile){if(p==null||!p.isAlive()||p.isRemoved())throw new IllegalStateException("Living loaded self required");double[] values={profile.speed(),profile.attack(),profile.armor(),profile.knockback()};for(int i=0;i<4;i++)if(values[i]>0&&p.getAttribute(attributes()[i])==null)throw new IllegalArgumentException("Attribute unavailable");}
    private static final Map<UUID,List<AttributeInstance>> owned=new HashMap<>();
    static void apply(ServerPlayer p,UUID id,Profile profile){validate(p,profile);AttributeInstance[] instances=new AttributeInstance[4];for(int i=0;i<4;i++)instances[i]=p.getAttribute(attributes()[i]);applyAttributes(instances,id,profile);}
    static void applyAttributes(AttributeInstance[] instances,UUID id,Profile profile){if(instances.length!=4||owned.containsKey(id))throw new IllegalStateException("Owned modifier collision/slots");double[] values={profile.speed(),profile.attack(),profile.armor(),profile.knockback()};for(int i=0;i<4;i++)if(values[i]>0&&(instances[i]==null||instances[i].getModifier(id)!=null))throw new IllegalStateException("Missing attribute/foreign modifier collision");var touched=new ArrayList<AttributeInstance>();owned.put(id,touched);for(int i=0;i<4;i++)if(values[i]>0){var attribute=instances[i];touched.add(attribute);overlay(attribute,id,values[i],i==0?AttributeModifier.Operation.MULTIPLY_BASE:AttributeModifier.Operation.ADDITION);}}
    static void overlay(AttributeInstance attribute,UUID id,double amount,AttributeModifier.Operation operation){if(attribute==null||!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("Invalid attribute overlay");if(attribute.getModifier(id)!=null)throw new IllegalStateException("Owned modifier collision");attribute.addTransientModifier(new AttributeModifier(id,"nanites lease",amount,operation));}
    static void remove(ServerPlayer p,UUID id){var touched=owned.remove(id);if(touched!=null)for(AttributeInstance attribute:touched)attribute.removeModifier(id);} // Retained original instances survive player-list removal; never remove an unowned UUID.
    static String technology(ServerPlayer p){if(p==null||!p.hasPermissions(2))throw new SecurityException("Technology probe requires OP: foreign capability presence");List<String> out=new ArrayList<>();BlockPos center=p.blockPosition();for(BlockPos pos:BlockPos.betweenClosed(center.offset(-2,-2,-2),center.offset(2,2,2))){if(!p.serverLevel().hasChunkAt(pos))continue;var be=p.serverLevel().getBlockEntity(pos);if(be==null)continue;boolean energy=be.getCapability(ForgeCapabilities.ENERGY).isPresent(),items=be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),fluid=be.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent();if(energy||items||fluid)out.add(pos.toShortString()+" ENERGY="+energy+" ITEM_HANDLER="+items+" FLUID_HANDLER="+fluid);if(out.size()==16)break;}return "Loaded radius 2; presence only, no contents/write/ownership guarantee: "+out;}
}
