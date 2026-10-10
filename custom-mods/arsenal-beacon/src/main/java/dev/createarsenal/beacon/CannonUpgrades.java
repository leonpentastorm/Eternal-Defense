package dev.createarsenal.beacon;

/**
 * Pure rules for the Support Cannon's upgrades and the twelve fire support types (six more since 0.0.16, reworked in 0.0.18). Every player has their own set of levels and one
 * chosen type; both live in {@link SupportData.Base} and are read when a flare is thrown, so a flare handed to somebody else
 * still calls down the thrower's own choice.
 */
final class CannonUpgrades {
    private CannonUpgrades(){}

    enum Upgrade {
        TRAVERSE("traverse",3,6,10),     // faster turret
        RATE("rate",4,8,12),             // faster fire rate
        VOLLEY("volley",5,10,16),        // more shells per call
        DAMAGE("damage",5,10,16),
        QUANTUM("quantum",14),              // fire support works below ground
        SLOW("slow",8),                    // slowness field over the whole area
        PORTAL("portal",6),                // 5 minute portal that can be used twice
        AURA("aura",6),                    // healing aura round a supply flare
        AOE("aoe",5,9,14),              // wider area of effect for every fire support
        DIMENSION("dimension",18);          // fire support can be called in the other dimensions
        final String id;final int[] prices;
        Upgrade(String id,int... prices){this.id=id;this.prices=prices;}
        int max(){return prices.length;}
        /** Ardent Energy for the next level in the standalone edition (the pack pays in factory parts, see {@link Economy}), or -1 when maxed. */
        int price(int level){return level<0||level>=prices.length?-1:prices[level];}
    }

    /** The types are saved and sent by their position in this list: new ones are only ever added at the end. */
    enum FireType {
        EXPLOSION("explosion",0),ARROW("arrow",0),NARUKAMI("narukami",-1),BUNKER("bunker",0),HEAL("heal",0),CURSE("curse",0),
        CLUSTER("cluster",-1),CRYO("cryo",-2),NAPALM("napalm",-2),GRAVITY("gravity",-3),SHOCKWAVE("shockwave",-2),STARSHELL("starshell",0);
        final String id;final int volleyDelta;
        FireType(String id,int volleyDelta){this.id=id;this.volleyDelta=volleyDelta;}
        /** The id sent in call announcements and used in language keys. */
        String callId(){return "fire_"+id;}
        boolean damaging(){return this==EXPLOSION||this==ARROW||this==NARUKAMI||this==BUNKER||this==CLUSTER||this==NAPALM;}
        /** A single round whatever the Volley upgrade: the Bunker Buster's bomb, the starshell, and (0.0.18) the four that keep working where they land. */
        boolean single(){return this==BUNKER||this==STARSHELL||lingers();}
        /** One round whose effect lasts on the ground; the Volley upgrade makes it last longer instead of firing more rounds (0.0.18). */
        boolean lingers(){return this==CLUSTER||this==NAPALM||this==CRYO||this==GRAVITY;}
        /** The flat box (2 blocks high) of the ground effects napalm and frost. */
        boolean flat(){return this==NAPALM||this==CRYO;}
        static FireType of(int ordinal){var v=values();return v[Math.max(0,Math.min(v.length-1,ordinal))];}
    }

    static final int SHELL_BASE=SupportRules.FIRE_BLASTS,VOLLEY_STEP=2;
    /** Half-width of the area of a Bunker Buster, in blocks: a cube this far each way that is dug out completely. */
    static final int BUNKER_RADIUS=8;
    /** Every Area of Effect level widens the area by this much (a Bunker Buster, which digs a whole cube, grows one block a level). */
    static final double AOE_STEP=1.5,BUNKER_AOE_STEP=1;

