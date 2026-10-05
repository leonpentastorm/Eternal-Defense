package dev.createarsenal.beacon;

/** Every number of the base support system in one place; the field guide text mirrors these (and a test checks it). */
final class SupportRules {
    private SupportRules(){}
    // prices of gear bought at the Support Platform, in Ardent Energy
    static final int CANNON_PRICE=100,SUPPLY_FLARE_PRICE=15,RETURN_FLARE_PRICE=8,FIRE_FLARE_PRICE=40;
    // price of making the call itself
    static final int SUPPLY_CALL_FEE=5,RETURN_CALL_PRICE=10,FIRE_CALL_PRICE=60;
    static final int CANNON_RANGE=6;
    static final int RETURN_CHANNEL_TICKS=60;
    static final int FIRE_RADIUS=8,FIRE_BLASTS=6,FIRE_INTERVAL_TICKS=40,FIRE_DAMAGE=25,BLAST_RADIUS=4;
    static final int PARCEL_LIFETIME_TICKS=12000,PARCEL_DROP_HEIGHT=40,SUPPLY_DELAY_TICKS=30;
    static final int[] GRID={3,4,5,6};
    static final int[] UPGRADE_PLATING={0,16,32,64};
    static int grid(int mk){return GRID[Math.max(1,Math.min(4,mk))-1];}
    static int slots(int mk){return grid(mk)*grid(mk);}
    /** Plating needed to go from {@code mk} to {@code mk+1}; -1 at the top. */
    static int upgradeCost(int mk){return mk>=4?-1:UPGRADE_PLATING[mk];}
}
