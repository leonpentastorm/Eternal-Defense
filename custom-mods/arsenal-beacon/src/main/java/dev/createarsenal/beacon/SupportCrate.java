package dev.createarsenal.beacon;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** The delivered supply parcel: falls under a parachute (or appears at the caller's feet), then opens with a right-click. */
final class SupportCrate {
    private SupportCrate(){}
    static final class ParcelEntity extends Entity {
        private static final EntityDataAccessor<Boolean> FALLING=SynchedEntityData.defineId(ParcelEntity.class,EntityDataSerializers.BOOLEAN);
        private final NonNullList<ItemStack> contents=NonNullList.create();
        private int age;
        ParcelEntity(EntityType<? extends ParcelEntity> type,Level level){super(type,level);}
        void setContents(ListTag list){contents.clear();for(var t:list)contents.add(ItemStack.of((CompoundTag)t));}
        void setFalling(boolean falling){entityData.set(FALLING,falling);}
        boolean falling(){return entityData.get(FALLING);}
        @Override protected void defineSynchedData(){entityData.define(FALLING,false);}
        @Override public boolean isPickable(){return isAlive();}
        @Override public boolean isPushable(){return false;}
        @Override public void tick(){
            super.tick();age++;
            if(falling()){
                setDeltaMovement(0,-0.13,0);move(MoverType.SELF,getDeltaMovement());
                if(onGround()||verticalCollision){
                    setFalling(false);setDeltaMovement(Vec3.ZERO);
                    if(!level().isClientSide)level().playSound(null,getX(),getY(),getZ(),SoundEvents.BARREL_CLOSE,SoundSource.NEUTRAL,1.2f,.7f);
                }
            }else if(!onGround()&&!isNoGravity()){setDeltaMovement(0,-0.08,0);move(MoverType.SELF,getDeltaMovement());}
            if(!level().isClientSide&&age>SupportRules.PARCEL_LIFETIME_TICKS)discard();
        }
        @Override public InteractionResult interact(Player player,InteractionHand hand){
            if(level().isClientSide)return InteractionResult.SUCCESS;
            if(falling())return InteractionResult.PASS;
            for(var s:contents){var copy=s.copy();if(!player.getInventory().add(copy))player.drop(copy,false);}
            level().playSound(null,getX(),getY(),getZ(),SoundEvents.BARREL_OPEN,SoundSource.NEUTRAL,1f,1.1f);
            if(player instanceof ServerPlayer sp)sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.arsenal_beacon.support.opened"),true);
            discard();return InteractionResult.CONSUME;
        }
        @Override protected void readAdditionalSaveData(CompoundTag n){contents.clear();for(var t:n.getList("Contents",Tag.TAG_COMPOUND))contents.add(ItemStack.of((CompoundTag)t));age=n.getInt("Age");setFalling(n.getBoolean("Falling"));}
        @Override protected void addAdditionalSaveData(CompoundTag n){var list=new ListTag();for(var s:contents)list.add(s.save(new CompoundTag()));n.put("Contents",list);n.putInt("Age",age);n.putBoolean("Falling",falling());}
        @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    }
}
