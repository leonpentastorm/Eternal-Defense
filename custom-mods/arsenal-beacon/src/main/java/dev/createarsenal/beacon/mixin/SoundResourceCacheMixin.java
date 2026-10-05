package dev.createarsenal.beacon.mixin;

import dev.createarsenal.beacon.SoundResourcePaths;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AAA Particles' ModernFix path relaxation must not expose malformed ordinary resource paths. */
@Pseudo
@Mixin(targets="org.embeddedt.modernfix.resources.PackResourcesCacheEngine",remap=false)
public abstract class SoundResourceCacheMixin {
    @Inject(method="isValidCachedResourcePath",at=@At("HEAD"),cancellable=true,remap=false)
    private static void arsenal$validateGunSounds(Path path,CallbackInfoReturnable<Boolean> result){
        if(SoundResourcePaths.malformedCachedPath(path.toString()))result.setReturnValue(false);
    }
}
