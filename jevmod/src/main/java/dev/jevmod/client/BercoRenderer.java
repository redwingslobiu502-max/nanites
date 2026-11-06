package dev.jevmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jevmod.body.BercoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;

public final class BercoRenderer implements BlockEntityRenderer<BercoEntity> {
    public BercoRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(BercoEntity job,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.pushPose();pose.translate(0.5,0.125,0.5);
        BodyVisual.render(job.spec(),job.progress(),pose,buffers,light);
        if(job.running()){
            float y=(float)(1.8*job.progress());var plane=buffers.getBuffer(net.minecraft.client.renderer.RenderType.debugFilledBox());
            for(float[] point:new float[][]{{-.39f,y,-.39f},{-.39f,y,.39f},{.39f,y,.39f},{.39f,y,-.39f}})
                plane.addVertex(pose.last(),point[0],point[1],point[2]).setColor(65,195,255,100);
        }
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(BercoEntity entity){return true;}
}
