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
 * Client side of falling ordnance: the whistle that follows a shell or bomb down, and the shake of the view when one lands near the player.
 * The shake honours the game's "Screen Effect Scale" accessibility option (0 turns it off).
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class OrdnanceClient {
    private OrdnanceClient(){}
    /** Strongest shake (degrees) and the distance at which it fades out, for a shell and for a Bunker Buster. */
    static final float SHELL_SHAKE=1.4f,BOMB_SHAKE=4.5f;static final double SHELL_REACH=28,BOMB_REACH=64;
    static final int SHELL_SHAKE_TICKS=14,BOMB_SHAKE_TICKS=34;
    private static float strength;private static long startedAt=-1;private static int length;

    static void tick(Ordnance o){
        o.trail();
        if(o.tickCount==1&&o.style()!=Ordnance.STAR)Minecraft.getInstance().getSoundManager().play(new Whistle(o));
        if(o.tickCount==o.flight()&&o.style()!=Ordnance.STAR)landed(o);
    }
    /** Pure: how hard the view shakes at {@code distance} blocks from a landing (0 beyond the reach). */
    static float shakeAt(float strongest,double reach,double distance){return distance>=reach?0f:(float)(strongest*(1-distance/reach)*(1-distance/reach));}
    private static void landed(Ordnance o){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null)return;
        boolean bomb=o.style()==Ordnance.BOMB;
        float s=shakeAt(bomb?BOMB_SHAKE:SHELL_SHAKE,bomb?BOMB_REACH:SHELL_REACH,Math.sqrt(p.distanceToSqr(o.getX(),o.getY(),o.getZ())))*mc.options.screenEffectScale().get().floatValue();
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

    /** The incoming whistle: it rides on the falling ordnance (so it is heard where it is) and stops when it lands. */
    static final class Whistle extends AbstractTickableSoundInstance {
        private final Ordnance o;
        Whistle(Ordnance o){
            super(o.style()==Ordnance.BOMB?ArsenalSounds.BOMB_WHISTLE.get():ArsenalSounds.SHELL_WHISTLE.get(),SoundSource.BLOCKS,RandomSource.create());
            this.o=o;volume=4f;pitch=o.style()==Ordnance.BOMB?.9f+.1f*random.nextFloat():.92f+.16f*random.nextFloat();
            x=o.getX();y=o.getY();z=o.getZ();attenuation=SoundInstance.Attenuation.LINEAR;looping=false;
        }
        @Override public void tick(){
            if(o.isRemoved()||o.landed()){stop();return;}
            x=o.getX();y=o.getY();z=o.getZ();
        }
        @Override public boolean canStartSilent(){return true;}
    }
}
