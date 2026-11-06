package dev.jevmod.client;

import dev.jevmod.JevMod;
import dev.jevmod.body.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.nio.file.*;

/** Opt-in dev task: creates a disposable offline world and captures actual rendered screens. No OS input. */
final class WorkbenchCheck {
    private static boolean started,joined;
    private static int ticks;
    private static final BlockPos BERCO=new BlockPos(0,-59,0);
    static void tick(){
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen instanceof TitleScreen){
            started=true;
            mc.createWorldOpenFlows().createFreshLevel("jevmod-ui-check-"+System.currentTimeMillis(),
                    new LevelSettings("JevMod disposable UI check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(482101L,false,false),
                    registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if(!started||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!joined){joined=true;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.setScreen(new WorkbenchScreen(null));}
        ticks++;
        if(ticks==40)capture("01-code-brain.png");
        if(ticks==50){
            mc.getSingleplayerServer().execute(()->{
                try{var level=mc.getSingleplayerServer().overworld();var state=BodyContent.BERCO.get().defaultBlockState();level.setBlock(BERCO,state,2);level.setBlock(BERCO.above(),state.setValue(BercoBlock.HALF,DoubleBlockHalf.UPPER),3);
                    var job=(BercoEntity)level.getBlockEntity(BERCO);job.begin(new BodySpec("player","classic","","",200));
                    mc.execute(()->mc.setScreen(new WorkbenchScreen(BERCO)));
                }catch(Exception e){JevMod.LOGGER.error("Workbench check failed",e);mc.execute(mc::stop);}
            });
        }
        if(ticks==150)capture("02-berco-half.png");
        if(ticks==270)capture("03-berco-complete.png");
        if(ticks==300)startRecipe(new BodySpec("mob","minecraft:zombie","","",40));
        if(ticks==325)mc.setScreen(new WorkbenchScreen(BERCO));
        if(ticks==360)capture("04-registered-mob.png");
        if(ticks==380){
            try{
                Path folder=mc.gameDirectory.toPath().resolve("showcase");Path obj=folder.resolve("original-check-model.obj"),png=folder.resolve("original-check-skin.png");
                Files.writeString(obj,"v -0.3 0 -0.3\nv 0.3 0 -0.3\nv 0.3 0 0.3\nv -0.3 0 0.3\nv 0 1.8 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nf 1/1 2/2 5/3\nf 2/1 3/2 5/3\nf 3/1 4/2 5/3\nf 4/1 1/2 5/3\nf 4/4 3/3 2/2 1/1\n");
                var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,64,64,SkinAssembly.defaultPixels(),0,64);javax.imageio.ImageIO.write(image,"png",png.toFile());
                var content=new LocalContent(JevMod.CONTENT_DIR);startRecipe(new BodySpec("obj",content.importAsset(obj),content.importAsset(png),"",40));
            }catch(Exception e){JevMod.LOGGER.error("Custom asset check failed",e);mc.stop();}
        }
        if(ticks==405){var screen=new WorkbenchScreen(BERCO);mc.setScreen(screen);screen.onFilesDrop(java.util.List.of(mc.gameDirectory.toPath().resolve("showcase/original-check-skin.png"),mc.gameDirectory.toPath().resolve("showcase/original-check-model.obj")));}
        if(ticks==450)capture("05-custom-obj-png.png");
        if(ticks==470){mc.getSingleplayerServer().execute(()->{try{var job=(BercoEntity)mc.getSingleplayerServer().overworld().getBlockEntity(BERCO);job.release();job.begin(new BodySpec("player","classic","","",200));var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.teleportTo(3.5,-59,3.5);player.setYRot(135);player.setXRot(16);player.connection.teleport(3.5,-59,3.5,135,16);mc.execute(()->mc.setScreen(null));}catch(Exception e){JevMod.LOGGER.error("World renderer check failed",e);mc.execute(mc::stop);}});}
        if(ticks==560)capture("06-world-forming.png");
        if(ticks==690)capture("07-world-complete.png");
        if(ticks==710){JevMod.LOGGER.info("Workbench visual check complete; screenshots in run/showcase");mc.stop();}
    }
    private static void startRecipe(BodySpec recipe){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{var job=(BercoEntity)mc.getSingleplayerServer().overworld().getBlockEntity(BERCO);job.cancel();job.begin(recipe);}catch(Exception e){JevMod.LOGGER.error("Appearance check failed",e);mc.execute(mc::stop);}});}
    private static void capture(String name){
        var mc=Minecraft.getInstance();
        try{Path folder=mc.gameDirectory.toPath().resolve("showcase");Files.createDirectories(folder);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name));}
            JevMod.LOGGER.info("Workbench screenshot: {}",name);
        }catch(Exception e){JevMod.LOGGER.error("Workbench capture failed",e);}
    }
}
