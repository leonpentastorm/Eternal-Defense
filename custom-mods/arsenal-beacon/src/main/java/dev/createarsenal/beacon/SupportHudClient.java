package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client drawing of the chosen fire support: its icon, a large banner with its name, and the banner shown above the hotbar. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SupportHudClient {
    private SupportHudClient(){}
    static Component name(CannonUpgrades.FireType type){return Ui.t("cannon.type."+type.id);}

    /** Draws a type's icon at {@code size} pixels (16, 32 or 64 use their own pre-reduced texture). */
    static void icon(GuiGraphics g,CannonUpgrades.FireType type,int x,int y,int size){
        int src=size<=16?16:size<=32?32:64;
        g.blit(SupportHud.icon(type,src),x,y,size,size,0,0,src,src,src,src);
    }

    /** The "calling this" banner: a big icon and the type's name in large letters, tinted in its colour. */
    static void banner(GuiGraphics g,Font font,int x,int y,int w,int h,CannonUpgrades.FireType type,Component label){
        int accent=SupportHud.accent(type);
        Ui.card(g,x,y,w,h,accent);
        int icon=Math.min(h-6,64);
        icon(g,type,x+4,y+(h-icon)/2,icon);
        int tx=x+icon+10,room=w-icon-16;
        if(label!=null)Ui.text(g,font,label,tx,y+4,Ui.MUTED,room);
        var name=name(type).copy().withStyle(net.minecraft.ChatFormatting.BOLD);
        float scale=Math.max(1f,Math.min(2f,(float)room/Math.max(1,font.width(name))));
        scale=Math.min(scale,(h-(label!=null?16:8))/9f);
        var pose=g.pose();pose.pushPose();pose.translate(tx,y+(label!=null?4+(h-4-9*scale)/2f:(h-9*scale)/2f),0);pose.scale(scale,scale,1f);
        g.drawString(font,name.getVisualOrderText(),0,0,accent,true);pose.popPose();
    }

    @SubscribeEvent public static void register(RegisterGuiOverlaysEvent e){
        e.registerAbove(VanillaGuiOverlay.HOTBAR.id(),"fire_support",(gui,g,partial,w,h)->{
            var mc=Minecraft.getInstance();var p=mc.player;
            if(p==null||mc.screen!=null||mc.options.hideGui)return;
            var flare=ArsenalBeacon.FIRE_FLARE.get();
            if(!p.getMainHandItem().is(flare)&&!p.getOffhandItem().is(flare))return;
            int bw=Math.min(200,w-20),bh=44,x=(w-bw)/2,y=h-bh-58;
            banner(g,mc.font,x,y,bw,bh,SupportHud.current(),Ui.t("cannon.hud"));
        });
    }
}
