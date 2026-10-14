package dev.eternaldefense.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/** The Loadout Station's screen: a closet with the selected kit worn by the player's own figure, the wardrobe of shared coin slots, the kit to pack, and the locker. */
final class LoadoutScreen extends AbstractContainerScreen<LoadoutMenu> {
    static final int W=LoadoutMenu.W,H=LoadoutMenu.H,BUTTON_Y=104,BUTTON_GAP=20;
    /** The closet display on the left, the wardrobe column, and the right-hand column. */
    static final int CLOSET_X=8,CLOSET_Y=28,CLOSET_W=136,CLOSET_H=150,WARD_X=LoadoutMenu.WARD_X,WARD_W=LoadoutMenu.WARD_W,TEXT_X=LoadoutMenu.INV_X-1,TEXT_W=W-8-TEXT_X;
    private Ui.UiButton make,setActive,print,forget,page,previous,next;
    private final KitMannequin mannequin=new KitMannequin();
    private static final ItemStack[] ARMOR_GHOSTS={new ItemStack(Items.IRON_HELMET),new ItemStack(Items.IRON_CHESTPLATE),new ItemStack(Items.IRON_LEGGINGS),new ItemStack(Items.IRON_BOOTS)};
    LoadoutScreen(LoadoutMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    private void action(int id){BeaconNetwork.menuButton(menu.containerId,id);}
    @Override protected void init(){
        super.init();
        make=addRenderableWidget(new Ui.UiButton(leftPos+LoadoutMenu.MAKE_X,topPos+LoadoutMenu.ROW_A-1,W-8-LoadoutMenu.MAKE_X,18,Ui.t("loadout.make"),Ui.Look.PRIMARY,b->action(LoadoutMenu.MAKE)));
        // Two rows of two buttons under the kit: what to do with the selected slot, and the locker
        int bw=(TEXT_W-4)/2,bx=leftPos+TEXT_X,by=topPos+BUTTON_Y;
        print=addRenderableWidget(new Ui.UiButton(bx,by,bw,16,Ui.t("loadout.print"),Ui.Look.PRIMARY,b->action(LoadoutMenu.PRINT)));
        setActive=addRenderableWidget(new Ui.UiButton(bx+bw+4,by,bw,16,Ui.t("loadout.set_active"),Ui.Look.NORMAL,b->action(LoadoutMenu.SET_ACTIVE)));
        forget=addRenderableWidget(new Ui.UiButton(bx,by+BUTTON_GAP,bw,16,Ui.t("loadout.forget"),Ui.Look.NORMAL,b->action(LoadoutMenu.FORGET)));
        page=addRenderableWidget(new Ui.UiButton(bx+bw+4,by+BUTTON_GAP,bw,16,Ui.t("loadout.locker"),Ui.Look.NORMAL,b->action(LoadoutMenu.PAGE)));
        // Browsing the closet: the coin slot before and after the selected one
        int cy=topPos+CLOSET_Y+CLOSET_H+4;
        previous=addRenderableWidget(new Ui.UiButton(leftPos+CLOSET_X,cy,16,14,Component.literal("<"),Ui.Look.NORMAL,b->action(LoadoutMenu.SELECT+(menu.selected()+LoadoutRules.COIN_SLOTS-1)%LoadoutRules.COIN_SLOTS)));
        next=addRenderableWidget(new Ui.UiButton(leftPos+CLOSET_X+CLOSET_W-16,cy,16,14,Component.literal(">"),Ui.Look.NORMAL,b->action(LoadoutMenu.SELECT+(menu.selected()+1)%LoadoutRules.COIN_SLOTS)));
        update();
    }
    /** The wardrobe row under the mouse (0-based), or -1. */
    private int row(double mx,double my){
        double ry=my-(topPos+LoadoutMenu.COIN_Y-2);
        if(mx<leftPos+WARD_X||mx>=leftPos+WARD_X+WARD_W||ry<0)return -1;
        int r=(int)(ry/LoadoutMenu.COIN_ROW);return r<LoadoutRules.COIN_SLOTS?r:-1;
    }
    @Override protected void containerTick(){super.containerTick();update();}
    private boolean busy(){return menu.busy();}
    /** The player printing at this station, as the station tells every client. */
    private String occupant(){var be=minecraft.level==null?null:minecraft.level.getBlockEntity(menu.anchor);return be instanceof LoadoutStation.StationEntity s&&s.busy()?s.occupantName:"";}
    private boolean packing(){for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)if(!menu.pack.getItem(i).isEmpty())return true;return false;}
    private void update(){
        boolean kitPage=menu.page()==0;int sel=menu.selected();boolean any=packing();
        make.visible=kitPage;make.active=any;make.warning=any&&!feeOk();
        setActive.visible=kitPage;setActive.active=sel!=menu.active();
        print.visible=kitPage;print.active=!busy()&&menu.cooldown()==0;
        forget.visible=kitPage;forget.active=menu.coins.getItem(sel).isEmpty()&&!menu.kind(sel).isEmpty();
        page.setMessage(Ui.t(kitPage?"loadout.locker":"loadout.back"));
        mannequin.wear(preview());
    }
    /** The coin the closet shows: the selected slot's coin, else the kind it remembers (empty when neither). */
    private ItemStack shownCoin(){var coin=menu.coins.getItem(menu.selected());return coin.isEmpty()?menu.kind(menu.selected()):coin;}
    /** The kit on the figure: the one being packed, else the selected slot's coin, else nothing. */
    private List<ItemStack> preview(){
        if(packing()){var kit=new ArrayList<ItemStack>();for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)kit.add(menu.pack.getItem(i));return kit;}
        var coin=shownCoin();return coin.isEmpty()?List.of():LoadoutCoin.kit(coin);
    }
    private boolean feeOk(){return minecraft.player.isCreative()||menu.data.get(LoadoutMenu.D_PLATING)>=Economy.COIN_FEE_PLATING&&menu.data.get(LoadoutMenu.D_ARDENT)>=Economy.COIN_FEE_ARDENT;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;Ui.panel(g,x,y,W,H);Ui.header(g,font,x,y,W,title,Ui.t("loadout.subtitle"));
        closet(g,x,y);
        wardrobe(g,x,y,mx,my);
        int rx=x+TEXT_X;
        if(menu.page()==0){
            Ui.text(g,font,Ui.t("loadout.kit"),rx,y+30,Ui.MUTED,TEXT_W);
            for(int i=0;i<LoadoutRules.ARMOR;i++){int sx=x+LoadoutMenu.INV_X+i*18;Ui.slot(g,sx-1,y+LoadoutMenu.ROW_A-1,Ui.BRASS_DK);if(menu.pack.getItem(LoadoutRules.ARMOR-1-i).isEmpty())Ui.ghost(g,ARMOR_GHOSTS[i],sx,y+LoadoutMenu.ROW_A);}
            Ui.slot(g,x+LoadoutMenu.OFFHAND_X-1,y+LoadoutMenu.ROW_A-1,Ui.BRASS_DK);if(menu.pack.getItem(LoadoutRules.OFFHAND_SLOT).isEmpty())Ui.ghost(g,new ItemStack(Items.SHIELD),x+LoadoutMenu.OFFHAND_X,y+LoadoutMenu.ROW_A);
            for(int i=0;i<LoadoutRules.HOTBAR;i++)Ui.slot(g,x+LoadoutMenu.INV_X-1+i*18,y+LoadoutMenu.ROW_B-1,Ui.EDGE);
            // The fee, read from the pack and the Home Zone's Hubs
            int plating=menu.data.get(LoadoutMenu.D_PLATING),ardent=menu.data.get(LoadoutMenu.D_ARDENT);
            Ui.wrap(g,font,Ui.t("loadout.fee",Economy.COIN_FEE_PLATING,plating,Economy.COIN_FEE_ARDENT,ardent),rx,y+83,TEXT_W,feeOk()?Ui.MUTED:Ui.ORANGE,2);
        }else{
            Ui.text(g,font,Ui.t("loadout.locker_title"),rx,y+30,Ui.MUTED,TEXT_W);
            for(int i=0;i<LoadoutRules.LOCKER_SLOTS;i++)Ui.slot(g,x+LoadoutMenu.INV_X-1+i%9*18,y+LoadoutMenu.LOCKER_Y-1+i/9*18,Ui.EDGE);
            Ui.wrap(g,font,Ui.t("loadout.locker_note"),rx,y+LoadoutMenu.LOCKER_Y+58,TEXT_W,Ui.MUTED,2);
        }
        Ui.inventory(g,x+LoadoutMenu.INV_X-1,y+LoadoutMenu.INV_Y-1);
    }
    /** The wardrobe: the 9 coin slots hanging from a rail, numbered, the selected one lit, the active one tagged. */
    private void wardrobe(GuiGraphics g,int x,int y,int mx,int my){
        int wx=x+WARD_X,top=y+LoadoutMenu.COIN_Y,railX=wx+10,hover=row(mx,my);
        Ui.text(g,font,Ui.t("loadout.shelf"),wx,y+30,Ui.MUTED,WARD_W);
        g.fill(railX,top-3,railX+2,top+(LoadoutRules.COIN_SLOTS-1)*LoadoutMenu.COIN_ROW+12,Ui.BRASS_DK);g.fill(railX,top-3,railX+1,top+(LoadoutRules.COIN_SLOTS-1)*LoadoutMenu.COIN_ROW+12,Ui.BRASS);
        for(int i=0;i<LoadoutRules.COIN_SLOTS;i++){
            int ry=top+i*LoadoutMenu.COIN_ROW;boolean sel=i==menu.selected(),active=i==menu.active();
            if(sel)g.fill(wx,ry-2,wx+WARD_W,ry+18,Ui.CYAN_FILL);else if(i==hover)g.fill(wx,ry-2,wx+WARD_W,ry+18,Ui.SLATE);
            if(sel)g.fill(wx,ry-2,wx+1,ry+18,Ui.CYAN);
            var n=Component.literal(String.valueOf(i+1));g.drawString(font,n,wx+5-font.width(n)/2,ry+5,sel?Ui.INK:active?Ui.BRASS:Ui.MUTED,false);
            g.fill(railX+2,ry+7,LoadoutMenu.COIN_X+x-1,ry+9,sel?Ui.CYAN:Ui.BRASS_DK);
            Ui.slot(g,x+LoadoutMenu.COIN_X-1,ry-1,sel?Ui.CYAN:active?Ui.BRASS:Ui.EDGE);
            if(menu.coins.getItem(i).isEmpty()&&!menu.kind(i).isEmpty())Ui.ghost(g,menu.kind(i),x+LoadoutMenu.COIN_X,ry);
            if(active)Ui.chip(g,font,Ui.t("loadout.active_tag"),x+LoadoutMenu.COIN_X+21,ry+3,Ui.BRASS,wx+WARD_W-(x+LoadoutMenu.COIN_X+21));
        }
    }
    /** The closet: the figure in the shown kit, which kit it is, what it holds, and whether the station is free. */
    private void closet(GuiGraphics g,int x,int y){
        int cx=x+CLOSET_X,cy=y+CLOSET_Y;
        mannequin.render(g,cx,cy,CLOSET_W,CLOSET_H,54);
        boolean packing=packing();int sel=menu.selected();var coin=menu.coins.getItem(sel);
        Component caption=packing?Ui.t("loadout.closet.new"):Ui.t(sel==menu.active()?"loadout.closet.slot_active":"loadout.closet.slot",sel+1);
        int capY=cy+CLOSET_H+7;Ui.text(g,font,caption,cx+CLOSET_W/2-Math.min(font.width(caption),CLOSET_W-40)/2,capY,packing?Ui.ORANGE:Ui.INK,CLOSET_W-40);
        Component line;
        var kit=preview();
        if(kit.isEmpty())line=Ui.t("loadout.closet.empty");
        else if(!packing&&coin.isEmpty())line=Ui.t("loadout.closet.kind");
        else line=summary(kit);
        Ui.text(g,font,line,cx,capY+14,Ui.MUTED,CLOSET_W);
        Component status;int color;
        if(busy()){status=Ui.t("loadout.status.busy",occupant());color=Ui.ORANGE;}
        else if(menu.cooldown()>0){status=Ui.t("loadout.status.cooldown",menu.cooldown());color=Ui.ORANGE;}
        else{status=Ui.t("loadout.status.ready",menu.active()+1);color=Ui.CYAN;}
        Ui.wrap(g,font,status,cx,capY+28,CLOSET_W,color,2);
    }
    /** "4 armor, off hand, 9 items" for a kit. */
    private static Component summary(List<ItemStack> kit){
        int armor=0,hotbar=0;for(int i=0;i<kit.size();i++){if(kit.get(i).isEmpty())continue;if(i<LoadoutRules.ARMOR)armor++;else if(i>=LoadoutRules.HOTBAR_FIRST)hotbar++;}
        boolean off=kit.size()>LoadoutRules.OFFHAND_SLOT&&!kit.get(LoadoutRules.OFFHAND_SLOT).isEmpty();
        return Ui.t(off?"loadout.closet.summary_off":"loadout.closet.summary",armor,hotbar);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&mannequin.press(mx,my,leftPos+CLOSET_X,topPos+CLOSET_Y,CLOSET_W,CLOSET_H))return true;
        // A click anywhere on a wardrobe row shows that slot in the closet; on the slot itself it also takes or puts the coin
        int r=row(mx,my);if(r>=0&&r!=menu.selected())action(LoadoutMenu.SELECT+r);
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return mannequin.drag(mx)||super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(mannequin.dragging()){mannequin.release();return true;}return super.mouseReleased(mx,my,button);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        boolean overCloset=mx>=leftPos+CLOSET_X&&mx<leftPos+CLOSET_X+CLOSET_W&&my>=topPos+CLOSET_Y&&my<topPos+CLOSET_Y+CLOSET_H;
        if(overCloset&&!mannequin.dragging()&&menu.getCarried().isEmpty()){g.renderTooltip(font,font.split(Ui.t("loadout.closet.tip"),190),mx,my);return;}
        if(previous.isHovered()||next.isHovered()){g.renderTooltip(font,font.split(Ui.t("loadout.closet.browse_tip"),190),mx,my);return;}
        if(hoveredSlot!=null&&hoveredSlot.container==menu.coins&&!hoveredSlot.hasItem()&&menu.getCarried().isEmpty()){
            var kind=menu.kind(hoveredSlot.getContainerSlot());
            g.renderTooltip(font,font.split(kind.isEmpty()?Ui.t("loadout.slot.free"):Ui.t("loadout.slot.kind",hoveredSlot.getContainerSlot()+1),190),mx,my);return;
        }
        if(menu.getCarried().isEmpty()&&hoveredSlot==null&&(row(mx,my)>=0||Ui.inside(mx,my,leftPos+WARD_X,topPos+28,WARD_W,12))){g.renderTooltip(font,font.split(Ui.t("loadout.shelf_tip"),190),mx,my);return;}
        if(menu.page()!=0)return;
        if(make.visible&&make.isHovered())Ui.materialTooltip(g,font,List.of(Ui.t("loadout.make_tip")),feeRows(),mx,my);
        else if(print.visible&&print.isHovered())g.renderTooltip(font,font.split(Ui.t("loadout.print_tip",menu.selected()+1),190),mx,my);
        else if(setActive.visible&&setActive.isHovered())g.renderTooltip(font,font.split(Ui.t("loadout.set_active_tip",menu.selected()+1),190),mx,my);
        else if(forget.visible&&forget.isHovered())g.renderTooltip(font,font.split(Ui.t("loadout.forget_tip"),190),mx,my);
    }
    private net.minecraft.nbt.ListTag feeRows(){
        var rows=Ui.singleCost(new ItemStack(EternalDefense.PLATING.get()),Economy.COIN_FEE_PLATING,menu.data.get(LoadoutMenu.D_PLATING));
        rows.addAll(Ui.singleCost(new ItemStack(EternalDefense.ARDENT_ENERGY.get()),Economy.COIN_FEE_ARDENT,menu.data.get(LoadoutMenu.D_ARDENT)));return rows;
    }
}
