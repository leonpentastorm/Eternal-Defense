package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The pure decisions behind stuck raiders: the progress tracker, the escalation ladder, corridor scoring and the rescue place filter. */
final class StuckRaiderTest {
    private static final long S=RaidMarch.TICKS_PER_SECOND;

    // ---- progress tracker ----------------------------------------------------------------------------------------------------------
    @Test void realProgressRestartsTheStallClockAndMovesTheReference(){
        var t=RaidMarch.start(100,0);
        t=RaidMarch.observe(t,96,10*S,false); // 4 blocks closer
        assertEquals(96,t.best());assertEquals(10*S,t.improvedAt());
        assertFalse(RaidMarch.stuck(t,10*S+24*S,false));
        assertTrue(RaidMarch.stuck(t,10*S+25*S,false));
    }
    @Test void aCreepOfLessThanThreeBlocksIsNotProgressUntilItAddsUp(){
        var t=RaidMarch.start(100,0);
        t=RaidMarch.observe(t,98.5,5*S,false);assertEquals(100,t.best(),"1.5 blocks is not progress");assertEquals(0,t.improvedAt());
        t=RaidMarch.observe(t,97.1,12*S,false);assertEquals(100,t.best(),"2.9 blocks is still not progress");
        t=RaidMarch.observe(t,97.0,20*S,false);assertEquals(97,t.best(),"exactly three blocks is progress");assertEquals(20*S,t.improvedAt());
    }
    @Test void movingAwayOrStandingStillNeverCountsAsProgress(){
        var t=RaidMarch.start(60,0);
        for(int sec=1;sec<=24;sec++)t=RaidMarch.observe(t,60+sec,sec*S,false);
        assertEquals(60,t.best());assertEquals(0,t.improvedAt());assertTrue(RaidMarch.stuck(t,25*S,false));
    }
    @Test void aRaiderIsStuckAfterExactlyTwentyFiveSecondsWithoutProgress(){
        var t=RaidMarch.start(80,1000);
        assertFalse(RaidMarch.stuck(t,1000+25*S-1,false));assertTrue(RaidMarch.stuck(t,1000+25*S,false));
        assertEquals(25,RaidMarch.STUCK_SECONDS);assertEquals(3,RaidMarch.PROGRESS_BLOCKS);
    }
    @Test void aBusyRaiderIsNeverStuckAndBeingBusyRestartsTheClock(){
        var t=RaidMarch.start(80,0);
        assertFalse(RaidMarch.stuck(t,40*S,true),"busy right now");
        // busy at second 40: the stall clock restarts, so the raider gets the whole 25 s again once it stops
        t=RaidMarch.observe(t,80,40*S,true);assertEquals(40*S,t.improvedAt());
        assertFalse(RaidMarch.stuck(t,40*S+24*S,false));assertTrue(RaidMarch.stuck(t,40*S+25*S,false));
    }
    @Test void aRescuedRaiderGetsAFreshClockAndAGraceWindow(){
        var t=RaidMarch.rescued(150,1000);
        assertEquals(150,t.best());assertEquals(1000,t.improvedAt());assertEquals(1000+10*S,t.graceUntil());assertEquals(0,t.dry());
        assertFalse(RaidMarch.stuck(t,1000+10*S,false),"grace and the clock both still run");
        assertFalse(RaidMarch.stuck(t,1000+24*S,false));assertTrue(RaidMarch.stuck(t,1000+25*S,false));
    }
    @Test void theGraceWindowAloneDefersAFlagOnAnOldStall(){
        var t=RaidMarch.start(90,0); // stalled since second 0
        assertTrue(RaidMarch.stuck(t,600,false));
        var dry=RaidMarch.dry(t,600);
        assertEquals(1,dry.dry());assertEquals(0,dry.improvedAt(),"the stall itself is not forgiven");
        assertFalse(RaidMarch.stuck(dry,600+10*S-1,false));assertTrue(RaidMarch.stuck(dry,600+10*S,false));
    }
    @Test void progressKeepsTheGraceAndForgetsDryAttempts(){
        var t=RaidMarch.dry(RaidMarch.start(90,0),600);
        var moved=RaidMarch.observe(t,80,700,false);
        assertEquals(0,moved.dry());assertEquals(t.graceUntil(),moved.graceUntil());assertEquals(80,moved.best());
    }
    @Test void aFirstSightingStartsATracker(){
        var t=RaidMarch.observe(null,42,77,false);assertEquals(42,t.best());assertEquals(77,t.improvedAt());assertFalse(RaidMarch.stuck(t,77,false));
    }

