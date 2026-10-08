package dev.createarsenal.beacon.mixin;

import dev.createarsenal.beacon.MealGunCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.tacz.guns.util.ExplodeUtil",remap=false)
public abstract class MealExplosionMixin {
    @ModifyVariable(method="createExplosion(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;FFZZLnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),argsOnly=true,index=3,remap=false)
    private static float arsenal$mealRadius(float value,Entity owner,Entity exploder,float damage,float radius,boolean knockback,boolean destroy,Vec3 hit){return MealGunCompat.radius(owner,exploder,value);}
}
