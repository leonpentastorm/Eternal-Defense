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

    // ---- the scheduler, the clock, the backlog, the light --------------------------------------------------------------------------------------
    @Test void passiveDeliveriesComeEvery2TOver08(){
        assertEquals(6250,period(2500),"T = 2500 ticks: one delivery every 6250");
        assertEquals(.8,PASSIVE_EFFICIENCY,1e-9);
        assertTrue(passive(true,true,CLOCK,false));
        assertFalse(passive(true,true,0,false),"nothing while the inspection is overdue");
        assertFalse(passive(true,true,CLOCK,true),"nothing while the reward chest is backed up");
        assertFalse(passive(false,true,CLOCK,false),"nothing while the FOB is not ACTIVE (DOWN pauses)");
        assertFalse(passive(true,false,CLOCK,false),"nothing before the route is opened");
        assertEquals(8*72_000L,CLOCK,"8 hours of play");
        assertTrue(warning(CLOCK/4));assertFalse(warning(CLOCK/4+1));assertFalse(warning(0),"overdue is not a warning, it is red");
        assertFalse(backlog(199));assertTrue(backlog(200));assertEquals(200,BACKLOG_CAP);
    }
    @Test void theLightHasOneMeaningEverywhere(){
        assertEquals(Signal.GREEN,signal(true,true,true,true,CLOCK,false,false));
        assertEquals(Signal.AMBER,signal(true,true,true,true,CLOCK,true,false),"a ride under way");
        assertEquals(Signal.AMBER,signal(true,true,true,true,CLOCK/5,false,false),"the inspection's last quarter");
        assertEquals(Signal.AMBER,signal(true,true,true,true,CLOCK,false,true),"waiting on the backlog");
        assertEquals(Signal.RED,signal(true,true,true,false,CLOCK,false,false),"no route yet");
        assertEquals(Signal.RED,signal(true,true,true,true,0,false,false),"overdue");
        assertEquals(Signal.RED,signal(true,true,false,true,CLOCK,false,false),"the FOB is DOWN");
        assertEquals(Signal.RED,signal(true,false,true,true,CLOCK,false,false),"the partner gate is missing");
        assertEquals(Signal.RED,signal(false,true,true,true,CLOCK,false,false),"unlinked");
        assertEquals(Signal.RED,signal(true,true,true,true,0,true,true),"red wins over amber");
    }

    // ---- the numbers against the owner's tuning principles ----------------------------------------------------------------------------------------
    static double perHour(int villagers){double sum=0;for(var g:GOODS)sum+=produced(g,villagers,HOUR);return sum;}
    static double passivePerHour(double distance,double speed){long t=Math.round(distance/speed);return capacity(distance)*(double)HOUR/period(t);}
    @Test void theNumbersFollowTheTuningPrinciples(){
        double fullSpeed=.4;   // a vanilla cart's top speed, blocks a tick
        // (a) the route binds at normal play: one FOB of 10 villagers makes a little more than passive trips carry on a 1 km route at full speed
        double made=perHour(10),carried=passivePerHour(1000,fullSpeed);
        assertTrue(made>carried&&made<carried*1.3,"10 villagers make "+made+"/h, a 1 km route carries "+carried+"/h");
        assertTrue(passivePerHour(1000,fullSpeed/2)<carried,"a slower rail carries less: a faster rail pays");
        // (b) riding beats passive by 2 to 3 times per hour of riding
        long t=Math.round(1000/fullSpeed);double ridden=activeCapacity(1000)*(double)HOUR/(2*t);
        assertTrue(ridden/carried>=2&&ridden/carried<=3,"riding carries "+ridden+"/h, passive "+carried+"/h");
        // (c) far FOBs are never worse per hour at equal speed: flat up to 4000 blocks (the capacity cap), never falling before it
        double last=0;
        for(int d=200;d<=4000;d+=200){double p=passivePerHour(d,fullSpeed);assertTrue(p>=last*.97,"per hour at "+d+": "+p+" after "+last);last=Math.max(last,p);}
        for(int d=MIN_SEPARATION;d<=20_000;d+=37){assertTrue(capacity(d)>=capacity(d-37),"capacity never falls with distance ("+d+")");}
        assertEquals(1,capacity(MIN_SEPARATION));assertEquals(5,capacity(1000));assertEquals(MAX_CAPACITY,capacity(9000));assertEquals(10,activeCapacity(1000));
    }
    @Test void theStockpileGrowsWithVillagersAndIsCapped(){
        var redstone=GOODS.get(0);
        assertEquals(15,produced(redstone,10,HOUR),1e-9,"1.5 an hour per villager");
        assertEquals(produced(redstone,MAX_WORKERS,HOUR),produced(redstone,50,HOUR),1e-9,"only 20 villagers work");
        assertEquals(0,produced(redstone,0,HOUR),1e-9);assertEquals(0,produced(redstone,10,-5),1e-9);
        var s=accrue(new double[GOODS.size()],10,100*HOUR);
        for(int i=0;i<GOODS.size();i++)assertEquals(cap(GOODS.get(i),10),s[i],1e-9,"capped: "+GOODS.get(i).id());
        assertEquals(15*CAP_HOURS,cap(redstone,10),1e-9);assertEquals(MIN_CAP,cap(GOODS.get(6),1),1e-9,"at least 16 of each");
    }
    @Test void everyItemIsPaidExactlyOnce(){
        var rnd=new Random(7);
        for(int run=0;run<500;run++){
            double[] stock=new double[GOODS.size()];for(int i=0;i<stock.length;i++)stock[i]=rnd.nextDouble()*(rnd.nextBoolean()?3:40);
            int cap=rnd.nextInt(30);int[] taken=take(stock,cap);
            assertTrue(total(taken)<=cap,"never more than the trip carries");
            double whole=0;for(double v:stock)whole+=Math.floor(v);
            assertEquals((int)Math.min(cap,whole),total(taken),"as much as the trip carries, when the stock has it");
            for(int i=0;i<stock.length;i++)assertTrue(taken[i]<=stock[i]+1e-9,"never more of a good than is there");
        }
    }
    /** Tribute is free: if the Exchange bought any of it, every delivery would be Ardent Energy. Checked against both editions' default lists. */
    @Test void noTributeGoodCanBeSoldAtTheExchange(){
        for(String list:List.of(ExchangeShop.PACK,ExchangeShop.STANDALONE)){
            String sell=list.substring(list.indexOf("[sell]"),list.indexOf("[buy]"));
            for(var g:GOODS)assertFalse(sell.contains(g.id()),g.id()+" is bought by the Exchange");
        }
        for(var g:GOODS)assertFalse(g.id().contains("emerald")||g.id().contains("ardent")||g.id().contains("coin"),"no currency: "+g.id());
    }

    // ---- the saved data --------------------------------------------------------------------------------------------------------------------------
    @Test void routesSaveAndLoad(){
        var d=new FobData();var f=d.add(new BlockPos(0,64,0),1L,0L);d.onlineTicks=123_456;
        f.deliveryGate=new BlockPos(5,64,5);f.receivingGate=new BlockPos(-900,70,12);f.commissioned=true;f.baseline=3210;f.clock=99_000;f.nextPassive=130_000;
        f.stock[0]=12.75;f.stock[3]=4;f.accruedAt=123_000;f.workers=9;f.note[2]=3;f.lastKind="ride";f.lastItems=10;f.lastAt=120_000;
        f.stockpile.putString("future","kept");
        var back=FobData.load(d.save(new CompoundTag())).byId(f.id);
        assertEquals(new BlockPos(5,64,5),back.deliveryGate);assertEquals(new BlockPos(-900,70,12),back.receivingGate);
        assertTrue(back.commissioned);assertEquals(3210,back.baseline);assertEquals(99_000,back.clock);assertEquals(130_000,back.nextPassive);
        assertEquals(12.75,back.stock[0],1e-9);assertEquals(4,back.stock[3],1e-9);assertEquals(123_000,back.accruedAt);assertEquals(9,back.workers);
        assertEquals(3,back.note[2]);assertEquals("ride",back.lastKind);assertEquals(10,back.lastItems);assertEquals(120_000,back.lastAt);
        assertEquals("kept",back.stockpile.getString("future"),"unknown keys in the slice 2 tags survive");
        assertEquals(123_456,FobData.load(d.save(new CompoundTag())).onlineTicks);
        assertTrue(back.hasRoute());assertEquals(Math.hypot(905,7),back.separation(),1e-6);
        assertSame(FobData.load(d.save(new CompoundTag())).byGate(new BlockPos(5,64,5)).getClass(),FobData.Fob.class);
        var none=FobData.load(new FobData().save(new CompoundTag()));assertEquals(0,none.onlineTicks);
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
    @Test void theOpeningIsClearAndTheFrameIsThreeByThree() throws Exception{
        for(var kind:RailGates.Kind.values()){
            double x0=99,x1=-99,y0=99,y1=-99,z0=99,z1=-99;
            for(double[] b:elements(kind.id)){
                x0=Math.min(x0,b[0]);y0=Math.min(y0,b[1]);z0=Math.min(z0,b[2]);x1=Math.max(x1,b[3]);y1=Math.max(y1,b[4]);z1=Math.max(z1,b[5]);
                boolean inOpening=b[0]<16&&b[3]>0&&b[1]<15.85&&b[4]>-16;
                assertFalse(inOpening,kind.id+": an element reaches into the opening a rider passes through: "+Arrays.toString(b));
            }
            assertEquals(RailGates.WIDE,(int)Math.round((x1-x0)/16+.4),kind.id+" width from "+x0+" to "+x1);
            assertEquals(RailGates.TALL,(int)Math.round((y1-y0)/16),kind.id+" height");assertEquals(RailGates.DEEP,(int)Math.ceil((z1-z0)/16));
            assertTrue(y0>=-16&&y1<=32&&x0>=-16&&x1<=32&&z0>=0&&z1<=16,"inside the 3 x 3 cells around the anchor");
        }
        assertEquals(7,RailGates.Frame.CELLS.length,"seven parts around the anchor; the rail cell is not the gate's");
        for(int[] f:RailGates.Frame.CELLS)assertFalse(f[0]==0&&f[1]<=0,"no part in the opening");
    }
    @Test void theLampsAreSwitchedByAnOverlayAndTheOwnersFilesAreUnchanged() throws Exception{
        var art=Path.of("../../docs/art/Create-Arsenal-Supply-Rail-Gates");
        for(var kind:RailGates.Kind.values()){
            assertEquals(sha(art.resolve("minecraft-models/block/"+kind.id+".json")),sha(Path.of(ASSETS+"models/block/"+kind.id+".json")),kind.id+" model unchanged");
            assertEquals(sha(art.resolve("textures/block/"+kind.id+".png")),sha(Path.of(ASSETS+"textures/block/"+kind.id+".png")),kind.id+" texture unchanged");
            String state=Files.readString(Path.of(ASSETS+"blockstates/"+kind.id+".json"));
            for(String s:List.of("red","amber","green"))assertTrue(state.contains("supply_gate_signal_"+s)&&state.contains("\"signal\": \""+s+"\""),kind.id+" shows the "+s+" light");
            String model=Files.readString(Path.of(ASSETS+"models/block/"+kind.id+".json"));
            for(String lamp:List.of("Signal_lamp_0","Signal_lamp_1","Signal_lamp_2"))assertTrue(model.contains(lamp),"the owner's lamp "+lamp+" is where the overlay draws");
        }
        assertTrue(Files.exists(Path.of(ASSETS+"textures/block/supply_gate_signal.png")),"the overlay has its own new texture");
    }
    private static String sha(Path p) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    @Test void thePricesAndTheGuideAgree(){
        assertTrue(WorkshopFabrication.ITEMS.contains("supply_receiving_gate"),"the Receiving Gate is sold at home");
        assertFalse(WorkshopFabrication.ITEMS.contains("supply_delivery_gate"),"the Delivery Gate only at a FOB");
        assertEquals(16,Economy.GATE_IRON);assertEquals(8,Economy.GATE_COPPER);assertEquals(8,Economy.GATE_REDSTONE);assertEquals(1,Economy.GATE_LOGISTICS);assertEquals(4,Economy.GATE_ARDENT);
    }
}
