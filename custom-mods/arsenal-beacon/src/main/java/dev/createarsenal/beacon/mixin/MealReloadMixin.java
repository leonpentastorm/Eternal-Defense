package dev.createarsenal.beacon.mixin;

import dev.createarsenal.beacon.MealGunCompat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.tacz.guns.entity.shooter.LivingEntityReload",remap=false)
public abstract class MealReloadMixin {
    @Shadow private LivingEntity shooter;
    @Unique private final MealGunCompat.ReloadClock arsenal$mealReload=new MealGunCompat.ReloadClock();
    @Inject(method="tickReloadState",at=@At("HEAD"),remap=false)
    private void arsenal$advanceMealReload(CallbackInfoReturnable<?> ci){arsenal$mealReload.advance(shooter);}
}
