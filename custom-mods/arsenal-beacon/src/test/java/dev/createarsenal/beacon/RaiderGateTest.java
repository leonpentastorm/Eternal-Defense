package dev.createarsenal.beacon;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.createarsenal.beacon.TerrainProbe.Hazard.*;
import static dev.createarsenal.beacon.TerrainProbe.UNKNOWN;
import static org.junit.jupiter.api.Assertions.*;

/** The pure decisions behind raider gates: the terrain probe, the channel clock, damage delay, which gate a raider joins. */
final class RaiderGateTest {
    private static final long S=RaidMarch.TICKS_PER_SECOND;
    private static int[] flat(int n,int y){int[] s=new int[n];Arrays.fill(s,y);return s;}

    // ---- the probe (look-ahead and destination check share it) -----------------------------------------------------------------------
    @Test void flatDryGroundIsClear(){
        assertEquals(NONE,TerrainProbe.probe(flat(11,64),new boolean[11],new boolean[11],1));
    }
    @Test void lavaAndMagmaAlwaysBlock(){
        var lava=new boolean[11];lava[7]=true;
        assertEquals(LAVA,TerrainProbe.probe(flat(11,64),new boolean[11],lava,1));
        lava=new boolean[11];lava[1]=true;assertEquals(LAVA,TerrainProbe.probe(flat(11,64),new boolean[11],lava,1),"even a single lava column");
    }
    @Test void aDropOfSixBlocksBlocksButFiveDoesNot(){
        int[] six=flat(11,70);for(int i=4;i<11;i++)six[i]=64; // 70 -> 64 between samples 3 and 4
        assertEquals(DROP,TerrainProbe.probe(six,new boolean[11],new boolean[11],1));
        int[] five=flat(11,70);for(int i=4;i<11;i++)five[i]=65;
        assertEquals(NONE,TerrainProbe.probe(five,new boolean[11],new boolean[11],1));
        assertEquals(6,RaidMarch.GATE_DROP_BLOCKS);
    }
    @Test void aLongGentleSlopeIsNotAChasm(){
        int[] slope=new int[11];for(int i=0;i<11;i++)slope[i]=70-i; // 10 blocks down over 10 blocks: walkable
        assertEquals(NONE,TerrainProbe.probe(slope,new boolean[11],new boolean[11],1));
    }
    @Test void aRiseIsNeverADropAndAClimbIsLeftToDigging(){
        int[] wall=flat(11,64);for(int i=5;i<11;i++)wall[i]=75;
        assertEquals(NONE,TerrainProbe.probe(wall,new boolean[11],new boolean[11],1),"only the way down counts");
    }
    @Test void eightWideFluidBlocksButAThreeWidePondDoesNot(){
        var fluid=new boolean[11];for(int i=2;i<10;i++)fluid[i]=true; // 8 in a row
        assertEquals(FLUID,TerrainProbe.probe(flat(11,64),fluid,new boolean[11],1));
        fluid=new boolean[11];for(int i=2;i<9;i++)fluid[i]=true; // 7 in a row
        assertEquals(NONE,TerrainProbe.probe(flat(11,64),fluid,new boolean[11],1),"seven blocks of water can be waded");
        fluid=new boolean[11];for(int i=4;i<7;i++)fluid[i]=true; // a 3-wide pond
        assertEquals(NONE,TerrainProbe.probe(flat(11,64),fluid,new boolean[11],1),"a short pond must not trigger");
        assertEquals(8,RaidMarch.GATE_FLUID_SPAN);
    }
    @Test void twoShortPondsSeparatedByLandAreNotOneLake(){
        var fluid=new boolean[11];for(int i=1;i<5;i++)fluid[i]=true;for(int i=6;i<10;i++)fluid[i]=true; // 4 + land + 4
        assertEquals(NONE,TerrainProbe.probe(flat(11,64),fluid,new boolean[11],1));
    }
    @Test void unknownSamplesNeverBlockAndBreakARunOfWater(){
        int[] surface=flat(11,64);var fluid=new boolean[11];for(int i=1;i<11;i++)fluid[i]=true;
        surface[5]=UNKNOWN; // 4 wet, unknown, 5 wet
        assertEquals(NONE,TerrainProbe.probe(surface,fluid,new boolean[11],1),"an unloaded column is not water");
        int[] cliff=flat(11,70);cliff[4]=UNKNOWN;for(int i=5;i<11;i++)cliff[i]=40;
        assertEquals(NONE,TerrainProbe.probe(cliff,new boolean[11],new boolean[11],1),"a drop next to an unknown sample is not judged");
        var lava=new boolean[11];lava[3]=true;int[] unknownLava=flat(11,64);unknownLava[3]=UNKNOWN;
        assertEquals(NONE,TerrainProbe.probe(unknownLava,new boolean[11],lava,1),"lava in an unread column is not known");
        int[] all=new int[11];Arrays.fill(all,UNKNOWN);assertEquals(NONE,TerrainProbe.probe(all,new boolean[11],new boolean[11],1));
    }
    @Test void theFluidSpanIsMeasuredInBlocksWhateverTheSpacing(){
        assertEquals(0,TerrainProbe.spanOf(0,2));assertEquals(1,TerrainProbe.spanOf(1,2));assertEquals(3,TerrainProbe.spanOf(2,2));assertEquals(9,TerrainProbe.spanOf(5,2));
        var fluid=new boolean[10];for(int i=2;i<6;i++)fluid[i]=true; // 4 samples, 2 blocks apart: at least 7 blocks wide
        assertEquals(NONE,TerrainProbe.probe(flat(10,64),fluid,new boolean[10],2));
        fluid=new boolean[10];for(int i=2;i<7;i++)fluid[i]=true; // 5 samples: at least 9 blocks
        assertEquals(FLUID,TerrainProbe.probe(flat(10,64),fluid,new boolean[10],2));
    }
    @Test void lavaOutranksADropAndADropOutranksWater(){
        int[] surface=flat(11,70);for(int i=6;i<11;i++)surface[i]=60;var fluid=new boolean[11];for(int i=0;i<11;i++)fluid[i]=true;var lava=new boolean[11];
        assertEquals(DROP,TerrainProbe.probe(surface,fluid,lava,1));lava[9]=true;assertEquals(LAVA,TerrainProbe.probe(surface,fluid,lava,1));
    }

