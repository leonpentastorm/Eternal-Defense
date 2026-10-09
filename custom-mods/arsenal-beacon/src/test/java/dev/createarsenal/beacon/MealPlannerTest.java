package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.createarsenal.beacon.MealRules.Effect.*;

/** The recipe planner against a pantry shaped like the bundled vanilla traits (no Minecraft types needed). */
final class MealPlannerTest {
    private static MealPlanner.Food food(String key,String group,int limit,Object... effects){
        var weights=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);for(int i=0;i<effects.length;i++)weights.put((MealRules.Effect)effects[i],1);
        return new MealPlanner.Food(key,group,weights,false,limit);
    }
    /** Enough of each group to make every ordinary effect, with the same-group pairs the doc lists for doubling. */
    static Map<String,MealPlanner.Food> pantry(){
        var list=List.of(
            food("cooked_chicken","protein",64,VITALITY),food("cooked_mutton","protein",64,VITALITY),food("beef","protein",64,MIGHT),food("cooked_beef","protein",64,MIGHT),food("milk_bucket","protein",1,FORTITUDE),
            food("apple","fruit",64,MOBILITY,FORTUNE),food("glow_berries","fruit",64,FORTUNE),food("honey_bottle","fruit",16,RECOVERY),food("golden_apple","fruit",64,RECOVERY),food("chorus_fruit","fruit",64,SPRINGY),food("melon_slice","fruit",64,MOBILITY,SPRINGY),
            food("cooked_cod","fish",64,MOBILITY),food("cooked_salmon","fish",64,MOBILITY),food("cod","fish",64,SWIM),food("salmon","fish",64,SWIM),
            food("brown_mushroom","fungi",64,STEADINESS),food("red_mushroom","fungi",64,STEADINESS),food("rotten_flesh","fungi",64,HEARTH),food("spider_eye","fungi",64,HEARTH),
            food("carrot","vegetables",64,FORTITUDE),food("potato","vegetables",64,FORTITUDE),food("beetroot_soup","vegetables",1,FORTITUDE),
            food("bread","grain",64,STEADINESS),food("wheat","grain",64,STEADINESS),food("cookie","grain",64,AGILITY),food("pumpkin_pie","grain",64,AGILITY));
        var map=new LinkedHashMap<String,MealPlanner.Food>();for(var f:list)map.put(f.key(),f);return map;
    }
    private static Map<String,Integer> stock(Map<String,MealPlanner.Food> foods,int each){var map=new LinkedHashMap<String,Integer>();foods.keySet().forEach(k->map.put(k,each));return map;}
    private static List<MealPlanner.Food> table(Map<String,MealPlanner.Food> catalogue,MealPlanner.Plan plan){return plan.keys().stream().map(catalogue::get).toList();}

    @Test void unmetEffectsAreReportedInsteadOfRejectingTheWholeOrder(){
        var c=pantry();var onlyChicken=List.of(c.get("cooked_chicken"),c.get("cooked_mutton"));
        var eval=MealPlanner.evaluate(onlyChicken,false,1,EnumSet.of(FIREPOWER));
        assertNull(eval.meal());assertEquals("missing",eval.problem());assertEquals(EnumSet.of(FIREPOWER),eval.unmet());
        var mixed=MealPlanner.evaluate(List.of(c.get("cooked_chicken"),c.get("bread")),false,1,EnumSet.of(FIREPOWER,VITALITY));
        assertEquals(EnumSet.of(VITALITY),mixed.unmet(),"chicken is reserved by the Protein + Grain recipe, so Vitality needs a different food");
        var fine=MealPlanner.evaluate(List.of(c.get("cooked_chicken"),c.get("bread"),c.get("carrot")),true,1,EnumSet.of(FIREPOWER,FORTITUDE));
        assertEquals("",fine.problem());assertEquals(2,fine.meal().bonuses().size());
    }
    @Test void recipesThatShareAFoodGroupAreAnInvalidSelection(){
        var c=pantry();var eval=MealPlanner.evaluate(List.of(c.get("cooked_chicken"),c.get("bread")),true,4,EnumSet.of(FIREPOWER,BRAWLER));
        assertEquals("selection",eval.problem());
        assertEquals("selection",MealPlanner.evaluate(List.of(),false,1,EnumSet.of(VITALITY,FORTITUDE,MIGHT)).problem(),"a sandwich holds two");
        assertNull(MealPlanner.plan(EnumSet.of(FIREPOWER,BRAWLER),true,4,c,stock(c,64),Set.of(),false));
    }
    @Test void autoModeStillRanksOrdinaryEffectsAndDoublesFromMkThree(){
        var c=pantry();var pair=List.of(c.get("cooked_chicken"),c.get("cooked_mutton"));
        for(int mk=1;mk<=4;mk++)for(boolean stew:new boolean[]{false,true}){var meal=MealPlanner.evaluate(pair,stew,mk,null).meal();assertEquals(mk>=3,meal.bonuses().get(0).pair(),"mk "+mk+" stew="+stew);assertEquals(MealRules.tier(mk).ticks(),meal.duration(),"the meal lasts as long as its hall allows");}
        assertEquals("slots",MealPlanner.evaluate(List.of(c.get("cooked_chicken"),c.get("cooked_mutton"),c.get("carrot"),c.get("bread")),true,3,null).problem(),"three food slots below Mk IV");
        assertEquals("ingredients",MealPlanner.evaluate(List.of(c.get("cooked_chicken"),c.get("cooked_chicken")),true,4,null).problem(),"two stacks of one food are still one kind");
    }
    @Test void everySingleEffectCanBePlannedForEveryTierAndMode(){
        var c=pantry();var stock=stock(c,64);
        for(var effect:MealRules.Effect.values())for(boolean stew:new boolean[]{false,true})for(int mk=1;mk<=4;mk++){
            var plan=MealPlanner.plan(EnumSet.of(effect),stew,mk,c,stock,Set.of(),true);
            assertNotNull(plan,effect+" stew="+stew+" mk="+mk);
            var eval=MealPlanner.evaluate(table(c,plan),stew,mk,EnumSet.of(effect));
            assertEquals("",eval.problem(),effect+" "+eval);assertTrue(plan.covered());
            assertTrue(plan.keys().size()>=2&&plan.keys().size()<=(stew?Math.min(MealRules.tier(mk).slots(),MealRules.tier(mk).ingredients()):3));
            assertEquals(stew?MealRules.tier(mk).ingredients():plan.keys().size(),Arrays.stream(plan.units()).sum(),"units pay exactly one batch");
        }
    }
    @Test void everyPairOfEffectsIsEitherPlannedSoundlyOrImpossible(){
        var c=pantry();var stock=stock(c,64);var values=MealRules.Effect.values();int planned=0;
        for(int a=0;a<values.length;a++)for(int b=a+1;b<values.length;b++)for(boolean stew:new boolean[]{false,true}){
            var order=EnumSet.of(values[a],values[b]);var plan=MealPlanner.plan(order,stew,4,c,stock,Set.of(),false);
            if(plan==null)continue;planned++;
            var eval=MealPlanner.evaluate(table(c,plan),stew,4,order);assertEquals("",eval.problem(),order+" stew="+stew);
            assertEquals(order,new HashSet<>(eval.meal().bonuses().stream().map(MealData.Bonus::effect).toList()));
        }
        assertTrue(planned>350,"most pairs work: "+planned);
    }
    @Test void plannerFindsATableWheneverOneExistsOnSmallPantries(){
        var all=new ArrayList<>(pantry().values());var random=new Random(7);var values=MealRules.Effect.values();int agreed=0;
        for(int round=0;round<150;round++){
            // at most three foods per group, so no candidate list is truncated
            Collections.shuffle(all,random);var chosen=new ArrayList<MealPlanner.Food>();var perGroup=new HashMap<String,Integer>();
            for(var f:all){if(chosen.size()>=8)break;if(perGroup.merge(f.group(),1,Integer::sum)<=3)chosen.add(f);}
            var catalogue=new LinkedHashMap<String,MealPlanner.Food>();chosen.forEach(f->catalogue.put(f.key(),f));
            boolean stew=random.nextBoolean();int mk=1+random.nextInt(4);int size=1+random.nextInt(stew?3:2);var order=EnumSet.noneOf(MealRules.Effect.class);
            while(order.size()<size)order.add(values[random.nextInt(values.length)]);
            int cap=stew?Math.min(MealRules.tier(mk).slots(),MealRules.tier(mk).ingredients()):3;boolean exists=false;var keys=new ArrayList<>(catalogue.keySet());
            for(int mask=1;mask<(1<<keys.size())&&!exists;mask++){
                if(Integer.bitCount(mask)>cap)continue;var foods=new ArrayList<MealPlanner.Food>();for(int i=0;i<keys.size();i++)if((mask>>i&1)!=0)foods.add(catalogue.get(keys.get(i)));
                exists=MealPlanner.evaluate(foods,stew,mk,order).problem().isEmpty();
            }
            var plan=MealPlanner.plan(order,stew,mk,catalogue,stock(catalogue,64),Set.of(),false);
            assertEquals(exists,plan!=null,"order "+order+" stew="+stew+" mk="+mk+" pantry "+keys);agreed++;
        }
        assertEquals(150,agreed);
    }
    @Test void mkFourStewPlansDoubleEveryEffectWhenThePackAllowsIt(){
        var c=pantry();var stock=stock(c,64);
        var plan=MealPlanner.plan(EnumSet.of(VITALITY,FORTITUDE,MOBILITY),true,4,c,stock,Set.of(),true);
        assertNotNull(plan);assertEquals(3,plan.doubled());assertEquals(6,plan.keys().size());
        var legendary=MealPlanner.plan(EnumSet.of(FIREPOWER),true,4,c,stock,Set.of(),true);
        assertEquals(1,legendary.doubled());assertEquals(4,legendary.keys().size(),"two foods from EACH group");
        assertEquals(0,MealPlanner.plan(EnumSet.of(FIREPOWER),true,3,c,stock,Set.of(),true).doubled());
        assertEquals(1,MealPlanner.plan(EnumSet.of(VITALITY,FORTITUDE),false,4,c,stock,Set.of(),true).doubled(),"a sandwich holds three kinds: one doubled effect and one single");
        assertEquals(0,MealPlanner.plan(EnumSet.of(VITALITY,FORTITUDE),false,2,c,stock,Set.of(),true).doubled(),"Mk II cannot double");
        var mkThree=MealPlanner.plan(EnumSet.of(VITALITY,FORTITUDE),true,3,c,stock,Set.of(),true);assertEquals(1,mkThree.doubled(),"Mk III has three slots: one doubled effect and one single");assertEquals(3,mkThree.keys().size());
        // without a second food of a group the plan still works, single strength
        var thin=new HashMap<>(stock);thin.keySet().removeIf(k->k.equals("cooked_mutton"));
        var single=MealPlanner.plan(EnumSet.of(VITALITY),true,4,c,thin,Set.of(),true);assertNotNull(single);assertEquals(0,single.doubled());
    }
    @Test void smallTiersRefuseOrdersThatNeedMoreFoodTypesThanTheyHold(){
        var c=pantry();var stock=stock(c,64);
        assertNull(MealPlanner.plan(EnumSet.of(FIREPOWER,FOCUS,FAST_DRAW),true,1,c,stock,Set.of(),false),"three legendary recipes need six distinct kinds");
        assertNull(MealPlanner.plan(EnumSet.of(FIREPOWER,FOCUS,FAST_DRAW),true,3,c,stock,Set.of(),false),"three food slots hold three kinds");
        assertNotNull(MealPlanner.plan(EnumSet.of(FIREPOWER,FOCUS,FAST_DRAW),true,4,c,stock,Set.of(),false),"Mk IV has six");
        assertNotNull(MealPlanner.plan(EnumSet.of(VITALITY,FORTITUDE,MOBILITY),true,2,c,stock,Set.of(),false),"three everyday effects fit three slots");
        assertNull(MealPlanner.plan(EnumSet.of(FIREPOWER,FOCUS),false,4,c,stock,Set.of(),false),"a sandwich holds at most three kinds");
    }
    @Test void scarcePacksGetTheBestPlanTheyCanAndAreMarkedUncovered(){
        var c=pantry();var stock=new HashMap<String,Integer>();stock.put("cooked_chicken",3);stock.put("cooked_mutton",3);stock.put("carrot",1);
        var plan=MealPlanner.plan(EnumSet.of(VITALITY),true,4,c,stock,Set.of(),true);
        assertNotNull(plan);assertFalse(plan.covered(),"7 units against a 12 unit batch");assertEquals(7,Arrays.stream(plan.units()).sum());
        assertNull(MealPlanner.plan(EnumSet.of(MIGHT),true,4,c,stock,Set.of(),true),"no food in the pack feeds Might");
        stock.put("carrot",9);var better=MealPlanner.plan(EnumSet.of(VITALITY),true,4,c,stock,Set.of(),true);
        assertTrue(better.covered());assertEquals(12,Arrays.stream(better.units()).sum());
        assertTrue(Arrays.stream(better.units()).allMatch(n->n>=1));
    }
    @Test void foodsThatStackToOneCountAsOneUnit(){
        var c=pantry();var stock=new HashMap<String,Integer>();stock.put("milk_bucket",5);stock.put("beetroot_soup",5);
        var plan=MealPlanner.plan(EnumSet.of(FORTITUDE),true,4,c,stock,Set.of(),true);
        assertNotNull(plan);assertFalse(plan.covered());assertEquals(2,Arrays.stream(plan.units()).sum());
        assertEquals(1,MealPlanner.usable(c,stock,"milk_bucket"));
    }
    @Test void foodsAlreadyOnTheTableAreReusedBeforeFreshOnes(){
        var c=pantry();var stock=stock(c,10);
        var plan=MealPlanner.plan(EnumSet.of(VITALITY),false,1,c,stock,Set.of("cooked_mutton","bread"),true);
        assertEquals(Set.of("cooked_mutton","bread"),new HashSet<>(plan.keys()),"nothing needs to move that is already in place");
    }
    @Test void planningIsDeterministicAndFast(){
        var c=pantry();var stock=stock(c,64);var order=EnumSet.of(FORTITUDE,MOBILITY,FIREPOWER);
        var first=MealPlanner.plan(order,true,4,c,stock,Set.of(),true);var second=MealPlanner.plan(order,true,4,c,stock,Set.of(),true);
        assertEquals(first.keys(),second.keys());assertArrayEquals(first.units(),second.units());
        long start=System.nanoTime();for(int i=0;i<200;i++)MealPlanner.plan(order,true,4,c,stock,Set.of(),false);
        assertTrue((System.nanoTime()-start)/200/1e6<5,"a feasibility check stays well under a tick");
    }
}
