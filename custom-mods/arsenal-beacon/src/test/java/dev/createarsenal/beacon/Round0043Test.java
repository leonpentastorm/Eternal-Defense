package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 0.0.43 list: the Exchange's iron and andesite alloy, and clay and decorative stones for the FOB's mine. */
final class Round0043Test {
    static TextTable.Row buy(String template,String id){return RaidRoundTest.buys(template).stream().filter(r->r.id().equals(id)).findFirst().orElse(null);}

    // ---- the Exchange -------------------------------------------------------------------------------------------------------------------------
    @Test void ironComesAsOreAtTwiceThePriceAndAndesiteAlloyCostsHalf(){
        assertNull(buy(ExchangeShop.PACK,"minecraft:iron_ingot"),"iron ingots are no longer sold");
        var ore=buy(ExchangeShop.PACK,"minecraft:iron_ore");assertEquals(16,ore.count());assertEquals(2,ore.value(),"16 Iron Ore for 2 (was 16 ingots for 1)");
        var blocks=buy(ExchangeShop.PACK,"minecraft:iron_block");assertEquals(8,blocks.count());assertEquals(8,blocks.value(),"8 blocks for 8 (was 16 for 8)");
        var alloy=buy(ExchangeShop.PACK,"create:andesite_alloy");assertEquals(16,alloy.count());assertEquals(1,alloy.value(),"16 andesite alloy for 1 (was 2)");
        // iron costs twice as much as before, per unit, in both offers
        var oldIngots=buy(ExchangeShop.PACK_0041,"minecraft:iron_ingot");var oldBlocks=buy(ExchangeShop.PACK_0041,"minecraft:iron_block");
        assertEquals(2*oldIngots.value()/(double)oldIngots.count(),ore.value()/(double)ore.count(),1e-9);
        assertEquals(2*oldBlocks.value()/(double)oldBlocks.count(),blocks.value()/(double)blocks.count(),1e-9);
        // and these three lines are the only change
        assertEquals(ExchangeShop.PACK_0041.replace("create:andesite_alloy x16 = 2","create:andesite_alloy x16 = 1").replace("minecraft:iron_ingot x16 = 1","minecraft:iron_ore x16 = 2")
            .replace("minecraft:iron_block x16 = 8","minecraft:iron_block x8 = 8"),ExchangeShop.PACK);
    }
    @Test void anUntouched0041FileGetsTheNewPricesAnEditedOneIsKept(){
        assertTrue(ExchangeShop.oldDefault(ExchangeShop.HEADER+"\n"+ExchangeShop.PACK_0041),"0.0.41 and 0.0.42 wrote this file");
        assertFalse(ExchangeShop.oldDefault(ExchangeShop.HEADER+"\n"+ExchangeShop.PACK),"the new default stays");
        assertFalse(ExchangeShop.oldDefault(ExchangeShop.HEADER+"\n"+ExchangeShop.PACK_0041.replace("minecraft:diamond = 6","minecraft:diamond = 7")),"a file the admin edited is theirs");
        for(String old:List.of(ExchangeShop.PACK_BEFORE_0021,ExchangeShop.PACK_0021,ExchangeShop.PACK_0028))assertTrue(ExchangeShop.oldDefault(ExchangeShop.HEADER+"\n"+old),"older defaults move on too");
    }

    // ---- the mine -------------------------------------------------------------------------------------------------------------------------------
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
        // the Exchange never buys tribute (RouteTest.theExchangeBuysOnlyTheseTributeItems covers the new items too)
        String sell=ExchangeShop.PACK.substring(ExchangeShop.PACK.indexOf("[sell]"),ExchangeShop.PACK.indexOf("[buy]"));
        for(String id:ADDED)assertFalse(sell.contains(id),id);
    }
    @Test void theChooseButtonSaysWhatTheMineSends() throws Exception{
        assertTrue(Round0041Test.lang().contains("\"gui.arsenal_beacon.gate.slot.choose.tip.mine\": \"Pick what the mine sends: redstone, iron, andesite, flint, gunpowder, charcoal, clay, or a stone for building: calcite, dripstone, red sandstone, blackstone or one of Create's stones.\""));
    }
}
