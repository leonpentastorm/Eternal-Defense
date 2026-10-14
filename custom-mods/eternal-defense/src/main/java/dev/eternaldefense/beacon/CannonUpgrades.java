package dev.eternaldefense.beacon;

/** Pure rules for the Support Cannon's upgrades and the twelve fire support types. */
final class CannonUpgrades {
    private CannonUpgrades(){}

    enum Upgrade {
        TRAVERSE("traverse",3),          // Faster turret
        RATE("rate",3),                  // Faster fire rate
        VOLLEY("volley",3),              // More shells per call
        DAMAGE("damage",3),
        QUANTUM("quantum",1),            // Fire support works below ground
        SLOW("slow",1),                  // Slowness field over the whole area
        PORTAL("portal",1),              // 5 minute portal that can be used twice
        AURA("aura",1),                  // Healing aura round a supply flare
        AOE("aoe",3),                    // Wider area of effect for every fire support
        DIMENSION("dimension",1);        // Fire support can be called in the other dimensions
        /** The levels each upgrade has; what each level costs (factory parts) is in Economy.PACK_AMOUNTS. */
        final String id;final int levels;
        Upgrade(String id,int levels){this.id=id;this.levels=levels;}
        int max(){return levels;}
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
        /** A single round whatever the Volley upgrade: the Bunker Buster's bomb, the starshell */
        boolean single(){return this==BUNKER||this==STARSHELL||lingers();}
        /** One round whose effect lasts on the ground; the Volley upgrade makes it last longer instead of firing */
        boolean lingers(){return this==CLUSTER||this==NAPALM||this==CRYO||this==GRAVITY;}
        /** The flat box (2 blocks high) of the ground effects napalm and frost. */
        boolean flat(){return this==NAPALM||this==CRYO;}
        static FireType of(int ordinal){var v=values();return v[Math.max(0,Math.min(v.length-1,ordinal))];}
    }

    static final int SHELL_BASE=SupportRules.FIRE_BLASTS,VOLLEY_STEP=2;
    /** Half-width of the area of a Bunker Buster, in blocks: a cube this far each way that is dug out completely. */
    static final int BUNKER_RADIUS=8;
    /** The Bunker Buster's blow is worth this many Explosion Barrage shells at every Damage level (2 before). */
    static final int BUNKER_HITS=5;
    /** Every Area of Effect level widens the area by this much */
    static final double AOE_STEP=1.5,BUNKER_AOE_STEP=1;

    /** Shells per call: the base six, plus the type's bonus and two per Volley level. */
    static int volleys(FireType type,int volleyLevel){
        if(type.single())return 1;
        return Math.max(1,SHELL_BASE+type.volleyDelta+VOLLEY_STEP*Math.max(0,volleyLevel));
    }
    /** Ticks between shells: 40, 33, 26, 20. */
    static int interval(int rateLevel){return Math.max(20,SupportRules.FIRE_INTERVAL_TICKS-7*Math.max(0,rateLevel));}
    /** +25% damage per level. */
    static float damage(int level){return 1f+.25f*Math.max(0,level);}
    /** Turret speed multiplier by Faster traverse level: with no upgrade a turn takes 1.5 times as long as it did */
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

