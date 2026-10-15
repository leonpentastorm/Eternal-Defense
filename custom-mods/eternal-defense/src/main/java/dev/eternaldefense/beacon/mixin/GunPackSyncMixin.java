package dev.eternaldefense.beacon.mixin;

import dev.eternaldefense.beacon.GunPackSync;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.tacz.guns.network.NetworkHandler",remap=false)
public abstract class GunPackSyncMixin {
    @Inject(method="sendToClientPlayer",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void eternal$splitCatalogue(Object message,Player player,CallbackInfo ci){if(GunPackSync.intercept(message,player))ci.cancel();}
}
