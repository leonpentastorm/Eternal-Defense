package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 0.0.37 notes: bowls and Paper Cups are used up at the pots; an endless home effect that another mod keeps taking away stops blinking. */
final class Round0037Test {
    static String source(String name) throws Exception{return Round0035Test.source(name);}
    static String body(String src,String from,String to){return Round0036Test.body(src,from,to);}

    // ---- bowls and cups are a sink ---------------------------------------------------------------------------------------------------
    @Test void eatingAndDrinkingAtThePotsUseUpTheBowlAndTheCup() throws Exception{
        var cook=body(source("CookPot.java"),"void use(ServerPlayer p,InteractionHand hand){","@Override protected void saveAdditional");
        assertTrue(cook.contains("PlayerMeals.eat(p,stew);")&&cook.indexOf("PlayerMeals.eat(p,stew);")<cook.indexOf("if(!p.getAbilities().instabuild)p.getItemInHand(hand).shrink(1);"),"the bowl is used up after the serving is eaten");
        var brew=body(source("BrewPot.java"),"void use(ServerPlayer p,InteractionHand hand){","@Override protected void saveAdditional");
        assertTrue(brew.contains("PlayerDrinks.drink(p,served);")&&brew.indexOf("PlayerDrinks.drink(p,served);")<brew.indexOf("if(!p.getAbilities().instabuild)p.getItemInHand(hand).shrink(1);"),"the cup is used up after the drink");
        assertEquals(2,(cook+brew).split("shrink\\(1\\)",-1).length-1,"one bowl, one cup, each only once and only on a serving");
    }
    @Test void theTextsSayTheBowlAndTheCupAreUsedUp() throws Exception{
        var json=com.google.gson.JsonParser.parseString(Round0035Test.lang()).getAsJsonObject();
        java.util.function.Function<String,String> t=k->json.get("gui.eternal_defense."+k).getAsString();
        for(String k:new String[]{"kitchen.served","bowls.tip","dispenser.tip.use_bowl","drinks.cup_tip","cups.tip","dispenser.tip.use_cup","guide.kitchen.body","guide.drinks.body"}){
            var v=t.apply(k);
            assertFalse(v.contains("reusable")||v.contains("cup is kept")||v.contains("you keep the cup")||v.contains("come back when you eat")||v.contains("are returned when you eat")||v.contains("keeps the cup"),k+": "+v);
        }
        assertTrue(t.apply("kitchen.served").contains("used up")&&t.apply("drinks.cup_tip").contains("used up"));
        assertTrue(t.apply("guide.kitchen.body").contains("The bowl is used up.")&&t.apply("guide.drinks.body").contains("The cup is used up"));
    }

    // ---- an endless home effect that something keeps taking away ----------------------------------------------------------------------
    @Test void twoLossesWithinHalfAMinuteSwitchToTheLongTimer(){
        var times=new ArrayDeque<Long>();
        assertFalse(EndlessEffects.trips(times,100),"one loss is not enough (a totem, a one-off)");
        assertTrue(EndlessEffects.trips(times,100+EndlessEffects.WINDOW),"two within 30 s");
        var spread=new ArrayDeque<Long>();EndlessEffects.trips(spread,0);
        assertFalse(EndlessEffects.trips(spread,EndlessEffects.WINDOW+1),"two far apart are not");
        assertEquals(24000,EndlessEffects.STEADY,"20 minutes");assertEquals(1200,EndlessEffects.REFRESH,"topped up every minute");
        assertTrue(EndlessEffects.steadyTime(EndlessEffects.STEADY));assertTrue(EndlessEffects.steadyTime(EndlessEffects.STEADY-EndlessEffects.REFRESH));
        assertFalse(EndlessEffects.steadyTime(EndlessEffects.STEADY+1));assertFalse(EndlessEffects.steadyTime(12000),"a 10-minute potion is not the stand-in");
        assertTrue(EndlessEffects.due(EndlessEffects.STEADY-EndlessEffects.REFRESH));assertFalse(EndlessEffects.due(EndlessEffects.STEADY-1));
    }
    @Test void theDrinkKnowsItsLongTimerAsItsOwn(){
        assertTrue(PlayerDrinks.ours(0,false,EndlessEffects.STEADY-300,0,true,9000),"the stand-in at home is the drink's");
        assertTrue(PlayerDrinks.ours(0,false,EndlessEffects.STEADY-300,0,false,9000),"and just come out of home, so it is turned into the field time");
        assertFalse(PlayerDrinks.ours(1,false,EndlessEffects.STEADY-300,0,true,9000),"never at another level");
        assertFalse(PlayerDrinks.ours(0,false,4800,0,true,9000),"a 4-minute potion is not");
    }
    @Test void onlyLossesFromOutsideCountAndTheTrackersExtendInPlace() throws Exception{
        var drinks=source("PlayerDrinks.java");var meals=source("PlayerMeals.java");var endless=source("EndlessEffects.java");
        assertTrue(body(drinks,"static void strip(","\n    }").contains("applying=true;EndlessEffects.own++;"),"the drink's own removals are not losses");
        assertTrue(source("MilkDispenser.java").contains("EndlessEffects.run(()->{PlayerMeals.clear(player);player.removeAllEffects();});"),"nor the Milk Dispenser's cleanse");
        assertTrue(meals.contains("if(!extend)EndlessEffects.run(()->p.removeEffect(effect));"),"the meal's long timer is topped up in place, not taken off");
        assertTrue(drinks.contains("apply(p,e,part.amplifier(),EndlessEffects.home(p,e));"),"the drink at home: endless, or the long timer");
        assertTrue(endless.contains("if(own==0&&e.getEntity() instanceof ServerPlayer p&&!e.isCanceled()&&MealZone.at(p).home()&&tracked(p,e.getEffectInstance()))lost("),"a loss: at home, not the mod's own change");
        assertTrue(source("EternalDefense.java").contains("MinecraftForge.EVENT_BUS.register(new EndlessEffects.EndlessEvents());"));
    }
}