    // The six types added reworked (shares are of SupportRules.FIRE_DAMAGE, before the Damage upgrade)
    /** How long a lingering type keeps working after its round lands (ticks) */
    static int lingerTicks(FireType type,int volleyLevel){
        int lv=Math.max(0,volleyLevel);
        return switch(type){
            case CLUSTER->CLUSTER_TICKS;case NAPALM->NAPALM_TICKS+NAPALM_STEP*lv;case CRYO->CRYO_TICKS+CRYO_STEP*lv;case GRAVITY->GRAVITY_TICKS+GRAVITY_STEP*lv;
            case STARSHELL->STAR_TICKS+STAR_STEP*lv;default->0;
        };
    }
    /** Cluster Strike: one shell that bursts over the box and keeps a rain of bomblets going for CLUSTER_TICKS */
    static final int CLUSTER_TICKS=240,CLUSTER_PULSE_TICKS=10,BOMBLETS_PER_PULSE=3,CLUSTER_HITS=6;static final double CLUSTER_SPREAD=1.75;
    /** Height over the flare at which a Cluster Strike shell bursts. */
    static final double CLUSTER_BURST_HEIGHT=6;
    /** Pure: what one bomblet pulse deals to every hostile mob in the box. */
    static float clusterPulse(float multiplier){return SupportRules.FIRE_DAMAGE*CLUSTER_HITS*multiplier/(CLUSTER_TICKS/CLUSTER_PULSE_TICKS);}
    /** Napalm Carpet and Cryo Shell cover a flat box, 2 blocks high, GROUND_SPREAD times as wide as a barrage's. */
    static final double GROUND_SPREAD=1.2,GROUND_HEIGHT=2;
    /** Napalm Carpet: the ground burns */
    static final int NAPALM_TICKS=200,NAPALM_STEP=80,NAPALM_BURN_SECONDS=4,FIRE_ZONE_MARGIN=4;static final float NAPALM_SHARE=.2f;
    /** Pure: what the burning ground deals each second to a mob standing in it. */
    static float napalm(float multiplier){return hit(NAPALM_SHARE,multiplier);}
    /** Spotter Arrows: each volley every hostile mob in the box takes one arrow hit of FireMarks.SPOTTER_HIT */
    static float spotterHit(float multiplier){return FireMarks.SPOTTER_HIT*multiplier;}
    /** Curse of Debilitation: what every mob one shell touches keeps until it dies. */
    record Curse(String effect,int amplifier){}
    static final java.util.List<Curse> CURSES=java.util.List.of(new Curse("minecraft:wither",1),new Curse("minecraft:poison",1),new Curse("minecraft:weakness",1),new Curse("minecraft:slowness",2),new Curse("minecraft:blindness",0));
    /** Cryo Shell: no damage. */
    static final int CRYO_TICKS=300,CRYO_STEP=100,CRYO_SLOW_AMPLIFIER=4,CRYO_BOSS_AMPLIFIER=2;
    /** The Cryo Shell's frost reaches this many blocks further on every side than the napalm's; not higher. */
    static final double CRYO_EXTRA=3;
    /** Gravity Well: no damage; every hostile mob in its (twice as wide) box is dragged to the flare */
    static final int GRAVITY_TICKS=120,GRAVITY_STEP=40;static final double GRAVITY_PULL=.16,WELL_SPREAD=2;
    /** Shockwave: no damage. */
    static final int SHOCK_PUSH_TICKS=16;
    /** Pure: the outward speed (blocks per tick) that carries a mob distance from the flare out past radius. */
    static double shockSpeed(double distance,double radius){return Math.max(.5,Math.min(1.6,(radius+1-distance)*.35));}
    /** A raid boss is moved by a gravity well this much less (on top of its own knockback resistance). */
    static final double BOSS_MOVE=.35;
    /** Starshell: bursts high over the flare and burns for 30 seconds, sinking slowly. */
    static final int STAR_TICKS=600,STAR_STEP=200,STAR_GLOW_TICKS=100,STAR_SPEED_TICKS=600,STAR_PULSE_TICKS=40,STAR_LIGHT_SPACING=4;static final double STAR_RADIUS=16,STAR_AOE_STEP=2,STAR_BURST_HEIGHT=14;

