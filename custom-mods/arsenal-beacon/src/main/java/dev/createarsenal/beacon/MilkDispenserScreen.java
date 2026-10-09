package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The milk tank as a graduated gauge, the refill slots with a hint of what goes in, and the two ways to use the block. */
final class MilkDispenserScreen extends AbstractContainerScreen<MilkDispenser.DispenserMenu> {
    static final int WIDTH=176,HEIGHT=225,MILK=0xfff2eee0;
    MilkDispenserScreen(MilkDispenser.DispenserMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=WIDTH;imageHeight=HEIGHT;}
    private int amount(){return menu.data.get(0);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);Ui.header(g,font,leftPos,topPos,imageWidth,title,Ui.t("dispenser.subtitle.milk"));
        int amount=amount(),doses=amount/MilkDispenser.DOSE;boolean low=doses<=2;int accent=amount<MilkDispenser.DOSE?Ui.RED:low?Ui.ORANGE:Ui.CYAN;
        Ui.card(g,leftPos+8,topPos+32,160,64,accent);
        Ui.tank(g,leftPos+16,topPos+38,18,52,amount/(float)MilkDispenser.CAPACITY,MILK,8);
        int inX=leftPos+MilkDispenser.DispenserMenu.IN_X,outX=leftPos+MilkDispenser.DispenserMenu.OUT_X,y=topPos+MilkDispenser.DispenserMenu.SLOT_Y;
        Ui.slot(g,inX-1,y-1,Ui.EDGE);Ui.slot(g,outX-1,y-1,Ui.EDGE);
        if(menu.stock.getItem(0).isEmpty())Ui.ghost(g,new ItemStack(Items.MILK_BUCKET),inX,y);
        if(menu.stock.getItem(1).isEmpty())Ui.ghost(g,new ItemStack(Items.BUCKET),outX,y);
        Ui.text(g,font,Component.literal("→"),inX+24,y+5,Ui.MUTED);
        Ui.text(g,font,Ui.t("milk.fill",String.format("%,d",amount),String.format("%,d",MilkDispenser.CAPACITY)),leftPos+44,topPos+64,accent,118);
        Ui.text(g,font,Ui.t(amount<MilkDispenser.DOSE?"milk.empty_short":"milk.doses",doses,MilkDispenser.DOSE),leftPos+44,topPos+76,amount<MilkDispenser.DOSE?Ui.ORANGE:Ui.INK,118);
        var use=Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage();var sneak=Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage();
        int next=Ui.keyHint(g,font,use,Ui.t("dispenser.use_milk"),leftPos+8,topPos+102,160);
        Ui.keyHint(g,font,sneak,Ui.t("dispenser.stock"),leftPos+8,next,160);
        Ui.text(g,font,playerInventoryTitle,leftPos+8,topPos+MilkDispenser.DispenserMenu.INV_Y-12,Ui.MUTED,160);
        Ui.inventory(g,leftPos+7,topPos+MilkDispenser.DispenserMenu.INV_Y-1);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(hoveredSlot!=null){
            if(hoveredSlot.container==menu.stock&&!hoveredSlot.hasItem())g.renderTooltip(font,font.split(Ui.t(hoveredSlot.getContainerSlot()==0?"milk.in_tip":"milk.out_tip"),180),mx,my);
            return;
        }
        if(Ui.inside(mx,my,leftPos+8,topPos+102,160,13))g.renderTooltip(font,font.split(Ui.t("dispenser.tip.use_milk"),180),mx,my);
        else if(Ui.inside(mx,my,leftPos+8,topPos+117,160,13))g.renderTooltip(font,font.split(Ui.t("dispenser.tip.stock"),180),mx,my);
        else if(Ui.inside(mx,my,leftPos+16,topPos+38,18,52))g.renderTooltip(font,font.split(Ui.t("milk.dose",MilkDispenser.DOSE,amount()/MilkDispenser.DOSE),180),mx,my);
    }
}
