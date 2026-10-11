package dev.createarsenal.beacon;

import java.util.List;
import java.util.Locale;

/**
 * The supply rail of a Forward Operating Base (0.0.24, FOB slice 2): the pure rules, without Minecraft types, so they can be unit tested.
 * A pair of gates (a Supply Receiving Gate at home, a Supply Delivery Gate at the FOB) is opened by one minecart ride from home to the FOB;
 * the route then delivers the FOB's tribute by itself, and a full round trip carries more and renews its inspection clock (see {@link Routes}).
 */
final class RouteRules {
    private RouteRules(){}

    // ---- the goods ----------------------------------------------------------------------------------------------------------------------------
    /** A tribute good and how much of it one villager makes in an hour of play. Nothing the Exchange buys (see FobTest), so tribute never becomes Ardent. */
    record Good(String id,double perVillagerHour){}
    static final List<Good> GOODS=List.of(new Good("minecraft:redstone",1.5),new Good("minecraft:gunpowder",1.0),new Good("minecraft:flint",1.5),
        new Good("minecraft:bread",1.0),new Good("minecraft:carrot",.5),new Good("minecraft:potato",.5),new Good("minecraft:leather",.25),new Good("minecraft:paper",.25),
        new Good("minecraft:emerald",.25));   // 0.0.28 (owner): emeralds too, the villagers' own currency (the standalone Exchange buys 2 for 1 Ardent Energy)
    /** Villagers beyond this many make nothing more. */
    static final int MAX_WORKERS=20;
    /** The stockpile holds this many hours of play of production (at least {@link #MIN_CAP} of each good). */
    static final int CAP_HOURS=8,MIN_CAP=16;
    static final long HOUR=72_000L;
    /** Pure: how much of good {@code g} the FOB makes in {@code ticks} ticks of play with {@code villagers} villagers. */
    static double produced(Good g,int villagers,long ticks){return ticks<=0?0:g.perVillagerHour()*Math.min(MAX_WORKERS,Math.max(0,villagers))*ticks/(double)HOUR;}
    static double cap(Good g,int villagers){return Math.max(MIN_CAP,g.perVillagerHour()*Math.min(MAX_WORKERS,Math.max(0,villagers))*CAP_HOURS);}
    /** Pure: the stockpile after {@code ticks} ticks of play, each good capped. */
    static double[] accrue(double[] stock,int villagers,long ticks){
        var out=stock.clone();
        for(int i=0;i<GOODS.size();i++)out[i]=Math.min(cap(GOODS.get(i),villagers),out[i]+produced(GOODS.get(i),villagers,ticks));
        return out;
    }
    /**
     * Pure: what a trip of {@code capacity} items takes from the stockpile: whole items, as evenly as the stock allows (the fullest goods first),
     * never more of a good than is there. The caller subtracts it in the same step as it queues it, so every item is paid once.
     */
    static int[] take(double[] stock,int capacity){
        int n=GOODS.size();int[] out=new int[n];int left=Math.max(0,capacity);
        while(left>0){
            int best=-1;double most=0;
            for(int i=0;i<n;i++){double rest=stock[i]-out[i];if(rest>=1&&rest>most){most=rest;best=i;}}
            if(best<0)break;
            out[best]++;left--;
        }
        return out;
    }
    static int total(int[] amounts){int t=0;for(int a:amounts)t+=a;return t;}

    // ---- the route's numbers -------------------------------------------------------------------------------------------------------------------
    /** The gates must stand at least this far apart (straight line, sideways). */
    static final int MIN_SEPARATION=128;
    /** A passive delivery comes every 2T / 0.8: as if a cart rode the round trip continuously at 80 percent of the ride's speed. */
    static final double PASSIVE_EFFICIENCY=0.8;
    /** Trip capacity: one item per this many blocks between the gates, at least 1 and at most {@link #MAX_CAPACITY} (passive); a ride carries {@link #ACTIVE_FACTOR} times as much. */
    static final int BLOCKS_PER_ITEM=200,MAX_CAPACITY=20,ACTIVE_FACTOR=2;
    /** Pure: what a passive delivery carries over {@code distance} blocks. Flat or rising with distance (up to 4000 blocks), never falling. */
    static int capacity(double distance){return (int)Math.max(1,Math.min(MAX_CAPACITY,Math.round(distance/BLOCKS_PER_ITEM)));}
    static int activeCapacity(double distance){return ACTIVE_FACTOR*capacity(distance);}
    /** Pure: ticks between passive deliveries for a one-way time T. */
    static long period(long baseline){return Math.max(20,Math.round(2*baseline/PASSIVE_EFFICIENCY));}
    /** The fastest a cart covers ground (vanilla's 0.4 blocks a tick on a straight rail, with room for diagonal track): no route is timed faster. */
    static final double MAX_SPEED=0.57;
    /** Pure: a measured one-way time, never faster than a cart can cover the straight-line distance. */
    static long clamp(long ticks,double distance){return Math.max(ticks,(long)Math.ceil(distance/MAX_SPEED));}
    /** Pure: the new baseline after a round trip: the average of its two legs, clamped. */
    static long baseline(long out,long back,double distance){return clamp((out+back)/2,distance);}