    /** Shells per call: the base six, plus the type's bonus and two per Volley level. A Bunker Buster and a starshell are always a single round. */
    static int volleys(FireType type,int volleyLevel){
        if(type.single())return 1;
        return Math.max(1,SHELL_BASE+type.volleyDelta+VOLLEY_STEP*Math.max(0,volleyLevel));
    }
    /** Ticks between shells: 40, 33, 26, 20. */
    static int interval(int rateLevel){return Math.max(20,SupportRules.FIRE_INTERVAL_TICKS-7*Math.max(0,rateLevel));}
    /** +25% damage per level. */
    static float damage(int level){return 1f+.25f*Math.max(0,level);}
    /**
     * Turret speed multiplier by Faster traverse level (0.0.18): with no upgrade a turn takes 1.5 times as long as it did before, and the top
     * level (3) turns exactly as fast as the old unupgraded turret did (multiplier 1). Levels in between are spaced evenly (geometrically).
     */
    static final float TURN_BASE=0.617f;
    static float turnSpeed(int level){return (float)Math.pow(TURN_BASE,1-Math.max(0,Math.min(3,level))/3.0);}
    /** Half-width of the red box in blocks: 4 (8 for a Bunker Buster) plus one step per Area of Effect level. */
    static double radius(FireType type,int aoeLevel){
        int lv=Math.max(0,aoeLevel);
        return switch(type){
            case BUNKER->BUNKER_RADIUS+BUNKER_AOE_STEP*lv;
            case CLUSTER->(SupportRules.BLAST_RADIUS+AOE_STEP*lv)*CLUSTER_SPREAD;
            case NAPALM->(SupportRules.BLAST_RADIUS+AOE_STEP*lv)*GROUND_SPREAD;
            case CRYO->(SupportRules.BLAST_RADIUS+AOE_STEP*lv)*GROUND_SPREAD+CRYO_EXTRA;
            case GRAVITY->(SupportRules.BLAST_RADIUS+AOE_STEP*lv)*WELL_SPREAD;
            case SHOCKWAVE->Rules.radius(0)+AOE_STEP*lv;
            case STARSHELL->STAR_RADIUS+STAR_AOE_STEP*lv;
            default->SupportRules.BLAST_RADIUS+AOE_STEP*lv;
        };
    }

