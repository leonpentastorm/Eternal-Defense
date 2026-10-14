package dev.eternaldefense.beacon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The radio answer when a flare's call is taken: one of the lines for that flare */
final class SupportChatter {
    private SupportChatter(){}
    /** Lines shipped per flare (tools/audio/import_chatter.py) */
    static final int FIRE_LINES=6,SUPPLY_LINES=2,RECALL_LINES=3;
    /** Volume 1: heard 16 blocks around the player who threw the flare. */
    static final float VOLUME=1f;
    /** How long each line plays, in ticks rounded up */
    static final int[] FIRE_TICKS={80,93,79,82,95,71},SUPPLY_TICKS={85,60},RECALL_TICKS={74,64,72};
    /** The longest line, in ticks: a radio claimed further ahead than this is from an earlier server run and counts */
    static final int LONGEST=100;
    /** The server tick each player's current radio line ends. */
    private static final Map<UUID,Integer> ON_AIR=new HashMap<>();
    /** The last line each player heard, per flare kind. */
    private static final Map<UUID,int[]> LAST=new HashMap<>();

    static int count(SupportCalls.Kind kind){return switch(kind){case FIRE->FIRE_LINES;case SUPPLY->SUPPLY_LINES;case RETURN->RECALL_LINES;};}
    /** The flare's own lines, 1-based. */
    static int[] lines(SupportCalls.Kind kind){return java.util.stream.IntStream.rangeClosed(1,count(kind)).toArray();}
    /** The sound event name of a line: flare.chatter_N, flare.supply_chatter_N, flare.recall_chatter_N. */
    static String event(SupportCalls.Kind kind,int line){return "flare."+switch(kind){case FIRE->"chatter_";case SUPPLY->"supply_chatter_";case RETURN->"recall_chatter_";}+line;}
    /** Pure: the line to play (1-based), roll choosing among lines but never last while there is another; 0 for none. */
    static int pick(int[] lines,int last,int roll){
        if(lines.length==0)return 0;
        int[] choices=lines.length==1?lines:Arrays.stream(lines).filter(n->n!=last).toArray();
        if(choices.length==0)choices=lines;
        return choices[Math.floorMod(roll,choices.length)];
    }
    static int ticks(SupportCalls.Kind kind,int line){return (switch(kind){case FIRE->FIRE_TICKS;case SUPPLY->SUPPLY_TICKS;case RETURN->RECALL_TICKS;})[line-1];}
    /** Pure: may a new line start at tick now when the last one ends at until (a stale until counts as free). */
    static boolean free(int now,int until){return now>=until||until-now>LONGEST;}
    /** Claims the player's radio for ticks when it is free; false while a line is still playing (then nothing plays). */
    static boolean claim(ServerPlayer p,int ticks){
        int now=p.server.getTickCount();
        if(!free(now,ON_AIR.getOrDefault(p.getUUID(),now)))return false;
        ON_AIR.put(p.getUUID(),now+ticks);return true;
    }
    /** The call of a flare that landed at flare was taken: the thrower's radio answers. */
    static void say(ServerLevel level,Vec3 flare,SupportCalls.Kind kind,ServerPlayer owner){
        int[] last=owner==null?new int[SupportCalls.Kind.values().length]:LAST.computeIfAbsent(owner.getUUID(),k->new int[SupportCalls.Kind.values().length]);
        int line=pick(lines(kind),last[kind.ordinal()],level.random.nextInt());
        if(line==0)return;
        var sound=DefenseSounds.chatter(kind,line);
        if(owner!=null&&owner.isAlive()){if(!claim(owner,ticks(kind,line)))return;last[kind.ordinal()]=line;owner.level().playSound(null,owner,sound,SoundSource.VOICE,VOLUME,1f);}
        else level.playSound(null,flare.x,flare.y,flare.z,sound,SoundSource.VOICE,VOLUME,1f);
    }
}
