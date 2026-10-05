package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Independent from the nearby/tool status HUD; server state reaches every connected defender. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class BeaconAlerts {
    private static boolean attacking;
    private static long received;
    static void receive(boolean active){attacking=active;received=System.nanoTime();}
    static void clear(){attacking=false;received=0;}
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGuiOverlaysEvent e){e.registerAboveAll("beacon_attack_warning",(gui,g,partial,w,h)->{if(Minecraft.getInstance().screen==null)render(g,w,h);});}
    }
    @SubscribeEvent public static void screen(ScreenEvent.Render.Post e){var mc=Minecraft.getInstance();if(mc.level!=null)render(e.getGuiGraphics(),mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());}
    /** Pulsing red banner with hazard edges. The words carry the meaning; flashing only adds urgency. */
    private static void render(GuiGraphics g,int w,int h){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.options.hideGui||!attacking||System.nanoTime()-received>4_000_000_000L)return;
        draw(g,w,h);
    }
    static void draw(GuiGraphics g,int w,int h){
        var mc=Minecraft.getInstance();
        var text=Ui.t("alert.attack");float scale=Math.min(1.6f,(w-48f)/mc.font.width(text));
        int textWidth=(int)(mc.font.width(text)*scale),bw=textWidth+40,x=(w-bw)/2,y=Math.min(58,Math.max(8,h/5));
        boolean bright=(System.nanoTime()/400_000_000L)%2==0;
        g.pose().pushPose();g.pose().translate(0,0,600);
        g.fill(x-1,y-6,x+bw+1,y+23,0xff05090d);g.fill(x,y-5,x+bw,y+22,bright?0xf0590b12:0xe0300709);
        Ui.hazard(g,x,y-5,bw,3,0xff7a0d12,bright?0xffffd23f:0xffb8901f);
        Ui.hazard(g,x,y+19,bw,3,0xff7a0d12,bright?0xffffd23f:0xffb8901f);
        g.fill(x+8,y+2,x+24,y+15,0xffffd23f);g.fill(x+14,y+4,x+18,y+10,0xff300709);g.fill(x+14,y+11,x+18,y+13,0xff300709);
        g.pose().translate(x+24+(bw-24)/2f,y+4,0);g.pose().scale(scale,scale,1);
        g.drawString(mc.font,text.getVisualOrderText(),-mc.font.width(text)/2,0,bright?0xffff6a6a:0xffff4545,true);g.pose().popPose();
    }
}
