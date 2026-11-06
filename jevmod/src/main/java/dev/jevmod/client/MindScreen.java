package dev.jevmod.client;

import dev.jevmod.JevMod;
import dev.jevmod.mind.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Local core controls; code and JSON remain editable in the same in-game workbench. */
public final class MindScreen extends Screen {
    private final BlockPos position;
    private EditBox profile,systems,body,portName,portPosition;
    private String status="";
    public MindScreen(BlockPos position){super(Component.literal("Mente / Imagination"));this.position=position;}
    @Override protected void init(){
        if(minecraft.level==null)return;int left=14,w=Math.max(100,width/2-28);var core=minecraft.level.getBlockEntity(position) instanceof MindCoreEntity value?value:null;
        profile=field(left,47,w,"Personalidade",core==null?"jarvis":core.profile());systems=field(left,82,w,"Sistemas",core==null?"energy":String.join(",",core.systems()));body=field(left,117,w,"UUID do corpo",core==null||core.bodyId()==null?"":core.bodyId().toString());
        portName=field(left,152,w/3-4,"Porta","reactor");portPosition=field(left+w/3+6,152,w*2/3-6,"Bloco x y z",position.getX()+" "+position.getY()+" "+(position.getZ()+1));
        addRenderableWidget(new BlueButton(left,181,w/2-3,"Aplicar Mente",b->action("configure")));addRenderableWidget(new BlueButton(left+w/2+3,181,w/2-3,"Conectar porta",b->action("port")));
        addRenderableWidget(new BlueButton(width/2+14,181,w/2-3,"Ligar / desligar",b->action("toggle")));addRenderableWidget(new BlueButton(width/2+w/2+20,181,w/2-3,"Código / APIs",b->minecraft.setScreen(new WorkbenchScreen(null))));
        addRenderableWidget(new BlueButton(width-64,8,50,"Fechar",b->onClose()));
    }
    private EditBox field(int x,int y,int w,String label,String value){var box=addRenderableWidget(new BlueEditBox(font,x,y,w,18,Component.literal(label)));box.setMaxLength(512);box.setValue(value);return box;}
    private void action(String action){
        var server=minecraft.getSingleplayerServer();if(server==null||minecraft.player==null){status="Abra um mundo single-player local";return;}
        String selected=profile.getValue(),selectedSystems=systems.getValue(),selectedBody=body.getValue(),name=portName.getValue(),target=portPosition.getValue();var dimension=minecraft.level.dimension();var playerId=minecraft.player.getUUID();
        server.execute(()->{try{var level=server.getLevel(dimension);var player=server.getPlayerList().getPlayer(playerId);if(level==null||player==null||player.serverLevel()!=level||player.distanceToSqr(position.getCenter())>64||!(level.getBlockEntity(position) instanceof MindCoreEntity core))throw new IllegalArgumentException("Aproxime-se da Mente");core.claim(playerId);
            switch(action){case "configure"->{var ids=Arrays.stream(selectedSystems.split("[,\\s]+")).filter(s->!s.isBlank()).toList();UUID id=selectedBody.isBlank()?null:UUID.fromString(selectedBody);if(id!=null&&!(level.getEntity(id) instanceof dev.jevmod.body.GeneratedBody))throw new IllegalArgumentException("UUID precisa ser um corpo Jev carregado");for(String system:ids)if(!JevMod.minds().catalog().systems.containsKey(system))throw new IllegalArgumentException("Sistema ausente: "+system);core.configure(selected);core.useSystems(ids);core.bindBody(id);}case "port"->{var xyz=target.trim().split("\\s+");if(xyz.length!=3)throw new IllegalArgumentException("Informe x y z");core.bindPort(name,new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2])));}case "toggle"->core.setEnabled(!core.enabled());}
            minecraft.execute(()->status="Mente atualizada");
        }catch(Exception e){minecraft.execute(()->status=e.getMessage());}});
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xFFEAF6FD);g.fill(0,0,width,27,0xFFFFFFFF);g.drawString(font,"MENTE / IMAGINATION",14,9,0xFF235477,false);String[] labels={"Personalidade (jarvis / friday / ultron)","Sistemas (IDs separados por vírgula)","Corpo Jev (UUID opcional)","Porta / coordenadas do bloco"};for(int i=0;i<labels.length;i++)g.drawString(font,font.plainSubstrByWidth(labels[i],width/2-28),14,35+i*35,0xFF52768C,false);
        int x=width/2+14,w=width/2-28;g.fill(x-5,33,width-14,174,0xFFFFFFFF);g.drawString(font,"MEMÓRIA / CONEXÕES",x,39,0xFF235477,false);
        if(minecraft.level!=null&&minecraft.level.getBlockEntity(position) instanceof MindCoreEntity core){String text="Estado: "+(core.enabled()?"Ligada":"Desligada")+"\nPortas: "+core.ports()+"\nMemória: "+core.memory()+"\n"+core.error();g.enableScissor(x,52,x+w,171);g.drawWordWrap(font,Component.literal(text.substring(0,Math.min(500,text.length()))),x,54,w,0xFF52768C);g.disableScissor();}
        super.render(g,mx,my,partial);g.fill(0,height-23,width,height,0xFFFFFFFF);g.drawString(font,font.plainSubstrByWidth(status.isBlank()?"Redstone · wireless · sistemas e APIs do jogador":status,width-28),14,height-15,0xFF52768C,false);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
