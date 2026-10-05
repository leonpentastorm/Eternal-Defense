package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class SupportTest {
    private static SupportCosts.Entry e(String id,int count,int rarity){return new SupportCosts.Entry(id,count,rarity);}
    private static final Map<String,Double> NONE=Map.of();

    @Test void anEmptyGridCannotBeCalled(){assertEquals(0,SupportCosts.cost(List.of(),NONE));assertEquals(0,SupportCosts.cost(List.of(e("minecraft:iron_ingot",0,0)),NONE));}
    @Test void costScalesWithRarityAndQuantity(){
        assertEquals(5+1,SupportCosts.cost(List.of(e("minecraft:iron_ingot",1,0)),NONE));
        assertEquals(5+64,SupportCosts.cost(List.of(e("minecraft:iron_ingot",64,0)),NONE));
        assertEquals(5+6,SupportCosts.cost(List.of(e("minecraft:diamond",1,1)),NONE));
        assertEquals(5+16,SupportCosts.cost(List.of(e("minecraft:netherite_ingot",1,3)),NONE));
        assertEquals(5+20,SupportCosts.cost(List.of(e("mod:mythic_thing",1,3)),NONE),"unknown epic items use the epic base");
        assertEquals(5+8,SupportCosts.cost(List.of(e("mod:rare_thing",1,2)),NONE));
        assertEquals(5+3,SupportCosts.cost(List.of(e("mod:fine_thing",1,1)),NONE));
        assertEquals(5+1,SupportCosts.cost(List.of(e("mod:plain_thing",1,0)),NONE));
    }
    @Test void bulkBlocksAreCheap(){
        assertEquals(5+7,SupportCosts.cost(List.of(e("minecraft:cobblestone",64,0)),NONE),"64 cobblestone is 6.4 -> 7");
        assertEquals(5+7,SupportCosts.cost(List.of(e("minecraft:oak_planks",64,0)),NONE));
        assertTrue(SupportCosts.cost(List.of(e("minecraft:cobblestone",64,0)),NONE)<SupportCosts.cost(List.of(e("minecraft:iron_ingot",64,0)),NONE));
    }
    @Test void everyStackInTheGridIsAdded(){
        assertEquals(5+1+6+2,SupportCosts.cost(List.of(e("minecraft:iron_ingot",1,0),e("minecraft:diamond",1,1),e("minecraft:gold_ingot",1,0)),NONE));
    }
    @Test void tacz_gunsAmmoAndAttachmentsHaveTheirOwnTiers(){
        assertEquals(5+45,SupportCosts.cost(List.of(e("tacz:modern_kinetic_gun",1,1)),NONE));
        assertEquals(5+(int)Math.ceil(0.35*30),SupportCosts.cost(List.of(e("tacz:ammo",30,0)),NONE));
        assertEquals(5+12,SupportCosts.cost(List.of(e("tacz:attachment",1,1)),NONE));
    }
    @Test void adminOverridesAlwaysWin(){
        var rows=TextTable.parse(List.of("minecraft:diamond = 2","minecraft:arrow x16 = 4"),id->true,4096,100000).rows();
        var overrides=SupportCosts.overridesFrom(rows);
        assertEquals(5+2,SupportCosts.cost(List.of(e("minecraft:diamond",1,1)),overrides));
        assertEquals(5+4,SupportCosts.cost(List.of(e("minecraft:arrow",16,0)),overrides),"x16 = 4 means a quarter each");
    }
    @Test void platformGridGrowsFromThreeToSix(){
        assertArrayEquals(new int[]{9,16,25,36},new int[]{SupportRules.slots(1),SupportRules.slots(2),SupportRules.slots(3),SupportRules.slots(4)});
        assertEquals(16,SupportRules.upgradeCost(1));assertEquals(32,SupportRules.upgradeCost(2));assertEquals(64,SupportRules.upgradeCost(3));assertEquals(-1,SupportRules.upgradeCost(4));
        assertEquals(3,SupportRules.grid(0),"out-of-range Mk clamps");assertEquals(6,SupportRules.grid(9));
    }
    @Test void fireSupportRunsTwelveSecondsOfBlastsEveryTwo(){
        assertEquals(12,SupportRules.FIRE_BLASTS*SupportRules.FIRE_INTERVAL_TICKS/20);assertEquals(40,SupportRules.FIRE_INTERVAL_TICKS);
    }
    @Test void theFieldGuideQuotesTheRealNumbers(){
        String text=guide("support.detail")+guide("support.body");
        for(String expected:List.of(SupportRules.SUPPLY_FLARE_PRICE+" energy",SupportRules.RETURN_FLARE_PRICE+"",SupportRules.FIRE_FLARE_PRICE+"",SupportRules.CANNON_PRICE+"",
                "Return "+SupportRules.RETURN_CALL_PRICE,"Fire Support "+SupportRules.FIRE_CALL_PRICE,"flat "+SupportRules.SUPPLY_CALL_FEE,
                SupportRules.FIRE_BLASTS*SupportRules.FIRE_INTERVAL_TICKS/20+" seconds ("+SupportRules.FIRE_BLASTS+" blasts)","within "+SupportRules.FIRE_RADIUS+" blocks",
                SupportRules.FIRE_DAMAGE+" damage","within "+SupportRules.BLAST_RADIUS+" blocks and","within "+SupportRules.CANNON_RANGE+" blocks",
                SupportRules.RETURN_CHANNEL_TICKS/20+" seconds",SupportRules.PARCEL_LIFETIME_TICKS/1200+" minutes",
                SupportRules.UPGRADE_PLATING[1]+", "+SupportRules.UPGRADE_PLATING[2]+" and "+SupportRules.UPGRADE_PLATING[3]))
            assertTrue(text.contains(expected),"guide does not mention '"+expected+"'");
    }
    private static String guide(String key){
        try(var in=SupportTest.class.getResourceAsStream("/assets/arsenal_beacon/lang/en_us.json")){
            var json=com.google.gson.JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            return json.get("gui.arsenal_beacon.guide."+key).getAsString();
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
