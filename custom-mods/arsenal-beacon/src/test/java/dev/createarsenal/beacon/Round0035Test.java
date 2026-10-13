package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 0.0.35 list, Part 2: the Drink Station, the Brew Pot, the Cup Dispenser, the Paper Cup, the Latte and the active drink. */
final class Round0035Test {
    static final String SRC="src/main/java/dev/createarsenal/beacon/",RES="src/main/resources/";
    static String source(String name) throws Exception{return Files.readString(Path.of(SRC+name));}
    static String lang() throws Exception{return Files.readString(Path.of(RES+"assets/arsenal_beacon/lang/en_us.json"));}

    // ---- the numbers --------------------------------------------------------------------------------------------------------------
    @Test void effectsAndPotsPerTier(){
        for(int mk=1;mk<=4;mk++){var t=DrinkRules.tier(mk);assertEquals(mk-1,t.effects(),"effects at Mk "+mk);assertEquals(mk-1,t.pots(),"Brew Pots at Mk "+mk);assertEquals(mk>=2,t.mixing());}
        assertEquals(List.of(0,1,2,2),List.of(DrinkRules.latteEffects(1),DrinkRules.latteEffects(2),DrinkRules.latteEffects(3),DrinkRules.latteEffects(4)),"a Latte carries up to 2, never more than the tier");
        assertEquals(8,DrinkRules.LINK_RANGE);
    }
    @Test void aDrinkLastsTenMinutesAndTheSweetenerAddsFullSteps(){
        assertEquals(12000,DrinkRules.duration(0),"10 minutes");assertEquals(24000,DrinkRules.duration(1));assertEquals(36000,DrinkRules.duration(2),"30 minutes at most");
        assertEquals(36000,DrinkRules.duration(5),"never more than two steps");assertEquals(12000,DrinkRules.duration(-1));
        assertEquals(0,DrinkRules.sweetenerCost(0));assertEquals(1000,DrinkRules.sweetenerCost(1));assertEquals(2000,DrinkRules.sweetenerCost(2));
        assertEquals(250,DrinkRules.sweetener(0),"Regeneration I, also the long one");assertEquals(750,DrinkRules.sweetener(1),"Regeneration II");
        assertEquals(4000,DrinkRules.TANK_MB);assertEquals(4,DrinkRules.TANK_MB/DrinkRules.STEP_MB,"a gauge mark every 1,000 mB");
        assertEquals(10,DrinkRules.minutes(DrinkRules.BASE_TICKS));
    }
    @Test void theStorageAndItsBreakGuard(){
        assertEquals(54,DrinkRules.STORAGE_SLOTS);assertEquals(64,DrinkRules.PER_SLOT);
        assertFalse(DrinkRules.guarded(256),"256 bottles: it can be broken");assertTrue(DrinkRules.guarded(257),"more than 256: it cannot");
        assertEquals(16,DrinkRules.LATTE_STACK);assertEquals(64,DrinkRules.CUP_STOCK);
    }
    @Test void whatAMixingSlotRefusesAndWhy() throws Exception{
        assertEquals("",DrinkRules.refusal(true,1,false,true,false,false),"a drinkable potion with one good lasting effect mixes");
        assertEquals("thrown",DrinkRules.refusal(false,1,false,true,false,false),"splash and lingering");
        assertEquals("plain",DrinkRules.refusal(true,0,false,false,false,false),"water, awkward");
        assertEquals("multi",DrinkRules.refusal(true,2,false,true,false,false),"Turtle Master");
        assertEquals("regeneration",DrinkRules.refusal(true,1,true,true,false,false),"Regeneration only goes in the sweetener");
        assertEquals("harmful",DrinkRules.refusal(true,1,false,false,false,false),"Poison, Weakness, Slowness");
        assertEquals("instant",DrinkRules.refusal(true,1,false,true,true,false),"Healing has no duration");
        assertEquals("duplicate",DrinkRules.refusal(true,1,false,true,false,true),"one potion per effect");
        assertTrue(lang().contains("\"gui.arsenal_beacon.drinks.notice.refuse.regeneration\": \"Regeneration only goes in the sweetener.\""));
        for(var why:List.of("thrown","plain","multi","regeneration","harmful","instant","duplicate"))assertTrue(Files.exists(Path.of(RES+"assets/arsenal_beacon/lang/en_us.json"))&&lang_has("drinks.notice.refuse."+why),why);
    }
    static boolean lang_has(String key){try{return lang().contains("\"gui.arsenal_beacon."+key+"\"");}catch(Exception e){return false;}}

