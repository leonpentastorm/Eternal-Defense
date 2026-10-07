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
    @Test void eachTierKeepsBothMealModesAndImprovesCommunalCapacity(){
        for(int mk=1;mk<=4;mk++){assertEquals(mk,MealRules.tier(mk).pots());assertEquals(mk*4,MealRules.tier(mk).servings());}
        assertEquals(2,MealRules.HOME_MULTIPLIER);assertEquals(36000,MealRules.FIELD_TICKS);
    }
}
