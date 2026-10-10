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
 * The radio answer when a flare's call is taken (0.0.19; it replaces the 0.0.16 flare siren): one of the owner's six fire support lines,
 * picked at random but never the same line twice in a row for one player. A fire support flare can get any line; a supply flare only
 * the two that fit a delivery (1 "payload incoming", 4 "stay out of the drop zone"); a return flare none. The line follows the player
 * who threw the flare, like their radio (others next to them hear it too), on the game's Voice/Speech volume slider. A refused call
 * (under cover without Quantum tunneling, a Bunker Buster at home) gets no answer.
 */
final class SupportChatter {
    private SupportChatter(){}
    /** Lines shipped: sounds/sfx/chatter_1.ogg ... chatter_6.ogg (tools/audio/import_chatter.py). */
    static final int LINES=6;
    static final int[] FIRE={1,2,3,4,5,6},SUPPLY={1,4},RETURN={};
    /** Volume 1: heard 16 blocks around the player who threw the flare. */
    static final float VOLUME=1f;
    private static final Map<UUID,Integer> LAST=new HashMap<>();

    static int[] lines(SupportCalls.Kind kind){return switch(kind){case FIRE->FIRE;case SUPPLY->SUPPLY;case RETURN->RETURN;};}
    /** Pure: the line to play (1-based), {@code roll} choosing among {@code lines} but never {@code last} while there is another; 0 for none. */
    static int pick(int[] lines,int last,int roll){
        if(lines.length==0)return 0;
        int[] choices=lines.length==1?lines:Arrays.stream(lines).filter(n->n!=last).toArray();
        if(choices.length==0)choices=lines;
        return choices[Math.floorMod(roll,choices.length)];
    }
    /** The call of a flare that landed at {@code flare} was taken: the thrower's radio answers. */
    static void say(ServerLevel level,Vec3 flare,SupportCalls.Kind kind,ServerPlayer owner){
        int line=pick(lines(kind),owner==null?0:LAST.getOrDefault(owner.getUUID(),0),level.random.nextInt());
        if(line==0)return;
        var sound=ArsenalSounds.CHATTER.get(line-1).get();
        if(owner!=null&&owner.isAlive()){LAST.put(owner.getUUID(),line);owner.level().playSound(null,owner,sound,SoundSource.VOICE,VOLUME,1f);}
        else level.playSound(null,flare.x,flare.y,flare.z,sound,SoundSource.VOICE,VOLUME,1f);
    }
}
