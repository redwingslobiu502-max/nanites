package dev.jevmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jevmod.body.GeneratedBody;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class BodyRenderer extends EntityRenderer<GeneratedBody> {
    public BodyRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0.3f;}
    @Override public ResourceLocation getTextureLocation(GeneratedBody entity){return ResourceLocation.fromNamespaceAndPath("jevmod","textures/block/berco.png");}
    @Override public void render(GeneratedBody body,float yaw,float partialTick,PoseStack pose,MultiBufferSource buffers,int light){pose.pushPose();pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));BodyVisual.render(body.spec(),1,pose,buffers,light);pose.popPose();super.render(body,yaw,partialTick,pose,buffers,light);}
}
