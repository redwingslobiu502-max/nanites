package dev.jevmod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class BlueEditBox extends EditBox {
    BlueEditBox(Font font,int x,int y,int width,int height,Component label){super(font,x,y,width,height,label);setBordered(false);setTextColor(0x285875);setTextColorUneditable(0x789AAD);}
    @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
        g.fill(getX()-3,getY()-2,getX()+getWidth()+3,getY()+getHeight()+2,0xFFFFFFFF);
        g.renderOutline(getX()-3,getY()-2,getWidth()+6,getHeight()+4,isFocused()?0xFF51B6E8:0xFFCCE4F4);
        g.pose().pushPose();g.pose().translate(0,5,0);super.renderWidget(g,mx,my,partial);g.pose().popPose();
    }
}
