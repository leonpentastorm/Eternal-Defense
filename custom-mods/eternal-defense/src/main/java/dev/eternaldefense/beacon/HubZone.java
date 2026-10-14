package dev.eternaldefense.beacon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 0.0.41 (owner): where the Logistics Center Hubs reach. A player standing in the Home Zone of a base that has at least one working Hub
 * gets the Logistics Hub Zone effect (display only, like the Home Zone effect), so they can see that tables, kitchens and the beacon may
 * draw on the Hubs here; the beacon's Overview says how many Hubs work. A Hub works while it stands in the Home Zone of a standing beacon
 * ({@link HubData.Hub#working}); there is one beacon per world, so every working Hub belongs to it.
 */
final class HubZone {
    private HubZone(){}
    /** How often the effect is checked, in ticks. */
    static final int PERIOD=20;
    /** How many Hubs work for the base (the overworld's Home Zone). */
    static int active(ServerLevel level){
        var overworld=level.getServer().overworld();
        return (int)HubData.get(overworld).all().stream().filter(h->h.placed&&h.dimension==Level.OVERWORLD&&h.working(overworld)).count();
    }
    /** True while the player stands in the Home Zone of a standing beacon and at least one Hub works there. */
    static boolean inside(ServerPlayer p){
        return p.level().dimension()==Level.OVERWORLD&&BaseZone.problem(p.level(),p.blockPosition())==null&&active(p.serverLevel())>0;
    }
    /** Once a second: shows or clears the effect. */
    static void tick(ServerPlayer p){
        if(p.tickCount%PERIOD!=0)return;
        PlayerMeals.zoneEffect(p,MealEffects.HUB.get(),inside(p));
    }
}
