package dev.eternaldefense.beacon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.eternaldefense.beacon.EliteClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client only: a grown elite is drawn at its size whatever renders it (vanilla, GeckoLib or model packs). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EliteScaleMixin {
    private static final String DRAW="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    @Inject(method="render",at=@At(value="INVOKE",target=DRAW,shift=At.Shift.BEFORE),require=0)
    private <E extends Entity> void eternal$growElite(E entity,double x,double y,double z,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo info){EliteClient.begin(entity,pose);}
    @Inject(method="render",at=@At(value="INVOKE",target=DRAW,shift=At.Shift.AFTER),require=0)
    private <E extends Entity> void eternal$endElite(E entity,double x,double y,double z,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo info){EliteClient.end(entity,pose);}
}
