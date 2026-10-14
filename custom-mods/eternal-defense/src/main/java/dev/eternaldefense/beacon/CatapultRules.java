package dev.eternaldefense.beacon;

/** The Supply Catapult and the specialty seller, the parts that need no world */
final class CatapultRules {
    private CatapultRules(){}
    /** The seller opens at the top research level only. */
    static final int SELLER_LEVEL=RouteRules.MAX_LEVEL;
    /** A lot is worth one S-tier item: one S item, two A items, four B... */
    static final double LOT_VALUE=RouteRules.TIER_VALUE[5];
    /** The two buy buttons: one lot, or four. */
    static final int[] LOTS={1,4};
    /** Shipments a FOB's catapult holds waiting (the one being thrown included). */
    static final int MAX_QUEUE=4;

    static int lotSize(RouteRules.Loot loot){return Math.max(1,(int)Math.round(LOT_VALUE/loot.value()));}
    /** Ardent Energy for lots lots. */
    static int price(int lots){return Math.max(0,lots)*Economy.sellerLotPrice();}

    /** Why a FOB's seller is closed: a key under tactical.seller.closed., or null when it is open. */
    static String closed(boolean active,int level,boolean catapult,boolean base){
        if(!base)return "base";
        if(!active)return "down";
        if(level<SELLER_LEVEL)return "research";
        if(!catapult)return "catapult";
        return null;
    }

    // The throw
    static final int THROW_TICKS=4,HOLD_TICKS=6,RELOAD_TICKS=40,CYCLE=THROW_TICKS+HOLD_TICKS+RELOAD_TICKS;
    static final float STOP_DEGREES=-88,RELEASE_DEGREES=-45;
    /** The tick of the throw at which the box leaves the cup (the arm has passed -45 degrees). */
    static final int RELEASE_TICK=3;
    /** Ticks the frame sits still on its heading before it throws. */
    static final int SETTLE_TICKS=10;
    /** The arm's pitch t ticks into a throw (fractional for smooth drawing); 0 when cocked. */
    static float armAngle(float t){
        if(t<=0||t>=CYCLE)return 0;
        if(t<THROW_TICKS){float k=t/THROW_TICKS;return STOP_DEGREES*k*k;}           // Ease in: fast at the end
        if(t<THROW_TICKS+HOLD_TICKS)return STOP_DEGREES;
        float k=(t-THROW_TICKS-HOLD_TICKS)/RELOAD_TICKS;k=k*k*(3-2*k);                  // The windlass winds it back down
        return STOP_DEGREES*(1-k);
    }
    /** After the throw the box comes home as a Supply Flare's parcel does */
    static final int FLIGHT_TICKS=SupportRules.supplyTicks(true);
    /** The game tick the parcel appears at, clear open blocks above its landing spot, for a flight landing at due. */
    static long spawnAt(long due,int clear,boolean outside){return outside?due-(int)Math.ceil(clear/SupportRules.PARCEL_FALL_SPEED):due;}

    /** What one tick of the catapult did. */
    enum Event{NONE,LOADED,THROW,RELEASE}
    /** The catapult's motion. */
    static final class Motion {
        float yaw,prevYaw,target,speed,rest;int throwAge=-1,settled;boolean loaded;
        private final float[] motor=new float[2];
        Motion(float rest){this.rest=rest;yaw=prevYaw=target=rest;}
        boolean throwing(){return throwAge>=0;}
        boolean ready(){return Math.abs(target-yaw)<0.6f&&Math.abs(speed)<0.2f;}
        /** Aims the shortest way round at goal. */
        void aim(float goal){float diff=goal-yaw;diff=((diff%360)+540)%360-180;target=yaw+diff;}
        /** The arm's pitch partial of a tick on. */
        float armAngle(float partial){return throwAge<0?0:armAngle(throwAge+partial);}
        /** One tick: box a box waits to be thrown toward goal. */
        Event step(boolean box,float goal){
            prevYaw=yaw;motor[0]=yaw;motor[1]=speed;SupportCannon.spin(motor,target,.8f);yaw=motor[0];speed=motor[1];
            if(throwAge>=0){
                throwAge++;
                if(throwAge==RELEASE_TICK)return Event.RELEASE;
                if(throwAge>=CYCLE)throwAge=-1;
                return Event.NONE;
            }
            if(!box){loaded=false;settled=0;aim(rest);return Event.NONE;}
            Event event=Event.NONE;
            if(!loaded){loaded=true;event=Event.LOADED;}
            aim(goal);
            settled=ready()?settled+1:0;
            if(settled>=SETTLE_TICKS){settled=0;throwAge=0;return Event.THROW;}
            return event;
        }
    }
    /** The heading a catapult placed by someone looking playerYRot (the game's yaw: 0 south, 90 west) rests */
    static float restFor(float playerYRot){float yaw=180-playerYRot;yaw=Math.round(yaw/90f)*90f;return ((yaw%360)+540)%360-180;}

    // The cup at release, in the frame's space
    static final double CUP_X=8,CUP_Y=26.9,CUP_Z=15.6,PIVOT_X=8,PIVOT_Z=8;
    /** Where the box leaves the cup and how fast, in blocks from the catapult block's corner */
    static double[] release(double yaw,double speed){
        double rad=Math.toRadians(yaw),c=Math.cos(rad),s=Math.sin(rad);
        double lx=(CUP_X-PIVOT_X)/16,lz=(CUP_Z-PIVOT_Z)/16,vx=0,vz=-0.707*speed;
        return new double[]{.5+lx*c+lz*s,CUP_Y/16,.5-lx*s+lz*c,vx*c+vz*s,0.707*speed,-vx*s+vz*c};
    }
    /** The catapult's heading to face a point (the support cannon's: 0 is north). */
    static float yawTo(double fromX,double fromZ,double toX,double toZ){return (float)Math.toDegrees(Math.atan2(-(toX-fromX),-(toZ-fromZ)));}
}
