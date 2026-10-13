package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 0.0.40: the supply source's pure rules: the split between the pack and the Hubs, all or nothing, and the Hub's chat line. */
final class SupplyRulesTest {
    private static boolean[][] all(int sources,int costs){var a=new boolean[sources][costs];for(var row:a)Arrays.fill(row,true);return a;}
    private static int hubUnits(SupplyRules.Allocation a,int[] tier){int n=0;for(int s=0;s<tier.length;s++)if(tier[s]>0)n+=a.from(s);return n;}

    // ---- the split -------------------------------------------------------------------------------------------------------------------
    @Test void thePackPaysFirstAndTheHubOnlyTheRest(){
        var a=SupplyRules.allocate(new int[]{6,64},new int[]{0,1},new int[]{10},all(2,1));
        assertTrue(a.payable());assertEquals(6,a.from(0),"all six from the pack");assertEquals(4,a.from(1),"only the missing four from the Hub");
        var covered=SupplyRules.allocate(new int[]{12,64},new int[]{0,1},new int[]{10},all(2,1));
        assertTrue(covered.payable());assertEquals(10,covered.from(0));assertEquals(0,covered.from(1),"the pack covers it: the Hub gives nothing");
        var hubOnly=SupplyRules.allocate(new int[]{0,64},new int[]{0,1},new int[]{10},all(2,1));
        assertTrue(hubOnly.payable());assertEquals(10,hubOnly.from(1),"an empty pack: all from the Hub");
    }
    @Test void theNearestHubPaysBeforeTheNextOne(){
        // pack 2, nearest Hub 5, the next Hub 64: 20 wanted
        var a=SupplyRules.allocate(new int[]{2,5,64},new int[]{0,1,2},new int[]{20},all(3,1));
        assertTrue(a.payable());assertEquals(2,a.from(0));assertEquals(5,a.from(1),"the nearest Hub is emptied first");assertEquals(13,a.from(2));
        // listed in the other order, the same answer: the tier decides, not the order
        var b=SupplyRules.allocate(new int[]{64,5,2},new int[]{2,1,0},new int[]{20},all(3,1));
        assertEquals(2,b.from(2));assertEquals(5,b.from(1));assertEquals(13,b.from(0));
    }
    @Test void overlappingIngredientsNeverBlockARequestThatCanBePaid(){
        // cost 0 takes iron or gold, cost 1 takes gold only; the pack has 1 gold, the Hub 1 iron: gold must go to cost 1, iron to cost 0
        boolean[][] accepts={{true,true},{true,false}};   // source 0 = pack gold, source 1 = Hub iron
        var a=SupplyRules.allocate(new int[]{1,1},new int[]{0,1},new int[]{1,1},accepts);
        assertTrue(a.payable(),"a greedy pack-first pass would give the gold to cost 0 and fail");
        assertEquals(1,a.taken()[0][1],"the pack's gold pays the gold-only cost");assertEquals(1,a.taken()[1][0],"the Hub's iron pays the other");
        // the same inputs twice in one request (repeated costs) never count a slot twice
        var twice=SupplyRules.allocate(new int[]{1},new int[]{0},new int[]{1,1},new boolean[][]{{true,true}});
        assertFalse(twice.payable(),"one item cannot pay two costs");
    }
    @Test void aRequestThatCannotBePaidInFullTakesNothing(){
        var a=SupplyRules.allocate(new int[]{3,4},new int[]{0,1},new int[]{10},all(2,1));
        assertFalse(a.payable(),"7 of 10: not payable, so the caller takes nothing");
        assertTrue(a.got()[0]<10);
        var none=SupplyRules.allocate(new int[]{64},new int[]{0},new int[]{0},all(1,1));assertFalse(none.payable(),"a cost of nothing is a broken request");
        var free=SupplyRules.allocate(new int[]{64},new int[]{0},new int[]{},all(1,0));assertTrue(free.payable(),"no costs at all (creative) is paid");
        var unmatched=SupplyRules.allocate(new int[]{64},new int[]{0},new int[]{1},new boolean[][]{{false}});assertFalse(unmatched.payable(),"a source that does not match pays nothing");
    }
    @Test void overManyRandomRequestsThePackIsAlwaysUsedToTheFullAndNothingIsOverdrawn(){
        var random=new Random(40);
        for(int round=0;round<3000;round++){
            int sources=1+random.nextInt(7),costs=1+random.nextInt(4);int[] supply=new int[sources],tier=new int[sources],demand=new int[costs];var accepts=new boolean[sources][costs];
            for(int s=0;s<sources;s++){supply[s]=random.nextInt(40);tier[s]=s==0?0:random.nextInt(4);for(int c=0;c<costs;c++)accepts[s][c]=random.nextInt(3)>0;}
            for(int c=0;c<costs;c++)demand[c]=1+random.nextInt(30);
            var a=SupplyRules.allocate(supply,tier,demand,accepts);
            int got=0,want=0;for(int c=0;c<costs;c++){got+=a.got()[c];want+=demand[c];assertTrue(a.got()[c]<=demand[c],"never more than a cost asks");}
            for(int s=0;s<sources;s++){assertTrue(a.from(s)<=supply[s],"never more than a source holds");for(int c=0;c<costs;c++)if(!accepts[s][c])assertEquals(0,a.taken()[s][c],"only matching sources pay");}
            assertEquals(got==want,a.payable(),"payable exactly when every cost is paid in full");
            // the pack first: the Hubs pay only what the pack, used as fully as the costs allow, cannot
            int[] packOnly=supply.clone();for(int s=0;s<sources;s++)if(tier[s]>0)packOnly[s]=0;
            var best=SupplyRules.allocate(packOnly,tier,demand,accepts);int fromPack=0;for(int c=0;c<costs;c++)fromPack+=best.got()[c];
            assertEquals(got-fromPack,hubUnits(a,tier),"the Hubs pay no more than the pack cannot");
        }
    }

