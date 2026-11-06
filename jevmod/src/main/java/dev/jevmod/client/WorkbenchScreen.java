package dev.jevmod.client;

import dev.jevmod.JevMod;
import dev.jevmod.body.*;
import dev.jevmod.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobCategory;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;
import java.util.*;

/** In-game local workbench. All world mutations execute on the integrated server thread. */
public final class WorkbenchScreen extends Screen {
    private final LocalContent content=new LocalContent(JevMod.CONTENT_DIR);
    private final BlockPos cradle;
    private final BrainView brain=new BrainView();
    private final Map<String,String> drafts=new LinkedHashMap<>(),originals=new HashMap<>();
    private List<String> files=List.of(),mobIds=List.of();
    private String current="",status="",kind="player",model="classic",texture="",brainId="";
    private int fileOffset,mobIndex;private double seconds=10;
    private boolean bodyTab;
    private List<String> resistances=List.of();
    private com.google.gson.JsonObject metadata=new com.google.gson.JsonObject();
    private CodeBox editor;
    private EditBox newPath,modelBox,timeBox,brainBox;
    private int viewX,viewY,viewW,viewH;

    public WorkbenchScreen(BlockPos cradle){super(Component.literal("JevMod · Ateliê neural"));this.cradle=cradle;bodyTab=cradle!=null;
        try{files=content.files();String b=files.stream().filter(f->f.startsWith("brains/")).findFirst().orElse("");if(!b.isEmpty()){current=b;readFile(b);brain.update(Path.of(b).getFileName().toString().replace(".json",""),drafts.get(b));}}
        catch(Exception e){status=e.getMessage();}
    }
    @Override protected void init(){
        clearWidgets();int split=Math.max(170,width*3/5);viewX=split+4;viewY=57;viewW=width-viewX-12;viewH=height-82;
        button(12,30,78,"Código",b->{stash();bodyTab=false;rebuildWidgets();});
        button(94,30,86,"Cérebro 2D/3D",b->{brain.flat=!brain.flat;});
        button(184,30,72,"Berço",b->{stash();bodyTab=true;rebuildWidgets();});
        button(width-64,8,52,"Fechar",b->onClose());
        if(bodyTab)initBody(split);else initCode(split);
        if(minecraft.getSingleplayerServer()==null)status="Edição e geração disponíveis no mundo single-player local";
    }
    private void button(int x,int y,int w,String text,net.minecraft.client.gui.components.Button.OnPress action){addRenderableWidget(new BlueButton(x,y,w,text,action));}
    private void initCode(int split){
        int side=Math.min(110,Math.max(76,width/5));int bottom=height-49;
        editor=addRenderableWidget(new CodeBox(side+16,77,Math.max(60,split-side-24),Math.max(40,bottom-105)));
        editor.value(drafts.getOrDefault(current,""));editor.changed=s->{drafts.put(current,s);if(current.startsWith("brains/"))brain.update(Path.of(current).getFileName().toString().replace(".json",""),s);};
        int rows=Math.max(1,(bottom-108)/22);
        for(int i=0;i<rows&&i+fileOffset<files.size();i++){String file=files.get(i+fileOffset);String label=font.plainSubstrByWidth(file,side-8);button(12,77+i*22,side,label,b->select(file));}
        button(12,bottom-26,side/2-2,"↑",b->{stash();fileOffset=Math.max(0,fileOffset-rows);rebuildWidgets();});
        button(14+side/2,bottom-26,side/2-2,"↓",b->{stash();fileOffset=Math.min(Math.max(0,files.size()-rows),fileOffset+rows);rebuildWidgets();});
        newPath=addRenderableWidget(new BlueEditBox(font,12,bottom+2,split-76,20,Component.literal("Novo arquivo")));newPath.setTextColor(0x285875);newPath.setValue("powers/my_power.json");newPath.setMaxLength(100);
        button(split-58,bottom+2,46,"Novo",b->newFile());button(side+16,bottom-22,50,"Excluir",b->deleteCurrent());int saveWidth=Math.min(98,split-side-82);button(split-12-saveWidth,bottom-22,saveWidth,"Salvar e aplicar",b->save());
    }
    private void initBody(int split){
        if(cradle!=null&&minecraft.level!=null&&minecraft.level.getBlockEntity(cradle) instanceof BercoEntity job){if(job.running()||job.ready()){var s=job.spec();kind=s.kind();model=s.model();texture=s.texture();brainId=s.brain();seconds=s.durationTicks()/20.0;resistances=s.resistances();metadata=s.metadata();}}
        mobIds=BuiltInRegistries.ENTITY_TYPE.entrySet().stream().filter(e->e.getValue().getCategory()!=MobCategory.MISC).map(e->e.getKey().location().toString()).sorted().toList();
        button(12,77,Math.max(90,split-24),"Base: "+switch(kind){case "mob"->"Mob registrado";case "obj"->"Modelo OBJ";default->"Humanoide / skin";},b->{rememberBody();kind=kind.equals("player")?"mob":kind.equals("mob")?"obj":"player";model=kind.equals("mob")?"minecraft:zombie":kind.equals("player")?"classic":"";rebuildWidgets();});
        modelBox=addRenderableWidget(new BlueEditBox(font,12,110,split-24,20,Component.literal("Modelo")));modelBox.setMaxLength(160);modelBox.setValue(model);modelBox.setTextColor(0x285875);
        button(12,135,Math.max(50,(split-28)/2),"Próximo mob",b->{rememberBody();if(!mobIds.isEmpty()){kind="mob";model=mobIds.get(mobIndex++%mobIds.size());rebuildWidgets();}});
        button(16+(split-28)/2,135,Math.max(50,(split-28)/2),"Importar PNG/OBJ",b->picker());
        timeBox=addRenderableWidget(new BlueEditBox(font,12,173,Math.max(45,(split-28)/2),20,Component.literal("Tempo em segundos")));timeBox.setMaxLength(8);timeBox.setValue(String.valueOf(seconds));timeBox.setTextColor(0x285875);
        brainBox=addRenderableWidget(new BlueEditBox(font,16+(split-28)/2,173,Math.max(45,(split-28)/2),20,Component.literal("Cérebro opcional")));brainBox.setMaxLength(48);brainBox.setValue(brainId);brainBox.setTextColor(0x285875);
        int row=Math.min(218,height-44);button(12,row,Math.max(50,(split-32)/3),"Gerar",b->worldAction("start"));button(16+(split-32)/3,row,Math.max(50,(split-32)/3),"Cancelar",b->worldAction("cancel"));button(20+2*((split-32)/3),row,Math.max(50,(split-32)/3),"Liberar",b->worldAction("release"));
    }
    private boolean local(){if(minecraft.getSingleplayerServer()!=null&&minecraft.player!=null)return true;status="Abra um mundo single-player local para editar";return false;}
    private void readFile(String file)throws Exception{if(!drafts.containsKey(file)){String s=content.read(file);drafts.put(file,s);originals.put(file,s);}}
    private void stash(){if(!bodyTab&&editor!=null&&!current.isEmpty())drafts.put(current,editor.value());else if(bodyTab)rememberBody();}
    private void select(String file){stash();try{readFile(file);current=file;if(file.startsWith("brains/"))brain.update(Path.of(file).getFileName().toString().replace(".json",""),drafts.get(file));bodyTab=false;rebuildWidgets();}catch(Exception e){status=e.getMessage();}}
    private void newFile(){if(!local())return;stash();try{String name=newPath.getValue();content.path(name);if(files.contains(name)){select(name);return;}String id=Path.of(name).getFileName().toString().replace(".json","").replace(".js","");String template=name.startsWith("systems/")&&name.endsWith(".js")?"function onTick(ctx, config) {\n    // Escreva seu sistema.\n}\n":name.startsWith("systems/")?"{\n  \"id\": \""+id+"\",\n  \"engine\": \"javascript\",\n  \"script\": \""+id+".js\",\n  \"config\": {}\n}\n":name.startsWith("minds/")?"{\n  \"id\": \""+id+"\",\n  \"instructions\": \"Descreva sua Mente\",\n  \"systems\": []\n}\n":name.endsWith(".js")?"function run(ctx, params) {\n    ctx.say(\"Pronto\");\n}\n":name.startsWith("brains/")?"{\n  \"id\": \""+id+"\",\n  \"powers\": [\"*\"],\n  \"instructions\": \"Siga os pedidos do jogador.\"\n}\n":name.startsWith("bodies/")?Json.PRETTY.toJson(BodySpec.defaults().json()):"{\n  \"id\": \""+id+"\",\n  \"kind\": \"instant\",\n  \"description\": \"Descreva o poder\",\n  \"cooldown_ticks\": 40\n}\n";drafts.put(name,template);originals.put(name,"");var updated=new ArrayList<>(files);updated.add(name);Collections.sort(updated);files=updated;current=name;rebuildWidgets();status="Novo rascunho · salve para aplicar";}catch(Exception e){status=e.getMessage();}}
    private void save(){if(!local())return;stash();try{content.save(current,drafts.get(current));originals.put(current,drafts.get(current));minecraft.getSingleplayerServer().execute(()->{var problems=JevMod.runtime().reload();minecraft.execute(()->status=problems.isEmpty()?"Salvo · histórico preservado · aplicado":"Salvo; "+problems.getFirst());});}catch(Exception e){status=e.getMessage();}}
    void deleteCurrent(){if(!local()||current.isEmpty())return;stash();String target=current;
        minecraft.setScreen(new ConfirmScreen(yes->{if(yes)try{content.delete(target);drafts.remove(target);originals.remove(target);files=content.files();current=files.isEmpty()?"":files.getFirst();if(!current.isEmpty())readFile(current);fileOffset=Math.min(fileOffset,Math.max(0,files.size()-1));status="Excluído · cópia no histórico";reloadContent();}catch(Exception e){status=e.getMessage();}minecraft.setScreen(this);},Component.literal("Excluir "+target+"?"),Component.literal("O arquivo salvo terá uma cópia no histórico. Rascunhos não salvos desse arquivo serão descartados.")));
    }
    boolean unsaved(String path){return dirty(path);}
    void importCompleted(List<ContentImport.Plan> plans)throws Exception{for(var p:plans){drafts.remove(p.relative());originals.remove(p.relative());}files=content.files();current=plans.getFirst().relative();readFile(current);fileOffset=Math.max(0,files.indexOf(current));bodyTab=false;status="Importados "+plans.size()+" arquivos · histórico preservado";reloadContent();}
    private void reloadContent(){minecraft.getSingleplayerServer().execute(()->{var problems=JevMod.runtime().reload();if(!problems.isEmpty())minecraft.execute(()->status="Conteúdo aplicado; "+problems.getFirst());});}
    private void rememberBody(){if(modelBox!=null)model=modelBox.getValue();if(brainBox!=null)brainId=brainBox.getValue();if(timeBox!=null)try{seconds=Double.parseDouble(timeBox.getValue());}catch(NumberFormatException ignored){}}
    private BodySpec selected(){rememberBody();try{seconds=Double.parseDouble(timeBox.getValue());}catch(NumberFormatException e){throw new IllegalArgumentException("Tempo: informe segundos (1 a 3600)");}if(!Double.isFinite(seconds)||seconds<1||seconds>3600)throw new IllegalArgumentException("Tempo: 1 a 3600 segundos");return new BodySpec(kind,model,texture,brainId,(int)Math.round(seconds*20),resistances,metadata);}
    private void worldAction(String action){if(!local())return;if(cradle==null){status="Coloque um berço e abra com botão direito para gerar";return;}try{BodySpec spec=action.equals("start")?selected():BodySpec.defaults();var dimension=minecraft.level.dimension();var playerId=minecraft.player.getUUID();minecraft.getSingleplayerServer().execute(()->{try{var world=minecraft.getSingleplayerServer().getLevel(dimension);var player=minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);if(player==null||player.distanceToSqr(cradle.getX()+0.5,cradle.getY()+0.5,cradle.getZ()+0.5)>64)throw new IllegalStateException("Aproxime-se do berço");if(world==null||!(world.getBlockEntity(cradle) instanceof BercoEntity job))throw new IllegalStateException("Berço não encontrado");switch(action){case "start"->job.begin(spec);case "cancel"->job.cancel();case "release"->job.release();}minecraft.execute(()->status=job.failure().isEmpty()?"Berço atualizado":job.failure());}catch(Exception e){minecraft.execute(()->status=e.getMessage());}});}catch(Exception e){status=e.getMessage();}}
    private void picker(){if(!local())return;String path=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Importar PNG ou OBJ","",null,"PNG / OBJ",false);if(path!=null)onFilesDrop(List.of(Path.of(path)));}
    @Override public void onFilesDrop(List<Path> paths){if(!local()||paths.isEmpty())return;stash();try{
        var code=paths.stream().filter(p->p.getFileName().toString().matches(".*\\.(json|js)")).toList();
        if(!code.isEmpty()){if(code.size()!=paths.size())throw new IllegalArgumentException("Importe JSON/JS separado dos PNG/OBJ");minecraft.setScreen(new ContentImportScreen(this,content,code));return;}
        for(Path path:paths.stream().sorted(Comparator.comparingInt(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".obj")?0:1)).toList()){String name=content.importAsset(path);if(name.endsWith(".obj")){kind="obj";model=name;}else{if(kind.equals("player")){var image=javax.imageio.ImageIO.read(path.toFile());if(image.getWidth()!=64||image.getHeight()!=64)throw new IllegalArgumentException("Skin: use PNG 64x64; textura OBJ/mob aceita outros tamanhos");}texture=name;}}BodyVisual.reset();bodyTab=true;rebuildWidgets();status="Importado · pronto para formar no berço";}catch(Exception e){status=e.getMessage();}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xFFEAF6FD);g.fill(0,0,width,26,0xFFFFFFFF);g.drawString(font,"JEV / ATELIÊ NEURAL",12,9,0xFF235477,false);
        if(bodyTab){g.drawString(font,"Modelo / ID registrado",12,100,0xFF68889C,false);g.drawString(font,"Tempo (s)",12,162,0xFF68889C,false);g.drawString(font,"Cérebro opcional",16+(Math.max(170,width*3/5)-28)/2,162,0xFF68889C,false);g.drawString(font,texture.isEmpty()?"Arraste PNG/OBJ para importar":"Textura: "+texture.substring(0,12)+"…",12,155,0xFF68889C,false);renderBody(g,partial);}
        else{g.drawString(font,"ARQUIVOS",12,61,0xFF68889C,false);g.drawString(font,font.plainSubstrByWidth(current+(dirty(current)?" *":""),Math.max(60,viewX-width/5-24)),width/5+16,61,0xFF285875,false);brain.draw(g,viewX,viewY,viewW,viewH,mx,my);}
        super.render(g,mx,my,partial);g.fill(0,height-22,width,height,0xFFF9FCFF);g.drawString(font,font.plainSubstrByWidth(status.isEmpty()?"F8 · Ctrl S salva · mudanças têm histórico":status,width-20),12,height-14,0xFF52768C,false);
    }
    private void renderBody(GuiGraphics g,float partial){
        g.fill(viewX,viewY,viewX+viewW,viewY+viewH,0xFFFFFFFF);g.renderOutline(viewX,viewY,viewW,viewH,0xFFCCE4F4);g.drawString(font,"CORPO / BERÇO",viewX+8,viewY+9,0xFF235477,false);
        BodySpec spec;double progress=1;String label="Prévia completa";
        try{spec=selected();if(cradle!=null&&minecraft.level.getBlockEntity(cradle) instanceof BercoEntity job&&(job.running()||job.ready())){spec=job.spec();progress=job.progress();label=job.ready()?"Pronto para liberar":String.format(java.util.Locale.ROOT,"Formando %.1f%% · %.1f / %.1f s",progress*100,job.elapsed()/20.0,spec.durationTicks()/20.0);}}
        catch(Exception e){g.drawWordWrap(font,Component.literal(e.getMessage()),viewX+8,viewY+32,viewW-16,0xFF68889C);return;}
        g.enableScissor(viewX+1,viewY+27,viewX+viewW-1,viewY+viewH-33);g.pose().pushPose();g.pose().translate(viewX+viewW/2.0,viewY+viewH-35,180);float size=Math.min(viewW/1.5f,(viewH-65)/1.9f)*brain.zoom;g.pose().scale(size,-size,size);g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)Math.toDegrees(brain.yaw)));g.pose().mulPose(com.mojang.math.Axis.XP.rotation(brain.pitch));com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();BodyVisual.render(spec,progress,g.pose(),g.bufferSource(),0xF000F0);g.pose().popPose();g.flush();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();g.disableScissor();
        try{int uv=Math.min(48,viewW/3);g.fill(viewX+viewW-uv-8,viewY+30,viewX+viewW-8,viewY+30+uv,0xFFE2F1FA);g.blit(BodyVisual.assembledSkin(spec,progress),viewX+viewW-uv-8,viewY+30,uv,uv,0,0,64,64,64,64);g.drawString(font,"UV / 2D",viewX+viewW-uv-8,viewY+32+uv,0xFF52768C,false);}catch(Exception ignored){}
        int barY=viewY+viewH-24;g.fill(viewX+8,barY,viewX+viewW-8,barY+4,0xFFE2F1FA);g.fill(viewX+8,barY,viewX+8+(int)((viewW-16)*progress),barY+4,0xFF65BFE9);g.drawString(font,font.plainSubstrByWidth(label,viewW-16),viewX+8,barY+8,0xFF52768C,false);
    }
    private boolean dirty(String path){return !drafts.getOrDefault(path,"").equals(originals.getOrDefault(path,""));}
    @Override public boolean mouseClicked(double x,double y,int button){if(!bodyTab&&x>=viewX&&button==0){var node=brain.hit(x,y);if(node!=null&&files.contains(node.file())){select(node.file());return true;}}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(x>=viewX&&x<viewX+viewW&&y>=viewY&&y<viewY+viewH&&button==0){brain.yaw+=dx*0.015f;brain.pitch=Math.max(-1.2f,Math.min(1.2f,brain.pitch+(float)dy*0.012f));return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(x>=viewX){brain.zoom=Math.max(0.5f,Math.min(2f,brain.zoom+(float)sy*0.1f));return true;}return super.mouseScrolled(x,y,sx,sy);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_S&&(modifiers&GLFW.GLFW_MOD_CONTROL)!=0&&!bodyTab){save();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){stash();if(drafts.keySet().stream().anyMatch(this::dirty))minecraft.setScreen(new ConfirmScreen(discard->{if(discard)minecraft.setScreen(null);else minecraft.setScreen(this);},Component.literal("Rascunhos não salvos"),Component.literal("Fechar e descartar as alterações não salvas?")));else super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){}
    @Override public void resize(net.minecraft.client.Minecraft client,int width,int height){stash();super.resize(client,width,height);}
}
