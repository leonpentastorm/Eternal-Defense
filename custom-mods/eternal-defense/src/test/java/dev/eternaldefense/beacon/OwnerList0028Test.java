package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** The list the parts that need no world: the Exchange, the matrices, the recipe, the raid's receiver */
final class OwnerList0028Test {
    static String file(String path) throws Exception{return Files.readString(Path.of(path));}

    @Test void theWarlordSealSellsAtTheExchange(){
        assertTrue(ExchangeShop.PACK.contains("eternal_defense:warlord_seal = 4"),"pack: 4 Ardent Energy");
        for(String list:java.util.List.of(ExchangeShop.PACK))
            assertTrue(list.indexOf("eternal_defense:warlord_seal")<list.indexOf("[buy]"),"a seal is sold to the shop, never bought");
    }
    @Test void restorationMatrixPricesAreHalvedAndTheRecipeMakesEight() throws Exception{
        assertEquals(4,Economy.matrices("restoration_matrix",8));assertEquals(1,Economy.matrices("eternal_defense:restoration_matrix",2));assertEquals(1,Economy.matrices("restoration_matrix",1),"rounded up");
        assertEquals(8,Economy.matrices("reinforced_plating",8),"other parts unchanged");
        assertArrayEquals(new int[]{6},Economy.PACK_AMOUNTS.get(CannonUpgrades.Upgrade.QUANTUM),"Quantum tunneling 12 -> 6");
        assertArrayEquals(new int[]{4},Economy.PACK_AMOUNTS.get(CannonUpgrades.Upgrade.AURA),"Healing aura 8 -> 4");
        assertArrayEquals(new int[]{10},Economy.PACK_AMOUNTS.get(CannonUpgrades.Upgrade.DIMENSION),"Dimensional link 20 -> 10");
        assertTrue(SosTest.source("WeaponPlatform.java").contains("cost(\"eternal_defense:restoration_matrix\",8));"),"the platform's last era: 16 -> 8");
        var recipe=com.google.gson.JsonParser.parseString(file("src/main/resources/data/eternal_defense/recipes/beacon/restoration_matrix.json")).getAsJsonObject();
        assertEquals("create:mechanical_crafting",recipe.get("type").getAsString());
        assertEquals(8,recipe.getAsJsonObject("result").get("count").getAsInt(),"8 per craft");
        assertEquals("eternal_defense:brass_receiver",recipe.getAsJsonObject("key").getAsJsonObject("H").get("item").getAsString(),"a Brass Receiver in the middle");
        String lang=file(EnchantTest.ASSETS+"lang/en_us.json");
        assertTrue(lang.contains("Restoration matrices: Quantum tunneling 6, Healing aura 4, Dimensional link 10."),"the guide's cannon prices");
    }
    @Test void everyWonRaidGivesOnePressedReceiver(){
        String rewards=SosTest.source("RaidRewards.java");
        int hard=rewards.indexOf("if(hard){for(var s:list)s.setCount(");int receiver=rewards.indexOf("add(list,\"eternal_defense:pressed_receiver\",1);");
        assertTrue(hard>0&&receiver>hard,"one Pressed Receiver, added after the hard raid's bonus so it stays one");
        assertTrue(receiver<rewards.indexOf("static List<ItemStack> total("),"in the pack edition's completion rewards");
    }
    @Test void woodCostsAreNamedWood(){
        assertEquals("Wood (any log)",UniversalWood.name("minecraft:logs"));
        assertEquals("Planks (any wood)",UniversalWood.name("minecraft:planks"));
        assertEquals("Any wood slabs",UniversalWood.name("minecraft:wooden_slabs"));
    }
}
