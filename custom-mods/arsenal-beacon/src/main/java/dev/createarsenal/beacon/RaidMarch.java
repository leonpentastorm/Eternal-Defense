package dev.createarsenal.beacon;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Raiders march straight at the beacon and only stop to dig when something blocks them; this tells which ones are blocked. */
final class RaidMarch {
    private RaidMarch(){}
    /** A siege creeper only digs within this many blocks of the protected zone. */
    static final int DIG_RANGE=24;
    /** Less than this many blocks of progress in one second counts as being blocked. */
    static final double MIN_PROGRESS=1.0;
    private static final Map<Integer,Vec3> LAST=new HashMap<>();
    /** Called once a second per raider: true when it hardly moved since the last call. */
    static boolean idle(Mob mob){
        var now=mob.position();var before=LAST.put(mob.getId(),now);
        return before!=null&&now.distanceToSqr(before)<MIN_PROGRESS*MIN_PROGRESS;
    }
    static void forget(){LAST.clear();}
    /** Pure: is the progress of one second too small? */
    static boolean blocked(double movedBlocks){return movedBlocks<MIN_PROGRESS;}
}
