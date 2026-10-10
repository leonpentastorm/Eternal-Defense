package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of falling ordnance: the incoming-shell sound, played where the round will land so its scream ends with the impact (the
 * 0.0.18 sound, back in 0.0.20: the 0.0.19 whistle rode on the round and was lost whenever the round left the client's view), and the
 * shake of the view when one lands near the player. The shake honours the game's "Screen Effect Scale" accessibility option (0 turns it off).
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class OrdnanceClient {
    private OrdnanceClient(){}
    /** Strongest shake (degrees) and the distance at which it fades out, for a shell and for a Bunker Buster. */
    static final float SHELL_SHAKE=1.4f,BOMB_SHAKE=4.5f;static final double SHELL_REACH=28,BOMB_REACH=64;
    static final int SHELL_SHAKE_TICKS=14,BOMB_SHAKE_TICKS=34;
    /**
     * The incoming sound (the owner's 5.15-second scream, back in 0.0.20 after the 0.0.19 whistle): it is played once, standing still where the
     * round will land, the moment this client first sees the round, so it is heard the same whichever way the player looks and wherever the
     * round is. Heard up to 64 blocks from the impact (volume 4). Always at pitch 1: another pitch would move its end off the impact.
     */
    static final float INCOMING_VOLUME=4f;
    /** A round first seen later than this (it came into range in mid-fall) stays silent: its scream would end after the impact. */
    static final int INCOMING_LATEST_TICK=10;
    private static float strength;private static long startedAt=-1;private static int length;
    /** Rounds whose landing was already shaken (the client may see the last tick or not, so the removal also counts). */
    private static final java.util.Set<Integer> LANDED=new java.util.HashSet<>();
    /** Rounds whose incoming sound this client already started. */
    private static final java.util.Set<Integer> HEARD=new java.util.HashSet<>();

    static void tick(Ordnance o){
        o.trail();
        boolean round=o.style()==Ordnance.SHELL||o.style()==Ordnance.BOMB;
        if(!round)return;
        if(incomingDue(o.tickCount)&&HEARD.add(o.getId())){
            var at=o.target();
            if(at!=null)Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ArsenalSounds.INCOMING.get(),SoundSource.BLOCKS,INCOMING_VOLUME,1f,RandomSource.create(),at.x,at.y+1,at.z));
        }
        if(o.tickCount>=o.flight()-1)landed(o);
    }
    /** Pure: this client plays the incoming sound for a round it sees at {@code tick} (the first tick it sees, at most {@link #INCOMING_LATEST_TICK}). */
    static boolean incomingDue(int tick){return tick>=1&&tick<=INCOMING_LATEST_TICK;}
    /** The round left the client: if that was its landing (a shell is removed the tick it lands), the landing still shakes the view. */
    static void removed(Ordnance o){
        boolean round=o.style()==Ordnance.SHELL||o.style()==Ordnance.BOMB;
        if(round&&o.tickCount>=o.flight()-4)landed(o);
        LANDED.remove(o.getId());HEARD.remove(o.getId());
    }
    /** Pure: how hard the view shakes at {@code distance} blocks from a landing (0 beyond the reach). */
    static float shakeAt(float strongest,double reach,double distance){return distance>=reach?0f:(float)(strongest*(1-distance/reach)*(1-distance/reach));}
    private static void landed(Ordnance o){
        if(!LANDED.add(o.getId()))return;
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null)return;
        boolean bomb=o.style()==Ordnance.BOMB;
        var at=o.target()!=null?o.target():o.position();
        float s=shakeAt(bomb?BOMB_SHAKE:SHELL_SHAKE,bomb?BOMB_REACH:SHELL_REACH,Math.sqrt(p.distanceToSqr(at.x,at.y,at.z)))*mc.options.screenEffectScale().get().floatValue();
        if(s<=0.05f)return;
        long now=mc.level.getGameTime();
        if(startedAt>=0&&now-startedAt<length&&current(now,0)>s)return;   // a stronger shake is still running
        strength=s;startedAt=now;length=bomb?BOMB_SHAKE_TICKS:SHELL_SHAKE_TICKS;
    }
    private static float current(long now,float partial){
        if(startedAt<0)return 0;float t=(now-startedAt+partial)/length;if(t>=1){startedAt=-1;return 0;}
        return strength*(1-t)*(1-t);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();if(startedAt<0||mc.level==null)return;
        float partial=(float)e.getPartialTick();float a=current(mc.level.getGameTime(),partial);if(a<=0)return;
        float time=(mc.level.getGameTime()+partial)*.9f;
        e.setYaw(e.getYaw()+a*(Mth.sin(time*2.3f)*.6f+Mth.sin(time*5.1f)*.4f));
        e.setPitch(e.getPitch()+a*(Mth.sin(time*3.7f+1f)*.6f+Mth.sin(time*6.3f)*.4f));
        e.setRoll(e.getRoll()+a*.5f*Mth.sin(time*4.4f+2f));
    }
}
