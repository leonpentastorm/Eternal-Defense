package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.eternaldefense.beacon.SosRules.*;

/** Calls for help, DOWN and repairs (0.0.25, FOB slice 3) without a world: the SOS clock, the answer, the state machine, the prices, the data. */
final class SosTest {
    static final String SRC="src/main/java/dev/eternaldefense/beacon/";

    // ---- the SOS clock ----------------------------------------------------------------------------------------------------------------------------
    @Test void theSosComesTenToTwelveHoursOfPlayApart(){
        assertEquals(720_000L,SOS_MIN,"10 hours of play (72,000 ticks an hour)");assertEquals(864_000L,SOS_MAX,"12 hours of play");
        assertEquals(SOS_MIN,delay(0),"the earliest");
        assertEquals(SOS_MAX,delay(Math.nextDown(1.0)),"the latest");
        assertEquals(SOS_MAX,delay(1.0),"never later, whatever the random number");assertEquals(SOS_MIN,delay(-3),"never earlier");
        var rnd=new Random(7);long low=Long.MAX_VALUE,high=0;double sum=0;
        for(int i=0;i<20_000;i++){long d=delay(rnd.nextDouble());assertTrue(d>=SOS_MIN&&d<=SOS_MAX,"in range: "+d);low=Math.min(low,d);high=Math.max(high,d);sum+=d;}
        assertTrue(low<SOS_MIN+2_000&&high>SOS_MAX-2_000,"the whole range is used");
        assertEquals((SOS_MIN+SOS_MAX)/2.0,sum/20_000,2_000,"evenly spread");
    }
    @Test void theClockPausesWhenNobodyCouldAnswer(){
        assertTrue(counts(1,false,false,false),"one ACTIVE FOB, nothing else going on: it counts");
        assertFalse(counts(0,false,false,false),"no ACTIVE FOB: paused");
        assertFalse(counts(3,true,false,false),"an SOS is open: no second one");
        assertFalse(counts(3,false,true,false),"a Home raid runs: paused");
        assertFalse(counts(3,false,false,true),"the Home raid's warning day: paused");
        assertEquals(24_000,TacticalRules.WARNING_TICKS,"the same warning day that locks Tactical Operations");
        // it counts ticks of play: FobData.onlineTicks grows only while a player is online (Routes.second runs only then)
        assertTrue(source("Routes.java").contains("d.onlineTicks+=20")&&source("Fobs.java").contains("getPlayerCount()==0)return"),"only time with a player online counts");
        assertTrue(source("FobSos.java").contains("d.sosClock=Math.max(0,d.sosClock-20)"),"the SOS clock counts down by the second, in ticks");
    }
    @Test void theCallPicksAnActiveFobAtRandom(){
        assertEquals(-1,pick(0,.5),"none to pick");
        assertEquals(0,pick(1,.99),"the only one");
        int[] seen=new int[5];var rnd=new Random(3);for(int i=0;i<5_000;i++)seen[pick(5,rnd.nextDouble())]++;
        for(int n:seen)assertTrue(n>800&&n<1_200,"each of five equally often: "+Arrays.toString(seen));
        assertEquals(4,pick(5,1.0),"never out of range");assertEquals(0,pick(5,-1),"never out of range");
    }

    // ---- the answer -------------------------------------------------------------------------------------------------------------------------------
    @Test void theAnswerHasSevenDaysAndTheHomeRaidComesFirst(){
        assertEquals(168_000L,RESPONSE_TICKS,"7 days of play");
        assertTrue(responseCounts(false,false),"it runs down");
        assertFalse(responseCounts(true,false),"not while the Home raid runs");
        assertFalse(responseCounts(false,true),"not while the FOB's own raid decides it");
        assertEquals(Arrival.START,arrival(false,false,true),"a player in the village starts the raid");
        assertEquals(Arrival.HOME_RAID,arrival(true,false,true),"refused (and told) while the Home raid runs");
        assertEquals(Arrival.RAID_RUNNING,arrival(true,true,true),"a raid already runs: nothing more");
        assertEquals(Arrival.NO_RAIDS,arrival(false,false,false),"Peaceful or raids off");
        assertEquals(0,OMEN_AMPLIFIER,"Bad Omen I, as the activation raid");
        assertTrue(source("FobSos.java").contains("p.removeEffect(MobEffects.BAD_OMEN);"),"a Bad Omen the player carries is replaced, never stacked");
    }
    @Test void theStateMachinePlantedActiveDown(){
        // slice 1: the activation raid
        assertEquals(FobRules.State.ACTIVE,FobRules.after(FobRules.State.PLANTED,FobRules.Outcome.WON));
        assertEquals(FobRules.State.DOWN,FobRules.after(FobRules.State.PLANTED,FobRules.Outcome.LOST));
        // slice 3: the SOS
        assertEquals(FobRules.State.ACTIVE,after(FobRules.State.ACTIVE,End.WON),"an SOS won: it stays ACTIVE");
        assertEquals(FobRules.State.DOWN,after(FobRules.State.ACTIVE,End.LOST),"lost: DOWN");
        assertEquals(FobRules.State.DOWN,after(FobRules.State.ACTIVE,End.EXPIRED),"nobody came: DOWN");
        assertEquals(FobRules.State.ACTIVE,after(FobRules.State.ACTIVE,End.REMOVED),"called off: as it was");
        // the repair
        assertEquals(FobRules.State.ACTIVE,repaired(FobRules.State.DOWN,true),"a FOB that ever won is ACTIVE again");
        assertEquals(FobRules.State.PLANTED,repaired(FobRules.State.DOWN,false),"one that never won is PLANTED: its raid again, no shortcut");
        assertEquals(FobRules.State.ACTIVE,repaired(FobRules.State.ACTIVE,true),"bringing villagers back changes no state");
        assertTrue(source("FobSos.java").contains("if(f.state==FobRules.State.PLANTED){String problem=Fobs.callRaid(l,f,p);"),"and the activation raid is called again at once");
        assertTrue(mayRepair(FobRules.State.DOWN,0,false),"DOWN with nobody lost: the base price");
        assertTrue(mayRepair(FobRules.State.ACTIVE,2,false),"villagers lost in a won raid can be brought back");
        assertFalse(mayRepair(FobRules.State.ACTIVE,0,false),"nothing to pay for");
        assertFalse(mayRepair(FobRules.State.DOWN,3,true),"never while a raid runs");
        // DOWN stops what only an ACTIVE FOB does (slices 1 and 2) and pauses the route's clock
        assertFalse(FobRules.working(FobRules.State.DOWN));
        assertFalse(RouteRules.passive(FobRules.working(FobRules.State.DOWN),true,1000,false),"no tribute while DOWN");
        assertEquals(RouteRules.Signal.RED,RouteRules.signal(true,true,false,true,1000,false,false),"the gates are red");
        assertTrue(source("Routes.java").contains("if(active&&f.commissioned&&f.clock>0)"),"the inspection clock only runs while ACTIVE");
    }

