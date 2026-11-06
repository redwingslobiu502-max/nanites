package dev.jevmod.body;

import dev.jevmod.JevMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BodyContent {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(JevMod.MODID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(JevMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,JevMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,JevMod.MODID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<BercoBlock> BERCO=BLOCKS.registerBlock("berco",BercoBlock::new,BlockBehaviour.Properties.of().strength(2).noOcclusion());
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> BERCO_ITEM=ITEMS.registerSimpleBlockItem(BERCO);
    public static final java.util.function.Supplier<BlockEntityType<BercoEntity>> BERCO_ENTITY=BLOCK_ENTITIES.register("berco",()->BlockEntityType.Builder.of(BercoEntity::new,BERCO.get()).build(null));
    public static final java.util.function.Supplier<EntityType<GeneratedBody>> BODY=ENTITIES.register("body",()->EntityType.Builder.of(GeneratedBody::new,MobCategory.CREATURE).sized(0.6f,1.8f).clientTrackingRange(10).build("jevmod:body"));
    public static java.util.function.Consumer<BlockPos> openBerco=p->{};
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);BLOCK_ENTITIES.register(bus);ENTITIES.register(bus);bus.addListener(BodyContent::attributes);}
    private static void attributes(EntityAttributeCreationEvent event){event.put(BODY.get(),net.minecraft.world.entity.Mob.createMobAttributes().build());}
}
