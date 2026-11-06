package dev.jevmod.client;

import dev.jevmod.body.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** Preview destinations, resolve ambiguous scripts and explicitly confirm replacement of existing content. */
final class ContentImportScreen extends Screen {
    private final WorkbenchScreen parent;private final LocalContent content;private final List<ContentImport.File> files;
    private final List<String> folders=new ArrayList<>();private int index;private String status="";
    ContentImportScreen(WorkbenchScreen parent,LocalContent content,List<Path> paths)throws Exception{
        super(Component.literal("Importar JSON / JS"));this.parent=parent;this.content=content;var loaded=new ArrayList<ContentImport.File>();
        for(var path:paths){var file=ContentImport.read(path,content);loaded.add(file);folders.add(file.inferredFolder());}files=List.copyOf(loaded);
        // A JSON in the same drop can identify its associated script without executing it.
        for(int i=0;i<files.size();i++)if(folders.get(i)==null&&files.get(i).source().toString().endsWith(".js")){
            var matches=new HashSet<String>();for(int j=0;j<files.size();j++)if(folders.get(j)!=null&&files.get(j).source().toString().endsWith(".json")){var o=dev.jevmod.core.Json.parseObject(files.get(j).text());String script=dev.jevmod.core.Json.str(o,"script",dev.jevmod.core.Json.str(o,"id","")+".js");if(script.equals(files.get(i).source().getFileName().toString()))matches.add(folders.get(j));}if(matches.size()==1)folders.set(i,matches.iterator().next());
        }
    }
    @Override protected void init(){
        int w=Math.min(350,width-24),x=(width-w)/2;
        addRenderableWidget(new BlueButton(x,57,w,"Destino: "+(folders.get(index)==null?"Escolha o tipo":folders.get(index)),b->{int next=(ContentImport.FOLDERS.indexOf(folders.get(index))+1)%ContentImport.FOLDERS.size();folders.set(index,ContentImport.FOLDERS.get(next));rebuildWidgets();}));
        addRenderableWidget(new BlueButton(x,height-65,w/2-3,"Anterior",b->{index=Math.floorMod(index-1,files.size());rebuildWidgets();}));
        addRenderableWidget(new BlueButton(x+w/2+3,height-65,w/2-3,"Próximo",b->{index=(index+1)%files.size();rebuildWidgets();}));
        addRenderableWidget(new BlueButton(x,height-39,w/2-3,"Cancelar",b->onClose()));addRenderableWidget(new BlueButton(x+w/2+3,height-39,w/2-3,"Importar todos",b->importAll()));
    }
    void importAll(){try{
        var plans=new ArrayList<ContentImport.Plan>();var existing=new ArrayList<String>();
        for(int i=0;i<files.size();i++){var f=files.get(i);String relative=f.relative(folders.get(i));LocalContent.validate(relative,f.text());plans.add(new ContentImport.Plan(relative,f.text()));if(Files.exists(content.path(relative))||parent.unsaved(relative))existing.add(relative);}
        if(!existing.isEmpty())minecraft.setScreen(new ConfirmScreen(yes->{if(yes)commit(plans);else minecraft.setScreen(this);},Component.literal("Substituir arquivos / rascunhos?"),Component.literal(String.join(", ",existing)+". Versões atuais serão guardadas no histórico.")));else commit(plans);
    }catch(Exception e){status=e.getMessage();}}
    private void commit(List<ContentImport.Plan> plans){try{ContentImport.save(content,plans,true);parent.importCompleted(plans);minecraft.setScreen(parent);}catch(Exception e){status=e.getMessage();minecraft.setScreen(this);}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xFFEAF6FD);g.fill(0,0,width,27,0xFFFFFFFF);int x=Math.max(12,(width-350)/2);g.drawString(font,"IMPORTAR JSON / JS",x,9,0xFF235477,false);var file=files.get(index);g.drawString(font,font.plainSubstrByWidth((index+1)+" / "+files.size()+" · "+file.source().getFileName(),width-24),x,36,0xFF52768C,false);g.enableScissor(x,84,width-12,height-74);g.drawWordWrap(font,Component.literal(file.text().substring(0,Math.min(1000,file.text().length()))),x,87,Math.min(350,width-24),0xFF52768C);g.disableScissor();super.render(g,mx,my,partial);if(!status.isEmpty())g.drawString(font,font.plainSubstrByWidth(status,width-24),12,height-11,0xFFA04040,false);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
}
