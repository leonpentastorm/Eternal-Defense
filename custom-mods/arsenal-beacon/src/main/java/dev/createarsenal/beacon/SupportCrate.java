package dev.createarsenal.beacon;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** The delivered supply parcel: a chest that parachutes in (or appears at the caller's feet). Right-click opens it like any chest. */
final class SupportCrate {
    private SupportCrate(){}
    static final class ParcelEntity extends Entity {
        private static final EntityDataAccessor<Boolean> FALLING=SynchedEntityData.defineId(ParcelEntity.class,EntityDataSerializers.BOOLEAN);
        private static final MenuType<?>[] ROWS={MenuType.GENERIC_9x1,MenuType.GENERIC_9x2,MenuType.GENERIC_9x3,MenuType.GENERIC_9x4};
        private int age,rows=1;
        /** The chest contents. When the last item is taken and the window closes, the empty parcel folds away. */
        final SimpleContainer box=new SimpleContainer(36){
            @Override public boolean stillValid(Player p){return ParcelEntity.this.isAlive()&&p.distanceToSqr(ParcelEntity.this)<=64;}
            @Override public void startOpen(Player p){level().playSound(null,getX(),getY(),getZ(),SoundEvents.BARREL_OPEN,SoundSource.NEUTRAL,1f,1.1f);}
            @Override public void stopOpen(Player p){
                if(level().isClientSide||!ParcelEntity.this.isAlive())return;
                level().playSound(null,getX(),getY(),getZ(),SoundEvents.BARREL_CLOSE,SoundSource.NEUTRAL,1f,1f);
                if(isEmpty()){if(level() instanceof ServerLevel s)s.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,getX(),getY()+.5,getZ(),10,.3,.3,.3,.02);discard();}
            }
        };
        ParcelEntity(EntityType<? extends ParcelEntity> type,Level level){super(type,level);}
        void setContents(ListTag list){
            box.clearContent();int i=0;
            for(var t:list)if(i<36){var s=ItemStack.of((CompoundTag)t);if(!s.isEmpty())box.setItem(i++,s);}
            rows=Math.max(1,Math.min(4,(i+8)/9));
        }
        void setFalling(boolean falling){entityData.set(FALLING,falling);}
        /** While it falls the canopy (about 5 blocks wide and 5 tall) is part of what is drawn, so it must not be culled with the small crate. */
        @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){return falling()?getBoundingBox().inflate(3,0,3).expandTowards(0,5.5,0):super.getBoundingBoxForCulling();}
        boolean falling(){return entityData.get(FALLING);}
        @Override protected void defineSynchedData(){entityData.define(FALLING,false);}
        @Override public boolean isPickable(){return isAlive();}
        @Override public boolean isPushable(){return false;}
        @Override public void tick(){
            super.tick();age++;
            if(falling()){
                setDeltaMovement(0,-SupportRules.PARCEL_FALL_SPEED,0);move(MoverType.SELF,getDeltaMovement());
                if(onGround()||verticalCollision){
                    setFalling(false);setDeltaMovement(Vec3.ZERO);
                    if(!level().isClientSide)level().playSound(null,getX(),getY(),getZ(),SoundEvents.BARREL_CLOSE,SoundSource.NEUTRAL,1.4f,.6f);
                }
            }else if(!onGround()&&!isNoGravity()){setDeltaMovement(0,-0.08,0);move(MoverType.SELF,getDeltaMovement());}
            if(!level().isClientSide&&age>SupportRules.PARCEL_LIFETIME_TICKS){Containers.dropContents(level(),blockPosition(),box);discard();}
        }
        @Override public InteractionResult interact(Player player,InteractionHand hand){
            if(level().isClientSide)return InteractionResult.SUCCESS;
            if(falling())return InteractionResult.PASS;
            player.openMenu(new SimpleMenuProvider((id,inv,p)->new ChestMenu(ROWS[rows-1],id,inv,box,rows),Component.translatable("entity.arsenal_beacon.support_parcel")));
            return InteractionResult.CONSUME;
        }
        @Override protected void readAdditionalSaveData(CompoundTag n){setContents(n.getList("Contents",Tag.TAG_COMPOUND));age=n.getInt("Age");setFalling(n.getBoolean("Falling"));}
        @Override protected void addAdditionalSaveData(CompoundTag n){var list=new ListTag();for(int i=0;i<box.getContainerSize();i++)if(!box.getItem(i).isEmpty())list.add(box.getItem(i).save(new CompoundTag()));n.put("Contents",list);n.putInt("Age",age);n.putBoolean("Falling",falling());}
        @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    }
}
