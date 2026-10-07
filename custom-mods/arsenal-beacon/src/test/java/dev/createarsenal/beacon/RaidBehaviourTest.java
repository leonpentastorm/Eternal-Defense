package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RaidBehaviourTest {
    @Test void aRaiderThatBarelyMovedInASecondIsBlocked(){
        assertTrue(RaidMarch.blocked(0));assertTrue(RaidMarch.blocked(.99));assertFalse(RaidMarch.blocked(1.0));assertFalse(RaidMarch.blocked(4.3));
    }
    @Test void spawnsStayAtOrAboveTheBeaconUntilReinforcementsHaveWaitedLong(){
        assertEquals(0,RaidSpawns.allowedDrop(0));
        assertEquals(0,RaidSpawns.allowedDrop(599));
        assertEquals(4,RaidSpawns.allowedDrop(600));
        assertEquals(10,RaidSpawns.allowedDrop(1800));
    }
    @Test void aMarchersDigRangeAndTheSiegeCreeperLimitAreSane(){
        assertTrue(RaidMarch.DIG_RANGE>=16&&RaidMarch.DIG_RANGE<=48);
    }
}
