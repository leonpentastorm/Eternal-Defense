package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.0.38 (owner): the MRE Station's one screen. Top: the six food slots, the Marines Fuel over the Paper, an arrow and the output. Under
 * them the live totals of one MRE, a row each: nutrition out of 20 (a mark per point) and saturation; holding a food over a food slot
 * previews it, in red when it would pass 20 (the slot refuses it). Then Make and Take back, one line that says what is missing or what
 * Make will give, and the pack. The screen only reads the slots; the server checks everything again.
 */
final class MreStationScreen extends AbstractContainerScreen<MreStation.StationMenu> {
    static final int W=MreStation.StationMenu.WIDTH,H=MreStation.StationMenu.HEIGHT;
    static final int CARD_Y=30,CARD_H=54,TOTALS_Y=87,TOTALS_H=36,BUTTON_Y=126,STATUS_Y=145;
    static final int BAR_X=16,BAR_W=140,NUTRITION=0xffd9a441,SATURATION=0xffffd166;
    Ui.UiButton make,takeBack;
    MreStationScreen(MreStation.StationMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    private void action(int id){BeaconNetwork.menuButton(menu.containerId,id);}
    @Override protected void init(){
        super.init();
        make=addRenderableWidget(new Ui.UiButton(leftPos+8,topPos+BUTTON_Y,94,16,Ui.t("mre.make"),Ui.Look.PRIMARY,b->action(MreStation.StationMenu.MAKE)));
        takeBack=addRenderableWidget(new Ui.UiButton(leftPos+106,topPos+BUTTON_Y,62,16,Ui.t("mre.take_back"),Ui.Look.NORMAL,b->action(MreStation.StationMenu.TAKE_BACK)));
        update();
    }
    @Override protected void containerTick(){super.containerTick();update();}
    /** Make lights up only when the slots make an MRE; Take back when there is something to take. */
    private void update(){
        make.active=menu.check().ready();
        boolean any=false;for(int i=0;i<MreStation.SLOTS;i++)any|=!menu.items.getItem(i).isEmpty();takeBack.active=any;
    }
    /** The food slot under the mouse, or -1. */
    private int hoveredFood(){return hoveredSlot!=null&&hoveredSlot.container==menu.items&&hoveredSlot.getContainerSlot()<MreRules.FOOD_SLOTS?hoveredSlot.getContainerSlot():-1;}
    /** Holding a food over a food slot: what the slots give without that slot, and the held food. */
    private record Preview(int others,MreRules.Food food){boolean fits(){return MreRules.fits(others,food.nutrition());}}
    private Preview preview(){
        int slot=hoveredFood();if(slot<0||menu.getCarried().isEmpty())return null;
        var f=MreStation.food(menu.getCarried(),menu.player);return f==null?null:new Preview(MreStation.nutritionExcept(menu.items,slot,menu.player),f);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;var check=menu.check();boolean ready=check.ready();int accent=ready?Ui.CYAN:Ui.BRASS;
        Ui.panel(g,x,y,W,H);Ui.header(g,font,x,y,W,title,Ui.t("mre.subtitle"));
        // the slots: food, the seal (fuel over paper), the output
        Ui.card(g,x+8,y+CARD_Y,160,CARD_H,accent);
        Ui.text(g,font,Ui.t("mre.food_label"),x+16,y+CARD_Y+3,Ui.INK,62);
        Ui.text(g,font,Ui.t("mre.seal_label"),x+MreStation.StationMenu.FUEL_X-1,y+CARD_Y+3,Ui.INK,40);
        for(int i=0;i<MreRules.FOOD_SLOTS;i++)Ui.slot(g,x+MreStation.StationMenu.FOOD_X-1+i%3*18,y+MreStation.StationMenu.FOOD_Y-1+i/3*18,i==hoveredFood()&&preview()!=null&&!preview().fits()?Ui.RED:Ui.EDGE);
        Ui.slot(g,x+MreStation.StationMenu.FUEL_X-1,y+MreStation.StationMenu.FUEL_Y-1,menu.items.getItem(MreStation.FUEL).isEmpty()?Ui.ORANGE:Ui.EDGE);
        Ui.slot(g,x+MreStation.StationMenu.FUEL_X-1,y+MreStation.StationMenu.PAPER_Y-1,menu.items.getItem(MreStation.PAPER).isEmpty()?Ui.ORANGE:Ui.EDGE);
        if(menu.items.getItem(MreStation.FUEL).isEmpty())Ui.ghost(g,new ItemStack(ArsenalBeacon.MARINES_FUEL.get()),x+MreStation.StationMenu.FUEL_X,y+MreStation.StationMenu.FUEL_Y);
        if(menu.items.getItem(MreStation.PAPER).isEmpty())Ui.ghost(g,new ItemStack(Items.PAPER),x+MreStation.StationMenu.FUEL_X,y+MreStation.StationMenu.PAPER_Y);
        arrow(g,x+104,y+MreStation.StationMenu.OUT_Y+4,ready?Ui.CYAN:Ui.EDGE);
        g.fill(x+MreStation.StationMenu.OUT_X-3,y+MreStation.StationMenu.OUT_Y-3,x+MreStation.StationMenu.OUT_X+19,y+MreStation.StationMenu.OUT_Y+19,ready?Ui.CYAN_DIM:Ui.SLATE_HI);
        Ui.slot(g,x+MreStation.StationMenu.OUT_X-1,y+MreStation.StationMenu.OUT_Y-1,accent);
        if(menu.items.getItem(MreStation.OUTPUT).isEmpty())Ui.ghost(g,new ItemStack(ArsenalBeacon.MRE.get()),x+MreStation.StationMenu.OUT_X,y+MreStation.StationMenu.OUT_Y);
        var label=Ui.t("mre.output_label");Ui.text(g,font,label,x+MreStation.StationMenu.OUT_X+8-font.width(label)/2,y+MreStation.StationMenu.OUT_Y+21,Ui.MUTED);
        // the live totals of one MRE
        Ui.card(g,x+8,y+TOTALS_Y,160,TOTALS_H,accent);
        int n=MreStation.nutritionExcept(menu.items,-1,menu.player);float sat=check.totals()==null?0:check.totals().saturation();
        var p=preview();int base=p==null?n:p.others();
        // a row each: the label on the left, the number on the right, the bar under them
        Ui.text(g,font,Ui.t("mre.nutrition_label"),x+BAR_X,y+TOTALS_Y+3,Ui.INK,70);
        Ui.right(g,font,Ui.t("mre.nutrition_value",p==null?n:p.others()+p.food().nutrition(),MreRules.MAX_NUTRITION),x+BAR_X+BAR_W,y+TOTALS_Y+3,p!=null&&!p.fits()?Ui.RED:n>=MreRules.MAX_NUTRITION?Ui.CYAN:Ui.INK);
        nutritionBar(g,x+BAR_X,y+TOTALS_Y+13,base,p==null?0:p.food().nutrition(),p!=null&&!p.fits());
        Ui.text(g,font,Ui.t("mre.saturation_label"),x+BAR_X,y+TOTALS_Y+21,Ui.INK,70);
        Ui.right(g,font,Component.literal(MreRules.number(sat)),x+BAR_X+BAR_W,y+TOTALS_Y+21,Ui.MUTED);
        Ui.bar(g,x+BAR_X,y+TOTALS_Y+31,BAR_W,3,sat/MreRules.MAX_SATURATION,SATURATION);
        // what Make will do, or what is missing
        Component status;int color;
        if(ready){status=Ui.t("mre.status.ready",check.totals().nutrition(),MreRules.number(check.totals().saturation()));int back=menu.containers().size();if(back>0)status=status.copy().append(Ui.t("mre.status.back",back));color=Ui.CYAN;}
        else{status=Ui.t("mre.problem."+check.problem(),MreRules.MAX_NUTRITION);color=check.problem().equals("no_food")?Ui.MUTED:Ui.ORANGE;}
        Ui.text(g,font,status,x+8,y+STATUS_Y,color,160);
        // no "Inventory" label: the screen stays 238 tall, so it fits a 720p window at GUI scale 3
        Ui.inventory(g,x+7,y+MreStation.StationMenu.INV_Y-1);
    }
    /** 20 points, a mark at each and a stronger one every 2 (a hunger shank); the held food shows after the slots, cyan or red. */
    private static void nutritionBar(GuiGraphics g,int x,int y,int have,int adding,boolean over){
        int per=BAR_W/MreRules.MAX_NUTRITION;
        g.fill(x,y,x+BAR_W,y+5,Ui.TRACK);g.fill(x,y+4,x+BAR_W,y+5,Ui.DEEP);
        int fill=Math.min(MreRules.MAX_NUTRITION,have)*per;if(fill>0){g.fill(x,y,x+fill,y+5,NUTRITION);g.fill(x,y,x+fill,y+1,0x55ffffff);}
        if(adding>0){int end=Math.min(MreRules.MAX_NUTRITION,have+adding)*per;g.fill(x+fill,y,x+end,y+5,over?Ui.RED:Ui.CYAN_DIM);if(over)g.fill(x+BAR_W-2,y-1,x+BAR_W,y+6,Ui.RED);}
        for(int i=1;i<MreRules.MAX_NUTRITION;i++){int tx=x+i*per;g.fill(tx,y,tx+1,y+5,i%2==0?0x99000000:0x44000000);}
    }
    private static void arrow(GuiGraphics g,int x,int y,int color){
        g.fill(x,y+3,x+20,y+5,color);
        for(int i=0;i<5;i++)g.fill(x+20+i,y-1+i,x+21+i,y+9-i,color);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        var p=preview();
        if(p!=null&&!p.fits())g.renderTooltip(font,font.split(Ui.t("mre.over_tip",p.others(),p.food().nutrition(),p.others()+p.food().nutrition(),MreRules.MAX_NUTRITION).copy().withStyle(net.minecraft.ChatFormatting.RED),190),mx,my);
        else if(hoveredSlot!=null&&hoveredSlot.container==menu.items&&!hoveredSlot.hasItem()&&menu.getCarried().isEmpty()){
            int slot=hoveredSlot.getContainerSlot();
            String key=slot<MreRules.FOOD_SLOTS?"mre.slot.food":slot==MreStation.FUEL?"mre.slot.fuel":slot==MreStation.PAPER?"mre.slot.paper":"mre.slot.output";
            g.renderTooltip(font,font.split(Ui.t(key,MreRules.MAX_NUTRITION),190),mx,my);
        }
        else if(hoveredSlot==null&&Ui.inside(mx,my,leftPos+8,topPos+TOTALS_Y,160,TOTALS_H))g.renderTooltip(font,font.split(Ui.t("mre.totals_tip",MreRules.MAX_NUTRITION),190),mx,my);
        else if(!make.active&&make.isHovered())g.renderTooltip(font,font.split(Ui.t("mre.problem."+menu.check().problem(),MreRules.MAX_NUTRITION),190),mx,my);
    }
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class MreRegistration {
        @SubscribeEvent public static void mreScreens(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.MRE_STATION_MENU.get(),MreStationScreen::new));}
    }
}
