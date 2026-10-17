package dev.eternaldefense.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Support Platform: Supply grid, Shop and Upgrade tabs in the shared Eternal Defense style. */
final class SupportScreen extends AbstractContainerScreen<SupportPlatform.PlatformMenu> {
    static final int W=308,H=214;
    /** Shop list: full-height rows, as many as fit above the Buy button, the rest behind a scroll bar. */
    static final int ROW=26,BAR=6,LIST_Y=58,VISIBLE=(H-30-14-LIST_Y)/ROW;
    private int tab,selected,scroll,lastSerial=-1;private boolean dragging;private long shownAt;private Component feedback=Component.empty();
    private final List<Ui.UiButton> tabs=new ArrayList<>(),rows=new ArrayList<>();private Ui.UiButton buyButton,upgradeButton;
    SupportScreen(SupportPlatform.PlatformMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    @Override protected void init(){
        super.init();clearWidgets();tabs.clear();rows.clear();scroll=Math.min(scroll,maxScroll());
        int tw=(W-24)/3;String[] names={"grid","shop","upgrade"};
        for(int i=0;i<3;i++){int index=i;var b=new Ui.UiButton(leftPos+12+i*tw,topPos+31,tw-2,18,Ui.t("support.tab."+names[i]),Ui.Look.TAB,x->showTab(index));tabs.add(addRenderableWidget(b));}
        addRenderableWidget(new Ui.UiButton(leftPos+W-30,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
        for(int i=0;i<Math.min(VISIBLE,SupportShop.PRODUCTS.size());i++){
            int slot=i;var row=new Ui.UiButton(leftPos+12,topPos+LIST_Y+i*ROW,rowWidth(),ROW-2,Component.empty(),Ui.Look.ROW,b->selected=scroll+slot);
            row.painter((g,self,hover)->paintProduct(g,self,scroll+slot)).noLabel();rows.add(addRenderableWidget(row));
        }
        buyButton=addRenderableWidget(new Ui.UiButton(leftPos+W-12-140,topPos+H-30,140,20,Ui.t("support.buy"),Ui.Look.PRIMARY,b->{}));
        upgradeButton=addRenderableWidget(new Ui.UiButton(leftPos+W-12-140,topPos+H-30,140,20,Ui.t("support.upgrade"),Ui.Look.PRIMARY,b->{}));
        menu.slotsVisible=tab==0;
    }
    void showTab(int index){tab=index;menu.slotsVisible=index==0;dragging=false;}
    private int maxScroll(){return Math.max(0,SupportShop.PRODUCTS.size()-VISIBLE);}
    private int rowWidth(){return W-24-(maxScroll()>0?BAR+4:0);}
    // The scroll bar beside the list: drag the thumb or click the track
    private int barX(){return leftPos+W-12-BAR;}
    private int trackH(){return VISIBLE*ROW-2;}
    private int thumbH(){return Math.max(12,trackH()*VISIBLE/Math.max(VISIBLE,SupportShop.PRODUCTS.size()));}
    private void scrollTo(double my){int room=trackH()-thumbH();scroll=room<=0?0:Math.max(0,Math.min(maxScroll(),(int)Math.round((my-topPos-LIST_Y-thumbH()/2.0)*maxScroll()/room)));}
    /** The row showing a product, or null when it is scrolled out of view. */
    private Ui.UiButton rowOf(int index){int slot=index-scroll;return slot>=0&&slot<rows.size()?rows.get(slot):null;}
    private void paintProduct(GuiGraphics g,Ui.UiButton row,int index){
        var product=SupportShop.PRODUCTS.get(index);var stack=new ItemStack(product.item().get());
        g.renderItem(stack,row.getX()+6,row.getY()+4);
        boolean affordable=minecraft.player.isCreative()||menu.energy.get()>=product.price();
        Ui.text(g,font,Ui.t("support.product."+product.id()),row.getX()+28,row.getY()+4,affordable?Ui.INK:Ui.MUTED,row.getWidth()-100);
        Ui.text(g,font,Ui.t("support.product."+product.id()+".note"),row.getX()+28,row.getY()+14,Ui.MUTED,row.getWidth()-100);
        Component price=Ui.t("exchange.cost",product.price());
        g.renderItem(new ItemStack(EternalDefense.ARDENT_ENERGY.get()),row.getX()+row.getWidth()-22,row.getY()+4);
        Ui.right(g,font,price,row.getX()+row.getWidth()-24,row.getY()+8,affordable?Ui.CYAN:Ui.ORANGE);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        Ui.panel(g,leftPos,topPos,W,H);
        Ui.header(g,font,leftPos,topPos,W,Ui.t("support.title",menu.mk),Ui.t("support.subtitle"));
        var energy=new ItemStack(EternalDefense.ARDENT_ENERGY.get());
        if(tab==0){
            int n=SupportRules.grid(menu.mk);
            Ui.text(g,font,Ui.t("support.grid",n,n),leftPos+SupportPlatform.GRID_LEFT,topPos+SupportPlatform.GRID_TOP-10,Ui.MUTED,120);
            Ui.text(g,font,Ui.t("support.inventory"),leftPos+SupportPlatform.INV_LEFT,topPos+SupportPlatform.INV_TOP-10,Ui.MUTED,160);
            for(int r=0;r<n;r++)for(int c=0;c<n;c++)Ui.field(g,leftPos+SupportPlatform.GRID_LEFT+c*18,topPos+SupportPlatform.GRID_TOP+r*18,18,18,false);
            for(int r=0;r<3;r++)for(int c=0;c<9;c++)Ui.field(g,leftPos+SupportPlatform.INV_LEFT+c*18,topPos+SupportPlatform.INV_TOP+r*18,18,18,false);
            for(int c=0;c<9;c++)Ui.field(g,leftPos+SupportPlatform.INV_LEFT+c*18,topPos+SupportPlatform.INV_TOP+58,18,18,false);
            // Everything in the grid is taken by the next Support Flare, so say so where the player is looking.
            Ui.wrap(g,font,Ui.t("support.grid.note"),leftPos+SupportPlatform.INV_LEFT,topPos+SupportPlatform.INV_TOP+84,162,Ui.CYAN,3);
        }else{
            g.renderItem(energy,leftPos+14,topPos+H-30);
            Ui.text(g,font,Ui.t("exchange.balance",menu.energy.get()),leftPos+34,topPos+H-26,Ui.CYAN,140);
        }
        if(tab==2){
            int mk=menu.mk,cost=SupportRules.upgradeCost(mk);
            Ui.card(g,leftPos+12,topPos+58,W-24,86,mk>=4?Ui.BRASS:Ui.CYAN);
            Ui.text(g,font,Ui.t("support.upgrade.current",mk,SupportRules.grid(mk),SupportRules.grid(mk)),leftPos+22,topPos+64,Ui.INK,W-44);
            if(cost<0)Ui.text(g,font,Ui.t("support.upgrade.max"),leftPos+22,topPos+80,Ui.BRASS,W-44);
            else{
                Ui.text(g,font,Ui.t("support.upgrade.next",mk+1,SupportRules.grid(mk+1),SupportRules.grid(mk+1)),leftPos+22,topPos+80,Ui.CYAN,W-44);
                int have=0;for(var s:minecraft.player.getInventory().items)if(s.is(EternalDefense.PLATING.get()))have+=s.getCount();
                var plating=new ItemStack(EternalDefense.PLATING.get());g.renderItem(plating,leftPos+22,topPos+98);
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
        for(int i=0;i<rows.size();i++){rows.get(i).visible=tab==1&&scroll+i<SupportShop.PRODUCTS.size();rows.get(i).selected=scroll+i==selected;}
        buyButton.visible=tab==1;upgradeButton.visible=tab==2;
        var product=SupportShop.PRODUCTS.get(Math.min(selected,SupportShop.PRODUCTS.size()-1));
        buyButton.active=minecraft.player.isCreative()||menu.energy.get()>=product.price();buyButton.warning=!buyButton.active;
        upgradeButton.active=SupportRules.upgradeCost(menu.mk)>=0;
        super.render(g,mx,my,partial);
        if(tab==1&&maxScroll()>0){
            int x=barX(),y=topPos+LIST_Y,ty=y+(trackH()-thumbH())*scroll/maxScroll();
            g.fill(x,y,x+BAR,y+trackH(),Ui.TRACK);g.fill(x+1,ty,x+BAR-1,ty+thumbH(),dragging||Ui.inside(mx,my,x-1,y,BAR+2,trackH())?Ui.CYAN:Ui.MUTED);
            Ui.text(g,font,Ui.t("support.scroll",scroll+1,Math.min(SupportShop.PRODUCTS.size(),scroll+VISIBLE),SupportShop.PRODUCTS.size()),leftPos+14,topPos+LIST_Y+VISIBLE*ROW+2,Ui.MUTED,W-28);
        }
        if(!feedback.getString().isEmpty()&&net.minecraft.Util.getMillis()-shownAt<4000)Ui.text(g,font,feedback,leftPos+14,topPos+H-12,Ui.CYAN,W-28);
        renderTooltip(g,mx,my);
        if(tab==1&&!dragging&&menu.getCarried().isEmpty())for(int i=0;i<SupportShop.PRODUCTS.size();i++){var row=rowOf(i);if(row!=null&&row.visible&&row.isHovered()){g.renderTooltip(font,productTip(i),mx,my);break;}}
        if(upgradeButton.visible&&upgradeButton.isHovered()&&SupportRules.upgradeCost(menu.mk)>=0)Ui.materialTooltip(g,font,List.of(Ui.t("support.tab.upgrade")),Ui.singleCost(new ItemStack(EternalDefense.PLATING.get()),SupportRules.upgradeCost(menu.mk),menu.plating.get()),mx,my);
    }
    /** A product's full text: its name, the shop's note in full, then the item's own description. */
    private List<net.minecraft.util.FormattedCharSequence> productTip(int index){
        var product=SupportShop.PRODUCTS.get(index);var stack=new ItemStack(product.item().get());
        var item=net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft,stack);var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        lines.add(item.get(0).getVisualOrderText());
        lines.addAll(font.split(Ui.t("support.product."+product.id()+".note").copy().withStyle(net.minecraft.ChatFormatting.AQUA),220));
        for(int k=1;k<item.size();k++)lines.addAll(font.split(item.get(k),220));
        return lines;
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(tab==1&&button==0&&maxScroll()>0&&Ui.inside(mx,my,barX()-1,topPos+LIST_Y,BAR+2,trackH())){dragging=true;scrollTo(my);return true;}
        if(tab==1&&buyButton.isMouseOver(mx,my)&&buyButton.active&&button==0){BeaconNetwork.CHANNEL.sendToServer(new SupportShop.Buy(selected));return true;}
        if(tab==2&&upgradeButton.isMouseOver(mx,my)&&upgradeButton.active&&button==0){BeaconNetwork.CHANNEL.sendToServer(new SupportShop.Upgrade());return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double amount){
        if(tab==1&&maxScroll()>0&&Ui.inside(mx,my,leftPos+12,topPos+LIST_Y,W-24,VISIBLE*ROW)){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(amount)));return true;}
        return super.mouseScrolled(mx,my,amount);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(dragging){scrollTo(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(dragging){dragging=false;return true;}return super.mouseReleased(mx,my,button);}
}
