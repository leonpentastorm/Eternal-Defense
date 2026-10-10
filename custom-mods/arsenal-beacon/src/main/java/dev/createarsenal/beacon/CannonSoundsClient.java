package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Client only: the Support Cannon's turning sound, looped at the turret for as long as it moves and faded out when it stops. */
final class CannonSoundsClient {
    private CannonSoundsClient(){}
    /** Volume of the turning loop (heard about 32 blocks away) and how fast it fades when the turret stops (ticks). */
    static final float TURN_VOLUME=2f;static final int FADE_TICKS=4;
    private static final java.util.Map<net.minecraft.core.BlockPos,Turning> PLAYING=new java.util.HashMap<>();

    static void tick(SupportCannon.CannonEntity cannon){
        var sound=PLAYING.get(cannon.getBlockPos());
        if(cannon.turning()&&(sound==null||sound.isStopped()||sound.fading)){
            if(sound!=null)Minecraft.getInstance().getSoundManager().stop(sound);
            var next=new Turning(cannon);PLAYING.put(cannon.getBlockPos(),next);Minecraft.getInstance().getSoundManager().play(next);cannon.grinds++;
        }
        PLAYING.values().removeIf(Turning::isStopped);
    }

    static final class Turning extends AbstractTickableSoundInstance {
        private final SupportCannon.CannonEntity cannon;boolean fading;private int fade=FADE_TICKS;
        Turning(SupportCannon.CannonEntity cannon){
            super(ArsenalSounds.CANNON_TURNING.get(),SoundSource.BLOCKS,SoundInstance.createUnseededRandom());
            this.cannon=cannon;looping=true;delay=0;volume=TURN_VOLUME;attenuation=SoundInstance.Attenuation.LINEAR;
            var p=cannon.getBlockPos();x=p.getX()+.5;y=p.getY()+1;z=p.getZ()+.5;
        }
        @Override public void tick(){
            if(cannon.isRemoved()||!cannon.turning())fading=true;
            if(fading){if(--fade<=0){stop();return;}volume=TURN_VOLUME*fade/FADE_TICKS;}
        }
        @Override public boolean canStartSilent(){return true;}
    }
}
