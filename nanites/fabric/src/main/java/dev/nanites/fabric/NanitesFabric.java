package dev.nanites.fabric;

import dev.nanites.game.GameService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;

public final class NanitesFabric implements ModInitializer {
    public static GameService service;
    private static MinecraftServer server;
    public static final Item BOX=new Box();
    public static final Item STORAGE=new Item(new Item.Properties().stacksTo(1));
    private final Map<UUID,Long> vfx=new HashMap<>();private long vfxTick=-1;private int globalVfx;
    @Override public void onInitialize(){
        Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("nanites","box"),BOX);
        Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("nanites","reservoir"),STORAGE);
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;try{service=new GameService(s.getWorldPath(LevelResource.ROOT),FabricLoader.getInstance().getConfigDir(),new GameService.Host(){
            public void message(UUID id,String text){var p=server.getPlayerList().getPlayer(id);if(p!=null)p.sendSystemMessage(Component.literal(text));}
            public void main(Runnable r){var target=server;if(target!=null)target.execute(r);}public boolean online(UUID id){return server!=null&&server.getPlayerList().getPlayer(id)!=null;}
            public boolean operator(UUID id){var p=server.getPlayerList().getPlayer(id);return p!=null&&p.hasPermissions(2);}
            public List<String> mods(){return FabricLoader.getInstance().getAllMods().stream().map(m->m.getMetadata().getId()+"@"+m.getMetadata().getVersion().getFriendlyString()).sorted().toList();}
            public Map<String,java.nio.file.Path> modFiles(){var result=new HashMap<String,java.nio.file.Path>();for(var m:FabricLoader.getInstance().getAllMods())if(m.getOrigin().getKind().name().equals("PATH")&&!m.getOrigin().getPaths().isEmpty())result.put(m.getMetadata().getId(),m.getOrigin().getPaths().get(0));return Map.copyOf(result);}
            public String loader(){return "fabric";}public String minecraft(){return "1.21.1";}public String loaderVersion(){return FabricLoader.getInstance().getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString();}
            public void particles(UUID id,String shape,int radius){var p=server.getPlayerList().getPlayer(id);if(p==null)return;long t=p.serverLevel().getGameTime();if(t-vfx.getOrDefault(id,-100L)<20)return;if(vfxTick!=t){vfxTick=t;globalVfx=0;}if(globalVfx+24>256)return;globalVfx+=24;vfx.put(id,t);
                for(int i=0;i<24;i++){double a=i*Math.PI/12;double y=shape.equals("shape.column")?(i%8)*0.4:1;p.serverLevel().sendParticles(ParticleTypes.END_ROD,p.getX()+Math.cos(a)*radius*0.4,p.getY()+y,p.getZ()+Math.sin(a)*radius*0.4,1,0,0,0,0);}}
        });}catch(Exception x){throw new IllegalStateException("Nanites configuration/state error",x);}});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{if(service!=null)service.close();service=null;server=null;});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(service!=null)for(var p:s.getPlayerList().getPlayers())service.tick(p.getUUID(),p.serverLevel().getGameTime());});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,s)->{if(service!=null)service.login(handler.player.getUUID());});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,s)->{vfx.remove(handler.player.getUUID());if(service!=null)service.logout(handler.player.getUUID());});
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(Commands.literal("nanites").requires(s->s.getEntity() instanceof ServerPlayer).executes(c->{if(service!=null)service.command(c.getSource().getPlayerOrException().getUUID(),"help");return 1;}).then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->{if(service!=null){var p=c.getSource().getPlayerOrException();String text=StringArgumentType.getString(c,"text");if(text.equals("absorb")){var held=p.getMainHandItem();if(!held.isEmpty())service.absorb(p.getUUID(),BuiltInRegistries.ITEM.getKey(held.getItem()).toString(),()->held.shrink(1));}else service.command(p.getUUID(),text);}return 1;}))));
    }
    private static final class Box extends Item {
        Box(){super(new Item.Properties().stacksTo(16));}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(stack);if(hand!=InteractionHand.MAIN_HAND||stack.isEmpty()||!stack.is(this)||player.getCooldowns().isOnCooldown(this)||service==null)return InteractionResultHolder.fail(stack);
            boolean reservoir=player.getInventory().items.stream().anyMatch(s->s.is(STORAGE));if(!reservoir){player.sendSystemMessage(Component.literal("Carregue nanites:reservoir no inventário."));return InteractionResultHolder.fail(stack);}
            boolean ok=service.deposit(player.getUUID(),()->{if(stack.isEmpty()||!stack.is(this))throw new IllegalStateException("Box unavailable");stack.shrink(1);});if(ok)player.getCooldowns().addCooldown(this,10);return ok?InteractionResultHolder.consume(stack):InteractionResultHolder.fail(stack);}
    }
}
