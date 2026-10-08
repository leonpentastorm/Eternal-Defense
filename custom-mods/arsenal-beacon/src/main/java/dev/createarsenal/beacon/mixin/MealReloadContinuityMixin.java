package dev.createarsenal.beacon.mixin;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.shooter.*;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import dev.createarsenal.beacon.MealGunBackend;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cooperates with the pinned backend's timestamp scale/restore pair. The backend field is resolved after transformation.
 * The correction runs at the native gun call, after all HEAD callbacks, independent of their injection ordering.
 */
@Mixin(value=LivingEntityReload.class,priority=900,remap=false)
public abstract class MealReloadContinuityMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;
    @Unique private final MealGunBackend.ReloadTransition arsenal$transition=new MealGunBackend.ReloadTransition();
    @Unique private long arsenal$beforeReload;
    @Unique private double arsenal$rate=1;

    @Inject(method="reload",at=@At("HEAD"))
    private void arsenal$beforeReload(CallbackInfo ci){arsenal$beforeReload=data.reloadTimestamp;}
    @Inject(method="reload",at=@At("RETURN"))
    private void arsenal$beginReload(CallbackInfo ci){
        if(data.reloadTimestamp<0)arsenal$transition.reset();
        else if(data.reloadTimestamp!=arsenal$beforeReload&&data.currentGunItem!=null)
            arsenal$transition.begin(data.currentGunItem.get(),MealGunBackend.reloadRate(shooter));
    }
    @Inject(method="cancelReload",at=@At("RETURN"))
    private void arsenal$cancelReload(CallbackInfo ci){if(data.reloadTimestamp<0||!data.reloadStateType.isReloading())arsenal$transition.reset();}

    @Inject(method="tickReloadState",at=@At(value="INVOKE",target="Lcom/tacz/guns/api/item/gun/AbstractGunItem;tickReload(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Lcom/tacz/guns/api/entity/ReloadState;"))
    private void arsenal$preserveProgress(CallbackInfoReturnable<ReloadState> ci){
        arsenal$rate=MealGunBackend.reloadRate(shooter);
        long backend=MealGunBackend.backendTimestamp(this),original=backend<0?data.reloadTimestamp:backend;
        if(original<0||data.currentGunItem==null){arsenal$transition.reset();return;}
        long now=System.currentTimeMillis(),rebased=arsenal$transition.rebase(data.currentGunItem.get(),original,arsenal$rate,now);
        if(rebased==original)return;
        if(backend>=0){
            MealGunBackend.backendTimestamp(this,rebased);
            data.reloadTimestamp=now-Math.round((now-rebased)*arsenal$rate);
        }else data.reloadTimestamp=rebased; // The backend intentionally skips scaling at rate 1.
    }
    @Redirect(method="tickReloadState",at=@At(value="INVOKE",target="Lcom/tacz/guns/api/item/gun/AbstractGunItem;tickReload(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Lcom/tacz/guns/api/entity/ReloadState;"))
    private ReloadState arsenal$retainNativeStageChanges(AbstractGunItem gun,ShooterDataHolder holder,ItemStack stack,LivingEntity user){
        long before=holder.reloadTimestamp;var result=gun.tickReload(holder,stack,user);
        // Native single-round scripts adjustReloadTime between shells. Preserve that delta across the backend's restoration.
        long backend=MealGunBackend.backendTimestamp(this);
        if(backend>=0&&holder.reloadTimestamp>=0&&holder.reloadTimestamp!=before)
            MealGunBackend.backendTimestamp(this,backend+Math.round((holder.reloadTimestamp-before)/arsenal$rate));
        if(!result.getStateType().isReloading())arsenal$transition.reset();
        return result;
    }
}
