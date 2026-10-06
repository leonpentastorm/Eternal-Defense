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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
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
        e.registerEntityRenderer(ArsenalBeacon.FLARE.get(),FlareRenderer::new);
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
            float k=(float)SupportCannon.SCALE,turn=(float)SupportCannon.TURN_Y;
            pose.translate(.5,turn,.5);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.scale(k,k,k);pose.translate(-.5,-turn,-.5);
            draw(pose,buffer,RenderType.cutout(),TURRET,light,overlay);
            // the barrel hinges on the turret and slides back along its own axis when it fires
            float t=Math.max(0f,(be.recoil-partial)/SupportCannon.RECOIL_TICKS);
            pose.translate(.5,1.7,.5);
            pose.mulPose(Axis.XP.rotationDegrees((float)SupportCannon.PITCH_DEGREES));
            pose.translate(0,0,Math.sin(t*Math.PI)*0.45);
            pose.translate(-.5,-.5,-1.5);   // the barrel model keeps its hinge at (8, 8, 24)
            draw(pose,buffer,RenderType.cutout(),BARREL,light,overlay);
            pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(SupportCannon.CannonEntity be){return false;}
    }

    /** Draws a flare with its own item sprite, a Return Flare as a standing purple portal, and a landed Fire Support Flare with its red area box. */
    static final class FlareRenderer extends EntityRenderer<SupportFlares.FlareEntity> {
        static final ResourceLocation PORTAL=new ResourceLocation(ArsenalBeacon.ID,"textures/entity/return_portal.png");
        private final net.minecraft.client.renderer.entity.ItemRenderer items;
        FlareRenderer(EntityRendererProvider.Context ctx){super(ctx);items=ctx.getItemRenderer();shadowRadius=0f;}
        @Override public void render(SupportFlares.FlareEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
            var kind=e.kind();boolean landed=e.landed();
            if(kind==SupportCalls.Kind.RETURN&&landed){portal(e,partial,pose,buffer);return;}
            Item item=switch(kind){case SUPPLY->ArsenalBeacon.SUPPLY_FLARE.get();case RETURN->ArsenalBeacon.RETURN_FLARE.get();case FIRE->ArsenalBeacon.FIRE_FLARE.get();};
            pose.pushPose();
            pose.translate(0,landed?.45:.12,0);pose.scale(1.4f,1.4f,1.4f);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());pose.mulPose(Axis.YP.rotationDegrees(180f));
            items.renderStatic(new ItemStack(item),ItemDisplayContext.GROUND,15728880,OverlayTexture.NO_OVERLAY,pose,buffer,e.level(),e.getId());
            pose.popPose();
            if(kind==SupportCalls.Kind.FIRE&&landed)area(e,partial,pose,buffer);
        }
        /** The area box, drawn exactly where the shells act. Red for the damaging types, green for healing, violet for curses. */
        private void area(SupportFlares.FlareEntity e,float partial,PoseStack pose,MultiBufferSource buffer){
            double r=e.radius();
            AABB box=new AABB(-r,-0.5,-r,r,r,r);
            float[] c=switch(e.type()){case HEAL->new float[]{.2f,1f,.35f};case CURSE->new float[]{.7f,.25f,1f};case NARUKAMI->new float[]{1f,.85f,.2f};case ARROW->new float[]{1f,.55f,.15f};default->new float[]{1f,.1f,.1f};};
            float pulse=.5f+.5f*Mth.sin((e.tickCount+partial)*.2f);
            DebugRenderer.renderFilledBox(pose,buffer,box,c[0],c[1],c[2],.10f+.08f*pulse);
            LevelRenderer.renderLineBox(pose,buffer.getBuffer(RenderType.lines()),box,c[0],Math.min(1f,c[1]+.15f),Math.min(1f,c[2]+.1f),1f);
        }
        private void portal(SupportFlares.FlareEntity e,float partial,PoseStack pose,MultiBufferSource buffer){
            float age=e.tickCount+partial;
            float grow=Math.min(1f,age/12f),pulse=1f+.04f*Mth.sin(age*.3f);
            int frame=(int)(age/3f)%4;float u0=frame/4f,u1=(frame+1)/4f;
            pose.pushPose();
            pose.scale(grow*pulse,grow*pulse,grow*pulse);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());pose.mulPose(Axis.YP.rotationDegrees(180f));
            var last=pose.last();var m=last.pose();var n=last.normal();
            var vc=buffer.getBuffer(RenderType.entityTranslucentEmissive(PORTAL));
            corner(vc,m,n,-.5f,0f,u0,1f);corner(vc,m,n,.5f,0f,u1,1f);corner(vc,m,n,.5f,2f,u1,0f);corner(vc,m,n,-.5f,2f,u0,0f);
            pose.popPose();
        }
        private static void corner(com.mojang.blaze3d.vertex.VertexConsumer vc,org.joml.Matrix4f m,org.joml.Matrix3f n,float x,float y,float u,float v){
            vc.vertex(m,x,y,0f).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(n,0f,1f,0f).endVertex();
        }
        @Override public ResourceLocation getTextureLocation(SupportFlares.FlareEntity e){return TextureAtlas.LOCATION_BLOCKS;}
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
