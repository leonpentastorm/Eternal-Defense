package dev.eternaldefense.beacon.mixin;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.shooter.*;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import dev.eternaldefense.beacon.MealGunBackend;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cooperates with the pinned backend's timestamp scale/restore pair. */
@Mixin(value=LivingEntityReload.class,priority=900,remap=false)
public abstract class MealReloadContinuityMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;
    @Unique private final MealGunBackend.ReloadTransition eternal$transition=new MealGunBackend.ReloadTransition();
    @Unique private long eternal$beforeReload;
    @Unique private double eternal$rate=1;

    @Inject(method="reload",at=@At("HEAD"),require=0)
    private void eternal$beforeReload(CallbackInfo ci){eternal$beforeReload=data.reloadTimestamp;}
    @Inject(method="reload",at=@At("RETURN"),require=0)
    private void eternal$beginReload(CallbackInfo ci){
        if(data.reloadTimestamp<0)eternal$transition.reset();
        else if(data.reloadTimestamp!=eternal$beforeReload&&data.currentGunItem!=null)
            eternal$transition.begin(data.currentGunItem.get(),MealGunBackend.reloadRate(shooter));
    }
    @Inject(method="cancelReload",at=@At("RETURN"),require=0)
    private void eternal$cancelReload(CallbackInfo ci){if(data.reloadTimestamp<0||!data.reloadStateType.isReloading())eternal$transition.reset();}

    @Inject(method="tickReloadState",at=@At(value="INVOKE",target="Lcom/tacz/guns/api/item/gun/AbstractGunItem;tickReload(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Lcom/tacz/guns/api/entity/ReloadState;"),require=0)
    private void eternal$preserveProgress(CallbackInfoReturnable<ReloadState> ci){
        eternal$rate=MealGunBackend.reloadRate(shooter);
        long backend=MealGunBackend.backendTimestamp(this),original=backend<0?data.reloadTimestamp:backend;
        if(original<0||data.currentGunItem==null){eternal$transition.reset();return;}
        long now=System.currentTimeMillis(),rebased=eternal$transition.rebase(data.currentGunItem.get(),original,eternal$rate,now);
        if(rebased==original)return;
        if(backend>=0){
            MealGunBackend.backendTimestamp(this,rebased);
            data.reloadTimestamp=now-Math.round((now-rebased)*eternal$rate);
        }else data.reloadTimestamp=rebased; // The backend intentionally skips scaling at rate 1.
    }
    @Redirect(method="tickReloadState",at=@At(value="INVOKE",target="Lcom/tacz/guns/api/item/gun/AbstractGunItem;tickReload(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Lcom/tacz/guns/api/entity/ReloadState;"),require=0)
    private ReloadState eternal$retainNativeStageChanges(AbstractGunItem gun,ShooterDataHolder holder,ItemStack stack,LivingEntity user){
        long before=holder.reloadTimestamp;var result=gun.tickReload(holder,stack,user);
        // Native single-round scripts adjustReloadTime between shells.
        long backend=MealGunBackend.backendTimestamp(this);
        if(backend>=0&&holder.reloadTimestamp>=0&&holder.reloadTimestamp!=before)
            MealGunBackend.backendTimestamp(this,backend+Math.round((holder.reloadTimestamp-before)/eternal$rate));
        if(!result.getStateType().isReloading())eternal$transition.reset();
        return result;
    }
}
