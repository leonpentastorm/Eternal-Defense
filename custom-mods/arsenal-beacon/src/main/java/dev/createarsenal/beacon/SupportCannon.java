package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.UUID;

/**
 * The Support Cannon: a 3 x 3 x 2 gun emplacement. Its turret is heavy, so it spins up and brakes slowly; it turns toward the
 * flare a player threw (never toward the player) and fires there. The turret and barrel are drawn by a renderer.
 */
final class SupportCannon {
    private SupportCannon(){}
    static final int RECOIL_TICKS=16;
    /** Turret kinematics in degrees per tick: slow acceleration and braking give the barrel its weight. */
    static final float ACCEL=0.12f,MAX_SPEED=2.2f;
    /** Barrel geometry shared by the renderer and the muzzle effects: hinge height in blocks, elevation, and reach from hinge to muzzle. */
    static final double SCALE=1.25,TURN_Y=10/16.0,PITCH_DEGREES=50;
    /** The turret and barrel are drawn {@link #SCALE} times larger around the turntable: hinge height and hinge-to-muzzle reach in blocks. */
    static final double PIVOT_Y=TURN_Y+(1.7-TURN_Y)*SCALE,REACH=2.5*SCALE;

    /** One tick of the turret motor. {@code state} is {yaw, speed}; pure so the server and every client agree and tests can run it. */
    static void spin(float[] state,float target){
        float d=target-state[0],dir=Math.signum(d),dist=Math.abs(d),v=state[1];
        if(dist<0.05f&&Math.abs(v)<ACCEL*1.5f){state[0]=target;state[1]=0;return;}
        boolean toward=v==0||v*dir>0;
        float stop=v*v/(2*ACCEL);
        if(toward&&stop<dist)v+=dir*ACCEL;          // room left: speed up
        else if(toward)v-=Math.signum(v)*ACCEL;     // must brake now
        else v+=dir*ACCEL*2;                        // moving away from the target: turn round
        v=Math.max(-MAX_SPEED,Math.min(MAX_SPEED,v));
        float next=state[0]+v;
        if(Math.signum(target-next)!=dir&&dir!=0){next=target;v=0;}   // arrived
        state[0]=next;state[1]=v;
    }
    /** Ticks the turret needs to turn through {@code degrees} from rest (for tests and timing). */
    static int turnTicks(float degrees){var s=new float[]{0,0};int n=0;while(n<2000&&(Math.abs(degrees-s[0])>0.01f||s[1]!=0)){spin(s,degrees);n++;}return n;}

