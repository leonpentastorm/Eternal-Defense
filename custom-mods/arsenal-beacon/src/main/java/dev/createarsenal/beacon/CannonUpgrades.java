package dev.createarsenal.beacon;

/**
 * Pure rules for the Support Cannon's upgrades and the twelve fire support types (six more since 0.0.16). Every player has their own set of levels and one
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
        EXPLOSION("explosion",0),ARROW("arrow",2),NARUKAMI("narukami",-1),BUNKER("bunker",0),HEAL("heal",0),CURSE("curse",0),
        CLUSTER("cluster",-1),CRYO("cryo",-2),NAPALM("napalm",-2),GRAVITY("gravity",-3),SHOCKWAVE("shockwave",-2),STARSHELL("starshell",0);
        final String id;final int volleyDelta;
        FireType(String id,int volleyDelta){this.id=id;this.volleyDelta=volleyDelta;}
        /** The id sent in call announcements and used in language keys. */
        String callId(){return "fire_"+id;}
        boolean damaging(){return this==EXPLOSION||this==ARROW||this==NARUKAMI||this==BUNKER||this==CLUSTER||this==CRYO||this==NAPALM||this==GRAVITY||this==SHOCKWAVE;}
        /** A single round, whatever the Volley upgrade (the Bunker Buster's bomb, the starshell). */
        boolean single(){return this==BUNKER||this==STARSHELL;}
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
    /** Turret speed multiplier: +40% per level (spin time falls accordingly). */
    static float turnSpeed(int level){return 1f+.4f*Math.max(0,level);}
    /** Half-width of the red box in blocks: 4 (8 for a Bunker Buster) plus one step per Area of Effect level. */
    static double radius(FireType type,int aoeLevel){
        int lv=Math.max(0,aoeLevel);
        return switch(type){
            case BUNKER->BUNKER_RADIUS+BUNKER_AOE_STEP*lv;
            case CLUSTER->(SupportRules.BLAST_RADIUS+AOE_STEP*lv)*CLUSTER_SPREAD;
            case STARSHELL->STAR_RADIUS+STAR_AOE_STEP*lv;
            default->SupportRules.BLAST_RADIUS+AOE_STEP*lv;
        };
    }

    // ---- the six types added in 0.0.16 (shares are of SupportRules.FIRE_DAMAGE, before the Damage upgrade) ---------------------------
    /** Cluster Strike: each shell bursts over the box into this many bomblets, scattered over a box this much wider; each hurts mobs within its own radius. */
    static final int CLUSTER_BOMBLETS=6;static final double CLUSTER_SPREAD=1.75,BOMBLET_RADIUS=2.5;static final float BOMBLET_SHARE=.6f;
    /** Height over the flare at which a Cluster Strike shell bursts. */
    static final double CLUSTER_BURST_HEIGHT=6;
    /** Cryo Shell: a small cold hit, then the mobs are frozen solid (shaking) and slowed almost to a stop; a raid boss for half as long. */
    static final float CRYO_SHARE=.32f;static final int CRYO_HOLD_TICKS=100,CRYO_SLOW_AMPLIFIER=4,CRYO_FROZEN_TICKS=300;
    /** Napalm: each shell leaves the box burning for a while; once a second every burning patch a mob stands in hurts it and sets it alight. */
    static final int NAPALM_TICKS=100,NAPALM_BURN_SECONDS=4;static final float NAPALM_SHARE=.2f;
    /** Gravity Well: pulls hostile mobs in the box (and this much beyond it) to the flare for a while, then implodes. */
    static final int GRAVITY_TICKS=60;static final double GRAVITY_PULL=.16,GRAVITY_REACH=3;static final float GRAVITY_SHARE=.8f;
    /** Shockwave: a blast that hurts a little and throws hostile mobs away from the flare. */
    static final float SHOCKWAVE_SHARE=.48f;static final double SHOCKWAVE_PUSH=1.6;
    /** A raid boss is moved by gravity and shockwaves this much less (on top of its own knockback resistance) and frozen half as long. */
    static final double BOSS_MOVE=.35;
    /** Starshell: bursts high over the flare and burns for 30 seconds, sinking slowly; hostile mobs within reach glow, players near it see in the dark. */
    static final int STAR_TICKS=600,STAR_GLOW_TICKS=100,STAR_VISION_TICKS=220,STAR_PULSE_TICKS=40;static final double STAR_RADIUS=16,STAR_AOE_STEP=2,STAR_BURST_HEIGHT=14;

    /** Pure: the damage of one hit worth {@code share} of a shell, with the Damage upgrade's multiplier. */
    static float hit(float share,float multiplier){return SupportRules.FIRE_DAMAGE*share*multiplier;}
    /** Pure: napalm damage for a mob standing in {@code patches} burning patches at once (one hit a second). */
    static float napalm(int patches,float multiplier){return hit(NAPALM_SHARE,multiplier)*Math.max(0,patches);}
    /** Pure: how strongly a mob is moved (gravity, shockwave): less with knockback resistance, much less for a raid boss. */
    static double moveScale(double knockbackResistance,boolean boss){return Math.max(0,1-Math.max(0,Math.min(1,knockbackResistance)))*(boss?BOSS_MOVE:1);}
    /** Pure: the horizontal pull (blocks per tick) towards the flare for a mob {@code dx, dz} away from it: full strength, never past the centre. */
    static double[] pull(double dx,double dz,double scale){
        double d=Math.sqrt(dx*dx+dz*dz);if(d<.4)return new double[]{0,0};
        double step=Math.min(GRAVITY_PULL*scale,d*.5);return new double[]{-dx/d*step,-dz/d*step};
    }
    /** Pure: the velocity a shockwave gives a mob {@code dx, dz} away from the flare (straight up and out; a mob on the flare goes straight up). */
    static double[] push(double dx,double dz,double scale){
        double d=Math.sqrt(dx*dx+dz*dz),s=SHOCKWAVE_PUSH*scale;
        return d<.2?new double[]{0,.45*s,0}:new double[]{dx/d*s,.45*s,dz/d*s};
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
