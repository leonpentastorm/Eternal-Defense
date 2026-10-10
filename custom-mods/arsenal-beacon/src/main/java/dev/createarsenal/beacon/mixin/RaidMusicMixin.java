package dev.createarsenal.beacon.mixin;

import dev.createarsenal.beacon.RaidMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client only: during a raid near the beacon (and for the victory fanfare) the raid music replaces the game's own choice. See RaidMusic. */
@Mixin(Minecraft.class)
public abstract class RaidMusicMixin {
    @Inject(method="getSituationalMusic",at=@At("HEAD"),cancellable=true)
    private void arsenal$raidMusic(CallbackInfoReturnable<Music> result){
        Music music=RaidMusic.situational();
        if(music!=null)result.setReturnValue(music);
    }
}
