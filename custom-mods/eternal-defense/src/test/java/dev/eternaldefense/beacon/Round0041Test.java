package dev.eternaldefense.beacon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The list (besides the end of the standalone edition) */
final class Round0041Test {
    static String source(String name) throws Exception{return Round0035Test.source(name);}
    static String lang() throws Exception{return Files.readString(Path.of(EnchantTest.ASSETS+"lang/en_us.json"));}
    static JsonObject json(String path) throws Exception{return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();}
    static JsonObject element(JsonObject model,String name){
        for(var e:model.getAsJsonArray("elements"))if(name.equals(e.getAsJsonObject().get("name").getAsString()))return e.getAsJsonObject();
        return null;
    }

    // The MRE Station's press rod
    @Test void thePressRodRunsUpIntoTheHopperSoThePressNeverFloats() throws Exception{
        var owner=json(EnchantTest.ASSETS+"models/block/mre_maker.json");
        var rod=element(owner,"Press_rod");assertNotNull(rod);
        assertEquals(26,rod.getAsJsonArray("to").get(1).getAsDouble(),"the owner's new rod reaches y 26, inside the hopper");
        assertNotNull(element(owner,"Rod_collar"),"the guide collar at the bay ceiling");
        var press=json(EnchantTest.ASSETS+"models/block/anim/mre_maker_press.json");
        assertNotNull(element(press,"Press_rod"),"the rod moves with the press");
        assertNull(element(press,"Rod_collar"));
        var still=json(EnchantTest.ASSETS+"models/block/mre_maker_static.json");
        assertNotNull(element(still,"Rod_collar"),"the collar stays put");assertNull(element(still,"Press_rod"));
        // The press travels 2.6 px: the rod's top stays above the collar (19.5..20), so it never leaves the ceiling
        assertTrue(26-AnimationRules.PRESS_TRAVEL>20);
    }

    // The ammo and attachment tables
    @Test void anEmptyComponentTableSaysHowManyPartsWaitInLaterAges() throws Exception{
        String screen=source("PlatformScreen.java"),lang=lang();
        assertTrue(screen.contains("private Component emptyHint()"),"the hint is back");
        assertTrue(screen.contains("Ui.t(\"platform.later_gun.\"+kind+(locked==1?\".one\":\"\"),locked)"));
        assertTrue(screen.contains("if(data.getString(\"notice\").equals(\"Equip a gun\"))return Ui.t(\"platform.hold_gun\""),"no gun in hand: the old line");
        assertFalse(screen.contains("NoticeScreen"),"the unused pop-up is gone");
        assertTrue(lang.contains("\"gui.eternal_defense.platform.later_gun.attachment\": \"There are %s more attachments for this weapon in later Ages. Upgrade your table"));
        assertFalse(lang.contains("Upgrade it at the weapon table"),"every table upgrades on its own");
        assertTrue(source("WeaponPlatform.java").contains("n.putInt(\"locked\","),"the server still counts them");
    }

    // The radio
    @Test void spammingFlaresOrOrdersPlaysOneLineAtATime(){
        assertTrue(SupportChatter.free(0,0));
        assertFalse(SupportChatter.free(10,80),"a line is playing");
        assertTrue(SupportChatter.free(80,80),"it ended");
    }

    // Recipes and prices
    @Test void platingIsThreeKindsOfSheetCompactedWithoutHeat() throws Exception{
        String raw=Files.readString(Path.of("src/main/resources/data/eternal_defense/recipes/beacon/reinforced_plating.json"));
        var recipe=com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
        assertEquals("create:compacting",recipe.get("type").getAsString());
        var inputs=new java.util.ArrayList<String>();recipe.getAsJsonArray("ingredients").forEach(i->inputs.add(i.getAsJsonObject().get("item").getAsString()));
        assertEquals(List.of("create:iron_sheet","create:iron_sheet","create:golden_sheet","create:golden_sheet","create:copper_sheet","create:copper_sheet"),inputs);
        assertFalse(recipe.has("heatRequirement"),"no heat");
        assertFalse(raw.contains("hardened_alloy"));
    }
    @Test void aBlazeRodCostsSix(){
        var rod=RaidRoundTest.buys(ExchangeShop.PACK).stream().filter(r->r.id().equals("minecraft:blaze_rod")).findFirst().orElseThrow();
        assertEquals(1,rod.count());assertEquals(6,rod.value());
    }

