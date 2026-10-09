package dev.createarsenal.beacon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The red gate a stuck raider channels beside (drawn with the Return portal sprite, see {@code SupportClient.GateRenderer}). It only exists to be
 * seen: players cannot enter, hit, push or target it, and it is never saved (after a reload the raiders fall back to the progress timer). Which
 * raiders belong to it, and when it goes away, is decided by {@link RaiderGates}; the entity itself keeps no state, so it needs no packet either:
 * the vanilla spawn packet is enough for clients to see it.
 */
final class RaiderGate extends Entity {
    RaiderGate(EntityType<? extends RaiderGate> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean isNoGravity(){return true;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean isInvulnerable(){return true;}
    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
