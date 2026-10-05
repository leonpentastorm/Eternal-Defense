package dev.createarsenal.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client side of the support system: the turning cannon, the parcel with its parachute, and the platform screen. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SupportClient {
    private SupportClient(){}
    static final ResourceLocation TURRET=new ResourceLocation(ArsenalBeacon.ID,"block/support_cannon_turret"),BARREL=new ResourceLocation(ArsenalBeacon.ID,"block/support_cannon_barrel"),
        PARCEL=new ResourceLocation(ArsenalBeacon.ID,"block/support_parcel"),CHUTE=new ResourceLocation(ArsenalBeacon.ID,"block/support_parcel_chute");

    @SubscribeEvent public static void supportModels(ModelEvent.RegisterAdditional e){e.register(TURRET);e.register(BARREL);e.register(PARCEL);e.register(CHUTE);}
    @SubscribeEvent public static void supportRenderers(EntityRenderersEvent.RegisterRenderers e){
        e.registerBlockEntityRenderer(ArsenalBeacon.CANNON_ENTITY.get(),CannonRenderer::new);
        e.registerEntityRenderer(ArsenalBeacon.PARCEL.get(),ParcelRenderer::new);
        e.registerEntityRenderer(ArsenalBeacon.FLARE.get(),ThrownItemRenderer::new);
    }
    @SubscribeEvent public static void supportScreens(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(ArsenalBeacon.SUPPORT_MENU.get(),SupportScreen::new));}

    static void draw(PoseStack pose,MultiBufferSource buffer,RenderType type,ResourceLocation model,int light,int overlay){
        var mc=Minecraft.getInstance();BakedModel baked=mc.getModelManager().getModel(model);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffer.getBuffer(type),null,baked,1f,1f,1f,light,overlay,ModelData.EMPTY,type);
    }

    static final class CannonRenderer implements BlockEntityRenderer<SupportCannon.CannonEntity> {
        CannonRenderer(BlockEntityRendererProvider.Context ctx){}
        @Override public void render(SupportCannon.CannonEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            float yaw=Mth.lerp(partial,be.prevYaw,be.yaw);
            pose.pushPose();
            pose.translate(.5,.5625,.5);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.translate(-.5,-.5625,-.5);
            draw(pose,buffer,RenderType.cutout(),TURRET,light,overlay);
            float t=Math.max(0f,(be.recoil-partial)/SupportCannon.RECOIL_TICKS);
            pose.translate(0,0,Math.sin(t*Math.PI)*0.2);
            draw(pose,buffer,RenderType.cutout(),BARREL,light,overlay);
            pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(SupportCannon.CannonEntity be){return false;}
    }

    static final class ParcelRenderer extends EntityRenderer<SupportCrate.ParcelEntity> {
        ParcelRenderer(EntityRendererProvider.Context ctx){super(ctx);shadowRadius=.4f;}
        @Override public void render(SupportCrate.ParcelEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
            var type=RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
            pose.pushPose();pose.translate(0,0,0);
            float sway=e.falling()?(float)Math.sin((e.tickCount+partial)*.12)*4f:0f;
            pose.mulPose(Axis.ZP.rotationDegrees(sway));pose.translate(-.5,0,-.5);
            draw(pose,buffer,type,PARCEL,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            if(e.falling())draw(pose,buffer,type,CHUTE,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        @Override public ResourceLocation getTextureLocation(SupportCrate.ParcelEntity e){return TextureAtlas.LOCATION_BLOCKS;}
    }
}
