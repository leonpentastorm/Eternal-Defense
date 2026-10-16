package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.10: the widest zone matches the tallest; the first level stays, each step grows more. */
final class Release1110Test {
    @Test void theZoneGrowsHarderToSixtyFiveAcross() throws Exception{
        int[] radius={8,12,20,32};
        for(int core=0;core<4;core++)assertEquals(radius[core],Rules.radius(core),"Mk-"+(core+1));
        assertEquals(8,Rules.radius(0),"the first level is unchanged");
        for(int core=1;core<3;core++)assertTrue(Rules.radius(core+1)-Rules.radius(core)>Rules.radius(core)-Rules.radius(core-1),"each step bigger than the last");
        assertEquals(Rules.below(4)+Rules.above(4)+1,2*Rules.radius(3)+1,"as wide as the full vertical zone is tall: 65");
        assertTrue(Release114Test.text("guide.zone.detail").contains("zone radii 12, 20 and 32 blocks (65 blocks across at Mk-4"));
    }
}
