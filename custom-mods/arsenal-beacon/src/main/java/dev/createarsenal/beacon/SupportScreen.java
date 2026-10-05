package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Support Platform: Supply grid, Shop and Upgrade tabs in the shared Create Arsenal style. */
final class SupportScreen extends AbstractContainerScreen<SupportPlatform.PlatformMenu> {
    static final int W=308,H=200;
    private int tab,selected,lastSerial=-1;private long shownAt;private Component feedback=Component.empty();
    private final List<Ui.UiButton> tabs=new ArrayList<>();private Ui.UiButton buyButton,upgradeButton;
    SupportScreen(SupportPlatform.PlatformMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    @Override protected void init(){
        super.init();clearWidgets();tabs.clear();
        int tw=(W-24)/3;String[] names={"grid","shop","upgrade"};
        for(int i=0;i<3;i++){int index=i;var b=new Ui.UiButton(leftPos+12+i*tw,topPos+31,tw-2,18,Ui.t("support.tab."+names[i]),Ui.Look.TAB,x->{tab=index;menu.slotsVisible=index==0;});tabs.add(addRenderableWidget(b));}
        addRenderableWidget(new Ui.UiButton(leftPos+W-30,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
        for(int i=0;i<SupportShop.PRODUCTS.size();i++){
            int index=i;var row=new Ui.UiButton(leftPos+12,topPos+58+i*26,W-24,24,Component.empty(),Ui.Look.ROW,b->selected=index);
            row.painter((g,self,hover)->paintProduct(g,self,index)).noLabel();addRenderableWidget(row);
        }
        buyButton=addRenderableWidget(new Ui.UiButton(leftPos+W-12-140,topPos+H-30,140,20,Ui.t("support.buy"),Ui.Look.PRIMARY,b->{}));
        upgradeButton=addRenderableWidget(new Ui.UiButton(leftPos+W-12-140,topPos+H-30,140,20,Ui.t("support.upgrade"),Ui.Look.PRIMARY,b->{}));
        menu.slotsVisible=tab==0;
    }
    private void paintProduct(GuiGraphics g,Ui.UiButton row,int index){
        var product=SupportShop.PRODUCTS.get(index);var stack=new ItemStack(product.item().get());
        g.renderItem(stack,row.getX()+6,row.getY()+4);
        boolean affordable=minecraft.player.isCreative()||ArdentEnergy.balance(minecraft.player)>=product.price();
        Ui.text(g,font,Ui.t("support.product."+product.id()),row.getX()+28,row.getY()+4,affordable?Ui.INK:Ui.MUTED,row.getWidth()-100);
        Ui.text(g,font,Ui.t("support.product."+product.id()+".note"),row.getX()+28,row.getY()+14,Ui.MUTED,row.getWidth()-100);
        Component price=Ui.t("exchange.cost",product.price());
        g.renderItem(new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get()),row.getX()+row.getWidth()-22,row.getY()+4);
        Ui.right(g,font,price,row.getX()+row.getWidth()-24,row.getY()+8,affordable?Ui.CYAN:Ui.ORANGE);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        Ui.panel(g,leftPos,topPos,W,H);
        Ui.header(g,font,leftPos,topPos,W,Ui.t("support.title",menu.mk),Ui.t("support.subtitle"));
        var energy=new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get());
        if(tab==0){
            int n=SupportRules.grid(menu.mk);
            Ui.text(g,font,Ui.t("support.grid",n,n),leftPos+SupportPlatform.GRID_LEFT,topPos+SupportPlatform.GRID_TOP-10,Ui.MUTED,120);
            Ui.text(g,font,Ui.t("support.inventory"),leftPos+SupportPlatform.INV_LEFT,topPos+SupportPlatform.INV_TOP-10,Ui.MUTED,160);
            for(int r=0;r<n;r++)for(int c=0;c<n;c++)Ui.field(g,leftPos+SupportPlatform.GRID_LEFT+c*18,topPos+SupportPlatform.GRID_TOP+r*18,18,18,false);
            for(int r=0;r<3;r++)for(int c=0;c<9;c++)Ui.field(g,leftPos+SupportPlatform.INV_LEFT+c*18,topPos+SupportPlatform.INV_TOP+r*18,18,18,false);
            for(int c=0;c<9;c++)Ui.field(g,leftPos+SupportPlatform.INV_LEFT+c*18,topPos+SupportPlatform.INV_TOP+58,18,18,false);
            int cost=menu.cost.get();boolean short_=cost>0&&!minecraft.player.isCreative()&&ArdentEnergy.balance(minecraft.player)<cost;
            int by=topPos+SupportPlatform.GRID_TOP+n*18+6;
            g.renderItem(energy,leftPos+SupportPlatform.GRID_LEFT-2,by-3);
            Ui.wrap(g,font,cost==0?Ui.t("support.cost.empty"):Ui.t("support.cost",cost),leftPos+SupportPlatform.GRID_LEFT+18,by,SupportPlatform.INV_LEFT-SupportPlatform.GRID_LEFT-24,short_?Ui.ORANGE:Ui.CYAN,3);
        }else{
            g.renderItem(energy,leftPos+14,topPos+H-30);
            Ui.text(g,font,Ui.t("exchange.balance",ArdentEnergy.balance(minecraft.player)),leftPos+34,topPos+H-26,Ui.CYAN,140);
        }
        if(tab==2){
            int mk=menu.mk,cost=SupportRules.upgradeCost(mk);
            Ui.card(g,leftPos+12,topPos+58,W-24,86,mk>=4?Ui.BRASS:Ui.CYAN);
            Ui.text(g,font,Ui.t("support.upgrade.current",mk,SupportRules.grid(mk),SupportRules.grid(mk)),leftPos+22,topPos+64,Ui.INK,W-44);
            if(cost<0)Ui.text(g,font,Ui.t("support.upgrade.max"),leftPos+22,topPos+80,Ui.BRASS,W-44);
            else{
                Ui.text(g,font,Ui.t("support.upgrade.next",mk+1,SupportRules.grid(mk+1),SupportRules.grid(mk+1)),leftPos+22,topPos+80,Ui.CYAN,W-44);
                int have=0;for(var s:minecraft.player.getInventory().items)if(s.is(ArsenalBeacon.PLATING.get()))have+=s.getCount();
                var plating=new ItemStack(ArsenalBeacon.PLATING.get());g.renderItem(plating,leftPos+22,topPos+98);
                boolean enough=minecraft.player.isCreative()||have>=cost;
                Ui.text(g,font,Ui.t("support.upgrade.cost",have,cost,plating.getHoverName()),leftPos+44,topPos+102,enough?Ui.INK:Ui.ORANGE,W-70);
            }
            Ui.wrap(g,font,Ui.t("support.upgrade.note"),leftPos+22,topPos+120,W-44,Ui.MUTED,2);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        int serial=menu.serial.get();
        if(lastSerial<0)lastSerial=serial;
        if(serial!=lastSerial){lastSerial=serial;shownAt=net.minecraft.Util.getMillis();feedback=switch(menu.result.get()){
            case SupportShop.BOUGHT->Ui.t("support.result.bought");case SupportShop.NO_ENERGY->Ui.t("support.result.energy");
            case SupportShop.NO_PLATING->Ui.t("support.result.plating");case SupportShop.MAXED->Ui.t("support.result.max");case SupportShop.UPGRADED->Ui.t("support.result.upgraded",menu.mk);
            default->Component.empty();};}
        for(int i=0;i<tabs.size();i++)tabs.get(i).selected=i==tab;
        var rows=children().stream().filter(c->c instanceof Ui.UiButton b&&b.look==Ui.Look.ROW).map(c->(Ui.UiButton)c).toList();
        for(int i=0;i<rows.size();i++){rows.get(i).visible=tab==1;rows.get(i).selected=i==selected;}
        buyButton.visible=tab==1;upgradeButton.visible=tab==2;
        var product=SupportShop.PRODUCTS.get(Math.min(selected,SupportShop.PRODUCTS.size()-1));
        buyButton.active=minecraft.player.isCreative()||ArdentEnergy.balance(minecraft.player)>=product.price();buyButton.warning=!buyButton.active;
        upgradeButton.active=SupportRules.upgradeCost(menu.mk)>=0;
        super.render(g,mx,my,partial);
        if(!feedback.getString().isEmpty()&&net.minecraft.Util.getMillis()-shownAt<4000)Ui.text(g,font,feedback,leftPos+14,topPos+H-12,Ui.CYAN,W-28);
        renderTooltip(g,mx,my);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(tab==1&&buyButton.isMouseOver(mx,my)&&buyButton.active&&button==0){BeaconNetwork.CHANNEL.sendToServer(new SupportShop.Buy(selected));return true;}
        if(tab==2&&upgradeButton.isMouseOver(mx,my)&&upgradeButton.active&&button==0){BeaconNetwork.CHANNEL.sendToServer(new SupportShop.Upgrade());return true;}
        return super.mouseClicked(mx,my,button);
    }
}
