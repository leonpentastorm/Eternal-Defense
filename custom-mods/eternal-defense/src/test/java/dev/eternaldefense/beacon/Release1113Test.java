package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.13: what is put on while another screen is open reaches the player's own inventory. */
final class Release1113Test {
    @Test void theWholeInventoryIsSentWhenAnotherScreenIsOpen() throws Exception{
        String pay=Release114Test.source("InventoryPayment.java");
        assertTrue(pay.contains("static void sync(net.minecraft.world.entity.player.Player p){")&&pay.contains("if(p.containerMenu!=p.inventoryMenu)p.inventoryMenu.sendAllDataToRemote();"),"armor and off-hand slots are sent as a whole inventory");
        assertTrue(Release114Test.source("LoadoutCapsule.java").contains("InventoryPayment.sync(sp);"),"the capsule's Equip");
        assertTrue(Release114Test.source("LoadoutPrint.java").contains("InventoryPayment.sync(p);"),"the station's print");
        assertTrue(Release114Test.source("Fobs.java").contains("InventoryPayment.sync(p);"),"FOB research paid from the off hand");
        assertTrue(pay.contains("sync(p);if(p.containerMenu!=p.inventoryMenu)p.containerMenu.broadcastChanges();return true;"),"every table and shop payment");
        for(String f:new String[]{"LoadoutCapsule.java","LoadoutPrint.java","Fobs.java"})assertFalse(Release114Test.source(f).contains("p.inventoryMenu.broadcastChanges();"),f+" sends single slots no more");
    }
}
