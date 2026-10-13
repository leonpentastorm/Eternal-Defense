package dev.createarsenal.beacon;

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
        PartsEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.ANIMATED_PARTS_ENTITY.get(),pos,state);}
        /** The parts reach past the anchor block (the dish swings out, the table is two wide): drawn while any of that is in view. */
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2);}
    }

    static boolean needsEntity(BlockState state){
        return state.is(ArsenalBeacon.FOB_BEACON.get())||state.is(ArsenalBeacon.SATELLITE_BEACON.get())||state.is(ArsenalBeacon.COMMAND_TABLE.get())||state.is(ArsenalBeacon.ENCHANTING_TABLE.get());
    }

    /**
     * Such a block placed before 0.0.39 has no block entity in its chunk, and its blockstate now draws the model without its moving parts:
     * the entity is made when the chunk loads. Forge posts the load on the server thread after the chunk's own block entities are in and
     * before the chunk is sent to any player, so the client receives it with the chunk. Making a block entity changes no block and wakes no
     * neighbour. Returns how many were made.
     */
    static int restore(LevelChunk chunk){
        int made=0;var sections=chunk.getSections();
        for(int i=0;i<sections.length;i++){var section=sections[i];if(section.hasOnlyAir()||!section.maybeHas(AnimatedParts::needsEntity))continue;
            int baseY=chunk.getMinBuildHeight()+i*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){if(!needsEntity(section.getBlockState(x,y,z)))continue;
                var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
                if(chunk.getBlockEntity(pos,LevelChunk.EntityCreationType.CHECK)==null&&chunk.getBlockEntity(pos,LevelChunk.EntityCreationType.IMMEDIATE)!=null)made++;}
        }
        return made;
    }
    static final class Upgrade {
        @SubscribeEvent public void load(ChunkEvent.Load event){if(event.getLevel() instanceof ServerLevel&&event.getChunk() instanceof LevelChunk chunk)restore(chunk);}
    }
}
