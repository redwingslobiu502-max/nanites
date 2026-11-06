package dev.jevmod.mind;

import dev.jevmod.body.BodyContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Reuses the mod's existing deferred registries; register before they are attached to the bus. */
public final class MindContent {
    public static final net.neoforged.neoforge.registries.DeferredBlock<MindCoreBlock> CORE=BodyContent.BLOCKS.registerBlock("mind_core",MindCoreBlock::new,BlockBehaviour.Properties.of().strength(3).lightLevel(s->8));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> CORE_ITEM=BodyContent.ITEMS.registerSimpleBlockItem(CORE);
    public static final java.util.function.Supplier<BlockEntityType<MindCoreEntity>> CORE_ENTITY=BodyContent.BLOCK_ENTITIES.register("mind_core",()->BlockEntityType.Builder.of(MindCoreEntity::new,CORE.get()).build(null));
    public static final java.util.function.Supplier<EntityType<NuggetProjectile>> NUGGET=BodyContent.ENTITIES.register("nugget",()->EntityType.Builder.<NuggetProjectile>of(NuggetProjectile::new,MobCategory.MISC).sized(.15f,.15f).clientTrackingRange(8).updateInterval(1).build("jevmod:nugget"));
    public static java.util.function.Consumer<BlockPos> openMind=pos->{};
    public static void init(){}
}
