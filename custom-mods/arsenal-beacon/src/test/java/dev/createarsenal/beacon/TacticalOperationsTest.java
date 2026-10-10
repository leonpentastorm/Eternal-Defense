package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tactical Operations (0.0.20): the board, the mission's states, the pay, the scan sampler and the packets, without a world. */
final class TacticalOperationsTest {
    // ---- the numbers ----------------------------------------------------------------------------------------------------------------------
    @Test void theSatelliteBuysChoiceNotIncome(){
        assertArrayEquals(new int[]{2,3,4},new int[]{TacticalRules.offers(1),TacticalRules.offers(2),TacticalRules.offers(3)});
        assertArrayEquals(new int[]{256,512,1024},new int[]{TacticalRules.range(1),TacticalRules.range(2),TacticalRules.range(3)});
        assertEquals(TacticalRules.offers(1),TacticalRules.offers(0),"an out-of-range Mk clamps");assertEquals(TacticalRules.range(3),TacticalRules.range(9));
        assertEquals(List.of(TacticalRules.Kind.PATROL),TacticalRules.classes(1));
        assertEquals(List.of(TacticalRules.Kind.PATROL,TacticalRules.Kind.GARRISON),TacticalRules.classes(2));
        assertEquals(List.of(TacticalRules.Kind.PATROL,TacticalRules.Kind.GARRISON,TacticalRules.Kind.WARLORD),TacticalRules.classes(3));
        assertArrayEquals(new int[]{2,4,8},new int[]{TacticalRules.regionScale(1),TacticalRules.regionScale(2),TacticalRules.regionScale(3)},"Region covers +-range on 256 pixels");
        // the pay does not depend on the Mk: an upgrade buys more offers and classes, never a higher price for the same class
        for(var kind:TacticalRules.Kind.values())assertEquals(Economy.hunt(kind,4,true),Economy.hunt(kind,4,true));
    }
    @Test void theConstantsAreTheOnesTheSpecAsksFor(){
        assertEquals(8,TacticalRules.UPLINK_DISTANCE);assertEquals(150,TacticalRules.BEYOND_ZONE);assertEquals(40,TacticalRules.SEPARATION,1e-9);
        assertEquals(24000,TacticalRules.WARNING_TICKS);assertEquals(64,TacticalRules.APPROACH);assertEquals(24,TacticalRules.SPAWN_RADIUS);assertEquals(32,TacticalRules.LEASH);
        assertEquals(128,TacticalRules.AWAY);assertEquals(6000,TacticalRules.AWAY_TICKS);assertEquals(2,TacticalRules.CAPTAIN_HEALTH_FACTOR);
        assertEquals(256,TacticalRules.SCAN_SIZE);assertEquals(128,TacticalRules.BASE_HALF);assertEquals(600,TacticalRules.SCAN_COOLDOWN_TICKS,"30 second cooldown");
        assertEquals(2,TacticalRules.EXTRA_PER_PLAYER);assertEquals(8,TacticalRules.EXTRA_MAX);
        assertEquals("arsenal_beacon_hunts_v1",HuntData.NAME);
        assertEquals(new int[]{16,32}[1],Economy.SATELLITE_UPGRADE_COILS[1]);assertEquals(10,Economy.SATELLITE_UPGRADE_ARDENT[0]);assertEquals(20,Economy.SATELLITE_UPGRADE_ARDENT[1]);
    }
    @Test void warbandsGrowWithPlayersUpToEightMore(){
        assertEquals(8,TacticalRules.warband(TacticalRules.Kind.PATROL,0,1));assertEquals(12,TacticalRules.warband(TacticalRules.Kind.PATROL,4,1));
        assertEquals(14,TacticalRules.warband(TacticalRules.Kind.GARRISON,0,1));assertEquals(20,TacticalRules.warband(TacticalRules.Kind.GARRISON,6,1));
        assertEquals(10,TacticalRules.warband(TacticalRules.Kind.WARLORD,0,1),"a Warlord's count is the escort; the captain comes on top");
        for(int roll=-50;roll<50;roll++){int n=TacticalRules.warband(TacticalRules.Kind.PATROL,roll,1);assertTrue(n>=8&&n<=12,"roll "+roll+" gave "+n);}
        assertEquals(10,TacticalRules.warband(TacticalRules.Kind.PATROL,0,2));assertEquals(14,TacticalRules.warband(TacticalRules.Kind.PATROL,0,4));
        assertEquals(16,TacticalRules.warband(TacticalRules.Kind.PATROL,0,5),"capped at +8");assertEquals(16,TacticalRules.warband(TacticalRules.Kind.PATROL,0,40));
        assertEquals(8,TacticalRules.warband(TacticalRules.Kind.PATROL,0,0),"nobody online counts as one");
        assertEquals(1,TacticalRules.danger(TacticalRules.Kind.PATROL,0));assertEquals(3,TacticalRules.danger(TacticalRules.Kind.WARLORD,4));assertEquals(4,TacticalRules.danger(TacticalRules.Kind.WARLORD,5));
    }

