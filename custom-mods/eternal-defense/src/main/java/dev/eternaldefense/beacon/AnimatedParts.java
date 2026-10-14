package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** A block entity with no data of its own, for the blocks that had none and now have moving parts */
final class AnimatedParts {
    private AnimatedParts(){}
    static final class PartsEntity extends BlockEntity {
        PartsEntity(BlockPos pos,BlockState state){super(EternalDefense.ANIMATED_PARTS_ENTITY.get(),pos,state);}
        /** The parts reach past the anchor block (the dish swings out, the table is two wide) */
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2);}
    }


}
