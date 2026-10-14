package dev.eternaldefense.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/** The Loadout Station's screen: the kit to pack and its fee, the shared coin slots, and the locker on a second page. */
final class LoadoutScreen extends AbstractContainerScreen<LoadoutMenu> {
    static final int W=LoadoutMenu.W,H=LoadoutMenu.H,TAB_Y=LoadoutMenu.COIN_Y+18,BUTTON_Y=130,STATUS_Y=148;
    private Ui.UiButton make,setActive,print,forget,page;private final List<Ui.UiButton> tabs=new ArrayList<>();
    private static final ItemStack[] ARMOR_GHOSTS={new ItemStack(Items.IRON_HELMET),new ItemStack(Items.IRON_CHESTPLATE),new ItemStack(Items.IRON_LEGGINGS),new ItemStack(Items.IRON_BOOTS)};
    LoadoutScreen(LoadoutMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    private void action(int id){BeaconNetwork.menuButton(menu.containerId,id);}
    @Override protected void init(){
        super.init();tabs.clear();
        make=addRenderableWidget(new Ui.UiButton(leftPos+LoadoutMenu.MAKE_X,topPos+LoadoutMenu.ROW_A-1,72,18,Ui.t("loadout.make"),Ui.Look.PRIMARY,b->action(LoadoutMenu.MAKE)));
        for(int i=0;i<LoadoutRules.COIN_SLOTS;i++){int slot=i;
            var tab=new Ui.UiButton(leftPos+LoadoutMenu.INV_X-1+i*18,topPos+TAB_Y,18,9,Component.literal(String.valueOf(i+1)),Ui.Look.TAB,b->action(LoadoutMenu.SELECT+slot));
            tab.noLabel().painter((g,self,hover)->paintTab(g,self,slot));tabs.add(addRenderableWidget(tab));}
        int w=(W-16-12)/4,x=leftPos+8;
        setActive=addRenderableWidget(new Ui.UiButton(x,topPos+BUTTON_Y,w,16,Ui.t("loadout.set_active"),Ui.Look.NORMAL,b->action(LoadoutMenu.SET_ACTIVE)));
        print=addRenderableWidget(new Ui.UiButton(x+(w+4),topPos+BUTTON_Y,w,16,Ui.t("loadout.print"),Ui.Look.PRIMARY,b->action(LoadoutMenu.PRINT)));
        forget=addRenderableWidget(new Ui.UiButton(x+2*(w+4),topPos+BUTTON_Y,w,16,Ui.t("loadout.forget"),Ui.Look.NORMAL,b->action(LoadoutMenu.FORGET)));
        page=addRenderableWidget(new Ui.UiButton(x+3*(w+4),topPos+BUTTON_Y,w,16,Ui.t("loadout.locker"),Ui.Look.NORMAL,b->action(LoadoutMenu.PAGE)));
        update();
    }
    private void paintTab(GuiGraphics g,Ui.UiButton self,int slot){
        int x=self.getX(),y=self.getY();boolean sel=menu.selected()==slot,active=menu.active()==slot;
        g.fill(x+1,y,x+17,y+9,sel?Ui.CYAN_FILL:Ui.DEEP);if(sel)g.fill(x+1,y,x+17,y+1,Ui.CYAN);
        var n=Component.literal(String.valueOf(slot+1));g.drawString(font,n,x+(active?5:9)-font.width(n)/2,y+1,sel?Ui.INK:Ui.MUTED,false);
        if(active)Ui.check(g,x+10,y+2,Ui.BRASS);
    }
    @Override protected void containerTick(){super.containerTick();update();}
    private boolean busy(){return menu.busy();}
    /** The player printing at this station, as the station tells every client. */
    private String occupant(){var be=minecraft.level==null?null:minecraft.level.getBlockEntity(menu.anchor);return be instanceof LoadoutStation.StationEntity s&&s.busy()?s.occupantName:"";}
    private void update(){
        boolean kitPage=menu.page()==0;int sel=menu.selected();
        boolean any=false;for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)any|=!menu.pack.getItem(i).isEmpty();
        make.visible=kitPage;make.active=any;make.warning=any&&!feeOk();
        for(var t:tabs)t.visible=kitPage;
        setActive.visible=kitPage;setActive.active=sel!=menu.active();
        print.visible=kitPage;print.active=!busy()&&menu.cooldown()==0;
        forget.visible=kitPage;forget.active=menu.coins.getItem(sel).isEmpty()&&!menu.kind(sel).isEmpty();
        page.setMessage(Ui.t(kitPage?"loadout.locker":"loadout.back"));
    }
    private boolean feeOk(){return minecraft.player.isCreative()||menu.data.get(LoadoutMenu.D_PLATING)>=Economy.COIN_FEE_PLATING&&menu.data.get(LoadoutMenu.D_ARDENT)>=Economy.COIN_FEE_ARDENT;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;Ui.panel(g,x,y,W,H);Ui.header(g,font,x,y,W,title,Ui.t("loadout.subtitle"));
        if(menu.page()==0){
            Ui.text(g,font,Ui.t("loadout.kit"),x+LoadoutMenu.INV_X,y+30,Ui.MUTED,100);
            for(int i=0;i<LoadoutRules.ARMOR;i++){int sx=x+LoadoutMenu.INV_X+i*18;Ui.slot(g,sx-1,y+LoadoutMenu.ROW_A-1,Ui.BRASS_DK);if(menu.pack.getItem(LoadoutRules.ARMOR-1-i).isEmpty())Ui.ghost(g,ARMOR_GHOSTS[i],sx,y+LoadoutMenu.ROW_A);}
            Ui.slot(g,x+LoadoutMenu.OFFHAND_X-1,y+LoadoutMenu.ROW_A-1,Ui.BRASS_DK);if(menu.pack.getItem(LoadoutRules.OFFHAND_SLOT).isEmpty())Ui.ghost(g,new ItemStack(Items.SHIELD),x+LoadoutMenu.OFFHAND_X,y+LoadoutMenu.ROW_A);
            for(int i=0;i<LoadoutRules.HOTBAR;i++)Ui.slot(g,x+LoadoutMenu.INV_X-1+i*18,y+LoadoutMenu.ROW_B-1,Ui.EDGE);
            // The fee, read from the pack and the Home Zone's Hubs
            int plating=menu.data.get(LoadoutMenu.D_PLATING),ardent=menu.data.get(LoadoutMenu.D_ARDENT);
            Ui.text(g,font,Ui.t("loadout.fee",Economy.COIN_FEE_PLATING,plating,Economy.COIN_FEE_ARDENT,ardent),x+LoadoutMenu.INV_X,y+81,feeOk()?Ui.MUTED:Ui.ORANGE,W-2*LoadoutMenu.INV_X+10);
            Ui.text(g,font,Ui.t("loadout.shelf"),x+LoadoutMenu.INV_X,y+LoadoutMenu.COIN_Y-10,Ui.MUTED,W-2*LoadoutMenu.INV_X);
            for(int i=0;i<LoadoutRules.COIN_SLOTS;i++){
                int sx=x+LoadoutMenu.INV_X+i*18;Ui.slot(g,sx-1,y+LoadoutMenu.COIN_Y-1,i==menu.selected()?Ui.CYAN:i==menu.active()?Ui.BRASS:Ui.EDGE);
                if(menu.coins.getItem(i).isEmpty()&&!menu.kind(i).isEmpty())Ui.ghost(g,menu.kind(i),sx,y+LoadoutMenu.COIN_Y);
            }
            Component status;int color;
            if(busy()){status=Ui.t("loadout.status.busy",occupant());color=Ui.ORANGE;}
            else if(menu.cooldown()>0){status=Ui.t("loadout.status.cooldown",menu.cooldown());color=Ui.ORANGE;}
            else{status=Ui.t("loadout.status.ready",menu.active()+1);color=Ui.CYAN;}
            Ui.text(g,font,status,x+8,y+STATUS_Y,color,W-16);
        }else{
            Ui.text(g,font,Ui.t("loadout.locker_title"),x+LoadoutMenu.INV_X,y+30,Ui.MUTED,W-2*LoadoutMenu.INV_X);
            for(int i=0;i<LoadoutRules.LOCKER_SLOTS;i++)Ui.slot(g,x+LoadoutMenu.INV_X-1+i%9*18,y+LoadoutMenu.LOCKER_Y-1+i/9*18,Ui.EDGE);
            Ui.wrap(g,font,Ui.t("loadout.locker_note"),x+LoadoutMenu.INV_X,y+LoadoutMenu.LOCKER_Y+58,W-2*LoadoutMenu.INV_X,Ui.MUTED,2);
        }
        Ui.inventory(g,x+LoadoutMenu.INV_X-1,y+LoadoutMenu.INV_Y-1);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(menu.page()!=0)return;
        if(hoveredSlot!=null&&hoveredSlot.container==menu.coins&&!hoveredSlot.hasItem()&&menu.getCarried().isEmpty()){
            var kind=menu.kind(hoveredSlot.getContainerSlot());
            g.renderTooltip(font,font.split(kind.isEmpty()?Ui.t("loadout.slot.free"):Ui.t("loadout.slot.kind",hoveredSlot.getContainerSlot()+1),190),mx,my);
        }
        else if(make.visible&&make.isHovered())Ui.materialTooltip(g,font,List.of(Ui.t("loadout.make_tip")),feeRows(),mx,my);
        else if(print.visible&&print.isHovered())g.renderTooltip(font,font.split(Ui.t("loadout.print_tip",menu.selected()+1),190),mx,my);
        else if(setActive.visible&&setActive.isHovered())g.renderTooltip(font,font.split(Ui.t("loadout.set_active_tip",menu.selected()+1),190),mx,my);
        else if(forget.visible&&forget.isHovered())g.renderTooltip(font,font.split(Ui.t("loadout.forget_tip"),190),mx,my);
    }
    private net.minecraft.nbt.ListTag feeRows(){
        var rows=Ui.singleCost(new ItemStack(EternalDefense.PLATING.get()),Economy.COIN_FEE_PLATING,menu.data.get(LoadoutMenu.D_PLATING));
        rows.addAll(Ui.singleCost(new ItemStack(EternalDefense.ARDENT_ENERGY.get()),Economy.COIN_FEE_ARDENT,menu.data.get(LoadoutMenu.D_ARDENT)));return rows;
    }
}
