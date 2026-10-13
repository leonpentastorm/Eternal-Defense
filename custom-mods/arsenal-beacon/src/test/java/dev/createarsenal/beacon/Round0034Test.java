package dev.createarsenal.beacon;

import dev.createarsenal.beacon.RouteRules.Slot;
import dev.createarsenal.beacon.RouteRules.Specialty;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 0.0.34 list: the live catapult, the supply-drop landing, the merged specialty, the catapult's status, the radio, the specialty cap. */
final class Round0034Test {
    static String lang() throws Exception{return Files.readString(Path.of(EnchantTest.ASSETS+"lang/en_us.json"));}

    // ---- the catapult turns and throws with its FOB, seen or not ------------------------------------------------------------------------------------
    @Test void theCatapultTurnsThrowsAndTurnsBack(){
        var m=new CatapultRules.Motion(0);float goal=90;
        assertEquals(CatapultRules.Event.LOADED,m.step(true,goal),"a box loaded");
        int t=1,thrownAt=-1,releasedAt=-1;
        for(;t<2000&&releasedAt<0;t++){var e=m.step(true,goal);if(e==CatapultRules.Event.THROW)thrownAt=t;if(e==CatapultRules.Event.RELEASE)releasedAt=t;}
        assertTrue(thrownAt>0,"it threw");assertEquals(thrownAt+CatapultRules.RELEASE_TICK,releasedAt,"the box leaves as the arm passes -45 degrees");
        assertEquals(goal,m.yaw,1,"it faces the beacon when it throws");
        assertTrue(m.throwing());
        // nothing left: the arm winds down, then the frame turns back to its rest heading
        for(int i=0;i<2000;i++)assertNotEquals(CatapultRules.Event.THROW,m.step(false,goal));
        assertFalse(m.throwing()||m.loaded);assertEquals(0,m.yaw,.6,"back at rest");
        // the turn takes time: from rest a quarter turn and the settle are well over half a second
        assertTrue(thrownAt>CatapultRules.SETTLE_TICKS+10,"turned before it threw: "+thrownAt);
    }
    @Test void itRestsFacingAwayFromWhoPlacedIt(){
        assertEquals(0,CatapultRules.restFor(180),1e-4,"placed looking north: it faces north");
        assertEquals(-90,CatapultRules.restFor(-90),1e-4,"looking east: east");
        assertEquals(90,CatapultRules.restFor(90),1e-4,"looking west: west");
        assertEquals(180,Math.abs(CatapultRules.restFor(0)),1e-4,"looking south: south");
        assertEquals(0,CatapultRules.restFor(170),1e-4,"square to the compass");
    }
    @Test void theMotionIsSavedWithTheFob(){
        var d=new FobData();var f=d.add(new BlockPos(0,64,0),1L,0L);f.catapult=new BlockPos(3,64,3);f.motion=new CatapultRules.Motion(-90);f.motion.yaw=45;
        var back=FobData.load(d.save(new CompoundTag())).byId(f.id).motion;
        assertEquals(-90,back.rest,1e-4);assertEquals(45,back.yaw,1e-4);assertFalse(back.throwing());
        String source=SosTest.source("SupplyCatapult.java");
        assertTrue(source.contains("static void tick(ServerLevel l,int clock)")&&SosTest.source("ArsenalBeacon.java").contains("SupplyCatapult.tick(l,clock)"),"run every tick, loaded or not");
        assertFalse(source.contains("UNSEEN_THROW_TICKS"),"no separate unseen throw any more");
    }

    // ---- the box comes home as a supply drop ---------------------------------------------------------------------------------------------------------
    @Test void theBoxLandsLikeASupplyDrop(){
        assertEquals(SupportRules.supplyTicks(true),CatapultRules.FLIGHT_TICKS);assertEquals(12*20,CatapultRules.FLIGHT_TICKS,"the Supply Flare's 12 seconds");
        assertEquals(1000-(int)Math.ceil(40/SupportRules.PARCEL_FALL_SPEED),CatapultRules.spawnAt(1000,40,true),"it appears 40 blocks up and parachutes down on time");
        assertEquals(1000,CatapultRules.spawnAt(1000,3,false),"no open sky: it appears at the spot when the count ends");
        String source=SosTest.source("SupplyCatapult.java");
        assertTrue(source.contains("\"gui.arsenal_beacon.support.countdown\""),"the Supply Flare's chat countdown");
        assertTrue(source.contains("SupportCalls.deliver(home,null"),"the Supply Flare's parcel");
    }

