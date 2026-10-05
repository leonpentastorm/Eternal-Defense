package dev.createarsenal.beacon;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RulesTest {
    @Test void selectedLevelCapsBothDifficultyAndPayoutIncludingPaidBonuses(){
        assertEquals(2,Rules.selectedTier(2,10,3));assertEquals(0,Rules.selectedTier(0,10,3));assertEquals(4,Rules.selectedTier(10,1,3));assertEquals(10,Rules.selectedTier(10,10,3));
        for(int cap=0;cap<=10;cap++)for(int base=0;base<=10;base++)assertTrue(Rules.selectedTier(cap,base,3)<=cap);
    }
    @Test void unselectedSaveDefaultKeepsFullProgressionAndRejectsOverflow(){
        assertEquals(7,Rules.selectedTier(10,7,0));assertEquals(0,Rules.selectedTier(-1,10,3));assertEquals(10,Rules.selectedTier(999,999,999));assertEquals(0,Rules.selectedTier(10,-999,-999));
    }
    @Test void soloFirstReinforcementsScaleWithoutUnboundedDensity(){
        assertEquals(19,Rules.enemies(6,1,1));assertEquals(26,Rules.enemies(6,8,1));
        assertEquals(32,Rules.enemies(6,1,2));assertEquals(43,Rules.enemies(6,8,2));
        for(int tier=0;tier<=10;tier++)for(int wave=1;wave<=8;wave++){
            int solo=Rules.waveEnemies(tier,wave,1,0,false),duo=Rules.waveEnemies(tier,wave,2,0,false);
            assertTrue(duo>solo&&duo<solo*2);assertTrue(Rules.waveEnemies(tier,wave,100,3,true)<=144);
            assertTrue(Rules.concurrentAttackers(1,tier)<=24&&Rules.concurrentAttackers(100,tier)<=48);
        }
    }
    @Test void veteranEscalationBuysReinforcementsAndStopsAtThirtyPercent(){
        assertEquals(0,Rules.veteranPressure(4,1000));assertEquals(0,Rules.veteranPressure(6,2));assertEquals(1,Rules.veteranPressure(6,3));assertEquals(3,Rules.veteranPressure(6,100000));
        assertEquals(25,Rules.waveEnemies(6,1,1,3,false));assertEquals(650,Rules.scaledBossHealth(6,1));assertEquals(943,Rules.scaledBossHealth(6,2));assertEquals(1528,Rules.scaledBossHealth(6,1000));
    }
    @Test void platformAgesRaiseScoreAndHaveARewardConsequence(){
        assertEquals(40,Rules.blockScore("arsenal_beacon:gun_platform#platform",1));
        assertEquals(450,Rules.blockScore("arsenal_beacon:gun_platform#platform",5));
        assertEquals(450,Rules.blockScore("arsenal_beacon:gun_platform#platform",500));
        assertEquals(0,Rules.rewardTier(0,3*Rules.platformScore(1)));
        assertEquals(1,Rules.rewardTier(0,3*Rules.platformScore(3)));
        assertEquals(2,Rules.rewardTier(0,3*Rules.platformScore(5)));
    }
    @Test void hudHidesOutsideRaidsUnlessControllerIsHeld() {
        assertFalse(Rules.showHud("disabled",false,true));assertFalse(Rules.showHud("preparation",false,true));
        assertTrue(Rules.showHud("raid",false,true));assertTrue(Rules.showHud("disabled",true,true));
        assertFalse(Rules.showHud("unplaced",true,true));assertFalse(Rules.showHud("decommissioning",true,true));assertFalse(Rules.showHud("raid",true,false));
    }
    @Test void starterWavesGiveTheSlowFlintlockTimeToWork() {
        assertEquals(4,Rules.enemies(0,1,1));assertEquals(7,Rules.enemies(0,1,2));assertEquals(6,Rules.enemies(0,3,1));
        assertEquals(2,Rules.contactDamage(0,0));assertEquals(1,Rules.contactDamage(0,4));assertTrue(Rules.contactDamage(5,0)>Rules.contactDamage(0,0));
    }
    @Test void attackersAreRevealedAtOneMinuteOrImmediatelyWithReconnaissance() {
        assertFalse(Rules.highlightAttackers(0,1199));
        assertTrue(Rules.highlightAttackers(0,1200));
        assertTrue(Rules.highlightAttackers(0,3600));
        assertTrue(Rules.highlightAttackers(1,0));
    }
    @Test void earlyCampaignRemainsWeekly() {
        assertEquals(7, Rules.intervalDays(Rules.rewardTier(0,0)));
        assertEquals(7, Rules.intervalDays(Rules.rewardTier(0,499)));
        assertEquals(6, Rules.intervalDays(Rules.rewardTier(1,0)));
        assertEquals(2, Rules.intervalDays(99));
    }
    @Test void defensesStayBoundedForCoop() {
        assertTrue(Rules.enemies(5,8,30)<=144);
        assertEquals(24, Rules.radius(100));
        assertEquals(8, Rules.waves(100));
    }
    @Test void deathsPenalizeTheSharedBeaconAtTheThresholdOnly() {
        assertEquals(0, Rules.deathPenalty(2,3,1000));
        assertEquals(100, Rules.deathPenalty(3,3,1000));
        assertEquals(100, Rules.deathPenalty(6,3,1000));
    }
    @Test void countOnlyMaterialBiasIsRetiredAndBossRaidsAreBounded() {
        assertEquals(0, Rules.blockScore("minecraft:dirt",100000));
        assertEquals(Rules.blockScore("create:mechanical_press",24), Rules.blockScore("create:mechanical_press",1000));
        assertEquals(Rules.blockScore("minecraft:stone_bricks",1),Rules.blockScore("create:mechanical_press",1));
        assertFalse(Rules.hardRaid(1));assertFalse(Rules.hardRaid(2));assertTrue(Rules.hardRaid(3));assertTrue(Rules.hardRaid(6));assertFalse(Rules.hardRaid(7));
        assertEquals(100,Rules.bossHealth(0));assertEquals(650,Rules.bossHealth(500));
    }
}
