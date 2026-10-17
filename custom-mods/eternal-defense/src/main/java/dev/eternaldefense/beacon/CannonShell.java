package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** A Heavy Cannon shell: fired flat when the way is clear, lobbed like a mortar over cover, and bursting where it lands */
final class CannonShell extends Entity implements IEntityAdditionalSpawnData {
    /** Gravity a tick, the flat shot's and the lob's speeds (blocks a tick), and the longest flight (ticks) */
    static final double G=.06,FLAT_SPEED=2.5,LOB_SPEED=1.6,FAR_LOB_SPEED=2.1;
    static final int LIFE=200;
    /** The gun lifts from -5 to 90 degrees; the barrel's length past the trunnions and the trunnions' place behind the turning axis (blocks) */
    static final double MIN_PITCH=-5,BARREL=33.75/16,TRUNNION_HEIGHT=1.25,TRUNNION_BACK=2/16.0;
    /** A blast does its full damage at its middle and this share less at its edge */
    static final float BLAST_FALLOFF=.6f;
    /** A shell that stops on a block this close to where it was aimed still counts as a way through (blocks) */
    static final double CLOSE=2.5;

    Vec3 start=Vec3.ZERO,velocity=Vec3.ZERO;float damage,radius;BlockPos root=BlockPos.ZERO;
    CannonShell(EntityType<? extends CannonShell> type,Level level){super(type,level);noPhysics=true;}