    // ---- the active drink never takes what it did not give ----------------------------------------------------------------------------
    @Test void theTrackerKnowsItsOwnEffects(){
        assertTrue(PlayerDrinks.ours(1,true,-1,1,true,6000),"endless at home, its level: its own");
        assertTrue(PlayerDrinks.ours(1,false,6000,1,false,6010),"in the field, its level and time");
        assertFalse(PlayerDrinks.ours(1,false,3600,1,false,6000),"the same level but another time: a vanilla potion");
        assertFalse(PlayerDrinks.ours(2,false,6000,1,false,6000),"another level: not its own");
        assertTrue(PlayerDrinks.ours(1,false,6000,1,true,6010),"just come home: its own timed effect, about to be made endless");
        assertFalse(PlayerDrinks.ours(1,false,3600,1,true,6000),"at home a timed one of another time is vanilla's");
    }
    @Test void itGoesOnOnlyOverNothingOrSomethingWeaker(){
        assertTrue(PlayerDrinks.mayApply(false,0,0));assertTrue(PlayerDrinks.mayApply(true,0,1),"over a weaker potion (vanilla keeps it underneath)");
        assertFalse(PlayerDrinks.mayApply(true,1,1),"an equal one runs out first");assertFalse(PlayerDrinks.mayApply(true,2,1),"a stronger one wins while it lasts");
    }
    @Test void takingItsOwnOffKeepsEveryOtherLayer(){
        var ours=new PlayerDrinks.Layer(-1,1,false,true,true);var weaker=new PlayerDrinks.Layer(4000,0,false,true,true);var stronger=new PlayerDrinks.Layer(900,2,false,true,true);
        assertEquals(List.of(weaker),PlayerDrinks.kept(List.of(ours,weaker),1,true),"its own on top goes, the weaker potion underneath stays with its own time");
        assertEquals(List.of(stronger),PlayerDrinks.kept(List.of(stronger,ours),1,false),"a stronger potion on top stays; its endless copy underneath goes");
        assertEquals(List.of(stronger,weaker),PlayerDrinks.kept(List.of(stronger,weaker),1,false),"nothing of its own: nothing changes");
        var src=readQuietly("PlayerDrinks.java");
        assertTrue(src.contains("setCurativeItems(new ArrayList<>())"),"the vanilla milk bucket does not cure the drink");
        assertTrue(src.contains("MealZone.at(p).home()"),"the timer stops where the meal's does: the Home Zone or an ACTIVE FOB zone");
    }
    @Test void aCoveredPotionIsRememberedOnceWithItsLongestTime(){
        var s=new PlayerDrinks.State(new DrinkData(List.of(new DrinkData.Part(new net.minecraft.resources.ResourceLocation("speed"),1)),12000,false),12000);
        var speed=new net.minecraft.resources.ResourceLocation("speed");
        s.cover(speed,new PlayerDrinks.Layer(3000,0,false,true,true));s.cover(speed,new PlayerDrinks.Layer(2000,0,false,true,true));s.cover(speed,new PlayerDrinks.Layer(5000,0,false,true,true));
        assertEquals(1,s.covered.size(),"one entry a level");assertEquals(5000,s.covered.get(0).duration,"the longest time");
        s.cover(speed,new PlayerDrinks.Layer(0,0,false,true,true));assertEquals(1,s.covered.size(),"a finished layer is not remembered");
        var tag=new PlayerDrinks().save(new net.minecraft.nbt.CompoundTag());assertNotNull(tag);
        var src=readQuietly("PlayerDrinks.java");assertTrue(src.contains("MobEffectEvent.Added")&&src.contains("s.covered.removeIf"),"potions drunk over the drink are remembered and keep counting down");
    }
    static String readQuietly(String name){try{return source(name);}catch(Exception e){throw new RuntimeException(e);}}
    @Test void milkFromTheDispenserOrABottleEndsTheDrinkAndDeathForgetsIt() throws Exception{
        var milk=source("MilkDispenser.java");assertTrue(milk.contains("PlayerDrinks.forget(player)")&&milk.contains("PlayerDrinks.of(sp)!=null"),"the Milk Dispenser and the Milk Bottle clear the drink");
        var drinks=source("PlayerDrinks.java");assertTrue(drinks.contains("LivingDeathEvent")&&drinks.contains("forget(p)"),"death clears it");
        assertTrue(drinks.contains("PlayerLoggedOutEvent"),"logging out pauses it (the timer only runs while the player ticks)");
    }

