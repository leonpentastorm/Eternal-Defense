package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class MilkDispenserScreen extends AbstractContainerScreen<MilkDispenser.DispenserMenu> {
    MilkDispenserScreen(MilkDispenser.DispenserMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=176;imageHeight=183;inventoryLabelY=89;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);Ui.header(g,font,leftPos,topPos,imageWidth,title,null);
        Ui.field(g,leftPos+43,topPos+42,18,18,false);Ui.field(g,leftPos+79,topPos+42,18,18,false);
        Ui.text(g,font,Component.literal("→"),leftPos+64,topPos+47,Ui.MUTED,12);
        int amount=menu.data.get(0);Ui.field(g,leftPos+117,topPos+31,18,34,false);int h=(int)(30*amount/(float)MilkDispenser.CAPACITY);g.fill(leftPos+119,topPos+63-h,leftPos+133,topPos+63,0xfff0f4e6);
        Ui.text(g,font,Ui.t("milk.fill",amount,MilkDispenser.CAPACITY),leftPos+8,topPos+68,Ui.CYAN,160);
        Ui.text(g,font,Ui.t("dispenser.interact"),leftPos+8,topPos+79,Ui.MUTED,160);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)Ui.field(g,leftPos+7+col*18,topPos+100+row*18,18,18,false);
        for(int col=0;col<9;col++)Ui.field(g,leftPos+7+col*18,topPos+158,18,18,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){Ui.text(g,font,playerInventoryTitle,8,89,Ui.MUTED,160);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(Ui.inside(mx,my,leftPos+117,topPos+31,18,34))g.renderTooltip(font,Ui.t("milk.dose",MilkDispenser.DOSE,menu.data.get(0)/MilkDispenser.DOSE),mx,my);
        if(Ui.inside(mx,my,leftPos+8,topPos+79,160,9))g.renderTooltip(font,Ui.t("dispenser.empty_hands"),mx,my);
    }
}
