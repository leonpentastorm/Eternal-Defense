package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/**
 * Centre-screen announcements: raid start, each wave, victory, defeat, and support calls.
 * Big events queue one after another; support calls stack as small toasts underneath.
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class BeaconPopups {
    enum Style{RAID,WAVE,VICTORY,DEFEAT,SUPPORT}
    record Popup(Style style,Component title,Component sub,long durationMs){}
    private static final List<Popup> QUEUE=new ArrayList<>();
    private static final List<Popup> TOASTS=new ArrayList<>();
    private static Popup current;private static long startedAt;
    private static final List<long[]> TOAST_TIMES=new ArrayList<>();

    static void show(BeaconNetwork.Announce a){
        Popup popup=switch(a.kind()){
            case "raid_start" -> new Popup(Style.RAID,a.b()==1?Ui.t("popup.raid_hard"):Ui.t("popup.raid_start"),Ui.t("popup.raid_sub",a.a()),3200);
            case "wave" -> new Popup(Style.WAVE,Ui.t("popup.wave",a.a(),a.b()),Ui.t("popup.wave_sub",a.who()),2600);
            case "victory" -> new Popup(Style.VICTORY,Ui.t("popup.victory"),Ui.t("popup.victory_sub"),6500);
            case "defeat" -> new Popup(Style.DEFEAT,Ui.t("popup.defeat"),Ui.t("popup.defeat_sub"),4200);
            case "support" -> new Popup(Style.SUPPORT,Ui.t("popup.support",a.who(),Ui.t("support.name."+a.what())),Component.empty(),4200);
            default -> null;
        };
        if(popup==null)return;
        if(popup.style()==Style.SUPPORT){TOASTS.add(popup);TOAST_TIMES.add(new long[]{net.minecraft.Util.getMillis()});if(TOASTS.size()>3){TOASTS.remove(0);TOAST_TIMES.remove(0);}sound(SoundEvents.UI_TOAST_IN);return;}
        QUEUE.add(popup);
    }
    static void clear(){QUEUE.clear();TOASTS.clear();TOAST_TIMES.clear();current=null;}
    private static void sound(net.minecraft.sounds.SoundEvent event){Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event,1f));}

    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void popupOverlay(RegisterGuiOverlaysEvent e){e.registerAboveAll("beacon_popups",(gui,g,partial,w,h)->render(g,w,h));}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
    @SubscribeEvent public static void screen(ScreenEvent.Render.Post e){var mc=Minecraft.getInstance();if(mc.level!=null)render(e.getGuiGraphics(),mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());}

    static void render(GuiGraphics g,int w,int h){
        var mc=Minecraft.getInstance();if(mc.options.hideGui&&mc.screen==null)return;
        long now=net.minecraft.Util.getMillis();
        if(current!=null&&now-startedAt>current.durationMs())current=null;
        if(current==null&&!QUEUE.isEmpty()){current=QUEUE.remove(0);startedAt=now;
            switch(current.style()){case VICTORY->sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);case RAID->sound(SoundEvents.RAID_HORN.get());case WAVE->sound(SoundEvents.BELL_BLOCK);case DEFEAT->sound(SoundEvents.WITHER_DEATH);default->{}}}
        g.pose().pushPose();g.pose().translate(0,0,650);
        if(current!=null)draw(g,mc,w,h,current,now-startedAt);
        for(int i=0;i<TOASTS.size();){
            long age=now-TOAST_TIMES.get(i)[0];
            if(age>TOASTS.get(i).durationMs()){TOASTS.remove(i);TOAST_TIMES.remove(i);continue;}
            toast(g,mc,w,h,TOASTS.get(i),age,i);i++;
        }
        g.pose().popPose();
    }
    /** 0..1 fade: quick in, hold, slow out. */
    static float alpha(long age,long total){float in=Math.min(1f,age/220f),out=Math.min(1f,(total-age)/520f);return Math.max(0f,Math.min(in,out));}
    static int argb(int rgb,float a){return ((int)(255*Math.max(0,Math.min(1,a)))<<24)|(rgb&0xffffff);}
    static int hue(float h){
        float r=Math.max(0,Math.min(1,Math.abs(h*6%6-3)-1)),gg=Math.max(0,Math.min(1,2-Math.abs(h*6%6-2))),b=Math.max(0,Math.min(1,2-Math.abs(h*6%6-4)));
        return ((int)(255*(.45f+.55f*r))<<16)|((int)(255*(.45f+.55f*gg))<<8)|(int)(255*(.45f+.55f*b));
    }
    private static void draw(GuiGraphics g,Minecraft mc,int w,int h,Popup p,long age){
        var font=mc.font;float a=alpha(age,p.durationMs());
        boolean victory=p.style()==Style.VICTORY;
        int accent=switch(p.style()){case RAID->Ui.RED;case WAVE->Ui.CYAN;case DEFEAT->Ui.RED;default->Ui.BRASS;};
        int y=Math.max(20,h/4),band=victory?64:46;
        if(victory){
            // Colourful flash: a short white-gold pulse, then confetti behind rainbow lettering.
            float flash=Math.max(0f,1f-age/450f);if(flash>0)g.fill(0,0,w,h,argb(0xfff3b0,.55f*flash));
            for(int i=0;i<90;i++){
                int sx=(i*7919)%w,speed=40+(i*131)%90;float t=age/1000f;int py=(int)(((i*104729)%h)+t*speed)%h-4,px=sx+(int)(Math.sin(t*2+i)*8);
                g.fill(px,py,px+3,py+3,argb(hue((i*0.137f+t*0.3f)%1f),a));
            }
        }
        g.fill(0,y-band/2,w,y+band/2,argb(0x0a1218,.78f*a));
        if(p.style()==Style.RAID||p.style()==Style.DEFEAT){Ui.hazard(g,0,y-band/2-4,w,4,argb(0x7a0d12,a),argb(0xffd23f,a));Ui.hazard(g,0,y+band/2,w,4,argb(0x7a0d12,a),argb(0xffd23f,a));}
        else{g.fill(0,y-band/2-2,w,y-band/2,argb(accent,a));g.fill(0,y+band/2,w,y+band/2+2,argb(accent,a));}
        float scale=victory?3f:2.4f;
        scale=Math.min(scale,(w-24f)/Math.max(1,font.width(p.title())));
        g.pose().pushPose();g.pose().translate(w/2f,y-(victory?20:14),0);g.pose().scale(scale,scale,1);
        if(victory){
            String text=p.title().getString();int x=-font.width(text)/2;
            for(int i=0;i<text.length();i++){String c=String.valueOf(text.charAt(i));int color=argb(hue((i*0.09f+age/700f)%1f),a);g.drawString(font,c,x,(int)(Math.sin(age/160f+i)*2),color,true);x+=font.width(c);}
        }else g.drawString(font,p.title().getVisualOrderText(),-font.width(p.title())/2,0,argb(p.style()==Style.WAVE?Ui.CYAN:0xffedf5f8,a),true);
        g.pose().popPose();
        if(!p.sub().getString().isEmpty())g.drawCenteredString(font,p.sub(),w/2,y+(victory?14:10),argb(0xedf5f8,a*.9f));
    }
    private static void toast(GuiGraphics g,Minecraft mc,int w,int h,Popup p,long age,int slot){
        var font=mc.font;float a=alpha(age,p.durationMs());int width=Math.min(w-16,font.width(p.title())+34),x=(w-width)/2,y=Math.max(8,h/4+44)+slot*20+(current!=null?26:0);
        g.fill(x-1,y-1,x+width+1,y+17,argb(0x05090d,a));g.fill(x,y,x+width,y+16,argb(0x111e28,.92f*a));g.fill(x,y,x+3,y+16,argb(Ui.CYAN,a));
        Ui.beaconGlyph(g,x+9,y+2);
        g.drawString(font,Ui.fit(font,p.title(),width-30),x+24,y+4,argb(0xedf5f8,a),false);
    }
}
