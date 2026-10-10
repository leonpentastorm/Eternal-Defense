package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of falling ordnance: the whistle that rides a shell or bomb down for the last of its fall and ends as it lands (the 0.0.16
 * whistle, back in 0.0.19 after the 0.0.18 incoming sound; the 5.15-second flight stayed), and the shake of the view when one lands near
 * the player. The shake honours the game's "Screen Effect Scale" accessibility option (0 turns it off).
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class OrdnanceClient {
    private OrdnanceClient(){}
    /** Strongest shake (degrees) and the distance at which it fades out, for a shell and for a Bunker Buster. */
    static final float SHELL_SHAKE=1.4f,BOMB_SHAKE=4.5f;static final double SHELL_REACH=28,BOMB_REACH=64;
    static final int SHELL_SHAKE_TICKS=14,BOMB_SHAKE_TICKS=34;
    /** The whistle: its file's length (shell_whistle.ogg and bomb_whistle.ogg), its volume (heard 64 blocks away), and its pitch ranges. */
    static final double WHISTLE_SECONDS=1.55;static final float WHISTLE_VOLUME=4f;
    /**
     * The blast of a round is heard this many ticks after the client's copy of the round reaches its landing tick (the blast and the
     * round reach the client by different paths): measured in the 0.0.19 recording, where whistles timed on the landing tick ended
     * 0.03 to 0.10 s (0.07 s on average) before their blasts. The whistle is timed to the blast.
     */
    static final double BLAST_HEARD_AFTER_LANDING=1.4;
    static final float SHELL_PITCH=.92f,SHELL_PITCH_SPREAD=.16f,BOMB_PITCH=.9f,BOMB_PITCH_SPREAD=.1f;
    private static float strength;private static long startedAt=-1;private static int length;
    /** Rounds whose landing was already shaken (the client may see the last tick or not, so the removal also counts). */
    private static final java.util.Set<Integer> LANDED=new java.util.HashSet<>();

    static void tick(Ordnance o){
        o.trail();
        boolean round=o.style()==Ordnance.SHELL||o.style()==Ordnance.BOMB;
        if(!round)return;
        boolean bomb=o.style()==Ordnance.BOMB;float pitch=whistlePitch(bomb,o.getId());
        if(o.tickCount==whistleStart(o.flight(),pitch))Minecraft.getInstance().getSoundManager().play(new Whistle(o,pitch));
        if(o.tickCount>=o.flight()-1)landed(o);
    }
    /** Pure: the pitch of a round's whistle, varied from round to round (a pitch above 1 plays it faster, so shorter). */
    static float whistlePitch(boolean bomb,int id){
        float u=((id*0x9E3779B9)>>>8&0xffff)/65535f;
        return bomb?BOMB_PITCH+BOMB_PITCH_SPREAD*u:SHELL_PITCH+SHELL_PITCH_SPREAD*u;
    }
    /** Pure: the client tick at which the whistle starts so that, at {@code pitch}, it ends as the round's blast is heard (tick 1 for a short fall). */
    static int whistleStart(int flight,float pitch){return Math.max(1,(int)Math.round(flight+BLAST_HEARD_AFTER_LANDING-WHISTLE_SECONDS*20/pitch));}
    /** The round left the client: if that was its landing (a shell is removed the tick it lands), the landing still shakes the view. */
    static void removed(Ordnance o){
        boolean round=o.style()==Ordnance.SHELL||o.style()==Ordnance.BOMB;
        if(round&&o.tickCount>=o.flight()-4)landed(o);
        LANDED.remove(o.getId());
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
    /**
     * The whistle rides on the falling round (so it is heard where it is) and plays to its end, the blast; only a round that vanishes well
     * before its landing (out of sight, a chunk unloading) silences it.
     */
    static final class Whistle extends AbstractTickableSoundInstance {
        private final Ordnance o;
        Whistle(Ordnance o,float pitch){
            super(o.style()==Ordnance.BOMB?ArsenalSounds.BOMB_WHISTLE.get():ArsenalSounds.SHELL_WHISTLE.get(),SoundSource.BLOCKS,RandomSource.create());
            this.o=o;volume=WHISTLE_VOLUME;this.pitch=pitch;x=o.getX();y=o.getY();z=o.getZ();attenuation=SoundInstance.Attenuation.LINEAR;looping=false;
        }
        @Override public void tick(){
            if(o.isRemoved()){if(o.tickCount<o.flight()-5)stop();return;}
            x=o.getX();y=o.getY();z=o.getZ();
        }
        @Override public boolean canStartSilent(){return true;}
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
