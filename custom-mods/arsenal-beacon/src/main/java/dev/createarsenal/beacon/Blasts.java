package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * What a fire support looks and sounds like when it lands (server side; vanilla particle and sound packets only). The big ones are sent to
 * players far beyond the usual 32 blocks, so a barrage on the far side of the base is still seen and heard.
 */
final class Blasts {
    private Blasts(){}
    /** Players this close see the flash, fireballs and smoke column of a big blast (the vanilla limit is 32 blocks). */
    static final double FAR=160;
    /** Volumes: a sound is heard up to 16 blocks per unit of volume. */
    static final float CANNON_VOLUME=8f,FLARE_VOLUME=6f,SHELL_VOLUME=8f,BUNKER_VOLUME=12f,FIRE_VOLUME=5f;

    /** Sends a particle burst to every player within {@code range}, past the vanilla distance limit. */
    static void far(ServerLevel l,ParticleOptions particle,double x,double y,double z,int count,double dx,double dy,double dz,double speed,double range){
        for(var p:l.players())if(p.distanceToSqr(x,y,z)<=range*range)l.sendParticles(p,particle,true,x,y,z,count,dx,dy,dz,speed);
    }
    static void sound(ServerLevel l,Vec3 at,SoundEvent event,float volume,float pitch){l.playSound(null,at.x,at.y,at.z,event,SoundSource.BLOCKS,volume,pitch);}
    private static float vary(ServerLevel l,float base){return base*(.94f+.12f*l.random.nextFloat());}

    /** A ring of puffs running outward along the ground: the pressure wave. */
    static void ring(ServerLevel l,Vec3 c,ParticleOptions particle,int points,double speed,double y){
        for(int i=0;i<points;i++){double a=i*Math.PI*2/points;far(l,particle,c.x+Math.cos(a)*.6,c.y+y,c.z+Math.sin(a)*.6,0,Math.cos(a),0,Math.sin(a),speed,64);}
    }
    /** Pieces of the ground thrown up (the block under the blast). */
    static void debris(ServerLevel l,Vec3 c,int count,double spread){
        var ground=l.getBlockState(BlockPos.containing(c.x,c.y-.5,c.z));
        if(ground.isAir())ground=net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        far(l,new BlockParticleOption(ParticleTypes.BLOCK,ground),c.x,c.y+.4,c.z,count,spread,.3,spread,.45,64);
    }

