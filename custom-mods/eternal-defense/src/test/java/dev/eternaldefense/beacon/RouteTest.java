package dev.eternaldefense.beacon;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.eternaldefense.beacon.RouteRules.*;

/** The supply rail (0.0.24, FOB slice 2) without a world: passes, rides, the scheduler, the clock, the light, the numbers, the data and the models. */
final class RouteTest {
    static final String ASSETS="src/main/resources/assets/eternal_defense/";

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

    // ---- the tribute (0.0.31): four loot slots; 0.0.32: by the hour, research, the 30 minute promise ---------------------------------------------
    static final boolean[] ALL={true,true,true,true};
    static Yield y(boolean[] on,double distance,int villagers,double speed,int level){return new Yield(on,distance,villagers,period(Math.round(distance/speed)),level);}
    /** Tribute value an hour of play, every slot together, for a route of this length ridden at this speed (blocks a tick). */
    static double valuePerHour(double distance,int villagers,double speed,int level){
        var yi=y(ALL,distance,villagers,speed,level);double sum=0;for(var slot:Slot.values())sum+=budget(slot,yi);
        return sum*tripsPerHour(yi.period());
    }
    /** The owner's floor (0.0.32): even the slowest FOB, with S open, makes an S-tier item every 30 minutes of play, whatever the rail. */
    @Test void theSlowestFobMakesAnSTierItemEvery30Minutes(){
        for(double speed:new double[]{.05,.1,.4,.57}){
            var slowest=y(ALL,MIN_SEPARATION,MIN_VILLAGERS,speed,UNLOCK_S);
            double perHalfHour=budget(Slot.SPECIALTY,slowest)*tripsPerHour(slowest.period())*S_HOURS;
            assertTrue(perHalfHour>=TIER_VALUE[5]-1e-9,"at "+speed+" blocks a tick: "+perHalfHour+" value in 30 minutes");
        }
        assertEquals(TIER_VALUE[5],budget(Slot.SPECIALTY,y(ALL,MIN_SEPARATION,3,.4,UNLOCK_S))*6,1e-9,"six deliveries of 5 minutes: exactly one");
        // every S item of every list is within it: none is worth more than an S
        for(var spec:Specialty.values())for(var l:spec.options()){
            assertTrue(l.value()<=TIER_VALUE[5]+1e-9,l.id()+" is worth "+l.value());
            if(l.tier()==5)assertEquals(UNLOCK_S,unlockLevel(l));
        }
        // and the other slots at that size: 16 value an hour each before research
        assertEquals(SLOWEST_PER_HOUR/4,budget(Slot.FARM,y(ALL,MIN_SEPARATION,3,.4,0))*12,1e-9);
    }
    @Test void theNumbersFollowTheTuningPrinciples(){
        double fullSpeed=REF_SPEED;
        assertEquals(64,perHour(MIN_SEPARATION,3),1e-9,"the slowest FOB");
        assertEquals(128,perHour(MIN_SEPARATION,20),1e-9,"twenty villagers make twice three");
        assertEquals(perHour(1000,20),perHour(1000,60),1e-9,"only twenty count");
        assertEquals(256,perHour(1000,20),1e-9,"and a 1 km line twice again");
        assertEquals(1.2*256,perHour(5000,20),1e-9,"+20 percent at 5 km: the best is 4.8 times the slowest");
        assertEquals(perHour(5000,20),perHour(40_000,20),1e-9);
        assertEquals(64/3.0,perHour(MIN_SEPARATION,1),1e-9,"fewer than 3 (after losses) make less: a third each");assertEquals(0,perHour(1000,0),1e-9);
        // a vanilla cart gets the promised value an hour; a faster rail more (the same trips, more often); a slower one never less
        for(int d=MIN_SEPARATION;d<=10_000;d+=211){
            assertEquals(perHour(d,10),valuePerHour(d,10,fullSpeed,0),1e-6,"at "+d);
            assertTrue(valuePerHour(d,10,fullSpeed/4,0)>=perHour(d,10)-1e-6,"a slow rail at "+d);
            assertTrue(valuePerHour(d,10,MAX_SPEED,0)>=perHour(d,10)-1e-6);
        }
        assertTrue(valuePerHour(2000,10,MAX_SPEED,0)>valuePerHour(2000,10,fullSpeed,0),"a faster rail pays on a long line");
        double last=0;for(int d=MIN_SEPARATION;d<=10_000;d+=97){double p=perHour(d,10);assertTrue(p>=last-1e-9,"never worse farther out");last=p;}
        assertEquals(0,distanceBonus(1000),1e-9);assertEquals(.10,distanceBonus(3000),1e-9);assertEquals(.20,distanceBonus(5000),1e-9);
        // riding back to back: 2.5 times passive
        long t=Math.round(1000/fullSpeed);double ridden=rideTrips(2*t,period(t))*HOUR/(2*t),passive=tripsPerHour(period(t));
        assertEquals(ACTIVE_FACTOR,rideTrips(2*t,period(t)),1e-9);assertEquals(2.5,ridden/passive,1e-9);
    }
    /** 0.0.30's stockpile capped what rides could take; without it a ride is capped by time: a short loop ridden over and over makes nothing extra. */
    @Test void ridingNeverPaysMoreThan2Point5TimesPassive(){
        for(int d=MIN_SEPARATION;d<=20_000;d+=137)for(double speed:new double[]{.1,.4,.57}){
            long t=Math.round(d/speed),p=period(t);
            for(long lap=2*t;lap<=2*t+12_000;lap+=1_500){
                double perHour=rideTrips(lap,p)*HOUR/lap;
                assertTrue(perHour<=RIDE_BOOST*tripsPerHour(p)+1e-9,"d="+d+" speed="+speed+" lap="+lap+": "+perHour+" trips an hour");
            }
        }
        assertEquals(0,rideTrips(0,6000),1e-9);assertEquals(1,rideTrips(2400,6000),1e-9);assertEquals(ACTIVE_FACTOR,rideTrips(1_000_000,6000),1e-9);
        assertEquals(4,EMERALD_MIN*tripsPerHour(MIN_PERIOD),1e-9,"the emerald minimum: at most 4 an hour by itself");
    }
    @Test void villageResearchOpensTiersAndGrowsTheSlots(){
        assertEquals(List.of(0,16,48,96,160,240),Arrays.stream(RESEARCH_XP).boxed().toList());
        assertEquals(0,level(15));assertEquals(1,level(16));assertEquals(2,level(48));assertEquals(4,level(160));assertEquals(5,level(240));assertEquals(5,level(9999));
        assertEquals(32,toNext(16));assertEquals(0,toNext(240));
        assertEquals(1,XP_EMERALD);assertEquals(4,XP_DIAMOND);assertEquals(2,XP_ARDENT);
        assertEquals(1.0,slotMultiplier(Slot.SPECIALTY,0),1e-9);assertEquals(2.0,slotMultiplier(Slot.SPECIALTY,4),1e-9);assertEquals(2.25,slotMultiplier(Slot.SPECIALTY,5),1e-9);
        assertEquals(1.25,slotMultiplier(Slot.EMERALD,5),1e-9);assertEquals(1.10,slotMultiplier(Slot.MINE,2),1e-9);assertEquals(1.05,slotMultiplier(Slot.FARM,1),1e-9);
        var tear=POTION_LOOT.stream().filter(l->l.id().equals("minecraft:ghast_tear")).findFirst().orElseThrow();
        var powder=POTION_LOOT.stream().filter(l->l.id().equals("minecraft:blaze_powder")).findFirst().orElseThrow();
        assertFalse(unlocked(tear,3));assertTrue(unlocked(tear,4));assertFalse(unlocked(powder,1));assertTrue(unlocked(powder,2));
        assertTrue(unlocked(POTION_LOOT.get(0),0),"D, C and B are always open");
        // a choice research has not opened yet sends the slot's first open item, and the first item of every list is open
        int tearIndex=POTION_LOOT.indexOf(tear);
        assertEquals(0,effectiveChoice(Slot.SPECIALTY,Specialty.POTIONS,tearIndex,0));assertEquals(tearIndex,effectiveChoice(Slot.SPECIALTY,Specialty.POTIONS,tearIndex,4));
        for(var spec:Specialty.values())assertTrue(unlocked(spec.options().get(0),0),spec+": its first item is open at level 0");
        // research makes every slot bigger: the specialty by a quarter a level, the rest by a twentieth
        var at0=y(ALL,1000,10,.4,0);var at5=y(ALL,1000,10,.4,5);
        // (on a small FOB: a big one's specialty reaches the 0.0.34 cap of 4 S-tier items an hour, Round0034Test)
        assertEquals(2.25,budget(Slot.SPECIALTY,y(ALL,MIN_SEPARATION,3,.4,5))/budget(Slot.SPECIALTY,y(ALL,MIN_SEPARATION,3,.4,0)),1e-9);assertEquals(1.25,budget(Slot.FARM,at5)/budget(Slot.FARM,at0),1e-9);
    }
    @Test void theSlotsThatAreOnShareTheTrip(){
        boolean[] farmOnly={false,false,true,false},two={false,true,true,false};
        double share=budget(Slot.FARM,y(ALL,1000,10,.4,0));
        assertEquals(perHour(1000,10)/4*period(2500)/(double)HOUR,share,1e-9,"all four on: a quarter of the trip each");
        assertEquals(4*share,budget(Slot.FARM,y(farmOnly,1000,10,.4,0)),1e-9,"the farm alone gets the whole trip");
        assertEquals(2*share,budget(Slot.MINE,y(two,1000,10,.4,0)),1e-9,"two on: half each");
        assertEquals(0,budget(Slot.EMERALD,y(farmOnly,1000,10,.4,0)),1e-9,"a slot that is off gets nothing");
        assertFalse(mayToggle(farmOnly,Slot.FARM));assertTrue(mayToggle(farmOnly,Slot.MINE));assertTrue(mayToggle(ALL,Slot.EMERALD));
        // every village sends emeralds: at least one every three deliveries; the slowest FOB makes exactly that
        assertEquals(EMERALD_MIN,itemsPerTrip(Slot.EMERALD,EMERALD,y(ALL,MIN_SEPARATION,3,.4,0)),1e-9);
        assertEquals(EMERALD_MIN,itemsPerTrip(Slot.EMERALD,EMERALD,y(ALL,MIN_SEPARATION,1,.4,0)),1e-9,"the minimum holds with fewer villagers");
        assertTrue(itemsPerTrip(Slot.EMERALD,EMERALD,y(new boolean[]{true,false,false,false},5000,20,.4,0))>1);
    }
    @Test void aTripPaysWholeItemsAndKeepsTheRest(){
        var rnd=new Random(11);
        for(int run=0;run<500;run++){
            boolean[] on=new boolean[4];for(int i=0;i<4;i++)on[i]=rnd.nextBoolean();if(enabled(on)==0)on[rnd.nextInt(4)]=true;
            var spec=Specialty.values()[rnd.nextInt(Specialty.values().length)];
            Loot[] loot={EMERALD,MINE_LOOT.get(rnd.nextInt(MINE_LOOT.size())),FARM_LOOT.get(rnd.nextInt(FARM_LOOT.size())),spec.options().get(rnd.nextInt(spec.options().size()))};
            var yi=y(on,MIN_SEPARATION+rnd.nextInt(8000),rnd.nextInt(25),.1+rnd.nextDouble()*.47,rnd.nextInt(6));double trips=rnd.nextDouble()*2;
            double[] tally=new double[4];for(int i=0;i<4;i++)tally[i]=rnd.nextDouble()*loot[i].value();
            double[] before=tally.clone();int[] got=trip(tally,loot,yi,trips);
            for(int i=0;i<4;i++){
                double added=trips*perTrip(Slot.values()[i],yi);
                assertEquals(before[i]+added,got[i]*loot[i].value()+tally[i],1e-6,"value is never lost or made: slot "+i);
                assertTrue(tally[i]>=-1e-9&&tally[i]<loot[i].value()+1e-9,"only a part of one item stays in the tally");
                if(!on[i])assertEquals(0,got[i]);
            }
        }
    }
    static Loot find(List<Loot> list,String id){return list.stream().filter(l->l.id().equals("minecraft:"+id)).findFirst().orElseThrow(()->new AssertionError(id));}
    @Test void theLootListsAreTheOwnersLists(){
        // the owner's mine (0.0.31), then 0.0.43's clay and decorative stones, at the end so saved choices keep their item
        assertEquals(List.of("minecraft:redstone","minecraft:iron_ingot","minecraft:andesite","minecraft:flint","minecraft:gunpowder","minecraft:charcoal",
            "minecraft:clay","minecraft:calcite","minecraft:dripstone_block","minecraft:red_sandstone","minecraft:blackstone",
            "create:asurine","create:crimsite","create:ochrum","create:veridium","create:limestone","create:scoria","create:scorchia"),MINE_LOOT.stream().map(Loot::id).toList());
        assertTrue(FARM_LOOT.stream().map(Loot::id).toList().containsAll(List.of("minecraft:wheat","minecraft:carrot","minecraft:potato","minecraft:beetroot","minecraft:melon_slice","minecraft:pumpkin","minecraft:apple","minecraft:sweet_berries","minecraft:glow_berries","minecraft:cocoa_beans")));
        assertTrue(FISHING_LOOT.stream().map(Loot::id).toList().containsAll(List.of("minecraft:cod","minecraft:salmon","minecraft:tropical_fish","minecraft:pufferfish")));
        assertTrue(HUSBANDRY_LOOT.stream().map(Loot::id).toList().containsAll(List.of("minecraft:beef","minecraft:porkchop","minecraft:chicken","minecraft:mutton","minecraft:rabbit","minecraft:leather","minecraft:rabbit_hide")));
        // potion makers: the owner's tiers (0.0.32: dragon's breath to S), each tier twice the one below
        assertEquals(17,POTION_LOOT.size());
        for(var l:POTION_LOOT)assertEquals(TIER_VALUE[l.tier()],l.value(),1e-9);
        for(String s:List.of("ghast_tear","rabbit_foot","turtle_helmet","dragon_breath"))assertEquals(5,find(POTION_LOOT,s).tier(),s+": S");
        for(String a:List.of("phantom_membrane","pufferfish","blaze_powder"))assertEquals(4,find(POTION_LOOT,a).tier(),a+": A");
        assertEquals(1,find(POTION_LOOT,"sugar").tier());assertEquals(16,TIER_VALUE[5]/TIER_VALUE[1],1e-9);
        // the monster hunter guild (0.0.32): monster drops, no boss drop; ender pearls and blaze rods for eyes of ender
        for(var l:MONSTER_LOOT)assertEquals(TIER_VALUE[l.tier()],l.value(),1e-9);
        assertEquals(4,find(MONSTER_LOOT,"ender_pearl").tier());assertEquals(4,find(MONSTER_LOOT,"blaze_rod").tier());
        for(String boss:List.of("nether_star","dragon_egg","elytra","wet_sponge","dragon_head"))assertTrue(MONSTER_LOOT.stream().noneMatch(l->l.id().equals("minecraft:"+boss)),boss);
        // an item two lists share has one tier
        for(var m:MONSTER_LOOT)for(var p:POTION_LOOT)if(m.id().equals(p.id()))assertEquals(p.tier(),m.tier(),m.id());
        // the wandering trader: everything it sells in 1.20.1, by price; the endgame goods locked
        assertEquals(70,TRADER_LOOT.size());
        assertEquals(5*TRADER_PER_EMERALD,find(TRADER_LOOT,"oak_sapling").value(),1e-9);assertEquals(TRADER_PER_EMERALD/8,find(TRADER_LOOT,"sand").value(),1e-9);
        assertEquals(TIER_VALUE[5],find(TRADER_LOOT,"blue_ice").value(),1e-9,"the dearest trade is an S");
        assertEquals(5,find(TRADER_LOOT,"nautilus_shell").tier());assertEquals(4,find(TRADER_LOOT,"slime_ball").tier());assertEquals(0,find(TRADER_LOOT,"oak_sapling").tier());
        assertEquals(6,find(TRADER_LOOT,"blue_ice").price());
        assertEquals(4,Specialty.values().length);   // 0.0.34: fishing and animal husbandry are one
        for(var spec:Specialty.values())assertFalse(spec.options().isEmpty());
        for(var list:List.of(MINE_LOOT,FARM_LOOT,FISHING_LOOT,HUSBANDRY_LOOT,POTION_LOOT,TRADER_LOOT,MONSTER_LOOT))for(var l:list)assertFalse(l.id().contains("eternal_defense")||l.id().contains("coin"),"no currency of the mod: "+l.id());
    }
    /**
     * The tribute items the Exchange buys (they turn into Ardent Energy). Listed here so a change to either list is a decision. 0.0.32: an
     * emerald is worth 4 and an S item 16, so emeralds and ghast tears earned the same Ardent Energy at the (discontinued) standalone Exchange.
     */
    @Test void theExchangeBuysOnlyTheseTributeItems(){
        var all=new java.util.TreeSet<String>();for(var list:List.of(EMERALD_LOOT,MINE_LOOT,FARM_LOOT,FISHING_LOOT,HUSBANDRY_LOOT,POTION_LOOT,TRADER_LOOT,MONSTER_LOOT))for(var l:list)all.add(l.id());
        for(String list:List.of(ExchangeShop.PACK)){
            String sell=list.substring(list.indexOf("[sell]"),list.indexOf("[buy]"));var bought=new java.util.TreeSet<String>();
            for(String id:all)if(sell.contains(id+" ")||sell.contains(id+"\n"))bought.add(id);
            assertEquals(new java.util.TreeSet<String>(),bought,"tribute goes to the Receiving Gate; the Exchange buys none of it");
        }
        // the values were set against the standalone Exchange's prices (1 Ardent Energy for 2 emeralds, 2 for a ghast tear, 1 for a blaze rod or an ender pearl)
        double perValueEmerald=0.5/EMERALD_VALUE,perValueTear=2/TIER_VALUE[5],perValueA=1/TIER_VALUE[4];
        assertEquals(perValueEmerald,perValueTear,1e-9,"emeralds and ghast tears earn the same an hour");
        assertEquals(perValueEmerald,perValueA,1e-9,"and so do blaze rods and ender pearls");
        // a shulker shell (S, the monster hunter guild's) sells for 4: twice the others per value, an open question for the owner (HANDOFF)
        assertEquals(2*perValueEmerald,4/TIER_VALUE[5],1e-9);
    }