    // ---- channel clock ---------------------------------------------------------------------------------------------------------------
    @Test void aChannelLastsEightSecondsUndisturbed(){
        assertEquals(1000+8*S,RaiderGates.completeAt(1000,0));assertEquals(8,RaidMarch.CHANNEL_SECONDS);
    }
    @Test void everyHurtSecondAddsThreeSecondsUpToTenInTotal(){
        int delay=0;var steps=new ArrayList<Integer>();
        for(int i=0;i<6;i++){delay=RaiderGates.delayedBy(delay);steps.add(delay);}
        assertEquals(List.of(3*(int)S,6*(int)S,9*(int)S,10*(int)S,10*(int)S,10*(int)S),steps);
        assertEquals(1000+(8+10)*S,RaiderGates.completeAt(1000,delay),"however hard it is shot, at most 18 seconds");
    }
    @Test void damageIsALossOfHealthNotAHeal(){
        assertTrue(RaiderGates.hurt(20f,19f));assertFalse(RaiderGates.hurt(20f,20f));assertFalse(RaiderGates.hurt(19f,20f));assertFalse(RaiderGates.hurt(20f,19.9995f),"rounding noise is not damage");
    }
    @Test void theDestinationShowsItselfThreeSecondsBeforeTheEnd(){
        long end=2000;assertFalse(RaiderGates.telegraphDue(end-3*S-1,end));assertTrue(RaiderGates.telegraphDue(end-3*S,end));assertTrue(RaiderGates.telegraphDue(end,end));
        assertEquals(3,RaidMarch.TELEGRAPH_SECONDS);
    }

    // ---- which gate a raider uses ----------------------------------------------------------------------------------------------------
    @Test void aStuckRaiderJoinsTheNearestGateWithinEightBlocks(){
        var gates=List.of(new Vec3(0,64,0),new Vec3(20,64,0),new Vec3(10,64,0));
        assertEquals(1,RaiderGates.nearestGate(gates,new Vec3(24,64,0),RaidMarch.GATE_JOIN_RADIUS));
        assertEquals(2,RaiderGates.nearestGate(gates,new Vec3(14,64,0),RaidMarch.GATE_JOIN_RADIUS),"nearest, not first");
        assertEquals(-1,RaiderGates.nearestGate(gates,new Vec3(40,64,0),RaidMarch.GATE_JOIN_RADIUS),"beyond eight blocks a new gate is needed");
        assertEquals(-1,RaiderGates.nearestGate(List.of(),new Vec3(0,64,0),8));
        assertEquals(0,RaiderGates.nearestGate(List.of(new Vec3(0,64,0)),new Vec3(8,64,0),8),"on the limit");
        assertEquals(-1,RaiderGates.nearestGate(List.of(new Vec3(0,64,0)),new Vec3(8.01,64,0),8));
        assertEquals(8,RaidMarch.GATE_JOIN_RADIUS);
    }
    @Test void aGateHasRoomForEightChannelersAndTheRestWait(){
        for(int channeling=0;channeling<RaidMarch.GATE_CAPACITY;channeling++)assertTrue(RaiderGates.hasRoom(channeling));
        assertFalse(RaiderGates.hasRoom(RaidMarch.GATE_CAPACITY));assertFalse(RaiderGates.hasRoom(9));assertEquals(8,RaidMarch.GATE_CAPACITY);
    }
    @Test void aGateStandsBesideTheRaiderNotInFrontOfIt(){
        var spot=RaiderGates.gateSpot(new Vec3(100,64,0),new Vec3(0,64,0)); // marching along -x: the gate is 1.6 blocks to the side
        assertEquals(100,spot.x,1e-9);assertEquals(64,spot.y,1e-9);assertEquals(1.6,Math.abs(spot.z),1e-9);
        var onTop=RaiderGates.gateSpot(new Vec3(5,70,5),new Vec3(5,64,5));assertEquals(1.6,Math.hypot(onTop.x-5,onTop.z-5),1e-9,"even when it stands over the beacon");
    }

    // ---- escalation, in gates --------------------------------------------------------------------------------------------------------
    @Test void thirdGateThenWithdraw(){
        assertEquals(3,RaidMarch.MAX_GATES);
        assertEquals(RaidMarch.Step.RESCUE,RaidMarch.next(0,false));assertEquals(RaidMarch.Step.RESCUE,RaidMarch.next(2,false));
        assertEquals(RaidMarch.Step.WITHDRAW,RaidMarch.next(3,false));assertEquals(RaidMarch.Step.RESCUE,RaidMarch.next(3,true),"a boss gets another gate");
    }
}