    /** Pure: the damage of one hit worth share of a shell, with the Damage upgrade's multiplier. */
    static float hit(float share,float multiplier){return SupportRules.FIRE_DAMAGE*share*multiplier;}
    /** Pure: how strongly a gravity well moves a mob: less with knockback resistance, much less for a raid boss. */
    static double moveScale(double knockbackResistance,boolean boss){return Math.max(0,1-Math.max(0,Math.min(1,knockbackResistance)))*(boss?BOSS_MOVE:1);}
    /** Pure: the horizontal pull (blocks per tick) towards the flare for a mob dx, dz away */
    static double[] pull(double dx,double dz,double scale){
        double d=Math.sqrt(dx*dx+dz*dz);if(d<.4)return new double[]{0,0};
        double step=Math.min(GRAVITY_PULL*scale,d*.5);return new double[]{-dx/d*step,-dz/d*step};
    }
    static int portalTicks(boolean longPortal){return longPortal?6000:SupportRules.PORTAL_LIFETIME_TICKS;}
    static final int AURA_AFTER_PICKUP_TICKS=600,AURA_RADIUS=6;

    /** The numbers the cannon menu prints for one fire support with one player's upgrades */
    record Facts(int rounds,double every,double damage,double width,double height,double lasts,double special,double total,double ablaze){}
    static Facts facts(Config c){
        var t=c.type();double r=c.radius(),hit=SupportRules.FIRE_DAMAGE*c.damage(),lasts=c.lingerTicks()/20.0;
        double height=t==FireType.BUNKER||t==FireType.STARSHELL?2*r:t.flat()?GROUND_HEIGHT:r+.5;
        double special=switch(t){case ARROW->spotterHit(c.damage());case BUNKER->hit*BUNKER_HITS;case CLUSTER->clusterPulse(c.damage());case NAPALM->napalm(c.damage());default->hit;};
        double total=switch(t){case CLUSTER->special*(CLUSTER_TICKS/CLUSTER_PULSE_TICKS);case NAPALM->special*lasts;case EXPLOSION,NARUKAMI->hit*c.volleys();case ARROW->special*c.volleys();default->special;};
        return new Facts(c.volleys(),c.interval()/20.0,hit,2*r,height,lasts,special,total,FireMarks.ablazeFor(c.damage()));
    }
    /** Pure: a number for the menu: whole numbers without a decimal point, others with one decimal. */
    static String number(double v){double rounded=Math.round(v*10)/10.0;return rounded==Math.rint(rounded)?String.valueOf((long)Math.rint(rounded)):String.valueOf(rounded);}
    /** The arguments of a type's menu lines, in order (%1$s... %9$s). */
    static Object[] factArgs(Facts f){return new Object[]{f.rounds(),number(f.every()),number(f.damage()),number(f.width()),number(f.height()),number(f.lasts()),number(f.special()),number(f.total()),number(f.ablaze())};}

    /** Everything a thrown flare needs to know, frozen at the moment of the throw. */
    record Config(FireType type,int volleys,int interval,float damage,boolean tunnel,boolean slow,boolean longPortal,boolean aura,int aoe,int volleyLevel){
        double radius(){return CannonUpgrades.radius(type,aoe);}
        /** How long this call's effect keeps working after its round lands (see CannonUpgrades.lingerTicks). */
        int lingerTicks(){return CannonUpgrades.lingerTicks(type,volleyLevel);}
        static Config of(int[] levels,FireType type){
            int[] l=levels.length>=Upgrade.values().length?levels:java.util.Arrays.copyOf(levels,Upgrade.values().length);
            return new Config(type,CannonUpgrades.volleys(type,l[Upgrade.VOLLEY.ordinal()]),CannonUpgrades.interval(l[Upgrade.RATE.ordinal()]),CannonUpgrades.damage(l[Upgrade.DAMAGE.ordinal()]),
                l[Upgrade.QUANTUM.ordinal()]>0,l[Upgrade.SLOW.ordinal()]>0,l[Upgrade.PORTAL.ordinal()]>0,l[Upgrade.AURA.ordinal()]>0,l[Upgrade.AOE.ordinal()],l[Upgrade.VOLLEY.ordinal()]);
        }
    }
}
