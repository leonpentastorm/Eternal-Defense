package dev.eternaldefense.beacon;

/** Forward Operating Bases: calls for help (SOS), DOWN and repairs */
final class SosRules {
    /** The answer starts the raid after this many seconds in the village with its villagers loaded. */
    static final int ARRIVE_SECONDS=3;
    private SosRules(){}

    // The SOS clock
    /** One clock for the whole server, in ticks of play (FobData.onlineTicks) */
    static final long SOS_MIN=720_000L,SOS_MAX=864_000L;
    /** Ticks of play before the next SOS, for roll in [0, 1); SOS_MIN to SOS_MAX inclusive. */
    static long delay(double random){
        double r=Math.max(0,Math.min(Math.nextDown(1.0),random));
        return Math.min(SOS_MAX,SOS_MIN+(long)Math.floor(r*(SOS_MAX-SOS_MIN+1)));
    }
    /** Pure: does the SOS clock count down this second? */
    static boolean counts(int activeFobs,boolean sosOpen,boolean homeRaid,boolean homeWarning){return activeFobs>0&&!sosOpen&&!homeRaid&&!homeWarning;}
    /** Index of the active FOB that calls, in id order, for roll in [0, 1); -1 if none. */
    static int pick(int active,double random){return active<=0?-1:(int)Math.max(0,Math.min(active-1,Math.floor(random*active)));}

    // The answer
    /** The SOS must be answered within 7 days of play (168,000 ticks). */
    static final long RESPONSE_TICKS=168_000L;
    /** Pure: does the response timer count down this second? */
    static boolean responseCounts(boolean homeRaid,boolean fobRaid){return !homeRaid&&!fobRaid;}
    /** The SOS raid's Bad Omen: level I (amplifier 0), the same as the activation raid */
    static final int OMEN_AMPLIFIER=0;
    /** What happens when a player stands in the village of the FOB that called. */
    enum Arrival{START,HOME_RAID,RAID_RUNNING,NO_RAIDS}
    /** Pure: start the raid now, or why not (the Home raid comes first; a raid already runs; raids are off). */
    static Arrival arrival(boolean homeRaid,boolean raidRunning,boolean raidsPossible){
        if(raidRunning)return Arrival.RAID_RUNNING;
        if(homeRaid)return Arrival.HOME_RAID;
        if(!raidsPossible)return Arrival.NO_RAIDS;
        return Arrival.START;
    }
    /** How an SOS ends: its raid won or lost, nobody came in time, or the FOB was taken back with the shovel. */
    enum End{WON,LOST,EXPIRED,REMOVED}
    /** Pure: the FOB's state after its SOS: won keeps it as it was (ACTIVE), lost or unanswered puts it DOWN. */
    static FobRules.State after(FobRules.State state,End end){return end==End.LOST||end==End.EXPIRED?FobRules.State.DOWN:state;}

    // The repair
    /** Pure: what bringing one lost villager back costs, by its level (1 Novice to 5 Master) */
    static int villagerCost(int[] byLevel,int level){return byLevel.length==0?0:byLevel[Math.max(1,Math.min(byLevel.length,level))-1];}
    /** Pure: the price of a repair: the base price when the FOB is DOWN (none when it only brings villagers back) */
    static int repairCost(boolean down,int base,int[] villagerCosts){int t=down?Math.max(0,base):0;for(int c:villagerCosts)t+=Math.max(0,c);return t;}
    /** Whether a repair has anything to pay for. */
    static boolean mayRepair(FobRules.State state,int lost,boolean raidRunning){return !raidRunning&&(state==FobRules.State.DOWN||lost>0);}
    /** Pure: a repaired DOWN FOB is ACTIVE again if it ever won a raid */
    static FobRules.State repaired(FobRules.State state,boolean activated){return state!=FobRules.State.DOWN?state:activated?FobRules.State.ACTIVE:FobRules.State.PLANTED;}
    /** At most this many lost villagers are kept for a repair */
    static final int MAX_RECORDS=64;

    // Names of the lost
    static final String[] NAMES={"Abel","Bertha","Cyril","Dora","Edgar","Fern","Gideon","Hilda","Ivo","Juno","Kasimir","Lotte","Magnus","Nell","Otto","Pia",
        "Quentin","Rosa","Silas","Tilda","Ulrich","Vera","Wendel","Xenia","Yorick","Zelda","Anselm","Britta","Corwin","Delia","Emrys","Freya"};
    /** Pure: the name of a villager with no name tag, always the same for the same UUID ("Hilda"). */
    static String villagerName(long most,long least){return NAMES[(int)Math.floorMod(TacticalRules.mix(most,least,0x534F53L),(long)NAMES.length)];}
}
