package dev.eternaldefense.beacon;

import net.minecraft.server.level.ServerPlayer;

/** Where a player's meal counts as eaten at home: inside the Home Zone */
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
