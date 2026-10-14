package dev.eternaldefense.beacon.mixin;

import dev.eternaldefense.beacon.MealGunCompat;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** The radius passed to CustomExplosion is shared by its falloff, blocks and radius-dependent presentation. */
@Pseudo
@Mixin(targets="me.xjqsh.lrtactical.entity.GrenadeEntity",remap=false)
public abstract class MealGrenadeMixin {
    @Shadow public abstract float getRadius();
    @Redirect(method="onDeath",at=@At(value="INVOKE",target="Lme/xjqsh/lrtactical/entity/GrenadeEntity;getRadius()F"),remap=false)
    private float eternal$mealGrenadeRadius(@Coerce Object grenade){return MealGunCompat.grenadeRadius((Projectile)grenade,getRadius());}
}
