package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's list of 0.0.21: armed raiders, night-only raids, raids that ignore cover, the hunt arrival, the maps, the colours, the Starshell. */
final class RaidRoundTest {
    static final String SRC="src/main/java/dev/createarsenal/beacon/";
    static String source(String file) throws Exception{return Files.readString(Path.of(SRC+file));}

    // ---- raiders ----------------------------------------------------------------------------------------------------------------------
    @Test void everyRaidSkeletonAndPillagerIsArmedBeforeItJoins() throws Exception{
        String beacon=source("ArsenalBeacon.java");
        int raider=beacon.indexOf("static Mob raider(");assertTrue(raider>0,"one place makes a raid mob");
        int arm=beacon.indexOf("RaidBalance.arm(mob)",raider),join=beacon.indexOf("addFreshEntity(mob)",raider);
        assertTrue(arm>0&&arm<join,"armed before it joins the world (the beacon goals are chosen when it joins)");
        assertTrue(source("RaidBalance.java").contains("mob.setCanPickUpLoot(false);arm(mob);"),"the tier 2+ balance arms too");
        assertTrue(beacon.contains("RaidBalance.arm(mob);if(mob instanceof net.minecraft.world.entity.monster.Zombie"),"so does the early balance");
        assertTrue(source("HuntWarband.java").contains("RaidBalance.arm(mob);"),"hunt warbands share it");
        String combat=source("BeaconCombat.java");
        assertTrue(combat.contains("else if(mob instanceof AbstractSkeleton||mob instanceof Pillager)mob.goalSelector.addGoal(0,new MeleeBeacon(mob));"),
            "an unarmed skeleton or pillager still walks in and hits the beacon");
    }

    // ---- when raids come --------------------------------------------------------------------------------------------------------------
    @Test void raidsOnlyStartAtNight(){
        assertFalse(Rules.raidHour(6000),"noon");assertFalse(Rules.raidHour(12999));
        assertTrue(Rules.raidHour(13000),"dusk");assertTrue(Rules.raidHour(20999));assertFalse(Rules.raidHour(21000),"too late to start one");
        assertTrue(Rules.raidHour(24000L*7+15000),"any day");assertFalse(Rules.raidHour(-1000));
        assertEquals(7000,Rules.untilRaidHour(6000));assertEquals(0,Rules.untilRaidHour(13000));assertEquals(15000,Rules.untilRaidHour(22000));
        assertEquals(1,Rules.untilRaidHour(12999));
    }
    @Test void startRaidByDayBooksItForNightfall() throws Exception{
        String selection=source("RaidSelection.java");
        assertTrue(selection.indexOf("if(!Rules.raidHour(l.getDayTime())){d.raidRequested=true;")<selection.indexOf("ArsenalBeacon.begin(l,d)"),"by day it only books the raid");
        String beacon=source("ArsenalBeacon.java");
        assertTrue(beacon.contains("(d.preparationTicks>=Rules.intervalDays(d.rewardTier)*24000L||d.raidRequested)&&Rules.raidHour(l.getDayTime())"),"a due or booked raid waits for night");
        assertTrue(beacon.contains("if(!d.raidRequested&&RaidRespite.waitTick(d))break;"),"a booked raid ends bought break time, as starting one did");
        assertTrue(beacon.contains("d.raidRequested=false;d.introRaid=!d.introCompleted;"),"starting clears the booking");
        var lang=RaidFixesTest.lang();
        assertEquals("Raid at nightfall",lang.get("gui.arsenal_beacon.raid.start.booked"));
        assertTrue(lang.get("gui.arsenal_beacon.guide.raids.detail").contains("10, 9, 8, 7, 6, 5, 4, then 3 days"),"the guide quotes the intervals");
        int[] days={10,9,8,7,6,5,4,3};for(int t=0;t<days.length;t++)assertEquals(days[t],Rules.intervalDays(t));
    }

