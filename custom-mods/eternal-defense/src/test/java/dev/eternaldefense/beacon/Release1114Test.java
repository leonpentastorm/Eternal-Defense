package dev.eternaldefense.beacon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.14: the five Age armor sets with their set bonuses, and the Handheld Radio. */
final class Release1114Test {
    static final String[] SLOTS={"head","chest","legs","feet"},PIECES={"helmet","chestplate","leggings","boots"};
    static final String ASSETS="src/main/resources/assets/eternal_defense/";
    static double setScore(int age){double s=0;for(int i=0;i<4;i++)s+=ArmorBalance.score(ArmorSetRules.DEFENSE[age-1][i],ArmorSetRules.TOUGHNESS[age-1],0,SLOTS[i],10)*ArmorBalance.protectionShare(SLOTS[i])/20.0;return s;}

    @Test void frontierIsIronModernIsDiamondAndLaterAgesDifferByTheirBonus(){
        assertArrayEquals(new int[]{2,6,5,2},ArmorSetRules.DEFENSE[0],"Frontier: iron armor points");assertEquals(0,ArmorSetRules.TOUGHNESS[0]);assertEquals(15,ArmorSetRules.DURABILITY[0],"iron durability");
        assertEquals(16,java.util.Arrays.stream(ArmorSetRules.DEFENSE[1]).sum(),"World Wars: one point over iron");assertEquals(1,ArmorSetRules.TOUGHNESS[1]);
        for(int age=3;age<=5;age++){assertArrayEquals(new int[]{3,8,6,3},ArmorSetRules.DEFENSE[age-1],"diamond armor points");assertEquals(2,ArmorSetRules.TOUGHNESS[age-1]);assertEquals(33,ArmorSetRules.DURABILITY[age-1],"diamond durability");}
        assertEquals(10,setScore(1),1e-9,"protects like a vanilla iron set");assertEquals(17.5,setScore(3),1e-9,"protects like a vanilla diamond set");
        assertTrue(setScore(1)<setScore(2)&&setScore(2)<setScore(3));assertEquals(setScore(3),setScore(5),1e-9,"Advanced and Exotic are stronger through their bonus only");
        assertEquals(List.of("frontier","worldwars","modern","advanced","exotic"),ArmorSetRules.SETS);for(int a=1;a<=5;a++)assertEquals(a,ArmorSetRules.age(ArmorSetRules.SETS.get(a-1)));
        assertEquals(ArmorSetRules.SETS,ArmorSetLayers.SETS,"the worn models cover every set");
    }
    @Test void theSetBonusesAreTheChosenOnes(){
        assertEquals(.15,ArmorSetRules.QUICK_DRAW);assertEquals(.10,ArmorSetRules.GUN_DAMAGE);assertEquals(.25,ArmorSetRules.WARDED);
        assertEquals(.25,ArmorSetRules.warded("advanced","bullet"));assertEquals(.25,ArmorSetRules.warded("advanced","explosion"));
        assertEquals(0,ArmorSetRules.warded("advanced","melee"));assertEquals(0,ArmorSetRules.warded("exotic","bullet"));assertEquals(0,ArmorSetRules.warded("modern","explosion"));
        assertEquals(1200,ArmorSetRules.WARD_COOLDOWN,"once a minute");assertEquals(200,ArmorSetRules.WARD_ABSORPTION_TICKS,"10 seconds");
        assertEquals(8,4*(ArmorSetRules.WARD_ABSORPTION_LEVEL+1),"Absorption II: 8 health, 4 hearts");assertEquals(4,ArmorSetRules.EXOTIC_HEALTH,"2 extra hearts");
        assertTrue(ArmorSetRules.wardFires(20,20,15,100,0),"5 health left of 20: below 30%");
        assertFalse(ArmorSetRules.wardFires(20,20,13,100,0),"7 left: above 30%");
        assertFalse(ArmorSetRules.wardFires(5,20,6,100,0),"a lethal hit is not warded");
        assertFalse(ArmorSetRules.wardFires(20,20,15,100,101),"still cooling down");
        String bonus=read("src/main/java/dev/eternaldefense/beacon/ArmorSetBonus.java");
        assertTrue(bonus.contains("FRONTIER,MealGunBackend::reload,ArmorSetRules.QUICK_DRAW")&&bonus.contains("WORLDWARS,MealGunBackend::damage")&&bonus.contains("MODERN,MealGunBackend::damage"),"Quick Draw, Stormtrooper and Night Operator use the TaCZ Attributes");
        assertTrue(bonus.contains("ADVANCED,()->Attributes.KNOCKBACK_RESISTANCE,1")&&bonus.contains("EXOTIC,()->Attributes.KNOCKBACK_RESISTANCE,1"),"no knockback on Advanced and Exotic");
        assertTrue(bonus.contains("set==ArmorSetMaterials.MODERN?MobEffects.NIGHT_VISION:set==ArmorSetMaterials.EXOTIC?MobEffects.REGENERATION"),"Night Vision and Regeneration");
        assertTrue(bonus.contains("set==ArmorSetMaterials.WORLDWARS&&e==MobEffects.POISON||set==ArmorSetMaterials.ADVANCED&&e==MobEffects.MOVEMENT_SLOWDOWN"),"Poison and Slowness immunity");
        assertTrue(bonus.contains("i.isInfiniteDuration()&&i.isAmbient()"),"only the set's own effects are taken off with it");
        assertTrue(read("src/main/java/dev/eternaldefense/beacon/EternalDefense.java").contains("MinecraftForge.EVENT_BUS.register(new ArmorSetBonus.SetEvents());"));
    }
    @Test void theTextQuotesTheRealNumbers() throws Exception{
        var lang=JsonParser.parseString(read(ASSETS+"lang/en_us.json")).getAsJsonObject();
        String names="frontier Ranger,worldwars Trench,modern Operator,advanced Vanguard Exo,exotic Resonance";
        for(String set:ArmorSetRules.SETS){
            for(String piece:PIECES)assertTrue(lang.has("item.eternal_defense."+set+"_"+piece),set+"_"+piece);
            assertTrue(lang.has("tooltip.eternal_defense.armor_set."+set+".name")&&lang.has("tooltip.eternal_defense.armor_set."+set+".desc"),set);
        }
        assertTrue(names.contains("Vanguard Exo"));
        String t=lang.get("tooltip.eternal_defense.armor_set.frontier.desc").getAsString()+lang.get("tooltip.eternal_defense.armor_set.worldwars.desc").getAsString()+lang.get("tooltip.eternal_defense.armor_set.modern.desc").getAsString()
            +lang.get("tooltip.eternal_defense.armor_set.advanced.desc").getAsString()+lang.get("tooltip.eternal_defense.armor_set.exotic.desc").getAsString();
        for(String n:List.of(pct(ArmorSetRules.QUICK_DRAW),pct(ArmorSetRules.GUN_DAMAGE),pct(ArmorSetRules.WARDED),pct(ArmorSetRules.WARD_BELOW),(ArmorSetRules.EXOTIC_HEALTH/2)+" extra hearts",
            (4*(ArmorSetRules.WARD_ABSORPTION_LEVEL+1)/2)+" extra hearts for "+ArmorSetRules.WARD_ABSORPTION_TICKS/20+" seconds","Poison","Slowness","Night Vision","Regeneration","no knockback"))assertTrue(t.contains(n),n);
        String stations=Release114Test.text("guide.stations.body");
        assertTrue(stations.contains("# Age Armor Sets\n- What it is:")&&stations.contains("\n# Set bonuses\n"));
        assertTrue(stations.contains("It always makes the five Age Armor Sets."));
        assertFalse(stations.contains("With no armor mod installed it has nothing to make"),"the table is never empty now");
        for(String n:List.of(pct(ArmorSetRules.QUICK_DRAW),pct(ArmorSetRules.WARDED),pct(ArmorSetRules.WARD_BELOW)))assertTrue(stations.contains(n),n);
        String support=Release114Test.text("guide.support.body");
        assertTrue(support.contains("# Handheld Radio\n- What it is:"));
        assertTrue(support.contains("up to "+SupportRules.RADIO_RANGE+" blocks away")&&support.contains("up to "+SupportRules.RADIO_FLARES+" of each"));
        assertTrue(Release114Test.text("guide.support.detail").contains("Handheld Radio "+Economy.RADIO_PRICE));
        assertTrue(Release114Test.text("support.product.radio").equals("Handheld Radio"));
    }
    static String pct(double share){return Math.round(share*100)+"%%";}
    @Test void theArmorTableAlwaysListsTheSets() throws Exception{
        String armor=Release114Test.source("ArmorPlatform.java");
        assertTrue(armor.contains("for(var set:EternalDefense.ARMOR_SETS)"),"the sets are listed without any armor mod");
        assertTrue(armor.contains("p.output().getItem() instanceof ArmorSetItem?null:medians.get(p.age())"),"priced as a typical piece of their Age");
        assertTrue(armor.contains("if(stack.getItem() instanceof ArmorSetItem a)return a.set().age();"),"their Age is fixed");
        int override=armor.indexOf("CraftBalance.override("),own=armor.indexOf("instanceof ArmorSetItem a)return a.set().age()");assertTrue(override>0&&override<own,"a hand-set Age still wins");
        assertFalse(ArmorPlatform.MODS.contains(EternalDefense.ID));
        assertTrue(Release114Test.source("WorkshopFabrication.java").contains(".isEmpty()"),"the table is sold when it has content, and now it always has");
    }
    @Test void everyPieceHasItsArt() throws Exception{
        for(String set:ArmorSetRules.SETS){
            assertTrue(Files.exists(Path.of(ASSETS+"textures/models/armor/"+set+".png")),set+" worn texture");
            assertTrue(Files.exists(Path.of(ASSETS+"textures/item/armor/"+set+".png")),set+" item texture");
            for(String piece:PIECES){var model=JsonParser.parseString(read(ASSETS+"models/item/"+set+"_"+piece+".json")).getAsJsonObject();
                assertEquals("eternal_defense:item/armor/"+set,model.getAsJsonObject("textures").get("0").getAsString());}
        }
        assertTrue(Files.exists(Path.of(ASSETS+"textures/item/handheld_radio.png"))&&Files.exists(Path.of(ASSETS+"models/item/handheld_radio.json")));
        assertFalse(Files.exists(Path.of("src/main/resources/data/minecraft/tags/items/trimmable_armor.json")),"trims do not fit these textures");
        String layers=Release114Test.source("ArmorSetLayers.java");assertFalse(layers.contains(".py")||layers.contains("GENERATED"),"no tool names in the code");
        String item=Release114Test.source("ArmorSetItem.java");assertTrue(item.contains("textures/models/armor/\"+set.key()+\".png")&&item.contains("ArmorSetClient.model(set.key(),slot,original)"));
        assertTrue(Release114Test.source("CreativeCatalog.java").contains("ARMOR.stream()"));
    }
    @Test void theRadioCarriesFlaresAndPlantsThem() throws Exception{
        assertEquals(48,SupportRules.RADIO_RANGE);assertEquals(16,SupportRules.RADIO_FLARES);assertEquals(8,Economy.RADIO_PRICE);
        var hit=new net.minecraft.world.phys.Vec3(3.2,64,7.9);var front=new net.minecraft.core.BlockPos(4,64,7);
        assertEquals(hit,HandheldRadio.marker(hit,net.minecraft.core.Direction.UP,front,null),"aimed at the ground: right there");
        assertEquals(new net.minecraft.world.phys.Vec3(4.5,62,7.5),HandheldRadio.marker(hit,net.minecraft.core.Direction.EAST,front,62.0),"aimed at a wall: on the ground in front of it");
        assertNull(HandheldRadio.marker(hit,net.minecraft.core.Direction.EAST,front,null),"no ground near: refused");
        String flares=Release114Test.source("SupportFlares.java"),radio=Release114Test.source("HandheldRadio.java");
        assertTrue(flares.contains("!call(sp,kind,null)"),"a thrown flare makes the same call");
        assertTrue(flares.contains("if(marker!=null){flare.setPos(marker.x,marker.y,marker.z);level.addFreshEntity(flare);flare.land(marker);return true;}"),"a planted flare lands where it was marked");
        assertTrue(radio.contains("if(!SupportFlares.call(p,kind,at))return;\n        if(!p.getAbilities().instabuild)count(radio,kind,count(radio,kind)-1);"),"a flare is used only when the call goes out");
        assertTrue(radio.contains("DefenseSounds.RADIO.get()")&&radio.contains("if(sp.isShiftKeyDown()){open(sp,hand);"),"sneak and use opens the menu with the radio's static");
        assertTrue(radio.contains("d.players.setActive(sp.getUUID(),slot);d.setDirty();"),"the kit choice is the station's active slot");
        assertTrue(radio.contains("type==ClickType.SWAP&&(button==locked"),"the radio cannot be swapped out of the hand");
        assertTrue(Release114Test.source("SupportShop.java").contains("new Product(\"radio\",()->EternalDefense.RADIO.get(),Economy.RADIO_PRICE)"));
        assertTrue(Release114Test.source("SupportClient.java").contains("MenuScreens.register(EternalDefense.RADIO_MENU.get(),RadioScreen::new)"));
        var sounds=JsonParser.parseString(read(ASSETS+"sounds.json")).getAsJsonObject();
        assertEquals("eternal_defense:sfx/radio_static",sounds.getAsJsonObject("radio.static").getAsJsonArray("sounds").get(0).getAsString(),"a short burst of the radio static (1 s)");
    }
    static String read(String path){try{return Files.readString(Path.of(path));}catch(Exception e){throw new IllegalStateException(e);}}
}
