package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/**
 * Support Cannon menu: the chosen fire support in large letters, the fire support types to choose from (left, a scrolling list) and the
 * upgrades to buy (right: the three-level upgrades first, then the one-level ones). Every row can be hovered to read what it does, a
 * maxed upgrade included.
 */
final class CannonScreen extends BeaconClient.PanelScreen {
    private static final int BANNER=36;
    /** Upgrades as shown: the ones with several levels on top, then the single-level ones (the packets still use the enum order). */
    static final List<CannonUpgrades.Upgrade> ORDER=Arrays.stream(CannonUpgrades.Upgrade.values()).sorted(Comparator.comparingInt(u->-u.max())).toList();
    private final BlockPos pos;private CompoundTag data;
    private final List<Ui.UiButton> types=new ArrayList<>(),ups=new ArrayList<>();
    private String hover="";private int row=17,typeScroll,visibleTypes;
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
    private int typeTop(){return listTop()+BANNER+4;}
    /** Pure: how many type rows fit above a description card of at least {@code card} pixels. */
    static int visibleRows(int space,int row,int card,int total){return Math.max(3,Math.min(total,(space-card)/Math.max(1,row)));}
    /** Pure: the first row to show so that row {@code chosen} is on screen, starting from {@code scroll}. */
    static int scrollTo(int scroll,int chosen,int visible,int total){
        int s=Math.max(0,Math.min(scroll,total-visible));
        if(chosen<s)s=chosen;else if(chosen>=s+visible)s=chosen-visible+1;
        return Math.max(0,Math.min(s,Math.max(0,total-visible)));
    }
    @Override protected void init(){
        super.init();pw=Math.min(460,width-16);ph=Math.min(300,height-16);left=(width-pw)/2;top=(height-ph)/2;types.clear();ups.clear();
        closeButton();
        // ten upgrade rows must fit between the headings and the balance line: shrink the rows on a short window
        row=Math.max(13,Math.min(17,(ph-58-36)/CannonUpgrades.Upgrade.values().length));
        int colW=(pw-36)/2,x1=left+14,x2=left+22+colW;
        int total=CannonUpgrades.FireType.values().length;
        visibleTypes=visibleRows(top+ph-34-typeTop(),row,46,total);
        typeScroll=scrollTo(typeScroll,data.getInt("fire"),visibleTypes,total);
        for(int i=0;i<total;i++){
            int index=i;var r=new Ui.UiButton(x1,typeTop()+i*row,colW-6,row-1,Component.empty(),Ui.Look.ROW,b->BeaconNetwork.CHANNEL.sendToServer(new CannonControl.Act(pos,CannonControl.SELECT,index)));
            r.painter((g,self,h)->paintType(g,self,index,h)).noLabel();types.add(addRenderableWidget(r));
        }
        layoutTypes();
        for(int i=0;i<ORDER.size();i++){
            var up=ORDER.get(i);int index=up.ordinal();
            var r=new Ui.UiButton(x2,listTop()+i*row,colW,row-1,Component.empty(),Ui.Look.ROW,b->BeaconNetwork.CHANNEL.sendToServer(new CannonControl.Act(pos,CannonControl.UPGRADE,index)));
            r.painter((g,self,h)->paintUpgrade(g,self,index)).noLabel();ups.add(addRenderableWidget(r));
        }
    }
    /** Shows the rows of the scrolled window, in place, and hides the rest. */
    private void layoutTypes(){
        for(int i=0;i<types.size();i++){
            var r=types.get(i);int slot=i-typeScroll;r.visible=slot>=0&&slot<visibleTypes;r.setY(typeTop()+Math.max(0,slot)*row);
        }
    }
    private boolean overTypes(double mx,double my){int colW=(pw-36)/2;return mx>=left+14&&mx<left+14+colW&&my>=typeTop()&&my<typeTop()+visibleTypes*row;}
    @Override public boolean mouseScrolled(double mx,double my,double amount){
        if(overTypes(mx,my)){int total=types.size();typeScroll=Math.max(0,Math.min(total-visibleTypes,typeScroll-(int)Math.signum(amount)));layoutTypes();return true;}
        return super.mouseScrolled(mx,my,amount);
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
    private void paintUpgrade(GuiGraphics g,Ui.UiButton r,int index){
        var up=CannonUpgrades.Upgrade.values()[index];if(r.isHovered())hover="up."+up.id;   // a maxed (inactive) row still explains itself
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
    private void scrollbar(GuiGraphics g){
        int total=types.size();if(total<=visibleTypes)return;
        int colW=(pw-36)/2,x=left+14+colW-4,y=typeTop(),h=visibleTypes*row-1;
        g.fill(x,y,x+3,y+h,0xff1b2833);
        int thumb=Math.max(10,h*visibleTypes/total),ty=y+(h-thumb)*typeScroll/Math.max(1,total-visibleTypes);
        g.fill(x,ty,x+3,ty+thumb,Ui.CYAN);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        panel(g);hover="";
        int colW=(pw-36)/2,x1=left+14,x2=left+22+colW;
        Ui.text(g,font,Ui.t("cannon.fire"),x1,top+47,Ui.MUTED,colW);Ui.text(g,font,Ui.t("cannon.upgrades"),x2,top+47,Ui.MUTED,colW);
        Ui.rule(g,x1,top+55,colW);Ui.rule(g,x2,top+55,colW);
        for(int i=0;i<types.size();i++){types.get(i).selected=data.getInt("fire")==i;}
        for(int i=0;i<ups.size();i++){var up=ORDER.get(i);var price=Economy.cannon(up,level(up.ordinal()));ups.get(i).warning=price!=null&&!affordable(price);ups.get(i).active=price!=null;}
        // the big banner: what this player's flares call down right now
        SupportHudClient.banner(g,font,x1,listTop(),colW,BANNER,CannonUpgrades.FireType.of(data.getInt("fire")),Ui.t("cannon.current"));
        super.render(g,mx,my,partial);
        scrollbar(g);
        // the card under the type list explains whatever is hovered, or the chosen type
        String key=hover.isEmpty()?"type."+CannonUpgrades.FireType.of(data.getInt("fire")).id:hover;
        int cy=typeTop()+visibleTypes*row+3,ch=top+ph-34-cy;
        if(ch>14){Ui.card(g,x1,cy,colW,ch,Ui.CYAN);Ui.wrap(g,font,Ui.t("cannon."+key+".desc"),x1+6,cy+4,colW-12,Ui.INK,Math.max(1,(ch-6)/10));}
        var energy=new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get());
        footerLine(g,top+ph-30);
        g.renderItem(energy,left+12,top+ph-18);
        Ui.text(g,font,data.getBoolean("creative")?Ui.t("exchange.creative"):Ui.t("exchange.balance",data.getInt("energy")),left+32,top+ph-14,Ui.CYAN,pw-50);
        for(int i=0;i<ups.size();i++)if(ups.get(i).isHovered()){
            var up=ORDER.get(i);var price=Economy.cannon(up,level(up.ordinal()));
            if(price!=null)Ui.materialTooltip(g,font,List.of(Ui.t("cannon.up."+up.id),Ui.t("cannon.up."+up.id+".desc")),Ui.singleCost(new ItemStack(price.item()),price.amount(),Economy.have(Minecraft.getInstance().player,price.item())),mx,my);
            else g.renderTooltip(font,List.of(Ui.t("cannon.up."+up.id)),Optional.of(new Ui.InfoTip(List.of(Ui.Block.line(Ui.t("cannon.up."+up.id+".desc"),Ui.MUTED),Ui.Block.marked(1,Ui.t("cannon.maxed"),Ui.BRASS)))),mx,my);
        }
    }
}
