package dev.nanites.forge;

import dev.nanites.game.GameService;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.*;
import java.util.*;

@Mod("nanites")
public final class NanitesForge {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"nanites");
    public static final RegistryObject<Item> BOX=ITEMS.register("box",()->new Box());
    public static final RegistryObject<Item> STORAGE=ITEMS.register("reservoir",()->new Item(new Item.Properties().stacksTo(1)));
    private GameService service;
    private MinecraftServer server;
    private final Map<UUID,Long> vfx=new HashMap<>();
    private long vfxTick=-1; private int globalVfx;
    public NanitesForge(){ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());MinecraftForge.EVENT_BUS.register(this);}
    private static NanitesForge active;
    @SubscribeEvent public void start(ServerStartedEvent e){server=e.getServer();active=this;try{service=new GameService(server.getWorldPath(LevelResource.ROOT),FMLPaths.CONFIGDIR.get(),new GameService.Host(){
        public void message(UUID id,String text){var p=server.getPlayerList().getPlayer(id);if(p!=null)p.sendSystemMessage(Component.literal(text));}
        public void main(Runnable r){var target=server;if(target!=null)target.execute(r);}public boolean online(UUID id){return server!=null&&server.getPlayerList().getPlayer(id)!=null;}
        public boolean operator(UUID id){var p=server.getPlayerList().getPlayer(id);return p!=null&&p.hasPermissions(2);}
        public List<String> mods(){return ModList.get().getMods().stream().map(m->m.getModId()+"@"+m.getVersion()).sorted().toList();}
        public Map<String,java.nio.file.Path> modFiles(){var m=new HashMap<String,java.nio.file.Path>();for(var info:ModList.get().getMods())m.put(info.getModId(),ModList.get().getModFileById(info.getModId()).getFile().getFilePath());return Map.copyOf(m);}
        public String loader(){return "forge";}public String minecraft(){return "1.20.1";}public String loaderVersion(){return net.minecraftforge.versions.forge.ForgeVersion.getVersion();}
        public void particles(UUID id,String shape,int radius){var p=server.getPlayerList().getPlayer(id);if(p==null)return;long t=p.serverLevel().getGameTime();if(t-vfx.getOrDefault(id,-100L)<20)return;if(vfxTick!=t){vfxTick=t;globalVfx=0;}if(globalVfx+24>256)return;globalVfx+=24;vfx.put(id,t);
            for(int i=0;i<24;i++){double a=i*Math.PI/12;double y=shape.equals("shape.column")?(i%8)*0.4:1; p.serverLevel().sendParticles(ParticleTypes.END_ROD,p.getX()+Math.cos(a)*radius*0.4,p.getY()+y,p.getZ()+Math.sin(a)*radius*0.4,1,0,0,0,0);}}
    });}catch(Exception x){throw new IllegalStateException("Nanites configuration/state error",x);}}
    @SubscribeEvent public void stop(ServerStoppingEvent e){if(service!=null)service.close();service=null;active=null;}
    @SubscribeEvent public void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("nanites").requires(s->s.getEntity() instanceof ServerPlayer).executes(c->{service.command(c.getSource().getPlayerOrException().getUUID(),"help");return 1;}).then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->{if(service!=null){var p=c.getSource().getPlayerOrException();String text=StringArgumentType.getString(c,"text");if(text.equals("absorb")){var held=p.getMainHandItem();if(!held.isEmpty())service.absorb(p.getUUID(),ForgeRegistries.ITEMS.getKey(held.getItem()).toString(),()->held.shrink(1));}else service.command(p.getUUID(),text);}return 1;})));}
    @SubscribeEvent public void hurt(LivingHurtEvent e){if(service==null||!(e.getEntity() instanceof ServerPlayer p))return;String kind=e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)?"fall":"impact";float reduced=service.damage(p.getUUID(),kind,e.getAmount(),p.fallDistance,e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE));e.setAmount(reduced);if(reduced<=0)e.setCanceled(true);}
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&service!=null&&e.player instanceof ServerPlayer p)service.tick(p.getUUID(),p.serverLevel().getGameTime());}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(service!=null)service.login(e.getEntity().getUUID());}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){vfx.remove(e.getEntity().getUUID());if(service!=null)service.logout(e.getEntity().getUUID());}
    private static final class Box extends Item {
        Box(){super(new Item.Properties().stacksTo(16));}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){ItemStack stack=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(stack);
            if(hand!=InteractionHand.MAIN_HAND||stack.isEmpty()||!stack.is(this)||player.getCooldowns().isOnCooldown(this)||active==null||active.service==null)return InteractionResultHolder.fail(stack);
            boolean reservoir=player.getInventory().items.stream().anyMatch(s->s.is(STORAGE.get()));
            if(!reservoir){player.sendSystemMessage(Component.literal("Carregue um reservatório nanites:reservoir no inventário."));return InteractionResultHolder.fail(stack);}
            boolean ok=active.service.deposit(player.getUUID(),()->{if(stack.isEmpty()||!stack.is(this))throw new IllegalStateException("Box unavailable");stack.shrink(1);});if(ok)player.getCooldowns().addCooldown(this,10);return ok?InteractionResultHolder.consume(stack):InteractionResultHolder.fail(stack);}
    }
}
