package dev.createarsenal.beacon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The radio answer when a flare's call is taken (0.0.19; it replaces the 0.0.16 flare siren): one of the owner's lines for that flare,
 * picked at random but never the same line twice in a row for one player. A fire support flare gets one of the six fire support lines;
 * since 0.0.22 a supply flare gets one of the owner's two supply lines (it used fire support lines 1 and 4 before) and a return flare one
 * of the three recall lines (it had none). The line follows the player who threw the flare, like their radio (others next to them hear it
 * too), on the game's Voice/Speech volume slider. A refused call (under cover without Quantum tunneling, a Bunker Buster at home) gets no answer.
 */
final class SupportChatter {
    private SupportChatter(){}
    /** Lines shipped per flare (tools/audio/import_chatter.py): sounds/sfx/chatter_1..6, supply_chatter_1..2, recall_chatter_1..3. */
    static final int FIRE_LINES=6,SUPPLY_LINES=2,RECALL_LINES=3;
    /** Volume 1: heard 16 blocks around the player who threw the flare. */
    static final float VOLUME=1f;
    /** The last line each player heard, per flare kind. */
    private static final Map<UUID,int[]> LAST=new HashMap<>();

    static int count(SupportCalls.Kind kind){return switch(kind){case FIRE->FIRE_LINES;case SUPPLY->SUPPLY_LINES;case RETURN->RECALL_LINES;};}
    /** The flare's own lines, 1-based. */
    static int[] lines(SupportCalls.Kind kind){return java.util.stream.IntStream.rangeClosed(1,count(kind)).toArray();}
    /** The sound event name of a line: {@code flare.chatter_N}, {@code flare.supply_chatter_N}, {@code flare.recall_chatter_N}. */
    static String event(SupportCalls.Kind kind,int line){return "flare."+switch(kind){case FIRE->"chatter_";case SUPPLY->"supply_chatter_";case RETURN->"recall_chatter_";}+line;}
    /** Pure: the line to play (1-based), {@code roll} choosing among {@code lines} but never {@code last} while there is another; 0 for none. */
    static int pick(int[] lines,int last,int roll){
        if(lines.length==0)return 0;
        int[] choices=lines.length==1?lines:Arrays.stream(lines).filter(n->n!=last).toArray();
        if(choices.length==0)choices=lines;
        return choices[Math.floorMod(roll,choices.length)];
    }
    /** The call of a flare that landed at {@code flare} was taken: the thrower's radio answers. */
    static void say(ServerLevel level,Vec3 flare,SupportCalls.Kind kind,ServerPlayer owner){
        int[] last=owner==null?new int[SupportCalls.Kind.values().length]:LAST.computeIfAbsent(owner.getUUID(),k->new int[SupportCalls.Kind.values().length]);
        int line=pick(lines(kind),last[kind.ordinal()],level.random.nextInt());
        if(line==0)return;
        var sound=ArsenalSounds.chatter(kind,line);
        if(owner!=null&&owner.isAlive()){last[kind.ordinal()]=line;owner.level().playSound(null,owner,sound,SoundSource.VOICE,VOLUME,1f);}
        else level.playSound(null,flare.x,flare.y,flare.z,sound,SoundSource.VOICE,VOLUME,1f);
    }
}
