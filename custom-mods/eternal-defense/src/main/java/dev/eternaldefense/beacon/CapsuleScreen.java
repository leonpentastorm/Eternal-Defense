package dev.eternaldefense.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Loadout Capsule's screen: the kit where it was packed, and Equip. */
final class CapsuleScreen extends AbstractContainerScreen<LoadoutCapsule.CapsuleMenu> {
    static final int W=LoadoutCapsule.CapsuleMenu.W,H=LoadoutCapsule.CapsuleMenu.H;
    private Ui.UiButton equip;
    CapsuleScreen(LoadoutCapsule.CapsuleMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    @Override protected void init(){
        super.init();
        equip=addRenderableWidget(new Ui.UiButton(leftPos+LoadoutCapsule.CapsuleMenu.INV_X-1,topPos+84,80,16,Ui.t("loadout.capsule.equip"),Ui.Look.PRIMARY,b->BeaconNetwork.menuButton(menu.containerId,LoadoutCapsule.CapsuleMenu.EQUIP)));
    }
    @Override protected void containerTick(){super.containerTick();equip.active=!menu.items.isEmpty();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos,ix=LoadoutCapsule.CapsuleMenu.INV_X;
        Ui.panel(g,x,y,W,H);Ui.header(g,font,x,y,W,title,Ui.t("loadout.capsule.subtitle"));
        Ui.text(g,font,Ui.t("loadout.kit"),x+ix,y+30,Ui.MUTED,W-2*ix);
        for(int i=0;i<LoadoutRules.ARMOR;i++)Ui.slot(g,x+ix-1+i*18,y+LoadoutCapsule.CapsuleMenu.ROW_A-1,Ui.BRASS_DK);
        Ui.slot(g,x+LoadoutCapsule.CapsuleMenu.OFFHAND_X-1,y+LoadoutCapsule.CapsuleMenu.ROW_A-1,Ui.BRASS_DK);
        for(int i=0;i<LoadoutRules.HOTBAR;i++)Ui.slot(g,x+ix-1+i*18,y+LoadoutCapsule.CapsuleMenu.ROW_B-1,Ui.EDGE);
        Ui.wrap(g,font,Ui.t("loadout.capsule.note"),x+ix+86,y+84,W-ix-96,Ui.MUTED,3);
        Ui.inventory(g,x+ix-1,y+LoadoutCapsule.CapsuleMenu.INV_Y-1);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(equip.isHovered())g.renderTooltip(font,font.split(Ui.t("loadout.capsule.equip_tip"),190),mx,my);
    }
}
