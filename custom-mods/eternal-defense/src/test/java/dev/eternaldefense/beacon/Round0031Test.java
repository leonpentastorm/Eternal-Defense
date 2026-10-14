package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The list, the parts that need no world: the gate's storage, its buttons, the Command Table's FOB tab */
final class Round0031Test {
    static String lang() throws Exception{return Files.readString(Path.of(EnchantTest.ASSETS+"lang/en_us.json"));}

    @Test void bothGatesCostTwoChestsForTheStorage(){
        assertEquals(2,Economy.GATE_CHESTS);
        String economy=SosTest.source("Economy.java");
        assertTrue(economy.contains("case \"supply_delivery_gate\",\"supply_receiving_gate\"->List.of(")&&economy.contains("WeaponPlatform.cost(\"minecraft:chest\",GATE_CHESTS)"),"one price for both gates, with the chests");
        assertEquals(54,FobData.DEPOT_SLOTS,"a double chest");
    }
    @Test void theStorageIsTakeOnly(){
        String gates=SosTest.source("RailGates.java");
        assertTrue(gates.contains("@Override public boolean mayPlace(ItemStack stack){return false;}"),"nothing goes in by hand");
        assertTrue(SosTest.source("Routes.java").contains("anchor.equals(f.receivingGate)?f.depot():null"),"only the Receiving Gate opens the storage");
    }
    /** Every button of the gate screen has its own id, and the ids that do not fit a byte go by the mod's own packet. */
    @Test void everyGateButtonHasItsOwnId(){
        var ids=new HashSet<Integer>();
        for(int i=0;i<FobRules.MAX_FOBS;i++)assertTrue(ids.add(Routes.LINK+i));
        for(var slot:RouteRules.Slot.values())assertTrue(ids.add(Routes.TOGGLE+slot.ordinal()),"toggle "+slot);
        for(var spec:RouteRules.Specialty.values())for(var slot:RouteRules.Slot.values())assertTrue(RouteRules.options(slot,spec).size()<=Routes.CHOICES,"room for every choice of "+slot);
        for(var slot:RouteRules.Slot.values())for(int i=0;i<RouteRules.options(slot,RouteRules.Specialty.TRADER).size();i++)assertTrue(ids.add(Routes.chooseButton(slot,i)),slot+" "+i);
        assertTrue(Routes.LINK+FobRules.MAX_FOBS<=Routes.TOGGLE&&Routes.TOGGLE+RouteRules.Slot.values().length<=Routes.CHOOSE);
        assertTrue(Routes.chooseButton(RouteRules.Slot.SPECIALTY,RouteRules.TRADER_LOOT.size()-1)>Byte.MAX_VALUE,"these ids would not survive the vanilla button packet");
    }
    /** The vanilla button packet carries a byte: ids above 127 broke on a dedicated server. */
    @Test void menuButtonsGoByTheModsOwnPacket(){
        String net=SosTest.source("BeaconNetwork.java");
        assertTrue(net.contains("b.writeVarInt(p.container);b.writeVarInt(p.id);"),"the id as a VarInt");
        assertTrue(net.contains("CHANNEL.registerMessage(22,MenuButton.class"));
        assertTrue(net.contains("if(menu.clickMenuButton(player,p.id))menu.broadcastChanges();"),"handled as vanilla does");
        for(String client:List.of("EnchantClient.java","FobClient.java","GateClient.java","TacticalClient.java","MessHallScreen.java")){
            String s=SosTest.source(client);
            assertFalse(s.contains("handleInventoryButtonClick"),client+" still sends the vanilla packet");
            assertTrue(s.contains("BeaconNetwork.menuButton("),client);
        }
        assertTrue(TacticalEnchanting.BUY>Byte.MAX_VALUE,"the case that broke");
    }
    @Test void deliveriesComeAtMostEveryFiveMinutes(){
        assertEquals(6_000,RouteRules.MIN_PERIOD);assertEquals(RouteRules.MIN_PERIOD,RouteRules.period(10));
        assertEquals(Math.round(2*5000/RouteRules.PASSIVE_EFFICIENCY),RouteRules.period(5000));
        String routes=SosTest.source("Routes.java");
        assertTrue(routes.contains("haul(f,RouteRules.rideTrips(sinceLast(d,f,0),period),d.onlineTicks)"),"a ride is capped by the time since the last delivery");
        assertTrue(routes.contains("return f.lastTrip==0?Long.MAX_VALUE/4:"),"a route that never delivered gets a full first ride");
        assertTrue(routes.contains("f.lastTrip=now;"),"every delivery, passive or ridden, sets the time");
    }
    @Test void aNewFobGetsASpecialtyThatIsSaved(){
        var d=new FobData();var seen=new HashSet<RouteRules.Specialty>();
        for(int i=0;i<200;i++){var f=d.add(new BlockPos(i*300,64,0),i,0);assertNotNull(f.specialty);seen.add(f.specialty);
            if(i==0){f.specialty=RouteRules.Specialty.TRADER;assertEquals(RouteRules.Specialty.TRADER,FobData.load(d.save(new CompoundTag())).byId(f.id).specialty);}}
        assertEquals(RouteRules.Specialty.values().length,seen.size(),"every specialty can be rolled");
    }
    @Test void theScreensHaveTheirLines() throws Exception{
        String lang=lang();
        for(String key:List.of("tactical.view.fobs","tactical.fob.title","tactical.fob.none","tactical.fob.sos","tactical.fob.reprint","tactical.fob.arrow",
                "tactical.result.no_sos","tactical.result.sos_reprinted","fob.removed_storage"))
            assertTrue(lang.contains("\"gui.eternal_defense."+key+"\""),"no line for "+key);
        for(var spec:RouteRules.Specialty.values())assertTrue(lang.contains("\"gui.eternal_defense.gate.specialty."+spec.name().toLowerCase(java.util.Locale.ROOT)+"\""),spec.name());
        assertEquals(6,TacticalBlocks.REPRINT_SOS);
        assertTrue(SosTest.source("Hunts.java").contains("case TacticalBlocks.REPRINT_SOS"),"the table's button reprints the SOS map");
    }
}
