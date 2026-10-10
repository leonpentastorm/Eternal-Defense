package dev.createarsenal.beacon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * A shell, a bomb or a starshell falling onto a fire support flare. It is only there to be seen and heard: it falls from high above the flare,
 * whistles on the way down and lands exactly when the flare's own clock resolves the shot (all damage and effects stay with the flare). The
 * cannon at the base fires up into the sky; nothing flies from it to the target, so there is no long projectile flight to simulate, and a
 * flare below ground (Quantum tunneling) is reached the same way: the ordnance passes through the rock.
 * <p>Styles: {@link #SHELL} (artillery shell, banded in the fire support's colour), {@link #BOMB} (the Bunker Buster, which keeps boring down
 * through its crater after it lands), {@link #STAR} (a starshell's burning light that sinks slowly after it bursts high over the flare).
 * Never saved, cannot be hit or pushed; the client predicts the fall from the synced numbers.
 */
final class Ordnance extends Entity {
    static final int SHELL=0,BOMB=1,STAR=2;
    /** Height above the flare a shell or bomb is first seen at (blocks). */
    static final double DROP_HEIGHT=48;
    static final EntityDataAccessor<Integer> STYLE=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> TYPE=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.INT);
    /** Ticks until it lands. */
    static final EntityDataAccessor<Integer> FLIGHT=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.INT);
    /** Ticks it keeps going after it lands (a bomb boring through its crater, a star sinking) and how far it travels in that time. */
    static final EntityDataAccessor<Integer> AFTER=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> AFTER_DEPTH=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.FLOAT);
    /** Height of the fall, so the client can work out the speed itself. */
    static final EntityDataAccessor<Float> HEIGHT=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.FLOAT);

    Ordnance(EntityType<? extends Ordnance> type,Level level){super(type,level);noPhysics=true;}

    /** The pure arithmetic of the fall, apart so it can be checked without the entity's synced data. */
    static final class Fall {
        private Fall(){}
        /** Pure: how far down (blocks) it is after {@code age} ticks: the fall, then the slower travel after landing. */
        static double drop(double height,int flight,int after,double afterDepth,double age){
            if(age<=flight)return height*Math.max(0,age)/Math.max(1,flight);
            return height+afterDepth*Math.min(1,(age-flight)/Math.max(1,after));
        }
    }
    /** Server: drops one onto {@code target}; it lands {@code flight} ticks from now. */
    static Ordnance drop(ServerLevel level,Vec3 target,int style,CannonUpgrades.FireType type,int flight,double height,int after,double afterDepth){
        var o=ArsenalBeacon.ORDNANCE.get().create(level);if(o==null)return null;
        o.entityData.set(STYLE,style);o.entityData.set(TYPE,type.ordinal());o.entityData.set(FLIGHT,flight);o.entityData.set(HEIGHT,(float)height);
        o.entityData.set(AFTER,after);o.entityData.set(AFTER_DEPTH,(float)afterDepth);
        o.setPos(target.x,target.y+height,target.z);o.target=target;level.addFreshEntity(o);return o;
    }
    private Vec3 target;
    int style(){return entityData.get(STYLE);}
    CannonUpgrades.FireType type(){return CannonUpgrades.FireType.of(entityData.get(TYPE));}
    int flight(){return entityData.get(FLIGHT);}
    int after(){return entityData.get(AFTER);}
    /** Where it lands (the client works it out from where it appeared). */
    Vec3 target(){return target;}
    boolean landed(){return tickCount>=flight();}

    @Override protected void defineSynchedData(){entityData.define(STYLE,SHELL);entityData.define(TYPE,0);entityData.define(FLIGHT,30);entityData.define(AFTER,0);entityData.define(AFTER_DEPTH,0f);entityData.define(HEIGHT,(float)DROP_HEIGHT);}
    @Override public void tick(){
        if(target==null)target=position().subtract(0,entityData.get(HEIGHT),0);   // the client sees it first at its starting point
        super.tick();
        double down=Fall.drop(entityData.get(HEIGHT),flight(),after(),entityData.get(AFTER_DEPTH),tickCount);
        setPos(target.x,target.y+entityData.get(HEIGHT)-down,target.z);
        if(level().isClientSide){net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->OrdnanceClient.tick(this));return;}
        if(tickCount>=flight()+after())discard();
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<256*256;}
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(3);}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}

    /** Smoke and sparks that trail behind it (client only). */
    void trail(){
        var l=level();double x=getX(),y=getY()+(style()==BOMB?2.4:1.0),z=getZ();
        if(style()==STAR){
            if(tickCount%2==0)l.addParticle(ParticleTypes.END_ROD,x,getY()+.2,z,(random.nextDouble()-.5)*.05,.02,(random.nextDouble()-.5)*.05);
            if(tickCount%3==0)l.addParticle(ParticleTypes.FIREWORK,x,getY(),z,(random.nextDouble()-.5)*.08,-.05,(random.nextDouble()-.5)*.08);
            return;
        }
        if(landed())return;
        l.addParticle(ParticleTypes.SMOKE,x,y,z,0,.05,0);
        if(style()==BOMB){l.addParticle(ParticleTypes.LARGE_SMOKE,x,y+.3,z,0,.04,0);if(tickCount%2==0)l.addParticle(ParticleTypes.FLAME,x,y,z,0,.08,0);}
    }
}