    // ---- the pay ------------------------------------------------------------------------------------------------------------------------------
    @Test void huntsPayAShareOfARaidsGuaranteedArdent(){
        assertEquals(8,Economy.huntGuaranteed(3,true));assertEquals(4,Economy.huntGuaranteed(3,false));
        assertEquals(22,Economy.huntGuaranteed(10,true));assertEquals(22,Economy.huntGuaranteed(50,true),"the tier is capped at 10");assertEquals(2,Economy.huntGuaranteed(-3,true));
        // tier 3, standalone: g = 8; B = 4, 8, 14
        assertEquals(new Economy.HuntPay(2,9),Economy.hunt(TacticalRules.Kind.PATROL,3,true));
        assertEquals(new Economy.HuntPay(5,17),Economy.hunt(TacticalRules.Kind.GARRISON,3,true));
        assertEquals(new Economy.HuntPay(8,30),Economy.hunt(TacticalRules.Kind.WARLORD,3,true));
        // tier 3, pack: g = 4; B = 2, 4, 7
        assertEquals(new Economy.HuntPay(1,4),Economy.hunt(TacticalRules.Kind.PATROL,3,false));
        assertEquals(new Economy.HuntPay(2,9),Economy.hunt(TacticalRules.Kind.GARRISON,3,false));
        assertEquals(new Economy.HuntPay(4,15),Economy.hunt(TacticalRules.Kind.WARLORD,3,false));
        for(boolean standalone:new boolean[]{true,false})for(int t=0;t<=10;t++){
            var p=Economy.hunt(TacticalRules.Kind.PATROL,t,standalone);var g=Economy.hunt(TacticalRules.Kind.GARRISON,t,standalone);var w=Economy.hunt(TacticalRules.Kind.WARLORD,t,standalone);
            assertTrue(p.coins()<=g.coins()&&g.coins()<=w.coins()&&p.ardent()<=g.ardent()&&g.ardent()<=w.ardent(),"a harder class never pays less (tier "+t+")");
            // the whole pay, valued at the Exchange's coin price, stays at the class's share of a raid's guaranteed Ardent (rounding aside)
            double value=w.ardent()+w.coins()/Economy.COINS_PER_ARDENT;
            assertEquals(1.75*Economy.huntGuaranteed(t,standalone),value,1.0);
        }
    }
    @Test void coinsFromAHuntNeverSellBackForArdent(){
        // the Exchange "loops never pay" rule, with the coins a hunt hands out: neither edition buys coins back, and the coin rate is the shop's
        for(String template:List.of(ExchangeShop.PACK,ExchangeShop.STANDALONE)){
            var parts=ExchangeShop.sections(List.of(template.split("\n")));
            var sell=TextTable.parse(parts.get("sell"),id->true,64,9999);var buy=TextTable.parse(parts.get("buy"),id->true,64,9999);
            assertTrue(sell.rows().stream().noneMatch(r->r.id().equals("arsenal_beacon:universal_ammo_coin")),"coins must never sell for Ardent Energy");
            var coin=buy.rows().stream().filter(r->r.id().equals("arsenal_beacon:universal_ammo_coin")).findFirst().orElseThrow();
            assertEquals(Economy.COINS_PER_ARDENT,coin.count()/(double)coin.value(),1e-9,"hunt coins are valued at the shop's own price");
        }
    }