    // ---- the live view in the seller -------------------------------------------------------------------------------------------------------------------
    @Test void theSellerShowsTheCatapultLive() throws Exception{
        String net=SosTest.source("BeaconNetwork.java");
        assertEquals("41",BeaconNetwork.PROTOCOL,"protocol 41 since 0.0.41");
        assertTrue(net.contains("registerMessage(23,SupplyCatapult.View.class"),"the live view packet");
        String table=SosTest.source("TacticalClient.java");
        assertTrue(table.contains("CatapultView.draw(g,f.getLong(\"id\")"),"drawn in the seller");
        assertTrue(table.contains("sellCols(){return Math.max(1,(fobW()-viewW()-8)/22);}"),"the grid leaves it room");
        String lang=lang();
        for(String k:List.of("none","ready","turning","throwing","flying"))assertTrue(lang.contains("\"gui.arsenal_beacon.tactical.seller.view."+k+"\""),k);
    }

    // ---- the radio --------------------------------------------------------------------------------------------------------------------------------------
    @Test void theRadioCracklesAndAnswers() throws Exception{
        var icon=javax.imageio.ImageIO.read(Path.of(EnchantTest.ASSETS+"textures/gui/radio.png").toFile());
        assertEquals(16,icon.getWidth());assertEquals(16,icon.getHeight());
        String sounds=Files.readString(Path.of(EnchantTest.ASSETS+"sounds.json"));
        assertTrue(sounds.contains("\"seller.radio_static\"")&&Files.exists(Path.of(EnchantTest.ASSETS+"sounds/sfx/radio_static.ogg")));
        for(int n=1;n<=SupplyCatapult.ORDER_LINES;n++){assertTrue(sounds.contains("\"seller.order_chatter_"+n+"\""));assertTrue(Files.exists(Path.of(EnchantTest.ASSETS+"sounds/sfx/order_chatter_"+n+".ogg")),"line "+n);}
        assertEquals(4,SupplyCatapult.ORDER_LINES);
        String table=SosTest.source("TacticalClient.java");
        assertTrue(table.contains(".icon(RADIO)")&&table.contains("contact();"),"the radio on Contact Specialty Seller, its static when pressed");
        assertTrue(SosTest.source("SupplyCatapult.java").contains("radio(p);\n        return \"tactical.seller.bought\";"),"an order line on every purchase");
        String lang=lang();assertTrue(lang.contains("\"subtitles.arsenal_beacon.seller.radio_static\"")&&lang.contains("\"subtitles.arsenal_beacon.seller.order_chatter\""));
    }

    /** The owner's war horn: a FOB planted, a FOB's raid starting (the activation raid, an SOS raid), once for planting and its raid together. */
    @Test void theWarHornSounds() throws Exception{
        assertTrue(Files.exists(Path.of(EnchantTest.ASSETS+"sounds/sfx/war_horn.ogg")));
        assertTrue(Files.readString(Path.of(EnchantTest.ASSETS+"sounds.json")).contains("\"fob.war_horn\""));
        assertTrue(lang().contains("\"subtitles.arsenal_beacon.fob.war_horn\""));
        String fobs=SosTest.source("Fobs.java");
        assertTrue(fobs.contains("announce(l,Component.translatable(\"gui.arsenal_beacon.fob.planted\",p.getDisplayName(),f.name,villagers));\n        horn(l,f);"),"on planting");
        assertTrue(fobs.contains("announce(l,Component.translatable(\"gui.arsenal_beacon.fob.raid_started\",f.name,f.raidVillagers,(f.raidVillagers+1)/2));\n        horn(l,f);"),"when the raid starts");
        assertTrue(SosTest.source("FobSos.java").contains("Fobs.horn(l,f);"),"when an SOS raid starts");
        assertEquals(240,Fobs.HORN_TICKS,"not twice while one sounds");
    }

    // ---- fishing and animal husbandry are one specialty ----------------------------------------------------------------------------------------------
    @Test void fishingAndHusbandryAreOne() throws Exception{
        assertEquals(List.of(Specialty.FISHING_HUSBANDRY,Specialty.POTIONS,Specialty.TRADER,Specialty.MONSTERS),List.of(Specialty.values()));
        var all=Specialty.FISHING_HUSBANDRY.options();
        assertEquals(RouteRules.FISHING_LOOT.size()+RouteRules.HUSBANDRY_LOOT.size(),all.size());
        assertTrue(all.containsAll(RouteRules.FISHING_LOOT)&&all.containsAll(RouteRules.HUSBANDRY_LOOT));
        assertEquals(List.of("spec_fishing","spec_husbandry"),Specialty.FISHING_HUSBANDRY.icons(),"both icons");
        for(var s:Specialty.values())for(String i:s.icons())assertTrue(Files.exists(Path.of(EnchantTest.ASSETS+"textures/gui/tribute/"+i+".png")),i);
        assertTrue(lang().contains("\"gui.arsenal_beacon.gate.specialty.fishing_husbandry\": \"Fishing & Animal Husbandry\""),"both names");
        assertTrue(all.size()<=Routes.CHOICES);
    }
    @Test void oldSavesFindTheirSpecialty(){
        for(int old=0;old<5;old++){
            var d=new FobData();var f=d.add(new BlockPos(0,64,0),1L,0L);f.special=2;
            var tag=d.save(new CompoundTag());var t=((CompoundTag)tag.getList("fobs",10).get(0)).getCompound("route").getCompound("tribute");
            t.remove("spec");t.putInt("specialty",old);
            var back=FobData.load(tag).byId(f.id);
            assertEquals(Specialty.legacy(old),back.specialty);
            if(old==1)assertEquals("minecraft:chicken",RouteRules.loot(Slot.SPECIALTY,back.specialty,back.special).id(),"a husbandry choice still points at the same item");
            else assertEquals(2,back.special);
        }
        assertEquals(Specialty.FISHING_HUSBANDRY,Specialty.legacy(0));assertEquals(Specialty.FISHING_HUSBANDRY,Specialty.legacy(1));
        assertEquals(Specialty.POTIONS,Specialty.legacy(2));assertEquals(Specialty.TRADER,Specialty.legacy(3));assertEquals(Specialty.MONSTERS,Specialty.legacy(4));
        // saved by name from now on
        var d=new FobData();var f=d.add(new BlockPos(0,64,0),1L,0L);f.specialty=Specialty.MONSTERS;
        assertEquals("monsters",((CompoundTag)d.save(new CompoundTag()).getList("fobs",10).get(0)).getCompound("route").getCompound("tribute").getString("spec"));
        assertEquals(Specialty.MONSTERS,FobData.load(d.save(new CompoundTag())).byId(f.id).specialty);
    }

