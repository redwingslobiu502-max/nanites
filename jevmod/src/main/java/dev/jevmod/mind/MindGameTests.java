package dev.jevmod.mind;

import dev.jevmod.JevMod;
import dev.jevmod.api.mind.MindContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

/** Real Minecraft oracle for player-defined systems, block capabilities and actual projectiles. */
@GameTestHolder("jevmod") @PrefixGameTestTemplate(false)
public final class MindGameTests {
    @GameTest(template="empty",timeoutTicks=50) public static void personaDecisionEventControlsProgrammedSystemsWithoutNetwork(GameTestHelper h){
        var core=core(h,2,List.of("energy","assistant"));var body=body(h,core);
        var provider=dev.jevmod.core.jev.FixedProvider.of(java.util.Map.of("body_action",dev.jevmod.core.jev.Answer.choice("halt",java.util.Map.of("halt",1.0),1)));
        h.runAtTickTime(5,()->{var context=new MindContext(core,JevMod.minds().catalog().systems.get("assistant"),provider);var state=new com.google.gson.JsonObject();state.addProperty("request","pare o corpo");var questions=dev.jevmod.core.Json.parseObject("{\"body_action\":{\"type\":\"choice\",\"instructions\":\"O que fazer?\",\"criteria\":{\"keep\":null,\"halt\":null,\"resume\":null}}}");h.assertTrue(context.ai.decide(state,questions),"Mind must accept a typed decision request");h.assertTrue(provider.lastState.getAsJsonObject().get("instructions").getAsString().contains("JARVIS"),"Personality must reach the provider");});
        h.runAtTickTime(12,()->{h.assertTrue(core.error().isEmpty(),"Decision system must remain valid: "+core.error());h.assertTrue(body.mindHalted(),"Decision event must halt the body");h.assertTrue(core.memory().getAsJsonObject("assistant").get("requested_halt").getAsBoolean(),"Program policy must persist the AI request across energy ticks");h.succeed();});
    }
    private static MindCoreEntity core(GameTestHelper h,int x,List<String> systems){var pos=h.absolutePos(new BlockPos(x,1,2));h.getLevel().setBlock(pos,MindContent.CORE.get().defaultBlockState(),3);var core=(MindCoreEntity)h.getLevel().getBlockEntity(pos);core.configure("jarvis");core.useSystems(systems);return core;}
    private static dev.jevmod.body.GeneratedBody body(GameTestHelper h,MindCoreEntity core){var body=dev.jevmod.body.BodyContent.BODY.get().create(h.getLevel());body.moveTo(core.getBlockPos().getX()+.5,core.getBlockPos().getY(),core.getBlockPos().getZ()+2.5,0,0);h.getLevel().addFreshEntity(body);core.bindBody(body.getUUID());return body;}
    private static ChestBlockEntity chest(GameTestHelper h,MindCoreEntity core,String name,ItemStack contents){var pos=core.getBlockPos().south();h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(pos);chest.setItem(0,contents);core.bindPort(name,pos);return chest;}
    @GameTest(template="empty",timeoutTicks=50) public static void energyDepletionActuallyStopsBodyAndPersists(GameTestHelper h){
        var core=core(h,2,List.of("energy","java_counter"));core.claim(java.util.UUID.fromString("00000000-0000-0000-0000-000000000123"));var body=body(h,core);var state=new com.google.gson.JsonObject();state.addProperty("charge",2);core.memory().add("energy",state);
        h.runAtTickTime(8,()->{h.assertTrue(body.mindHalted(),"Battery script must halt at zero energy");h.assertTrue(core.memory().getAsJsonObject("energy").get("charge").getAsDouble()==0,"Charge must be depleted");h.assertTrue(core.error().isEmpty(),"Energy system must remain valid: "+core.error());double count=core.memory().getAsJsonObject("java_counter").get("count").getAsDouble();body.setDeltaMovement(1,0,0);var position=body.position();h.runAfterDelay(3,()->{h.assertTrue(core.memory().getAsJsonObject("java_counter").get("count").getAsDouble()>count,"Mind must continue running while the body is stopped");h.assertTrue(body.position().distanceToSqr(position)<.001,"Halted body must not move");var restored=new MindCoreEntity(core.getBlockPos(),core.getBlockState());restored.loadWithComponents(core.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(restored.bodyId().equals(body.getUUID())&&restored.owner().equals(core.owner())&&restored.profile().equals(core.profile())&&restored.systems().equals(core.systems())&&restored.memory().equals(core.memory()),"Mind identity/body/memory must survive NBT");h.succeed();});});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void reactorConsumesRealFuelAndResumesBody(GameTestHelper h){
        var core=core(h,2,List.of("reactor","energy"));var body=body(h,core);var state=new com.google.gson.JsonObject();state.addProperty("charge",0);core.memory().add("energy",state);var fuel=chest(h,core,"reactor",new ItemStack(Items.COAL,1));
        h.runAtTickTime(25,()->{h.assertTrue(fuel.getItem(0).isEmpty(),"Reactor must consume inventory coal");h.assertTrue(core.memory().getAsJsonObject("reactor").get("burned").getAsDouble()==1,"Reactor script must record one real fuel conversion");h.assertTrue(!body.mindHalted(),"Recharged body must resume");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=50) public static void programmableWirelessFeedsActualRedstone(GameTestHelper h){
        var sender=core(h,1,List.of());var receiver=core(h,5,List.of("wireless_switch"));
        h.runAtTickTime(5,()->{var payload=new com.google.gson.JsonObject();payload.addProperty("power",12);h.assertTrue(JevMod.minds().transmit(sender,"home/switch",payload,32)==1,"Subscribed core must receive packet");});
        h.runAtTickTime(9,()->{h.assertTrue(receiver.output(Direction.NORTH)==12,"Wireless script must drive output");h.assertTrue(h.getLevel().getSignal(receiver.getBlockPos(),Direction.SOUTH)==12,"Minecraft must observe real redstone power");var payload=new com.google.gson.JsonObject();h.assertTrue(JevMod.minds().transmit(sender,"unsubscribed",payload,32)==0,"Other channels must be isolated");h.assertTrue(JevMod.minds().transmit(sender,"home/switch",payload,1)==0,"Wireless range must be enforced");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=45) public static void jsonRulesReadBlockPowerAndControlOutput(GameTestHelper h){
        var core=core(h,2,List.of("redstone_rules"));h.getLevel().setBlock(core.getBlockPos().west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        h.runAtTickTime(5,()->{h.assertTrue(core.output(Direction.NORTH)==15,"JSON rule must read actual redstone input");h.getLevel().setBlock(core.getBlockPos().west(),Blocks.AIR.defaultBlockState(),3);});
        h.runAtTickTime(9,()->{h.assertTrue(core.output(Direction.NORTH)==0,"JSON rule must respond to signal removal");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void homeDefenseFiresConsumedIronNuggetsAndDamagesTarget(GameTestHelper h){
        var core=core(h,2,List.of("home_defense"));var ammo=chest(h,core,"ammo",new ItemStack(Items.IRON_NUGGET,2));var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setNoAi(true);zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));zombie.moveTo(core.getBlockPos().getX()+3.5,core.getBlockPos().getY(),core.getBlockPos().getZ()+.5,0,0);h.getLevel().addFreshEntity(zombie);
        h.runAtTickTime(45,()->{h.assertTrue(ammo.getItem(0).getCount()<2,"Defense must consume real iron nuggets");h.assertTrue(zombie.getHealth()<20,"Projectile collision must damage the target");h.assertTrue(core.memory().getAsJsonObject("home_defense").get("shots").getAsDouble()>0,"Player script must record shots");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40) public static void separateJavaAddonRunsItsOwnModuleAndApi(GameTestHelper h){
        var core=core(h,2,List.of("java_counter","java_api_client","java_api_rules"));h.runAtTickTime(7,()->{h.assertTrue(core.error().isEmpty(),"Addon must load: "+core.error());var memory=core.memory().getAsJsonObject("java_counter");h.assertTrue(memory!=null&&memory.get("count").getAsDouble()>0,"External Java system must execute");h.assertTrue(memory.get("doubled").getAsDouble()==memory.get("count").getAsDouble()*2,"Custom Java API must return its own result");h.assertTrue(core.memory().getAsJsonObject("java_api_client").get("answer").getAsDouble()==42,"JavaScript must call the custom Java API");h.assertTrue(core.memory().getAsJsonObject("java_api_rules").get("answer").getAsDouble()==42,"JSON must call the custom Java API");h.succeed();});
    }
}
