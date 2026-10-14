package dev.eternaldefense.beacon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Tactical Operations: the numbers and pure decisions of the hunting missions, testable without Minecraft. */
final class TacticalRules {
    private TacticalRules(){}

    // The Satellite Beacon
    static final int MAX_MK=3;
    /** Offers on the board and the reach of the satellite (blocks from the beacon), by Mk (I, II, III). */
    static final int[] OFFERS={2,3,4},RANGE={256,512,1024};
    static int mk(int mk){return Math.max(1,Math.min(MAX_MK,mk));}
    static int offers(int mk){return OFFERS[mk(mk)-1];}
    static int range(int mk){return RANGE[mk(mk)-1];}
    /** Contract classes the satellite can find at this Mk: Patrol from Mk I, Garrison from Mk II, Warlord at Mk III. */
    static List<Kind> classes(int mk){var out=new ArrayList<Kind>();for(var k:Kind.values())if(k.fromMk<=mk(mk))out.add(k);return out;}

    /** A contract class: its warband size (before extra players) and its pay as a share of a raid's guaranteed */
    enum Kind {
        PATROL("patrol",1,8,12,.5),GARRISON("garrison",2,14,20,1.0),WARLORD("warlord",3,10,14,1.75);
        final String id;final int fromMk,min,max;final double budget;
        Kind(String id,int fromMk,int min,int max,double budget){this.id=id;this.fromMk=fromMk;this.min=min;this.max=max;this.budget=budget;}
        static Kind of(String id){for(var k:values())if(k.id.equals(id))return k;return PATROL;}
    }

    // Uplink
    /** The satellite must stand within this many blocks (straight line) of the table. */
    static final int UPLINK_DISTANCE=8;
    /** Pure: is there an uplink? */
    static boolean uplink(double distanceSquared,boolean satelliteInZone,boolean openSky){return distanceSquared<=UPLINK_DISTANCE*UPLINK_DISTANCE&&satelliteInZone&&openSky;}

    // The board
    /** An objective lies at least this far beyond the edge of the beacon zone */
    static final int BEYOND_ZONE=150;static final double SEPARATION=40;
    static int minDistance(int zoneRadius){return zoneRadius+BEYOND_ZONE;}
    /** Pure: difference of two compass bearings in degrees, 0 to 180. */
    static double angle(double a,double b){double d=Math.abs(((a-b)%360+360)%360);return d>180?360-d:d;}
    static boolean separated(double bearing,List<Double> others){for(double o:others)if(angle(bearing,o)<SEPARATION)return false;return true;}
    /** Compass bearing (0 north, 90 east) from (fromX, fromZ) to (toX, toZ). */
    static double bearing(double fromX,double fromZ,double toX,double toZ){double b=Math.toDegrees(Math.atan2(toX-fromX,-(toZ-fromZ)));return b<0?b+360:b;}
    /** The eight-point compass name of a bearing: N, NE, E... */
    static String compass(double bearing){String[] n={"N","NE","E","SE","S","SW","W","NW"};return n[(int)Math.floor(((bearing%360+360)%360+22.5)/45)%8];}
    /** A placed objective. */
    record Spot(int x,int z,double bearing,int distance){}
    /** Something that says whether a column is inside the world border (pure tests pass a lambda). */
    interface Inside{boolean test(int x,int z);}
    /** Pure: an objective for a Patrol or Warlord: a random bearing at least SEPARATION degrees from the bearings */
    static Spot place(Random random,int beaconX,int beaconZ,int zoneRadius,int range,List<Double> taken,Inside inside){
        int min=minDistance(zoneRadius),max=Math.max(min,range);
        for(int attempt=0;attempt<64;attempt++){
            double bearing=random.nextDouble()*360;if(!separated(bearing,taken))continue;
            int distance=min+random.nextInt(max-min+1);double r=Math.toRadians(bearing);
            int x=beaconX+(int)Math.round(Math.sin(r)*distance),z=beaconZ-(int)Math.round(Math.cos(r)*distance);
            if(inside.test(x,z))return new Spot(x,z,bearing,distance);
        }
        return null;
    }
    /** Pure: a structure at (x, z) qualifies as a Garrison objective (distance window, bearing separation, border). */
    static Spot garrison(int beaconX,int beaconZ,int zoneRadius,int range,int x,int z,List<Double> taken,Inside inside){
        double distance=Math.hypot(x-beaconX,z-beaconZ),bearing=bearing(beaconX,beaconZ,x,z);
        if(distance<minDistance(zoneRadius)||distance>range||!separated(bearing,taken)||!inside.test(x,z))return null;
        return new Spot(x,z,bearing,(int)Math.round(distance));
    }

    /** Pure: one well-mixed 64-bit number from several (SplitMix64's finalizer over each in turn) */
    static long mix(long... parts){
        long h=0x9E3779B97F4A7C15L;
        for(long p:parts){h^=p;h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;h=(h^(h>>>27))*0x94D049BB133111EBL;h^=h>>>31;h+=0x9E3779B97F4A7C15L;}
        return h;
    }

