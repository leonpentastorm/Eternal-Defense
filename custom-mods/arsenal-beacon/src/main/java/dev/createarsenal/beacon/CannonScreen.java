package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Support Cannon menu: the chosen fire support in large letters, the six types to choose from (left) and the upgrades to buy (right). */
final class CannonScreen extends BeaconClient.PanelScreen {
    private static final int BANNER=36;
    private final BlockPos pos;private CompoundTag data;
    private final List<Ui.UiButton> types=new ArrayList<>(),ups=new ArrayList<>();
    private String hover="";private int row=17;
    CannonScreen(BlockPos pos,CompoundTag data){super(Ui.t("cannon.title"));this.pos=pos;this.data=data;SupportHud.chosen=data.getInt("fire");}
    static void receive(CannonControl.Open packet){
        var mc=Minecraft.getInstance();
        SupportHud.chosen=packet.data().getInt("fire");
        if(mc.screen instanceof CannonScreen s&&s.pos.equals(packet.pos())){s.data=packet.data();s.message=packet.message().isEmpty()?s.message:Ui.t("cannon.msg."+packet.message()).getString();}
        else{var s=new CannonScreen(packet.pos(),packet.data());mc.setScreen(s);}
    }
    @Override Component subtitle(){return Ui.t("cannon.owner",data.getString("owner"));}
    private int level(int up){int[] l=data.getIntArray("up");return up<l.length?l[up]:0;}
    private boolean affordable(Economy.Price price){
        if(data.getBoolean("creative"))return true;
        if(price.ardent())return data.getInt("energy")>=price.amount();
        int[] stock=data.getIntArray("stock");int slot=price.item()==ArsenalBeacon.PLATING.get()?0:price.item()==ArsenalBeacon.LOGISTICS.get()?1:price.item()==ArsenalBeacon.COIL.get()?2:3;
        return slot<stock.length&&stock[slot]>=price.amount();
    }
    private int listTop(){return top+58;}
    @Override protected void init(){
        super.init();pw=Math.min(460,width-16);ph=Math.min(300,height-16);left=(width-pw)/2;top=(height-ph)/2;types.clear();ups.clear();
        closeButton();
        // ten upgrade rows must fit between the headings and the balance line: shrink the rows on a short window
        row=Math.max(13,Math.min(17,(ph-58-36)/CannonUpgrades.Upgrade.values().length));
        int colW=(pw-36)/2,x1=left+14,x2=left+22+colW;
        int typeTop=listTop()+BANNER+4;
        for(int i=0;i<CannonUpgrades.FireType.values().length;i++){
            int index=i;var r=new Ui.UiButton(x1,typeTop+i*row,colW,row-1,Component.empty(),Ui.Look.ROW,b->BeaconNetwork.CHANNEL.sendToServer(new CannonControl.Act(pos,CannonControl.SELECT,index)));
            r.painter((g,self,h)->paintType(g,self,index,h)).noLabel();types.add(addRenderableWidget(r));
        }
        for(int i=0;i<CannonUpgrades.Upgrade.values().length;i++){
            int index=i;var r=new Ui.UiButton(x2,listTop()+i*row,colW,row-1,Component.empty(),Ui.Look.ROW,b->BeaconNetwork.CHANNEL.sendToServer(new CannonControl.Act(pos,CannonControl.UPGRADE,index)));
            r.painter((g,self,h)->paintUpgrade(g,self,index,h)).noLabel();ups.add(addRenderableWidget(r));
        }
    }
    private void paintType(GuiGraphics g,Ui.UiButton r,int index,boolean hovered){
        var type=CannonUpgrades.FireType.values()[index];if(hovered)hover="type."+type.id;
        boolean on=data.getInt("fire")==index;
        int volleys=CannonUpgrades.volleys(type,level(CannonUpgrades.Upgrade.VOLLEY.ordinal()));
        int icon=Math.min(16,r.getHeight()-1);
        SupportHudClient.icon(g,type,r.getX()+3,r.getY()+(r.getHeight()-icon)/2,icon);
        Ui.text(g,font,Ui.t("cannon.type."+type.id),r.getX()+icon+8,r.getY()+(r.getHeight()-8)/2,on?Ui.INK:Ui.MUTED,r.getWidth()-icon-60);
        Ui.right(g,font,Ui.t(volleys==1?"cannon.shell":"cannon.shells",volleys),r.getX()+r.getWidth()-5,r.getY()+(r.getHeight()-8)/2,on?SupportHud.accent(type):Ui.MUTED);
    }
    private void paintUpgrade(GuiGraphics g,Ui.UiButton r,int index,boolean hovered){
        var up=CannonUpgrades.Upgrade.values()[index];if(hovered)hover="up."+up.id;
        int lv=level(index),ty=r.getY()+(r.getHeight()-8)/2;var price=Economy.cannon(up,lv);
        Ui.text(g,font,Ui.t("cannon.up."+up.id),r.getX()+6,ty,price==null?Ui.BRASS:Ui.INK,r.getWidth()-(up.max()*6+70));
        int px=r.getX()+r.getWidth()-5;
        if(price==null){Ui.right(g,font,Ui.t("cannon.max"),px,ty,Ui.BRASS);px-=font.width(Ui.t("cannon.max"))+4;}
        else{
            boolean ok=affordable(price);Component c=Component.literal(price.ardent()?String.valueOf(price.amount()):"x"+price.amount());
            g.renderItem(new ItemStack(price.item()),px-14,r.getY()+(r.getHeight()-16)/2);Ui.right(g,font,c,px-16,ty,ok?Ui.CYAN:Ui.ORANGE);px-=font.width(c)+20;
        }
        Ui.pips(g,px-Ui.pipsWidth(up.max()),ty+1,lv,up.max(),price==null?Ui.BRASS:Ui.CYAN);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        panel(g);hover="";
        int colW=(pw-36)/2,x1=left+14,x2=left+22+colW;
        Ui.text(g,font,Ui.t("cannon.fire"),x1,top+47,Ui.MUTED,colW);Ui.text(g,font,Ui.t("cannon.upgrades"),x2,top+47,Ui.MUTED,colW);
        Ui.rule(g,x1,top+55,colW);Ui.rule(g,x2,top+55,colW);
        for(int i=0;i<types.size();i++){types.get(i).selected=data.getInt("fire")==i;}
        for(int i=0;i<ups.size();i++){var up=CannonUpgrades.Upgrade.values()[i];var price=Economy.cannon(up,level(i));ups.get(i).warning=price!=null&&!affordable(price);ups.get(i).active=price!=null;}
        // the big banner: what this player's flares call down right now
        SupportHudClient.banner(g,font,x1,listTop(),colW,BANNER,CannonUpgrades.FireType.of(data.getInt("fire")),Ui.t("cannon.current"));
        super.render(g,mx,my,partial);
        // the card under the type list explains whatever is hovered, or the chosen type
        String key=hover.isEmpty()?"type."+CannonUpgrades.FireType.of(data.getInt("fire")).id:hover;
        int cy=listTop()+BANNER+4+types.size()*row+3,ch=top+ph-34-cy;
        if(ch>14){Ui.card(g,x1,cy,colW,ch,Ui.CYAN);Ui.wrap(g,font,Ui.t("cannon."+key+".desc"),x1+6,cy+4,colW-12,Ui.INK,Math.max(1,(ch-6)/10));}
        var energy=new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get());
        footerLine(g,top+ph-30);
        g.renderItem(energy,left+12,top+ph-18);
        Ui.text(g,font,data.getBoolean("creative")?Ui.t("exchange.creative"):Ui.t("exchange.balance",data.getInt("energy")),left+32,top+ph-14,Ui.CYAN,pw-50);
    }
}
