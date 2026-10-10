package dev.createarsenal.beacon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 0.0.19: the fire support radio answers a flare instead of the siren; the falling round's sound (the 0.0.16 whistle in 0.0.19, the incoming sound again in 0.0.20). */
final class RadioAndWhistleTest {
    @Test void theRadioLinesAreShippedAndTheSirenIsGone() throws Exception{
        var sounds=json("/assets/arsenal_beacon/sounds.json");var lang=json("/assets/arsenal_beacon/lang/en_us.json");
        for(int n=1;n<=SupportChatter.LINES;n++){
            var event=sounds.getAsJsonObject("flare.chatter_"+n);assertNotNull(event,"line "+n);
            assertEquals("arsenal_beacon:sfx/chatter_"+n,event.getAsJsonArray("sounds").get(0).getAsString());
            assertTrue(lang.has(event.get("subtitle").getAsString()),"a subtitle for line "+n);
            var file=HandTunedRoundTest.ogg("/assets/arsenal_beacon/sounds/sfx/chatter_"+n+".ogg");
            assertEquals(1,file.channels(),"line "+n+" follows the player, so it is mono");
            assertTrue(file.seconds()>3&&file.seconds()<5,"line "+n+" is "+file.seconds()+" s");
        }
        assertFalse(sounds.has("flare.signal"),"the siren is gone");
        assertNull(RadioAndWhistleTest.class.getResource("/assets/arsenal_beacon/sounds/sfx/flare_signal.ogg"));
    }
    @Test void theRadioPicksAnyLineButNeverTheSameTwiceInARow(){
        for(var kind:SupportCalls.Kind.values()){
            int[] lines=SupportChatter.lines(kind);
            for(int n:lines)assertTrue(n>=1&&n<=SupportChatter.LINES);
            var heard=new HashSet<Integer>();var random=new Random(kind.ordinal());int last=0;
            for(int call=0;call<500;call++){
                int line=SupportChatter.pick(lines,last,random.nextInt());
                if(lines.length==0){assertEquals(0,line,"no answer");continue;}
                assertNotEquals(last,line,kind+" call "+call+" repeats line "+line);
                assertTrue(Arrays.stream(lines).anyMatch(n->n==line));
                heard.add(line);last=line;
            }
            assertEquals(lines.length,heard.size(),kind+": every line it can use comes up");
        }
        assertEquals(6,SupportChatter.lines(SupportCalls.Kind.FIRE).length,"a fire support flare can get any of the six lines");
        assertArrayEquals(new int[]{1,4},SupportChatter.lines(SupportCalls.Kind.SUPPLY),"a delivery: \"payload incoming\", \"stay out of the drop zone\"");
        assertEquals(0,SupportChatter.lines(SupportCalls.Kind.RETURN).length,"the return portal gets no radio line");
        assertEquals(7,SupportChatter.pick(new int[]{7},7,3),"a single line may repeat");
    }
    /** 0.0.20: the owner asked for the 0.0.18 incoming sound again; the 0.0.19 whistle was lost whenever its round left the player's view. */
    @Test void theIncomingSoundIsBackAndPlaysWhereTheRoundLands() throws Exception{
        var sounds=json("/assets/arsenal_beacon/sounds.json");
        assertTrue(sounds.has("ordnance.incoming"),"the incoming sound is registered");
        for(String gone:List.of("shell.whistle","bomb.whistle"))assertFalse(sounds.has(gone),gone+" is gone");
        for(String name:List.of("shell_whistle","bomb_whistle"))assertNull(RadioAndWhistleTest.class.getResource("/assets/arsenal_beacon/sounds/sfx/"+name+".ogg"),name+".ogg is gone");
        assertEquals(1,HandTunedRoundTest.ogg("/assets/arsenal_beacon/sounds/sfx/ordnance_incoming.ogg").channels(),"it stands where the round lands, so it is mono");
        assertEquals(103,SupportRules.BLAST_FLIGHT_TICKS,"the pace the owner kept: 5.15 s from shot to impact");
        assertFalse(OrdnanceClient.incomingDue(0));assertTrue(OrdnanceClient.incomingDue(1),"played the first tick a client sees the round");
        assertTrue(OrdnanceClient.incomingDue(OrdnanceClient.INCOMING_LATEST_TICK));assertFalse(OrdnanceClient.incomingDue(OrdnanceClient.INCOMING_LATEST_TICK+1),"too late: it would end after the impact");
    }
    private static com.google.gson.JsonObject json(String path) throws Exception{
        try(var in=RadioAndWhistleTest.class.getResourceAsStream(path)){return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,path),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
}
