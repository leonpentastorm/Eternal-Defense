package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RaidBehaviourTest {
    @Test void aRaiderThatBarelyMovedInASecondIsBlocked(){
        assertTrue(RaidMarch.blocked(0));assertTrue(RaidMarch.blocked(.99));assertFalse(RaidMarch.blocked(1.0));assertFalse(RaidMarch.blocked(4.3));
    }
    @Test void spawnsMayBeABitBelowTheBeaconAtOnceAndLowerLater(){
        assertEquals(20,RaidSpawns.allowedDrop(0),"1.1.22: 20 blocks at once (0 before)");
        assertEquals(20,RaidSpawns.allowedDrop(599));
        assertEquals(30,RaidSpawns.allowedDrop(600),"after 30 seconds");
        assertEquals(30,RaidSpawns.allowedDrop(1800));
    }
    @Test void aMarchersDigRangeAndTheSiegeCreeperLimitAreSane(){
        assertTrue(RaidMarch.DIG_RANGE>=16&&RaidMarch.DIG_RANGE<=48);
    }
}
