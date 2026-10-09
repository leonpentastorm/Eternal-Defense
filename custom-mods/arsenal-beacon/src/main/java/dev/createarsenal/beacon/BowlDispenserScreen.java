package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One stack of bowls behind a gauge, and the two ways to use the block, with the player's own key labels. */
final class BowlDispenserScreen extends AbstractContainerScreen<BowlDispenser.DispenserMenu> {
    static final int WIDTH=176,HEIGHT=206;
    BowlDispenserScreen(BowlDispenser.DispenserMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=WIDTH;imageHeight=HEIGHT;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);Ui.header(g,font,leftPos,topPos,imageWidth,title,Ui.t("dispenser.subtitle.bowl"));
        int count=menu.stock.getItem(0).getCount();boolean low=count<=8;int accent=count==0?Ui.RED:low?Ui.ORANGE:Ui.BRASS;
        Ui.card(g,leftPos+8,topPos+32,160,46,accent);
        Ui.slot(g,leftPos+BowlDispenser.DispenserMenu.STOCK_X-1,topPos+BowlDispenser.DispenserMenu.STOCK_Y-1,accent);
        if(menu.stock.getItem(0).isEmpty())Ui.ghost(g,new ItemStack(Items.BOWL),leftPos+BowlDispenser.DispenserMenu.STOCK_X,topPos+BowlDispenser.DispenserMenu.STOCK_Y);
        Ui.text(g,font,Ui.t("bowls.label"),leftPos+42,topPos+40,Ui.INK,70);
        Ui.right(g,font,Ui.t("bowls.count",count,64),leftPos+162,topPos+40,count==0?Ui.RED:low?Ui.ORANGE:Ui.CYAN);
        Ui.bar(g,leftPos+42,topPos+53,120,7,count/64f,accent);
        Ui.text(g,font,Ui.t(count==0?"bowls.empty_short":"bowls.note"),leftPos+42,topPos+65,count==0?Ui.ORANGE:Ui.MUTED,120);
        var use=Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage();var sneak=Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage();
        int y=Ui.keyHint(g,font,use,Ui.t("dispenser.use_bowl"),leftPos+8,topPos+84,160);
        Ui.keyHint(g,font,sneak,Ui.t("dispenser.stock"),leftPos+8,y,160);
        Ui.text(g,font,playerInventoryTitle,leftPos+8,topPos+BowlDispenser.DispenserMenu.INV_Y-12,Ui.MUTED,160);
        Ui.inventory(g,leftPos+7,topPos+BowlDispenser.DispenserMenu.INV_Y-1);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(hoveredSlot==null&&Ui.inside(mx,my,leftPos+8,topPos+32,160,46))g.renderTooltip(font,font.split(Ui.t("bowls.tip"),180),mx,my);
        else if(hoveredSlot==null&&Ui.inside(mx,my,leftPos+8,topPos+84,160,13))g.renderTooltip(font,font.split(Ui.t("dispenser.tip.use_bowl"),180),mx,my);
        else if(hoveredSlot==null&&Ui.inside(mx,my,leftPos+8,topPos+99,160,13))g.renderTooltip(font,font.split(Ui.t("dispenser.tip.stock"),180),mx,my);
        else if(hoveredSlot!=null&&hoveredSlot.container==menu.stock&&!hoveredSlot.hasItem())g.renderTooltip(font,Ui.t("bowls.slot_tip"),mx,my);
    }
}
