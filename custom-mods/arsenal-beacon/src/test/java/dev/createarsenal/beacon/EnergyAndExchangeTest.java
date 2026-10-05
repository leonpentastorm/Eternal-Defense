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
        assertEquals(1,ExchangeShop.DEFAULT.size());
        assertEquals("arsenal_beacon:universal_ammo_coin",ExchangeShop.DEFAULT.get(0).id());
        var template=TextTable.parse(List.of(ExchangeShop.TEMPLATE.split("\n")),id->true,64,9999);
        assertEquals(ExchangeShop.DEFAULT,template.rows(),"The generated template ships the documented default offer");
        assertTrue(template.problems().isEmpty());
    }
}
