package dev.createarsenal.beacon;

/**
 * The Supply Catapult and the specialty seller (0.0.33, owner), the parts that need no world: who may buy, what a lot holds and costs, the
 * queue, the throw's timing and the flight home. Prices themselves live in {@link Economy}.
 */
final class CatapultRules {
    private CatapultRules(){}
    /** The seller opens at the top research level only. */
    static final int SELLER_LEVEL=RouteRules.MAX_LEVEL;
    /** A lot is worth one S-tier item: one S item, two A items, four B ... a cheap item by the stack's worth. */
    static final double LOT_VALUE=RouteRules.TIER_VALUE[5];
    /** The two buy buttons: one lot, or four. */
    static final int[] LOTS={1,4};
    /** Shipments a FOB's catapult holds waiting (the one being thrown included). */
    static final int MAX_QUEUE=4;

    static int lotSize(RouteRules.Loot loot){return Math.max(1,(int)Math.round(LOT_VALUE/loot.value()));}
    /** Ardent Energy for {@code lots} lots. */
    static int price(int lots){return Math.max(0,lots)*Economy.sellerLotPrice();}

    /** Why a FOB's seller is closed: a key under {@code tactical.seller.closed.}, or null when it is open. */
    static String closed(boolean active,int level,boolean catapult,boolean base){
        if(!base)return "base";
        if(!active)return "down";
        if(level<SELLER_LEVEL)return "research";
        if(!catapult)return "catapult";
        return null;
    }

    // ---- the throw (the owner's README: the arm from cocked 0 to -88 degrees in about 4 ticks, the box leaves at -45, wound back over 40) ------
    static final int THROW_TICKS=4,HOLD_TICKS=6,RELOAD_TICKS=40,CYCLE=THROW_TICKS+HOLD_TICKS+RELOAD_TICKS;
    static final float STOP_DEGREES=-88,RELEASE_DEGREES=-45;
    /** The tick of the throw at which the box leaves the cup (the arm has passed -45 degrees). */
    static final int RELEASE_TICK=3;
    /** Ticks the frame sits still on its heading before it throws. */
    static final int SETTLE_TICKS=10;
    /** The arm's pitch {@code t} ticks into a throw (fractional for smooth drawing); 0 when cocked. */
    static float armAngle(float t){
        if(t<=0||t>=CYCLE)return 0;
        if(t<THROW_TICKS){float k=t/THROW_TICKS;return STOP_DEGREES*k*k;}           // ease in: fast at the end
        if(t<THROW_TICKS+HOLD_TICKS)return STOP_DEGREES;
        float k=(t-THROW_TICKS-HOLD_TICKS)/RELOAD_TICKS;k=k*k*(3-2*k);                  // the windlass winds it back down
        return STOP_DEGREES*(1-k);
    }
    /** The box's flight to the base, in ticks: 5 seconds, a second more per 50 blocks, at most 30 seconds. */
    static int flightTicks(double distance){return (int)Math.max(100,Math.min(600,100+Math.round(distance/50*20)));}
    /** Ticks from a purchase to the throw when the catapult's land is not loaded (nobody sees it turn). */
    static final int UNSEEN_THROW_TICKS=60;

    // ---- the cup at release, in the frame's space (model pixels; the owner's README) ------------------------------------------------------------------
    static final double CUP_X=8,CUP_Y=26.9,CUP_Z=15.6,PIVOT_X=8,PIVOT_Z=8;
    /**
     * Where the box leaves the cup and how fast, in blocks from the catapult block's corner, the frame turned {@code yaw} degrees (the support
     * cannon's convention: 0 faces north, the frame turns about the block's centre): {x, y, z, vx, vy, vz}.
     */
    static double[] release(double yaw,double speed){
        double rad=Math.toRadians(yaw),c=Math.cos(rad),s=Math.sin(rad);
        double lx=(CUP_X-PIVOT_X)/16,lz=(CUP_Z-PIVOT_Z)/16,vx=0,vz=-0.707*speed;
        return new double[]{.5+lx*c+lz*s,CUP_Y/16,.5-lx*s+lz*c,vx*c+vz*s,0.707*speed,-vx*s+vz*c};
    }
    /** The catapult's heading to face a point (the support cannon's: 0 is north). */
    static float yawTo(double fromX,double fromZ,double toX,double toZ){return (float)Math.toDegrees(Math.atan2(-(toX-fromX),-(toZ-fromZ)));}
}
