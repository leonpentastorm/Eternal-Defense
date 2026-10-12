package dev.createarsenal.beacon;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.createarsenal.beacon.RouteRules.*;

/** The supply rail (0.0.24, FOB slice 2) without a world: passes, rides, the scheduler, the clock, the light, the numbers, the data and the models. */
final class RouteTest {
    static final String ASSETS="src/main/resources/assets/arsenal_beacon/";

    // ---- a pass through a gate ----------------------------------------------------------------------------------------------------------------
    @Test void aPassIsASegmentCrossingTheGatePlane(){
        // a gate over the rail cell (10, 64, 20), its rail along X: the plane is x = 10.5
        assertEquals(1,crossing(9.8,64,20.5,10.9,64,20.5,10,64,20,true,.2),"eastwards");
        assertEquals(-1,crossing(11,64,20.5,10.2,64,20.5,10,64,20,true,.2),"westwards");
        assertEquals(1,crossing(4,64.1,20.5,16,64.1,20.4,10,64,20,true,.2),"a cart moving 12 blocks in one tick (a powered rail, a chunk edge) still passes");
        assertEquals(0,crossing(9.6,64,20.5,10.4,64,20.5,10,64,20,true,.2),"stopping inside the arch is no pass");
        assertEquals(0,crossing(10.4,64,20.5,10.4,64,20.5,10,64,20,true,.2),"standing still is no pass");
        assertEquals(0,crossing(9.8,64,22.5,10.9,64,22.5,10,64,20,true,.2),"on a track beside the gate, no pass");
        assertEquals(0,crossing(9.8,67,20.5,10.9,67,20.5,10,64,20,true,.2),"over the arch, no pass");
        assertEquals(0,crossing(10.5,64,19.8,10.5,64,21.3,10,64,20,true,.2),"along the plane (the wrong axis), no pass");
        assertEquals(1,crossing(10.5,64,19.8,10.5,64,21.3,10,64,20,false,.2),"the same move through a gate whose rail runs along Z");
        assertEquals(0,crossing(10.5,64,20.5,11,64,20.5,10,64,20,true,.2),"leaving from exactly the plane was counted by the step before");
    }

    // ---- a ride ---------------------------------------------------------------------------------------------------------------------------------
    @Test void aRideRunsHomeFobHome(){
        assertEquals(Step.STARTED,step(Gate.RECEIVING,null,false,false,500));
        assertEquals(Step.COMMISSIONED,step(Gate.DELIVERY,Stage.OUTBOUND,true,false,500),"the first ride to the FOB opens the route");
        assertEquals(Step.LOADED,step(Gate.DELIVERY,Stage.OUTBOUND,true,true,500),"later rides load at the FOB");
        assertEquals(Step.COMPLETE,step(Gate.RECEIVING,Stage.RETURN,true,true,500));
        assertEquals(Step.WRONG_GATE,step(Gate.DELIVERY,Stage.RETURN,true,true,500),"passing the FOB gate twice changes nothing");
        assertEquals(Step.RESTARTED,step(Gate.RECEIVING,Stage.OUTBOUND,true,true,500),"back through the home gate before the FOB: start again");
        assertEquals(Step.START_AT_HOME,step(Gate.DELIVERY,null,false,true,500),"a ride starts at home, not at the FOB");
        assertEquals(Step.BUSY,step(Gate.RECEIVING,Stage.OUTBOUND,false,true,500),"a second rider on the same route");
        assertEquals(Step.BUSY,step(Gate.DELIVERY,Stage.OUTBOUND,false,true,500));
        assertEquals(Step.TOO_CLOSE,step(Gate.DELIVERY,Stage.OUTBOUND,true,false,MIN_SEPARATION-1),"gates too close never open a route");
        assertEquals(Step.COMMISSIONED,step(Gate.DELIVERY,Stage.OUTBOUND,true,false,MIN_SEPARATION));
        assertEquals(128,MIN_SEPARATION);
    }
    @Test void theBaselineIsTheRideAndCannotBeFaked(){
        assertEquals(3000,clamp(3000,1000),"an honest ride");
        assertEquals((long)Math.ceil(1000/MAX_SPEED),clamp(10,1000),"faster than a cart can go is clamped");
        assertEquals(2600,baseline(2400,2800,1000),"a round trip averages its two legs");
        assertEquals((long)Math.ceil(1000/MAX_SPEED),baseline(5,5,1000));
        assertEquals(12_000,legTimeout(0));assertEquals(12_000,legTimeout(2000));assertEquals(30_000,legTimeout(10_000),"a long route gets 3 T");
    }