    // ---- the saved data --------------------------------------------------------------------------------------------------------------------------
    @Test void routesSaveAndLoad(){
        var d=new FobData();var f=d.add(new BlockPos(0,64,0),1L,0L);d.onlineTicks=123_456;
        f.deliveryGate=new BlockPos(5,64,5);f.receivingGate=new BlockPos(-900,70,12);f.commissioned=true;f.baseline=3210;f.clock=99_000;f.nextPassive=130_000;
        f.workers=9;f.note.put("minecraft:flint",3);f.lastKind="ride";f.lastItems=10;f.lastAt=120_000;f.lastTrip=119_000;
        f.slotOn[0]=false;f.mine=2;f.farm=5;f.special=3;f.specialty=Specialty.POTIONS;f.tally[1]=.75;f.tally[3]=2.5;f.zoneColumns=new long[]{7,9};f.researchXp=97;
        f.stockpile.putString("future","kept");
        var back=FobData.load(d.save(new CompoundTag())).byId(f.id);
        assertEquals(new BlockPos(5,64,5),back.deliveryGate);assertEquals(new BlockPos(-900,70,12),back.receivingGate);
        assertTrue(back.commissioned);assertEquals(3210,back.baseline);assertEquals(99_000,back.clock);assertEquals(130_000,back.nextPassive);
        assertEquals(9,back.workers);assertEquals(3,back.note.get("minecraft:flint"));assertEquals("ride",back.lastKind);assertEquals(10,back.lastItems);assertEquals(120_000,back.lastAt);assertEquals(119_000,back.lastTrip);
        assertFalse(back.slotOn[0]);assertTrue(back.slotOn[1]&&back.slotOn[2]&&back.slotOn[3]);
        assertEquals(2,back.mine);assertEquals(5,back.farm);assertEquals(3,back.special);assertEquals(Specialty.POTIONS,back.specialty);
        assertEquals(.75,back.tally[1],1e-12);assertEquals(2.5,back.tally[3],1e-12);assertArrayEquals(new long[]{7,9},back.zoneColumns);
        assertEquals(97,back.researchXp);assertEquals(3,back.level());
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
        var art=Path.of("../../docs/art/Eternal-Defense-Supply-Rail-Gates-4wide");
        for(var kind:RailGates.Kind.values()){
            for(String half:List.of("_right","_left")){
                assertEquals(sha(art.resolve("minecraft-models/block/"+kind.id+half+".json")),sha(Path.of(ASSETS+"models/block/"+kind.id+half+".json")),kind.id+half+" model unchanged");
                assertEquals(sha(art.resolve("textures/block/"+kind.id+half+".png")),sha(Path.of(ASSETS+"textures/block/"+kind.id+half+".png")),kind.id+half+" texture unchanged");
                String state=Files.readString(Path.of(ASSETS+"blockstates/"+kind.id+(half.equals("_right")?"":"_left")+".json"));
                assertTrue(state.contains("eternal_defense:block/"+kind.id+half+"\""),kind.id+half+" is drawn by its block");
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
        assertEquals(16,Economy.GATE_IRON);assertEquals(8,Economy.GATE_COPPER);assertEquals(8,Economy.GATE_REDSTONE);assertEquals(1,Economy.GATE_LOGISTICS);
    }
}
