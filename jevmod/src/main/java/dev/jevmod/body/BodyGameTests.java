package dev.jevmod.body;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.*;

/** Isolated Minecraft world verification; never opens or edits user saves. */
@GameTestHolder("jevmod")
@PrefixGameTestTemplate(false)
public final class BodyGameTests {
    @GameTest(template="empty",timeoutTicks=80)
    public static void timedAssemblyAndSingleRelease(GameTestHelper h) throws Exception {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,1,3));
        var state=BodyContent.BERCO.get().defaultBlockState();
        level.setBlock(pos,state,2);
        level.setBlock(pos.above(),state.setValue(BercoBlock.HALF,DoubleBlockHalf.UPPER),3);
        var job=(BercoEntity)level.getBlockEntity(pos);
        h.assertTrue(job!=null,"Lower half must own the job");
        h.assertTrue(level.getBlockEntity(pos.above())==null,"Upper half cannot run a second job");
        job.begin(new BodySpec("player","classic","","",20));
        h.runAtTickTime(10,()->{
            h.assertTrue(job.running()&&!job.ready(),"Generation must take the configured time");
            h.assertTrue(job.progress()>0.3&&job.progress()<0.7,"World ticks must advance spatial assembly");
            var restored=new BercoEntity(pos,state);
            restored.loadWithComponents(job.saveWithoutMetadata(level.registryAccess()),level.registryAccess());
            h.assertTrue(restored.elapsed()==job.elapsed()&&restored.running(),"NBT must preserve the unfinished job");
        });
        h.runAtTickTime(23,()->{
            h.assertTrue(job.ready()&&!job.running()&&job.elapsed()==20,"Completion must clamp to the exact duration");
            job.release();
            h.assertTrue(!job.ready(),"Release must consume the completed job");
            var bounds=new net.minecraft.world.phys.AABB(pos).inflate(4);
            h.assertTrue(level.getEntitiesOfClass(GeneratedBody.class,bounds).size()==1,"One physical body must be spawned");
            job.release();
            h.assertTrue(level.getEntitiesOfClass(GeneratedBody.class,bounds).size()==1,"Repeated release must not duplicate bodies");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void cancelledJobCannotComplete(GameTestHelper h)throws Exception {
        var pos=h.absolutePos(new BlockPos(3,1,3));var state=BodyContent.BERCO.get().defaultBlockState();
        h.getLevel().setBlock(pos,state,2);h.getLevel().setBlock(pos.above(),state.setValue(BercoBlock.HALF,DoubleBlockHalf.UPPER),3);
        var job=(BercoEntity)h.getLevel().getBlockEntity(pos);job.begin(new BodySpec("mob","minecraft:zombie","","",20));job.cancel();
        h.runAtTickTime(23,()->{h.assertTrue(!job.running()&&!job.ready()&&job.elapsed()==0,"Cancelled job must stay cancelled");h.succeed();});
    }
}