    // ---- the scheduler, the clock, the light -------------------------------------------------------------------------------------------------
    @Test void passiveDeliveriesComeEvery2TOver08(){
        assertEquals(6250,period(2500),"T = 2500 ticks: one delivery every 6250");
        assertEquals(MIN_PERIOD,period(60),"0.0.31: never more often than every 5 minutes of play");assertEquals(6000,MIN_PERIOD);
        assertEquals(.8,PASSIVE_EFFICIENCY,1e-9);
        assertTrue(passive(true,true,CLOCK,false));
        assertFalse(passive(true,true,0,false),"nothing while the inspection is overdue");
        assertFalse(passive(true,true,CLOCK,true),"nothing while the Receiving Gate's storage is full");
        assertFalse(passive(false,true,CLOCK,false),"nothing while the FOB is not ACTIVE (DOWN pauses)");
        assertFalse(passive(true,false,CLOCK,false),"nothing before the route is opened");
        assertEquals(8*72_000L,CLOCK,"8 hours of play");
        assertTrue(warning(CLOCK/4));assertFalse(warning(CLOCK/4+1));assertFalse(warning(0),"overdue is not a warning, it is red");
        assertEquals(28.8,tripsPerHour(2500),1e-9);assertEquals(0,tripsPerHour(0),1e-9);
    }
    @Test void theLightHasOneMeaningEverywhere(){
        assertEquals(Signal.GREEN,signal(true,true,true,true,CLOCK,false,false));
        assertEquals(Signal.AMBER,signal(true,true,true,true,CLOCK,true,false),"a ride under way");
        assertEquals(Signal.AMBER,signal(true,true,true,true,CLOCK/5,false,false),"the inspection's last quarter");
        assertEquals(Signal.AMBER,signal(true,true,true,true,CLOCK,false,true),"the storage is full");
        assertEquals(Signal.RED,signal(true,true,true,false,CLOCK,false,false),"no route yet");
        assertEquals(Signal.RED,signal(true,true,true,true,0,false,false),"overdue");
        assertEquals(Signal.RED,signal(true,true,false,true,CLOCK,false,false),"the FOB is DOWN");
        assertEquals(Signal.RED,signal(true,false,true,true,CLOCK,false,false),"the partner gate is missing");
        assertEquals(Signal.RED,signal(false,true,true,true,CLOCK,false,false),"unlinked");
        assertEquals(Signal.RED,signal(true,true,true,true,0,true,true),"red wins over amber");
    }

