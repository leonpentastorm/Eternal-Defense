package dev.createarsenal.beacon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 0.0.19: the fire support radio answers a flare instead of the siren, and the falling round whistles again (the 0.0.16 whistle). */
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
    @Test void theWhistleIsBackAndEndsAsTheRoundLands() throws Exception{
        var sounds=json("/assets/arsenal_beacon/sounds.json");
        for(String name:List.of("shell_whistle","bomb_whistle")){
            assertTrue(sounds.has(name.replace('_','.')),name);
            var file=HandTunedRoundTest.ogg("/assets/arsenal_beacon/sounds/sfx/"+name+".ogg");
            assertEquals(1,file.channels(),name+" rides on the round, so it is mono");
            assertEquals(OrdnanceClient.WHISTLE_SECONDS,file.seconds(),.02,name);
        }
        assertFalse(sounds.has("ordnance.incoming"),"the 0.0.18 incoming sound is gone");
        assertEquals(103,SupportRules.BLAST_FLIGHT_TICKS,"the pace the owner kept: 5.15 s from shot to impact");
        for(boolean bomb:new boolean[]{false,true})for(int id=0;id<5000;id+=7){
            float pitch=OrdnanceClient.whistlePitch(bomb,id);
            assertTrue(bomb?pitch>=.9f&&pitch<=1f:pitch>=.92f&&pitch<=1.08f,"pitch "+pitch);
            int start=OrdnanceClient.whistleStart(SupportRules.BLAST_FLIGHT_TICKS,pitch);
            double ends=start+OrdnanceClient.WHISTLE_SECONDS*20/pitch;
            assertEquals(SupportRules.BLAST_FLIGHT_TICKS+OrdnanceClient.BLAST_HEARD_AFTER_LANDING,ends,.5,"the whistle (pitch "+pitch+") ends as the round's blast is heard");
            assertTrue(start>60,"it is heard for the last part of the fall only");
        }
        assertEquals(1,OrdnanceClient.whistleStart(10,1f),"a short fall whistles from its first tick");
    }
    private static com.google.gson.JsonObject json(String path) throws Exception{
        try(var in=RadioAndWhistleTest.class.getResourceAsStream(path)){return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,path),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
}