    // ---- busy ------------------------------------------------------------------------------------------------------------------------
    @Test void busyMeansMeleeReachFiringRangeARecentBreachADefenderOrAFrozenChunk(){
        assertTrue(RaidMarch.inMeleeReach(2.0,2.2,true));assertTrue(RaidMarch.inMeleeReach(2.2,2.2,true));
        assertFalse(RaidMarch.inMeleeReach(2.0,2.2,false),"in reach but behind a block is not attacking");assertFalse(RaidMarch.inMeleeReach(2.3,2.2,true));
        assertTrue(RaidMarch.holdingRange(true,10,true));assertTrue(RaidMarch.holdingRange(true,RaidMarch.FIRING_DISTANCE,true));
        assertFalse(RaidMarch.holdingRange(true,15,true));assertFalse(RaidMarch.holdingRange(true,10,false));assertFalse(RaidMarch.holdingRange(false,10,true),"a melee raider is not holding a firing distance");
        assertTrue(RaidMarch.breachedRecently(1000,1000+5*S));assertFalse(RaidMarch.breachedRecently(1000,1000+5*S+1));assertFalse(RaidMarch.breachedRecently(-1,10),"never breached");
        assertTrue(RaidMarch.fought(true,true));assertFalse(RaidMarch.fought(true,false),"a bystander next to an unreachable raider is no fight");assertFalse(RaidMarch.fought(false,true),"hurt by something far away");assertEquals(12,RaidMarch.ENGAGED_RADIUS);
        assertFalse(RaidMarch.busy(false,false,false,false,false));
        assertTrue(RaidMarch.busy(true,false,false,false,false));assertTrue(RaidMarch.busy(false,true,false,false,false));assertTrue(RaidMarch.busy(false,false,true,false,false));
        assertTrue(RaidMarch.busy(false,false,false,true,false));assertTrue(RaidMarch.busy(false,false,false,false,true));
    }

    // ---- escalation ladder -----------------------------------------------------------------------------------------------------------
    @Test void aRaiderIsRescuedThreeTimesAndWithdrawnOnTheFourthStall(){
        assertEquals(3,RaidMarch.MAX_RESCUES);
        int rescues=0;var steps=new ArrayList<RaidMarch.Step>();
        for(int stall=0;stall<4;stall++){var step=RaidMarch.next(rescues,false);steps.add(step);if(step==RaidMarch.Step.RESCUE)rescues++;}
        assertEquals(List.of(RaidMarch.Step.RESCUE,RaidMarch.Step.RESCUE,RaidMarch.Step.RESCUE,RaidMarch.Step.WITHDRAW),steps);
    }
    @Test void aBossIsAlwaysRescuedAndNeverWithdrawn(){
        for(int rescues:new int[]{0,1,2,3,4,10,1000})assertEquals(RaidMarch.Step.RESCUE,RaidMarch.next(rescues,true));
        for(int dry=0;dry<20;dry++)assertEquals(RaidMarch.Step.RETRY,RaidMarch.afterNoDestination(dry,true));
    }
    @Test void aRaiderWithNoPlaceToGoIsRetriedTwiceThenWithdrawn(){
        assertEquals(RaidMarch.Step.RETRY,RaidMarch.afterNoDestination(1,false));assertEquals(RaidMarch.Step.RETRY,RaidMarch.afterNoDestination(2,false));
        assertEquals(RaidMarch.Step.WITHDRAW,RaidMarch.afterNoDestination(3,false));assertEquals(3,RaidMarch.DRY_LIMIT);
    }
    @Test void theWithdrawalLineCountsTheWave(){
        assertEquals("1 stuck raider withdrew",RaidMarch.withdrawnMessage(1));assertEquals("2 stuck raiders withdrew",RaidMarch.withdrawnMessage(2));
    }