    // Codenames
    static final String[] FIRST={"IRON","STEEL","SILENT","BLACK","CRIMSON","GRANITE","AMBER","COPPER","COBALT","ASHEN","FROST","THUNDER","BROKEN","HOLLOW","SCARLET","GHOST","WINTER","RUSTED","SABLE","NORTHERN"};
    static final String[] SECOND={"HOUND","LANCE","ANVIL","WOLF","TALON","HAMMER","SPEAR","RAVEN","BASTION","SHIELD","FALCON","VIPER","MANTIS","GLACIER","EMBER","SENTINEL","BADGER","COBRA","TEMPEST","ARROW"};
    /** Pure: the operation codename of a seed, always the same for the same seed ("OP IRON HOUND"). */
    static String codename(long seed){
        long a=seed*0x9E3779B97F4A7C15L;a^=a>>>29;long b=(seed+0x632BE59BD9B4E019L)*0xBF58476D1CE4E5B9L;b^=b>>>31;
        return "OP "+FIRST[(int)Math.floorMod(a,(long)FIRST.length)]+" "+SECOND[(int)Math.floorMod(b,(long)SECOND.length)];
    }

    // The raid lock
    /** No mission can be accepted in the last day before a raid is due (the raid warning window). */
    static final int WARNING_TICKS=24000;
    /** Ticks until the next raid is due (the same count the beacon's status shows) */
    static long untilRaid(int rewardTier,long preparationTicks,long respiteTicks){return Math.max(0,Rules.intervalDays(rewardTier)*24000L-preparationTicks)+Math.max(0,respiteTicks);}
    static boolean raidWarning(long untilRaid){return untilRaid<=WARNING_TICKS;}
    /** Pure: why a mission cannot be accepted now (a language key suffix), or null. */
    static String acceptBlocked(boolean uplink,boolean raidActive,long untilRaid,boolean missionRunning){
        if(!uplink)return "no_uplink";
        if(raidActive)return "raid";
        if(raidWarning(untilRaid))return "raid_warning";
        if(missionRunning)return "busy";
        return null;
    }

    // Dawn
    static long day(long dayTime){return Math.floorDiv(dayTime,24000L);}
    /** Pure: how many new offers come at this tick: one at the first tick of a new day when the board is short */
    static int dawnOffers(long lastDay,long today,int missing){return today>lastDay&&missing>0?1:0;}

    // Warbands
    static final int EXTRA_PER_PLAYER=2,EXTRA_MAX=8;
    /** Pure: how many mobs a warband has (roll any int): the class range, plus two for every extra online player */
    static int warband(Kind kind,int roll,int onlinePlayers){
        int base=kind.min+Math.floorMod(roll,kind.max-kind.min+1);
        return base+Math.min(EXTRA_MAX,EXTRA_PER_PLAYER*Math.max(0,onlinePlayers-1));
    }
    /** Arrival: the warband needs one block to stand on. */
    static final int MINI_ZONE=6,RING_FROM=MINI_ZONE+2,RING_TO=MINI_ZONE+6,ANCHOR_SEARCH=16,RETURN_GATE_TICKS=6000;
    /** A player within this many blocks of the objective brings the warband */
    static final int APPROACH=64,SPAWN_RADIUS=ANCHOR_SEARCH+RING_TO,LEASH=32;
    /** Pure: the columns around an objective within radius, nearest first (ties in a fixed order), as {dx, dz} */
    static List<int[]> anchorOrder(int radius){
        var out=new ArrayList<int[]>();
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(dx*dx+dz*dz<=radius*radius)out.add(new int[]{dx,dz});
        out.sort(Comparator.<int[]>comparingInt(o->o[0]*o[0]+o[1]*o[1]).thenComparingInt(o->o[0]).thenComparingInt(o->o[1]));
        return out;
    }
    /** Pure: a column on the spawn ring around the anchor */
    static int[] ringOffset(double angle,double t){double r=RING_FROM+Math.max(0,Math.min(1,t))*(RING_TO-RING_FROM);return new int[]{(int)Math.round(Math.cos(angle)*r),(int)Math.round(Math.sin(angle)*r)};}
    /** An engaged warband with no player within AWAY blocks for AWAY_TICKS scatters */
    static final int AWAY=128,AWAY_TICKS=6000;
    static final int CAPTAIN_HEALTH_FACTOR=2;
    /** Pure: the danger shown on an offer card, 1 (low) to 4 (extreme) */
    static int danger(Kind kind,int rewardTier){return Math.min(4,kind.ordinal()+1+(rewardTier>=5?1:0));}

    // The scan
    /** Both images are 256 x 256: Base is +-128 blocks (one block a pixel), Region is +-range. */
    static final int SCAN_SIZE=256,BASE_HALF=128,ROWS_PER_TICK=2,SCAN_COOLDOWN_TICKS=600,BIOME_STEP=4;
    /** Blocks per pixel of the Region image at this Mk (2, 4 or 8). */
    static int regionScale(int mk){return Math.max(1,2*range(mk)/SCAN_SIZE);}

    // The mission map
    /** Blocks kept between the beacon or the red X and the edge of the mission map. */
    static final int MAP_MARGIN=16;
    /** Pure: the centre of the mission map on one axis, halfway between the beacon and the objective. */
    static int mapCentre(int beacon,int objective){return Math.floorDiv(beacon+objective,2);}
    /** Pure: the scale (0 to 4) of the mission map: the smallest at which the beacon and the objective both lie */
    static int mapScale(int beaconX,int beaconZ,int x,int z){
        int cx=mapCentre(beaconX,x),cz=mapCentre(beaconZ,z);
        int reach=Math.max(Math.max(Math.abs(beaconX-cx),Math.abs(x-cx)),Math.max(Math.abs(beaconZ-cz),Math.abs(z-cz)))+MAP_MARGIN;
        for(int s=0;s<=4;s++)if(reach<=64<<s)return s;
        return 4;
    }
}
