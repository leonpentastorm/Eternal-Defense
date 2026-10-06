package dev.createarsenal.beacon;

/**
 * Pure rules for the Support Cannon's upgrades and the six fire support types. Every player has their own set of levels and one
 * chosen type; both live in {@link SupportData.Base} and are read when a flare is thrown, so a flare handed to somebody else
 * still calls down the thrower's own choice.
 */
final class CannonUpgrades {
    private CannonUpgrades(){}

    enum Upgrade {
        TRAVERSE("traverse",60,120,200),     // faster turret
        RATE("rate",80,160,260),             // faster fire rate
        VOLLEY("volley",100,200,320),        // more shells per call
        DAMAGE("damage",100,200,320),
        QUANTUM("quantum",400),              // fire support works below ground
        SLOW("slow",250),                    // slowness field over the whole area
        PORTAL("portal",200),                // 5 minute portal that can be used twice
        AURA("aura",200),                    // healing aura round a supply flare
        AOE("aoe",120,220,340),              // wider area of effect for every fire support
        DIMENSION("dimension",500);          // fire support can be called in the other dimensions
        final String id;final int[] prices;
        Upgrade(String id,int... prices){this.id=id;this.prices=prices;}
        int max(){return prices.length;}
        /** Ardent Energy for the next level, or -1 when maxed. */
        int price(int level){return level<0||level>=prices.length?-1:prices[level];}
    }

    enum FireType {
        EXPLOSION("explosion",0),ARROW("arrow",2),NARUKAMI("narukami",-1),BUNKER("bunker",0),HEAL("heal",0),CURSE("curse",0);
        final String id;final int volleyDelta;
        FireType(String id,int volleyDelta){this.id=id;this.volleyDelta=volleyDelta;}
        /** The id sent in call announcements and used in language keys. */
        String callId(){return "fire_"+id;}
        boolean damaging(){return this==EXPLOSION||this==ARROW||this==NARUKAMI||this==BUNKER;}
        static FireType of(int ordinal){var v=values();return v[Math.max(0,Math.min(v.length-1,ordinal))];}
    }

    static final int SHELL_BASE=SupportRules.FIRE_BLASTS,VOLLEY_STEP=2;
    /** Half-width of the area of a Bunker Buster, in blocks: a cube this far each way that is dug out completely. */
    static final int BUNKER_RADIUS=8;
    /** Every Area of Effect level widens the area by this much (a Bunker Buster, which digs a whole cube, grows one block a level). */
    static final double AOE_STEP=1.5,BUNKER_AOE_STEP=1;

    /** Shells per call: the base six, plus the type's bonus and two per Volley level. A Bunker Buster is always a single bomb. */
    static int volleys(FireType type,int volleyLevel){
        if(type==FireType.BUNKER)return 1;
        return Math.max(1,SHELL_BASE+type.volleyDelta+VOLLEY_STEP*Math.max(0,volleyLevel));
    }
    /** Ticks between shells: 40, 33, 26, 20. */
    static int interval(int rateLevel){return Math.max(20,SupportRules.FIRE_INTERVAL_TICKS-7*Math.max(0,rateLevel));}
    /** +25% damage per level. */
    static float damage(int level){return 1f+.25f*Math.max(0,level);}
    /** Turret speed multiplier: +40% per level (spin time falls accordingly). */
    static float turnSpeed(int level){return 1f+.4f*Math.max(0,level);}
    /** Half-width of the red box in blocks: 4 (8 for a Bunker Buster) plus one step per Area of Effect level. */
    static double radius(FireType type,int aoeLevel){
        int lv=Math.max(0,aoeLevel);
        return type==FireType.BUNKER?BUNKER_RADIUS+BUNKER_AOE_STEP*lv:SupportRules.BLAST_RADIUS+AOE_STEP*lv;
    }
    static int portalTicks(boolean longPortal){return longPortal?6000:SupportRules.PORTAL_LIFETIME_TICKS;}
    static final int AURA_AFTER_PICKUP_TICKS=600,AURA_RADIUS=6;

    /** Everything a thrown flare needs to know, frozen at the moment of the throw. */
    record Config(FireType type,int volleys,int interval,float damage,boolean tunnel,boolean slow,boolean longPortal,boolean aura,int aoe){
        double radius(){return CannonUpgrades.radius(type,aoe);}
        static Config of(int[] levels,FireType type){
            int[] l=levels.length>=Upgrade.values().length?levels:java.util.Arrays.copyOf(levels,Upgrade.values().length);
            return new Config(type,CannonUpgrades.volleys(type,l[Upgrade.VOLLEY.ordinal()]),CannonUpgrades.interval(l[Upgrade.RATE.ordinal()]),CannonUpgrades.damage(l[Upgrade.DAMAGE.ordinal()]),
                l[Upgrade.QUANTUM.ordinal()]>0,l[Upgrade.SLOW.ordinal()]>0,l[Upgrade.PORTAL.ordinal()]>0,l[Upgrade.AURA.ordinal()]>0,l[Upgrade.AOE.ordinal()]);
        }
    }
}
