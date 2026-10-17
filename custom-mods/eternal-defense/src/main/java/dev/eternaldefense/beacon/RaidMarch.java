package dev.eternaldefense.beacon;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Raiders march straight at the beacon and only stop to dig when something blocks them. */
final class RaidMarch {
    private RaidMarch(){}
    /** A siege creeper only digs within this many blocks of the protected zone. */
    static final int DIG_RANGE=24;
    /** Less than this many blocks of progress in one second counts as being blocked. */
    static final double MIN_PROGRESS=1.0;

    // The sustained-progress tracker
    /** A raider has to get this much closer (3D distance to the beacon) to count as having made progress. */
    static final double PROGRESS_BLOCKS=3;
    /** No progress for this long, and not busy: the raider is stuck. */
    static final int STUCK_SECONDS=25;
    /** A rescued raider cannot be flagged again for this long. */
    static final int GRACE_SECONDS=10;
    /** Breaking or damaging a block makes a raider busy for this long. */
    static final int BREACH_BUSY_SECONDS=5;
    /** A raider opens this many gates before it joins the parachute squadron (a raid boss opens as many as it needs). */
    static final int MAX_GATES=3;
    /** A raider that is stuck and for which no rescue place was found this many times in a row joins the squadron as well (a boss too) */
    static final int DRY_LIMIT=3;
    /** A raider stuck within this many blocks of the zone's edge, with the beacon this much higher, drops at once: a gate would only send it back out */
    static final int DROP_NEAR=8,DROP_RISE=4;
    /** A player fighting a raider counts only this much higher or lower (one on a tower out of reach does not keep it busy) */
    static final double ENGAGED_RISE=4;
    /** A ranged raider holding its firing distance is busy within this many blocks of its target */
    static final double FIRING_DISTANCE=14;
    /** A raider that a player this close has just hurt is being fought */
    static final double ENGAGED_RADIUS=12;
    /** Persistent-data keys: how many gates a raider has opened, and how often it was dropped (survive chunk reloads). */
    static final String GATES="eternalGates",DROPS="eternalDrops";

    // Raider gates
    /** Blocks of terrain checked ahead of a raider, one sample per block. */
    static final int LOOKAHEAD_BLOCKS=10;
    /** Impassable drop between two neighbouring samples, in blocks. */
    static final int GATE_DROP_BLOCKS=6;
    /** Impassable run of surface fluid, in blocks; lava and magma always count. */
    static final int GATE_FLUID_SPAN=8;
    /** Distance between two samples when a destination's whole way to the zone is checked with the same probe. */
    static final int DESTINATION_SPACING=2;
    /** A raider channels this long beside its gate before it reappears. */
    static final int CHANNEL_SECONDS=8,EXIT_LINGER_SECONDS=2;
    /** Channel delay added each time the channeler is hurt (checked once a second). */
    static final int DAMAGE_DELAY_SECONDS=3;
    /** Cap on the total channel delay. */
    static final int MAX_DELAY_SECONDS=10;
    /** The destination shows particles and a portal sound this long before the raider appears. */
    static final int TELEGRAPH_SECONDS=3;
    /** A stuck raider within this many blocks of an existing gate uses that gate */
    static final double GATE_JOIN_RADIUS=8;
    static final int GATE_CAPACITY=8;
    static final long TICKS_PER_SECOND=20;

    // Swimming
    /** A raider in deep water that would swim longer than this to land toward its target opens a gate at once */
    static final int SWIM_SECONDS=5;
    /** Blocks of water looked at toward the target; no land within them counts as this many */
    static final int SWIM_LOOK=32;
    /** Vanilla water movement: 0.02 of the move speed a tick against 0.8 drag, so at best 1.6 blocks a second for each unit of speed */
    static final double SWIM_PER_SPEED=1.6;
    /** Pure: seconds to swim across this much water at this move speed (times the swim speed) */
    static double swimSeconds(int water,double speed){return Math.max(0,water)/(Math.max(.01,speed)*SWIM_PER_SPEED);}
    /** Pure: the swim is long enough to go through a gate at once */
    static boolean swimTooLong(double seconds){return seconds>SWIM_SECONDS;}
    /** Pure: in the water for longer than SWIM_SECONDS already (since the tick it was first seen swimming) */
    static boolean swumTooLong(long since,long now){return since>=0&&now-since>SWIM_SECONDS*TICKS_PER_SECOND;}
    /** Pure: a raider may be sent through a gate again (outside the grace window after its last one) */
    static boolean graceOver(Track t,long now){return t==null||now>=t.graceUntil();}

    /** The tracker entry of one raider. */
    record Track(double best,long improvedAt,long graceUntil,int dry){}

    /** Pure: the first sighting of a raider at distance. */
    static Track start(double distance,long now){return new Track(distance,now,0,0);}

    /** Pure: one observation (called about once a second). */
    static Track observe(Track t,double distance,long now,boolean busy){
        if(t==null)return start(distance,now);
        if(distance<=t.best()-PROGRESS_BLOCKS)return new Track(distance,now,t.graceUntil(),0);
        if(busy)return new Track(t.best(),now,t.graceUntil(),t.dry());
        return t;
    }

    /** Pure: no progress for STUCK_SECONDS, outside the grace window, and not busy right now. */
    static boolean stuck(Track t,long now,boolean busy){
        return t!=null&&!busy&&now>=t.graceUntil()&&now-t.improvedAt()>=STUCK_SECONDS*TICKS_PER_SECOND;
    }

