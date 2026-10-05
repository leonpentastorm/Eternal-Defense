package dev.createarsenal.beacon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
/** Deferred migration avoids changing chunk data while Forge is loading it. */
final class StructureMigration {
    private static final java.util.Map<ServerLevel,java.util.Set<net.minecraft.world.level.ChunkPos>> pending=new java.util.concurrent.ConcurrentHashMap<>();
    @SubscribeEvent public void load(ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk))return;
        pending.computeIfAbsent(level,l->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(chunk.getPos());
    }
    @SubscribeEvent public void stop(net.minecraftforge.event.server.ServerStoppedEvent event){pending.clear();}
    @SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        var queued=pending.get(level);if(queued==null)return;int budget=2;
        for(var it=queued.iterator();it.hasNext()&&budget-->0;){var location=it.next();it.remove();var chunk=level.getChunkSource().getChunkNow(location.x,location.z);if(chunk==null)continue;
            var sections=chunk.getSections();
            for(int i=0;i<sections.length;i++){var section=sections[i];if(!section.maybeHas(s->s.getBlock() instanceof WeaponPlatform.Station||s.is(ArsenalBeacon.STRUCTURE_PART.get())))continue;
                int baseY=chunk.getMinBuildHeight()+i*16;
                for(int y=0;y<16;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++){var state=section.getBlockState(x,y,z);var pos=new BlockPos(location.getMinBlockX()+x,baseY+y,location.getMinBlockZ()+z);
                    if(state.is(ArsenalBeacon.STRUCTURE_PART.get())){level.scheduleTick(pos,state.getBlock(),1);continue;}
                    if(!(state.getBlock() instanceof WeaponPlatform.Station))continue;level.getBlockEntity(pos);
                    var wide=state.setValue(ArsenalStructures.WIDE,true).setValue(ArsenalStructures.TALL,true);if(ArsenalStructures.available(level,pos,wide)){if(state!=wide)level.setBlock(pos,wide,3);ArsenalStructures.install(level,pos,wide);}
                }
            }
        }
    }
}