    // ---- the tribute (0.0.31): four loot slots ------------------------------------------------------------------------------------------------
    static final boolean[] ALL={true,true,true,true};
    /** Tribute value an hour of play for a FOB on a route of this length ridden at this speed (blocks a tick). */
    static double valuePerHour(double distance,int villagers,double speed){
        long t=Math.round(distance/speed);double sum=0;for(var slot:Slot.values())sum+=budget(slot,ALL,distance,villagers);
        return sum*tripsPerHour(period(t));
    }
    @Test void theNumbersFollowTheTuningPrinciples(){
        double fullSpeed=.4;   // a vanilla cart's top speed, blocks a tick
        // (a) 0.0.30's throughput is kept: ten villagers on a 1 km route at full speed bring 57.6 value an hour
        assertEquals(57.6,valuePerHour(1000,10,fullSpeed),1e-6);
        assertTrue(valuePerHour(1000,10,fullSpeed/2)<valuePerHour(1000,10,fullSpeed),"a slower rail brings less: a faster rail pays");
        assertEquals(2*valuePerHour(1000,10,fullSpeed),valuePerHour(1000,20,fullSpeed),1e-6,"twenty villagers bring twice ten");
        assertEquals(valuePerHour(1000,20,fullSpeed),valuePerHour(1000,60,fullSpeed),1e-6,"only twenty count");
        // (b) riding a round trip brings up to two deliveries: back to back that is 2.5 times passive
        long t=Math.round(1000/fullSpeed);double ridden=rideTrips(2*t,period(t))*HOUR/(2*t),passive=tripsPerHour(period(t));
        assertEquals(ACTIVE_FACTOR,rideTrips(2*t,period(t)),1e-9,"1 km at full speed: the full two");
        assertEquals(2.5,ridden/passive,1e-9);
        // (c) far routes are never worse per hour at equal speed, and pay the owner's bonus: up to 20 percent from 5,000 blocks
        double last=0;
        for(int d=MIN_SEPARATION;d<=10_000;d+=97){double p=valuePerHour(d,10,fullSpeed);assertTrue(p>=last*.999,"per hour at "+d+": "+p+" after "+last);last=p;}
        assertEquals(0,distanceBonus(1000),1e-9);assertEquals(.10,distanceBonus(3000),1e-9);assertEquals(.20,distanceBonus(5000),1e-9);assertEquals(.20,distanceBonus(40_000),1e-9);
        assertEquals(1.2*valuePerHour(1000,10,fullSpeed),valuePerHour(5000,10,fullSpeed),1e-6,"5 km: 20 percent more an hour than 1 km");
        assertEquals(1,units(MIN_SEPARATION),1e-9);assertEquals(5,units(1000),1e-9);assertEquals(MAX_UNITS,units(50_000),1e-9);
    }
    /** 0.0.30's stockpile capped what rides could take; without it a ride is capped by time: a short loop ridden over and over makes nothing extra. */
    @Test void ridingNeverPaysMoreThan2Point5TimesPassive(){
        for(int d=MIN_SEPARATION;d<=20_000;d+=137)for(double speed:new double[]{.1,.4,.57}){
            long t=Math.round(d/speed),p=period(t);
            for(long lap=2*t;lap<=2*t+12_000;lap+=1_500){   // back to back, or with a stop at either end
                double perHour=rideTrips(lap,p)*HOUR/lap;
                assertTrue(perHour<=RIDE_BOOST*tripsPerHour(p)+1e-9,"d="+d+" speed="+speed+" lap="+lap+": "+perHour+" trips an hour");
            }
        }
        assertEquals(0,rideTrips(0,6000),1e-9,"a ride right after a delivery brings nothing more");
        assertEquals(1,rideTrips(2400,6000),1e-9);assertEquals(ACTIVE_FACTOR,rideTrips(1_000_000,6000),1e-9,"never more than two");
        // the emerald minimum (a third a delivery) can bring at most 4 an hour by itself, and 10 an hour riding a short loop all day
        assertEquals(4,EMERALD_MIN*tripsPerHour(MIN_PERIOD),1e-9);
    }
    @Test void theSlotsThatAreOnShareTheTrip(){
        boolean[] farmOnly={false,false,true,false},two={false,true,true,false};
        double share=budget(Slot.FARM,ALL,1000,10);
        assertEquals(1.25,share,1e-9,"all four on: a quarter of 5 each");
        assertEquals(4*share,budget(Slot.FARM,farmOnly,1000,10),1e-9,"the farm alone gets the whole trip");
        assertEquals(2*share,budget(Slot.MINE,two,1000,10),1e-9,"two on: half each");
        assertEquals(0,budget(Slot.EMERALD,farmOnly,1000,10),1e-9,"a slot that is off gets nothing");
        assertFalse(mayToggle(farmOnly,Slot.FARM),"the last slot on stays on");assertTrue(mayToggle(farmOnly,Slot.MINE),"a slot that is off can always come on");assertTrue(mayToggle(ALL,Slot.EMERALD));
        // every village sends emeralds: at least one every three deliveries while the slot is on, more on big or far routes
        assertEquals(EMERALD_MIN,itemsPerTrip(Slot.EMERALD,EMERALD,ALL,MIN_SEPARATION,3),1e-9);
        assertEquals(1/3.0,EMERALD_MIN,1e-9);
        boolean[] emeraldOnly={true,false,false,false};
        assertTrue(itemsPerTrip(Slot.EMERALD,EMERALD,emeraldOnly,5000,20)>1,"an emerald village with 20 villagers 5 km out: more than one a delivery");
    }
    @Test void aTripPaysWholeItemsAndKeepsTheRest(){
        var rnd=new Random(11);
        for(int run=0;run<500;run++){
            boolean[] on=new boolean[4];for(int i=0;i<4;i++)on[i]=rnd.nextBoolean();if(enabled(on)==0)on[rnd.nextInt(4)]=true;
            var spec=Specialty.values()[rnd.nextInt(4)];
            Loot[] loot={EMERALD,MINE_LOOT.get(rnd.nextInt(MINE_LOOT.size())),FARM_LOOT.get(rnd.nextInt(FARM_LOOT.size())),spec.options().get(rnd.nextInt(spec.options().size()))};
            double distance=MIN_SEPARATION+rnd.nextInt(8000);int villagers=rnd.nextInt(25),trips=1+rnd.nextInt(2);
            double[] tally=new double[4];for(int i=0;i<4;i++)tally[i]=rnd.nextDouble()*loot[i].value();
            double[] before=tally.clone();int[] got=trip(tally,loot,on,distance,villagers,trips);
            for(int i=0;i<4;i++){
                double added=trips*perTrip(Slot.values()[i],on,distance,villagers);
                assertEquals(before[i]+added,got[i]*loot[i].value()+tally[i],1e-6,"value is never lost or made: slot "+i);
                assertTrue(tally[i]>=-1e-9&&tally[i]<loot[i].value()+1e-9,"only a part of one item stays in the tally");
                if(!on[i])assertEquals(0,got[i],"a slot that is off sends nothing");
            }
        }
    }
    @Test void theLootListsAreTheOwnersLists(){
        assertEquals(List.of("minecraft:redstone","minecraft:iron_ingot","minecraft:andesite","minecraft:flint","minecraft:gunpowder","minecraft:charcoal"),MINE_LOOT.stream().map(Loot::id).toList());
        assertTrue(FARM_LOOT.stream().map(Loot::id).toList().containsAll(List.of("minecraft:wheat","minecraft:carrot","minecraft:potato","minecraft:beetroot","minecraft:melon_slice","minecraft:pumpkin","minecraft:apple","minecraft:sweet_berries","minecraft:glow_berries","minecraft:cocoa_beans")));
        assertTrue(FISHING_LOOT.stream().map(Loot::id).toList().containsAll(List.of("minecraft:cod","minecraft:salmon","minecraft:tropical_fish","minecraft:pufferfish")));
        assertTrue(HUSBANDRY_LOOT.stream().map(Loot::id).toList().containsAll(List.of("minecraft:beef","minecraft:porkchop","minecraft:chicken","minecraft:mutton","minecraft:rabbit","minecraft:leather","minecraft:rabbit_hide")));
        // potion makers: the owner's tiers, each tier twice the one below: S takes 16 times as many deliveries as D
        assertEquals(17,POTION_LOOT.size(),"every brewing ingredient of 1.20.1");
        for(var l:POTION_LOOT)assertEquals(POTION_TIER_VALUE[l.note()],l.value(),1e-9);
        assertEquals(5,POTION_LOOT.stream().filter(l->l.id().equals("minecraft:ghast_tear")).findFirst().orElseThrow().note(),"ghast tear: S");
        assertEquals(1,POTION_LOOT.stream().filter(l->l.id().equals("minecraft:sugar")).findFirst().orElseThrow().note(),"sugar: D");
        assertEquals(16,POTION_TIER_VALUE[5]/POTION_TIER_VALUE[1],1e-9);
        // the wandering trader: everything it sells in 1.20.1, its price in emeralds sets the time
        assertEquals(70,TRADER_LOOT.size());
        var sapling=TRADER_LOOT.stream().filter(l->l.id().equals("minecraft:oak_sapling")).findFirst().orElseThrow();var sand=TRADER_LOOT.stream().filter(l->l.id().equals("minecraft:sand")).findFirst().orElseThrow();
        assertEquals(5*EMERALD_VALUE,sapling.value(),1e-9);assertEquals(EMERALD_VALUE/8,sand.value(),1e-9);
        for(var spec:Specialty.values())assertFalse(spec.options().isEmpty());
        for(var list:List.of(MINE_LOOT,FARM_LOOT,FISHING_LOOT,HUSBANDRY_LOOT,POTION_LOOT,TRADER_LOOT))for(var l:list)assertFalse(l.id().contains("arsenal")||l.id().contains("coin"),"no currency of the mod: "+l.id());
    }
    /**
     * The tribute items the Exchange buys (they turn into Ardent Energy). Listed here so a change to either list is a decision: an
     * emerald-only FOB of 10 villagers on a 1 km route brings 4.8 emeralds an hour, 2.4 Ardent Energy at the standalone Exchange.
     */
    @Test void theExchangeBuysOnlyTheseTributeItems(){
        var all=new java.util.TreeSet<String>();for(var list:List.of(EMERALD_LOOT,MINE_LOOT,FARM_LOOT,FISHING_LOOT,HUSBANDRY_LOOT,POTION_LOOT,TRADER_LOOT))for(var l:list)all.add(l.id());
        for(String list:List.of(ExchangeShop.PACK,ExchangeShop.STANDALONE)){
            String sell=list.substring(list.indexOf("[sell]"),list.indexOf("[buy]"));var bought=new java.util.TreeSet<String>();
            for(String id:all)if(sell.contains(id+" ")||sell.contains(id+"\n"))bought.add(id);
            var expected=list==ExchangeShop.PACK?java.util.Set.<String>of():java.util.Set.of("minecraft:emerald","minecraft:ghast_tear");
            assertEquals(new java.util.TreeSet<>(expected),bought,list==ExchangeShop.PACK?"pack":"standalone");
        }
        boolean[] emeraldOnly={true,false,false,false},potionsOnly={false,false,false,true};
        double perHour=itemsPerTrip(Slot.EMERALD,EMERALD,emeraldOnly,1000,10)*tripsPerHour(period(2500));
        assertEquals(4.8,perHour,1e-6);
        // the standalone Exchange pays 1 Ardent Energy for 2 emeralds and 2 for a ghast tear: a potion makers' FOB on ghast tears earns the same
        var tear=POTION_LOOT.stream().filter(l->l.id().equals("minecraft:ghast_tear")).findFirst().orElseThrow();
        double tears=itemsPerTrip(Slot.SPECIALTY,tear,potionsOnly,1000,10)*tripsPerHour(period(2500));
        assertEquals(perHour/2,tears*2,1e-6);
    }

