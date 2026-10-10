package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Raid music on the client. While a raid runs and the player is near the beacon, the game's own music is replaced by the raid's theme
 * (boss raid, special raid or ordinary raid), which loops; the player's Music volume slider controls it. Winning plays the victory fanfare
 * at once, over whatever was playing; losing (or walking away) stops the raid theme and the game's music comes back as usual.
 * The choice is made in {@link #situational}, which {@code mixin.RaidMusicMixin} asks whenever Minecraft picks music.
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class RaidMusic {
    private RaidMusic(){}
    /** How long the fanfare owns the music (ticks): its length plus a breath. */
    static final int VICTORY_TICKS=200;
    /** After the fanfare, the game waits this long before its own music returns (ticks). */
    static final int AFTER_VICTORY=6000;
    private static Music normal,boss,special,victory;
    private static long victoryUntil=-1;
    private static String playing="";

    /** Pure: which theme a raid needs: "boss" for a hard raid, "special" for a special raid, "normal" otherwise; "" when no raid music should play. */
    static String cue(boolean fresh,String phase,boolean near,boolean hardRaid,String raidType){
        if(!fresh||!near||!"raid".equals(phase))return "";
        if(hardRaid)return "boss";
        return RaidTypes.special(raidType)?"special":"normal";
    }
    private static Music music(String cue){
        if(normal==null){
            normal=new Music(ArsenalSounds.RAID_NORMAL.getHolder().orElseThrow(),0,0,true);boss=new Music(ArsenalSounds.RAID_BOSS.getHolder().orElseThrow(),0,0,true);
            special=new Music(ArsenalSounds.RAID_SPECIAL.getHolder().orElseThrow(),0,0,true);victory=new Music(ArsenalSounds.VICTORY.getHolder().orElseThrow(),AFTER_VICTORY,AFTER_VICTORY,true);
        }
        return switch(cue){case "boss"->boss;case "special"->special;case "normal"->normal;case "victory"->victory;default->null;};
    }
    private static long now(){var mc=Minecraft.getInstance();return mc.level==null?0:mc.level.getGameTime();}
    /** The music Minecraft should play now, or null to leave the choice to the game. */
    public static Music situational(){
        if(victoryUntil>=0&&now()<victoryUntil)return music("victory");
        String cue=BeaconClient.raidMusicCue();
        return cue.isEmpty()?null:music(cue);
    }
    /** The raid was won: the fanfare starts at once (it replaces the raid theme) and holds the music for a moment. */
    static void victory(){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        victoryUntil=now()+VICTORY_TICKS;mc.getMusicManager().stopPlaying();mc.getMusicManager().startPlaying(music("victory"));playing="victory";
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null){playing="";victoryUntil=-1;return;}
        String now=victoryUntil>=0&&now()<victoryUntil?"victory":BeaconClient.raidMusicCue();
        // a raid theme that is no longer wanted (defeat, walked away) is stopped; the game's own music returns after its usual pause
        if(!playing.isEmpty()&&!playing.equals("victory")&&now.isEmpty())mc.getMusicManager().stopPlaying();
        playing=now;
    }
}
