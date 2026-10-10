package dev.createarsenal.beacon.mixin;

import dev.createarsenal.beacon.RaidMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client only: while a raid song or the victory fanfare plays (and a short quiet after it), the game's music manager waits. See RaidMusic. */
@Mixin(MusicManager.class)
public abstract class RaidMusicMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void arsenal$raidMusic(CallbackInfo info){
        if(RaidMusic.holdsMusic())info.cancel();
    }
}