    // ---- placing offers -----------------------------------------------------------------------------------------------------------------------
    @Test void offersLieBeyondTheZoneApartAndInsideTheBorder(){
        var random=new Random(7);var taken=new ArrayList<Double>();
        for(int i=0;i<4;i++){
            var spot=TacticalRules.place(random,100,-200,40,1024,taken,(x,z)->true);
            assertNotNull(spot);
            assertTrue(spot.distance()>=190&&spot.distance()<=1024,"distance "+spot.distance());
            double real=Math.hypot(spot.x()-100,spot.z()+200);assertEquals(spot.distance(),real,1.0);
            assertEquals(spot.bearing(),TacticalRules.bearing(100,-200,spot.x(),spot.z()),1.0);
            for(double other:taken)assertTrue(TacticalRules.angle(spot.bearing(),other)>=40,"offers at least 40 degrees apart");
            taken.add(spot.bearing());
        }
        // no room: nothing is invented
        assertNull(TacticalRules.place(new Random(1),0,0,40,1024,new ArrayList<>(),(x,z)->false));
        var full=new ArrayList<Double>();for(int b=0;b<360;b+=30)full.add((double)b);
        assertNull(TacticalRules.place(new Random(1),0,0,40,1024,full,(x,z)->true),"every bearing is taken");
        // the border only lets the east half in
        for(int i=0;i<20;i++){var s=TacticalRules.place(new Random(i),0,0,40,512,new ArrayList<>(),(x,z)->x>0);if(s!=null)assertTrue(s.x()>0);}
    }
    @Test void aStructureQualifiesOnlyInsideTheWindow(){
        assertNull(TacticalRules.garrison(0,0,40,512,100,0,List.of(),(x,z)->true),"too close to the zone");
        assertNull(TacticalRules.garrison(0,0,40,512,600,0,List.of(),(x,z)->true),"beyond the satellite's range");
        assertNull(TacticalRules.garrison(0,0,40,512,300,0,List.of(95.0),(x,z)->true),"too close in bearing to another offer");
        var s=TacticalRules.garrison(0,0,40,512,300,0,List.of(),(x,z)->true);
        assertNotNull(s);assertEquals(90,s.bearing(),1e-9);assertEquals(300,s.distance());assertEquals("E",TacticalRules.compass(s.bearing()));
        assertEquals("N",TacticalRules.compass(TacticalRules.bearing(0,0,0,-10)));assertEquals("SW",TacticalRules.compass(TacticalRules.bearing(0,0,-10,10)));
        assertEquals(20,TacticalRules.angle(350,10),1e-9);assertEquals(180,TacticalRules.angle(0,180),1e-9);
    }
    @Test void offersOnOneBoardGetMixedClasses(){
        // the classes of offers made in the same tick (ids 1 to 4, same world seed and time) must not all come out alike
        int[] seen=new int[3];
        for(long world=0;world<50;world++)for(long id=1;id<=4;id++){var r=new Random(TacticalRules.mix(world*7919,id,123456L));seen[r.nextInt(3)]++;}
        for(int k=0;k<3;k++)assertTrue(seen[k]>40,"each class turns up: "+java.util.Arrays.toString(seen));
        int alike=0;for(long world=0;world<50;world++){java.util.Set<Integer> kinds=new java.util.HashSet<>();for(long id=1;id<=4;id++)kinds.add(new Random(TacticalRules.mix(world,id,99L)).nextInt(3));if(kinds.size()==1)alike++;}
        assertTrue(alike<=5,"boards of four with a single class: "+alike+" of 50");
        assertEquals(TacticalRules.mix(1,2,3),TacticalRules.mix(1,2,3));assertNotEquals(TacticalRules.mix(1,2,3),TacticalRules.mix(1,3,2));
    }
    @Test void codenamesAreDeterministicAndVaried(){
        assertEquals(TacticalRules.codename(42),TacticalRules.codename(42));
        assertTrue(TacticalRules.codename(42).matches("OP [A-Z]+ [A-Z]+"));
        var seen=new HashSet<String>();for(long s=0;s<400;s++)seen.add(TacticalRules.codename(s));
        assertTrue(seen.size()>150,"400 seeds give many different names, got "+seen.size());
        for(long s=-5;s<5;s++){var parts=TacticalRules.codename(s).split(" ");assertTrue(Arrays.asList(TacticalRules.FIRST).contains(parts[1])&&Arrays.asList(TacticalRules.SECOND).contains(parts[2]));}
    }
    @Test void theMissionMapPutsTheBaseAndTheObjectiveOnOneSheet(){
        assertEquals(0,TacticalRules.mapScale(0,0,40,40));
        int s=TacticalRules.mapScale(0,0,600,0);assertTrue(s>=3,"600 blocks east needs a zoomed-out map");
        int w=128<<s;assertEquals(Math.floorDiv(64,w),Math.floorDiv(600+64,w));
        assertEquals(4,TacticalRules.mapScale(0,0,5000,0),"too far for any map: the widest");
    }

