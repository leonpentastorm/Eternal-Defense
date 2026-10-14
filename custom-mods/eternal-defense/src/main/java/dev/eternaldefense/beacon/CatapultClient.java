package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Supply Catapult drawn (0.0.33): the owner's static deck is the block's own model; the frame turns about the block's centre, the arm
 * pitches about the owner's pivot (8, 11, 6) inside the turned frame, and the box rides in the cup until it is thrown (the owner's README).
 */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CatapultClient {
    private CatapultClient(){}
    static final ResourceLocation FRAME=new ResourceLocation(EternalDefense.ID,"block/supply_catapult_frame"),ARM=new ResourceLocation(EternalDefense.ID,"block/supply_catapult_arm"),
        PAYLOAD=new ResourceLocation(EternalDefense.ID,"block/supply_catapult_payload");
    /** The arm's pivot (model pixels) and the box's middle at rest (model pixels; from the owner's payload model). */
    static final float PIVOT_X=8,PIVOT_Y=11,PIVOT_Z=6,BOX_X=8,BOX_Y=15.55f,BOX_Z=24;

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){e.register(FRAME);e.register(ARM);e.register(PAYLOAD);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){
        e.registerBlockEntityRenderer(EternalDefense.CATAPULT_ENTITY.get(),CatapultRenderer::new);
        e.registerEntityRenderer(EternalDefense.THROWN_SUPPLY.get(),ThrownRenderer::new);
    }

    static final class CatapultRenderer implements BlockEntityRenderer<SupplyCatapult.CatapultEntity> {
        CatapultRenderer(BlockEntityRendererProvider.Context ctx){}
        @Override public void render(SupplyCatapult.CatapultEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            float yaw=Mth.lerp(partial,be.prevYaw,be.yaw);
            pose.pushPose();
            pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.translate(-.5,0,-.5);
            SupportClient.draw(pose,buffer,RenderType.cutout(),FRAME,light,overlay);
            pose.translate(PIVOT_X/16,PIVOT_Y/16,PIVOT_Z/16);pose.mulPose(Axis.XP.rotationDegrees(be.armAngle(partial)));pose.translate(-PIVOT_X/16,-PIVOT_Y/16,-PIVOT_Z/16);
            SupportClient.draw(pose,buffer,RenderType.cutout(),ARM,light,overlay);
            if(be.payloadShown())SupportClient.draw(pose,buffer,RenderType.cutout(),PAYLOAD,light,overlay);
            pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(SupplyCatapult.CatapultEntity be){return false;}
    }

    /** The thrown box: the owner's payload crate, tumbling slowly. */
    static final class ThrownRenderer extends EntityRenderer<SupplyCatapult.ThrownSupply> {
        ThrownRenderer(EntityRendererProvider.Context ctx){super(ctx);}
        @Override public void render(SupplyCatapult.ThrownSupply e,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
            pose.pushPose();
            pose.translate(0,.2,0);pose.mulPose(Axis.XP.rotationDegrees((e.life+partial)*-9f));
            pose.translate(-BOX_X/16,-BOX_Y/16,-BOX_Z/16);
            SupportClient.draw(pose,buffer,RenderType.cutout(),PAYLOAD,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(e,yaw,partial,pose,buffer,light);
        }
        @Override public ResourceLocation getTextureLocation(SupplyCatapult.ThrownSupply e){return TextureAtlas.LOCATION_BLOCKS;}
    }
}
