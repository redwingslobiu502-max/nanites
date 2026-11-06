package dev.jevmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayDeque;
import java.util.Deque;

/** White code editor with line numbers, selection, clipboard, indentation, scrolling and undo. */
final class CodeBox extends AbstractWidget {
    private static final java.util.regex.Pattern SYNTAX=java.util.regex.Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|//.*|\\b(?:function|var|let|const|return|if|else|true|false|null)\\b|\\b[0-9]+(?:\\.[0-9]+)?\\b");
    private String value="";private int cursor,anchor,firstLine,scrollX;
    private final Deque<String> undo=new ArrayDeque<>();
    java.util.function.Consumer<String> changed=s->{};
    CodeBox(int x,int y,int w,int h){super(x,y,w,h,Component.literal("Código JSON / JavaScript"));}
    String value(){return value;}
    void value(String s){value=s;cursor=anchor=0;firstLine=scrollX=0;undo.clear();}
    private void edit(String insertion){undo.addLast(value);while(undo.size()>32)undo.removeFirst();int lo=Math.min(cursor,anchor),hi=Math.max(cursor,anchor);String next=value.substring(0,lo)+insertion+value.substring(hi);if(next.length()>262144)return;value=next;cursor=anchor=lo+insertion.length();changed.accept(value);ensureVisible();}
    private void ensureVisible(){int line=lineOf(cursor);int rows=Math.max(1,(height-8)/11);if(line<firstLine)firstLine=line;if(line>=firstLine+rows)firstLine=line-rows+1;int cx=Minecraft.getInstance().font.width(value.substring(lineStart(cursor),cursor).replace("\t","    "));if(cx<scrollX)scrollX=cx;if(cx>scrollX+width-44)scrollX=cx-width+44;}
    private int lineOf(int index){int n=0;for(int i=0;i<index;i++)if(value.charAt(i)=='\n')n++;return n;}
    private int lineStart(int index){return value.lastIndexOf('\n',Math.max(-1,index-1))+1;}
    private int lineEnd(int index){int n=value.indexOf('\n',index);return n<0?value.length():n;}
    @Override public boolean charTyped(char c,int modifiers){if(!isFocused()||Character.isISOControl(c))return false;edit(String.valueOf(c));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!isFocused())return false;boolean ctrl=(modifiers&GLFW.GLFW_MOD_CONTROL)!=0,shift=(modifiers&GLFW.GLFW_MOD_SHIFT)!=0;
        var clipboard=Minecraft.getInstance().keyboardHandler;
        if(ctrl){switch(key){case GLFW.GLFW_KEY_A-> {anchor=0;cursor=value.length();return true;}case GLFW.GLFW_KEY_C->{clipboard.setClipboard(value.substring(Math.min(cursor,anchor),Math.max(cursor,anchor)));return true;}case GLFW.GLFW_KEY_X->{clipboard.setClipboard(value.substring(Math.min(cursor,anchor),Math.max(cursor,anchor)));edit("");return true;}case GLFW.GLFW_KEY_V->{edit(clipboard.getClipboard().replace("\r\n","\n"));return true;}case GLFW.GLFW_KEY_Z->{if(!undo.isEmpty()){value=undo.removeLast();cursor=anchor=Math.min(cursor,value.length());changed.accept(value);ensureVisible();}return true;}default->{}}
        }
        switch(key){
            case GLFW.GLFW_KEY_ENTER->{String indent=value.substring(lineStart(cursor),cursor).replaceAll("^(\\s*).*","$1");if(indent.contains("\n"))indent="";edit("\n"+indent);return true;}
            case GLFW.GLFW_KEY_TAB->{edit("    ");return true;}
            case GLFW.GLFW_KEY_BACKSPACE->{if(cursor==anchor&&cursor>0)anchor=cursor-1;edit("");return true;}
            case GLFW.GLFW_KEY_DELETE->{if(cursor==anchor&&cursor<value.length())anchor=cursor+1;edit("");return true;}
            case GLFW.GLFW_KEY_LEFT->cursor=Math.max(0,cursor-1);
            case GLFW.GLFW_KEY_RIGHT->cursor=Math.min(value.length(),cursor+1);
            case GLFW.GLFW_KEY_HOME->cursor=ctrl?0:lineStart(cursor);
            case GLFW.GLFW_KEY_END->cursor=ctrl?value.length():lineEnd(cursor);
            case GLFW.GLFW_KEY_UP,GLFW.GLFW_KEY_DOWN->{int column=cursor-lineStart(cursor);if(key==GLFW.GLFW_KEY_UP){int end=lineStart(cursor)-1;if(end>=0)cursor=Math.min(end,lineStart(end)+column);}else{int start=lineEnd(cursor)+1;if(start<=value.length())cursor=Math.min(lineEnd(start),start+column);}}
            case GLFW.GLFW_KEY_PAGE_UP->firstLine=Math.max(0,firstLine-10);
            case GLFW.GLFW_KEY_PAGE_DOWN->firstLine=Math.min(lineOf(value.length()),firstLine+10);
            default->{return false;}
        }
        if(!shift)anchor=cursor;if(key!=GLFW.GLFW_KEY_PAGE_UP&&key!=GLFW.GLFW_KEY_PAGE_DOWN)ensureVisible();return true;
    }
    private int position(double mx,double my){int line=Math.max(0,(int)((my-getY()-4)/11)+firstLine),start=0;for(int i=0;i<line;i++){int end=value.indexOf('\n',start);if(end<0)return value.length();start=end+1;}int end=lineEnd(start);double x=mx-getX()-34+scrollX;int index=start;var font=Minecraft.getInstance().font;while(index<end&&font.width(value.substring(start,index+1).replace("\t","    "))<x)index++;return index;}
    @Override public boolean mouseClicked(double mx,double my,int button){if(!isMouseOver(mx,my)||button!=0)return false;cursor=anchor=position(mx,my);setFocused(true);return true;}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(button!=0||!isFocused())return false;cursor=position(mx,my);ensureVisible();return true;}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(!isMouseOver(mx,my))return false;firstLine=Math.max(0,Math.min(lineOf(value.length()),firstLine-(int)(sy*3)));return true;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        var font=Minecraft.getInstance().font;g.fill(getX(),getY(),getX()+width,getY()+height,0xFFFFFFFF);g.renderOutline(getX(),getY(),width,height,isFocused()?0xFF51B6E8:0xFFCEE6F2);g.fill(getX()+1,getY()+1,getX()+30,getY()+height-1,0xFFF1F9FD);
        g.enableScissor(getX()+1,getY()+1,getX()+width-1,getY()+height-1);
        String[] lines=value.split("\n",-1);int offset=0;int lo=Math.min(cursor,anchor),hi=Math.max(cursor,anchor);
        for(int line=0;line<lines.length;line++){String text=lines[line];if(line>=firstLine&&line<firstLine+(height-4)/11){int y=getY()+4+(line-firstLine)*11;g.drawString(font,String.valueOf(line+1),getX()+4,y,0xFF8DAABA,false);int x=getX()+34-scrollX;
            int a=Math.max(lo,offset),b=Math.min(hi,offset+text.length());if(a<b)g.fill(x+font.width(text.substring(0,a-offset)),y-1,x+font.width(text.substring(0,b-offset)),y+10,0xFFB7DFFD);
            g.enableScissor(getX()+31,getY()+1,getX()+width-1,getY()+height-1);
            var matcher=SYNTAX.matcher(text);int at=0;
            while(matcher.find()){g.drawString(font,text.substring(at,matcher.start()),x+font.width(text.substring(0,at)),y,0xFF23455E,false);String token=matcher.group();int color=token.startsWith("//")?0xFF7794A4:token.startsWith("\"")||token.startsWith("'")?0xFF087F90:Character.isDigit(token.charAt(0))?0xFF8964AD:0xFF286CCC;g.drawString(font,token,x+font.width(text.substring(0,matcher.start())),y,color,false);at=matcher.end();}
            g.drawString(font,text.substring(at),x+font.width(text.substring(0,at)),y,0xFF23455E,false);
            if(isFocused()&&cursor>=offset&&cursor<=offset+text.length()&&System.currentTimeMillis()/400%2==0){int cx=x+font.width(text.substring(0,cursor-offset));g.fill(cx,y-1,cx+1,y+10,0xFF168AD0);}g.disableScissor();
        }offset+=text.length()+1;}
        g.disableScissor();
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out){out.add(NarratedElementType.TITLE,getMessage());out.add(NarratedElementType.USAGE,Component.literal("Editor. Ctrl S salva, Ctrl Z desfaz, Ctrl C e Ctrl V copiam e colam."));}
}
