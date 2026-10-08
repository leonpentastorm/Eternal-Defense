package dev.createarsenal.beacon.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent daylight ignition at its source; never remove fire caused by weapons or terrain. */
@Mixin(Mob.class)
public abstract class RaiderSunlightMixin {
    @Inject(method="isSunBurnTick",at=@At("HEAD"),cancellable=true)
    private void arsenal$raidersIgnoreSunlight(CallbackInfoReturnable<Boolean> ci){
        if(((Mob)(Object)this).getPersistentData().getBoolean("arsenalRaider"))ci.setReturnValue(false);
    }
}
