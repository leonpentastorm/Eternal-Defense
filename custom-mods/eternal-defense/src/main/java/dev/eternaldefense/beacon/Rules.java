package dev.eternaldefense.beacon;

/** Pure campaign rules, isolated so pacing and upgrades can be tested without Minecraft. */
public final class Rules {
    private Rules() {}
    static int selectedTier(int cap,int base,int bonus){return Math.max(0,Math.min(Math.max(0,Math.min(10,cap)),Math.min(10,Math.max(0,base)+Math.max(0,Math.min(3,bonus)))));}
    public static int rewardTier(int logistics, int baseScore) {
        return Math.min(5,Math.max(0,logistics)) + buildingTier(baseScore);
    }
    /** Base score points per building tier (5 at most). */
    public static final int BUILDING_TIER_SCORE=500;
    public static int buildingTier(int score){return Math.min(5,Math.max(0,score)/BUILDING_TIER_SCORE);}
    /** The whole set of the mod's installations in the Home Zone is worth one building tier */
    public static final int INSTALLATION_SET_SCORE=BUILDING_TIER_SCORE;
    public static int installationScore(int present,int of){return of<=0?0:INSTALLATION_SET_SCORE*Math.max(0,Math.min(present,of))/of;}
    public static int branchMaximum(String branch){return branch.equals("reconnaissance")?3:branch.equals("logistics")?5:branch.equals("core")?3:4;}
    /** Active days between raids: 10 at reward tier 0, one less each tier, never below 3 (tier 7 and up). */
    public static int intervalDays(int tier) { return Math.max(3, 10 - Math.max(0, Math.min(10, tier))); }
    /** The night window a raid may start in (game time of day) */
    public static final int RAID_HOUR_FROM=13000,RAID_HOUR_UNTIL=21000;
    public static boolean raidHour(long dayTime){long t=Math.floorMod(dayTime,24000L);return t>=RAID_HOUR_FROM&&t<RAID_HOUR_UNTIL;}
    /** Game ticks until the next raid window opens (0 inside it). */
    public static long untilRaidHour(long dayTime){long t=Math.floorMod(dayTime,24000L);return raidHour(dayTime)?0:Math.floorMod(RAID_HOUR_FROM-t,24000L);}
    public static boolean hardRaid(int raidNumber){return raidNumber>0&&raidNumber%3==0;}
    // Lookup tables are built once: these are asked thousands of times a tick
    private static final int[] BOSS_HEALTH={100,160,240,360,500,650},RADIUS={8,12,18,24},MAXIMUM_HEALTH={1000,1250,1500,2000},BELOW={3,6,10,13,16},ABOVE={8,16,24,36,48},PLATFORM_SCORE={0,40,90,180,300,450};
    public static int bossHealth(int tier){return BOSS_HEALTH[Math.max(0,Math.min(5,tier))];}
    public static int radius(int core) { return RADIUS[Math.max(0,Math.min(3,core))]; }
    public static int maximumHealth(int core) { return MAXIMUM_HEALTH[Math.max(0,Math.min(3,core))]; }
    public static int waves(int tier) { return 3 + Math.min(5, Math.max(0, tier)); }
    /** Ticks into a wave after which the raiders still alive glow through walls, by Reconnaissance level */
    static final int[] REVEAL_TICKS={6000,3600,1200,0};
    public static int revealTicks(int reconnaissance){return REVEAL_TICKS[Math.max(0,Math.min(REVEAL_TICKS.length-1,reconnaissance))];}
    public static boolean highlightAttackers(int reconnaissance, long waveTicks) { return waveTicks >= revealTicks(reconnaissance); }
    public static int enemies(int tier, int wave, int players) {
        int t=Math.max(0,Math.min(10,tier)),w=Math.max(1,Math.min(8,wave)),p=Math.max(1,Math.min(8,players));
        int solo=t==0?3+w:6+t*2+w;
        return Math.min(144,(int)Math.ceil(solo*(1+.65*(p-1))));
    }
    public static int veteranPressure(int tier,int victories){return tier<5?0:Math.min(3,Math.max(0,victories)/3);}
    public static int waveEnemies(int tier,int wave,int players,int veteran,boolean hard){return Math.min(144,(int)Math.ceil(enemies(tier,wave,players)*(1+.1*Math.max(0,Math.min(3,veteran)))*(hard?1.25:1)));}
    public static int concurrentAttackers(int players,int tier){return Math.min(48,Math.min(24,10+Math.max(0,Math.min(10,tier))*2)+8*(Math.max(1,Math.min(8,players))-1));}
    public static int scaledBossHealth(int tier,int players){return (int)Math.ceil(bossHealth(tier)*(1+.45*(Math.max(1,Math.min(4,players))-1)));}
    public static int defensePercent(int level){return Math.max(0,Math.min(4,level))*15;}
    public static int healingPercent(int level){return 5+Math.max(0,Math.min(4,level))*5;}
    public static int contactDamage(int tier,int defense){return Math.max(1,(int)Math.ceil((2+tier*3)*(100-defensePercent(defense))/100.0));}
    public static int below(int vertical){return BELOW[Math.max(0,Math.min(4,vertical))];}
    public static int above(int vertical){return ABOVE[Math.max(0,Math.min(4,vertical))];}
    public static boolean showHud(String phase,boolean holdingController,boolean nearby){return nearby&&!phase.equals("unplaced")&&!phase.equals("decommissioning")&&(phase.equals("raid")||holdingController);}
    public static int upgradeCost(int currentLevel) { return 8 * (1 << Math.min(4, Math.max(0, currentLevel))); }
    /** Defender deaths near the beacon during a raid that cost it a tenth of its health (every third one). */
    public static final int DEATHS_PER_PENALTY = 3;
    /** The deaths left before the next one costs the beacon health (3, 2, 1, then 3 again) */
    public static int livesLeft(int deaths) { return DEATHS_PER_PENALTY - Math.floorMod(deaths, DEATHS_PER_PENALTY); }
    public static int deathPenalty(int deaths, int threshold, int maximumHealth) {
        return deaths > 0 && deaths % threshold == 0 ? maximumHealth / 10 : 0;
    }
    /** Diversity and diminishing returns prevent a large dirt cube from becoming a reward exploit. */
    public static int blockScore(String id, int count) {
        // Scanner stores the highest Age, rather than quantity, for each platform kind.
        if(id.startsWith("eternal_defense:")&&id.endsWith("#platform"))return platformScore(count);
        // Old count-only summaries retain platform compatibility.
        return 0;
    }
    public static int platformScore(int age){return PLATFORM_SCORE[Math.max(0,Math.min(5,age))];}
}