    // ---- the raid lock and dawn -------------------------------------------------------------------------------------------------------------
    @Test void noMissionIsAcceptedInARaidOrTheDayBeforeOne(){
        long day=24000;
        assertEquals((Rules.intervalDays(0)*day-1000)+500,TacticalRules.untilRaid(0,1000,500));
        assertEquals(0,TacticalRules.untilRaid(0,Rules.intervalDays(0)*day+5,0),"overdue counts as now");
        assertTrue(TacticalRules.raidWarning(24000));assertFalse(TacticalRules.raidWarning(24001));
        assertEquals("no_uplink",TacticalRules.acceptBlocked(false,true,0,true),"no uplink comes first");
        assertEquals("raid",TacticalRules.acceptBlocked(true,true,0,false));
        assertEquals("raid_warning",TacticalRules.acceptBlocked(true,false,24000,false));
        assertEquals("busy",TacticalRules.acceptBlocked(true,false,24001,true));
        assertNull(TacticalRules.acceptBlocked(true,false,24001,false));
        assertTrue(TacticalRules.uplink(64,true,true));assertFalse(TacticalRules.uplink(65,true,true),"more than 8 blocks");
        assertFalse(TacticalRules.uplink(4,false,true));assertFalse(TacticalRules.uplink(4,true,false));
    }
    @Test void aFinishedOfferComesBackAtDawnOneADay(){
        assertEquals(0,TacticalRules.dawnOffers(5,5,2),"same day");
        assertEquals(1,TacticalRules.dawnOffers(5,6,2),"a new day, and never more than one");
        assertEquals(0,TacticalRules.dawnOffers(5,6,0),"the board is full");
        assertEquals(1,TacticalRules.dawnOffers(5,9,3),"days skipped still bring only one");
        var b=new HuntBoard();b.lastDay=10;
        assertEquals(1,b.dawn(11,2));assertEquals(11,b.lastDay);assertEquals(0,b.dawn(11,2),"once per day");
        assertEquals(TacticalRules.day(23999),0);assertEquals(TacticalRules.day(24000),1);
    }

    @Test void theBoardFillsOnceAndOnlyGrowsWithAnUpgrade(){
        var b=new HuntBoard();
        assertEquals(2,b.toFill(2),"the first uplink fills the board");
        b.offers.addAll(board().offers);b.filledTo(2);
        assertEquals(0,b.toFill(2));b.offers.remove(0);
        assertEquals(0,b.toFill(2),"a finished offer waits for dawn, opening the table does not refill it");
        assertEquals(1,b.toFill(3),"an upgrade to Mk II adds its new slot at once");
        b.filledTo(3);assertEquals(0,b.toFill(3));assertEquals(0,b.toFill(2),"never more than the slots");
        assertEquals(1,b.toFill(4));
    }