    // ---- bookkeeping: nothing is kept for raiders that are gone ----------------------------------------------------------------------
    @Test void pruningDropsEveryEntryOfAGoneRaiderAndResetClearsTheRest(){
        RaidMarch.forget();
        var alive=UUID.randomUUID();var gone=UUID.randomUUID();
        RaidMarch.track(alive,RaidMarch.start(50,0));RaidMarch.track(gone,RaidMarch.start(50,0));
        assertEquals(2,RaidMarch.kept());
        RaidMarch.prune(Set.of(alive));assertEquals(1,RaidMarch.kept());assertNotNull(RaidMarch.track(alive));assertNull(RaidMarch.track(gone));
        RaidMarch.withdrew();RaidMarch.withdrew();
        RaidMarch.forget();assertEquals(0,RaidMarch.kept());assertEquals(0,RaidMarch.takeWithdrawn());
    }
    @Test void withdrawnRaidersAreCountedOncePerWave(){
        RaidMarch.forget();RaidMarch.withdrew();RaidMarch.withdrew();
        assertEquals(2,RaidMarch.takeWithdrawn());assertEquals(0,RaidMarch.takeWithdrawn(),"announced once, then the count starts again");
    }

    // ---- marchable corridors ---------------------------------------------------------------------------------------------------------
    private static int[] flat(int n,int y){int[] s=new int[n];Arrays.fill(s,y);return s;}
    @Test void aFlatDryCorridorIsClean(){
        var s=RaidSpawns.score(24,flat(30,64),new boolean[30],24+29*RaidSpawns.PROBE_STEP);
        assertTrue(s.clean());assertEquals(0,s.unknown());
    }
    @Test void aSurfaceFluidOrMagmaIsAViolation(){
        var hazard=new boolean[30];hazard[10]=true;
        assertEquals(1,RaidSpawns.score(24,flat(30,64),hazard,24+29*4).violations());
        hazard[11]=true;assertEquals(2,RaidSpawns.score(24,flat(30,64),hazard,24+29*4).violations());
    }
    @Test void onlyTheStretchBetweenTheSpawnAndTheZoneIsJudged(){
        var hazard=new boolean[30];hazard[20]=true; // 80 blocks further out than the zone end
        assertTrue(RaidSpawns.score(24,flat(30,64),hazard,24+10*4).clean(),"a spawn nearer than the lake never crosses it");
        assertFalse(RaidSpawns.score(24,flat(30,64),hazard,24+20*4).clean(),"a spawn exactly at the lake does");
        assertFalse(RaidSpawns.score(24,flat(30,64),hazard,24+29*4).clean());
    }
    @Test void stepsAndDropsAreJudgedInTheDirectionOfTheMarch(){
        // sample 0 is nearest the zone; the raider walks from the high index to the low one
        int[] up={64,61};   // walking inwards the ground rises by 3: too steep
        assertEquals(1,RaidSpawns.score(24,up,new boolean[2],28).violations());
        int[] okUp={64,62}; // a rise of 2 is fine
        assertTrue(RaidSpawns.score(24,okUp,new boolean[2],28).clean());
        int[] down={60,64}; // walking inwards it drops by 4: a fall
        assertEquals(1,RaidSpawns.score(24,down,new boolean[2],28).violations());
        int[] okDown={61,64}; // a drop of 3 is fine
        assertTrue(RaidSpawns.score(24,okDown,new boolean[2],28).clean());
    }
    @Test void aCliffAndALakeAddUp(){
        int[] surface={64,64,70,70,66,66};boolean[] hazard={false,false,false,true,false,false};
        // pairs: 64-64 ok; 64-70 rise 64-70=-6 -> drop of 6 (violation); 70-70 ok; 70-66 rise 4 (violation); 66-66 ok; plus the lake
        assertEquals(3,RaidSpawns.score(24,surface,hazard,24+5*4).violations());
    }
    @Test void unknownSamplesAreNeitherFailuresNorJudged(){
        int[] surface={64,RaidSpawns.UNKNOWN,90,90};
        var s=RaidSpawns.score(24,surface,new boolean[4],24+3*4);
        assertTrue(s.clean(),"the 26 block jump next to an unknown sample is not judged");assertEquals(1,s.unknown());
        assertEquals(0,RaidSpawns.score(24,new int[]{RaidSpawns.UNKNOWN,RaidSpawns.UNKNOWN},new boolean[2],28).violations());
    }
    @Test void aDistanceInsideTheProbedStretchCountsOnlyItsSamples(){
        assertEquals(0,RaidSpawns.score(24,flat(30,64),new boolean[30],10).unknown());
        var short1=RaidSpawns.score(24,new int[]{64},new boolean[]{true},24);assertEquals(1,short1.violations(),"one sample at the zone end");
        assertTrue(RaidSpawns.score(24,new int[]{64},new boolean[]{true},23).clean(),"a spawn nearer than the first sample crosses nothing");
    }
    @Test void everyWedgeCentreMapsBackToItsWedgeAtAnyDistance(){
        var origin=new BlockPos(1000,64,-500);
        for(int w=0;w<RaidSpawns.WEDGES;w++)for(int distance:new int[]{72,100,136,192}){
            var p=RaidSpawns.ringAt(origin,distance,(w+.5)*RaidSpawns.WEDGE);
            assertEquals(w,RaidSpawns.wedgeOf(origin,p),"wedge "+w+" at "+distance);
        }
        assertEquals(0,RaidSpawns.wedgeOf(5,0));assertEquals(3,RaidSpawns.wedgeOf(0,5));assertEquals(6,RaidSpawns.wedgeOf(-5,0));assertEquals(9,RaidSpawns.wedgeOf(0,-5));assertEquals(11,RaidSpawns.wedgeOf(5,-.01));
    }

