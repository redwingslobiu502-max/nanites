package dev.jevmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jevmod.JevMod;
import dev.jevmod.body.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.nio.file.Files;
import java.util.*;

/** One appearance path shared by chamber preview, editor preview and the completed physical body. */
public final class BodyVisual {
    private static final Map<String,ResourceLocation> TEXTURES=new LinkedHashMap<>();
    private static final Map<String,ObjMesh> MESHES=new LinkedHashMap<>();
    private static final Map<String,LivingEntity> MOBS=new LinkedHashMap<>();
    private static PlayerModel<ArmorStand> playerModel;
    private static DynamicTexture assemblyTexture;
    private static ResourceLocation assemblyId;
    private static String assemblySource="";
    private static int assemblyLayer=-1;
    private static Object scene;
    private static final Set<String> WARNED=new HashSet<>();

    public static void reset(){var mc=Minecraft.getInstance();for(var t:TEXTURES.values())mc.getTextureManager().release(t);if(assemblyId!=null)mc.getTextureManager().release(assemblyId);assemblyTexture=null;assemblyId=null;assemblyLayer=-1;TEXTURES.clear();MESHES.clear();MOBS.clear();playerModel=null;WARNED.clear();scene=mc.level;}
    public static ResourceLocation assembledSkin(BodySpec spec,double progress)throws Exception {
        if(!spec.kind().equals("player")||progress>=1)return texture(spec.texture());
        int layer=(int)Math.floor(progress*128);
        if(assemblyTexture==null){assemblyTexture=new DynamicTexture(new NativeImage(64,64,true));assemblyId=Minecraft.getInstance().getTextureManager().register("jevassembly",assemblyTexture);}
        if(layer!=assemblyLayer||!spec.texture().equals(assemblySource)){
            int[] source=SkinAssembly.defaultPixels();
            if(!spec.texture().isEmpty()){var loaded=javax.imageio.ImageIO.read(JevMod.CONTENT_DIR.resolve("assets").resolve(spec.texture()).toFile());source=loaded.getRGB(0,0,64,64,null,0,64);}
            var skin=new SkinAssembly(source);skin.advance(new AssemblyProgress(layer,128));int[] pixels=skin.pixels();
            for(int y=0;y<64;y++)for(int x=0;x<64;x++){int c=pixels[y*64+x];assemblyTexture.getPixels().setPixelRGBA(x,y,(c&0xff00ff00)|((c>>16)&255)|((c&255)<<16));}
            assemblyTexture.upload();assemblyLayer=layer;assemblySource=spec.texture();
        }
        return assemblyId;
    }
    public static void render(BodySpec spec,double progress,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();if(mc.level==null||progress<=0)return;if(scene!=mc.level)reset();
        pose.pushPose();
        try {
            float height=(float)(1.8*Math.min(1,progress));
            ResourceLocation texture=spec.texture().isEmpty()&&spec.kind().equals("mob")?null:texture(spec.texture());
            var sliced=new SliceBuffer(buffers,pose.last().pose(),height,spec.kind().equals("mob")?texture:null);
            if(spec.kind().equals("obj")){
                ObjMesh mesh=MESHES.get(spec.model());if(mesh==null){mesh=ObjMesh.parse(Files.readString(JevMod.CONTENT_DIR.resolve("assets").resolve(spec.model())));if(MESHES.size()>=16)MESHES.clear();MESHES.put(spec.model(),mesh);}
                var consumer=sliced.getBuffer(RenderType.entityCutoutNoCull(texture));
                for(var triangle:mesh.triangles()){
                    var a=triangle.a();var b=triangle.b();var c=triangle.c();
                    var normal=new org.joml.Vector3f(b.x()-a.x(),b.y()-a.y(),b.z()-a.z()).cross(c.x()-a.x(),c.y()-a.y(),c.z()-a.z()).normalize();
                    for(var p:List.of(a,b,c,c))consumer.addVertex(pose.last(),p.x(),p.y(),p.z()).setColor(255,255,255,255).setUv(p.u(),p.v()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),normal.x,normal.y,normal.z);
                }
            }else if(spec.kind().equals("player")){
                if(playerModel==null)playerModel=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
                playerModel.young=false;
                var dummy=new ArmorStand(mc.level,0,0,0);playerModel.setupAnim(dummy,0,0,0,0,0);
                pose.scale(0.72f,-0.9f,-0.72f);pose.translate(0,-1.5,0);
                playerModel.renderToBuffer(pose,sliced.getBuffer(RenderType.entityCutoutNoCull(texture)),light,OverlayTexture.NO_OVERLAY);
            }else{
                LivingEntity entity=MOBS.get(spec.model());
                if(entity==null){var id=ResourceLocation.tryParse(spec.model());var raw=id!=null&&BuiltInRegistries.ENTITY_TYPE.containsKey(id)?BuiltInRegistries.ENTITY_TYPE.get(id).create(mc.level):null;if(!(raw instanceof LivingEntity living))throw new IllegalArgumentException("Mob indisponível");entity=living;if(MOBS.size()>=32)MOBS.clear();MOBS.put(spec.model(),entity);}
                float scale=Math.min(1.8f/Math.max(0.1f,entity.getBbHeight()),0.75f/Math.max(0.1f,entity.getBbWidth()));pose.scale(scale,scale,scale);
                mc.getEntityRenderDispatcher().getRenderer(entity).render(entity,0,0,pose,sliced,light);
            }
            sliced.finish();
        }catch(Exception e){if(WARNED.add(spec.kind()+spec.model()))JevMod.LOGGER.warn("Body preview failed: {}",spec.model(),e);}
        finally{pose.popPose();}
    }
    public static ResourceLocation texture(String name)throws Exception{
        String key=name.isEmpty()?"default":name;ResourceLocation cached=TEXTURES.get(key);if(cached!=null)return cached;
        NativeImage image;
        if(name.isEmpty()){image=new NativeImage(64,64,true);int[] pixels=SkinAssembly.defaultPixels();for(int y=0;y<64;y++)for(int x=0;x<64;x++){int c=pixels[y*64+x];image.setPixelRGBA(x,y,(c&0xff00ff00)|((c>>16)&255)|((c&255)<<16));}}
        else image=NativeImage.read(Files.readAllBytes(JevMod.CONTENT_DIR.resolve("assets").resolve(name)));
        ResourceLocation id=Minecraft.getInstance().getTextureManager().register("jevbody",new DynamicTexture(image));
        if(TEXTURES.size()>=32){String oldest=TEXTURES.keySet().iterator().next();Minecraft.getInstance().getTextureManager().release(TEXTURES.remove(oldest));}TEXTURES.put(key,id);return id;
    }
}
