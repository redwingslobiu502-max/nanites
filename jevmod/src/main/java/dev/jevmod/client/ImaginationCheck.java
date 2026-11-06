package dev.jevmod.client;

import dev.jevmod.JevMod;
import dev.jevmod.mind.*;
import dev.jevmod.body.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import java.nio.file.*;
import java.util.List;

/** Creates a new disposable scene and records actual rendered Mind UI, machinery and projectile frames. */
final class ImaginationCheck {
    private static boolean started,joined;private static int ticks;
    private static final BlockPos POS=new BlockPos(0,-59,0);
    static void tick(){var mc=Minecraft.getInstance();
        if(!started&&mc.screen instanceof TitleScreen){started=true;mc.createWorldOpenFlows().createFreshLevel("jevmod-imagination-check-"+System.currentTimeMillis(),new LevelSettings("Imagination disposable check",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(482101L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);}
        if(!started||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!joined){joined=true;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();level.setBlock(POS,MindContent.CORE.get().defaultBlockState(),3);var core=(MindCoreEntity)level.getBlockEntity(POS);core.claim(id);core.configure("jarvis");core.useSystems(List.of("reactor","energy","home_defense","java_counter"));
            var body=BodyContent.BODY.get().create(level);body.moveTo(2.5,-59,1.5,0,0);level.addFreshEntity(body);core.bindBody(body.getUUID());var energy=new com.google.gson.JsonObject();energy.addProperty("charge",2);core.memory().add("energy",energy);
            level.setBlock(POS.west(),Blocks.CHEST.defaultBlockState(),3);var fuel=(ChestBlockEntity)level.getBlockEntity(POS.west());fuel.setItem(0,new ItemStack(Items.COAL,2));core.bindPort("reactor",POS.west());level.setBlock(POS.south(),Blocks.CHEST.defaultBlockState(),3);var ammo=(ChestBlockEntity)level.getBlockEntity(POS.south());ammo.setItem(0,new ItemStack(Items.IRON_NUGGET,64));core.bindPort("ammo",POS.south());
            var zombie=EntityType.ZOMBIE.create(level);zombie.setNoAi(true);zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);zombie.setHealth(200);zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));zombie.moveTo(-5.5,-59,-2.5,0,0);level.addFreshEntity(zombie);level.setDayTime(6000);
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);player.connection.teleport(4.5,-59,5.5,142,13);
        }catch(Exception e){JevMod.LOGGER.error("Imagination setup failed",e);mc.execute(mc::stop);}});}
        ticks++;
        if(ticks==40)mc.setScreen(new MindScreen(POS));
        if(ticks==70)capture("08-mind-interface.png");
        if(ticks==80)mc.setScreen(new WorkbenchScreen(null));
        if(ticks==100)capture("09-imagination-editor.png");
        if(ticks==110)mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Imagination capture")){
            @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){}
            @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){}
            @Override public boolean isPauseScreen(){return false;}
        });
        if(ticks>=110&&ticks<=350&&ticks%2==0)capture(String.format(java.util.Locale.ROOT,"imagination-frame-%03d.png",ticks));
        if(ticks==360){var core=(MindCoreEntity)mc.level.getBlockEntity(POS);if(core!=null)JevMod.LOGGER.info("Imagination final memory={}, error={}",core.memory(),core.error());capture("10-mind-in-world.png");}
        if(ticks==380){JevMod.LOGGER.info("Imagination client check complete");mc.stop();}
    }
    private static void capture(String name){var mc=Minecraft.getInstance();try{var dir=mc.gameDirectory.toPath().resolve("showcase");Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(name));}if(!name.startsWith("imagination-frame"))JevMod.LOGGER.info("Imagination screenshot: {}",name);}catch(Exception e){JevMod.LOGGER.error("Imagination capture failed",e);}}
}
