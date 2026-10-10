package dev.createarsenal.beacon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The raid music player (0.0.17): which song each wave plays, and that every song is really there with the length the player shows. */
final class MusicPlayerTest {
    @Test void eachKindOfRaidHasItsPool(){
        assertEquals("normal",RaidPlaylist.cue(true,"raid",false,1,3,"normal"));
        assertEquals("special",RaidPlaylist.cue(true,"raid",false,2,3,"siege"));
        assertEquals("normal",RaidPlaylist.cue(true,"raid",true,1,3,"normal"),"a hard raid plays its own kind until the boss wave");
        assertEquals("special",RaidPlaylist.cue(true,"raid",true,2,3,"air"));
        assertEquals("boss",RaidPlaylist.cue(true,"raid",true,3,3,"normal"),"the boss walks in with the last wave");
        assertEquals("boss",RaidPlaylist.cue(true,"raid",true,3,3,"thousands"));
        assertEquals("",RaidPlaylist.cue(true,"raid",false,0,3,"normal"),"no song before the first wave");
        assertEquals("",RaidPlaylist.cue(true,"preparation",false,2,3,"normal"));
        assertEquals("",RaidPlaylist.cue(true,"restore",false,3,3,"normal"),"the raid is over");
        assertEquals("",RaidPlaylist.cue(false,"raid",false,1,3,"normal"),"stale beacon state plays nothing");
        for(String pool:List.of("normal","special","boss"))assertFalse(RaidPlaylist.pool(pool).isEmpty(),pool);
        assertEquals(8,RaidPlaylist.pool("normal").size(),"one ordinary song for every wave of the longest raid");
        assertEquals(8,Rules.waves(99),"the longest raid");
    }
    @Test void noSongPlaysTwiceInOneRaidAndEveryPlayerHearsTheSame(){
        var openers=new HashSet<String>();
        for(long seed=-400;seed<400;seed++)for(String type:List.of("normal","siege"))for(boolean hard:new boolean[]{false,true})for(int waves=3;waves<=8;waves++){
            var played=new ArrayList<String>();
            for(int wave=1;wave<=waves;wave++){
                var song=RaidPlaylist.song(RaidPlaylist.cue(true,"raid",hard,wave,waves,type),seed,wave);
                assertNotNull(song);
                assertFalse(played.contains(song.id()),"seed "+seed+" "+type+(hard?" hard":"")+" wave "+wave+" of "+waves+" plays "+song.id()+" again: "+played);
                assertEquals(song,RaidPlaylist.song(RaidPlaylist.cue(true,"raid",hard,wave,waves,type),seed,wave),"the same raid and wave always give the same song");
                played.add(song.id());
            }
            if(hard)assertEquals("boss_battle",played.get(waves-1),"the boss wave of a hard raid");
            if(type.equals("siege"))assertEquals("anomaly_protocol",played.get(0),"a special raid opens with its own song");
            else openers.add(played.get(0));
        }
        assertEquals(8,openers.size(),"any ordinary song can open a raid");
        // a raid longer than the songs (none is, today) starts a new shuffle, never with the song just played
        for(long seed=0;seed<300;seed++){int last=-1;for(int i=0;i<40;i++){int k=RaidPlaylist.order(seed,i,8);assertNotEquals(last,k,"seed "+seed+" index "+i);last=k;}}
        assertEquals(0,RaidPlaylist.order(42,3,1));assertEquals(0,RaidPlaylist.order(42,3,0));
        assertNull(RaidPlaylist.song("",7,1));assertNull(RaidPlaylist.song("normal",7,0));
        assertNotEquals(RaidPlaylist.seed(100,4),RaidPlaylist.seed(100,5),"each raid has its own order");
    }
    @Test void allTheMusicIsTheOwnersAndTheFanfareFitsItsTime() throws Exception{
        for(String gone:List.of("raid_normal","raid_boss","raid_special"))
            assertNull(MusicPlayerTest.class.getResource("/assets/arsenal_beacon/sounds/music/"+gone+".ogg"),gone+" (made for 0.0.16) was taken out");
        double fanfare=oggSeconds("/assets/arsenal_beacon/sounds/music/victory.ogg");
        assertEquals(10,fanfare,.5,"the owner's Victory Fanfare");
        assertTrue(fanfare*20+10<=RaidMusic.VICTORY_TICKS,"the fanfare is not cut off ("+fanfare+" s, "+RaidMusic.VICTORY_TICKS+" ticks)");
    }
    @Test void theClockReadsMinutesAndSeconds(){
        assertEquals("0:00",RaidPlaylist.clock(0));assertEquals("0:09",RaidPlaylist.clock(9));assertEquals("2:14",RaidPlaylist.clock(134));assertEquals("0:00",RaidPlaylist.clock(-4));
    }
    @Test void bothMusicChoicesAreOnUntilThePlayerSaysOtherwise(){
        assertTrue(ArsenalClientConfig.get(ArsenalClientConfig.RAID_MUSIC));assertTrue(ArsenalClientConfig.get(ArsenalClientConfig.MUSIC_PLAYER));
    }
    @Test void everySongIsShippedNamedAndAsLongAsThePlayerSays() throws Exception{
        var sounds=json("/assets/arsenal_beacon/sounds.json");var lang=json("/assets/arsenal_beacon/lang/en_us.json");
        var ids=new HashSet<String>();
        for(var song:RaidPlaylist.SONGS){
            assertTrue(ids.add(song.id()),"one entry per song");
            var event=sounds.getAsJsonObject("music."+song.id());assertNotNull(event,"sounds.json lists music."+song.id());
            var entry=event.getAsJsonArray("sounds").get(0).getAsJsonObject();
            assertEquals("arsenal_beacon:music/"+song.id(),entry.get("name").getAsString());assertTrue(entry.get("stream").getAsBoolean(),"songs are streamed");
            assertTrue(lang.has("gui.arsenal_beacon.music."+song.id()),"the player has a name for "+song.id());
            double seconds=oggSeconds("/assets/arsenal_beacon/sounds/music/"+song.id()+".ogg");
            assertEquals(seconds,song.seconds(),1.0,song.id()+" is "+seconds+" s long");
        }
        assertNotNull(sounds.getAsJsonObject("music.victory"));
    }
    private static com.google.gson.JsonObject json(String path) throws Exception{
        try(var in=MusicPlayerTest.class.getResourceAsStream(path)){return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,path),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    /** Length of an Ogg Vorbis file: the last page's granule position (samples) over the sample rate of the identification header. */
    static double oggSeconds(String path) throws Exception{
        byte[] b;try(var in=MusicPlayerTest.class.getResourceAsStream(path)){b=Objects.requireNonNull(in,path).readAllBytes();}
        int rate=le(b,27+(b[26]&0xff)+12,4);
        int last=-1;for(int i=b.length-4;i>=0;i--)if(b[i]=='O'&&b[i+1]=='g'&&b[i+2]=='g'&&b[i+3]=='S'){last=i;break;}
        long granule=0;for(int k=7;k>=0;k--)granule=granule<<8|(b[last+6+k]&0xff);
        return granule/(double)rate;
    }
    private static int le(byte[] b,int at,int n){int v=0;for(int k=n-1;k>=0;k--)v=v<<8|(b[at+k]&0xff);return v;}
}
