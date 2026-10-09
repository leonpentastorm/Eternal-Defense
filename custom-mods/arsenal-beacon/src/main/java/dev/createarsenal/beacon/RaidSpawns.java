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
    /** How far below the beacon a spawn may be: none at first, then more the longer reinforcements have been waiting for a place. */
    static int allowedDrop(long waveTicks){return waveTicks>=1800?10:waveTicks>=600?4:0;}
    static boolean safe(ServerLevel l,CampaignData d,BlockPos p){
        if(Math.max(Math.abs(p.getX()-d.beacon.getX()),Math.abs(p.getZ()-d.beacon.getZ()))<d.radius()+CLEARANCE||!l.hasChunkAt(p)||!l.isPositionEntityTicking(p)||!l.getWorldBorder().isWithinBounds(p))return false;
        // surface only, at or above the beacon's level
        if(p.getY()<d.beacon.getY()-allowedDrop(d.waveTicks)||p.getY()>d.beacon.getY()+24)return false;
        return environmentSafe(l,p)&&openGround(l,p);
    }
    /** Not a crevice: the ground around the spot is open and level, so a mob dropped here can walk away in any direction. */
    static boolean openGround(ServerLevel l,BlockPos p){
        int open=0;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            var cell=p.offset(dx,0,dz);if(!l.hasChunkAt(cell))return false;
            boolean free=l.getBlockState(cell).getCollisionShape(l,cell).isEmpty()&&l.getBlockState(cell.above()).getCollisionShape(l,cell.above()).isEmpty()&&l.getBlockState(cell.below()).isSolid();
            if(free)open++;
            else if(Math.abs(dx)<=1&&Math.abs(dz)<=1)return false; // the ring around the spot must be walkable
        }
        return open>=18;
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
    // ---- marchable corridors ----------------------------------------------------------------------------------------------------
    // Raiders march straight at the beacon, so a spawn whose straight line to the zone runs through water, lava, a cliff or a wall of
    // rock can leave a raider that never arrives, and one such raider can lose the raid. Each staging direction is therefore checked once
    // per raid from the surface heights along it, and clean directions are preferred. Nothing is ever force-loaded: a sample in an
    // unloaded chunk is "unknown", never a failure.
    /** The staging ring is cut into this many world-aligned wedges (wedge 0 starts due east, counting towards +z). One probe per wedge per raid. */
    static final int WEDGES=12;
    static final double WEDGE=Math.PI*2/WEDGES;
    /** Distance between two samples along a wedge's centre ray, and where the ray starts (blocks beyond the zone radius). */
    static final int PROBE_STEP=4,PROBE_FROM=8;
    /** A rise of more than this, or a drop of more than {@link #MAX_DROP}, between two samples is a violation. */
    static final int MAX_STEP=2,MAX_DROP=3;
    static final int UNKNOWN=Integer.MIN_VALUE;
    /** How many wedges, and how many angles in each, a rescue tries; and how far from every player it has to land. */
    static final int RESCUE_TRIES=3;
    static final double RESCUE_PLAYER_DISTANCE=24;

    /** Violations along a corridor (water, lava, magma, steps, drops) and how many samples could not be read. */
    record Score(int violations,int unknown){boolean clean(){return violations==0;}}
    /**
     * The surface profile of one wedge's centre ray: sample {@code i} lies {@code from+i*PROBE_STEP} blocks from the beacon (square ring
     * distance, like {@link #ringAt}); {@code surface[i]} is the surface height, {@link #UNKNOWN} when it could not be read or is not natural
     * ground; {@code hazard[i]} marks a surface of fluid or magma. Sample 0 is the one nearest the zone.
     */
    record Corridor(int from,int[] surface,boolean[] hazard){
        /** Pure: the score of the part of the corridor between {@code distance} and the zone (what a raider spawned at {@code distance} must cross). */
        Score score(int distance){return RaidSpawns.score(from,surface,hazard,distance);}
    }
    /** Pure: wedge of an offset from the beacon, 0 to {@link #WEDGES}-1. */
    static int wedgeOf(double dx,double dz){double a=Math.atan2(dz,dx);if(a<0)a+=Math.PI*2;return Math.min(WEDGES-1,(int)(a/WEDGE+1e-9));}
    static int wedgeOf(BlockPos origin,BlockPos p){return wedgeOf(p.getX()-origin.getX(),p.getZ()-origin.getZ());}
    /**
     * Pure. Walking inwards from {@code distance}: a hazard surface counts one violation, a rise of more than {@link #MAX_STEP} or a drop of more
     * than {@link #MAX_DROP} between two neighbouring samples counts one. A pair with an unknown sample is not judged.
     */
    static Score score(int from,int[] surface,boolean[] hazard,int distance){
        int count=Math.max(0,Math.min(surface.length,Math.floorDiv(distance-from,PROBE_STEP)+1));int violations=0,unknown=0;
        for(int i=0;i<count;i++){
            if(surface[i]==UNKNOWN){unknown++;continue;}
            if(hazard[i])violations++;
            if(i>0&&surface[i-1]!=UNKNOWN){int rise=surface[i-1]-surface[i];if(rise>MAX_STEP||-rise>MAX_DROP)violations++;}
        }
        return new Score(violations,unknown);
    }
    /** The farthest ring spawns are tried on: the outer edge of the active chunks, never beyond 192 blocks. */
    static int farthest(ServerLevel l,CampaignData d){
        return Math.min(192,Math.max(d.radius()+CLEARANCE,l.getServer().getPlayerList().getSimulationDistance()*16-24));
    }
    private static final Map<Integer,Corridor> CORRIDORS=new HashMap<>();
    private static long corridorRaid=Long.MIN_VALUE;
    private static int probesThisRaid;
    /** The corridor of one wedge, probed on first use and kept for the rest of the raid (so at most {@link #WEDGES} probes per raid). */
    static Corridor corridor(ServerLevel l,CampaignData d,int wedge){
        long raid=d.campaignSerial*1_000_003L+d.raidsStarted;
        if(raid!=corridorRaid){CORRIDORS.clear();corridorRaid=raid;probesThisRaid=0;}
        return CORRIDORS.computeIfAbsent(wedge,w->{probesThisRaid++;return probe(l,d,w);});
    }
    /** A new raid, the end of one, or the server stopping: forget every probe. */
    static void forgetCorridors(){CORRIDORS.clear();corridorRaid=Long.MIN_VALUE;probesThisRaid=0;}
    static int probesThisRaid(){return probesThisRaid;}
    private static Corridor probe(ServerLevel l,CampaignData d,int wedge){
        int from=d.radius()+PROBE_FROM,to=Math.max(from,farthest(l,d)),n=(to-from)/PROBE_STEP+1;int[] surface=new int[n];boolean[] hazard=new boolean[n];
        double angle=(wedge+.5)*WEDGE;var placed=BaseScoring.Ledger.get(l).placed;
        for(int i=0;i<n;i++){
            var column=ringAt(d.beacon,from+i*PROBE_STEP,angle);surface[i]=UNKNOWN;
            if(!l.hasChunkAt(column))continue; // never load a chunk for a probe
            int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());var top=new BlockPos(column.getX(),y-1,column.getZ());var state=l.getBlockState(top);
            if(!l.getFluidState(top).isEmpty()||state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)){hazard[i]=true;surface[i]=y;continue;}
            String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if(placed.contains(top.asLong())||id.endsWith("_log")||id.endsWith("_wood")||id.endsWith("_stem")||id.endsWith("_hyphae"))continue; // a trunk or a player's block is not the ground
            surface[i]=y;
        }
        return new Corridor(from,surface,hazard);
    }

    /**
     * Where a reinforcement appears. Rings are tried from the outer edge of the active chunks inwards, as before; on each ring the twelve
     * wedges are tried from a random one at a random angle. Candidates in wedges with a clean corridor come first, then those with the fewest
     * violations; every candidate is checked at most once, so the worst case costs what it always did.
     */
    static BlockPos find(ServerLevel l,CampaignData d){
        int minimum=d.radius()+CLEARANCE,farthest=farthest(l,d);
        record Candidate(BlockPos column,Score score){}
        List<Candidate> all=new ArrayList<>();
        for(int distance=farthest;distance>=minimum;distance=Math.max(minimum,distance-16)){
            int first=l.random.nextInt(WEDGES);double jitter=l.random.nextDouble()*WEDGE;
            for(int i=0;i<WEDGES;i++){
                var column=ringAt(d.beacon,distance,((first+i)%WEDGES)*WEDGE+jitter);
                all.add(new Candidate(column,corridor(l,d,wedgeOf(d.beacon,column)).score(distance)));
            }
            if(distance==minimum||all.size()>=240)break;
        }
        var order=new ArrayList<>(all.stream().filter(c->c.score().clean()).toList());
        order.addAll(all.stream().filter(c->!c.score().clean()).sorted(Comparator.comparingInt(c->c.score().violations())).toList());
        for(var c:order){
            if(!l.hasChunkAt(c.column()))continue;
            var p=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,c.column());if(safe(l,d,p)){WARNINGS.remove(l);return p;}
        }
        long now=l.getGameTime();if(d.waveTicks>=600&&now-WARNINGS.getOrDefault(l,now-600)>=600){WARNINGS.put(l,now);l.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("[Create Arsenal] Reinforcements are waiting for clear, loaded outdoor ground at least 64 blocks beyond the protected boundary. They will not spawn closer or inside buildings."),false);}return null;
    }

    // ---- rescue: a new place for a raider that cannot get to the beacon ------------------------------------------------------------
    /** Pure: nobody (in {@code players}) is closer than {@code min} blocks to {@code p}. */
    static boolean farFromPlayers(net.minecraft.world.phys.Vec3 p,Collection<net.minecraft.world.phys.Vec3> players,double min){
        for(var q:players)if(q.distanceToSqr(p)<min*min)return false;return true;
    }
    /** Pure: the order wedges are tried in for a rescue: every wedge but the one the raider was stuck in (starting at {@code start}), then that one. */
    static List<Integer> rescueOrder(int stuckWedge,int start){
        var order=new ArrayList<Integer>();for(int i=0;i<WEDGES;i++){int w=Math.floorMod(start+i,WEDGES);if(w!=stuckWedge)order.add(w);}order.add(stuckWedge);return order;
    }
    /** Pure: the first place {@code attempt} yields, trying the wedges in {@link #rescueOrder}; null when no wedge yields one. */
    static <T> T pickRescue(int stuckWedge,int start,java.util.function.IntFunction<T> attempt){
        for(int w:rescueOrder(stuckWedge,start)){T found=attempt.apply(w);if(found!=null)return found;}return null;
    }
    /**
     * A place for a stuck raider: it passes every rule a fresh spawn does ({@link #safe}: outside the zone and its {@link #CLEARANCE}, loaded and
     * entity-ticking, inside the world border, at or above the beacon's level, open and level ground) and its wedge has a clean corridor,
     * and it is at least {@link #RESCUE_PLAYER_DISTANCE} blocks from every player. It lies on the ring at the minimum distance plus the usual
     * spread, in another wedge than the one the raider was stuck in when any other will do. Null when nothing qualifies.
     */
    static BlockPos findRescue(ServerLevel l,CampaignData d,int stuckWedge,Collection<net.minecraft.world.phys.Vec3> players){
        return pickRescue(stuckWedge,l.random.nextInt(WEDGES),wedge->{
            for(int tries=0;tries<RESCUE_TRIES;tries++){
                int extra=l.random.nextInt(SPREAD+1);int distance=d.radius()+CLEARANCE+extra;
                var column=ring(d.beacon,d.radius(),(wedge+l.random.nextDouble())*WEDGE,extra);
                if(wedgeOf(d.beacon,column)!=wedge||!l.hasChunkAt(column))continue;
                if(!corridor(l,d,wedge).score(distance).clean())return null; // the whole wedge is no good
                var p=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column);
                if(!farFromPlayers(net.minecraft.world.phys.Vec3.atBottomCenterOf(p),players,RESCUE_PLAYER_DISTANCE))continue;
                if(safe(l,d,p))return p;
            }
            return null;
        });
    }
}
