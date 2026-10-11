package dev.createarsenal.beacon;

import java.util.Collection;
import java.util.Locale;

/**
 * Forward Operating Bases (0.0.23, slice 1): the pure rules, without Minecraft types, so they can be unit tested. A FOB Beacon planted in a
 * vanilla village starts a low vanilla raid; winning it makes the village an outpost of the base (see {@link Fobs}).
 */
final class FobRules {
    private FobRules(){}
    /** At most this many FOB Beacons per beacon, whatever their state (owner decision). */
    static final int MAX_FOBS=5;
    /** A village needs at least this many villagers in it to be taken. */
    static final int MIN_VILLAGERS=3;
    /** The FOB zone is searched at most this many sections (16 blocks each) from the FOB Beacon sideways, and this many up and down. */
    static final int ZONE_REACH=6,ZONE_REACH_Y=3;
    /** Vanilla's own village test ({@code ServerLevel.isVillage}): a section within this many sections of one holding a bed, workstation or bell. */
    static final int VILLAGE_SECTIONS=1;
    /** The zone, the villager tags and the raid are looked at once a second; the zone is worked out again at most this often (ticks). */
    static final int ZONE_REFRESH_TICKS=100;
    /** A name is at most this long (a renamed Name Tag can hold 50). */
    static final int NAME_MAX=32;

    /** PLANTED until its first raid is won, then ACTIVE; DOWN when a raid at it is lost. */
    enum State{PLANTED,ACTIVE,DOWN;
        static State of(int ordinal){var v=values();return v[Math.max(0,Math.min(v.length-1,ordinal))];}
        String id(){return name().toLowerCase(Locale.ROOT);}
    }

    // ---- planting -------------------------------------------------------------------------------------------------------------------------
    /**
     * Pure: why a FOB Beacon cannot be planted here, as a key under {@code fob.refused.}, or null. Checked in this order: no beacon, five
     * already placed, raids impossible (Peaceful or the raid game rule off), not in a village, the village already has one, too few villagers.
     */
    static String plantProblem(boolean beacon,int placed,boolean raidsPossible,boolean village,boolean taken,int villagers){
        if(!beacon)return "no_beacon";
        if(placed>=MAX_FOBS)return "max";
        if(!raidsPossible)return "no_raids";
        if(!village)return "not_village";
        if(taken)return "taken";
        if(villagers<MIN_VILLAGERS)return "villagers";
        return null;
    }

    // ---- the raid at a FOB --------------------------------------------------------------------------------------------------------------------
    /** Pure: the FOB's raid is lost when fewer than half of the villagers living there when it began are still alive (the owner's default rule). */
    static boolean raidFailed(int villagersAtStart,int alive){return villagersAtStart>0&&alive*2<villagersAtStart;}
    /** How a vanilla raid at a FOB ended, read from its status (or its absence) once a second. */
    enum Outcome{RUNNING,WON,LOST,NONE}
    /** Pure: {@code exists} false means the raid is gone from the level's raid list without our having seen a result (stopped, timed out). */
    static Outcome outcome(boolean exists,boolean victory,boolean loss,boolean stopped){
        if(!exists)return Outcome.NONE;
        if(victory)return Outcome.WON;
        if(loss)return Outcome.LOST;
        return stopped?Outcome.NONE:Outcome.RUNNING;
    }
    /** Pure: the state after a raid at the FOB ends: won makes it ACTIVE, lost makes it DOWN, no result leaves it as it was. */
    static State after(State state,Outcome outcome){
        return switch(outcome){case WON->State.ACTIVE;case LOST->State.DOWN;default->state;};
    }
    /** Pure: the shop, the meal bonus and (later) tribute and enchant levels only work while the FOB is ACTIVE. */
    static boolean working(State state){return state==State.ACTIVE;}
    /** Pure: a raid can be called at the FOB when it is PLANTED (not yet won) and none is running. */
    static boolean mayCallRaid(State state,boolean raidRunning){return state==State.PLANTED&&!raidRunning;}

    // ---- names --------------------------------------------------------------------------------------------------------------------------------
    /** Pure: the generated name of a FOB id, always the same for the same id ("FOB IRON HOUND"), from the hunt codename words. */
    static String codename(long id){
        long seed=TacticalRules.mix(id,0x464F42L);   // "FOB": not the same words as the hunt with the same number
        long a=seed*0x9E3779B97F4A7C15L;a^=a>>>29;long b=(seed+0x632BE59BD9B4E019L)*0xBF58476D1CE4E5B9L;b^=b>>>31;
        return "FOB "+TacticalRules.FIRST[(int)Math.floorMod(a,(long)TacticalRules.FIRST.length)]+" "+TacticalRules.SECOND[(int)Math.floorMod(b,(long)TacticalRules.SECOND.length)];
    }
    /** Pure: a generated name not used by another FOB of this beacon: the id's codename, or the next id's that is free (deterministic). */
    static String freeCodename(long id,Collection<String> taken){
        for(long k=0;k<400;k++){String name=codename(id+k*1_000_003L);if(!contains(taken,name))return name;}
        return "FOB "+id;
    }
    /** Pure: why a name cannot be used (a key under {@code fob.rename.}), or null: empty, too long, or already another FOB's (case-insensitive). */
    static String nameProblem(String name,Collection<String> others){
        String n=clean(name);
        if(n.isEmpty())return "empty";
        if(n.length()>NAME_MAX)return "too_long";
        if(contains(others,n))return "duplicate";
        return null;
    }
    /** Pure: the name as it is kept: trimmed, inner runs of spaces made one. */
    static String clean(String name){return name==null?"":name.trim().replaceAll("\\s+"," ");}
    static boolean contains(Collection<String> names,String name){for(String n:names)if(n.equalsIgnoreCase(clean(name)))return true;return false;}
}
