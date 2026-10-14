package dev.eternaldefense.beacon;

/**
 * Forward Operating Bases, slice 3 (0.0.25): calls for help (SOS), DOWN and repairs: the pure rules, without Minecraft types, so they can be
 * unit tested (the server side is {@link FobSos}). Every so often one ACTIVE FOB calls for help; a player who comes to its village in time
 * starts a vanilla raid there; losing it, or not coming, puts the FOB DOWN until it is repaired.
 */
final class SosRules {
    /** 0.0.32: the answer starts the raid after this many seconds in the village with its villagers loaded. */
    static final int ARRIVE_SECONDS=3;
    private SosRules(){}

    // ---- the SOS clock ------------------------------------------------------------------------------------------------------------------------
    /** One clock for the whole server, in ticks of play ({@code FobData.onlineTicks}): the next SOS comes 10 to 12 hours of play after the last. */
    static final long SOS_MIN=720_000L,SOS_MAX=864_000L;
    /** Pure: the ticks of play before the next SOS for a random number in [0, 1): from {@link #SOS_MIN} to {@link #SOS_MAX}, both included. */
    static long delay(double random){
        double r=Math.max(0,Math.min(Math.nextDown(1.0),random));
        return Math.min(SOS_MAX,SOS_MIN+(long)Math.floor(r*(SOS_MAX-SOS_MIN+1)));
    }
    /**
     * Pure: does the SOS clock count down this second? Only while at least one FOB is ACTIVE, no SOS is open, no Home raid runs and the Home
     * raid's warning day ({@link TacticalRules#WARNING_TICKS}) has not begun: nobody is asked to be in two places.
     */
    static boolean counts(int activeFobs,boolean sosOpen,boolean homeRaid,boolean homeWarning){return activeFobs>0&&!sosOpen&&!homeRaid&&!homeWarning;}
    /** Pure: which ACTIVE FOB calls (its index among them, in id order) for a random number in [0, 1); -1 when there is none. */
    static int pick(int active,double random){return active<=0?-1:(int)Math.max(0,Math.min(active-1,Math.floor(random*active)));}

    // ---- the answer -------------------------------------------------------------------------------------------------------------------------------
    /** The SOS must be answered within 7 days of play (168,000 ticks). */
    static final long RESPONSE_TICKS=168_000L;
    /** Pure: does the response timer count down this second? Not while a Home raid runs, and not while the FOB's own raid decides it. */
    static boolean responseCounts(boolean homeRaid,boolean fobRaid){return !homeRaid&&!fobRaid;}
    /** The SOS raid's Bad Omen: level I (amplifier 0), the same as the activation raid; a Bad Omen the player carries is replaced, never added to. */
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

    // ---- the repair ----------------------------------------------------------------------------------------------------------------------------
    /** Pure: what bringing one lost villager back costs, by its level (1 Novice to 5 Master), from the edition's table ({@code Economy}). */
    static int villagerCost(int[] byLevel,int level){return byLevel.length==0?0:byLevel[Math.max(1,Math.min(byLevel.length,level))-1];}
    /** Pure: the price of a repair: the base price when the FOB is DOWN (none when it only brings villagers back), plus each villager's own price. */
    static int repairCost(boolean down,int base,int[] villagerCosts){int t=down?Math.max(0,base):0;for(int c:villagerCosts)t+=Math.max(0,c);return t;}
    /** Pure: is there anything to pay for? A DOWN FOB (even with no villager lost), or lost villagers; never while a raid runs at the FOB. */
    static boolean mayRepair(FobRules.State state,int lost,boolean raidRunning){return !raidRunning&&(state==FobRules.State.DOWN||lost>0);}
    /** Pure: a repaired DOWN FOB is ACTIVE again if it ever won a raid; otherwise PLANTED, and its activation raid is called again (no shortcut). */
    static FobRules.State repaired(FobRules.State state,boolean activated){return state!=FobRules.State.DOWN?state:activated?FobRules.State.ACTIVE:FobRules.State.PLANTED;}
    /** At most this many lost villagers are kept for a repair (more than a village holds; the oldest record goes first). */
    static final int MAX_RECORDS=64;

    // ---- names of the lost --------------------------------------------------------------------------------------------------------------------------
    static final String[] NAMES={"Abel","Bertha","Cyril","Dora","Edgar","Fern","Gideon","Hilda","Ivo","Juno","Kasimir","Lotte","Magnus","Nell","Otto","Pia",
        "Quentin","Rosa","Silas","Tilda","Ulrich","Vera","Wendel","Xenia","Yorick","Zelda","Anselm","Britta","Corwin","Delia","Emrys","Freya"};
    /** Pure: the name of a villager with no name tag, always the same for the same UUID ("Hilda"). */
    static String villagerName(long most,long least){return NAMES[(int)Math.floorMod(TacticalRules.mix(most,least,0x534F53L),(long)NAMES.length)];}
}
