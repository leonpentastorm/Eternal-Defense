package dev.eternaldefense.beacon.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import dev.eternaldefense.beacon.GiantHeads;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A grown elite's head counts for headshots where its head is drawn. */
@Pseudo
@Mixin(targets="com.tacz.guns.util.EntityUtil",remap=false)
public abstract class GiantHeadMixin {
    @Inject(method="getHitResult",at=@At("RETURN"),cancellable=true,remap=false,require=0)
    private static void eternal$giantHead(Projectile bullet,Entity target,Vec3 from,Vec3 to,CallbackInfoReturnable<EntityKineticBullet.EntityResult> info){
        var hit=info.getReturnValue();if(hit==null)return;
        boolean head=GiantHeads.headshot(target,hit.getHitPos(),hit.isHeadshot());
        if(head!=hit.isHeadshot())info.setReturnValue(new EntityKineticBullet.EntityResult(hit.getEntity(),hit.getHitPos(),head));
    }
}
