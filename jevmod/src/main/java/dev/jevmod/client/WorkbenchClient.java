package dev.jevmod.client;

import dev.jevmod.body.BodyContent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;

public final class WorkbenchClient {
    private static final KeyMapping OPEN=new KeyMapping("key.jevmod.workbench",org.lwjgl.glfw.GLFW.GLFW_KEY_F8,"key.categories.jevmod");
    public static void init(IEventBus bus){
        bus.addListener((RegisterKeyMappingsEvent e)->e.register(OPEN));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{e.registerBlockEntityRenderer(BodyContent.BERCO_ENTITY.get(),BercoRenderer::new);e.registerEntityRenderer(BodyContent.BODY.get(),BodyRenderer::new);});
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{var mc=Minecraft.getInstance();while(OPEN.consumeClick())if(mc.player!=null)mc.setScreen(new WorkbenchScreen(null));});
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{if(!net.neoforged.fml.loading.FMLLoader.isProduction()&&Boolean.getBoolean("jevmod.devWorkbenchCheck"))WorkbenchCheck.tick();});
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{if(!net.neoforged.fml.loading.FMLLoader.isProduction()&&Boolean.getBoolean("jevmod.devImaginationCheck"))ImaginationCheck.tick();});
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{if(!net.neoforged.fml.loading.FMLLoader.isProduction()&&Boolean.getBoolean("jevmod.devQolCheck"))QolCheck.tick();});
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e)->e.getDispatcher().register(net.minecraft.commands.Commands.literal("jevui").executes(ctx->{Minecraft.getInstance().execute(()->Minecraft.getInstance().setScreen(new WorkbenchScreen(null)));return 1;})));
        BodyContent.openBerco=pos->Minecraft.getInstance().setScreen(new WorkbenchScreen(pos));
        dev.jevmod.mind.MindContent.openMind=pos->Minecraft.getInstance().setScreen(new MindScreen(pos));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->e.registerEntityRenderer(dev.jevmod.mind.MindContent.NUGGET.get(),net.minecraft.client.renderer.entity.ThrownItemRenderer::new));
    }
}
