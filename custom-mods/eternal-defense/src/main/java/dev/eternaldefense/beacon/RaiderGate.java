package dev.eternaldefense.beacon;

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

/** The red gate a stuck raider channels beside */
final class RaiderGate extends Entity {
    /** Server: this one is the exit at a channeler's destination (raiders come out of it; nobody joins it). */
    boolean exit;
    /** Server: the game time a spawn gate closes by itself (0: RaiderGates closes it). */
    long closeAt;
    /** A spawn gate (enemies come out of it; see RaiderGates.spawnGate): drawn bigger than a channel gate. */
    private static final EntityDataAccessor<Boolean> BIG=SynchedEntityData.defineId(RaiderGate.class,EntityDataSerializers.BOOLEAN);
    RaiderGate(EntityType<? extends RaiderGate> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(){entityData.define(BIG,false);}
    boolean big(){return entityData.get(BIG);}
    void setBig(boolean big){entityData.set(BIG,big);}
    /** The sprite is wider and taller than the entity (a spawn gate 1.6 x 3.2 blocks) */
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
