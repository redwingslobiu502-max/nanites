package dev.jevmod.client;

import dev.jevmod.JevMod;
import dev.jevmod.api.mind.MindContext;
import dev.jevmod.body.*;
import dev.jevmod.mind.*;
import dev.jevmod.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.nio.file.*;
import java.util.*;

/** Disposable real-client check of drag/drop, preview, actual import/delete callbacks and Mind-triggered assembly. */
final class QolCheck {
    private static boolean started,joined;private static int ticks;
    private static final BlockPos BERCO=new BlockPos(0,-59,0),CORE=new BlockPos(-2,-59,0);
    private static Path js,json;private static String id;
    static void tick(){var mc=Minecraft.getInstance();try{
        if(!started&&mc.screen instanceof TitleScreen){started=true;mc.createWorldOpenFlows().createFreshLevel("jevmod-qol-check-"+System.currentTimeMillis(),new LevelSettings("QoL disposable check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(482101L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);}
        if(!started||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!joined){joined=true;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);id="qol_drop_"+System.currentTimeMillis();var dir=mc.gameDirectory.toPath().resolve("showcase/qol-inputs");Files.createDirectories(dir);js=dir.resolve(id+".js");json=dir.resolve(id+".json");Files.writeString(js,"function onTick(ctx, config) {\n    var p = ctx.body.pos();\n    var contacts = ctx.entities.scan(0, 64, 0, 256, 'hostile');\n    ctx.memory.set('contacts', contacts.length);\n}\n");Files.writeString(json,"{\"id\":\""+id+"\",\"engine\":\"javascript\",\"script\":\""+id+".js\",\"enabled\":false}");var playerId=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();level.setBlock(CORE,MindContent.CORE.get().defaultBlockState(),3);var core=(MindCoreEntity)level.getBlockEntity(CORE);core.claim(playerId);core.useSystems(List.of());var state=BodyContent.BERCO.get().defaultBlockState();level.setBlock(BERCO,state,3);level.setBlock(BERCO.above(),state.setValue(BercoBlock.HALF,DoubleBlockHalf.UPPER),3);var ctx=new MindContext(core,SystemDef.parse(new com.google.gson.JsonObject(),"qol_check"));ctx.call("jev:core","berco.begin",Json.parseObject("{\"x\":0,\"y\":-59,\"z\":0,\"spec\":{\"duration_ticks\":200,\"resistances\":[\"minecraft:magic\"],\"metadata\":{\"team\":\"qol\",\"generation\":1}}}"));level.setDayTime(6000);var player=level.getServer().getPlayerList().getPlayer(playerId);player.connection.teleport(3.5,-59,3.5,135,16);}catch(Exception e){JevMod.LOGGER.error("QoL setup failed",e);mc.execute(mc::stop);}});
            mc.setScreen(new WorkbenchScreen(null));
        }
        ticks++;
        if(ticks==40)((WorkbenchScreen)mc.screen).onFilesDrop(List.of(js,json));
        if(ticks==60)capture("11-qol-import-preview.png");
        if(ticks==70)((ContentImportScreen)mc.screen).importAll();
        if(ticks==90){if(!Files.isRegularFile(JevMod.CONTENT_DIR.resolve("systems/"+id+".js")))throw new IllegalStateException("Actual import did not write file");capture("12-qol-editor.png");}
        if(ticks==100)((WorkbenchScreen)mc.screen).deleteCurrent();
        if(ticks==110)capture("13-qol-delete-confirm.png");
        if(ticks==120){var yes=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(Component.translatable("gui.yes").getString())).map(w->(Button)w).findFirst().orElseThrow();yes.onPress();}
        if(ticks==130){var content=new LocalContent(JevMod.CONTENT_DIR);if(Files.exists(content.path("systems/"+id+".js"))||!LocalContent.isDeleted(JevMod.CONTENT_DIR,"systems/"+id+".js"))throw new IllegalStateException("Actual GUI deletion did not persist");content.delete("systems/"+id+".json");JevMod.LOGGER.info("QoL actual drag/drop import and confirmed GUI deletion passed");}
        if(ticks==140)mc.setScreen(new WorkbenchScreen(BERCO));
        if(ticks==170)capture("14-qol-mind-assembly.png");
        if(ticks==230)mc.getSingleplayerServer().execute(()->{try{var core=(MindCoreEntity)mc.getSingleplayerServer().overworld().getBlockEntity(CORE);var ctx=new MindContext(core,SystemDef.parse(new com.google.gson.JsonObject(),"qol_check"));ctx.call("jev:core","berco.release",Json.parseObject("{\"x\":0,\"y\":-59,\"z\":0,\"bind\":true}"));if(!(core.body() instanceof GeneratedBody b))throw new IllegalStateException("Mind failed to release/bind");b.hurt(b.damageSources().magic(),10);JevMod.LOGGER.info("QoL bound body health={}, damage={}, spec={}",ctx.body.health(),core.lastBodyDamage(),b.spec().json());}catch(Exception e){JevMod.LOGGER.error("QoL release failed",e);mc.execute(mc::stop);}});
        if(ticks==240)mc.setScreen(new Screen(Component.literal("QoL capture")){@Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float p){}@Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int x,int y,float p){}@Override public boolean isPauseScreen(){return false;}});
        if(ticks>=250&&ticks<=490&&ticks%2==0)capture(String.format(java.util.Locale.ROOT,"qol-frame-%03d.png",ticks));
        if(ticks==500){capture("15-qol-mind-body.png");JevMod.LOGGER.info("QoL client check complete");mc.stop();}
    }catch(Exception e){JevMod.LOGGER.error("QoL client check failed",e);mc.stop();}}
    private static void capture(String name)throws Exception{var mc=Minecraft.getInstance();var dir=mc.gameDirectory.toPath().resolve("showcase");Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(name));}if(!name.startsWith("qol-frame"))JevMod.LOGGER.info("QoL screenshot: {}",name);}
}
