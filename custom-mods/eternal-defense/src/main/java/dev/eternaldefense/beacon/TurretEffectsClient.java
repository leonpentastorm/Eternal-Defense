package dev.eternaldefense.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/** Client only: spent casings thrown from the machine gun and shotgun turrets, and a flame turret's jet with one looping roar */
final class TurretEffectsClient {
    private TurretEffectsClient(){}
    /** Volume of the flame sounds (heard about 24 blocks away), the start clip's length and the roar's fade when it goes out (ticks) */
    static final float VOLUME=1.5f;static final int START_TICKS=20,FADE_TICKS=3;
    /** Flame particles a nozzle sends out each tick, and the twin's nozzles and gun barrels either side of its middle (blocks) */
    static final int FLAMES=3;static final double TWIN=.35;
    private static final Map<BlockPos,Burn> BURNING=new HashMap<>();

    /** Pure: where a turret drawn at this yaw (radians) and pitch (degrees, up positive) points */
    static Vec3 facing(float yaw,float pitch){double p=Math.toRadians(pitch);return new Vec3(-Math.sin(yaw)*Math.cos(p),Math.sin(p),-Math.cos(yaw)*Math.cos(p));}
    /** Pure: the gun's right-hand side at this yaw */
    static Vec3 right(float yaw){return new Vec3(Math.cos(yaw),0,-Math.sin(yaw));}
    /** Pure: the guns' sideways offsets: one gun in the middle, a twin's two either side */
    static double[] guns(int mk){return mk>=3?new double[]{-TWIN,TWIN}:new double[]{0};}

    /** A spent case thrown out of the side of each gun that fired (a red shell from a shotgun) */
    static void casing(Turrets.TurretEntity be){
        if(!(be.getLevel() instanceof ClientLevel level)||!(be.getBlockState().getBlock() instanceof Turrets.TurretBlock block)||block.line==Turrets.Line.FLAME)return;
        var dir=facing(be.drawYaw,be.drawPitch);var side=right(be.drawYaw);var r=level.random;
        var breech=Vec3.atLowerCornerOf(be.getBlockPos()).add(Turrets.middle(block.mk)).subtract(dir.scale(.1));
        boolean shell=block.line==Turrets.Line.SHOTGUN;
        for(double gun:guns(block.mk)){
            var out=side.scale(gun<0?-1:1);var at=breech.add(side.scale(gun)).add(out.scale(.18)).add(0,.05,0);
            var v=out.scale(.11+r.nextDouble()*.06).add(0,.13+r.nextDouble()*.06,0).subtract(dir.scale(.03));
            Minecraft.getInstance().particleEngine.add(new Casing(level,at,v,shell));
        }
    }

    /** Each tick of a flame turret: it lights with the start sound, roars on in a loop from the second second, and goes out with the end sound */
    static void flame(Turrets.TurretEntity be){
        if(!(be.getLevel() instanceof ClientLevel level)||!(be.getBlockState().getBlock() instanceof Turrets.TurretBlock block))return;
        var pos=be.getBlockPos();var burn=BURNING.get(pos);long now=level.getGameTime();
        if(be.flaming&&be.powered){
            if(burn==null){burn=new Burn(be,now);BURNING.put(pos.immutable(),burn);level.playLocalSound(pos,DefenseSounds.FLAME_START.get(),SoundSource.BLOCKS,VOLUME,1f,false);}
            if(now-burn.litAt>=START_TICKS&&(burn.roar==null||burn.roar.isStopped())){burn.roar=new Roar(be);Minecraft.getInstance().getSoundManager().play(burn.roar);}
            jet(level,be,block);
        }else if(burn!=null){
            BURNING.remove(pos);if(burn.roar!=null)burn.roar.out();
            level.playLocalSound(pos,DefenseSounds.FLAME_END.get(),SoundSource.BLOCKS,VOLUME,1f,false);
        }
        BURNING.values().removeIf(b->b.be.isRemoved());
    }
    /** A turret that is burning: when it lit, and its roar once that plays */
    static final class Burn {final Turrets.TurretEntity be;final long litAt;Roar roar;Burn(Turrets.TurretEntity be,long litAt){this.be=be;this.litAt=litAt;}}
    /** Flames sent along the drawn aim, fast enough to reach about the jet's length before they burn out */
    static void jet(ClientLevel level,Turrets.TurretEntity be,Turrets.TurretBlock block){
        var s=Turrets.stats(block.line,block.mk);var dir=facing(be.drawYaw,be.drawPitch);var side=right(be.drawYaw);var r=level.random;
        var nozzle=Vec3.atLowerCornerOf(be.getBlockPos()).add(Turrets.middle(block.mk)).add(dir.scale(block.mk>=3?.9:.6));
        double speed=s.range()/16.0,spread=s.spread()/s.range()*.5;
        for(double gun:guns(block.mk)){
            var from=nozzle.add(side.scale(gun));
            for(int i=0;i<FLAMES;i++){
                var v=dir.add(r.nextGaussian()*spread,r.nextGaussian()*spread*.6,r.nextGaussian()*spread).normalize().scale(speed*(.8+r.nextDouble()*.4));
                level.addParticle(i==0?ParticleTypes.SMALL_FLAME:ParticleTypes.FLAME,from.x,from.y,from.z,v.x,v.y,v.z);
            }
            if(r.nextInt(3)==0){var v=dir.scale(speed*.5);level.addParticle(ParticleTypes.SMOKE,from.x,from.y+.05,from.z,v.x,v.y+.02,v.z);}
        }
    }

    /** The cartridge case picture (its item model draws from a shared sheet, so the plain texture is used) */
    static final net.minecraft.resources.ResourceLocation CASE=new net.minecraft.resources.ResourceLocation(EternalDefense.ID,"item/cartridge_case");
    /** A spent case: the cartridge case picture, small, spinning as it flies and lying a moment where it lands */
    static final class Casing extends TextureSheetParticle {
        Casing(ClientLevel level,Vec3 at,Vec3 v,boolean shell){
            super(level,at.x,at.y,at.z);
            setSprite(Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(CASE));
            xd=v.x;yd=v.y;zd=v.z;gravity=1;friction=.98f;quadSize=shell?.075f:.06f;lifetime=40+random.nextInt(20);hasPhysics=true;
            roll=random.nextFloat()*(float)Math.PI*2;oRoll=roll;
            if(shell)setColor(1f,.42f,.36f);
        }
        @Override public ParticleRenderType getRenderType(){return ParticleRenderType.TERRAIN_SHEET;}
        @Override public void tick(){oRoll=roll;super.tick();if(!onGround)roll+=.7f;}
    }

    /** The roar: looped at the turret while it burns, faded out under the end sound */
    static final class Roar extends AbstractTickableSoundInstance {
        private final Turrets.TurretEntity be;private boolean fading;private int fade=FADE_TICKS;
        Roar(Turrets.TurretEntity be){
            super(DefenseSounds.FLAME_TURRET.get(),SoundSource.BLOCKS,SoundInstance.createUnseededRandom());
            this.be=be;looping=true;delay=0;volume=VOLUME;attenuation=SoundInstance.Attenuation.LINEAR;
            var p=be.getBlockPos();x=p.getX()+.5;y=p.getY()+1.2;z=p.getZ()+.5;
        }
        void out(){fading=true;}
        @Override public void tick(){
            if(be.isRemoved())fading=true;
            if(fading){if(--fade<=0){stop();return;}volume=VOLUME*fade/FADE_TICKS;}
        }
        @Override public boolean canStartSilent(){return true;}
    }
}
