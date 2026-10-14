package dev.eternaldefense.beacon.mixin;

import dev.eternaldefense.beacon.LoadoutCinematic;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client only: while the Loadout Station prints the local player, the camera takes the shot of the moment. */
@Mixin(Camera.class)
public abstract class LoadoutCameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="setup",at=@At("TAIL"))
    private void eternal$loadoutShot(BlockGetter level,Entity entity,boolean thirdPerson,boolean mirrored,float partial,CallbackInfo info){
        var shot=LoadoutCinematic.frame(entity,partial);if(shot==null)return;
        detached=true;setRotation((float)shot[3],(float)shot[4]);setPosition(new Vec3(shot[0],shot[1],shot[2]));
    }
}
