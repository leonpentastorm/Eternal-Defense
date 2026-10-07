package dev.createarsenal.beacon.mixin;

import dev.createarsenal.beacon.MealGunCompat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import java.util.List;

@Pseudo
@Mixin(targets="com.tacz.guns.item.ModernKineticGunItem",remap=false)
public abstract class MealBashMixin {
    @ModifyVariable(method="doPerLivingHurt",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private static float arsenal$mealKnockback(float value,LivingEntity user,LivingEntity target,float knockback,float damage,List<?> effects){return MealGunCompat.scale(user,"heavy_hand",value);}
    @ModifyVariable(method="doPerLivingHurt",at=@At("HEAD"),argsOnly=true,ordinal=1,remap=false)
    private static float arsenal$mealBash(float value,LivingEntity user,LivingEntity target,float knockback,float damage,List<?> effects){return MealGunCompat.scale(user,"brawler",value);}
}