    // The Logistics Hub zone
    @Test void aBaseWithAWorkingHubShowsTheHubZoneAndTheBeaconCountsHubs() throws Exception{
        String effects=source("MealEffects.java"),meals=source("PlayerMeals.java"),zone=source("HubZone.java"),lang=lang();
        assertTrue(effects.contains("HUB=EFFECTS.register(\"logistics_hub_zone\",()->new HubZoneEffect());"));
        assertTrue(Files.exists(Path.of(EnchantTest.ASSETS+"textures/mob_effect/logistics_hub_zone.png")),"its icon");
        assertTrue(meals.contains("{PlayerMeals.tick(p);HubZone.tick(p);}"),"checked on every player's tick (once a second inside)");
        assertTrue(zone.contains("BaseZone.problem(p.level(),p.blockPosition())==null&&active(p.serverLevel())>0"),"in the Home Zone, with a working Hub");
        assertTrue(lang.contains("\"effect.eternal_defense.logistics_hub_zone.description\": \"You are in the Logistics Hub zone"));
        assertTrue(effects.contains("static boolean zone(MobEffect effect){return effect==HOME.get()||effect==FOB.get()||effect==HUB.get();}"));
        assertTrue(source("MilkDispenser.java").contains("!MealEffects.zone(effect.getEffect())"),"milk does not list it as cleared");
        assertTrue(source("BeaconNetwork.java").contains("n.putInt(\"hubs\",HubZone.active(p.serverLevel()));"));
        String client=source("BeaconClient.java");
        assertTrue(client.contains("Ui.t(\"overview.hubs.none\")")&&client.contains("Ui.t(\"overview.hubs.one\",1)")&&client.contains("Ui.t(\"overview.hubs\",n(\"hubs\"))"));
        assertTrue(lang.contains("\"gui.eternal_defense.overview.hubs.none\": \"This base does not have any active Logistics Hub\""));
    }

    // The raid skull
    @Test void theSkullCountsTheDeathsLeftBeforeTheBeaconIsHurt() throws Exception{
        assertEquals(3,Rules.DEATHS_PER_PENALTY);
        int[] expected={3,2,1,3,2,1,3};
        for(int deaths=0;deaths<expected.length;deaths++){
            assertEquals(expected[deaths],Rules.livesLeft(deaths),deaths+" deaths");
            // The next death costs health exactly when one life is left
            assertEquals(Rules.livesLeft(deaths)==1,Rules.deathPenalty(deaths+1,Rules.DEATHS_PER_PENALTY,1000)>0,"death "+(deaths+1));
        }
        assertTrue(source("EternalDefense.java").contains("Rules.deathPenalty(d.deaths,Rules.DEATHS_PER_PENALTY,d.maximumHealth())"),"one rule for both");
        assertTrue(source("BeaconNetwork.java").contains("n.putInt(\"livesLeft\",Rules.livesLeft(d.deaths));"));
        String client=source("BeaconClient.java");
        assertTrue(client.contains("int livesWidth=raid?lives(g,font,x+width-8,y+17):0;"),"on the HUD's health line, during a raid");
        assertTrue(client.contains("if(active)lives(g,font,"),"and on the Overview's");
        var skull=javax.imageio.ImageIO.read(Path.of(EnchantTest.ASSETS+"textures/gui/skull.png").toFile());
        assertEquals(9,skull.getWidth());assertEquals(9,skull.getHeight());
        assertTrue(lang().contains("The skull on its health bar counts the deaths it can still take."));
    }

    // The Mess Hall and the Hubs
    @Test void theMessHallCountsTheHubsFoodSoGatherWorksFromTheHubAlone() throws Exception{
        String menu=source("MessHallMenu.java");
        assertTrue(menu.contains("private Map<String,Integer> hubFoods(ServerPlayer sp)"));
        assertTrue(menu.contains("hub.forEach((key,count)->stock.merge(key,count,Integer::sum));"),"the fit and the Gather button count the Hubs");
        assertTrue(menu.contains("long sig=signature(sp.getInventory())*31L+hub.hashCode();"),"a change in the Hubs refreshes the screen");
        assertTrue(menu.contains("var w=Supply.at(sp,pos);var borrowed=Supply.borrow(w,s->IngredientTraits.foodOf(s)!=null);"),"Gather itself takes from them (0.0.40)");
        String take=menu.substring(menu.indexOf("private boolean takeBack(ServerPlayer sp)"));take=take.substring(0,take.indexOf("\n    }")+6);
        assertTrue(take.contains("EternalDefense.give(sp,s)")&&!take.contains("Supply"),"Take back fills the pack, never a Hub");
        assertTrue(lang().contains("lays out the right food from your pack and your Logistics Hubs"));
    }

