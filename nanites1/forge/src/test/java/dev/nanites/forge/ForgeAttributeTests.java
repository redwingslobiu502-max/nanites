package dev.nanites.forge;

import net.minecraft.world.entity.ai.attributes.*;
import java.util.UUID;

/** Actual Minecraft attribute objects, no server/world/client start. */
public final class ForgeAttributeTests {
    private static int passed;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);passed++;}
    static void denied(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(RuntimeException expected){passed++;}}
    public static void main(String[] args){
        UUID owned=UUID.fromString("00000000-0000-0000-0000-000000000010"),foreign=UUID.fromString("00000000-0000-0000-0000-000000000011");
        var attack=new AttributeInstance(new RangedAttribute("nanites.test.attack",0,0,1024),a->{});attack.setBaseValue(10);
        attack.addPermanentModifier(new AttributeModifier(foreign,"foreign",2,AttributeModifier.Operation.ADDITION));
        ForgeRuntime.overlay(attack,owned,1,AttributeModifier.Operation.ADDITION);check(attack.getValue()==13,"real additive overlay");
        denied(()->ForgeRuntime.overlay(attack,owned,1,AttributeModifier.Operation.ADDITION),"owned UUID cannot stack");
        attack.setBaseValue(20);attack.removeModifier(foreign);attack.addPermanentModifier(new AttributeModifier(foreign,"foreign changed",5,AttributeModifier.Operation.ADDITION));check(attack.getValue()==26,"concurrent base/foreign update survives lease");
        attack.removeModifier(owned);check(attack.getValue()==25&&attack.getModifier(foreign)!=null,"remove only owned modifier");
        attack.removeModifier(owned);check(attack.getValue()==25,"cleanup idempotent");
        var speed=new AttributeInstance(new RangedAttribute("nanites.test.speed",.1,0,1024),a->{});speed.setBaseValue(.1);ForgeRuntime.overlay(speed,owned,.1,AttributeModifier.Operation.MULTIPLY_BASE);check(Math.abs(speed.getValue()-.11)<1e-12,"speed bounded ten percent of base");speed.setBaseValue(.2);check(Math.abs(speed.getValue()-.22)<1e-12,"speed observes changed base rather than stale restore");speed.removeModifier(owned);check(speed.getBaseValue()==.2,"speed cleanup leaves base intact");
        denied(()->ForgeRuntime.overlay(null,owned,1,AttributeModifier.Operation.ADDITION),"missing attribute denied");denied(()->ForgeRuntime.overlay(attack,owned,Double.NaN,AttributeModifier.Operation.ADDITION),"nonfinite denied");
        var profile=new dev.nanites.game.RuntimeInterventions.Profile(.1,1,0,0,0,0);
        ForgeRuntime.applyAttributes(new AttributeInstance[]{speed,attack,null,null},owned,profile);check(attack.getValue()==26,"tracked original instances receive overlay");
        ForgeRuntime.remove(null,owned);check(attack.getValue()==25&&speed.getModifier(owned)==null,"cleanup after player-list removal uses original instances");
        denied(()->ForgeRuntime.applyAttributes(new AttributeInstance[]{speed,attack,null,null},foreign,profile),"preflight detects foreign UUID before any overlay");ForgeRuntime.remove(null,foreign);check(attack.getModifier(foreign)!=null&&speed.getModifier(foreign)==null,"failed preflight cleanup never removes foreign UUID");
        ForgeRuntime.remove(null,owned);check(attack.getValue()==25,"tracked cleanup remains idempotent");
        System.out.println("ForgeAttributeTests: "+passed+" deterministic checks passed; real attribute objects only, no live game.");
    }
}
