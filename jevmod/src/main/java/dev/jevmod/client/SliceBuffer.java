package dev.jevmod.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Clips actual textured polygons at a body-local horizontal plane, preserving UVs and normals. */
public final class SliceBuffer implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final Matrix4f inverse;
    private final float height;
    private final net.minecraft.resources.ResourceLocation override;
    private Consumer current;
    public SliceBuffer(MultiBufferSource delegate,Matrix4f root,float height,net.minecraft.resources.ResourceLocation override){this.delegate=delegate;this.inverse=new Matrix4f(root).invert();this.height=height;this.override=override;}
    @Override public VertexConsumer getBuffer(RenderType type){
        finish();
        int size=type.mode()==VertexFormat.Mode.QUADS?4:type.mode()==VertexFormat.Mode.TRIANGLES?3:0;
        VertexConsumer target=delegate.getBuffer(override!=null&&size>0?RenderType.entityCutoutNoCull(override):type);
        if(size==0)return target;
        current=new Consumer(target,size,override!=null?4:size,inverse,height);return current;
    }
    public void finish(){if(current!=null){current.finish();current=null;}}
    private static final class Consumer implements VertexConsumer {
        private final VertexConsumer out;private final int size,outputSize;private final Matrix4f inverse;private final float height;
        private final List<float[]> vertices=new ArrayList<>();private float[] vertex;
        Consumer(VertexConsumer out,int size,int outputSize,Matrix4f inverse,float height){this.out=out;this.size=size;this.outputSize=outputSize;this.inverse=inverse;this.height=height;}
        @Override public VertexConsumer addVertex(float x,float y,float z){commit();vertex=new float[]{x,y,z,255,255,255,255,0,0,0,0,0,0,0,1,0};return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){vertex[3]=r;vertex[4]=g;vertex[5]=b;vertex[6]=a;return this;}
        @Override public VertexConsumer setUv(float u,float v){vertex[7]=u;vertex[8]=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){vertex[9]=u;vertex[10]=v;return this;}
        @Override public VertexConsumer setUv2(int u,int v){vertex[11]=u;vertex[12]=v;return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){vertex[13]=x;vertex[14]=y;vertex[15]=z;return this;}
        void finish(){commit();vertices.clear();}
        private void commit(){if(vertex==null)return;vertices.add(vertex);vertex=null;if(vertices.size()==size){emit();vertices.clear();}}
        private float localY(float[] v){return inverse.transformPosition(v[0],v[1],v[2],new Vector3f()).y;}
        private void emit(){
            List<float[]> clipped=dev.jevmod.body.HeightClip.below(vertices,height,this::localY);
            for(int i=1;i<clipped.size()-1;i++){write(clipped.getFirst());write(clipped.get(i));write(clipped.get(i+1));if(outputSize==4)write(clipped.get(i+1));}
        }
        private void write(float[] p){out.addVertex(p[0],p[1],p[2]).setColor(Math.round(p[3]),Math.round(p[4]),Math.round(p[5]),Math.round(p[6]))
                .setUv(p[7],p[8]).setUv1(Math.round(p[9]),Math.round(p[10])).setUv2(Math.round(p[11]),Math.round(p[12])).setNormal(p[13],p[14],p[15]);}
    }
}
