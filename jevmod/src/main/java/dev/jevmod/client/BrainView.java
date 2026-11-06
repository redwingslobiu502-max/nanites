package dev.jevmod.client;

import dev.jevmod.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Orbitable 3D functional brain graph, derived from the editable brain definition (not hidden model weights). */
final class BrainView {
    record Node(String label,String file,float x,float y,float z,int color){}
    private final List<Node> nodes=new ArrayList<>();private final Map<Node,int[]> projected=new HashMap<>();
    float yaw=0.35f,pitch=-0.18f,zoom=1;boolean flat;
    private String title="jarvis";
    void update(String name,String content){try{var brain=Json.parseObject(content);nodes.clear();title=name;
        var sensors=Json.arr(brain,"sensors");if(sensors!=null)for(int i=0;i<sensors.size();i++){var s=sensors.get(i);String type=s.isJsonPrimitive()?s.getAsString():Json.str(s.getAsJsonObject(),"as",Json.str(s.getAsJsonObject(),"type","sensor"));String file=!s.isJsonPrimitive()&&Json.str(s.getAsJsonObject(),"type","").equals("script")?"sensors/"+Json.str(s.getAsJsonObject(),"file",""):"brains/"+name+".json";nodes.add(new Node(type,file,-0.65f,-0.6f+i*0.22f,(i%2==0?-1:1)*0.2f,0xFF46B4D5));}
        var powers=Json.arr(brain,"powers");if(powers!=null)for(int i=0;i<powers.size();i++)nodes.add(new Node(powers.get(i).getAsString(),"powers/"+powers.get(i).getAsString()+".json",0.65f,-0.4f+i*0.28f,(i%2==0?1:-1)*0.2f,0xFF5687E3));
        nodes.add(new Node("decisão", "brains/"+name+".json",0,0.15f,0.2f,0xFFAF82D8));
    }catch(RuntimeException ignored){}}
    private int[] project(float x,float y,float z,int cx,int cy,int size){float a=flat?x:(float)(x*Math.cos(yaw)+z*Math.sin(yaw));float depth=flat?0:(float)(-x*Math.sin(yaw)+z*Math.cos(yaw));float b=flat?y:(float)(y*Math.cos(pitch)-depth*Math.sin(pitch));float d=flat?0:(float)(y*Math.sin(pitch)+depth*Math.cos(pitch));float perspective=3f/(3f+d);return new int[]{cx+(int)(a*size*zoom*perspective),cy+(int)(b*size*zoom*perspective)};}
    void draw(GuiGraphics g,int x,int y,int w,int h,int mx,int my){g.fill(x,y,x+w,y+h,0xFFF6FBFF);g.renderOutline(x,y,w,h,0xFFCCE4F4);int cx=x+w/2,cy=y+h/2,size=Math.min(w,h)/3;var font=Minecraft.getInstance().font;g.drawString(font,font.plainSubstrByWidth("CÉREBRO / "+title,w-18),x+9,y+9,0xFF245474,false);g.drawString(font,font.plainSubstrByWidth("Sensores · associação · decisão · motor",w-18),x+9,y+23,0xFF7696AA,false);g.enableScissor(x+1,y+36,x+w-1,y+h-22);
        for(int lobe=-1;lobe<=1;lobe+=2)for(int band=0;band<9;band++){float latitude=(float)(Math.PI*band/8);int[] last=null;for(int segment=0;segment<=36;segment++){float angle=(float)(2*Math.PI*segment/36);float px=lobe*0.27f+(float)Math.sin(latitude)*(float)Math.cos(angle)*0.61f,py=(float)Math.cos(latitude)*0.92f,pz=(float)Math.sin(latitude)*(float)Math.sin(angle)*0.55f;int[] p=project(px,py,pz,cx,cy,size);int color=py>0.3f?0xFFBEA3DC:px<-.2f?0xFF9DDAE7:px>.2f?0xFF9EBFF3:0xFFB5DECC;if(last!=null)line(g,last,p,color);last=p;}}
        for(int lobe=-1;lobe<=1;lobe+=2)for(int meridian=0;meridian<6;meridian++){float angle=(float)Math.PI*meridian/3;int[] last=null;for(int segment=0;segment<=24;segment++){float t=(float)Math.PI*segment/24;int[] p=project(lobe*0.27f+(float)Math.sin(t)*(float)Math.cos(angle)*0.61f,(float)Math.cos(t)*0.92f,(float)Math.sin(t)*(float)Math.sin(angle)*0.55f,cx,cy,size);if(last!=null)line(g,last,p,0xFFCEE3F0);last=p;}}
        projected.clear();int[] center=project(0,0.15f,0.2f,cx,cy,size);
        for(Node n:nodes){int[] p=project(n.x,n.y,n.z,cx,cy,size);projected.put(n,p);line(g,p,center,0xFF91CAE7);int pulse=(int)((System.currentTimeMillis()%1800)/1800.0*100);int ax=p[0]+(center[0]-p[0])*pulse/100,ay=p[1]+(center[1]-p[1])*pulse/100;g.fill(ax-1,ay-1,ax+2,ay+2,0xFF42B9EE);g.fill(p[0]-4,p[1]-4,p[0]+5,p[1]+5,n.color);g.drawString(font,n.label,p[0]+7,p[1]-4,0xFF315A79,false);}
        g.disableScissor();g.drawString(font,font.plainSubstrByWidth(flat?"2D · clique em um nó para editar":"3D · arraste para girar · roda: zoom",w-18),x+9,y+h-15,0xFF7596AA,false);
        Node hovered=hit(mx,my);if(hovered!=null)g.renderTooltip(font,net.minecraft.network.chat.Component.literal(hovered.label+" · "+hovered.file),mx,my);
    }
    Node hit(double x,double y){for(var entry:projected.entrySet()){int[] p=entry.getValue();if(Math.hypot(x-p[0],y-p[1])<12)return entry.getKey();}return null;}
    private static void line(GuiGraphics g,int[] a,int[] b,int color){int dx=b[0]-a[0],dy=b[1]-a[1],steps=Math.max(Math.abs(dx),Math.abs(dy));for(int i=0;i<=steps;i++){int x=a[0]+dx*i/Math.max(1,steps),y=a[1]+dy*i/Math.max(1,steps);g.fill(x,y,x+1,y+1,color);}}
}
