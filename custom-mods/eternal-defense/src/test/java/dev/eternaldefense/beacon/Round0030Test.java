package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The list, the parts that need no world: the FOB outline switch, the preview's lines */
final class Round0030Test {
    static String lang() throws Exception{return Files.readString(Path.of(EnchantTest.ASSETS+"lang/en_us.json"));}

    @Test void theZoneOutlineSwitchIsSavedAndStartsOn(){
        var d=new FobData();var f=d.add(new BlockPos(100,64,100),0,0);
        assertTrue(f.showZone,"a new FOB shows its outline");
        f.showZone=false;
        assertFalse(FobData.load(d.save(new CompoundTag())).byId(f.id).showZone,"turned off, it stays off after a save");
        // A FOB saved has no such key: it shows its outline
        var old=d.save(new CompoundTag());((CompoundTag)old.getList("fobs",10).get(0)).remove("showZone");
        assertTrue(FobData.load(old).byId(f.id).showZone);
        assertEquals(7,Fobs.OUTLINE,"its own menu button, after the repair");
    }
    @Test void everyRefusalTheItemCanGiveHasAPreviewLine() throws Exception{
        String lang=lang();
        // The reasons FobRules.plantProblem gives (and the Overworld check before it)
        for(String problem:List.of("overworld","no_beacon","max","no_raids","not_village","taken"))
            assertTrue(lang.contains("\"gui.eternal_defense.fob.preview.refused."+problem+"\""),"no preview line for "+problem);
        for(String key:List.of("look","good","villagers"))assertTrue(lang.contains("\"gui.eternal_defense.fob.preview."+key+"\""));
        assertTrue(lang.contains("\"gui.eternal_defense.fob.screen.outline\"")&&lang.contains("\"gui.eternal_defense.fob.screen.outline.tip\""));
        assertEquals("villagers",FobRules.plantProblem(true,0,true,true,false,FobRules.MIN_VILLAGERS-1));
        assertNull(FobRules.plantProblem(true,0,true,true,false,FobRules.MIN_VILLAGERS));
    }
    /** The empty slot's line stays clear of "ACTIVE FOBs". */
    @Test void theEnchantingScreenHasRoomForEveryLine(){
        int gearTextTop=TacticalEnchanting.EnchantMenu.GEAR_Y-4,gearTextBottom=gearTextTop+EnchantClient.EnchantScreen.GEAR_TEXT_LINES*10;
        assertTrue(gearTextBottom<EnchantClient.EnchantScreen.FOBS_RULE_Y,"the gear's three lines end above the FOB rule");
        assertTrue(TacticalEnchanting.EnchantMenu.GEAR_Y+18<EnchantClient.EnchantScreen.FOBS_RULE_Y,"the slot too");
        assertTrue(EnchantClient.EnchantScreen.FOBS_Y+12+9<EnchantClient.EnchantScreen.MK_RULE_Y,"the two FOB lines end above the table's rule");
        assertTrue(EnchantClient.EnchantScreen.MK_Y+9<EnchantClient.EnchantScreen.UPGRADE_Y,"the Mk line, then the upgrade button");
        assertTrue(EnchantClient.EnchantScreen.UPGRADE_Y+18<EnchantClient.EnchantScreen.ENERGY_Y,"the button, then what you carry");
        assertTrue(EnchantClient.EnchantScreen.ENERGY_Y+9<TacticalEnchanting.EnchantMenu.INV_Y-1,"then the inventory");
        assertTrue(TacticalEnchanting.EnchantMenu.INV_Y+58+18<=EnchantClient.EnchantScreen.H-4,"the hotbar inside the panel");
        assertTrue(TacticalEnchanting.EnchantMenu.INV_X+9*18<=EnchantClient.EnchantScreen.SIDE_X+EnchantClient.EnchantScreen.SIDE_W,"the inventory inside the left column");
        assertTrue(EnchantClient.EnchantScreen.SIDE_X+EnchantClient.EnchantScreen.SIDE_W<EnchantClient.EnchantScreen.LIST_X,"the columns do not touch");
        assertTrue(EnchantClient.EnchantScreen.W>384&&EnchantClient.EnchantScreen.H>236,"bigger than 0.0.29's 384 x 236");
        assertTrue(EnchantClient.EnchantScreen.W<=480&&EnchantClient.EnchantScreen.H<=270,"it still fits a 1920 x 1080 window at GUI scale 4");
    }
    @Test void theWorkshopSellsBothGatesAndScrolls() throws Exception{
        int delivery=WorkshopFabrication.ITEMS.indexOf("supply_delivery_gate"),receiving=WorkshopFabrication.ITEMS.indexOf("supply_receiving_gate");
        assertTrue(delivery>=0&&receiving==delivery+1,"the two gates side by side");
        String lang=lang();
        assertTrue(lang.contains("\"gui.eternal_defense.fabrication.name.supply_delivery_gate\"")&&lang.contains("\"gui.eternal_defense.fabrication.desc.supply_delivery_gate\""));
        String client=SosTest.source("BeaconClient.java");
        assertTrue(client.contains("public boolean mouseScrolled(double mx,double my,double amount){\n            if(tab==2&&fabMaxScroll()>0)"),"the mouse wheel scrolls the Fabrication tab");
        assertTrue(client.contains("g.enableScissor(x-2,viewTop,x+lw+2,viewBottom)"),"rows outside the window are clipped");
        assertTrue(client.contains("fabShown(cell)"),"a Make button shows only for a whole row");
    }
    @Test void aLoopsWayBackThroughTheDeliveryGateIsQuiet(){
        assertEquals(200,RouteRules.WRONG_GATE_QUIET,"10 s after loading, the hint comes back");
        assertTrue(SosTest.source("Routes.java").contains("case WRONG_GATE->{if(now-run.legStart>RouteRules.WRONG_GATE_QUIET)say(p,\"route.pass.wrong_gate\");}"));
    }
}
