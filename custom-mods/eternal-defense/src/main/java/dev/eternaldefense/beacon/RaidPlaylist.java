package dev.eternaldefense.beacon;

import java.util.List;
import java.util.Random;

/** Which raid song plays when (pure; the client plays it in RaidMusic). */
final class RaidPlaylist {
    private RaidPlaylist(){}
    /** A song: resource name under sounds/music/ */
    record Song(String id,String pool,int seconds){}
    /** The songs (made with Suno, converted by tools/audio/import_songs.py) */
    static final List<Song> SONGS=List.of(
        new Song("barren_gap","normal",120),new Song("line_holder","normal",134),new Song("phase_one_assault","normal",125),new Song("silent_trigger","normal",120),
        new Song("acid_redeemer","normal",119),new Song("assault_loop","normal",120),new Song("breach_core","normal",120),new Song("rolling_wave","normal",119),
        new Song("boss_battle","boss",120),
        new Song("anomaly_protocol","special",120));

    static List<Song> pool(String pool){return SONGS.stream().filter(s->s.pool().equals(pool)).toList();}

    /** Pure: the kind of raid music for this moment of a raid */
    static String cue(boolean fresh,String phase,boolean hardRaid,int wave,int waves,String raidType){
        if(!fresh||!"raid".equals(phase)||wave<1)return "";
        if(hardRaid&&wave>=waves)return "boss";
        return RaidTypes.special(raidType)?"special":"normal";
    }
    /** Pure: the same number for every player of one raid of one base. */
    static long seed(long beacon,int raidNumber){return beacon*0x9E3779B97F4A7C15L+raidNumber*0xC2B2AE3D27D4EB4FL;}
    /** Pure: the song of wave wave (from 1) of a raid of kind cue (see cue); null for no song. */
    static Song song(String cue,long seed,int wave){
        if(cue.isEmpty()||wave<1)return null;
        if(cue.equals("boss")||cue.equals("special")&&wave==1){var own=pool(cue);if(!own.isEmpty())return own.get(0);}
        var songs=pool("normal");if(songs.isEmpty())return null;
        return songs.get(order(seed,wave-1-(cue.equals("special")?1:0),songs.size()));
    }
    /** Pure: which of size songs the index-th ordinary wave (from 0) plays */
    static int order(long seed,int index,int size){
        if(size<=1)return 0;
        var random=new Random(seed);int[] deck=null;int last=-1;
        for(int i=0;i<=Math.max(0,index);i++){
            if(i%size==0){
                deck=new int[size];for(int k=0;k<size;k++)deck[k]=k;
                for(int k=size-1;k>0;k--){int j=random.nextInt(k+1),t=deck[k];deck[k]=deck[j];deck[j]=t;}
                if(deck[0]==last){int t=deck[0];deck[0]=deck[size-1];deck[size-1]=t;}
            }
            last=deck[i%size];
        }
        return last;
    }
    /** Pure: "m:ss" for the player. */
    static String clock(int seconds){int s=Math.max(0,seconds);return s/60+":"+(s%60<10?"0":"")+s%60;}
}