    // ---- the six types added in 0.0.16, reworked in 0.0.18 (shares are of SupportRules.FIRE_DAMAGE, before the Damage upgrade) -----------
    /**
     * How long a lingering type keeps working after its round lands (ticks), with {@code volleyLevel} levels of the Volley upgrade:
     * Cluster Strike 12 s (the Volley upgrade does nothing for it), Napalm Carpet 10 s + 4 s a level, Cryo Shell 15 s + 5 s, Gravity Well
     * 5 s + 2 s; the starshell burns 30 s. 0 for the others.
     */
    static int lingerTicks(FireType type,int volleyLevel){
        int lv=Math.max(0,volleyLevel);
        return switch(type){
            case CLUSTER->CLUSTER_TICKS;case NAPALM->NAPALM_TICKS+NAPALM_STEP*lv;case CRYO->CRYO_TICKS+CRYO_STEP*lv;case GRAVITY->GRAVITY_TICKS+GRAVITY_STEP*lv;
            case STARSHELL->STAR_TICKS;default->0;
        };
    }
    /**
     * Cluster Strike: one shell that bursts over the box and keeps a rain of bomblets going for CLUSTER_TICKS; every CLUSTER_PULSE_TICKS a few
     * go off and every hostile mob in the box is hurt. Over the whole strike a mob that stays inside takes CLUSTER_SHARE of what the six shells of
     * an Explosion Barrage would deal (80 percent: 120 before the Damage upgrade). The box is CLUSTER_SPREAD times as wide as a barrage's.
     */
    static final int CLUSTER_TICKS=240,CLUSTER_PULSE_TICKS=10,BOMBLETS_PER_PULSE=3;static final double CLUSTER_SPREAD=1.75;static final float CLUSTER_SHARE=.8f;
    /** Height over the flare at which a Cluster Strike shell bursts. */
    static final double CLUSTER_BURST_HEIGHT=6;
    /** Pure: what one bomblet pulse deals to every hostile mob in the box. */
    static float clusterPulse(float multiplier){return SupportRules.FIRE_DAMAGE*SupportRules.FIRE_BLASTS*CLUSTER_SHARE*multiplier/(CLUSTER_TICKS/CLUSTER_PULSE_TICKS);}
    /** Napalm Carpet and Cryo Shell cover a flat box, 2 blocks high, GROUND_SPREAD times as wide as a barrage's. */
    static final double GROUND_SPREAD=1.2,GROUND_HEIGHT=2;
    /**
     * Napalm Carpet: the ground burns (real fire on every free spot, kept burning, taken away when it ends) and once a second every hostile mob
     * in the box takes NAPALM_SHARE of a shell and is set alight for NAPALM_BURN_SECONDS. No fire is lit within FIRE_ZONE_MARGIN of the base zone.
     */
    static final int NAPALM_TICKS=200,NAPALM_STEP=80,NAPALM_BURN_SECONDS=4,FIRE_ZONE_MARGIN=4;static final float NAPALM_SHARE=.2f;
    /** Pure: what the burning ground deals each second to a mob standing in it. */
    static float napalm(float multiplier){return hit(NAPALM_SHARE,multiplier);}
    /**
     * Cryo Shell: no damage. The ground is covered in snow and every hostile mob in the box is slowed almost to a stop (Slowness V, Mining
     * Fatigue III) for as long as it stays; a raid boss is slowed less (Slowness III).
     */
    static final int CRYO_TICKS=300,CRYO_STEP=100,CRYO_SLOW_AMPLIFIER=4,CRYO_BOSS_AMPLIFIER=2;
    /** The Cryo Shell's frost reaches this many blocks further on every side than the napalm's (0.0.20, the owner's buff); not higher. */
    static final double CRYO_EXTRA=3;
    /** Gravity Well: no damage; every hostile mob in its (twice as wide) box is dragged to the flare, into one crowd to shoot. */
    static final int GRAVITY_TICKS=100,GRAVITY_STEP=40;static final double GRAVITY_PULL=.16,WELL_SPREAD=2;
    /**
     * Shockwave: no damage. Its box is as wide as a level 1 beacon zone (8 blocks each way); for SHOCK_PUSH_TICKS after every shell, every hostile
     * mob inside is shoved outward until it is past the edge, whatever its knockback resistance, a raid boss too.
     */
    static final int SHOCK_PUSH_TICKS=16;
    /** Pure: the outward speed (blocks per tick) that carries a mob {@code distance} from the flare out past {@code radius}. */
    static double shockSpeed(double distance,double radius){return Math.max(.5,Math.min(1.6,(radius+1-distance)*.35));}
    /** A raid boss is moved by a gravity well this much less (on top of its own knockback resistance). */
    static final double BOSS_MOVE=.35;
    /**
     * Starshell: bursts high over the flare and burns for 30 seconds, sinking slowly. It lights the whole cube (hidden light blocks every
     * STAR_LIGHT_SPACING blocks, taken away when it goes out); hostile mobs in it glow, players in it work faster (Haste II).
     */
    static final int STAR_TICKS=600,STAR_GLOW_TICKS=100,STAR_HASTE_TICKS=60,STAR_PULSE_TICKS=40,STAR_LIGHT_SPACING=4;static final double STAR_RADIUS=16,STAR_AOE_STEP=2,STAR_BURST_HEIGHT=14;

