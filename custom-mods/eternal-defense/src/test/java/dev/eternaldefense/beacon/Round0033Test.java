package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The Supply Catapult and specialty seller, the parts that need no world. */
final class Round0033Test {
    static String lang() throws Exception{return Files.readString(Path.of(EnchantTest.ASSETS+"lang/en_us.json"));}

    @Test void aLotIsWorthOneSTierItem(){
        assertEquals(RouteRules.TIER_VALUE[5],CatapultRules.LOT_VALUE);
        assertEquals(1,CatapultRules.lotSize(RouteRules.tiered("minecraft:ghast_tear",5)));
        assertEquals(2,CatapultRules.lotSize(RouteRules.tiered("minecraft:ender_pearl",4)));
        assertEquals(16,CatapultRules.lotSize(RouteRules.tiered("minecraft:bone",1)));
        assertEquals(32,CatapultRules.lotSize(RouteRules.plain("minecraft:dried_kelp",.5)));
        // Every specialty item: a lot is worth about one S item, never nothing
        for(var spec:RouteRules.Specialty.values())for(var l:spec.options()){int n=CatapultRules.lotSize(l);assertTrue(n>=1,l.id());assertTrue(n*l.value()>=CatapultRules.LOT_VALUE*.66&&n*l.value()<=CatapultRules.LOT_VALUE*1.5,l.id()+" "+n);}
    }
    @Test void pricesLiveInEconomy(){
        assertEquals(2,Economy.SELLER_LOT);
        assertEquals(CatapultRules.LOTS[1]*Economy.sellerLotPrice(),CatapultRules.price(CatapultRules.LOTS[1]));
        assertArrayEquals(new int[]{1,4},CatapultRules.LOTS);
        String economy=SosTest.source("Economy.java");
        assertTrue(economy.contains("case \"supply_catapult\"->List.of(new WeaponPlatform.Cost(Ingredient.of(net.minecraft.tags.ItemTags.LOGS),CATAPULT_LOGS)"),"the catapult's price");
        assertTrue(WorkshopFabrication.ITEMS.contains("supply_catapult"));
        assertTrue(SosTest.source("CreativeCatalog.java").contains("\"supply_catapult\""));
    }
    @Test void theSellerOpensAtTheTopResearchLevel(){
        assertEquals(5,CatapultRules.SELLER_LEVEL);
        assertNull(CatapultRules.closed(true,5,true,true));
        assertEquals("research",CatapultRules.closed(true,4,true,true));
        assertEquals("catapult",CatapultRules.closed(true,5,false,true));
        assertEquals("down",CatapultRules.closed(false,5,true,true));
        assertEquals("base",CatapultRules.closed(true,5,true,false));
    }
    @Test void theThrowFollowsTheOwnersReadme(){
        assertEquals(0,CatapultRules.armAngle(0));
        assertEquals(-88,CatapultRules.armAngle(CatapultRules.THROW_TICKS),1e-4);
        assertEquals(-88,CatapultRules.armAngle(CatapultRules.THROW_TICKS+CatapultRules.HOLD_TICKS),1e-4);
        assertEquals(0,CatapultRules.armAngle(CatapultRules.CYCLE),1e-4);
        assertTrue(CatapultRules.armAngle(CatapultRules.RELEASE_TICK)<=-45&&CatapultRules.armAngle(CatapultRules.RELEASE_TICK-1)>-45,"the box leaves as the arm passes -45 degrees");
        float last=0;for(float t=0;t<=CatapultRules.THROW_TICKS;t+=.25f){float a=CatapultRules.armAngle(t);assertTrue(a<=last+1e-4);last=a;}
        assertEquals(50,CatapultRules.CYCLE);
        // Facing north the box leaves the cup up and to the north, from the cup position
        double[] r=CatapultRules.release(0,1);
        assertEquals(.5,r[0],1e-9);assertEquals(26.9/16,r[1],1e-9);assertEquals(.5+7.6/16,r[2],1e-9);
        assertEquals(0,r[3],1e-9);assertEquals(.707,r[4],1e-9);assertEquals(-.707,r[5],1e-9);
        // Turned to the east (the cannon's yaw -90) it flies east
        double[] e=CatapultRules.release(-90,1);assertEquals(.707,e[3],1e-9);assertEquals(0,e[5],1e-9);
        assertEquals(-90,CatapultRules.yawTo(0,0,10,0),1e-4);assertEquals(0,CatapultRules.yawTo(0,0,0,-10),1e-4);
        // The flight home is the Supply Flare's 12 seconds whatever the distance
        assertEquals(240,CatapultRules.FLIGHT_TICKS);
    }
    @Test void sellerButtonsHaveTheirOwnIds(){
        assertTrue(15*16+15<SupplyCatapult.SELL,"the table's own buttons (action x 16 + argument) stay below");
        for(int fob=0;fob<FobRules.MAX_FOBS;fob++)for(int item=0;item<RouteRules.TRADER_LOOT.size();item++)for(int kind=0;kind<CatapultRules.LOTS.length;kind++)
            assertArrayEquals(new int[]{fob,item,kind},SupplyCatapult.decode(SupplyCatapult.sellButton(fob,item,kind)));
        assertTrue(SosTest.source("Hunts.java").contains("id>=SupplyCatapult.SELL?SupplyCatapult.buy(p,l,id)"),"the table hands them to the seller");
        assertTrue(SosTest.source("TacticalClient.java").contains("click(SupplyCatapult.sellButton(index,sellerItem,kind))"),"by the mod's packet, as every table button");
    }
    @Test void theCatapultAndItsBoxesAreSaved(){
        var d=new FobData();var f=d.add(new BlockPos(0,64,0),1L,0L);
        f.catapult=new BlockPos(7,64,-3);var ship=new CompoundTag();ship.putString("stage","flying");ship.putLong("due",1234);ship.putString("item","minecraft:ghast_tear");f.shipments.add(ship);
        var back=FobData.load(d.save(new CompoundTag())).byId(f.id);
        assertEquals(new BlockPos(7,64,-3),back.catapult);assertEquals(1,back.shipments.size());
        assertEquals("flying",back.shipments.get(0).getString("stage"));assertEquals(1234,back.shipments.get(0).getLong("due"));
        assertNotNull(FobData.load(d.save(new CompoundTag())).byCatapult(new BlockPos(7,64,-3)),"found by its catapult");
        var none=new FobData();var g=none.add(new BlockPos(0,64,0),1L,0L);assertNull(FobData.load(none.save(new CompoundTag())).byId(g.id).catapult);
    }
    @Test void theOwnersModelIsInstalled() throws Exception{
        var art=Path.of("../../docs/art/Eternal-Defense-Supply-Catapult");
        for(String part:List.of("supply_catapult","supply_catapult_frame","supply_catapult_arm","supply_catapult_payload","supply_catapult_assembled")){
            assertEquals(Files.readString(art.resolve("minecraft-models/block/"+part+".json")),Files.readString(Path.of(EnchantTest.ASSETS+"models/block/"+part+".json")),part+" as the owner made it");
            assertTrue(Files.exists(Path.of(EnchantTest.ASSETS+"textures/block/"+part+".png")),part+" texture");
        }
        assertTrue(Files.exists(Path.of(EnchantTest.ASSETS+"blockstates/supply_catapult.json")));
        assertTrue(Files.readString(Path.of(EnchantTest.ASSETS+"models/item/supply_catapult.json")).contains("supply_catapult_assembled"));
        assertTrue(Files.exists(Path.of("src/main/resources/data/eternal_defense/loot_tables/blocks/supply_catapult.json")));
        assertTrue(Files.readString(Path.of("src/main/resources/data/minecraft/tags/blocks/mineable/axe.json")).contains("eternal_defense:supply_catapult"));
        assertTrue(SosTest.source("DefenseStructures.java").contains("state.is(EternalDefense.SUPPLY_CATAPULT.get())||"),"a 3 x 3 x 2 structure like the cannon");
    }
    @Test void everyLineExists() throws Exception{
        String lang=lang();
        for(String key:List.of("block.eternal_defense.supply_catapult","entity.eternal_defense.thrown_supply","tooltip.eternal_defense.catapult",
                "gui.eternal_defense.fabrication.name.supply_catapult","gui.eternal_defense.fabrication.desc.supply_catapult","gui.eternal_defense.catapult.linked",
                "gui.eternal_defense.catapult.thrown","gui.eternal_defense.catapult.landed","gui.eternal_defense.tactical.seller.open","gui.eternal_defense.tactical.seller.bought",
                "gui.eternal_defense.tactical.seller.short","gui.eternal_defense.tactical.seller.busy","gui.eternal_defense.tactical.seller.gone"))
            assertTrue(lang.contains("\""+key+"\""),key);
        for(String why:List.of("base","down","research","catapult"))for(String kind:List.of("tactical.seller.closed.","tactical.seller.refused.","catapult.status.closed."))
            assertTrue(lang.contains("\"gui.eternal_defense."+kind+why+"\""),kind+why);
        for(String why:List.of("overworld","not_fob","has_catapult","no_room"))assertTrue(lang.contains("\"gui.eternal_defense.catapult.refused."+why+"\""),why);
        assertTrue(lang.contains("# Supply Catapult"),"a Field Guide card");
    }
    /** The block event that starts the throw (and the cannon's recoil) reaches the block entity and so the clients */
    @Test void blockEventsReachTheBlockEntity(){
        for(String f:List.of("SupplyCatapult.java","SupportCannon.java"))
            assertTrue(SosTest.source(f).contains("public boolean triggerEvent(BlockState s,Level l,BlockPos pos,int id,int param){super.triggerEvent(s,l,pos,id,param);var be=l.getBlockEntity(pos);return be!=null&&be.triggerEvent(id,param);}"),f);
    }
    
}