    // ---- the saved data --------------------------------------------------------------------------------------------------------------------------
    @Test void routesSaveAndLoad(){
        var d=new FobData();var f=d.add(new BlockPos(0,64,0),1L,0L);d.onlineTicks=123_456;
        f.deliveryGate=new BlockPos(5,64,5);f.receivingGate=new BlockPos(-900,70,12);f.commissioned=true;f.baseline=3210;f.clock=99_000;f.nextPassive=130_000;
        f.workers=9;f.note.put("minecraft:flint",3);f.lastKind="ride";f.lastItems=10;f.lastAt=120_000;f.lastTrip=119_000;
        f.slotOn[0]=false;f.mine=2;f.farm=5;f.special=3;f.specialty=Specialty.POTIONS;f.tally[1]=.75;f.tally[3]=2.5;f.zoneColumns=new long[]{7,9};
        f.stockpile.putString("future","kept");
        var back=FobData.load(d.save(new CompoundTag())).byId(f.id);
        assertEquals(new BlockPos(5,64,5),back.deliveryGate);assertEquals(new BlockPos(-900,70,12),back.receivingGate);
        assertTrue(back.commissioned);assertEquals(3210,back.baseline);assertEquals(99_000,back.clock);assertEquals(130_000,back.nextPassive);
        assertEquals(9,back.workers);assertEquals(3,back.note.get("minecraft:flint"));assertEquals("ride",back.lastKind);assertEquals(10,back.lastItems);assertEquals(120_000,back.lastAt);assertEquals(119_000,back.lastTrip);
        assertFalse(back.slotOn[0]);assertTrue(back.slotOn[1]&&back.slotOn[2]&&back.slotOn[3]);
        assertEquals(2,back.mine);assertEquals(5,back.farm);assertEquals(3,back.special);assertEquals(Specialty.POTIONS,back.specialty);
        assertEquals(.75,back.tally[1],1e-12);assertEquals(2.5,back.tally[3],1e-12);assertArrayEquals(new long[]{7,9},back.zoneColumns);
        assertEquals("kept",back.stockpile.getString("future"),"unknown keys in the slice 2 tags survive");
        assertEquals(123_456,FobData.load(d.save(new CompoundTag())).onlineTicks);
        assertTrue(back.hasRoute());assertEquals(Math.hypot(905,7),back.separation(),1e-6);
        var none=FobData.load(new FobData().save(new CompoundTag()));assertEquals(0,none.onlineTicks);
        // a FOB saved before 0.0.31: every slot on, its specialty from its id
        var old=d.save(new CompoundTag());((CompoundTag)old.getList("fobs",10).get(0)).getCompound("route").remove("tribute");
        var migrated=FobData.load(old).byId(f.id);
        assertTrue(enabled(migrated.slotOn)==4);assertEquals(Specialty.of((int)f.id),migrated.specialty);
    }

