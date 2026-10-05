package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Payouts arrive from the same server plan that actually queues earned rewards. */
final class RaidRewardScreen extends BeaconClient.PanelScreen {
    private final Screen parent;private final ListTag tiers;private int tier,scroll;private boolean hard;
    private final List<BeaconClient.Hover> icons=new ArrayList<>();
    RaidRewardScreen(Screen parent,CompoundTag state){super("RAID REWARDS / SHARED TEAM CACHE");this.parent=parent;tiers=state.getList("rewardTiers",Tag.TAG_COMPOUND).copy();tier=Math.max(0,Math.min(10,state.getInt("prospectiveTier")));hard=state.getBoolean("nextHard")||state.getBoolean("hardRaid");}
    @Override protected void init(){super.init();pw=Math.min(600,width-16);ph=Math.min(350,height-16);left=(width-pw)/2;top=(height-ph)/2;clearWidgets();
        button("Back",left+pw-70,top+8,56,b->onClose());
        for(int i=0;i<=10;i++){int value=i;int step=Math.max(12,Math.min(22,(ph-92)/11));addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Tier "+i),b->{tier=value;scroll=0;}).bounds(left+14,top+45+i*step,82,Math.max(11,step-1)).build());}
        int right=left+110;button("Normal raid",right,top+35,110,b->{hard=false;scroll=0;});button("Hard raid + boss",right+116,top+35,128,b->{hard=true;scroll=0;});
        button("Rescan base",left+14,top+ph-37,96,b->BeaconNetwork.action("survey",""));
    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent instanceof BeaconClient.PanelScreen?parent:new BeaconClient.ControlScreen());}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,scroll-(int)amount);return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        icons.clear();panel(g);int right=left+110,rw=pw-125;var row=tiers.size()>tier?tiers.getCompound(tier):new CompoundTag();
        text(g,"TIER "+tier+" / "+(hard?"HARD RAID":"NORMAL RAID"),right,top+63,hard?0xffffa36c:0xff5ae2df);
        wrap(g,"Guaranteed total for one successful defense, including "+row.getInt("waves")+" wave payouts. Shared between all players.",right,top+79,rw,0xff9fb3bc);
        var items=row.getList(hard?"hard":"normal",Tag.TAG_COMPOUND);int visible=Math.max(1,(ph-149)/20);scroll=Math.min(scroll,Math.max(0,items.size()-visible));
        for(int i=scroll;i<Math.min(items.size(),scroll+visible);i++){var n=items.getCompound(i);var item=ItemStack.of(n.getCompound("item"));int y=top+112+(i-scroll)*20;g.renderItem(item,right,y);icons.add(new BeaconClient.Hover(item,right,y));text(g,font.plainSubstrByWidth(n.getInt("count")+" x "+item.getHoverName().getString(),rw-24),right+23,y+4,0xffedf5f8);}
        text(g,"After payout: "+row.getInt("interval")+" active days between raids",right,top+ph-26,0xff9fb3bc);
        if(items.size()>visible)text(g,"Scroll to see every reward",right,top+ph-40,0xff9fb3bc);
        super.render(g,mx,my,partial);var hover=itemAt(mx,my);if(hover!=null)g.renderTooltip(font,hover.item(),mx,my);
    }
    @Override BeaconClient.Hover itemAt(double x,double y){for(var icon:icons)if(x>=icon.x()&&x<icon.x()+16&&y>=icon.y()&&y<icon.y()+16)return icon;return null;}
}
