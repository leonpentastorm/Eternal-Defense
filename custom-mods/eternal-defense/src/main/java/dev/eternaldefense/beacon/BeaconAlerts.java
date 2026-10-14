package dev.eternaldefense.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Independent from the nearby/tool status HUD; server state reaches every connected defender. The banner comes with the owner's alarm
 * (beacon_attacked.ogg: one double beep, a 0.6 s clip), heard wherever the player is: when it appears, then again after a 0.5 s pause,
 * "beep beep - pause - beep beep", for as long as it stays up ({@link #ALARM_PERIOD_TICKS}).
 */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
public final class BeaconAlerts {
    private static boolean attacking;
    private static long received;
    /** Client ticks since the alarm last started (-1: not in this attack yet). */
    private static int sinceAlarm=-1;
    /** The alarm clip (0.6 s) and the pause after it (0.5 s): it starts again every 22 client ticks while the banner shows. */
    static final double ALARM_CLIP_SECONDS=0.6,ALARM_PAUSE_SECONDS=0.5;
    static final int ALARM_PERIOD_TICKS=(int)Math.round((ALARM_CLIP_SECONDS+ALARM_PAUSE_SECONDS)*20);
    static void receive(boolean active){attacking=active;received=System.nanoTime();if(!active)sinceAlarm=-1;}
    static void clear(){attacking=false;received=0;sinceAlarm=-1;}
    /** Pure: should the alarm start now? {@code ticksSinceLast} counts client ticks since it last started (negative: none yet in this attack). */
    static boolean alarm(boolean showing,int ticksSinceLast){return showing&&(ticksSinceLast<0||ticksSinceLast>=ALARM_PERIOD_TICKS);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        boolean showing=mc.level!=null&&mc.player!=null&&attacking&&System.nanoTime()-received<=4_000_000_000L;
        if(sinceAlarm>=0)sinceAlarm++;
        if(alarm(showing,sinceAlarm)){
            sinceAlarm=0;
            mc.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(DefenseSounds.BEACON_ATTACKED.get().getLocation(),net.minecraft.sounds.SoundSource.BLOCKS,1f,1f,
                net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom(),false,0,net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,0,0,0,true));
        }
        if(!showing)sinceAlarm=-1;
    }
    @Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
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
