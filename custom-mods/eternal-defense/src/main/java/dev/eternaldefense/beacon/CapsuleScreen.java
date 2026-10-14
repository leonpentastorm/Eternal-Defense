package dev.eternaldefense.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;

/** The Loadout Capsule's screen: the kit on the player's own figure, the kit where it was packed, and Equip. */
final class CapsuleScreen extends AbstractContainerScreen<LoadoutCapsule.CapsuleMenu> {
    static final int W=LoadoutCapsule.CapsuleMenu.W,H=LoadoutCapsule.CapsuleMenu.H,CLOSET_X=8,CLOSET_Y=28,CLOSET_W=104,CLOSET_H=138;
    private Ui.UiButton equip;private final KitMannequin mannequin=new KitMannequin();
    CapsuleScreen(LoadoutCapsule.CapsuleMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    @Override protected void init(){
        super.init();
        equip=addRenderableWidget(new Ui.UiButton(leftPos+LoadoutCapsule.CapsuleMenu.INV_X-1,topPos+84,80,16,Ui.t("loadout.capsule.equip"),Ui.Look.PRIMARY,b->BeaconNetwork.menuButton(menu.containerId,LoadoutCapsule.CapsuleMenu.EQUIP)));
        dress();
    }
    @Override protected void containerTick(){super.containerTick();equip.active=!menu.items.isEmpty();dress();}
    private void dress(){var kit=new ArrayList<ItemStack>();for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)kit.add(menu.items.getItem(i));mannequin.wear(kit);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos,ix=LoadoutCapsule.CapsuleMenu.INV_X;
        Ui.panel(g,x,y,W,H);Ui.header(g,font,x,y,W,title,Ui.t("loadout.capsule.subtitle"));
        mannequin.render(g,x+CLOSET_X,y+CLOSET_Y,CLOSET_W,CLOSET_H,48);
        var caption=Ui.t("loadout.capsule.closet");
        Ui.text(g,font,caption,x+CLOSET_X+CLOSET_W/2-Math.min(font.width(caption),CLOSET_W)/2,y+CLOSET_Y+CLOSET_H+5,Ui.INK,CLOSET_W);
        Ui.text(g,font,Ui.t("loadout.kit"),x+ix-1,y+30,Ui.MUTED,W-ix-8);
        for(int i=0;i<LoadoutRules.ARMOR;i++)Ui.slot(g,x+ix-1+i*18,y+LoadoutCapsule.CapsuleMenu.ROW_A-1,Ui.BRASS_DK);
        Ui.slot(g,x+LoadoutCapsule.CapsuleMenu.OFFHAND_X-1,y+LoadoutCapsule.CapsuleMenu.ROW_A-1,Ui.BRASS_DK);
        for(int i=0;i<LoadoutRules.HOTBAR;i++)Ui.slot(g,x+ix-1+i*18,y+LoadoutCapsule.CapsuleMenu.ROW_B-1,Ui.EDGE);
        Ui.wrap(g,font,Ui.t("loadout.capsule.note"),x+ix+86,y+84,W-ix-94,Ui.MUTED,3);
        Ui.inventory(g,x+ix-1,y+LoadoutCapsule.CapsuleMenu.INV_Y-1);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&mannequin.press(mx,my,leftPos+CLOSET_X,topPos+CLOSET_Y,CLOSET_W,CLOSET_H))return true;
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return mannequin.drag(mx)||super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(mannequin.dragging()){mannequin.release();return true;}return super.mouseReleased(mx,my,button);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(equip.isHovered())g.renderTooltip(font,font.split(Ui.t("loadout.capsule.equip_tip"),190),mx,my);
    }
}