    /** Pure: the entry after a raider came through its gate */
    static Track rescued(double newDistance,long now){return new Track(newDistance,now,now+GRACE_SECONDS*TICKS_PER_SECOND,0);}

    /** Pure: the entry after a stuck raider found no place to go: look again after the grace window. */
    static Track dry(Track t,long now){return new Track(t.best(),t.improvedAt(),now+GRACE_SECONDS*TICKS_PER_SECOND,t.dry()+1);}

    // Busy
    /** Pure: close enough to hit the target, and it can see it. */
    static boolean inMeleeReach(double distance,double reach,boolean lineOfSight){return lineOfSight&&distance<=reach;}
    /** Pure: a ranged raider standing where it can shoot. */
    static boolean holdingRange(boolean ranged,double distance,boolean lineOfSight){return ranged&&lineOfSight&&distance<=FIRING_DISTANCE;}
    /** Pure: a defender is fighting the raider: a player is within ENGAGED_RADIUS and something hurt the raider */
    static boolean fought(boolean playerNear,boolean hurtRecently){return playerNear&&hurtRecently;}
    /** Pure: a player this much higher than the raider can be fought (see ENGAGED_RISE) */
    static boolean reachable(double rise){return Math.abs(rise)<=ENGAGED_RISE;}
    /** Pure: it broke or damaged a block within the last BREACH_BUSY_SECONDS (lastBreach<0 means never). */
    static boolean breachedRecently(long lastBreach,long now){return lastBreach>=0&&now-lastBreach<=BREACH_BUSY_SECONDS*TICKS_PER_SECOND;}
    /** Pure: any of the reasons a raider that is not advancing is not stuck. */
    static boolean busy(boolean inMeleeReach,boolean holdingRange,boolean breachedRecently,boolean defenderNear,boolean frozen){
        return inMeleeReach||holdingRange||breachedRecently||defenderNear||frozen;
    }

    // The escalation ladder
    enum Step{RESCUE,DROP,RETRY}
    /** Pure: what happens to a raider that is stuck, given how many gates it has opened already: a gate, or the parachute squadron. */
    static Step next(int gates,boolean boss){return boss||gates<MAX_GATES?Step.RESCUE:Step.DROP;}
    /** Pure: what happens when a stuck raider that should go through a gate found no destination (a boss too) */
    static Step afterNoDestination(int dryAttempts,boolean boss){return dryAttempts>=DRY_LIMIT?Step.DROP:Step.RETRY;}
    /** Pure: a raider stuck this far from the beacon (level), with the beacon this much higher, is dropped over it at once */
    static boolean dropNow(double horizontal,double rise,int zoneRadius){return horizontal<=zoneRadius+DROP_NEAR&&rise>=DROP_RISE;}

    // State (server thread only; transient)
    private static final Map<UUID,Vec3> LAST=new HashMap<>();
    private static final Map<UUID,Track> TRACKS=new HashMap<>();
    private static final Map<UUID,Long> BREACHES=new HashMap<>(),FALLBACKS=new HashMap<>(),SWIMS=new HashMap<>();
    private static int dropped;

    /** Called once a second per raider: true when it hardly moved since the last call. */
    static boolean idle(Mob mob){
        var now=mob.position();var before=LAST.put(mob.getUUID(),now);
        return before!=null&&now.distanceToSqr(before)<MIN_PROGRESS*MIN_PROGRESS;
    }
    /** Pure: is the progress of one second too small? */
    static boolean blocked(double movedBlocks){return movedBlocks<MIN_PROGRESS;}

    static Track track(UUID id){return TRACKS.get(id);}
    static void track(UUID id,Track t){TRACKS.put(id,t);}
    /** A raider broke or damaged a block just now. */
    static void breached(Mob mob,long now){if(mob.getPersistentData().getBoolean("eternalRaider"))BREACHES.put(mob.getUUID(),now);}
    static long lastBreach(UUID id){return BREACHES.getOrDefault(id,-1L);}
    /** BeaconCombat.approach had no usable path and pushed the raider straight at its goal (the log tells when). */
    static void forcedMove(Mob mob,long now){if(mob.getPersistentData().getBoolean("eternalRaider"))FALLBACKS.put(mob.getUUID(),now);}
    static long lastForcedMove(UUID id){return FALLBACKS.getOrDefault(id,-1L);}
    /** The tick a raider was first seen swimming this time (-1 when it is not swimming) */
    static long swimming(UUID id,boolean now,long tick){if(!now){SWIMS.remove(id);return -1;}return SWIMS.computeIfAbsent(id,k->tick);}
    static void dropped(){dropped++;}
    /** The number of raiders that joined the parachute squadron since the last call (chat says so once a second at most). */
    static int takeDropped(){int n=dropped;dropped=0;return n;}
    /** Drops everything kept for raiders that are gone: dead, unloaded or removed. */
    static void prune(Collection<UUID> alive){
        LAST.keySet().retainAll(alive);TRACKS.keySet().retainAll(alive);BREACHES.keySet().retainAll(alive);FALLBACKS.keySet().retainAll(alive);SWIMS.keySet().retainAll(alive);
    }
    /** A raid starts or ends, the server stops, the campaign is packed up. */
    static void forget(){LAST.clear();TRACKS.clear();BREACHES.clear();FALLBACKS.clear();SWIMS.clear();dropped=0;}
    /** For tests: how many entries are kept. */
    static int kept(){return LAST.size()+TRACKS.size()+BREACHES.size()+FALLBACKS.size()+SWIMS.size();}
}
