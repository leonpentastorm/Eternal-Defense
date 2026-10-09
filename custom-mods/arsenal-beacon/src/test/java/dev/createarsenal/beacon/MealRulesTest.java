package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class MealRulesTest {
    @Test void repeatedBoundaryCrossingsNeverRefreshTheFieldTimer(){
        int ticks=MealRules.FIELD_TICKS;for(int i=0;i<2000;i++)ticks=MealRules.remaining(ticks,true);assertEquals(MealRules.FIELD_TICKS,ticks);
        for(int i=0;i<12*60*20;i++)ticks=MealRules.remaining(ticks,false);assertEquals(18*60*20,ticks);
        for(int i=0;i<10;i++){for(int t=0;t<100;t++)ticks=MealRules.remaining(ticks,true);ticks=MealRules.remaining(ticks,false);}
        assertEquals(18*60*20-10,ticks);assertEquals(0,MealRules.remaining(0,false));
    }
    @Test void ingredientScoresChooseTwoOrThreeEffectsWithoutUnboundedStacking(){
        var scores=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);for(var e:MealRules.Effect.values())scores.put(e,1);scores.put(MealRules.Effect.MOBILITY,3);
        var sandwich=MealRules.select(scores,false);var stew=MealRules.select(scores,true);assertEquals(2,sandwich.size());assertEquals(3,stew.size());assertEquals(MealRules.Effect.MOBILITY,sandwich.get(0));assertEquals(MealRules.Effect.VITALITY,sandwich.get(1));assertEquals(sandwich,MealRules.select(new HashMap<>(scores),false));
        assertTrue(MealRules.select(Map.of(MealRules.Effect.FORTITUDE,0),true).isEmpty());assertEquals(1,MealRules.select(Map.of(MealRules.Effect.VITALITY,2),false).size());
    }
    @Test void eachTierBuildsOnTheLastOne(){
        int[] minutes={15,20,25,30},slots={3,3,3,6};
        for(int mk=1;mk<=4;mk++){
            var tier=MealRules.tier(mk);assertEquals(mk,tier.pots());assertEquals(mk*2+1,tier.servings(),"servings 3/5/7/9: a Mk IV pot holds nine");
            assertEquals(minutes[mk-1],tier.minutes(),"Mk "+mk+" meals last");assertEquals(minutes[mk-1]*1200,tier.ticks());assertEquals(slots[mk-1],tier.slots(),"Mk "+mk+" food slots");
            assertEquals(mk>=2,tier.stew(),"stew is unlocked by Mk II");assertEquals(mk>=3,tier.doubling(),"doubling is unlocked by Mk III");
        }
        assertEquals(2,MealRules.HOME_MULTIPLIER);assertEquals(36000,MealRules.FIELD_TICKS);assertEquals(MealRules.FIELD_TICKS,MealRules.tier(4).ticks(),"the longest meal is the old fixed half hour");
    }
    @Test void stewCostsGrowWithTheLevelAndConsumeRealFood(){
        int[] costs={4,7,10,12};for(int mk=1;mk<=4;mk++){
            assertEquals(costs[mk-1],MealRules.tier(mk).ingredients());var plan=MealRules.plan(new int[]{16,16,16},new int[]{0,1,2},costs[mk-1]);
            assertEquals(costs[mk-1],Arrays.stream(plan).sum());assertTrue(Arrays.stream(plan).allMatch(n->n>0));
        }
    }
    @Test void planReservesEachDistinctTypeAndFailsWithoutPartialConsumption(){
        int[] counts={1,3,2};assertArrayEquals(new int[]{1,1,2},MealRules.plan(counts,new int[]{0,0,1},4));
        assertArrayEquals(new int[]{1,3,2},counts);assertNull(MealRules.plan(counts,new int[]{0,0,1},7));
        assertNull(MealRules.plan(new int[]{1,1,1,1,1},new int[]{0,1,2,3,4},4));
        assertArrayEquals(new int[]{1,0,1},MealRules.plan(counts,new int[]{0,0,1},2));
    }
    @Test void firearmTraitsHaveStableIdsAndConservativeDistinctAmounts(){
        assertEquals(.08,MealRules.Effect.of("firepower").amount);assertEquals(.10,MealRules.Effect.of("quick_hands").amount);
        assertEquals(.20,MealRules.Effect.of("brawler").amount);assertEquals(.15,MealRules.Effect.of("heavy_hand").amount);assertEquals(.10,MealRules.Effect.of("demolition").amount);
        assertNull(MealRules.Effect.of("attack_damage"));
    }
    @Test void everyEffectHasAStableIdAndNewEffectsAreAppended(){
        var ids=new HashSet<String>();for(var e:MealRules.Effect.values()){assertTrue(ids.add(e.id));assertSame(e,MealRules.Effect.of(e.id));assertTrue(e.amount>0);}
        assertEquals(26,MealRules.Effect.values().length);
        for(int i=0;i<9;i++)assertEquals(new String[]{"vitality","fortitude","steadiness","mobility","firepower","quick_hands","brawler","heavy_hand","demolition"}[i],MealRules.Effect.values()[i].id,"saved meals keep their effect ids and order");
    }
    @Test void pairedFoodsDoubleTheBonusAndSurviveTheVanillaEffectCodec(){
        var single=new MealData.Bonus(MealRules.Effect.VITALITY,2);var pair=new MealData.Bonus(MealRules.Effect.VITALITY,2,true);
        assertEquals(single.field()*2,pair.field(),1e-9);assertEquals(single.home()*2,pair.home(),1e-9);assertEquals(single.field()*2,single.home(),1e-9);
        for(var e:MealRules.Effect.values())for(int strength=1;strength<=3;strength++)for(boolean p:new boolean[]{false,true}){
            var bonus=new MealData.Bonus(e,strength,p);var back=MealData.Bonus.ofAmplifier(e,bonus.amplifier());assertEquals(bonus,back,"amplifier "+bonus.amplifier()+" of "+e);
        }
    }
    @Test void everyPairOfSixGroupsHasExactlyOneLegendaryRecipe(){
        var groups=List.of("protein","fish","vegetables","fruit","grain","fungi");var effects=new HashSet<MealRules.Effect>();
        for(int i=0;i<6;i++)for(int j=i+1;j<6;j++){
            var mixes=MealRules.mixes(Set.of(groups.get(i),groups.get(j)));assertEquals(1,mixes.size());assertTrue(effects.add(mixes.get(0).effect()));assertTrue(mixes.get(0).effect().gun());
        }
        assertEquals(15,effects.size());assertEquals(3,MealRules.mixes(new HashSet<>(groups)).size());
        var used=new HashSet<String>();for(var mix:MealRules.mixes(new HashSet<>(groups))){assertTrue(used.add(mix.first()));assertTrue(used.add(mix.second()));}
    }
    @Test void doublesUnlockAtMkThreeAndLegendaryNeedsTwoFoodsFromEachGroup(){
        for(int mk=1;mk<3;mk++){assertFalse(MealRules.doubles(mk,2,2,true));assertFalse(MealRules.doubles(mk,2,0,false));}
        for(int mk=3;mk<=4;mk++){assertTrue(MealRules.doubles(mk,2,0,false));assertTrue(MealRules.doubles(mk,2,2,true));assertFalse(MealRules.doubles(mk,1,0,false));}
        assertFalse(MealRules.doubles(4,2,1,true));assertFalse(MealRules.doubles(4,1,2,true));
        assertEquals("−10%",new MealData.Bonus(MealRules.Effect.RECOIL_CONTROL,1).amountText(false));
    }
    @Test void damageProtectionsNeverReachFullImmunity(){
        var hearth=new MealData.Bonus(MealRules.Effect.HEARTH,3,true);var springy=new MealData.Bonus(MealRules.Effect.SPRINGY,3,true);
        assertTrue(hearth.home()<=.8&&springy.home()<=.9);assertTrue(hearth.field()>0&&springy.field()>0);
    }
}
