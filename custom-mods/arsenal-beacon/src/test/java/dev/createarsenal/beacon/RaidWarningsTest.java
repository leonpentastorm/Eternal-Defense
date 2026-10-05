package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RaidWarningsTest {
    @Test void silentUntilOneDayRemains(){
        assertEquals(Integer.MAX_VALUE,RaidWarnings.bucket(24001));
        assertEquals(4,RaidWarnings.bucket(24000));
        assertEquals(Integer.MAX_VALUE,RaidWarnings.bucket(7*24000L));
    }
    @Test void warnsEverySixHoursDownToTheRaid(){
        assertEquals(4,RaidWarnings.bucket(23999));assertEquals(4,RaidWarnings.bucket(18001));
        assertEquals(3,RaidWarnings.bucket(18000));assertEquals(3,RaidWarnings.bucket(12001));
        assertEquals(2,RaidWarnings.bucket(12000));assertEquals(1,RaidWarnings.bucket(6000));assertEquals(1,RaidWarnings.bucket(1));
        assertEquals(0,RaidWarnings.bucket(0));assertEquals(0,RaidWarnings.bucket(-500));
    }
    @Test void aWholeCountdownProducesExactlyFiveMessagesInOrder(){
        int last=Integer.MAX_VALUE,messages=0;String order="";
        for(long remaining=3*24000L;remaining>=-10;remaining--){
            int now=RaidWarnings.bucket(remaining);
            if(RaidWarnings.shouldWarn(last,now)){messages++;order+=now;last=now;}else if(now>last)last=now;
        }
        assertEquals(5,messages);assertEquals("43210",order);
    }
    @Test void aLongerWaitNeverRepeatsAMessage(){
        assertFalse(RaidWarnings.shouldWarn(3,3));assertFalse(RaidWarnings.shouldWarn(3,4));assertFalse(RaidWarnings.shouldWarn(3,Integer.MAX_VALUE));
    }
}
