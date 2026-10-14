package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import java.util.*;

/**
 * The FOB zone is the vanilla village (0.0.23): what {@code ServerLevel.isVillage} calls a village, a 16-block section within one section of a
 * section holding a bed, a workstation or a bell that a villager has claimed (vanilla's occupied points of interest; a bed nobody sleeps in
 * makes no village). The zone of a FOB is the group of such sections joined to the
 * one its beacon stands in, only in loaded chunks and at most {@link FobRules#ZONE_REACH} sections away. It is worked out when needed, kept in
 * memory for a few seconds and never saved.
 */
final class FobZone {
    private FobZone(){}
    private record Cached(Set<Long> sections,long at){}
    private static final Map<Long,Cached> CACHE=new HashMap<>();
    static void forget(long fobId){CACHE.remove(fobId);}
    static void clear(){CACHE.clear();}

    /** True when the 16-block section is part of a village by vanilla's own rule (only for a loaded chunk; nothing is ever loaded). */
    static boolean villageSection(ServerLevel l,SectionPos s){
        return l.hasChunk(s.x(),s.z())&&l.sectionsToVillage(s)<=FobRules.VILLAGE_SECTIONS;
    }
    /** The village sections joined to the one at {@code origin} (empty when that one is no village). */
    static Set<Long> sections(ServerLevel l,BlockPos origin){
        var start=SectionPos.of(origin);var out=new HashSet<Long>();
        if(!villageSection(l,start))return out;
        var queue=new ArrayDeque<SectionPos>();queue.add(start);out.add(start.asLong());
        int[][] steps={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while(!queue.isEmpty()){
            var s=queue.poll();
            for(int[] d:steps){
                var n=SectionPos.of(s.x()+d[0],s.y()+d[1],s.z()+d[2]);
                if(Math.abs(n.x()-start.x())>FobRules.ZONE_REACH||Math.abs(n.z()-start.z())>FobRules.ZONE_REACH||Math.abs(n.y()-start.y())>FobRules.ZONE_REACH_Y)continue;
                if(!l.isInWorldBounds(n.origin())||out.contains(n.asLong())||!villageSection(l,n))continue;
                out.add(n.asLong());queue.add(n);
            }
        }
        return out;
    }
    /** The zone of a FOB, worked out again at most every {@link FobRules#ZONE_REFRESH_TICKS}. */
    static Set<Long> of(ServerLevel l,FobData.Fob f){
        long now=l.getGameTime();var c=CACHE.get(f.id);
        if(c!=null&&now-c.at()<FobRules.ZONE_REFRESH_TICKS&&now>=c.at())return c.sections();
        var s=sections(l,f.pos);CACHE.put(f.id,new Cached(s,now));
        if(!s.isEmpty()){var cols=columns(s);if(!java.util.Arrays.equals(cols,f.zoneColumns)){f.zoneColumns=cols;FobData.get(l).setDirty();}}   // 0.0.31: kept for the Command Table's map
        return s;
    }
    /** The 16-block columns of a zone (x and z packed as in {@code ChunkPos.asLong}), sorted. */
    static long[] columns(Set<Long> zone){var set=new TreeSet<Long>();for(long s:zone)set.add(net.minecraft.world.level.ChunkPos.asLong(SectionPos.x(s),SectionPos.z(s)));return set.stream().mapToLong(Long::longValue).toArray();}
    static boolean contains(Set<Long> zone,BlockPos p){return zone.contains(SectionPos.asLong(SectionPos.blockToSectionCoord(p.getX()),SectionPos.blockToSectionCoord(p.getY()),SectionPos.blockToSectionCoord(p.getZ())));}
    /** The box around all sections of a zone (null when empty). */
    static AABB box(Set<Long> zone){
        if(zone.isEmpty())return null;
        int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
        for(long s:zone){int x=SectionPos.x(s),y=SectionPos.y(s),z=SectionPos.z(s);x0=Math.min(x0,x);y0=Math.min(y0,y);z0=Math.min(z0,z);x1=Math.max(x1,x);y1=Math.max(y1,y);z1=Math.max(z1,z);}
        return new AABB(x0*16,y0*16,z0*16,(x1+1)*16,(y1+1)*16,(z1+1)*16);
    }
    /** The living villagers in a zone (adults and children; not zombie villagers, not wandering traders). */
    static List<Villager> villagers(ServerLevel l,Set<Long> zone){
        var box=box(zone);if(box==null)return List.of();
        return l.getEntitiesOfClass(Villager.class,box,v->v.isAlive()&&contains(zone,v.blockPosition()));
    }
    /** Beds and workstations in the zone (shown on the FOB screen), counted in the loaded chunks of the zone only. */
    static int[] homesAndJobs(ServerLevel l,Set<Long> zone){
        int beds=0,jobs=0;var chunks=new HashSet<Long>();
        for(long s:zone)chunks.add(net.minecraft.world.level.ChunkPos.asLong(SectionPos.x(s),SectionPos.z(s)));
        var poi=l.getPoiManager();
        for(long c:chunks){
            var pos=new net.minecraft.world.level.ChunkPos(c);if(!l.hasChunk(pos.x,pos.z))continue;
            for(var r:poi.getInChunk(h->true,pos,net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()){
                if(!contains(zone,r.getPos()))continue;
                if(r.getPoiType().is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME))beds++;
                else if(r.getPoiType().is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE))jobs++;
            }
        }
        return new int[]{beds,jobs};
    }
}
