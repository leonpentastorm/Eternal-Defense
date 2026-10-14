package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The list: the Exchange's iron and andesite alloy, and clay and decorative stones for the FOB's mine. */
final class Round0043Test {
    static TextTable.Row buy(String template,String id){return RaidRoundTest.buys(template).stream().filter(r->r.id().equals(id)).findFirst().orElse(null);}

    // The Exchange
    @Test void ironComesAsOreAtTwiceThePriceAndAndesiteAlloyCostsHalf(){
        assertNull(buy(ExchangeShop.PACK,"minecraft:iron_ingot"),"iron ingots are no longer sold");
        var ore=buy(ExchangeShop.PACK,"minecraft:iron_ore");assertEquals(16,ore.count());assertEquals(2,ore.value(),"16 Iron Ore for 2 (was 16 ingots for 1)");
        var blocks=buy(ExchangeShop.PACK,"minecraft:iron_block");assertEquals(8,blocks.count());assertEquals(8,blocks.value(),"8 blocks for 8 (was 16 for 8)");
        var alloy=buy(ExchangeShop.PACK,"create:andesite_alloy");assertEquals(16,alloy.count());assertEquals(1,alloy.value(),"16 andesite alloy for 1 (was 2)");
        // Iron costs twice as much per unit as 16 ingots for 1 and 16 blocks for 8 did
        assertEquals(2*1/16.0,ore.value()/(double)ore.count(),1e-9);
        assertEquals(2*8/16.0,blocks.value()/(double)blocks.count(),1e-9);
    }

    // The mine
    static final List<String> ADDED=List.of("minecraft:clay","minecraft:calcite","minecraft:dripstone_block","minecraft:red_sandstone","minecraft:blackstone",
        "create:asurine","create:crimsite","create:ochrum","create:veridium","create:limestone","create:scoria","create:scorchia");
    @Test void theMineSendsClayAndDecorativeStones(){
        var ids=RouteRules.MINE_LOOT.stream().map(RouteRules.Loot::id).toList();
        assertEquals(List.of("minecraft:redstone","minecraft:iron_ingot","minecraft:andesite","minecraft:flint","minecraft:gunpowder","minecraft:charcoal"),ids.subList(0,6),
            "the first six keep their place: a FOB's choice is saved as its index");
        assertEquals(ADDED,ids.subList(6,ids.size()));
        assertEquals(ids.size(),new HashSet<>(ids).size(),"no item twice");
        assertTrue(ids.size()<=Routes.CHOICES,"a button for each");
        for(var l:RouteRules.MINE_LOOT){
            assertTrue(l.id().startsWith("minecraft:")||l.id().startsWith("create:"),"only items the pack always has (Create is required): "+l.id());
            assertEquals(0,l.tier(),"nothing in the mine is locked");assertTrue(RouteRules.unlocked(l,0));
        }
        assertEquals(1,Round0032Test.find(RouteRules.MINE_LOOT,"minecraft:clay").value(),1e-9,"a clay block (4 clay balls) is a common item");
        for(String stone:ADDED.subList(1,ADDED.size()))assertEquals(Round0032Test.find(RouteRules.MINE_LOOT,"minecraft:andesite").value(),Round0032Test.find(RouteRules.MINE_LOOT,stone).value(),1e-9,stone+" is a stone block, as andesite");
        // The Exchange never buys tribute (RouteTest.theExchangeBuysOnlyTheseTributeItems covers the new items too)
        String sell=ExchangeShop.PACK.substring(ExchangeShop.PACK.indexOf("[sell]"),ExchangeShop.PACK.indexOf("[buy]"));
        for(String id:ADDED)assertFalse(sell.contains(id),id);
    }
    @Test void theChooseButtonSaysWhatTheMineSends() throws Exception{
        assertTrue(Round0041Test.lang().contains("\"gui.eternal_defense.gate.slot.choose.tip.mine\": \"Pick what the mine sends: redstone, iron, andesite, flint, gunpowder, charcoal, clay, or a stone for building: calcite, dripstone, red sandstone, blackstone or one of Create's stones.\""));
    }
}
