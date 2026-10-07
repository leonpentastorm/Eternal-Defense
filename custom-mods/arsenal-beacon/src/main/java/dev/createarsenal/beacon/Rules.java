package dev.createarsenal.beacon;

/** Pure campaign rules, isolated so pacing and upgrades can be tested without Minecraft. */
public final class Rules {
    private Rules() {}
    static int selectedTier(int cap,int base,int bonus){return Math.max(0,Math.min(Math.max(0,Math.min(10,cap)),Math.min(10,Math.max(0,base)+Math.max(0,Math.min(3,bonus)))));}
    public static int rewardTier(int logistics, int baseScore) {
        return Math.min(5,Math.max(0,logistics)) + buildingTier(baseScore);
    }
    public static int buildingTier(int score){return Math.min(5,Math.max(0,score)/500);}
    public static int branchMaximum(String branch){return branch.equals("reconnaissance")?1:branch.equals("logistics")?5:branch.equals("core")?3:4;}
    public static int intervalDays(int tier) { return Math.max(2, 7 - Math.max(0, Math.min(5, tier))); }
    public static boolean hardRaid(int raidNumber){return raidNumber>0&&raidNumber%3==0;}
    public static int bossHealth(int tier){return new int[]{100,160,240,360,500,650}[Math.max(0,Math.min(5,tier))];}
    public static int radius(int core) { return new int[]{8,12,18,24}[Math.max(0,Math.min(3,core))]; }
    public static int maximumHealth(int core) { return new int[]{1000,1250,1500,2000}[Math.max(0,Math.min(3,core))]; }
    public static int waves(int tier) { return 3 + Math.min(5, Math.max(0, tier)); }
    public static boolean highlightAttackers(int reconnaissance, long waveTicks) { return reconnaissance > 0 || waveTicks >= 1200L; }
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
    public static int below(int vertical){return new int[]{3,6,10,13,16}[Math.max(0,Math.min(4,vertical))];}
    public static int above(int vertical){return new int[]{8,16,24,36,48}[Math.max(0,Math.min(4,vertical))];}
    public static boolean showHud(String phase,boolean holdingController,boolean nearby){return nearby&&!phase.equals("unplaced")&&!phase.equals("decommissioning")&&(phase.equals("raid")||holdingController);}
    public static int upgradeCost(int currentLevel) { return 8 * (1 << Math.min(4, Math.max(0, currentLevel))); }
    public static int deathPenalty(int deaths, int threshold, int maximumHealth) {
        return deaths > 0 && deaths % threshold == 0 ? maximumHealth / 10 : 0;
    }
    /** Diversity and diminishing returns prevent a large dirt cube from becoming a reward exploit. */
    public static int blockScore(String id, int count) {
        // Scanner stores the highest Age, rather than quantity, for each platform kind.
        if(id.startsWith("arsenal_beacon:")&&id.endsWith("#platform"))return platformScore(count);
        // Old count-only summaries retain platform compatibility. Current construction and
        // architecture scores require real positions and geometry in BaseScoring.analyze.
        return 0;
    }
    public static int platformScore(int age){return new int[]{0,40,90,180,300,450}[Math.max(0,Math.min(5,age))];}
}
