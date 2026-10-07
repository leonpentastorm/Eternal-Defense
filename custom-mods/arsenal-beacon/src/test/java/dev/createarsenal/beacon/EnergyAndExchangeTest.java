package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class EnergyAndExchangeTest {
    @Test void dropChanceFollowsRollAndLooting(){
        assertEquals(1,ArdentEnergy.amount(.11,.12,0,.03,false,12,20));
        assertEquals(0,ArdentEnergy.amount(.12,.12,0,.03,false,12,20));
        assertEquals(1,ArdentEnergy.amount(.20,.12,3,.03,false,12,20),"Looting III raises the chance to 21%");
        assertEquals(0,ArdentEnergy.amount(.22,.12,3,.03,false,12,20));
    }
    @Test void toughMobsPayMoreAndBossesAlwaysPay(){
        assertEquals(2,ArdentEnergy.amount(0,.12,0,.03,false,12,80));
        assertEquals(12,ArdentEnergy.amount(.99,.12,0,.03,true,12,20));
        assertEquals(0,ArdentEnergy.amount(0,0,0,0,false,12,20),"A zero chance never drops");
        assertEquals(1,ArdentEnergy.amount(.999,1,0,0,false,12,20),"A certain chance always drops");
    }
    @Test void offerFileParsesCountsCommentsAndCase(){
        var r=TextTable.parse(List.of("# header","","arsenal_beacon:universal_ammo_coin = 4","Minecraft:Arrow x16 = 20","  minecraft:diamond   =   40  ","// note"),id->true,64,9999);
        assertTrue(r.problems().isEmpty(),r.problems().toString());
        assertEquals(List.of(new TextTable.Row("arsenal_beacon:universal_ammo_coin",1,4),new TextTable.Row("minecraft:arrow",16,20),new TextTable.Row("minecraft:diamond",1,40)),r.rows());
    }
    @Test void badLinesAreReportedAndSkippedNotFatal(){
        var r=TextTable.parse(List.of("diamond = 4","minecraft:nope = 3","minecraft:arrow x0 = 3","minecraft:arrow x65 = 3","minecraft:arrow = 0","minecraft:arrow = 10000","minecraft:stick = 2"),id->!id.equals("minecraft:nope"),64,9999);
        assertEquals(List.of(new TextTable.Row("minecraft:stick",1,2)),r.rows());
        assertEquals(6,r.problems().size());
    }
    @Test void aFileWithNoValidOffersFallsBackToAmmoCoins(){
        assertEquals(1,ExchangeShop.DEFAULT_BUY.size());
        assertEquals("arsenal_beacon:universal_ammo_coin",ExchangeShop.DEFAULT_BUY.get(0).id());
    }
    @Test void bothTemplatesHaveBuyAndSellSectionsAndBuyingCostsMoreThanSelling(){
        for(String template:List.of(ExchangeShop.PACK,ExchangeShop.STANDALONE)){
            var parts=ExchangeShop.sections(List.of(template.split("\n")));
            assertTrue(parts.containsKey("buy")&&parts.containsKey("sell"));
            var buy=TextTable.parse(parts.get("buy"),id->true,64,9999);var sell=TextTable.parse(parts.get("sell"),id->true,64,9999);
            assertTrue(buy.problems().isEmpty()&&sell.problems().isEmpty(),buy.problems()+" "+sell.problems());
            assertFalse(buy.rows().isEmpty());assertFalse(sell.rows().isEmpty());
            // no loop pays: for an item that is both sold and bought, one unit costs more than it earns
            for(var b:buy.rows())for(var sl:sell.rows())if(b.id().equals(sl.id()))assertTrue(b.value()/(double)b.count()>sl.value()/(double)sl.count(),"buying "+b.id()+" must cost more than selling it");
        }
        assertTrue(ExchangeShop.sections(List.of("junk","[Sell]","a = 1","[buy]","b = 2")).get("sell").contains("a = 1"));
    }
    @Test void specialRaidsStartLateAndHalfTheTime(){
        for(int raid=1;raid<RaidTypes.FIRST_SPECIAL_RAID;raid++)assertEquals("normal",RaidTypes.roll(raid,0,0),"early raids are ordinary so support can be afforded first");
        assertEquals("normal",RaidTypes.roll(9,50,2));assertEquals("normal",RaidTypes.roll(9,99,2));
        assertEquals("air",RaidTypes.roll(4,49,0));assertEquals("paratroopers",RaidTypes.roll(4,0,1));assertEquals("siege",RaidTypes.roll(30,10,2));assertEquals("swarm",RaidTypes.roll(30,10,3));
        int special=0;for(int p=0;p<100;p++)if(!RaidTypes.roll(6,p,p).equals("normal"))special++;assertEquals(50,special);
        assertEquals(3,RaidTypes.bonusEnergy(0));assertTrue(RaidTypes.bonusEnergy(5)>RaidTypes.bonusEnergy(2));
        assertEquals(40,RaidTypes.count("swarm",20));assertEquals(5,RaidTypes.count("paratroopers",10));assertEquals(10,RaidTypes.count("air",10));
    }
    @Test void upgradePricesAreSane(){
        for(var u:CannonUpgrades.Upgrade.values()){
            for(int level=0;level<u.max();level++){
                assertTrue(u.price(level)>0&&Economy.PACK_AMOUNTS.get(u)[level]>0);
                if(level>0)assertTrue(u.price(level)>u.price(level-1),u+" gets dearer per level");
            }
            assertEquals(u.max(),Economy.PACK_AMOUNTS.get(u).length);
        }
        int total=0;for(var u:CannonUpgrades.Upgrade.values())for(int l=0;l<u.max();l++)total+=u.price(l);
        assertTrue(total<250,"the whole cannon is a few dozen raids of income, not hundreds: "+total);
        assertTrue(SupportRules.CANNON_PRICE+SupportRules.SUPPLY_FLARE_PRICE+SupportRules.RETURN_FLARE_PRICE+SupportRules.FIRE_FLARE_PRICE<=24,"basic support costs about two early raids");
        for(int l=0;l<5;l++)assertTrue(Economy.BEACON_ARDENT[l]>0&&(l==0||Economy.BEACON_ARDENT[l]>Economy.BEACON_ARDENT[l-1]));
        assertEquals(8,Rules.upgradeCost(0));
    }
}
