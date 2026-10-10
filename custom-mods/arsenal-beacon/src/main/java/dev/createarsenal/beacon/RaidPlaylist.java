package dev.createarsenal.beacon;

import java.util.List;
import java.util.Random;

/**
 * Which raid song plays when (pure; the client plays it in {@link RaidMusic}). Every wave starts a song from its pool and loops it until the
 * next wave starts, which changes to another song of the pool. The order is random but fixed per raid (it is worked out from the beacon and
 * the raid number), so every player of the base hears the same song, and a reconnect does not reshuffle it.
 * <p>Pools: {@code normal} for an ordinary raid, {@code special} for a special raid (Air Raid, Paratroopers, Siege, They Are Thousands),
 * {@code boss} for the boss wave (the last wave) of a hard raid; the other waves of a hard raid use the pool of its kind.
 */
final class RaidPlaylist {
    private RaidPlaylist(){}
    /** A song: resource name under {@code sounds/music/} (also its {@code music.<id>} event and lang key), pool, length in seconds (rounded). */
    record Song(String id,String pool,int seconds){}
    /** The owner's songs (made with Suno, converted by {@code tools/audio/import_songs.py}) and the three original themes of 0.0.16. */
    static final List<Song> SONGS=List.of(
        new Song("barren_gap","normal",120),new Song("line_holder","normal",134),new Song("phase_one_assault","normal",125),new Song("silent_trigger","normal",120),
        new Song("raid_normal","normal",75),
        new Song("boss_battle","boss",120),new Song("raid_boss","boss",67),
        new Song("anomaly_protocol","special",120),new Song("raid_special","special",64));

    static List<Song> pool(String pool){return SONGS.stream().filter(s->s.pool().equals(pool)).toList();}

    /** Pure: the pool for this moment of a raid; "" when no raid song should play (no fresh beacon state, or no raid running). */
    static String cue(boolean fresh,String phase,boolean hardRaid,int wave,int waves,String raidType){
        if(!fresh||!"raid".equals(phase)||wave<1)return "";
        if(hardRaid&&wave>=waves)return "boss";
        return RaidTypes.special(raidType)?"special":"normal";
    }
    /** Pure: the same number for every player of one raid of one base. */
    static long seed(long beacon,int raidNumber){return beacon*0x9E3779B97F4A7C15L+raidNumber*0xC2B2AE3D27D4EB4FL;}
    /**
     * Pure: which of {@code size} songs wave {@code wave} (from 1) plays: a random order fixed by {@code seed}, never the same song in two
     * waves in a row (when the pool has more than one).
     */
    static int pick(long seed,int wave,int size){
        if(size<=1)return 0;
        var random=new Random(seed);int last=-1;
        for(int w=1;w<=Math.max(1,wave);w++){int k=random.nextInt(last<0?size:size-1);if(last>=0&&k>=last)k++;last=k;}
        return last;
    }
    /** Pure: the song for a wave from {@code pool} (null for an empty or unknown pool). */
    static Song song(String pool,long seed,int wave){
        var songs=pool(pool);if(songs.isEmpty())return null;
        return songs.get(pick(seed^pool.hashCode(),wave,songs.size()));
    }
    /** Pure: "m:ss" for the player. */
    static String clock(int seconds){int s=Math.max(0,seconds);return s/60+":"+(s%60<10?"0":"")+s%60;}
}