    // ---- the repair ----------------------------------------------------------------------------------------------------------------------------
    @Test void theRepairIsTheBasePlusEachVillager(){
        int[] table={1,2,3,4,5};
        assertEquals(1,villagerCost(table,1),"Novice");assertEquals(5,villagerCost(table,5),"Master");
        assertEquals(1,villagerCost(table,0),"no level reads as Novice");assertEquals(5,villagerCost(table,9),"never above Master");
        assertEquals(6+1+3+5,repairCost(true,6,new int[]{1,3,5}),"DOWN: base plus each villager");
        assertEquals(6,repairCost(true,6,new int[0]),"DOWN with nobody lost: the base");
        assertEquals(1+3+5,repairCost(false,6,new int[]{1,3,5}),"ACTIVE: only the villagers");
        for(int n=0;n<=20;n++){int[] each=new int[n];Arrays.fill(each,2);assertEquals(6+2*n,repairCost(true,6,each),"the total is the sum");}
        // the numbers live in Economy (0.0.41: the pack's; the discontinued standalone edition asked 6 and 1 to 5)
        assertEquals(3,Economy.FOB_REPAIR_BASE,"base 3");assertArrayEquals(new int[]{1,1,2,2,3},Economy.FOB_REPAIR_VILLAGER,"per villager by level");
        assertEquals(64,MAX_RECORDS,"more lost villagers than any village holds are not kept");
    }
    @Test void lostVillagersHaveStableNames(){
        var a=UUID.fromString("0b6e0e1c-7f7e-4f8e-9a1a-3a1b2c3d4e5f");
        assertEquals(villagerName(a.getMostSignificantBits(),a.getLeastSignificantBits()),villagerName(a.getMostSignificantBits(),a.getLeastSignificantBits()),"the same villager, the same name");
        var names=new HashSet<String>();var rnd=new Random(11);
        for(int i=0;i<2_000;i++)names.add(villagerName(rnd.nextLong(),rnd.nextLong()));
        assertEquals(NAMES.length,names.size(),"every name is used");
        assertEquals(NAMES.length,new HashSet<>(List.of(NAMES)).size(),"no name twice in the list");
    }
    @Test void theSosAndTheLostVillagersAreSaved(){
        var d=new FobData();d.onlineTicks=5_000;d.sosClock=123_456;d.sosFob=0;d.sosLeft=0;d.sosSerial=4;
        var f=d.add(new BlockPos(100,64,-20),7,0);f.state=FobRules.State.DOWN;f.activated=true;
        var r=new CompoundTag();r.putString("name","Hilda");r.putString("profession","minecraft:farmer");r.putInt("level",3);r.put("data",new CompoundTag());f.deadVillagers.add(r);
        var g=d.add(new BlockPos(900,70,40),8,0);g.state=FobRules.State.ACTIVE;
        d.sosFob=g.id;d.sosLeft=99_000;
        var back=FobData.load(d.save(new CompoundTag()));
        assertEquals(123_456,back.sosClock);assertEquals(g.id,back.sosFob);assertEquals(99_000,back.sosLeft);assertEquals(4,back.sosSerial);
        var f2=back.byId(f.id);assertEquals(FobRules.State.DOWN,f2.state);assertEquals(1,f2.deadVillagers.size());
        assertEquals("Hilda",f2.deadVillagers.getCompound(0).getString("name"));assertEquals(3,f2.deadVillagers.getCompound(0).getInt("level"));
        // a broken or old save: no SOS fields at all, or an SOS pointing at a FOB that is gone
        var old=new CompoundTag();var empty=FobData.load(old);assertEquals(0,empty.sosFob);assertEquals(0,empty.sosClock);
        var gone=d.save(new CompoundTag());gone.putLong("sosFob",999);assertEquals(0,FobData.load(gone).sosFob,"an SOS of a FOB that is gone is dropped on load");
    }

    static String source(String name){try{return Files.readString(Path.of(SRC+name));}catch(Exception e){throw new RuntimeException(e);}}
}