    // ---- blocks, models, recipes, prices --------------------------------------------------------------------------------------------
    @Test void theOwnersModelsAreInstalledAndUsed() throws Exception{
        for(var m:List.of("drink_mixer_mk1","drink_mixer_mk2","drink_mixer_mk3","drink_mixer_mk4","drink_mixer","drink_pot","drink_pot_liquid","cup_dispenser"))assertTrue(Files.exists(Path.of(RES+"assets/arsenal_beacon/models/block/"+m+".json")),m);
        for(var m:List.of("drink_cup","drink_cup_empty","drink_pot","cup_dispenser","drink_mixer_mk1"))assertTrue(Files.exists(Path.of(RES+"assets/arsenal_beacon/models/item/"+m+".json")),m);
        for(var t:List.of("block/drink_mixer_mk1","block/drink_mixer_mk4","block/drink_pot","block/drink_pot_liquid","block/cup_dispenser","item/drink_cup","item/drink_cup_empty"))assertTrue(Files.exists(Path.of(RES+"assets/arsenal_beacon/textures/"+t+".png")),t);
        assertTrue(Files.readString(Path.of(RES+"assets/arsenal_beacon/models/block/drink_mixer_mk4.json")).contains("Create Arsenal - remodel"),"the owner's file, as supplied");
        assertTrue(Files.readString(Path.of(RES+"assets/arsenal_beacon/blockstates/drink_mixer_mk2.json")).contains("arsenal_beacon:block/drink_mixer_mk2"));
        var gen=Files.readString(Path.of("../../tools/kitchen/generate_drinks.py"));assertFalse(gen.contains("models/block")&&gen.contains("write(ASSETS / f'models"),"the generator never writes the owner's models");
        assertTrue(Files.readString(Path.of(RES+"assets/arsenal_beacon/models/item/drink_cup.json")).contains("tintindex"),"the Latte's band is tinted");
    }
    @Test void stationsAreHallSizedAndKeepTheirContentsWhenUpgraded() throws Exception{
        var station=source("DrinkStation.java");assertTrue(station.contains("super(true)"),"2 wide, 1 deep, 2 tall, like the Mess Hall");
        assertTrue(source("KitchenBlock.java").contains("s.getBlock() instanceof DrinkStation.StationBlock&&next.getBlock() instanceof DrinkStation.StationBlock"),"an upgrade keeps the footprint");
        assertTrue(station.contains("BrewingRecipeRegistry.canBrew")&&station.contains("BrewingRecipeRegistry.brewPotions"),"Forge's brewing registry, so other mods' recipes brew too");
        assertTrue(station.contains("BlockEvent.BreakEvent")&&station.contains("DrinkRules.guarded(st.total())"),"the break guard");
    }
    @Test void pricesAndTheWorkshop() throws Exception{
        assertArrayEquals(new int[]{8,16,32},Economy.DRINKS_PLATING,"pack: Reinforced Beacon Plating");assertArrayEquals(new int[]{4,8,14},Economy.DRINKS_ARDENT,"standalone: Ardent Energy");
        assertTrue(WorkshopFabrication.ITEMS.containsAll(List.of("drink_mixer_mk1","drink_pot","cup_dispenser")));
        int kitchen=WorkshopFabrication.ITEMS.indexOf("mess_hall_mk1"),support=WorkshopFabrication.ITEMS.indexOf("support_platform");
        for(var id:List.of("drink_mixer_mk1","drink_pot","cup_dispenser")){int at=WorkshopFabrication.ITEMS.indexOf(id);assertTrue(at>kitchen&&at<support,id+" sits in the Kitchen group");}
        assertTrue(source("BeaconClient.java").contains("FAB_GROUP_START={0,4,"+support+"}"),"the Kitchen group grew by three (and by the MRE Station in 0.0.38)");
        var recipe=Files.readString(Path.of(RES+"data/arsenal_beacon/recipes/drink_mixer_mk1.json"));assertTrue(recipe.contains("minecraft:brewing_stand"),"built around a brewing stand");
        var cup=Files.readString(Path.of(RES+"data/arsenal_beacon/recipes/drink_cup_empty.json"));assertTrue(cup.contains("minecraft:paper")&&cup.contains("\"count\": 4"),"paper makes four cups");
        for(var r:List.of("drink_mixer_mk1","drink_pot","drink_cup_empty"))assertTrue(Files.exists(Path.of("src/standalone/resources/data/arsenal_beacon/recipes/"+r+".json")),"standalone "+r);
    }
    @Test void theMixingSlotsDoNotShadowSlotIndex() throws Exception{
        // found on the real client: a captured "index" inside an anonymous Slot reads Slot's own field (5 to 7), so every mixing slot was inactive
        var menu=source("DrinkStationMenu.java");assertFalse(menu.contains("final int index=i;addSlot"),"the mixing slot's number has its own name");
        assertTrue(menu.contains("mixing<limit()"));
    }
    @Test void protocolFortyCarriesTheStationView() throws Exception{
        var net=source("BeaconNetwork.java");assertTrue(net.contains("\"40-standalone\":\"40-pack\""));assertTrue(net.contains("registerMessage(24,DrinkStationMenu.View.class"));
    }
    @Test void theFieldGuideQuotesTheRealNumbers() throws Exception{
        var all=lang();
        for(var s:List.of("54 storage slots that each hold 64 bottles","more than 256 bottles","+10 minutes for 1,000 mB, +20 for 2,000 mB","I adds 250 mB, II adds 750 mB","The tank holds 4,000 mB",
                "Up to 2 effects for 10 minutes","They stack to 16","Put up to 64 cups inside","Craft 4 from 3 paper","Pay 8, 16 and 32 Reinforced Beacon Plating.","Pay 4, 8 and 14 Ardent Energy.","1, 2 or 3 pots at Mk II, III and IV"))
            assertTrue(all.contains(s),"guide does not say '"+s+"'");
        assertTrue(all.contains("\"gui.arsenal_beacon.guide.drinks.title\""));
    }
}