    // ---- rescue place --------------------------------------------------------------------------------------------------------------
    @Test void aRescueNeedsEveryPlayerAtLeastTwentyFourBlocksAway(){
        var spot=new Vec3(0,64,0);
        assertTrue(RaidSpawns.farFromPlayers(spot,List.of(),24));
        assertTrue(RaidSpawns.farFromPlayers(spot,List.of(new Vec3(24,64,0)),24),"exactly 24 is far enough");
        assertFalse(RaidSpawns.farFromPlayers(spot,List.of(new Vec3(23.9,64,0)),24));
        assertFalse(RaidSpawns.farFromPlayers(spot,List.of(new Vec3(100,64,0),new Vec3(0,64,10)),24),"one close player is enough to refuse");
        assertFalse(RaidSpawns.farFromPlayers(spot,List.of(new Vec3(0,64+23,0)),24),"height counts too");
        assertEquals(24,RaidSpawns.RESCUE_PLAYER_DISTANCE);
    }
    @Test void aRescuePrefersAnotherWedgeThanTheOneItWasStuckIn(){
        for(int stuck=0;stuck<RaidSpawns.WEDGES;stuck++)for(int start=0;start<RaidSpawns.WEDGES;start++){
            var order=RaidSpawns.rescueOrder(stuck,start);
            assertEquals(RaidSpawns.WEDGES,order.size());assertEquals(RaidSpawns.WEDGES,new HashSet<>(order).size(),"every wedge once");
            assertEquals(stuck,order.get(order.size()-1),"the stuck wedge is the last resort");
            assertNotEquals(stuck,order.get(0));
        }
        assertEquals(List.of(5,6,7,8,9,10,11,0,1,2,3,4),RaidSpawns.rescueOrder(4,5),"others in order from the start, the stuck one last");
    }
    @Test void aRescueUsesTheStuckWedgeOnlyWhenNothingElseQualifies(){
        var asked=new ArrayList<Integer>();
        assertEquals("other",RaidSpawns.pickRescue(4,0,w->{asked.add(w);return w==9?"other":null;}));
        assertFalse(asked.contains(4),"the stuck wedge was never needed");
        asked.clear();
        assertEquals("same",RaidSpawns.pickRescue(4,0,w->{asked.add(w);return w==4?"same":null;}));
        assertEquals(4,asked.get(asked.size()-1));assertEquals(RaidSpawns.WEDGES,asked.size());
        assertNull(RaidSpawns.pickRescue(4,0,w->null));
    }

    // ---- diagnostics -------------------------------------------------------------------------------------------------------------------
    @Test void theLogLineIsOneLineOfKeyValuePairsStartingWithTheEvent(){
        var f=new LinkedHashMap<String,String>();f.put("type","minecraft:zombie");f.put("forcedMove","3s ago");f.put("dest","-");
        assertEquals("[stuck] event=RESCUE type=minecraft:zombie forcedMove=3s ago dest=-",RaidRescue.line("RESCUE",f));
        assertFalse(RaidRescue.logging(),"the diagnostics are off unless -Darsenal.stuckLog=true");
    }
}
