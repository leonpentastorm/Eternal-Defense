package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.21: every wave cleared adds 5 minutes to the raid's 20-minute clock. */
final class Release1121Test {
    @Test void everyClearedWaveAddsFiveMinutes(){
        assertEquals(20*1200,Rules.raidTimeLimit(0),"20 minutes before any wave is cleared");
        assertEquals(25*1200,Rules.raidTimeLimit(1));
        assertEquals(55*1200,Rules.raidTimeLimit(7),"the longest raid (8 waves) has 55 minutes in its last wave");
        assertEquals(Rules.raidTimeLimit(0),Rules.raidTimeLimit(-1),"wave 0 (before the first wave) counts no cleared wave");
        for(int tier=0;tier<=10;tier++)assertEquals(20+5*(Rules.waves(tier)-1),Rules.raidTimeLimit(Rules.waves(tier)-1)/1200,"tier "+tier);
    }
    @Test void theMinutesLeftRoundUpAndNeverGoBelowZero(){
        assertEquals(20,Rules.raidMinutesLeft(0,0));
        assertEquals(15,Rules.raidMinutesLeft(10*1200,1),"wave 1 cleared at minute 10: 25 - 10");
        assertEquals(1,Rules.raidMinutesLeft(25*1200-1,1),"one tick left is still a minute");
        assertEquals(0,Rules.raidMinutesLeft(30*1200,1));
    }
    @Test void theRaidUsesTheClockAndSaysSo() throws Exception{
        String src=Release114Test.source("EternalDefense.java");
        assertTrue(src.contains("if(d.raidTicks>Rules.raidTimeLimit(d.wave-1)){fail("),"the waves before the current one are the cleared ones");
        assertFalse(src.contains("d.raidTicks>24000L"),"no fixed 20 minutes left");
        int clear=src.indexOf("\"gui.eternal_defense.raid.wave_cleared\",d.wave,Rules.WAVE_CLEARED_BONUS_TICKS/1200,Rules.raidMinutesLeft(d.raidTicks,d.wave))"),next=src.indexOf("nextWave(l,d);}",clear);
        assertTrue(clear>0&&next>clear,"the line goes out when a wave that is not the last is cleared, then the next wave starts");
        assertEquals("Wave %s cleared: +%s minutes on the raid clock. %s minutes left to win.",Release114Test.lang().get("gui.eternal_defense.raid.wave_cleared").getAsString());
    }
    @Test void theGuideQuotesTheClock() throws Exception{
        String body=Release114Test.text("guide.raids.body");
        assertTrue(body.contains("A raid must be won within "+Rules.RAID_TIME_TICKS/1200+" minutes. Every wave you clear adds "+Rules.WAVE_CLEARED_BONUS_TICKS/1200+" more minutes, and chat shows the time left."));
        assertTrue(body.contains("If time runs out you lose, and the beacon loses a fifth of its health."));
    }
}