    // ---- the specialty makes at most 4 S-tier items an hour ------------------------------------------------------------------------------------------
    static double specialtyPerHour(boolean[] on,double distance,int villagers,int level){
        var y=new RouteRules.Yield(on,distance,villagers,0,level);
        return RouteRules.budget(Slot.SPECIALTY,y)/RouteRules.tripHours(distance,0);
    }
    @Test void aSpecialtyMakesAtMostFourSItemsAnHour(){
        double s=RouteRules.TIER_VALUE[5];boolean[] alone={false,false,false,true},all={true,true,true,true};
        assertEquals(4,RouteRules.SPECIALTY_MAX_PER_HOUR/s,1e-9);
        // the owner's playtest: research 5, every other slot off. Before 0.0.34 the slowest FOB made 9 an hour, a bigger one 12 and more
        assertEquals(4,specialtyPerHour(alone,128,3,5)/s,1e-9,"the slowest FOB");
        assertEquals(4,specialtyPerHour(alone,400,8,5)/s,1e-9,"a middling FOB");
        assertEquals(4,specialtyPerHour(alone,5000,20,5)/s,1e-9,"the biggest FOB");
        // the 30-minute floor holds: the slowest FOB, every slot on, research 4
        assertEquals(2,specialtyPerHour(all,128,3,4)/s,1e-9);
        // a fast rail delivers more often than the hours a trip carries: still 4 an hour, counted between real deliveries
        long fast=RouteRules.refPeriod(5000)/3;var quick=new RouteRules.Yield(alone,5000,20,fast,5);
        assertEquals(4,RouteRules.budget(Slot.SPECIALTY,quick)/(fast/(double)RouteRules.HOUR)/s,1e-9,"a fast rail");
        assertEquals(4,RouteRules.budget(Slot.SPECIALTY,quick)*RouteRules.tripsPerHour(fast)/s,1e-6,"as the gate screen counts it");
        // the other slots are untouched
        var y=new RouteRules.Yield(new boolean[]{false,false,true,false},5000,20,0,5);
        assertTrue(RouteRules.budget(Slot.FARM,y)/RouteRules.tripHours(5000,0)>RouteRules.SPECIALTY_MAX_PER_HOUR,"the farm alone is not capped");
    }

    // ---- the FOB screen ---------------------------------------------------------------------------------------------------------------------------------
    @Test void theFobScreenSaysTheCatapultsStateAndWhatLevelFiveOpens() throws Exception{
        assertTrue(SosTest.source("Fobs.java").contains("n.putString(\"catapult\",java.util.Objects.requireNonNullElse(SupplyCatapult.closed(l,f),\"online\"));"));
        String lang=lang();
        assertTrue(lang.contains("\"gui.arsenal_beacon.fob.catapult.online\": \"Supply Catapult: ONLINE\""));
        for(String why:List.of("catapult","research","down","base"))assertTrue(lang.contains("\"gui.arsenal_beacon.fob.catapult.offline."+why+"\""),why);
        assertTrue(lang.contains("\"gui.arsenal_beacon.fob.research.seller\": \"Supply Catapult + Specialty Shop\""));
        assertTrue(lang.contains("\"gui.arsenal_beacon.fob.research.levelup.seller\""));
        assertTrue(SosTest.source("FobClient.java").contains("boolean seller=l==CatapultRules.SELLER_LEVEL;"),"level 5 of the research tab");
        assertEquals(RouteRules.MAX_LEVEL,CatapultRules.SELLER_LEVEL);
    }
}