    // Side quest: the mod's installations add base score
    @Test void theWholeSetOfInstallationsIsWorthExactlyOneTier() throws Exception{
        int of=BaseScoring.INSTALLATIONS.size();
        assertEquals(20,of,"the twenty that can stand in the Home Zone");
        assertEquals(Rules.BUILDING_TIER_SCORE,Rules.installationScore(of,of),"all of them: one building tier (owner: exactly +1 level)");
        assertEquals(1,Rules.buildingTier(Rules.installationScore(of,of)));
        assertEquals(0,Rules.installationScore(0,of));
        for(int n=0;n<=of;n++)assertEquals(25*n,Rules.installationScore(n,of),n+" installations");
        assertEquals(Rules.installationScore(of,of),Rules.installationScore(of+5,of),"never more than the set");
        // One id per installation; the Mk variants count as their station
        assertEquals("mess_hall",BaseScoring.installation("eternal_defense:mess_hall_mk3"));
        assertEquals("drink_mixer",BaseScoring.installation("eternal_defense:drink_mixer_mk1"));
        assertEquals("logistics_hub",BaseScoring.installation("eternal_defense:logistics_hub"));
        for(String no:List.of("logistics_hub_part","structure_part","gate_part","supply_receiving_gate_left","defense_beacon","fob_beacon","supply_catapult","supply_delivery_gate"))
            assertNull(BaseScoring.installation("eternal_defense:"+no),no);
        assertNull(BaseScoring.installation("create:mess_hall"),"only this mod's blocks");
        // Every installation is a block the mod registers, and every registered block is either one or listed as not one
        String registry=source("EternalDefense.java");
        for(String id:BaseScoring.INSTALLATIONS)assertTrue(registry.contains("\""+id)||registry.contains("platform(\""+id.replace("_platform","")+"\")"),id);
        var ignored=Set.of("raider_bridge","structure_part","gate_part","logistics_hub_part","supply_delivery_gate_left","supply_receiving_gate_left","defense_beacon","fob_beacon","supply_catapult","supply_delivery_gate",
            "loadout_station","loadout_station_part","loadout_capsule","loadout_capsule_part","structure_port");
        var blocks=new TreeSet<String>();var m=java.util.regex.Pattern.compile("BLOCKS\\.register\\(\"([a-z0-9_]+)\"|kitchen\\(\"([a-z0-9_]+)\"|drinks\\(\"([a-z0-9_]+)\"").matcher(registry);
        while(m.find())for(int g=1;g<=3;g++)if(m.group(g)!=null)blocks.add(m.group(g));
        for(String id:blocks)assertTrue(ignored.contains(id)||BaseScoring.installation("eternal_defense:"+id)!=null,"a block of the mod that is neither an installation nor left out on purpose: "+id);
        String lang=lang();
        assertTrue(lang.contains("Each of this mod's installations standing in your zone adds 25 points (Installations on the Overview). All 20 together add 500, one whole reward tier."));
        assertTrue(source("BeaconClient.java").contains("\"installations\",\"furnishings\""),"the Overview shows it");
    }
    @Test void noReferenceLineIsRepeated() throws Exception{
        var lang=JsonParser.parseString(lang()).getAsJsonObject();var repeated=new ArrayList<String>();
        for(var e:lang.entrySet()){
            if(!e.getKey().startsWith("gui.eternal_defense.guide.")||!e.getKey().endsWith(".detail"))continue;
            var seen=new HashSet<String>();for(String line:e.getValue().getAsString().split("\n"))if(line.length()>40&&!seen.add(line))repeated.add(e.getKey()+": "+line.substring(0,40));
        }
        assertTrue(repeated.isEmpty(),"a Reference line appears twice (the zone and stations pages had one line 8 times until 0.0.41): "+repeated);
    }

    @Test void theProtocolIsFortyThree(){assertEquals("43",BeaconNetwork.PROTOCOL);}
}
