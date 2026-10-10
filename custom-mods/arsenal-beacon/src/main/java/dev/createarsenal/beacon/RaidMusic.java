package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/**
 * Raid music on the client, and its little player under the beacon's status card. While a raid runs, each wave starts a song from
 * {@link RaidPlaylist} and loops it until the next wave, which fades to another song; winning fades it out and plays the victory fanfare.
 * The songs are played here (not by the game's music manager, which waits meanwhile: {@code mixin.RaidMusicMixin}), so they can fade
 * and loop without a gap; they follow the Music volume slider. Both can be turned off in the beacon's Settings ({@link ArsenalClientConfig}).
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class RaidMusic {
    private RaidMusic(){}
    /** Fades (ticks): a new song comes in, an old one goes out (a wave change), the raid song gives way to the fanfare. */
    static final int FADE_IN=30,FADE_OUT=60,FADE_FOR_VICTORY=10;
    /** How long the fanfare owns the music (its length plus a breath), then how long the game's own music still waits (ticks). */
    static final int VICTORY_TICKS=200,QUIET_AFTER=600;
    /** A track that does not play (the sound engine dropped it) is started again after this many ticks. */
    static final int RESTART_AFTER=40;
    /** Height of the player strip (pixels). */
    static final int PLAYER_HEIGHT=22;

    private static Track track,fanfare;
    private static final List<Track> fading=new ArrayList<>();
    private static RaidPlaylist.Song song;
    private static long ticks,songStartedAt,trackStartedAt,victoryUntil=-1,holdUntil=-1;

    static boolean enabled(){return ArsenalClientConfig.get(ArsenalClientConfig.RAID_MUSIC);}
    static boolean playerShown(){return ArsenalClientConfig.get(ArsenalClientConfig.MUSIC_PLAYER);}
    static void setEnabled(boolean on){ArsenalClientConfig.set(ArsenalClientConfig.RAID_MUSIC,on);if(!on){stopRaid(FADE_OUT);stopFanfare();holdUntil=-1;}}
    static void setPlayerShown(boolean on){ArsenalClientConfig.set(ArsenalClientConfig.MUSIC_PLAYER,on);}
    /** True while raid music owns the music: the game's own music manager waits (see the mixin). */
    public static boolean holdsMusic(){return enabled()&&(track!=null||fanfare!=null||!fading.isEmpty()||ticks<holdUntil);}

    /** The song now playing, or null. */
    static RaidPlaylist.Song nowPlaying(){return song;}
    /** Seconds into the current loop of the song now playing. */
    static int position(){return song==null?0:(int)((ticks-songStartedAt)/20%Math.max(1,song.seconds()));}
    private static boolean audible(){var o=Minecraft.getInstance().options;return o.getSoundSourceVolume(SoundSource.MASTER)>0&&o.getSoundSourceVolume(SoundSource.MUSIC)>0;}

    /** The raid was won: the raid song gives way and the fanfare plays at once. Returns false if the fanfare is not heard (off or muted). */
    static boolean victory(){
        var mc=Minecraft.getInstance();if(mc.level==null||!enabled())return false;
        stopRaid(FADE_FOR_VICTORY);stopFanfare();mc.getMusicManager().stopPlaying();
        fanfare=new Track(ArsenalSounds.VICTORY.get(),false,0);mc.getSoundManager().play(fanfare);
        victoryUntil=ticks+VICTORY_TICKS;holdUntil=victoryUntil+QUIET_AFTER;
        return audible();
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null){if(track!=null||fanfare!=null||!fading.isEmpty()||song!=null)reset();return;}
        if(!mc.isPaused())ticks++;
        // fades that the sound engine never ticked (no audio device, a reload) are cleared by the clock
        fading.removeIf(t->{if(t.isStopped()||ticks>t.fadeEnds+5){mc.getSoundManager().stop(t);return true;}return false;});
        if(fanfare!=null&&ticks>=victoryUntil){stopFanfare();}
        RaidPlaylist.Song want=enabled()&&ticks>=victoryUntil?BeaconClient.raidSong():null;
        if(!java.util.Objects.equals(want,song)){
            boolean wasPlaying=track!=null;stopRaid(FADE_OUT);song=want;songStartedAt=ticks;
            if(want!=null)start(mc);
            else if(wasPlaying&&holdUntil<ticks+QUIET_AFTER)holdUntil=ticks+QUIET_AFTER;   // defeat or walking away: a pause before the game's music
        }else if(track!=null&&!mc.getSoundManager().isActive(track)&&ticks-trackStartedAt>RESTART_AFTER)start(mc);   // stopped by the game (a resource reload): play it again
    }
    private static void start(Minecraft mc){
        var event=ArsenalSounds.song(song.id());if(event==null)return;
        if(track==null)mc.getMusicManager().stopPlaying();
        else{mc.getSoundManager().stop(track);}
        track=new Track(event,true,FADE_IN);trackStartedAt=ticks;mc.getSoundManager().play(track);
    }
    private static void stopRaid(int fade){if(track!=null){track.fadeOut(fade);fading.add(track);track=null;}if(fade>0)song=null;}
    private static void stopFanfare(){if(fanfare!=null){Minecraft.getInstance().getSoundManager().stop(fanfare);fanfare=null;}}
    private static void reset(){
        var sounds=Minecraft.getInstance().getSoundManager();
        if(track!=null)sounds.stop(track);if(fanfare!=null)sounds.stop(fanfare);for(var t:fading)sounds.stop(t);
        track=fanfare=null;fading.clear();song=null;victoryUntil=holdUntil=-1;
    }

    /** Width the player needs to show the whole name of the song now playing (0 when it is not shown); the status card widens to match. */
    static int playerWidth(Font font){
        if(song==null||!playerShown())return 0;
        return 25+font.width(Ui.t("music."+song.id()))+12+Math.max(font.width("00:00"),font.width(Ui.t("music.muted")))+6;
    }
    /**
     * The player: a strip with a moving level meter, the song's name, the time into it and a progress bar, at {@code x, y} (under the
     * status card). Shown for the whole raid while a song plays, whether or not the status card is.
     */
    static void drawPlayer(GuiGraphics g,Font font,int x,int y,int width){
        if(song==null||!playerShown())return;
        boolean muted=!audible();long now=ticks;float partial=Minecraft.getInstance().getFrameTime();
        g.fill(x-1,y-1,x+width+1,y+PLAYER_HEIGHT+1,Ui.SHADOW);g.fill(x,y,x+width,y+PLAYER_HEIGHT,0xe6111e28);
        g.fill(x,y,x+3,y+PLAYER_HEIGHT,Ui.BRASS);g.fill(x+3,y,x+width,y+1,Ui.SLATE_HI);
        // the level meter: four bars that dance while it plays and lie flat when muted
        for(int i=0;i<4;i++){
            float t=(now+partial)*.21f+i*1.7f;int bar=muted?1:2+Math.round(7*Math.abs(Mth.sin(t)*Mth.cos(t*.63f+i)));
            g.fill(x+9+i*3,y+13-bar,x+11+i*3,y+13,muted?Ui.TRACK:Ui.BRASS);
        }
        Component time=muted?Ui.t("music.muted"):Component.literal(RaidPlaylist.clock(position()));
        int timeWidth=font.width(time);
        Ui.text(g,font,Ui.t("music."+song.id()),x+25,y+5,Ui.INK,width-43-timeWidth);
        Ui.right(g,font,time,x+width-6,y+5,Ui.MUTED);
        float fraction=(position()+((now-songStartedAt)%20+partial)/20f)/Math.max(1,song.seconds());
        g.fill(x+9,y+17,x+width-6,y+19,Ui.TRACK);g.fill(x+9,y+17,x+9+Math.round((width-15)*Math.min(1,fraction)),y+19,muted?Ui.MUTED:Ui.BRASS);
    }

    /** A raid song or the fanfare on the Music channel: everywhere at once (no position), fading in and out by itself. */
    static final class Track extends AbstractTickableSoundInstance {
        private final int fadeIn;private int age,fadeOut=-1,fadeLength;private float from;long fadeEnds=Long.MAX_VALUE;
        Track(SoundEvent event,boolean loop,int fadeIn){
            super(event,SoundSource.MUSIC,SoundInstance.createUnseededRandom());
            looping=loop;delay=0;relative=true;attenuation=SoundInstance.Attenuation.NONE;x=y=z=0;this.fadeIn=fadeIn;volume=fadeIn>0?.001f:1f;
        }
        void fadeOut(int length){
            if(fadeOut>=0)return;
            if(length<=0){stop();fadeEnds=ticks;return;}
            fadeOut=fadeLength=length;from=volume;fadeEnds=ticks+length;
        }
        @Override public void tick(){
            age++;
            if(fadeOut>=0){if(--fadeOut<=0){stop();return;}volume=Math.max(.001f,from*fadeOut/fadeLength);}
            else volume=fadeIn>0?Math.min(1f,Math.max(.001f,age/(float)fadeIn)):1f;
        }
        @Override public boolean canStartSilent(){return true;}
    }
}
