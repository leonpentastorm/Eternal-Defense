package dev.eternaldefense.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Exchange Shop menu: pick an offer, press Buy. */
final class ExchangeScreen extends BeaconClient.PanelScreen {
    private static final int ROW=24,BAR=6,LIST_Y=56;
    private final BlockPos pos;private CompoundTag data;
    private final List<Ui.UiButton> rows=new ArrayList<>();private Ui.UiButton buy;
    private int selected,scroll;private boolean sell,dragging;private Ui.UiButton buyTab,sellTab;private final List<BeaconClient.Hover> icons=new ArrayList<>();
    ExchangeScreen(BlockPos pos,CompoundTag data){super(Ui.t("exchange.title"));this.pos=pos;this.data=data;}
    static void receive(ExchangeShop.Open packet){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof ExchangeScreen s&&s.pos.equals(packet.pos())){s.data=packet.data();s.message=packet.message();s.selected=Math.min(s.selected,Math.max(0,s.offers().size()-1));}
        else{var s=new ExchangeScreen(packet.pos(),packet.data());s.message=packet.message();mc.setScreen(s);}
    }
    private ListTag offers(){return data.getList(sell?"sell":"buy",Tag.TAG_COMPOUND);}
    private boolean carrying(int index){var have=data.getList("have",Tag.TAG_COMPOUND);return index>=have.size()||data.getBoolean("creative")||have.getCompound(index).getInt("have")>=ItemStack.of(offers().getCompound(index).getCompound("item")).getCount();}
    @Override Component subtitle(){return Ui.t("exchange.subtitle");}
    @Override protected void init(){
        super.init();pw=Math.min(430,width-16);ph=Math.min(300,height-16);left=(width-pw)/2;top=(height-ph)/2;rows.clear();
        closeButton();
        int visible=visibleRows();
        for(int i=0;i<visible;i++){
            int index=i;var row=new Ui.UiButton(left+14,top+LIST_Y+i*ROW,pw-28-BAR-4,ROW-2,Component.empty(),Ui.Look.ROW,b->selected=scroll+index);
            row.painter((g,self,hover)->paintRow(g,self,index)).noLabel();rows.add(addRenderableWidget(row));
        }
        buy=button(sell?Ui.t("exchange.sell"):Ui.t("exchange.buy"),left+pw-14-130,top+ph-48,130,Ui.Look.PRIMARY,b->{if(selected<offers().size())BeaconNetwork.CHANNEL.sendToServer(new ExchangeShop.Buy(pos,selected,sell));});
        buyTab=button(Ui.t("exchange.tab.buy"),left+pw-14-176,top+30,86,Ui.Look.TAB,b->{sell=false;selected=scroll=0;rebuildWidgets();});
        sellTab=button(Ui.t("exchange.tab.sell"),left+pw-14-88,top+30,88,Ui.Look.TAB,b->{sell=true;selected=scroll=0;rebuildWidgets();});
        button(Ui.t("back"),left+14,top+ph-48,80,Ui.Look.NORMAL,b->onClose());
    }
    private int visibleRows(){return Math.max(1,(ph-56-56)/ROW);}
    private void paintRow(GuiGraphics g,Ui.UiButton row,int index){
        int at=scroll+index;var list=offers();if(at>=list.size()){row.visible=false;return;}
        var offer=list.getCompound(at);var stack=ItemStack.of(offer.getCompound("item"));int cost=offer.getInt("cost");
        boolean affordable=sell?carrying(at):data.getBoolean("creative")||data.getInt("energy")>=cost;
        g.renderItem(stack,row.getX()+5,row.getY()+3);icons.add(new BeaconClient.Hover(stack,row.getX()+5,row.getY()+3));
        Component cost_=sell?Ui.t("exchange.gain",cost):Ui.t("exchange.cost",cost);int cw=font.width(cost_)+22;
        Ui.text(g,font,Ui.t("exchange.offer",stack.getCount(),stack.getHoverName()),row.getX()+28,row.getY()+7,affordable?Ui.INK:Ui.MUTED,row.getWidth()-34-cw);
        var energy=new ItemStack(EternalDefense.ARDENT_ENERGY.get());g.renderItem(energy,row.getX()+row.getWidth()-19,row.getY()+3);
        Ui.right(g,font,cost_,row.getX()+row.getWidth()-22,row.getY()+7,sell?(affordable?Ui.BRASS:Ui.MUTED):affordable?Ui.CYAN:Ui.ORANGE);
    }
    private int maxScroll(){return Math.max(0,offers().size()-visibleRows());}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(amount)));return true;}
    // The scroll bar beside the list: drag the thumb or click the track
    private int barX(){return left+pw-14-BAR;}
    private int trackH(){return visibleRows()*ROW-2;}
    private int thumbH(){return Math.max(12,trackH()*visibleRows()/Math.max(visibleRows(),offers().size()));}
    private void scrollTo(double my){int room=trackH()-thumbH();scroll=room<=0?0:Math.max(0,Math.min(maxScroll(),(int)Math.round((my-top-LIST_Y-thumbH()/2.0)*maxScroll()/room)));}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&maxScroll()>0&&Ui.inside(mx,my,barX()-1,top+LIST_Y,BAR+2,trackH())){dragging=true;scrollTo(my);return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(dragging){scrollTo(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(dragging){dragging=false;return true;}return super.mouseReleased(mx,my,button);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        panel(g);icons.clear();
        var list=offers();
        Ui.inset(g,left+12,top+LIST_Y-2,pw-24,visibleRows()*ROW+2);
        if(maxScroll()>0){
            int x=barX(),y=top+LIST_Y,ty=y+(trackH()-thumbH())*scroll/maxScroll();
            g.fill(x,y,x+BAR,y+trackH(),Ui.TRACK);g.fill(x+1,ty,x+BAR-1,ty+thumbH(),dragging||Ui.inside(mx,my,x-1,y,BAR+2,trackH())?Ui.CYAN:Ui.MUTED);
        }
        for(int i=0;i<rows.size();i++){var row=rows.get(i);row.visible=scroll+i<list.size();row.selected=scroll+i==selected;
            if(row.visible){var offer=list.getCompound(scroll+i);row.warning=sell?!carrying(scroll+i):!data.getBoolean("creative")&&data.getInt("energy")<offer.getInt("cost");}}
        buyTab.selected=!sell;sellTab.selected=sell;
        var energyStack=new ItemStack(EternalDefense.ARDENT_ENERGY.get());
        g.renderItem(energyStack,left+14,top+34);
        Ui.text(g,font,Ui.t("exchange.balance",data.getInt("energy")),left+34,top+38,Ui.CYAN,pw-120);
        if(maxScroll()>0)Ui.text(g,font,Ui.t("exchange.scroll",scroll+1,Math.min(list.size(),scroll+visibleRows()),list.size()),left+14,top+LIST_Y+visibleRows()*ROW+4,Ui.MUTED,pw-28);
        Component hint=data.getBoolean("creative")?Ui.t("exchange.creative"):sell?Ui.t("exchange.sell_hint"):Ui.t("exchange.file");
        Ui.text(g,font,hint,left+14,top+ph-16,Ui.MUTED,pw-28);
        if(selected<list.size()){var offer=list.getCompound(selected);buy.active=sell?carrying(selected):data.getBoolean("creative")||data.getInt("energy")>=offer.getInt("cost");buy.warning=!buy.active;}
        else buy.active=false;
        super.render(g,mx,my,partial);footerLine(g,top+ph-30);
        for(var icon:icons)if(Ui.inside(mx,my,icon.x(),icon.y(),16,16)){g.renderTooltip(font,icon.item(),mx,my);return;}
        // A name too long for its row reads in full on hover
        for(int i=0;i<rows.size();i++){var row=rows.get(i);if(!row.visible||!row.isHovered())continue;
            var offer=list.getCompound(scroll+i);var stack=ItemStack.of(offer.getCompound("item"));int cost=offer.getInt("cost");
            Component cost_=sell?Ui.t("exchange.gain",cost):Ui.t("exchange.cost",cost);var name=Ui.t("exchange.offer",stack.getCount(),stack.getHoverName());
            if(font.width(name)>row.getWidth()-34-(font.width(cost_)+22))g.renderTooltip(font,font.split(name,220),mx,my);
            break;}
    }
    @Override BeaconClient.Hover itemAt(double x,double y){for(var icon:icons)if(Ui.inside(x,y,icon.x(),icon.y(),16,16))return icon;return null;}
}