    /** A shell of the Explosion Barrage lands: flash, fireballs, a ring of dust, earth thrown up, a column of smoke, and a blast that echoes. */
    static void shell(ServerLevel l,Vec3 c,double r){
        far(l,ParticleTypes.FLASH,c.x,c.y+1,c.z,1,0,0,0,0,FAR);
        far(l,ParticleTypes.EXPLOSION_EMITTER,c.x,c.y+.6,c.z,3,r*.25,.4,r*.25,0,FAR);
        far(l,ParticleTypes.EXPLOSION,c.x,c.y+.5,c.z,14,r*.45,.6,r*.45,0,96);
        far(l,ParticleTypes.FLAME,c.x,c.y+.4,c.z,60,r*.4,.5,r*.4,.12,64);
        far(l,ParticleTypes.LAVA,c.x,c.y+.4,c.z,14,r*.3,.2,r*.3,0,64);
        far(l,ParticleTypes.LARGE_SMOKE,c.x,c.y+1,c.z,40,r*.35,.8,r*.35,.06,FAR);
        far(l,ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,c.x,c.y+.5,c.z,8,r*.2,.2,r*.2,.02,FAR);
        ring(l,c,ParticleTypes.CLOUD,24,.7,.3);
        debris(l,c,40,r*.35);
        sound(l,c,ArsenalSounds.SHELL_EXPLOSION.get(),SHELL_VOLUME,vary(l,1f));
    }
    /** The Bunker Buster hits: everything of a shell, much bigger, twice the pressure wave and a fountain of earth. */
    static void bunker(ServerLevel l,Vec3 c,double r){
        far(l,ParticleTypes.FLASH,c.x,c.y+1.5,c.z,3,r*.2,.5,r*.2,0,FAR);
        far(l,ParticleTypes.EXPLOSION_EMITTER,c.x,c.y+.6,c.z,9,r*.35,r*.25,r*.35,0,FAR);
        far(l,ParticleTypes.EXPLOSION,c.x,c.y+.5,c.z,40,r*.5,r*.3,r*.5,0,FAR);
        far(l,ParticleTypes.FLAME,c.x,c.y+.5,c.z,160,r*.5,r*.25,r*.5,.18,96);
        far(l,ParticleTypes.LAVA,c.x,c.y+.5,c.z,50,r*.4,.3,r*.4,0,96);
        far(l,ParticleTypes.LARGE_SMOKE,c.x,c.y+1.5,c.z,140,r*.45,r*.3,r*.45,.09,FAR);
        far(l,ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,c.x,c.y+.5,c.z,30,r*.3,.3,r*.3,.03,FAR);
        ring(l,c,ParticleTypes.CLOUD,40,1.3,.3);ring(l,c,ParticleTypes.POOF,32,.9,1.2);
        debris(l,c,160,r*.45);
        sound(l,c,ArsenalSounds.BUNKER_IMPACT.get(),BUNKER_VOLUME,vary(l,1f));
    }
    /** The bomb bores another layer down. */
    static void dig(ServerLevel l,Vec3 at,double r){
        far(l,ParticleTypes.EXPLOSION,at.x,at.y,at.z,2,r*.4,0,r*.4,0,96);
        far(l,ParticleTypes.LARGE_SMOKE,at.x,at.y+1,at.z,10,r*.3,.5,r*.3,.05,96);
        sound(l,at,ArsenalSounds.BUNKER_DIG.get(),4f,vary(l,.9f));
    }
    /** The bomb's last blast at the bottom of its crater. */
    static void deep(ServerLevel l,Vec3 at,double r){
        far(l,ParticleTypes.EXPLOSION_EMITTER,at.x,at.y+.5,at.z,2,r*.2,.2,r*.2,0,FAR);
        far(l,ParticleTypes.LARGE_SMOKE,at.x,at.y+1,at.z,60,r*.3,r*.4,r*.3,.12,FAR);
        far(l,ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,at.x,at.y+1,at.z,16,r*.2,.3,r*.2,.03,FAR);
        sound(l,at,ArsenalSounds.BUNKER_DIG.get(),8f,.6f);
    }
    /** The cannon fires: a muzzle flash and smoke at the barrel and a roar heard across the base. */
    static void muzzle(ServerLevel l,double x,double y,double z){
        far(l,ParticleTypes.FLASH,x,y,z,1,0,0,0,0,FAR);
        far(l,ParticleTypes.FLAME,x,y,z,24,.2,.2,.2,.1,96);
        far(l,ParticleTypes.LARGE_SMOKE,x,y,z,22,.3,.3,.3,.06,FAR);
        far(l,ParticleTypes.CAMPFIRE_COSY_SMOKE,x,y,z,6,.2,.2,.2,.02,FAR);
        far(l,ParticleTypes.EXPLOSION,x,y,z,1,0,0,0,0,FAR);
        sound(l,new Vec3(x,y,z),ArsenalSounds.CANNON_FIRE.get(),CANNON_VOLUME,vary(l,1f));
    }
    /** A flare lands: a bright burst, sparks and a rising plume of signal smoke, with a sound that turns heads across the chunk. */
    static void flareLanded(ServerLevel l,Vec3 at,float pitch){
        far(l,ParticleTypes.FLASH,at.x,at.y+.3,at.z,1,0,0,0,0,96);
        far(l,ParticleTypes.FIREWORK,at.x,at.y+.3,at.z,30,.2,.3,.2,.18,96);
        far(l,ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,at.x,at.y+.2,at.z,4,.1,.1,.1,.01,FAR);
        l.playSound(null,at.x,at.y,at.z,ArsenalSounds.FLARE_SIGNAL.get(),SoundSource.PLAYERS,FLARE_VOLUME,pitch);
    }
}