    static final class CannonBlock extends Block implements EntityBlock {
        CannonBlock(){super(Properties.of().strength(4,8).noOcclusion().dynamicShape().sound(SoundType.METAL));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return ArsenalStructures.cell(s,0,0,0);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState s){return new CannonEntity(pos,s);}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){
            super.onPlace(s,l,pos,old,moving);
            if(l instanceof ServerLevel)ArsenalStructures.install(l,pos,s);
        }
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity who,ItemStack stack){
            super.setPlacedBy(l,pos,s,who,stack);
            if(l instanceof ServerLevel server&&who instanceof ServerPlayer sp&&l.getBlockEntity(pos) instanceof CannonEntity be){
                be.owner=sp.getUUID();be.ownerName=sp.getGameProfile().getName();be.setChanged();
                SupportData.get(server).setCannon(sp.getUUID(),pos);
            }
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){
                ArsenalStructures.remove(l,pos);
                if(l instanceof ServerLevel server&&l.getBlockEntity(pos) instanceof CannonEntity be&&be.owner!=null)SupportData.get(server).clearCannon(be.owner,pos);
            }
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,RandomSource random){ArsenalStructures.install(l,pos,s);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(!l.isClientSide&&p instanceof ServerPlayer sp&&l.getBlockEntity(pos) instanceof CannonEntity be){
                if(BaseZone.disabled(l,pos,sp))return InteractionResult.CONSUME;
                if(be.owner==null||!be.owner.equals(sp.getUUID())){BaseZone.say(sp,be.owner==null?"no_owner":"not_yours",be.ownerName);return InteractionResult.CONSUME;}
                var base=SupportData.get((ServerLevel)l).of(sp.getUUID());
                boolean linked=base!=null&&SupportData.near(base.platform,pos);
                sp.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.cannon."+(linked?"linked":"unlinked"),SupportRules.CANNON_RANGE),true);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
            return type==ArsenalBeacon.CANNON_ENTITY.get()?(lv,pos,st,be)->((CannonEntity)be).tick(lv):null;
        }
    }

    /** One Support Cannon per player, in the beacon zone, with room for its 3 x 3 x 2 body. */
    static final class CannonItem extends BaseZone.ZoneItem {
        CannonItem(Block block){super(block);}
        @Override String extra(BlockPlaceContext c){
            var p=c.getPlayer();if(!(c.getLevel() instanceof ServerLevel server))return null;
            if(p instanceof ServerPlayer sp){
                var base=SupportData.get(server).of(sp.getUUID());
                if(base!=null&&base.cannon!=null&&server.hasChunkAt(base.cannon)&&server.getBlockState(base.cannon).is(ArsenalBeacon.SUPPORT_CANNON.get()))return "only_one_cannon";
            }
            return ArsenalStructures.available(c.getLevel(),c.getClickedPos(),ArsenalBeacon.SUPPORT_CANNON.get().defaultBlockState())?null:"cannon_room";
        }
    }

    static final class CannonEntity extends BlockEntity {
        UUID owner;String ownerName="";
        /** Degrees, unwrapped. {@code target} is authoritative (saved and synced); {@code yaw} and {@code speed} are the motor state. */
        float target,yaw,prevYaw,speed;int recoil;
        /** Shots fired since the chunk loaded (not saved); handy for tests and debugging. */
        int shots;
        private final float[] motor=new float[2];
        CannonEntity(BlockPos pos,BlockState s){super(ArsenalBeacon.CANNON_ENTITY.get(),pos,s);}
        /** Server: face {@code point}; the turret takes the short way round and needs time to get there. */
        void aimAt(ServerLevel level,Vec3 point){
            double radians=Math.atan2(-(point.x-(worldPosition.getX()+.5)),-(point.z-(worldPosition.getZ()+.5)));
            float next=(float)Math.toDegrees(radians);
            float diff=next-yaw;diff=((diff%360)+540)%360-180;
            float goal=yaw+diff;
            if(Math.abs(goal-target)>0.01f){target=goal;setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        }
        /** True once the barrel points at the target and has stopped moving. */
        boolean ready(){return Math.abs(target-yaw)<0.6f&&Math.abs(speed)<0.2f;}
        /** Server: fire now (the caller waits for {@link #ready()}). */
        void fire(ServerLevel level){
            shots++;
            double rad=Math.toRadians(yaw),pitch=Math.toRadians(PITCH_DEGREES);
            double reach=REACH,flat=Math.cos(pitch)*reach;
            double x=worldPosition.getX()+.5-Math.sin(rad)*flat,y=worldPosition.getY()+PIVOT_Y+Math.sin(pitch)*reach,z=worldPosition.getZ()+.5-Math.cos(rad)*flat;
            level.sendParticles(ParticleTypes.FLAME,x,y,z,16,.15,.15,.15,.08);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,x,y,z,10,.25,.2,.25,.03);
            level.sendParticles(ParticleTypes.EXPLOSION,x,y,z,1,0,0,0,0);
            level.playSound(null,x,y,z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,3f,.55f);
            level.blockEvent(worldPosition,getBlockState().getBlock(),1,0);
        }
        void tick(Level level){
            motor[0]=yaw;motor[1]=speed;
            prevYaw=yaw;spin(motor,target);yaw=motor[0];speed=motor[1];
            if(level.isClientSide){
                if(recoil>0)recoil--;
                else if(Math.abs(speed)>0.3f&&level.getGameTime()%8==0)level.playLocalSound(worldPosition,SoundEvents.PISTON_EXTEND,SoundSource.BLOCKS,.25f,.5f,false);
            }
        }
        @Override public boolean triggerEvent(int id,int param){if(id==1){recoil=RECOIL_TICKS;return true;}return super.triggerEvent(id,param);}
        @Override protected void saveAdditional(CompoundTag n){
            super.saveAdditional(n);n.putFloat("Target",target);n.putFloat("Yaw",yaw);
            if(owner!=null){n.putUUID("Owner",owner);n.putString("OwnerName",ownerName);}
        }
        @Override public void load(CompoundTag n){
            super.load(n);target=n.getFloat("Target");yaw=prevYaw=n.contains("Yaw")?n.getFloat("Yaw"):target;speed=0;
            if(n.hasUUID("Owner")){owner=n.getUUID("Owner");ownerName=n.getString("OwnerName");}
        }
        @Override public CompoundTag getUpdateTag(){var n=new CompoundTag();n.putFloat("Target",target);return n;}
        @Override public void handleUpdateTag(CompoundTag n){target=n.getFloat("Target");if(!synced){synced=true;yaw=prevYaw=target;}}
        private boolean synced;
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(4);}
    }
}
