package dev.jevmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class BlueButton extends Button {
    BlueButton(int x,int y,int w,String text,OnPress action){super(x,y,w,20,Component.literal(text),action,DEFAULT_NARRATION);}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){g.fill(getX(),getY(),getX()+width,getY()+height,active?(isHoveredOrFocused()?0xFFBDE7FF:0xFFE1F3FF):0xFFE9EEF2);g.renderOutline(getX(),getY(),width,height,0xFF8DCBE8);g.drawCenteredString(Minecraft.getInstance().font,getMessage(),getX()+width/2,getY()+6,active?0xFF164767:0xFF8A9EAC);}
}
