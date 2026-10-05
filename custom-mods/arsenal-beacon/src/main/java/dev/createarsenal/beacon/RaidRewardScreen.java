package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Payouts arrive from the same server plan that actually queues earned rewards. */
final class RaidRewardScreen extends BeaconClient.PanelScreen {
    private final Screen parent;private final ListTag tiers;private final int currentTier,cap;private int tier,scroll;private boolean hard;
    private final List<BeaconClient.Hover> icons=new ArrayList<>();
    private final List<Ui.UiButton> tierButtons=new ArrayList<>();private Ui.UiButton normalButton,hardButton;
    RaidRewardScreen(Screen parent,CompoundTag state){
        super(Ui.t("rewards.title"));this.parent=parent;tiers=state.getList("rewardTiers",Tag.TAG_COMPOUND).copy();
        currentTier=Math.max(0,Math.min(10,state.getInt("prospectiveTier")));cap=Math.max(0,Math.min(10,state.getInt("raidLimit")));tier=currentTier;
        hard=state.getBoolean("nextHard")||state.getBoolean("hardRaid");
    }
    @Override Component subtitle(){return Ui.t("rewards.subtitle");}
    @Override protected void init(){
        super.init();pw=Math.min(600,width-16);ph=Math.min(350,height-16);left=(width-pw)/2;top=(height-ph)/2;clearWidgets();tierButtons.clear();
        closeButton();
        int step=Math.max(13,Math.min(22,(ph-96)/11));
        for(int i=0;i<=10;i++){
            int value=i;var b=button(Ui.t("rewards.tier",i),left+14,top+36+i*step,86,Math.max(12,step-1),Ui.Look.ROW,x->{tier=value;scroll=0;});
            b.painter((g,self,hover)->{
                if(value==currentTier)g.fill(self.getX()+self.getWidth()-7,self.getY()+2,self.getX()+self.getWidth()-3,self.getY()+self.getHeight()-2,Ui.BRASS);
                if(value>cap)g.fill(self.getX()+1,self.getY()+self.getHeight()-2,self.getX()+self.getWidth()-1,self.getY()+self.getHeight()-1,Ui.ORANGE);
            });
            tierButtons.add(b);
        }
        int right=left+112;
        normalButton=button(Ui.t("rewards.normal"),right,top+36,110,Ui.Look.NORMAL,b->{hard=false;scroll=0;});
        hardButton=button(Ui.t("rewards.hard"),right+114,top+36,140,Ui.Look.NORMAL,b->{hard=true;scroll=0;});
        button(Ui.t("rewards.rescan"),left+14,top+ph-34,86,Ui.Look.NORMAL,b->BeaconNetwork.action("survey",""));
        button(Ui.t("back"),left+pw-94,top+ph-34,80,Ui.Look.PRIMARY,b->onClose());
    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent instanceof BeaconClient.PanelScreen?parent:new BeaconClient.ControlScreen());}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,scroll-(int)amount);return true;}
    private static String key(ItemStack stack){return stack.getItem()+""+stack.getTag();}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        icons.clear();panel(g);int right=left+112,rw=pw-right+left-14;
        for(int i=0;i<tierButtons.size();i++)tierButtons.get(i).selected=i==tier;
        normalButton.selected=!hard;hardButton.selected=hard;hardButton.accent=Ui.BRASS;
        var row=tiers.size()>tier?tiers.getCompound(tier):new CompoundTag();
        Ui.text(g,font,Ui.t(hard?"rewards.head_hard":"rewards.head_normal",tier),right,top+62,hard?Ui.BRASS:Ui.CYAN,rw);
        boolean compact=ph<280;
        int y=Ui.wrap(g,font,Ui.t("rewards.note",row.getInt("waves")),right,top+75,rw,Ui.MUTED,compact?1:2);
        if(tier==currentTier)Ui.text(g,font,Ui.t("rewards.current"),right,y,Ui.BRASS,rw);
        else if(tier>cap)Ui.text(g,font,Ui.t("rewards.capped",cap),right,y,Ui.ORANGE,rw);
        else Ui.text(g,font,Ui.t("rewards.preview"),right,y,Ui.MUTED,rw);
        // Compare against the tier below so players can judge whether the next upgrade is worth it.
        Map<String,Integer> before=new HashMap<>();
        if(tier>0&&tiers.size()>tier-1)for(var entry:tiers.getCompound(tier-1).getList(hard?"hard":"normal",Tag.TAG_COMPOUND)){var n=(CompoundTag)entry;before.merge(key(ItemStack.of(n.getCompound("item"))),n.getInt("count"),Integer::sum);}
        var items=row.getList(hard?"hard":"normal",Tag.TAG_COMPOUND);
        int listTop=y+(compact?12:14),visible=Math.max(1,(top+ph-48-listTop)/20);scroll=Math.min(scroll,Math.max(0,items.size()-visible));
        Ui.inset(g,right-4,listTop-3,rw+4,visible*20+4);
        for(int i=scroll;i<Math.min(items.size(),scroll+visible);i++){
            var n=items.getCompound(i);var item=ItemStack.of(n.getCompound("item"));int iy=listTop+(i-scroll)*20,count=n.getInt("count");
            g.renderItem(item,right,iy);icons.add(new BeaconClient.Hover(item,right,iy));
            int delta=tier==0?0:count-before.getOrDefault(key(item),0);
            Component delta_=delta>0?Ui.t("rewards.more",delta):Component.empty();
            int deltaWidth=delta>0?font.width(delta_)+6:0;
            Ui.text(g,font,Ui.t("rewards.line",count,item.getHoverName()),right+23,iy+4,Ui.INK,rw-28-deltaWidth);
            if(delta>0)Ui.right(g,font,delta_,right+rw-4,iy+4,Ui.CYAN);
        }
        if(items.size()>visible)Ui.text(g,font,Ui.t("rewards.scroll",items.size()),right,top+ph-42,Ui.MUTED,rw);
        Ui.text(g,font,Ui.t("rewards.interval",row.getInt("interval")),right,top+ph-30,Ui.MUTED,rw-96);
        super.render(g,mx,my,partial);footer(g);var hover=itemAt(mx,my);if(hover!=null)g.renderTooltip(font,hover.item(),mx,my);
    }
    @Override BeaconClient.Hover itemAt(double x,double y){for(var icon:icons)if(Ui.inside(x,y,icon.x(),icon.y(),16,16))return icon;return null;}
}
