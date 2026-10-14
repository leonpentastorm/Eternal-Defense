package dev.eternaldefense.beacon;

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

/** A shell, a bomb or a starshell falling onto a fire support flare. */
final class Ordnance extends Entity {
    static final int SHELL=0,BOMB=1,STAR=2,WELL=3;
    /** Height above the flare a shell or bomb is first seen at (blocks); it falls faster and faster from there. */
    static final double DROP_HEIGHT=80;
    static final EntityDataAccessor<Integer> STYLE=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> TYPE=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.INT);
    /** Ticks until it lands. */
    static final EntityDataAccessor<Integer> FLIGHT=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.INT);
    /** Ticks it keeps going after it lands */
    static final EntityDataAccessor<Integer> AFTER=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> AFTER_DEPTH=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.FLOAT);
    /** Height of the fall, so the client can work out the speed itself. */
    static final EntityDataAccessor<Float> HEIGHT=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.FLOAT);
    /** Size of a gravity well's vortex: the half-width of its area (blocks). */
    static final EntityDataAccessor<Float> SIZE=SynchedEntityData.defineId(Ordnance.class,EntityDataSerializers.FLOAT);

    Ordnance(EntityType<? extends Ordnance> type,Level level){super(type,level);noPhysics=true;}

    /** The pure arithmetic of the fall, apart so it can be checked without the entity's synced data. */
    static final class Fall {
        private Fall(){}
        /** Pure: how far down (blocks) it is after age ticks: the fall, then the slower travel after landing. */
        static double drop(double height,int flight,int after,double afterDepth,double age){return drop(height,flight,after,afterDepth,age,false);}
        /** Pure: the same; with falling the round speeds up like something dropped (a shell or bomb) */
        static double drop(double height,int flight,int after,double afterDepth,double age,boolean falling){
            if(age<=flight){double k=Math.max(0,age)/Math.max(1,flight);return height*(falling?k*k:k);}
            return height+afterDepth*Math.min(1,(age-flight)/Math.max(1,after));
        }
    }
    /** Server: drops one onto target; it lands flight ticks from now. */
    static Ordnance drop(ServerLevel level,Vec3 target,int style,CannonUpgrades.FireType type,int flight,double height,int after,double afterDepth){
        var o=EternalDefense.ORDNANCE.get().create(level);if(o==null)return null;
        o.entityData.set(STYLE,style);o.entityData.set(TYPE,type.ordinal());o.entityData.set(FLIGHT,flight);o.entityData.set(HEIGHT,(float)height);
        o.entityData.set(AFTER,after);o.entityData.set(AFTER_DEPTH,(float)afterDepth);
        o.setPos(target.x,target.y+height,target.z);o.target=target;level.addFreshEntity(o);return o;
    }
    private Vec3 target;
    int style(){return entityData.get(STYLE);}
    CannonUpgrades.FireType type(){return CannonUpgrades.FireType.of(entityData.get(TYPE));}
    int flight(){return entityData.get(FLIGHT);}
    int after(){return entityData.get(AFTER);}
    float size(){return entityData.get(SIZE);}
    /** Server: a gravity well's vortex on the ground at at, radius blocks each way, for ticks. */
    static Ordnance well(ServerLevel level,Vec3 at,CannonUpgrades.FireType type,int ticks,double radius){
        var o=drop(level,at,WELL,type,ticks,0,0,0);if(o!=null)o.entityData.set(SIZE,(float)radius);return o;
    }
    /** Where it lands (the client works it out from where it appeared). */
    Vec3 target(){return target;}
    boolean landed(){return tickCount>=flight();}

    @Override protected void defineSynchedData(){entityData.define(STYLE,SHELL);entityData.define(TYPE,0);entityData.define(FLIGHT,30);entityData.define(AFTER,0);entityData.define(AFTER_DEPTH,0f);entityData.define(HEIGHT,(float)DROP_HEIGHT);entityData.define(SIZE,0f);}
    @Override public void tick(){
        if(target==null)target=position().subtract(0,entityData.get(HEIGHT),0);   // The client sees it first at its starting point
        super.tick();
        double down=Fall.drop(entityData.get(HEIGHT),flight(),after(),entityData.get(AFTER_DEPTH),tickCount,style()==SHELL||style()==BOMB);
        setPos(target.x,target.y+entityData.get(HEIGHT)-down,target.z);
        if(level().isClientSide){net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->OrdnanceClient.tick(this));return;}
        if(tickCount>=flight()+after())discard();
    }
    @Override public void onClientRemoval(){net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->OrdnanceClient.removed(this));}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<256*256;}
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(Math.max(3,size()+1));}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}

    /** Smoke and sparks that trail behind it (client only). */
    void trail(){
        var l=level();double x=getX(),y=getY()+(style()==BOMB?2.4:1.0),z=getZ();
        if(style()==WELL)return;
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
