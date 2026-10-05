package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Support Cannon: turns toward whoever calls support and fires. The turret and barrel are drawn by a renderer. */
final class SupportCannon {
    private SupportCannon(){}
    static final int RECOIL_TICKS=12;
    static final float TURN_DEGREES_PER_TICK=12f;

    static final class CannonBlock extends Block implements EntityBlock {
        private static final VoxelShape SHAPE=Block.box(1,0,1,15,15,15);
        CannonBlock(){super(Properties.of().strength(3,6).noOcclusion().sound(SoundType.METAL));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState s){return new CannonEntity(pos,s);}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){
            super.onPlace(s,l,pos,old,moving);
            if(l instanceof ServerLevel server&&server.dimension()==Level.OVERWORLD){var d=SupportData.get(server);d.cannons.add(pos.asLong());d.setDirty();}
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())&&l instanceof ServerLevel server){var d=SupportData.get(server);if(d.cannons.remove(pos.asLong()))d.setDirty();}
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
            return type==ArsenalBeacon.CANNON_ENTITY.get()?(lv,pos,st,be)->((CannonEntity)be).tick(lv):null;
        }
    }

    static final class CannonEntity extends BlockEntity {
        /** Degrees, unwrapped. {@code target} is authoritative (saved and synced); {@code yaw} is what is drawn. */
        float target,yaw,prevYaw;int recoil,fireIn=-1;
        /** Shots fired since the chunk loaded (not saved); handy for tests and debugging. */
        int shots;
        CannonEntity(BlockPos pos,BlockState s){super(ArsenalBeacon.CANNON_ENTITY.get(),pos,s);}
        /** Server: face {@code radians} (0 = north, positive counter-clockwise seen from above). */
        void aim(ServerLevel level,double radians){
            float next=(float)Math.toDegrees(radians);
            // keep the turret on the short way round
            float diff=next-target;diff=((diff%360)+540)%360-180;
            if(Math.abs(diff)>0.5f){target+=diff;setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
            turnDelay=Math.abs(diff);
        }
        private float turnDelay;
        /** Server: fire once the turret has finished turning. */
        void fire(ServerLevel level){fireIn=Math.max(1,(int)Math.ceil(turnDelay/TURN_DEGREES_PER_TICK)+2);turnDelay=0;}
        void muzzleFlash(ServerLevel level){
            shots++;
            double rad=Math.toRadians(target);double dx=-Math.sin(rad),dz=-Math.cos(rad);
            double x=worldPosition.getX()+.5+dx*1.1,y=worldPosition.getY()+.8,z=worldPosition.getZ()+.5+dz*1.1;
            level.sendParticles(ParticleTypes.FLAME,x,y,z,10,.1,.1,.1,.06);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,x,y,z,6,.15,.1,.15,.02);
            level.sendParticles(ParticleTypes.EXPLOSION,x,y,z,1,0,0,0,0);
            level.playSound(null,worldPosition,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,1.4f,.8f);
            level.blockEvent(worldPosition,getBlockState().getBlock(),1,0);
        }
        void tick(Level level){
            if(level.isClientSide){
                prevYaw=yaw;float diff=target-yaw;
                if(Math.abs(diff)<TURN_DEGREES_PER_TICK)yaw=target;else yaw+=Math.signum(diff)*TURN_DEGREES_PER_TICK;
                if(recoil>0)recoil--;
            }else if(fireIn>=0&&--fireIn<0&&level instanceof ServerLevel server)muzzleFlash(server);
        }
        @Override public boolean triggerEvent(int id,int param){if(id==1){recoil=RECOIL_TICKS;return true;}return super.triggerEvent(id,param);}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.putFloat("Target",target);}
        @Override public void load(CompoundTag n){super.load(n);target=n.getFloat("Target");yaw=prevYaw=target;}
        @Override public CompoundTag getUpdateTag(){var n=new CompoundTag();n.putFloat("Target",target);return n;}
        @Override public void handleUpdateTag(CompoundTag n){target=n.getFloat("Target");}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);}
    }
}