    // ---- the chat line ---------------------------------------------------------------------------------------------------------------
    @Test void pullsOfTheSameKindAddUpAndNewKindsJoinAtTheEnd(){
        var run=SupplyRules.merge(List.of(),List.of(new SupplyRules.Pulled("iron",54)));
        run=SupplyRules.merge(run,List.of(new SupplyRules.Pulled("copper",3),new SupplyRules.Pulled("iron",10)));
        assertEquals(List.of(new SupplyRules.Pulled("iron",64),new SupplyRules.Pulled("copper",3)),run,"a running total, first pulled first named");
        assertEquals(run,SupplyRules.merge(run,List.of(new SupplyRules.Pulled("gold",0))),"nothing pulled adds nothing");
    }
    @Test void aLineNamesAtMostThreeKindsAndCountsTheRest(){
        var items=List.of(new SupplyRules.Pulled("a",1),new SupplyRules.Pulled("b",2),new SupplyRules.Pulled("c",3),new SupplyRules.Pulled("d",4),new SupplyRules.Pulled("e",5));
        var line=SupplyRules.line(items);
        assertEquals(3,line.named().size());assertEquals(List.of("a","b","c"),line.named().stream().map(SupplyRules.Pulled::key).toList());assertEquals(2,line.more(),"and 2 more");
        assertEquals(0,SupplyRules.line(items.subList(0,3)).more(),"three kinds: no 'more'");
        assertEquals(1,SupplyRules.line(items.subList(0,1)).named().size());
        assertEquals(SupplyRules.NAMED,3);
    }
    @Test void theSamePlayersPullsWithinTwoSecondsAreOneLine(){
        assertEquals(40,SupplyRules.MERGE_TICKS,"2 seconds");
        assertTrue(SupplyRules.sameRun(100,140),"40 ticks later: the same running total");assertFalse(SupplyRules.sameRun(100,141),"41 ticks later: a new line");
        assertFalse(SupplyRules.due(100,140),"not sent while it may still grow");assertTrue(SupplyRules.due(100,141),"sent once 2 seconds pass with no new pull");
    }
}