    // ---- what a raid draws in ---------------------------------------------------------------------------------------------------------
    @Test void duringARaidCoverNoLongerHidesTheBeacon() throws Exception{
        int zone=18;
        assertTrue(BeaconCombat.attracted(true,false,500,zone,false),"a raider always comes");
        // between raids: within 24 blocks and seen
        assertTrue(BeaconCombat.attracted(false,false,20,zone,true));assertFalse(BeaconCombat.attracted(false,false,20,zone,false),"cover hides it");
        assertFalse(BeaconCombat.attracted(false,false,30,zone,true),"too far");
        // during a raid: within the zone plus 48, seen or not
        assertTrue(BeaconCombat.attracted(false,true,20,zone,false),"cover no longer hides it");
        assertTrue(BeaconCombat.attracted(false,true,zone+BeaconCombat.RAID_ATTRACT_BEYOND,zone,false));
        assertFalse(BeaconCombat.attracted(false,true,zone+BeaconCombat.RAID_ATTRACT_BEYOND+1,zone,true));
        assertTrue(BeaconCombat.RAID_ATTRACT_BEYOND>BeaconCombat.AMBIENT_RANGE,"natural mobs spawn at least 24 blocks from players: the reach must go past that");
        assertTrue(RaidFixesTest.lang().get("gui.arsenal_beacon.guide.start.body").contains("within "+BeaconCombat.RAID_ATTRACT_BEYOND+" blocks of your zone"));
    }
    @Test void theAlarmClipIsOneDoubleBeep() throws Exception{
        assertTrue(Files.readString(Path.of("../../tools/audio/import_sfx.py")).contains("(0.0, 0.495)"),"cut just before the third beep at 0.500 s");
        assertEquals(.6+.5,BeaconAlerts.ALARM_PERIOD_TICKS/20.0,1e-9,"0.6 s clip, 0.5 s pause");
    }