    // ---- the inspection clock, the backlog ----------------------------------------------------------------------------------------------------------
    /** Ticks of play the route keeps running after a ride (8 hours); the light turns amber in its last quarter. */
    static final long CLOCK=8*HOUR;
    static final double WARN_SHARE=.25;
    static boolean warning(long clock){return clock>0&&clock<=CLOCK*WARN_SHARE;}
    /** Queued reward stacks above which deliveries wait (the reward chest shows 54 slots; the rest stays queued in the campaign). */
    static final int BACKLOG_CAP=200;
    static boolean backlog(int queued){return queued>=BACKLOG_CAP;}
    /** Passive "Tribute delivered" chat lines: at most one per FOB in this many ticks of play, summing what arrived meanwhile. */
    static final long NOTE_TICKS=6_000;
    /** A leg of a ride gives up after max(10 minutes, 3 T). */
    static long legTimeout(long baseline){return Math.max(12_000L,3*Math.max(0,baseline));}

    // ---- the traffic light ---------------------------------------------------------------------------------------------------------------------
    enum Signal{RED,AMBER,GREEN;String id(){return name().toLowerCase(Locale.ROOT);}}
    /**
     * Pure, shared by the gates, the screens and the tests. RED: unlinked, no partner gate, the FOB not ACTIVE, no route yet, or the inspection
     * overdue. AMBER: a ride under way, the inspection in its last quarter, or deliveries waiting on the backlog. GREEN otherwise.
     */
    static Signal signal(boolean linked,boolean partner,boolean fobActive,boolean commissioned,long clock,boolean riding,boolean backlog){
        if(!linked||!partner||!fobActive||!commissioned||clock<=0)return Signal.RED;
        if(riding||warning(clock)||backlog)return Signal.AMBER;
        return Signal.GREEN;
    }
    /** Pure: may a passive delivery happen now? */
    static boolean passive(boolean fobActive,boolean commissioned,long clock,boolean backlog){return fobActive&&commissioned&&clock>0&&!backlog;}

    // ---- a pass through a gate -------------------------------------------------------------------------------------------------------------------
    /**
     * Pure: did a cart moving from (px, py, pz) to (cx, cy, cz) roll through a gate whose rail cell is at (gx, gy, gz), along the X axis
     * ({@code alongX}) or the Z axis? The cart must cross the plane through the cell's middle across the rail, between the cell's sides
     * (with {@code slack} blocks of play) and within its height. Both directions count; a cart that stops inside has not passed; a cart
     * moving several blocks in one tick still crosses (the segment is tested, not a point). Returns +1 or -1 for the direction along the
     * axis, 0 for no pass.
     */
    static int crossing(double px,double py,double pz,double cx,double cy,double cz,int gx,int gy,int gz,boolean alongX,double slack){
        double a0=alongX?px:pz,a1=alongX?cx:cz,mid=(alongX?gx:gz)+.5;
        if((a0-mid)*(a1-mid)>0||a0==a1)return 0;
        if(a0==mid)return 0;   // starting exactly on the plane: the previous step counted it
        double t=(mid-a0)/(a1-a0);
        double side=(alongX?pz:px)+t*((alongX?cz:cx)-(alongX?pz:px)),y=py+t*(cy-py);
        int sideCell=alongX?gz:gx;
        if(side<sideCell-slack||side>sideCell+1+slack)return 0;
        if(y<gy-slack||y>gy+1.5+slack)return 0;
        return a1>a0?1:-1;
    }

    // ---- a ride ---------------------------------------------------------------------------------------------------------------------------------
    enum Gate{RECEIVING,DELIVERY}
    /** Where a ride stands: out from home towards the FOB, or back home with a load. */
    enum Stage{OUTBOUND,RETURN}
    /** What a pass did, a key under {@code route.pass.} for the rider's action bar. */
    enum Step{STARTED,RESTARTED,COMMISSIONED,LOADED,COMPLETE,BUSY,START_AT_HOME,WRONG_GATE,TOO_CLOSE}
    /**
     * Pure: the next step of a ride at a gate. {@code stage} is null when no ride runs on this route; {@code sameRider} tells whether the pass
     * is by the rider (in the same cart) of the running ride. A ride starts at the Receiving Gate (home), turns at the Delivery Gate (the FOB;
     * the first time it opens the route) and ends at the Receiving Gate again.
     */
    static Step step(Gate gate,Stage stage,boolean sameRider,boolean commissioned,double distance){
        if(stage!=null&&!sameRider)return Step.BUSY;
        if(gate==Gate.RECEIVING){
            if(stage==null)return Step.STARTED;
            return stage==Stage.RETURN?Step.COMPLETE:Step.RESTARTED;
        }
        if(stage==null)return Step.START_AT_HOME;
        if(stage==Stage.RETURN)return Step.WRONG_GATE;
        if(distance<MIN_SEPARATION)return Step.TOO_CLOSE;
        return commissioned?Step.LOADED:Step.COMMISSIONED;
    }
}
