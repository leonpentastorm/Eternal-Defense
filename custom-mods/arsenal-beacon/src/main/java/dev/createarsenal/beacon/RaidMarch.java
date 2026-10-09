package dev.createarsenal.beacon;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Raiders march straight at the beacon and only stop to dig when something blocks them. This class tells which ones are blocked
 * for a second ({@link #idle}) and which ones have made no real headway for a long time ({@link Track}, the stuck tracker).
 * A stuck raider, or one that has walked up to lava, a chasm or a wide lake, opens a red gate, channels beside it for a few seconds
 * (it can be shot) and then reappears at the edge of the staging ring; after {@link #MAX_GATES} gates it is withdrawn, so one
 * unreachable mob can never decide a raid. The decisions are pure static functions; the world-touching parts live in
 * {@link RaidRescue} (the once-a-second check) and {@link RaiderGates} (gates and channels).
 */
final class RaidMarch {
    private RaidMarch(){}
    /** A siege creeper only digs within this many blocks of the protected zone. */
    static final int DIG_RANGE=24;
    /** Less than this many blocks of progress in one second counts as being blocked. */
    static final double MIN_PROGRESS=1.0;

    // ---- the sustained-progress tracker ------------------------------------------------------------------------------------------
    /** A raider has to get this much closer (3D distance to the beacon) to count as having made progress. */
    static final double PROGRESS_BLOCKS=3;
    /** No progress for this long, and not busy: the raider is stuck. Busy time does not count (the clock restarts while it is busy). */
    static final int STUCK_SECONDS=25;
    /** A rescued raider cannot be flagged again for this long. */
    static final int GRACE_SECONDS=10;
    /** Breaking or damaging a block makes a raider busy for this long. */
    static final int BREACH_BUSY_SECONDS=5;
    /** A raider opens this many gates before it is withdrawn (a raid boss opens as many as it needs). */
    static final int MAX_GATES=3;
    /** A raider that is stuck and for which no rescue place was found this many times in a row is withdrawn as well (never a boss). */
    static final int DRY_LIMIT=3;
    /** A ranged raider holding its firing distance is busy within this many blocks of its target (the distance {@code needsBreach} uses). */
    static final double FIRING_DISTANCE=14;
    /** A raider that a player this close has just hurt is being fought, not stuck (raiders hand their target to players within 5 blocks). */
    static final double ENGAGED_RADIUS=12;
    /** Persistent-data key of how many gates a raider has opened (survives chunk reloads). */
    static final String GATES="arsenalGates";

    // ---- raider gates ------------------------------------------------------------------------------------------------------------
    /** How far ahead of a raider, in blocks along the straight line to the beacon, the terrain is looked at (one sample per block). */
    static final int LOOKAHEAD_BLOCKS=10;
    /** Terrain a raider cannot cross: a drop of this many blocks between two neighbouring samples ... */
    static final int GATE_DROP_BLOCKS=6;
    /** ... or surface fluid on this many blocks in a row (a pond a zombie can wade through is shorter). Lava and magma always count. */
    static final int GATE_FLUID_SPAN=8;
    /** Distance between two samples when a destination's whole way to the zone is checked with the same probe. */
    static final int DESTINATION_SPACING=2;
    /** A raider channels this long beside its gate before it reappears. */
    static final int CHANNEL_SECONDS=8;
    /** Each time the channeler is hurt (checked once a second) the channel takes this much longer ... */
    static final int DAMAGE_DELAY_SECONDS=3;
    /** ... but the total delay never exceeds this, so a tough raider under long-range fire cannot stall the wave into the raid timeout. */
    static final int MAX_DELAY_SECONDS=10;
    /** The destination shows particles and a portal sound this long before the raider appears. */
    static final int TELEGRAPH_SECONDS=3;
    /** A stuck raider within this many blocks of an existing gate uses that gate; a gate has room for this many channelers, the rest wait. */
    static final double GATE_JOIN_RADIUS=8;
    static final int GATE_CAPACITY=8;
    static final long TICKS_PER_SECOND=20;

    /**
     * The tracker entry of one raider. {@code best} is the distance at the last accepted progress (not the lowest distance ever seen:
     * the raider has to get {@link #PROGRESS_BLOCKS} closer than this, so a slow creep still counts when it adds up). {@code improvedAt}
     * is when that happened, or when the raider last stopped being busy; {@code graceUntil} defers the next flag after a rescue or a
     * dry attempt; {@code dry} counts rescue attempts that found no place.
     */
    record Track(double best,long improvedAt,long graceUntil,int dry){}

    /** Pure: the first sighting of a raider at {@code distance}. */
    static Track start(double distance,long now){return new Track(distance,now,0,0);}

    /**
     * Pure: one observation (called about once a second). Real progress restarts the stall clock. So does being busy: a raider that is
     * fighting is not stalling, and when it stops it gets the whole {@link #STUCK_SECONDS} again before it is flagged.
     */
    static Track observe(Track t,double distance,long now,boolean busy){
        if(t==null)return start(distance,now);
        if(distance<=t.best()-PROGRESS_BLOCKS)return new Track(distance,now,t.graceUntil(),0);
        if(busy)return new Track(t.best(),now,t.graceUntil(),t.dry());
        return t;
    }

    /** Pure: no progress for {@link #STUCK_SECONDS}, outside the grace window, and not busy right now. */
    static boolean stuck(Track t,long now,boolean busy){
        return t!=null&&!busy&&now>=t.graceUntil()&&now-t.improvedAt()>=STUCK_SECONDS*TICKS_PER_SECOND;
    }

    /** Pure: the entry after a raider came through its gate: new distance, a fresh stall clock and a {@link #GRACE_SECONDS} grace window. */
    static Track rescued(double newDistance,long now){return new Track(newDistance,now,now+GRACE_SECONDS*TICKS_PER_SECOND,0);}

    /** Pure: the entry after a stuck raider found no place to go: look again after the grace window. */
    static Track dry(Track t,long now){return new Track(t.best(),t.improvedAt(),now+GRACE_SECONDS*TICKS_PER_SECOND,t.dry()+1);}

    // ---- busy ----------------------------------------------------------------------------------------------------------------------
    /** Pure: close enough to hit the target, and it can see it. */
    static boolean inMeleeReach(double distance,double reach,boolean lineOfSight){return lineOfSight&&distance<=reach;}
    /** Pure: a ranged raider standing where it can shoot. */
    static boolean holdingRange(boolean ranged,double distance,boolean lineOfSight){return ranged&&lineOfSight&&distance<=FIRING_DISTANCE;}
    /**
     * Pure: a defender is fighting the raider: a player is within {@link #ENGAGED_RADIUS} and something hurt the raider in the last five
     * seconds. A player merely standing nearby does not count, or a bystander next to an unreachable raider would keep the raid waiting.
     */
    static boolean fought(boolean playerNear,boolean hurtRecently){return playerNear&&hurtRecently;}
    /** Pure: it broke or damaged a block within the last {@link #BREACH_BUSY_SECONDS} ({@code lastBreach<0} means never). */
    static boolean breachedRecently(long lastBreach,long now){return lastBreach>=0&&now-lastBreach<=BREACH_BUSY_SECONDS*TICKS_PER_SECOND;}
    /**
     * Pure: any of the reasons a raider that is not advancing is not stuck. {@code defenderNear}: a player within {@link #ENGAGED_RADIUS}
     * (raiders hand their target to a player within 5 blocks, so a raider in a fight is not marching). {@code frozen}: its chunk is not
     * entity-ticking, so nothing about it can be judged.
     */
    static boolean busy(boolean inMeleeReach,boolean holdingRange,boolean breachedRecently,boolean defenderNear,boolean frozen){
        return inMeleeReach||holdingRange||breachedRecently||defenderNear||frozen;
    }

    // ---- the escalation ladder -----------------------------------------------------------------------------------------------------
    enum Step{RESCUE,WITHDRAW,RETRY}
    /** Pure: what happens to a raider that is stuck, given how many gates it has opened already. Bosses are never withdrawn. */
    static Step next(int gates,boolean boss){return boss||gates<MAX_GATES?Step.RESCUE:Step.WITHDRAW;}
    /** Pure: what happens when a stuck raider that should go through a gate found no destination: try again later, or give up on it (never a boss). */
    static Step afterNoDestination(int dryAttempts,boolean boss){return !boss&&dryAttempts>=DRY_LIMIT?Step.WITHDRAW:Step.RETRY;}

    /** Pure: the chat line for the raiders withdrawn in one wave. */
    static String withdrawnMessage(int count){return count+(count==1?" stuck raider withdrew":" stuck raiders withdrew");}

    // ---- state (server thread only; transient) --------------------------------------------------------------------------------------
    private static final Map<UUID,Vec3> LAST=new HashMap<>();
    private static final Map<UUID,Track> TRACKS=new HashMap<>();
    private static final Map<UUID,Long> BREACHES=new HashMap<>(),FALLBACKS=new HashMap<>();
    private static int withdrawn;

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
    static void breached(Mob mob,long now){if(mob.getPersistentData().getBoolean("arsenalRaider"))BREACHES.put(mob.getUUID(),now);}
    static long lastBreach(UUID id){return BREACHES.getOrDefault(id,-1L);}
    /** {@code BeaconCombat.approach} had no usable path and pushed the raider straight at its goal (the log tells when). */
    static void forcedMove(Mob mob,long now){if(mob.getPersistentData().getBoolean("arsenalRaider"))FALLBACKS.put(mob.getUUID(),now);}
    static long lastForcedMove(UUID id){return FALLBACKS.getOrDefault(id,-1L);}
    static void withdrew(){withdrawn++;}
    /** The number of raiders withdrawn since the last call (the chat line is sent once per wave). */
    static int takeWithdrawn(){int n=withdrawn;withdrawn=0;return n;}
    /** Drops everything kept for raiders that are gone: dead, withdrawn, unloaded or removed. Called every cycle with the live raiders. */
    static void prune(Collection<UUID> alive){
        LAST.keySet().retainAll(alive);TRACKS.keySet().retainAll(alive);BREACHES.keySet().retainAll(alive);FALLBACKS.keySet().retainAll(alive);
    }
    /** A raid starts or ends, the server stops, the campaign is packed up. */
    static void forget(){LAST.clear();TRACKS.clear();BREACHES.clear();FALLBACKS.clear();withdrawn=0;}
    /** For tests: how many entries are kept. */
    static int kept(){return LAST.size()+TRACKS.size()+BREACHES.size()+FALLBACKS.size();}
}