    // ---- the mission's states ---------------------------------------------------------------------------------------------------------------
    private static HuntBoard board(){
        var b=new HuntBoard();
        b.offers.add(new HuntBoard.Offer(1,TacticalRules.Kind.PATROL,"OP IRON HOUND",400,0,400,90));
        b.offers.add(new HuntBoard.Offer(2,TacticalRules.Kind.GARRISON,"OP STEEL LANCE",0,-500,500,0));
        b.nextId=3;return b;
    }
    @Test void aMissionGoesOfferedActiveEngagedClearedAndPaysOnce(){
        var b=board();
        assertEquals("raid",b.accept(1,"raid"));assertNull(b.mission,"a refused accept changes nothing");
        assertEquals("gone",b.accept(99,null));
        assertNull(b.accept(1,null));assertEquals(HuntBoard.State.ACTIVE,b.mission.state);assertEquals(1,b.mission.serial);assertTrue(b.running());
        assertEquals(1,b.open().size(),"the running offer is not open");
        b.engage(3);assertEquals(HuntBoard.State.ENGAGED,b.mission.state);assertEquals(3,b.mission.pending());
        b.spawnedOne();b.spawnedOne();b.spawnedOne();b.spawnedOne();assertEquals(3,b.mission.spawned,"never more than the total");
        assertFalse(b.killed(2,1),"another mission's mob");assertFalse(b.killed(1,0),"a stale warband's mob");
        assertFalse(b.killed(1,1));assertFalse(b.killed(1,1));assertEquals(1,b.mission.remaining);
        assertFalse(b.clear(),"not before the last one");
        assertTrue(b.killed(1,1));assertEquals(0,b.mission.remaining);assertFalse(b.killed(1,1),"no count below zero");
        assertTrue(b.clear());assertEquals(HuntBoard.State.CLEARED,b.mission.state);
        assertFalse(b.clear(),"CLEARED before paying, and only once: a second call never pays");
        assertNull(b.offer(1),"a cleared offer leaves the board");assertFalse(b.running());
        b.settle();assertNull(b.mission);assertEquals(1,b.missing(2),"its slot is free for the next dawn");
    }
    @Test void aWarbandLeftAloneScattersAndAnotherOneComes(){
        var b=board();b.accept(1,null);b.engage(10);
        assertFalse(b.away(true,TacticalRules.AWAY_TICKS-20));assertFalse(b.away(false,20),"someone came back: the clock resets");
        assertFalse(b.away(true,TacticalRules.AWAY_TICKS-1));assertTrue(b.away(true,1));
        b.scatter();assertEquals(HuntBoard.State.ACTIVE,b.mission.state);assertEquals(2,b.mission.serial,"the old warband is stale");
        assertEquals(0,b.mission.total);assertFalse(b.killed(1,1),"a mob of the scattered warband counts for nothing");
        b.engage(8);assertEquals(8,b.mission.remaining);assertTrue(b.offer(1)!=null,"the offer stays");
    }
    @Test void abandoningPutsTheOfferBackAndNeverHandsASecondFlare(){
        var b=board();b.accept(2,null);var player=UUID.randomUUID();
        assertTrue(b.mission.flared.add(player));
        var ended=b.abandon();assertEquals(2,ended.offer.id);assertNull(b.mission);assertNotNull(b.offer(2),"back on the board");
        assertNull(b.abandon(),"nothing left to abandon");
        b.accept(2,null);assertFalse(b.mission.flared.add(player),"the same offer accepted again: no second flare");
        b.engage(1);b.killed(2,1);b.clear();assertFalse(b.flared.containsKey(2L),"the record goes with the offer");
    }
    @Test void anUnreachableObjectiveIsReplaced(){
        var b=board();b.accept(1,null);var ended=b.unreachable();
        assertEquals(1,ended.offer.id);assertNull(b.offer(1));assertNull(b.mission);assertEquals(1,b.missing(2));
    }
    @Test void mobsLostWithoutDyingAreSpawnedAgainNeverWrittenOff(){
        var b=board();b.accept(1,null);b.engage(5);
        assertEquals(0,b.lost(0),"still spawning: nothing is lost yet");
        for(int i=0;i<5;i++)b.spawnedOne();
        b.killed(1,1);assertEquals(4,b.mission.expectedAlive());
        assertEquals(0,b.lost(4));assertEquals(2,b.lost(2));assertEquals(2,b.mission.pending(),"two more to spawn");assertEquals(4,b.mission.remaining,"the count is never lowered");
        assertFalse(b.clear());
    }
    @Test void theBoardSurvivesARestart(){
        var d=new HuntData();var b=d.board;
        b.offers.addAll(board().offers);b.nextId=3;b.filled=true;b.lastDay=77;
        b.accept(2,null);b.engage(14);b.spawnedOne();b.spawnedOne();b.killed(2,1);var player=UUID.randomUUID();b.mission.flared.add(player);
        d.table=new net.minecraft.core.BlockPos(1,64,2);d.satellite=new net.minecraft.core.BlockPos(3,64,2);d.satelliteMk=3;
        d.base.colors[5]=(byte)0x2d;d.base.fresh[5]=ScanSampler.LIVE;d.region.fresh[70000%65536]=ScanSampler.STALE;d.scannedAt=1234;d.scanVersion=4;d.scannedRange=1024;
        var copy=HuntData.load(d.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(2,copy.board.offers.size());assertEquals(3,copy.board.nextId);assertTrue(copy.board.filled);assertEquals(77,copy.board.lastDay);
        var m=copy.board.mission;assertNotNull(m);assertEquals(HuntBoard.State.ENGAGED,m.state,"an engaged mission stays engaged");
        assertEquals(14,m.total);assertEquals(13,m.remaining);assertEquals(2,m.spawned);assertEquals(12,m.pending(),"the rest spawns when someone comes back");
        assertTrue(m.flared.contains(player));assertEquals("OP STEEL LANCE",m.offer.codename);assertEquals(TacticalRules.Kind.GARRISON,m.offer.kind);
        assertEquals(d.table,copy.table);assertEquals(3,copy.satelliteMk);assertEquals(1234,copy.scannedAt);assertEquals(4,copy.scanVersion);assertEquals(1024,copy.scannedRange);
        assertEquals((byte)0x2d,copy.base.colors[5]);assertEquals(ScanSampler.LIVE,copy.base.fresh[5]);assertEquals(ScanSampler.STALE,copy.region.fresh[70000%65536]);
        // a cleared mission is not saved as running
        copy.board.mission.remaining=0;copy.board.clear();var again=HuntData.load(copy.save(new net.minecraft.nbt.CompoundTag()));assertNull(again.board.mission);assertEquals(1,again.board.offers.size());
    }

    // ---- the scan ------------------------------------------------------------------------------------------------------------------------------
    /** A test world: loaded where {@code loaded} says, flat grass at {@code height}, water east of x 0. */
    private record Flat(java.util.function.BiPredicate<Integer,Integer> loaded,int height) implements ScanSampler.Terrain {
        @Override public long column(int x,int z){if(!loaded.test(x,z))return -1;return x>0?ScanSampler.column(height,x%12,ScanSampler.WATER):ScanSampler.column(height,0,1);}
        @Override public int biome(int x,int z){return ScanSampler.pack(7,ScanSampler.NORMAL);}
    }
    @Test void theScanIsLiveWhereLoadedStaleWhereSeenAndBiomeElsewhere(){
        int size=TacticalRules.SCAN_SIZE;byte[] colors=new byte[size*size],fresh=new byte[size*size];int[] north=new int[size],biomes=new int[size/TacticalRules.BIOME_STEP];
        Arrays.fill(north,Integer.MIN_VALUE);
        // first scan: only the west half is loaded
        var west=new Flat((x,z)->x<0,70);
        for(int row=0;row<size;row++)ScanSampler.row(west,0,0,1,row,colors,fresh,north,biomes);
        assertEquals(ScanSampler.LIVE,fresh[10*size+10]);assertEquals(1,(colors[10*size+10]&0xff)>>2,"grass colour id");
        assertEquals(ScanSampler.BIOME,fresh[10*size+200]);assertEquals(ScanSampler.pack(7,ScanSampler.NORMAL),colors[10*size+200]&0xff);
        // second scan: nothing is loaded; what was seen is kept (stale), the rest stays a biome preview
        Arrays.fill(north,Integer.MIN_VALUE);var none=new Flat((x,z)->false,70);
        for(int row=0;row<size;row++)ScanSampler.row(none,0,0,1,row,colors,fresh,north,biomes);
        assertEquals(ScanSampler.STALE,fresh[10*size+10]);assertEquals(1,(colors[10*size+10]&0xff)>>2,"a stale pixel keeps its last colour");
        assertEquals(ScanSampler.BIOME,fresh[10*size+200]);
        // third scan: everything loaded, water shading by depth
        Arrays.fill(north,Integer.MIN_VALUE);var all=new Flat((x,z)->true,-30);
        for(int row=0;row<size;row++)ScanSampler.row(all,0,0,1,row,colors,fresh,north,biomes);
        assertEquals(ScanSampler.LIVE,fresh[10*size+10]);assertEquals(ScanSampler.LIVE,fresh[10*size+200]);assertEquals(ScanSampler.WATER,(colors[10*size+200]&0xff)>>2);
        assertEquals(ScanSampler.worldX(0,1,200),72,"pixel 200 is block 72 east of the beacon");
    }
    @Test void columnsEncodeNegativeHeightsAndShadeLikeVanillaMaps(){
        long c=ScanSampler.column(-59,3,12);assertTrue(c>=0,"a loaded column is never 'not loaded'");
        assertEquals(-59,ScanSampler.height(c));assertEquals(3,ScanSampler.depth(c));assertEquals(12,ScanSampler.colorId(c));
        assertEquals(319,ScanSampler.height(ScanSampler.column(319,0,1)));
        assertEquals(ScanSampler.HIGH,ScanSampler.brightness(70,68,1,0,0),"facing a lower north neighbour: brighter");
        assertEquals(ScanSampler.LOW,ScanSampler.brightness(66,68,1,0,0));assertEquals(ScanSampler.NORMAL,ScanSampler.brightness(68,68,1,0,0));
        assertEquals(ScanSampler.HIGH,ScanSampler.waterBrightness(1,0,0));assertEquals(ScanSampler.LOW,ScanSampler.waterBrightness(12,0,0));
        assertEquals((12<<2)|2,ScanSampler.pack(12,ScanSampler.HIGH));
    }

    // ---- the Field Guide ------------------------------------------------------------------------------------------------------------------------
    @Test void theFieldGuideQuotesTheRealNumbers(){
        String body=SupportTest.guide("tactical.body"),detail=SupportTest.guide("tactical.detail"),all=body+detail;
        for(String expected:List.of("within "+TacticalRules.UPLINK_DISTANCE+" blocks","2 at Mk I, 3 at Mk II and 4 at Mk III","from 256 to 512 and then 1024 blocks",
                "within "+TacticalRules.APPROACH+" blocks","more than "+TacticalRules.AWAY+" blocks away","for "+TacticalRules.AWAY_TICKS/1200+" minutes",
                "adds "+TacticalRules.EXTRA_PER_PLAYER+" enemies, up to "+TacticalRules.EXTRA_MAX+" more","8 to 12 enemies","14 to 20 enemies","10 to 14 escorts",
                "resonance coils: "+Economy.SATELLITE_UPGRADE_COILS[0]+" for Mk II and "+Economy.SATELLITE_UPGRADE_COILS[1]+" for Mk III",
                "Ardent Energy: "+Economy.SATELLITE_UPGRADE_ARDENT[0]+" for Mk II and "+Economy.SATELLITE_UPGRADE_ARDENT[1]+" for Mk III",
                "at least "+TacticalRules.BEYOND_ZONE+" blocks beyond","within "+TacticalRules.LEASH+" blocks of the objective",TacticalRules.SCAN_COOLDOWN_TICKS/20+" seconds after a scan ends",
                "8 glass and "+Economy.COMMAND_TABLE_PLATING+" reinforced plating","8 glass and "+Economy.COMMAND_TABLE_ARDENT+" Ardent Energy",
                "4 gold and "+Economy.SATELLITE_COILS+" resonance coils","4 gold and "+Economy.SATELLITE_ARDENT+" Ardent Energy"))
            assertTrue(all.contains(expected),"the Tactical Ops page does not say '"+expected+"'");
        for(boolean standalone:new boolean[]{false,true}){
            var p=Economy.hunt(TacticalRules.Kind.PATROL,3,standalone);var g=Economy.hunt(TacticalRules.Kind.GARRISON,3,standalone);var w=Economy.hunt(TacticalRules.Kind.WARLORD,3,standalone);
            String line="Patrol "+p.ardent()+" Ardent Energy and "+p.coins()+" ammo coins, Garrison "+g.ardent()+" and "+g.coins()+", Warlord "+w.ardent()+" and "+w.coins();
            assertTrue(detail.contains((standalone?"[standalone] ":"[pack] ")+"- Pay at reward tier 3: "+line),"the "+(standalone?"standalone":"pack")+" pay line says "+line);
        }
        assertTrue(TacticalRules.Kind.PATROL.min==8&&TacticalRules.Kind.PATROL.max==12&&TacticalRules.Kind.GARRISON.min==14&&TacticalRules.Kind.GARRISON.max==20&&TacticalRules.Kind.WARLORD.min==10&&TacticalRules.Kind.WARLORD.max==14);
    }

    // ---- the packets ---------------------------------------------------------------------------------------------------------------------------
    @Test void thePacketsRoundTrip(){
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var sync=new Hunts.MissionSync(true,"OP IRON HOUND","garrison",-640,321,14,7,true);
        Hunts.MissionSync.encode(sync,buf);assertEquals(sync,Hunts.MissionSync.decode(buf));
        var progress=new TacticalScan.ScanProgress(3,true,true,129);
        TacticalScan.ScanProgress.encode(progress,buf);assertEquals(progress,TacticalScan.ScanProgress.decode(buf));
        var n=new net.minecraft.nbt.CompoundTag();n.putString("blocked","raid_warning");
        Hunts.TableState.encode(new Hunts.TableState(n),buf);assertEquals("raid_warning",Hunts.TableState.decode(buf).data().getString("blocked"));
        // the scan images: header plus four 256 x 256 planes, through the chunked transfer, back byte for byte
        var d=new HuntData();d.scanVersion=9;d.scannedRange=512;d.scanCenter=new net.minecraft.core.BlockPos(100,64,-50);d.scannedAt=77;d.region.colors[123]=(byte)0x31;
        byte[] raw=TacticalScan.encode(d);assertEquals(TacticalScan.HEADER+4*256*256,raw.length);
        var parts=ChunkedPayload.split(raw);assertEquals(1,parts.size(),"a scan fits one part");
        TacticalScan.ScanImage.encode(new TacticalScan.ScanImage(parts.get(0)),buf);
        var back=new ChunkedPayload.Assembler().accept(TacticalScan.ScanImage.decode(buf).part());
        assertArrayEquals(raw,back);
        var in=java.nio.ByteBuffer.wrap(back);assertEquals(9,in.getInt());assertEquals(4,in.getInt(),"Region scale for 512 blocks");assertEquals(100,in.getInt());assertEquals(-50,in.getInt());assertEquals(77,in.getLong());
    }
}
