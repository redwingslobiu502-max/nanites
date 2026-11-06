package dev.jevmod.body;

import dev.jevmod.JevMod;
import dev.jevmod.core.Json;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Persistent server-side generation job. Completion and release are separate, preventing duplicate bodies. */
public final class BercoEntity extends BlockEntity {
    private BodySpec recipe=BodySpec.defaults();
    private int elapsed;
    private boolean running,ready;
    private String failure="";
    private SkinAssembly skin;
    public BercoEntity(BlockPos pos,BlockState state){super(BodyContent.BERCO_ENTITY.get(),pos,state);}
    public BodySpec spec(){return recipe;}
    public boolean running(){return running;}
    public boolean ready(){return ready;}
    public int elapsed(){return elapsed;}
    public String failure(){return failure;}
    public double progress(){return running||ready?(double)elapsed/recipe.durationTicks():0;}
    public void begin(BodySpec spec)throws Exception {
        if(running||ready)throw new IllegalStateException("Cancele ou libere o corpo atual primeiro");
        validateAssets(spec);recipe=spec;elapsed=0;ready=false;failure="";skin=null;prepareSkin();running=true;sync();
    }
    private void validateAssets(BodySpec spec)throws Exception{
        if(spec.kind().equals("mob")){
            var id=net.minecraft.resources.ResourceLocation.tryParse(spec.model());
            var registry=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
            if(id==null||!registry.containsKey(id)||!(registry.get(id).create(level) instanceof net.minecraft.world.entity.LivingEntity))throw new IllegalArgumentException("Mob não disponível: "+spec.model());
        }
        if(spec.kind().equals("obj"))ObjMesh.parse(Files.readString(asset(spec.model())));
        if(!spec.texture().isEmpty()){
            var image=ImageIO.read(asset(spec.texture()).toFile());
            if(image==null)throw new IllegalArgumentException("PNG inválido");
            if(spec.kind().equals("player")&&(image.getWidth()!=64||image.getHeight()!=64))throw new IllegalArgumentException("Skin humanoide precisa ser PNG 64x64");
        }
        if(!spec.brain().isEmpty()&&!JevMod.runtime().defs().brains.containsKey(spec.brain()))throw new IllegalArgumentException("Cérebro não encontrado");
    }
    private Path asset(String name)throws Exception{
        Path p=JevMod.CONTENT_DIR.resolve("assets").resolve(name);if(Files.isSymbolicLink(p)||Files.isSymbolicLink(p.getParent())||!Files.isRegularFile(p)||Files.size(p)>4_000_000)throw new IllegalArgumentException("Asset ausente/inválido");return p;
    }
    private void prepareSkin()throws Exception{
        if(!recipe.kind().equals("player"))return;
        int[] pixels=SkinAssembly.defaultPixels();
        if(!recipe.texture().isEmpty()){var image=ImageIO.read(asset(recipe.texture()).toFile());pixels=image.getRGB(0,0,64,64,null,0,64);}
        skin=new SkinAssembly(pixels);skin.advance(new AssemblyProgress(elapsed,recipe.durationTicks()));
    }
    public void cancel(){running=false;ready=false;elapsed=0;skin=null;failure="";sync();}
    public void release(){releaseBody();}
    /** API variant returns the actually spawned entity; the original void release contract stays available. */
    public GeneratedBody releaseBody(){
        if(!ready||level==null||level.isClientSide)return null;
        GeneratedBody body=BodyContent.BODY.get().create(level);if(body==null)return null;body.setSpec(recipe);
        for(int[] offset:new int[][]{{0,1},{1,0},{0,-1},{-1,0}}){
            body.moveTo(worldPosition.getX()+0.5+offset[0]*1.5,worldPosition.getY()+0.15,worldPosition.getZ()+0.5+offset[1]*1.5,0,0);
            if(level.noCollision(body)&&level.addFreshEntity(body)){cancel();return body;}
        }
        failure="Libere espaço ao lado do berço";sync();return null;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,BercoEntity job){
        if(!job.running)return;
        try{
            if(job.skin==null)job.prepareSkin();
            AssemblyProgress clock=new AssemblyProgress(job.elapsed,job.recipe.durationTicks()).advance();job.elapsed=clock.elapsed();
            if(job.skin!=null)job.skin.advance(clock);
            if(clock.finished()){job.finish();job.running=false;job.ready=true;job.sync();}
            else {job.setChanged();if(job.elapsed%2==0)job.sync();}
        }catch(Exception e){job.running=false;job.failure=e.getMessage();job.sync();JevMod.LOGGER.warn("Berço generation failed",e);}
    }
    private void finish()throws Exception{
        Path generated=new LocalContent(JevMod.CONTENT_DIR).folder("generated");
        String name=java.util.UUID.randomUUID().toString();
        if(skin!=null){var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,64,64,skin.pixels(),0,64);var bytes=new java.io.ByteArrayOutputStream();ImageIO.write(image,"png",bytes);LocalContent.atomic(generated.resolve(name+".png"),bytes.toByteArray());}
        if(recipe.kind().equals("obj")){
            var mesh=ObjMesh.parse(Files.readString(asset(recipe.model())));var out=new StringBuilder("# JevMod normalized custom body\n");int index=1;
            for(var triangle:mesh.triangles()){
                for(var p:java.util.List.of(triangle.a(),triangle.b(),triangle.c()))out.append("v ").append(p.x()).append(' ').append(p.y()).append(' ').append(p.z()).append('\n').append("vt ").append(p.u()).append(' ').append(1-p.v()).append('\n');
                out.append("f ").append(index).append('/').append(index).append(' ').append(index+1).append('/').append(index+1).append(' ').append(index+2).append('/').append(index+2).append('\n');index+=3;
            }
            LocalContent.atomic(generated.resolve(name+".obj"),out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        var artifact=recipe.json();artifact.addProperty("completed_layers",128);artifact.addProperty("height",1.8);LocalContent.atomic(generated.resolve(name+".json"),Json.PRETTY.toJson(artifact).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putString("Recipe",recipe.json().toString());tag.putInt("Elapsed",elapsed);tag.putBoolean("Running",running);tag.putBoolean("Ready",ready);tag.putString("Failure",failure);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);try{recipe=BodySpec.parse(Json.parseObject(tag.getString("Recipe")));elapsed=Math.max(0,Math.min(recipe.durationTicks(),tag.getInt("Elapsed")));running=tag.getBoolean("Running");ready=tag.getBoolean("Ready");failure=tag.getString("Failure");skin=null;}catch(RuntimeException e){recipe=BodySpec.defaults();elapsed=0;running=ready=false;failure="Receita inválida";}}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata(registries);}
}