    /** Pure: the damage of one hit worth {@code share} of a shell, with the Damage upgrade's multiplier. */
    static float hit(float share,float multiplier){return SupportRules.FIRE_DAMAGE*share*multiplier;}
    /** Pure: how strongly a gravity well moves a mob: less with knockback resistance, much less for a raid boss. */
    static double moveScale(double knockbackResistance,boolean boss){return Math.max(0,1-Math.max(0,Math.min(1,knockbackResistance)))*(boss?BOSS_MOVE:1);}
    /** Pure: the horizontal pull (blocks per tick) towards the flare for a mob {@code dx, dz} away from it: full strength, never past the centre. */
    static double[] pull(double dx,double dz,double scale){
        double d=Math.sqrt(dx*dx+dz*dz);if(d<.4)return new double[]{0,0};
        double step=Math.min(GRAVITY_PULL*scale,d*.5);return new double[]{-dx/d*step,-dz/d*step};
    }
    static int portalTicks(boolean longPortal){return longPortal?6000:SupportRules.PORTAL_LIFETIME_TICKS;}
    static final int AURA_AFTER_PICKUP_TICKS=600,AURA_RADIUS=6;

    /**
     * The numbers the cannon menu prints for one fire support with one player's upgrades (0.0.20): rounds, seconds between rounds, damage of
     * one hit, the area's width and height in blocks, how long a lingering effect lasts (s), the type's own figure (an arrow, the Bunker
     * Buster's blow, a bomblet pulse, a second of napalm) and a total (what a mob that takes every hit takes).
     */
    record Facts(int rounds,double every,double damage,double width,double height,double lasts,double special,double total){}
    static Facts facts(Config c){
        var t=c.type();double r=c.radius(),hit=SupportRules.FIRE_DAMAGE*c.damage(),lasts=c.lingerTicks()/20.0;
        double height=t==FireType.BUNKER||t==FireType.STARSHELL?2*r:t.flat()?GROUND_HEIGHT:r+.5;
        double special=switch(t){case ARROW->2.5*c.damage();case BUNKER->hit*2;case CLUSTER->clusterPulse(c.damage());case NAPALM->napalm(c.damage());default->hit;};
        double total=switch(t){case CLUSTER->special*(CLUSTER_TICKS/CLUSTER_PULSE_TICKS);case NAPALM->special*lasts;case EXPLOSION,NARUKAMI->hit*c.volleys();default->special;};
        return new Facts(c.volleys(),c.interval()/20.0,hit,2*r,height,lasts,special,total);
    }
    /** Pure: a number for the menu: whole numbers without a decimal point, others with one decimal. */
    static String number(double v){double rounded=Math.round(v*10)/10.0;return rounded==Math.rint(rounded)?String.valueOf((long)Math.rint(rounded)):String.valueOf(rounded);}
    /** The arguments of a type's menu lines, in order (%1$s ... %8$s). */
    static Object[] factArgs(Facts f){return new Object[]{f.rounds(),number(f.every()),number(f.damage()),number(f.width()),number(f.height()),number(f.lasts()),number(f.special()),number(f.total())};}

    /** Everything a thrown flare needs to know, frozen at the moment of the throw. */
    record Config(FireType type,int volleys,int interval,float damage,boolean tunnel,boolean slow,boolean longPortal,boolean aura,int aoe,int volleyLevel){
        double radius(){return CannonUpgrades.radius(type,aoe);}
        /** How long this call's effect keeps working after its round lands (see {@link CannonUpgrades#lingerTicks}). */
        int lingerTicks(){return CannonUpgrades.lingerTicks(type,volleyLevel);}
        static Config of(int[] levels,FireType type){
            int[] l=levels.length>=Upgrade.values().length?levels:java.util.Arrays.copyOf(levels,Upgrade.values().length);
            return new Config(type,CannonUpgrades.volleys(type,l[Upgrade.VOLLEY.ordinal()]),CannonUpgrades.interval(l[Upgrade.RATE.ordinal()]),CannonUpgrades.damage(l[Upgrade.DAMAGE.ordinal()]),
                l[Upgrade.QUANTUM.ordinal()]>0,l[Upgrade.SLOW.ordinal()]>0,l[Upgrade.PORTAL.ordinal()]>0,l[Upgrade.AURA.ordinal()]>0,l[Upgrade.AOE.ordinal()],l[Upgrade.VOLLEY.ordinal()]);
        }
    }
}