    /** The pure arithmetic of a shot, apart so it can be checked without the entity */
    static final class Arc {
        private Arc(){}
        /** A planned shot: where it leaves the barrel, its first tick's move, a lob or not, and the gun's turn and lift (radians) */
        record Shot(Vec3 from,Vec3 velocity,boolean high,float yaw,double pitch,double flight){
            /** Pure: where the shell is after t ticks (it moves, then falls a little faster) */
            Vec3 at(double t){return Arc.at(from,velocity,t);}
        }
        static Vec3 at(Vec3 from,Vec3 v,double t){return from.add(v.x*t,v.y*t-G*t*(t-1)/2,v.z*t);}
        /** Pure: which way it flies after t ticks */
        static Vec3 heading(Vec3 v,double t){return new Vec3(v.x,v.y-G*(t-.5),v.z);}
        /** Pure: the pitch (radians) that sends a shell at speed v over flat distance d to height dy under gravity g, the low or the high arc; null out of reach */
        static @Nullable Double launch(double d,double dy,double v,double g,boolean high){
            double v2=v*v,root=v2*v2-g*(g*d*d+2*dy*v2);
            if(root<0||d<=0)return null;
            double s=Math.sqrt(root);return Math.atan2(v2+(high?s:-s),g*d);
        }
        /** Pure: the muzzle of a cannon anchored at root, turned to yaw and lifted to pitch (radians) */
        static Vec3 muzzle(BlockPos root,float yaw,double pitch){
            double z=TRUNNION_BACK-BARREL*Math.cos(pitch),y=TRUNNION_HEIGHT+BARREL*Math.sin(pitch);
            return Vec3.atLowerCornerOf(root).add(1+z*Math.sin(yaw),y,1+z*Math.cos(yaw));
        }
        /** Pure: the shot at speed v from a cannon at root toward aim, leading a mob drifting by drift a tick; null when no arc reaches */
        static @Nullable Shot solve(BlockPos root,Vec3 aim,Vec3 drift,double v,boolean high){
            var target=aim;var from=Vec3.atLowerCornerOf(root).add(1,TRUNNION_HEIGHT,1);Shot shot=null;
            for(int i=0;i<4;i++){
                double dx=target.x-from.x,dz=target.z-from.z,d=Math.sqrt(dx*dx+dz*dz);
                if(d<.5)return null;
                var pitch=launch(d,target.y-from.y,v,G,high);
                if(pitch==null||Math.toDegrees(pitch)<MIN_PITCH)return null;
                float yaw=Turrets.yaw(dx,dz);var muzzle=muzzle(root,yaw,pitch);
                dx=target.x-muzzle.x;dz=target.z-muzzle.z;d=Math.sqrt(dx*dx+dz*dz);
                if(d<.5)return null;
                pitch=launch(d,target.y-muzzle.y,v,G,high);
                if(pitch==null||Math.toDegrees(pitch)<MIN_PITCH)return null;
                double h=v*Math.cos(pitch),flight=d/h;
                // Its first tick has no fall, so it rises G/2 less to stay on the smooth arc at every tick
                shot=new Shot(muzzle,new Vec3(dx/d*h,v*Math.sin(pitch)-G/2,dz/d*h),high,yaw,pitch,flight);
                from=muzzle;target=aim.add(drift.x*flight,0,drift.z*flight);
            }
            return shot;
        }
        /** Pure: what a blast does a mob this far from its middle */
        static float blastDamage(float centre,double distance,float radius){return distance>radius?0:centre*(1-BLAST_FALLOFF*(float)Mth.clamp(distance/radius,0,1));}
    }
    /** The shot a cannon at root takes at a mob: flat if its path is clear, else a lob, else none */
    static @Nullable Arc.Shot plan(Level l,BlockPos root,Mob target){
        var aim=target.position().add(0,target.getBbHeight()*.4,0);var drift=target.getDeltaMovement().multiply(1,0,1);
        for(int i=0;i<3;i++){
            var shot=Arc.solve(root,aim,drift,i==0?FLAT_SPEED:i==1?LOB_SPEED:FAR_LOB_SPEED,i>0);
            if(shot!=null&&clear(l,root,shot))return shot;
        }
        return null;
    }
    /** The path is clear up to the target, or the first block in the way is close enough for the blast to reach it */
    static boolean clear(Level l,BlockPos root,Arc.Shot shot){
        var end=shot.at(shot.flight());var prev=shot.from();int last=(int)Math.ceil(shot.flight());
        for(int t=Math.min(2,last);;t=Math.min(t+2,last)){
            var next=shot.at(t);var hit=l.clip(new Turrets.Sight(prev,next,root));
            if(hit.getType()!=HitResult.Type.MISS)return hit.getLocation().distanceToSqr(end)<=CLOSE*CLOSE;
            if(t>=last)return true;
            prev=next;
        }
    }
    /** Server: the cannon at root fires a shot */
    static void fire(ServerLevel l,Arc.Shot shot,float damage,float radius,BlockPos root){
        var s=EternalDefense.TURRET_SHELL.get().create(l);if(s==null)return;
        s.start=shot.from();s.velocity=shot.velocity();s.damage=damage;s.radius=radius;s.root=root.immutable();
        s.setPos(s.start);l.addFreshEntity(s);
    }
    @Override public void tick(){
        super.tick();
        var was=Arc.at(start,velocity,tickCount-1);var now=Arc.at(start,velocity,tickCount);setPos(now);
        if(level().isClientSide){level().addParticle(ParticleTypes.SMOKE,was.x,was.y,was.z,0,.02,0);return;}
        var l=(ServerLevel)level();
        var block=l.clip(new Turrets.Sight(was,now,root));var end=block.getType()==HitResult.Type.MISS?now:block.getLocation();
        Vec3 hit=null;double best=Double.MAX_VALUE;
        for(var m:l.getEntitiesOfClass(Mob.class,new AABB(was,end).inflate(1),Turrets::foe)){
            var box=m.getBoundingBox();var on=box.inflate(.3).clip(was,end);
            // A direct hit bursts on the mob itself, at the full damage
            if(on.isPresent()){double d=on.get().distanceToSqr(was);if(d<best){best=d;var p=on.get();hit=new Vec3(Mth.clamp(p.x,box.minX,box.maxX),Mth.clamp(p.y,box.minY,box.maxY),Mth.clamp(p.z,box.minZ,box.maxZ));}}
        }
        if(hit==null&&block.getType()!=HitResult.Type.MISS)hit=end;
        if(hit!=null){blast(l,hit,damage,radius);discard();return;}
        if(tickCount>LIFE||now.y<l.getMinBuildHeight())discard();
    }
    /** A shell bursts: every foe within its radius is hurt (less toward the edge) and thrown back; players and blocks are spared */
    static void blast(ServerLevel l,Vec3 c,float damage,float radius){
        var type=l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(Turrets.damage(Turrets.Line.CANNON));
        var source=new DamageSource(type,null,null,c);
        for(var m:l.getEntitiesOfClass(Mob.class,new AABB(c,c).inflate(radius),Turrets::foe)){
            var box=m.getBoundingBox();var near=new Vec3(Mth.clamp(c.x,box.minX,box.maxX),Mth.clamp(c.y,box.minY,box.maxY),Mth.clamp(c.z,box.minZ,box.maxZ));
            double d=near.distanceTo(c);float hurt=Arc.blastDamage(damage,d,radius);if(hurt<=0)continue;
            m.invulnerableTime=0;
            if(m.hurt(source,hurt))m.knockback(.5*(1-d/radius),c.x-m.getX(),c.z-m.getZ());
        }
        Blasts.far(l,ParticleTypes.EXPLOSION_EMITTER,c.x,c.y+.3,c.z,1,0,0,0,0,96);
        Blasts.far(l,ParticleTypes.EXPLOSION,c.x,c.y+.4,c.z,8,radius*.35,.4,radius*.35,0,96);
        Blasts.far(l,ParticleTypes.LARGE_SMOKE,c.x,c.y+.6,c.z,18,radius*.3,.5,radius*.3,.04,96);
        Blasts.debris(l,c,24,radius*.3);
        Blasts.explode(l,c,4f,1f);
    }
    @Override protected void defineSynchedData(){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<160*160;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    /** The client flies the same arc from the same start, so the shell needs no position updates on the way */
    @Override public void writeSpawnData(FriendlyByteBuf buf){buf.writeDouble(start.x);buf.writeDouble(start.y);buf.writeDouble(start.z);buf.writeDouble(velocity.x);buf.writeDouble(velocity.y);buf.writeDouble(velocity.z);buf.writeVarInt(tickCount);}
    @Override public void readSpawnData(FriendlyByteBuf buf){start=new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble());velocity=new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble());tickCount=buf.readVarInt();var p=Arc.at(start,velocity,tickCount);setPos(p);xo=p.x;yo=p.y;zo=p.z;}
}
