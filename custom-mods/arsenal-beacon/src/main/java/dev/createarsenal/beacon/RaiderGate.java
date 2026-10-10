package dev.createarsenal.beacon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The red gate a stuck raider channels beside (drawn with the Return portal sprite, see {@code SupportClient.GateRenderer}). It only exists to be
 * seen: players cannot enter, hit, push or target it, and it is never saved (after a reload the raiders fall back to the progress timer). Which
 * raiders belong to it, and when it goes away, is decided by {@link RaiderGates}; the entity itself keeps no state but its size (a spawn gate is
 * drawn bigger, synced with the vanilla entity data), so it needs no packet of ours: the vanilla spawn and data packets are enough.
 */
final class RaiderGate extends Entity {
    /** Server: this one is the exit at a channeler's destination (raiders come out of it; nobody joins it). */
    boolean exit;
    /** Server: the game time a spawn gate closes by itself (0: {@link RaiderGates} closes it). */
    long closeAt;
    /** A spawn gate (enemies come out of it; see {@link RaiderGates#spawnGate}): drawn bigger than a channel gate. */
    private static final EntityDataAccessor<Boolean> BIG=SynchedEntityData.defineId(RaiderGate.class,EntityDataSerializers.BOOLEAN);
    RaiderGate(EntityType<? extends RaiderGate> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(){entityData.define(BIG,false);}
    boolean big(){return entityData.get(BIG);}
    void setBig(boolean big){entityData.set(BIG,big);}
    /** The sprite is wider and taller than the entity (a spawn gate 1.6 x 3.2 blocks): cull by the sprite, not the 1.2 x 2.2 box. */
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(.4,1.2,.4);}
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
    @Override public void tick(){super.tick();if(!level().isClientSide&&closeAt>0&&level().getGameTime()>=closeAt)discard();}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
