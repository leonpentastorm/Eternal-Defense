package dev.createarsenal.beacon;

import net.minecraft.server.level.ServerPlayer;

/**
 * Where a player's meal counts as eaten at home (0.0.23): inside the Home Zone ({@link BaseZone#problem} finds nothing wrong there, the rule the
 * meal timer always used) or inside the zone of an ACTIVE Forward Operating Base. {@link BaseZone#problem} itself is not changed: it is the
 * build rule of the beacon zone and must never take FOB zones in.
 */
final class MealZone {
    private MealZone(){}
    enum Kind{NONE,HOME,FOB;boolean home(){return this!=NONE;}}
    /** Pure: the Home Zone wins where the two overlap. */
    static Kind of(boolean inHome,boolean inWorkingFob){return inHome?Kind.HOME:inWorkingFob?Kind.FOB:Kind.NONE;}
    static Kind at(ServerPlayer p){
        boolean home=BaseZone.problem(p.level(),p.blockPosition())==null;
        return of(home,!home&&Fobs.workingZoneAt(p.serverLevel(),p.blockPosition())!=null);
    }
}
