package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 0.0.39: a block entity with no data of its own, for the blocks that had none and now have moving parts (the FOB Beacon, the Satellite
 * Beacon, the Command Table, the Tactical Enchanting Table): the client draws their parts through it ({@code Animations}). It saves
 * nothing of its own.
 */
final class AnimatedParts {
    private AnimatedParts(){}
    static final class PartsEntity extends BlockEntity {
        PartsEntity(BlockPos pos,BlockState state){super(EternalDefense.ANIMATED_PARTS_ENTITY.get(),pos,state);}
        /** The parts reach past the anchor block (the dish swings out, the table is two wide): drawn while any of that is in view. */
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2);}
    }


}
