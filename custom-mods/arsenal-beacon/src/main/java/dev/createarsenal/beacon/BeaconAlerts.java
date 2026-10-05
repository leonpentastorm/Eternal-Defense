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
    private static void render(GuiGraphics g,int w,int h){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.options.hideGui||!attacking||System.nanoTime()-received>4_000_000_000L)return;
        String text="BEACON IS BEING ATTACKED";float scale=Math.min(1.6f,(w-20f)/mc.font.width(text));
        int bw=(int)(mc.font.width(text)*scale)+16,x=(w-bw)/2,y=Math.min(58,Math.max(8,h/5));
        boolean bright=(System.nanoTime()/400_000_000L)%2==0;
        g.pose().pushPose();g.pose().translate(0,0,600);g.fill(x,y-5,x+bw,y+21,bright?0xdd590b12:0xbb240609);
        g.fill(x,y-5,x+bw,y-3,bright?0xffff3030:0xffa80e0e);
        g.pose().translate(w/2f,y,0);g.pose().scale(scale,scale,1);
        g.drawString(mc.font,text,-mc.font.width(text)/2,0,bright?0xffff3434:0xffd82b2b,true);g.pose().popPose();
    }
}
