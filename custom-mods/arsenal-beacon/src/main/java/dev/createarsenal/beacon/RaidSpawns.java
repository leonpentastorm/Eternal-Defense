package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Exterior staging only: no closer fallback, no forced chunks, no roofs or interiors. */
final class RaidSpawns {
    static final int CLEARANCE=64,SPREAD=16;
    private static final Map<ServerLevel,Long> WARNINGS=new WeakHashMap<>();
    static BlockPos ring(BlockPos origin,int radius,double angle,int extra){
        return ringAt(origin,radius+CLEARANCE+Math.max(0,Math.min(SPREAD,extra)),angle);
    }
    static BlockPos ringAt(BlockPos origin,int distance,double angle){
        double cos=Math.cos(angle),sin=Math.sin(angle);double stretch=distance/Math.max(Math.abs(cos),Math.abs(sin));
        return new BlockPos(origin.getX()+(int)Math.round(cos*stretch),origin.getY(),origin.getZ()+(int)Math.round(sin*stretch));
    }
    static boolean safe(ServerLevel l,CampaignData d,BlockPos p){
        if(Math.max(Math.abs(p.getX()-d.beacon.getX()),Math.abs(p.getZ()-d.beacon.getZ()))<d.radius()+CLEARANCE||!l.hasChunkAt(p)||!l.isPositionEntityTicking(p)||!l.getWorldBorder().isWithinBounds(p)||Math.abs(p.getY()-d.beacon.getY())>12)return false;
        return environmentSafe(l,p);
    }
    static boolean environmentSafe(ServerLevel l,BlockPos p){
        if(p.getY()<l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()))return false;
        int airBelow=0;for(int depth=1;depth<=12;depth++){airBelow=l.getBlockState(p.below(depth)).isAir()?airBelow+1:0;if(airBelow>=2)return false;} // Reject thin terrain-coloured roofs and overhangs.
        var floor=p.below();String id=BuiltInRegistries.BLOCK.getKey(l.getBlockState(floor).getBlock()).toString();
        if(!BaseScoring.terrain(id)||!l.getBlockState(floor).isSolid()||!l.getFluidState(floor).isEmpty()||!l.getFluidState(p).isEmpty()||!l.getFluidState(p.above()).isEmpty()||!l.getBlockState(p).getCollisionShape(l,p).isEmpty()||!l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty())return false;
        var placed=BaseScoring.Ledger.get(l).placed;
        for(var cell:BlockPos.betweenClosed(p.offset(-6,-1,-6),p.offset(6,4,6))){
            if(!l.hasChunkAt(cell))return false;
            if(placed.contains(cell.asLong())||l.getBlockEntity(cell)!=null)return false;
            // Nearby crafted floors/walls signal a building even in older worlds without a ledger.
            var s=l.getBlockState(cell);String block=BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();if(!s.isAir()&&s.isSolid()&&!BaseScoring.terrain(block)&&!block.endsWith("_log")&&!block.endsWith("_stem"))return false;
        }return true;
    }
    static BlockPos find(ServerLevel l,CampaignData d){
        int minimum=d.radius()+CLEARANCE;
        int farthest=Math.min(192,Math.max(minimum,l.getServer().getPlayerList().getSimulationDistance()*16-24));
        // Start at the outer active-chunk edge and work inward, never inside the safety margin.
        int probes=0;for(int distance=farthest;distance>=minimum;distance=Math.max(minimum,distance-16)){
            double offset=l.random.nextDouble()*Math.PI*2;
            for(int sector=0;sector<12;sector++){
                probes++;var horizontal=ringAt(d.beacon,distance,offset+sector*Math.PI/6);if(!l.hasChunkAt(horizontal))continue;
                var p=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,horizontal);if(safe(l,d,p)){WARNINGS.remove(l);return p;}
            }
            if(distance==minimum||probes>=120)break;
        }
        long now=l.getGameTime();if(d.waveTicks>=600&&now-WARNINGS.getOrDefault(l,now-600)>=600){WARNINGS.put(l,now);l.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("[Create Arsenal] Reinforcements are waiting for clear, loaded outdoor ground at least 64 blocks beyond the protected boundary. They will not spawn closer or inside buildings."),false);}return null;
    }
}
