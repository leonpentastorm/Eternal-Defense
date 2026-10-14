package dev.eternaldefense.beacon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.eternaldefense.beacon.LoadoutCinematic;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client only: no first-person hands or held items (other mods' guns included) while a Loadout camera shot is on. */
@Mixin(GameRenderer.class)
public abstract class LoadoutHandMixin {
    @Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true)
    private void eternal$loadoutHands(PoseStack pose,Camera camera,float partial,CallbackInfo info){if(LoadoutCinematic.hidingHands())info.cancel();}
}
