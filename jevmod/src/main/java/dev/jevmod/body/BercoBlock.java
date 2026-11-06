package dev.jevmod.body;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Two-block chamber with a player-height interior; only the lower half owns the job. */
public final class BercoBlock extends BaseEntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final MapCodec<BercoBlock> CODEC=simpleCodec(BercoBlock::new);
    public BercoBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(HALF,DoubleBlockHalf.LOWER));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(HALF);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx){BlockPos p=ctx.getClickedPos();return p.getY()<ctx.getLevel().getMaxBuildHeight()-1&&ctx.getLevel().getBlockState(p.above()).canBeReplaced(ctx)?defaultBlockState():null;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){level.setBlock(pos.above(),defaultBlockState().setValue(HALF,DoubleBlockHalf.UPPER),3);}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos){
        boolean lower=state.getValue(HALF)==DoubleBlockHalf.LOWER;
        if(direction==(lower?Direction.UP:Direction.DOWN)&&(!neighbor.is(this)||neighbor.getValue(HALF)==state.getValue(HALF)))return Blocks.AIR.defaultBlockState();
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext ctx){
        VoxelShape columns=Shapes.or(box(0,0,0,2,16,2),box(14,0,0,16,16,2),box(0,0,14,2,16,16),box(14,0,14,16,16,16));
        return Shapes.or(columns,state.getValue(HALF)==DoubleBlockHalf.LOWER?box(0,0,0,16,2,16):box(0,15,0,16,16,16));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(HALF)==DoubleBlockHalf.LOWER?new BercoEntity(pos,state):null;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,BodyContent.BERCO_ENTITY.get(),BercoEntity::tick);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.isClientSide)BodyContent.openBerco.accept(state.getValue(HALF)==DoubleBlockHalf.LOWER?pos:pos.below());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
