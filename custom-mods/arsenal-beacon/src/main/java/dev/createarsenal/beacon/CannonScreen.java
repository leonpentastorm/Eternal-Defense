package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Support Cannon menu: pick this player's fire support type (left) and buy cannon upgrades (right). */
final class CannonScreen extends BeaconClient.PanelScreen {
    private static final int ROW=18;
    private final BlockPos pos;private CompoundTag data;
    private final List<Ui.UiButton> types=new ArrayList<>(),ups=new ArrayList<>();
    private String hover="";
    CannonScreen(BlockPos pos,CompoundTag data){super(Ui.t("cannon.title"));this.pos=pos;this.data=data;}
    static void receive(CannonControl.Open packet){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof CannonScreen s&&s.pos.equals(packet.pos())){s.data=packet.data();s.message=packet.message().isEmpty()?s.message:Ui.t("cannon.msg."+packet.message()).getString();}
        else{var s=new CannonScreen(packet.pos(),packet.data());mc.setScreen(s);}
    }
    @Override Component subtitle(){return Ui.t(data.getBoolean("linked")?"cannon.linked":"cannon.unlinked",data.getInt("range"));}
    private int level(int up){int[] l=data.getIntArray("up");return up<l.length?l[up]:0;}
    private boolean affordable(int price){return data.getBoolean("creative")||data.getInt("energy")>=price;}
    @Override protected void init(){
        super.init();pw=Math.min(450,width-16);ph=Math.min(250,height-16);left=(width-pw)/2;top=(height-ph)/2;types.clear();ups.clear();
        closeButton();
        int colW=(pw-36)/2,x1=left+14,x2=left+22+colW;
        for(int i=0;i<CannonUpgrades.FireType.values().length;i++){
            int index=i;var row=new Ui.UiButton(x1,top+60+i*ROW,colW,ROW-2,Component.empty(),Ui.Look.ROW,b->BeaconNetwork.CHANNEL.sendToServer(new CannonControl.Act(pos,CannonControl.SELECT,index)));
            row.painter((g,self,h)->paintType(g,self,index,h)).noLabel();types.add(addRenderableWidget(row));
        }
        for(int i=0;i<CannonUpgrades.Upgrade.values().length;i++){
            int index=i;var row=new Ui.UiButton(x2,top+60+i*ROW,colW,ROW-2,Component.empty(),Ui.Look.ROW,b->BeaconNetwork.CHANNEL.sendToServer(new CannonControl.Act(pos,CannonControl.UPGRADE,index)));
            row.painter((g,self,h)->paintUpgrade(g,self,index,h)).noLabel();ups.add(addRenderableWidget(row));
        }
    }
    private void paintType(GuiGraphics g,Ui.UiButton row,int index,boolean hovered){
        var type=CannonUpgrades.FireType.values()[index];if(hovered)hover="type."+type.id;
        boolean on=data.getInt("fire")==index;
        int volleys=CannonUpgrades.volleys(type,level(CannonUpgrades.Upgrade.VOLLEY.ordinal()));
        Ui.text(g,font,Ui.t("cannon.type."+type.id),row.getX()+6,row.getY()+4,on?Ui.INK:Ui.MUTED,row.getWidth()-50);
        Ui.right(g,font,Ui.t(volleys==1?"cannon.shell":"cannon.shells",volleys),row.getX()+row.getWidth()-5,row.getY()+4,on?Ui.CYAN:Ui.MUTED);
    }
    private void paintUpgrade(GuiGraphics g,Ui.UiButton row,int index,boolean hovered){
        var up=CannonUpgrades.Upgrade.values()[index];if(hovered)hover="up."+up.id;
        int lv=level(index),price=up.price(lv);
        Ui.text(g,font,Ui.t("cannon.up."+up.id),row.getX()+6,row.getY()+4,price<0?Ui.BRASS:Ui.INK,row.getWidth()-(up.max()*6+60));
        int px=row.getX()+row.getWidth()-5;
        if(price<0){Ui.right(g,font,Ui.t("cannon.max"),px,row.getY()+4,Ui.BRASS);px-=font.width(Ui.t("cannon.max"))+4;}
        else{Component c=Ui.t("exchange.cost",price);Ui.right(g,font,c,px,row.getY()+4,affordable(price)?Ui.CYAN:Ui.ORANGE);px-=font.width(c)+4;}
        Ui.pips(g,px-Ui.pipsWidth(up.max()),row.getY()+5,lv,up.max(),price<0?Ui.BRASS:Ui.CYAN);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        panel(g);hover="";
        int colW=(pw-36)/2,x1=left+14,x2=left+22+colW;
        Ui.text(g,font,Ui.t("cannon.fire"),x1,top+47,Ui.MUTED,colW);Ui.text(g,font,Ui.t("cannon.upgrades"),x2,top+47,Ui.MUTED,colW);
        Ui.rule(g,x1,top+57,colW);Ui.rule(g,x2,top+57,colW);
        for(int i=0;i<types.size();i++){types.get(i).selected=data.getInt("fire")==i;}
        for(int i=0;i<ups.size();i++){var up=CannonUpgrades.Upgrade.values()[i];int price=up.price(level(i));ups.get(i).warning=price>=0&&!affordable(price);ups.get(i).active=price>=0;}
        // the card under the type list explains whatever is hovered, or the chosen type
        super.render(g,mx,my,partial);
        String key=hover.isEmpty()?"type."+CannonUpgrades.FireType.of(data.getInt("fire")).id:hover;
        int cy=top+60+types.size()*ROW+2,ch=top+ph-34-cy;
        if(ch>14){Ui.card(g,x1,cy,colW,ch,Ui.CYAN);Ui.wrap(g,font,Ui.t("cannon."+key+".desc"),x1+6,cy+4,colW-12,Ui.INK,Math.max(1,(ch-6)/10));}
        var energy=new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get());
        footerLine(g,top+ph-30);
        g.renderItem(energy,left+12,top+ph-18);
        Ui.text(g,font,data.getBoolean("creative")?Ui.t("exchange.creative"):Ui.t("exchange.balance",data.getInt("energy")),left+32,top+ph-14,Ui.CYAN,pw-50);
    }
}