    // ---- the owner's models ------------------------------------------------------------------------------------------------------------------------
    static List<double[]> elements(String id) throws Exception{
        var out=new ArrayList<double[]>();
        for(var e:JsonParser.parseString(Files.readString(Path.of(ASSETS+"models/block/"+id+".json"))).getAsJsonObject().getAsJsonArray("elements")){
            var o=e.getAsJsonObject();var a=o.getAsJsonArray("from");var b=o.getAsJsonArray("to");
            out.add(new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble()});
        }
        return out;
    }
    /** The whole gate in the anchor's frame (x 0..64): the right half as the anchor draws it, the left half moved by 48 (the column is 3 cells on). */
    static List<double[]> whole(RailGates.Kind kind) throws Exception{
        var out=new ArrayList<double[]>(elements(kind.id+"_right"));
        for(double[] b:elements(kind.id+"_left"))out.add(new double[]{b[0]+48,b[1],b[2],b[3]+48,b[4],b[5]});
        return out;
    }
    @Test void theLanesAreClearAndTheGateIsFourWideAndTwoTall() throws Exception{
        for(var kind:RailGates.Kind.values()){
            double x0=99,x1=-99,y0=99,y1=-99,z0=99,z1=-99;
            for(double[] b:whole(kind)){
                x0=Math.min(x0,b[0]);y0=Math.min(y0,b[1]);z0=Math.min(z0,b[2]);x1=Math.max(x1,b[3]);y1=Math.max(y1,b[4]);z1=Math.max(z1,b[5]);
                boolean overLanes=b[0]<48&&b[3]>16;
                assertFalse(overLanes&&b[1]<24.95,kind.id+": an element hangs into the lanes below the beam: "+Arrays.toString(b));
            }
            assertEquals(RailGates.WIDE,(int)Math.round((x1-x0)/16),kind.id+" width from "+x0+" to "+x1);
            assertEquals(RailGates.TALL,(int)Math.round((y1-y0)/16),kind.id+" height");assertEquals(RailGates.DEEP,(int)Math.ceil((z1-z0)/16));
            assertTrue(y0>=0&&y1<=32&&x0>=0&&x1<=64&&z0>=0&&z1<=16,"inside the 4 x 2 cells from the anchor");
            for(double[] b:elements(kind.id+"_right"))assertTrue(b[0]>=0&&b[3]<=32,"the right half stays in x 0..32 (a model reaches 3 blocks at most)");
            for(double[] b:elements(kind.id+"_left"))assertTrue(b[0]>=-16&&b[3]<=16,"the left half stays in x -16..16");
        }
        assertEquals(4,RailGates.Frame.PARTS.length,"the beam: a part over each of the four bottom cells");
        for(int[] f:RailGates.Frame.PARTS)assertEquals(1,f[1],"the parts are the top row");
        assertArrayEquals(new int[]{3,0},RailGates.Frame.COLUMN,"the left column is the fourth cell");
        assertEquals(RailGates.LANES,RailGates.Frame.LANES.length);
        for(int[] lane:RailGates.Frame.LANES)assertTrue(lane[1]==0&&lane[0]>0&&lane[0]<3,"the lanes are the two middle cells of the bottom row");
        // over a lane only the beam collides: a seated rider's head (about 1.51 blocks above the rail) passes under it
        double[] beam=RailGates.Frame.BEAM;
        assertEquals(9.5,beam[1]);assertEquals(16,beam[4]);assertEquals(0,beam[0]);assertEquals(16,beam[3]);
        assertTrue(1+beam[1]/16>1.51,"the beam's underside clears a seated rider");
    }
    /** The clicked rail's lane: a second rail beside it decides; a lone rail grows the gate towards the side clicked. */
    @Test void aSecondRailBesideTheClickedOneDecidesTheLanes(){
        assertArrayEquals(new int[]{1,2},RailGates.Frame.laneOrder(true,false,-.3),"a rail on the clockwise side: the clicked one is lane 1");
        assertArrayEquals(new int[]{2,1},RailGates.Frame.laneOrder(false,true,.3),"a rail on the other side: the clicked one is lane 2");
        assertArrayEquals(new int[]{1,2},RailGates.Frame.laneOrder(false,false,.2),"a lone rail clicked on its clockwise half");
        assertArrayEquals(new int[]{2,1},RailGates.Frame.laneOrder(false,false,-.2),"a lone rail clicked on its other half");
        assertArrayEquals(new int[]{2,1},RailGates.Frame.laneOrder(true,true,-.2),"rails on both sides: the click decides");
    }
    /** The pass plane spans both lanes: a cart on either rail passes, one beside the gate does not. */
    @Test void aPassThroughEitherLaneCounts(){
        // lanes at z 20 and 21 (the corner cell 10, 64, 20), rails along X
        assertEquals(1,crossing(9.8,64.06,20.5,10.9,64.06,20.5,10,64,20,true,2,.2),"lane 1, eastwards");
        assertEquals(-1,crossing(11,64.06,21.5,10.2,64.06,21.5,10,64,20,true,2,.2),"lane 2, westwards: the way back of a loop");
        assertEquals(0,crossing(9.8,64.06,22.5,10.9,64.06,22.5,10,64,20,true,2,.2),"in the column's cell, no pass");
        assertEquals(0,crossing(9.8,64.06,19.5,10.9,64.06,19.5,10,64,20,true,2,.2),"in the anchor's cell, no pass");
        assertEquals(0,crossing(9.8,64.06,21.5,10.9,64.06,21.5,10,64,20,true,.2),"the one-lane form still covers one cell");
    }
    @Test void theLampsAreSwitchedByAnOverlayAndTheOwnersFilesAreUnchanged() throws Exception{
        var art=Path.of("../../docs/art/Create-Arsenal-Supply-Rail-Gates-4wide");
        for(var kind:RailGates.Kind.values()){
            for(String half:List.of("_right","_left")){
                assertEquals(sha(art.resolve("minecraft-models/block/"+kind.id+half+".json")),sha(Path.of(ASSETS+"models/block/"+kind.id+half+".json")),kind.id+half+" model unchanged");
                assertEquals(sha(art.resolve("textures/block/"+kind.id+half+".png")),sha(Path.of(ASSETS+"textures/block/"+kind.id+half+".png")),kind.id+half+" texture unchanged");
                String state=Files.readString(Path.of(ASSETS+"blockstates/"+kind.id+(half.equals("_right")?"":"_left")+".json"));
                assertTrue(state.contains("arsenal_beacon:block/"+kind.id+half+"\""),kind.id+half+" is drawn by its block");
                for(String s:List.of("red","amber","green")){
                    assertTrue(state.contains(kind.id+half+"_signal_"+s)&&state.contains("\"signal\": \""+s+"\""),kind.id+half+" shows the "+s+" light");
                    assertTrue(Files.exists(Path.of(ASSETS+"models/block/"+kind.id+half+"_signal_"+s+".json")),"overlay "+kind.id+half+"_signal_"+s);
                }
                String model=Files.readString(Path.of(ASSETS+"models/block/"+kind.id+half+".json"));
                String row=half.equals("_right")?"0":"1";
                for(int i=0;i<3;i++)assertTrue(model.contains("Signal_lamp_"+row+i),"the owner's lamp Signal_lamp_"+row+i+" is where the overlay draws");
            }
            assertEquals(sha(art.resolve("minecraft-models/item/"+kind.id+".json")),sha(Path.of(ASSETS+"models/item/"+kind.id+".json")),kind.id+" item model unchanged");
            assertEquals(sha(art.resolve("textures/item/"+kind.id+".png")),sha(Path.of(ASSETS+"textures/item/"+kind.id+".png")),kind.id+" item texture unchanged");
            assertFalse(Files.exists(Path.of(ASSETS+"models/block/"+kind.id+".json")),"the 3 x 3 model of 0.0.24 is gone");
        }
        assertTrue(Files.exists(Path.of(ASSETS+"textures/block/supply_gate_lamps.png")),"the overlay has its own new texture");
    }
    private static String sha(Path p) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    @Test void thePricesAndTheGuideAgree(){
        assertTrue(WorkshopFabrication.ITEMS.contains("supply_receiving_gate"),"the Receiving Gate is sold at home");
        assertTrue(WorkshopFabrication.ITEMS.contains("supply_delivery_gate"),"0.0.30: the Delivery Gate is sold at home too (and still at the FOB)");
        assertEquals(16,Economy.GATE_IRON);assertEquals(8,Economy.GATE_COPPER);assertEquals(8,Economy.GATE_REDSTONE);assertEquals(1,Economy.GATE_LOGISTICS);assertEquals(4,Economy.GATE_ARDENT);
    }
}
