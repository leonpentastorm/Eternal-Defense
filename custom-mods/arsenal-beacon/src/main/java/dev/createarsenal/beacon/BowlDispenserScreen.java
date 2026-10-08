package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class BowlDispenserScreen extends AbstractContainerScreen<BowlDispenser.DispenserMenu> {
    BowlDispenserScreen(BowlDispenser.DispenserMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=176;imageHeight=170;inventoryLabelY=76;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);Ui.header(g,font,leftPos,topPos,imageWidth,title,null);
        Ui.field(g,leftPos+79,topPos+41,18,18,false);Ui.text(g,font,Ui.t("bowls.stock"),leftPos+8,topPos+64,Ui.MUTED,160);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)Ui.field(g,leftPos+7+col*18,topPos+87+row*18,18,18,false);
        for(int col=0;col<9;col++)Ui.field(g,leftPos+7+col*18,topPos+145,18,18,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){Ui.text(g,font,playerInventoryTitle,8,76,Ui.MUTED,160);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
}
