package dev.createarsenal.beacon;

/** Every number of the base support system in one place; the field guide text mirrors these (and a test checks it). */
final class SupportRules {
    private SupportRules(){}
    // Ardent Energy prices of the gear bought at the Support Platform. Throwing a flare costs nothing.
    static final int CANNON_PRICE=12,SUPPLY_FLARE_PRICE=3,RETURN_FLARE_PRICE=2,FIRE_FLARE_PRICE=6;
    /** How close (blocks) a player must stand to a cannon to work its menu. The cannon and platform themselves can be anywhere inside the beacon zone. */
    static final int CANNON_REACH=12;
    /** Players farther than this from their own cannon (or in another dimension) cannot see or hear it, so chat tells them what it is doing. */
    static final int CANNON_HEARING=48;
    /** A Bunker Buster takes this share of a player's maximum health, whatever armor they wear. */
    static final float BUNKER_PLAYER_SHARE=0.5f;
    static final int PORTAL_LIFETIME_TICKS=600;
    /**
     * Fire support: every shell lands exactly on the flare and hurts hostile mobs inside the red box. A shell is seen falling from high above
     * the flare for BLAST_FLIGHT_TICKS after the cannon fires (1.5 seconds, long enough to hear it whistle in; 0.5 before 0.0.16).
     */
    static final int FIRE_BLASTS=6,FIRE_INTERVAL_TICKS=40,FIRE_DAMAGE=25,BLAST_RADIUS=4,BLAST_FLIGHT_TICKS=30;
    /** Seconds until supplies arrive, counted from the moment the flare lands. */
    static final int SUPPLY_SECONDS_OUTSIDE=12,SUPPLY_SECONDS_UNDERGROUND=7;
    static final int PARCEL_DROP_HEIGHT=40,PARCEL_MIN_DROP=8,PARCEL_LIFETIME_TICKS=36000;
    static final double PARCEL_FALL_SPEED=0.4;
    static final int[] GRID={3,4,5,6};
    static final int[] UPGRADE_PLATING={0,16,32,64};
    static int grid(int mk){return GRID[Math.max(1,Math.min(4,mk))-1];}
    static int slots(int mk){return grid(mk)*grid(mk);}
    /** Plating needed to go from {@code mk} to {@code mk+1}; -1 at the top. */
    static int upgradeCost(int mk){return mk>=4?-1:UPGRADE_PLATING[mk];}
    static int supplyTicks(boolean outside){return 20*(outside?SUPPLY_SECONDS_OUTSIDE:SUPPLY_SECONDS_UNDERGROUND);}
    /** When (ticks after landing) the parcel must appear so a fall of {@code clear} blocks ends exactly at the delivery time. */
    static int parcelSpawnTick(int clear){return supplyTicks(true)-(int)Math.ceil(clear/PARCEL_FALL_SPEED);}
}
