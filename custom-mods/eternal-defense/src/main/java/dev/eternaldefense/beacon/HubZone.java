package dev.eternaldefense.beacon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Where the Logistics Center Hubs reach. */
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