    // ---- hunts: arrival ---------------------------------------------------------------------------------------------------------------
    @Test void theAnchorIsLookedForFromTheXOutwards(){
        var order=TacticalRules.anchorOrder(TacticalRules.ANCHOR_SEARCH);
        assertArrayEquals(new int[]{0,0},order.get(0),"the X itself first");
        int r=TacticalRules.ANCHOR_SEARCH,last=-1,count=0;
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)if(dx*dx+dz*dz<=r*r)count++;
        assertEquals(count,order.size(),"every column within 16 blocks, once");
        for(var c:order){int d2=c[0]*c[0]+c[1]*c[1];assertTrue(d2>=last,"nearest first");last=d2;assertTrue(d2<=r*r);}
        assertEquals(new HashSet<>(order.stream().map(Arrays::toString).toList()).size(),order.size(),"no column twice");
    }
    @Test void theWarbandComesOutJustOutsideTheSmallZone(){
        var rnd=new Random(5);
        for(int i=0;i<5000;i++){
            var o=TacticalRules.ringOffset(rnd.nextDouble()*Math.PI*2,rnd.nextDouble());double d=Math.hypot(o[0],o[1]);
            assertTrue(d>TacticalRules.MINI_ZONE&&d<=TacticalRules.RING_TO+1,"outside the zone, close to it: "+d);
        }
        assertEquals(TacticalRules.ANCHOR_SEARCH+TacticalRules.RING_TO,TacticalRules.SPAWN_RADIUS,"so a mob appears within 28 blocks of the X");
        assertEquals(1,HuntWarband.SPAWNS_PER_TICK,"one mob a tick: no burst of work in one tick");
        assertEquals(5*60*20,TacticalRules.RETURN_GATE_TICKS);
    }
    @Test void anArrivalNeedsOneBlockAndNoGroundOpensAReturnGate() throws Exception{
        String band=source("HuntWarband.java");
        assertTrue(band.contains("return anchor;   // one block is all a warband needs"),"no ring ground: the warband comes out at the anchor");
        assertTrue(band.contains("if(found.complete()&&m.state==HuntBoard.State.ACTIVE){noGround(l,d,h,m);return;}"),"no ground only once all of it was looked at");
        assertTrue(band.contains("Hunts.returnGate(l,d,p,m.offer.codename)")&&band.contains("Hunts.unreachable(l,d,h);"),"return gates, then the offer is replaced");
        assertFalse(band.contains("openGround"),"no more need for a 5 x 5 patch of open natural ground");
        assertTrue(band.contains("rush(mob,anchor);RaidSpawns.rush(mob);live.add(mob.getUUID());"),"it rushes in, with the speed burst, and gets the red mark");
        assertTrue(source("RaidTypes.java").contains("for(int id:HuntWarband.markIds(l))raiders.add(id);"),"hunt mobs carry the raid's red mark");
    }

    // ---- hunts: maps, HUD, table ------------------------------------------------------------------------------------------------------
    @Test void mapsOfOperationsThatAreOverBurn(){
        var b=new HuntBoard();
        b.offers.add(new HuntBoard.Offer(7,TacticalRules.Kind.PATROL,"OP A",0,0,300,0));b.offers.add(new HuntBoard.Offer(8,TacticalRules.Kind.WARLORD,"OP B",0,0,300,90));
        assertFalse(Hunts.mapStale(b,0),"not a mission map");
        assertFalse(Hunts.mapStale(b,7),"on the board: it can still be taken");
        assertTrue(Hunts.mapStale(b,99),"gone from the board");
        assertNull(b.accept(8,null));assertFalse(Hunts.mapStale(b,8),"running");
        b.engage(3);for(int i=0;i<3;i++)b.spawnedOne();for(int i=0;i<3;i++)b.killed(8,b.mission.serial);
        assertTrue(b.clear(),"the last kill clears it (as Hunts.complete does)");
        assertTrue(Hunts.mapStale(b,8),"cleared: its maps burn");
    }
    @Test void theMissionLineNeedsTheMapOrTheShovelInHand() throws Exception{
        String client=source("BeaconClient.java");
        assertTrue(client.contains("TacticalClient.missionActive()&&mc.level!=null&&mc.level.dimension()==Level.OVERWORLD&&TacticalClient.holdingMissionItem()"));
        String tactical=source("TacticalClient.java");
        assertTrue(tactical.contains("s.is(ArsenalBeacon.CONTROLLER.get())||Hunts.mapMission(s)==mission.id()&&mission.id()!=0"),"this mission's map, or the shovel");
    }
    @Test void theTableShowsRarityAndTheChosenXInRed(){
        assertEquals(Ui.RARE_BLUE,TacticalClient.kindColor("patrol"));
        assertEquals(Ui.EPIC_PURPLE,TacticalClient.kindColor("garrison"));
        assertEquals(Ui.LEGEND_GOLD,TacticalClient.kindColor("warlord"));
        assertNotEquals(Ui.RED,Ui.LEGEND_GOLD,"red is only the chosen X");
    }
    @Test void offersStayOffWater() throws Exception{
        String hunts=source("Hunts.java");
        assertTrue(hunts.contains("l.getWorldBorder().isWithinBounds(x,z)&&dryLand(l,x,z)"),"every new objective is on land");
        assertTrue(hunts.contains("BiomeTags.IS_OCEAN")&&hunts.contains("BiomeTags.IS_RIVER")&&hunts.contains("BiomeTags.IS_DEEP_OCEAN"));
        assertEquals(12,Hunts.DRY_MARGIN);
    }

    // ---- the Starshell ----------------------------------------------------------------------------------------------------------------
    @Test void theStarshellHasteLastsLongEnoughToUse(){
        assertEquals(600,CannonUpgrades.STAR_SPEED_TICKS,"30 s from each pulse: steady while it burns, 30 s after");
        assertTrue(CannonUpgrades.STAR_SPEED_TICKS>200,"over 10 s, so its icon does not blink as if ending");
        assertTrue(CannonUpgrades.STAR_SPEED_TICKS>CannonUpgrades.STAR_PULSE_TICKS);
    }
    // ---- the Exchange Shop (side quest) -----------------------------------------------------------------------------------------------
    static List<TextTable.Row> buys(String template){return TextTable.parse(ExchangeShop.sections(List.of(template.split("\n"))).get("buy"),id->true,64,9999).rows();}
    @Test void thePackShopSellsTheStandaloneRareMaterialsToo(){
        var pack=buys(ExchangeShop.PACK);
        for(String rare:List.of("minecraft:gold_ingot","minecraft:blaze_rod","minecraft:ender_pearl","minecraft:diamond")){
            var s=buys(ExchangeShop.STANDALONE).stream().filter(r->r.id().equals(rare)).findFirst().orElseThrow();
            var p=pack.stream().filter(r->r.id().equals(rare)).findFirst().orElse(null);
            assertNotNull(p,rare+" can be bought in the pack edition too");
            assertEquals(s.count(),p.count(),rare);assertEquals(s.value(),p.value(),rare+": the same price in both editions");
        }
        assertTrue(pack.stream().anyMatch(r->r.id().equals("create:precision_mechanism")),"the pack's own goods stay");
    }
    @Test void bothShopsSellIronInBulkSlightlyCheaperPerIngot(){
        for(String template:List.of(ExchangeShop.PACK,ExchangeShop.STANDALONE)){
            var list=buys(template);
            var ingots=list.stream().filter(r->r.id().equals("minecraft:iron_ingot")).findFirst().orElseThrow();
            var blocks=list.stream().filter(r->r.id().equals("minecraft:iron_block")).findFirst().orElseThrow();
            assertEquals(16,ingots.count());assertEquals(1,ingots.value(),"16 ingots for 1 energy in both editions");
            assertEquals(16,blocks.count(),"a large lot: 16 blocks, 144 ingots");
            double perIngot=(double)ingots.value()/ingots.count(),perIngotInBulk=(double)blocks.value()/(blocks.count()*9);
            assertTrue(perIngotInBulk<perIngot,"the bulk lot is a little cheaper per ingot");
            assertTrue(perIngotInBulk>=perIngot*.85,"but only a little (8 energy instead of 9)");
        }
    }
    @Test void anUntouchedOldDefaultFileGetsTheNewDefaultAnEditedOneIsKept(){
        for(boolean standalone:new boolean[]{false,true}){
            String old=ExchangeShop.HEADER+"\n"+(standalone?ExchangeShop.STANDALONE_BEFORE_0021:ExchangeShop.PACK_BEFORE_0021);
            assertTrue(ExchangeShop.oldDefault(old,standalone));
            assertTrue(ExchangeShop.oldDefault(old.replace("\n","   \n")+"\n\n",standalone),"trailing spaces and blank lines do not matter");
            assertFalse(ExchangeShop.oldDefault(old.replace("universal_ammo_coin x16 = 3","universal_ammo_coin x16 = 4"),standalone),"an edited file is the admin's: kept");
            assertFalse(ExchangeShop.oldDefault(old,!standalone),"the other edition's old default is not this one's");
            assertFalse(ExchangeShop.oldDefault(ExchangeShop.HEADER+"\n"+(standalone?ExchangeShop.STANDALONE:ExchangeShop.PACK),standalone),"the current default is not replaced");
        }
    }
    @Test void theProtocolMovedOn() throws Exception{
        // 0.0.21 moved it to 30 (MissionSync carries the mission id); 0.0.23 to 31 (FOB screen and removal confirmation), see FobTest
        String net=source("BeaconNetwork.java");assertTrue(net.contains("\"40-standalone\"")&&net.contains("\"40-pack\""),"protocol 40 since 0.0.35 (the Drink Station view)");
    }
}
