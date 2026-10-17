package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.22: raiders spawn on the nearest good ground, from 32 blocks past the zone and up to 20 below the beacon, so a wave reaches the base sooner. */
final class Release1122Test {
    @Test void spawnsAreTriedNearestFirst(){
        assertEquals(32,RaidSpawns.CLEARANCE,"32 blocks past the zone edge (64 before 1.1.22)");
        var rings=RaidSpawns.spawnRings(8,168);
        assertEquals(8+RaidSpawns.CLEARANCE,rings.get(0),"the first ring is the nearest one allowed");
        assertEquals(168,rings.get(rings.size()-1),"out to the edge of the active chunks when nothing nearer will do");
        for(int i=1;i<rings.size();i++)assertTrue(rings.get(i)>rings.get(i-1),"farther and farther");
        assertEquals(List.of(40),RaidSpawns.spawnRings(8,30),"active chunks smaller than the clearance: only the nearest ring");
        assertEquals(List.of(64,80,96,112),RaidSpawns.spawnRings(32,112),"a Mk IV zone (radius 32)");
    }
    @Test void groundALittleBelowTheBeaconCountsFromTheStart(){
        assertEquals(20,RaidSpawns.allowedDrop(0),"a beacon on a hill still has spawn ground near it");
        assertEquals(30,RaidSpawns.allowedDrop(30*20));
        assertTrue(RaidSpawns.allowedDrop(30*20)<=RaidSpawns.GATE_HEIGHT,"never lower than a gate may be");
    }
    @Test void gatesComeNearestFirstAndStillCloserThanNaturalGround(){
        var rings=RaidSpawns.gateRings(8,168);
        assertEquals(8+RaidSpawns.GATE_BEYOND_ZONE,rings.get(0));
        assertTrue(RaidSpawns.GATE_BEYOND_ZONE<RaidSpawns.CLEARANCE);
    }
    @Test void aFreshRaiderNeverAppearsNextToAPlayer() throws Exception{
        assertEquals(16,RaidSpawns.SPAWN_PLAYER_DISTANCE);
        var player=List.of(new net.minecraft.world.phys.Vec3(0,64,0));
        assertFalse(RaidSpawns.farFromPlayers(new net.minecraft.world.phys.Vec3(15,64,0),player,RaidSpawns.SPAWN_PLAYER_DISTANCE));
        assertTrue(RaidSpawns.farFromPlayers(new net.minecraft.world.phys.Vec3(16,64,0),player,RaidSpawns.SPAWN_PLAYER_DISTANCE));
        String src=Release114Test.source("RaidSpawns.java");
        assertTrue(src.contains("for(int ring:spawnRings(d.radius(),farthest(l,d)))"),"find walks the rings nearest first");
        assertTrue(src.contains("farFromPlayers(net.minecraft.world.phys.Vec3.atBottomCenterOf(p),players,SPAWN_PLAYER_DISTANCE)&&safe(l,d,p)"));
        assertTrue(src.contains("sorted(Comparator.comparingInt(Candidate::ring).thenComparingInt(c->c.score().violations()))"),"the nearest ring wins over a cleaner way in farther out");
    }
    @Test void theGuideSaysWhereEnemiesComeFrom() throws Exception{
        assertTrue(Release114Test.text("guide.raids.body").contains("Enemies come in from open ground about "+RaidSpawns.CLEARANCE+" blocks outside your zone, with a burst of speed, and never right next to a player."));
    }
}
