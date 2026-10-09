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
        PARCEL=new ResourceLocation(ArsenalBeacon.ID,"block/support_parcel");

    @SubscribeEvent public static void supportModels(ModelEvent.RegisterAdditional e){e.register(TURRET);e.register(BARREL);e.register(PARCEL);}
    @SubscribeEvent public static void supportRenderers(EntityRenderersEvent.RegisterRenderers e){
        e.registerBlockEntityRenderer(ArsenalBeacon.CANNON_ENTITY.get(),CannonRenderer::new);
        e.registerBlockEntityRenderer(ArsenalBeacon.SUPPORT_PLATFORM_ENTITY.get(),PlatformRenderer::new);
        e.registerEntityRenderer(ArsenalBeacon.PARCEL.get(),ParcelRenderer::new);
        e.registerEntityRenderer(ArsenalBeacon.FLARE.get(),FlareRenderer::new);
        e.registerEntityRenderer(ArsenalBeacon.RAIDER_GATE.get(),GateRenderer::new);
    }
    @SubscribeEvent public static void supportScreens(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(ArsenalBeacon.SUPPORT_MENU.get(),SupportScreen::new));}

    static void draw(PoseStack pose,MultiBufferSource buffer,RenderType type,ResourceLocation model,int light,int overlay){
        var mc=Minecraft.getInstance();BakedModel baked=mc.getModelManager().getModel(model);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffer.getBuffer(type),null,baked,1f,1f,1f,light,overlay,ModelData.EMPTY,type);
    }

    // ---- owner nameplates ---------------------------------------------------------------------------------------------------
    static final ResourceLocation PLATE=new ResourceLocation(ArsenalBeacon.ID,"textures/entity/nameplate.png");
    /** Names of more than this many letters are split over two lines so they stay readable on a small plate. */
    static final int PLATE_LINE=9;
    static java.util.List<String> plateLines(String name){
        if(name==null||name.isBlank())return java.util.List.of("?");
        if(name.length()<=PLATE_LINE)return java.util.List.of(name);
        int cut=name.length()/2;
        for(int d=0;d<=cut-2;d++){for(int c:new int[]{cut-d,cut+d}){if(c>2&&c<name.length()-2&&(name.charAt(c)=='_'||name.charAt(c)=='-')){return java.util.List.of(name.substring(0,c+(name.charAt(c)=='_'?0:1)).replace("_",""),name.substring(c+1));}}}
        return java.util.List.of(name.substring(0,cut),name.substring(cut));
    }
    /**
     * A brass-framed plate with the owner's name, standing at {@code (cx, bottom, z)} (block units, in the block's own frame) and
     * facing +Z. {@code width} is the plate width in blocks; the height follows the number of lines; {@code tilt} leans it back.
     */
    static void plate(PoseStack pose,MultiBufferSource buffer,net.minecraft.client.gui.Font font,String name,int light,float cx,float bottom,float z,float width,float tilt){
        var lines=plateLines(name);int textW=1;for(var l:lines)textW=Math.max(textW,font.width(l));
        float pad=.035f,s=Math.min(.03f,(width-2*pad)/textW),h=lines.size()*9*s+2*pad;
        pose.pushPose();pose.translate(cx,bottom,z);pose.mulPose(Axis.XP.rotationDegrees(-tilt));
        var last=pose.last();var m=last.pose();var n=last.normal();
        var vc=buffer.getBuffer(RenderType.entityCutout(PLATE));
        float hw=width/2;
        quad(vc,m,n,-hw,0,hw,h,0f,light,.25f);                                    // brass frame
        quad(vc,m,n,-hw+.018f,.018f,hw-.018f,h-.018f,.004f,light,.75f);          // dark face
        pose.translate(-textW*s/2f,h-pad,.009f);pose.scale(s,-s,s);
        for(int i=0;i<lines.size();i++){
            float lw=font.width(lines.get(i));
            font.drawInBatch(lines.get(i),(textW-lw)/2f,i*9,0xfff1cc78,false,pose.last().pose(),buffer,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,light);
        }
        pose.popPose();
    }
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc,org.joml.Matrix4f m,org.joml.Matrix3f n,float x0,float y0,float x1,float y1,float z,int light,float u){
        float v=.5f;
        for(float[] c:new float[][]{{x0,y0},{x1,y0},{x1,y1},{x0,y1}})
            vc.vertex(m,c[0],c[1],z).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0f,0f,1f).endVertex();
    }

    /** The owner's rickety little sign, stuck on top of the platform. It is only drawn (no hitbox), so the platform stays two blocks tall. */
    static final class PlatformRenderer implements BlockEntityRenderer<SupportPlatform.PlatformEntity> {
        private final net.minecraft.client.gui.Font font;
        PlatformRenderer(BlockEntityRendererProvider.Context ctx){font=ctx.getFont();}
        @Override public void render(SupportPlatform.PlatformEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            if(be.ownerName==null||be.ownerName.isEmpty())return;
            var facing=be.getBlockState().getValue(SupportPlatform.FACING);
            pose.pushPose();
            pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-((facing.toYRot()+180f)%360f)));
            // the post, two crossed strips from the top cap of the platform
            var vc=buffer.getBuffer(RenderType.entityCutoutNoCull(PLATE)); // no culling: a flat strip would vanish seen from its back
            pose.pushPose();pose.translate(-.18,1.9,0);pose.mulPose(Axis.ZP.rotationDegrees(-5f));
            for(int side=0;side<2;side++){var last=pose.last();quad(vc,last.pose(),last.normal(),-.035f,0,.035f,.55f,0f,light,.25f);pose.mulPose(Axis.YP.rotationDegrees(90f));}
            pose.popPose();
            // the board hangs a little crooked from the post, readable from both sides
            pose.pushPose();pose.translate(-.18,2.32,0);pose.mulPose(Axis.ZP.rotationDegrees(9f));
            for(int side=0;side<2;side++){plate(pose,buffer,font,be.ownerName,light,0f,-.12f,.012f,.8f,0f);pose.mulPose(Axis.YP.rotationDegrees(180f));}
            pose.popPose();
            pose.popPose();
        }
    }

    static final class CannonRenderer implements BlockEntityRenderer<SupportCannon.CannonEntity> {
        private final net.minecraft.client.gui.Font font;
        CannonRenderer(BlockEntityRendererProvider.Context ctx){font=ctx.getFont();}
        @Override public void render(SupportCannon.CannonEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            if(be.ownerName!=null&&!be.ownerName.isEmpty()){
                pose.pushPose();plate(pose,buffer,font,be.ownerName,light,.5f,3.2f/16f,30.4f/16f,1.5f,14f);pose.popPose();
            }
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

    /** The red parachute of a paratrooper (any raider marked as floating down) and the red exclamation mark above every raider. */
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
    public static final class TrooperChutes {
        static final ResourceLocation RED=new ResourceLocation(ArsenalBeacon.ID,"textures/entity/parachute_red.png");
        static final ResourceLocation MARK=new ResourceLocation(ArsenalBeacon.ID,"textures/entity/raid_mark.png");
        @SubscribeEvent public static void draw(net.minecraftforge.client.event.RenderLivingEvent.Post<?,?> e){
            var mob=e.getEntity();
            if(!mob.isAlive())return;
            boolean chute=RaidTypes.CHUTED.contains(mob.getId())&&!mob.onGround();
            if(chute){
                var pose=e.getPoseStack();pose.pushPose();pose.translate(0,mob.getBbHeight()*.8,0);
                ParcelRenderer.canopy(pose,e.getMultiBufferSource(),e.getPackedLight(),mob.tickCount+e.getPartialTick(),RED);pose.popPose();
            }
            if(RaidTypes.RAIDERS.contains(mob.getId())&&mob.distanceToSqr(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())<=96*96)
                mark(e.getPoseStack(),e.getMultiBufferSource(),mob,chute?mob.getBbHeight()+3.6f:mob.getBbHeight()+.75f,mob.tickCount+e.getPartialTick(),net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        }
        /** A camera-facing exclamation mark that bobs above the head; it is drawn fully bright and a little larger the farther away the mob is. */
        static void mark(PoseStack pose,MultiBufferSource buffer,net.minecraft.world.entity.LivingEntity mob,float height,float age,org.joml.Quaternionf camera){
            double distance=Math.sqrt(mob.distanceToSqr(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
            float size=.5f+(float)Math.min(.9,distance/60.0*.9),bob=(float)Math.sin(age*.2)*.06f;
            pose.pushPose();pose.translate(0,height+bob,0);pose.mulPose(camera);pose.scale(size,size,size);
            var vc=buffer.getBuffer(RenderType.entityCutoutNoCull(MARK));var last=pose.last();var m=last.pose();var n=last.normal();
            float h=.5f;int light=15728880;
            vc.vertex(m,-h,0,0).color(255,255,255,255).uv(0,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
            vc.vertex(m,h,0,0).color(255,255,255,255).uv(1,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
            vc.vertex(m,h,1f,0).color(255,255,255,255).uv(1,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
            vc.vertex(m,-h,1f,0).color(255,255,255,255).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
            pose.popPose();
        }
    }

    /** Draws a flare with its own item sprite, a Return Flare as a standing purple portal, and a landed Fire Support Flare with its red area box. */
    static final class FlareRenderer extends EntityRenderer<SupportFlares.FlareEntity> {
        static final ResourceLocation PORTAL=new ResourceLocation(ArsenalBeacon.ID,"textures/entity/return_portal.png");
        private final net.minecraft.client.renderer.entity.ItemRenderer items;
        FlareRenderer(EntityRendererProvider.Context ctx){super(ctx);items=ctx.getItemRenderer();shadowRadius=0f;}
        @Override public void render(SupportFlares.FlareEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
            var kind=e.kind();boolean landed=e.landed();
            if(kind==SupportCalls.Kind.RETURN&&e.open()){drawPortal(pose,buffer,entityRenderDispatcher,e.tickCount+partial,3f,255,255,255,255);return;}
            Item item=switch(kind){case SUPPLY->ArsenalBeacon.SUPPLY_FLARE.get();case RETURN->ArsenalBeacon.RETURN_FLARE.get();case FIRE->ArsenalBeacon.FIRE_FLARE.get();};
            pose.pushPose();
            pose.translate(0,landed?.45:.12,0);pose.scale(1.4f,1.4f,1.4f);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());pose.mulPose(Axis.YP.rotationDegrees(180f));
            items.renderStatic(new ItemStack(item),ItemDisplayContext.GROUND,15728880,OverlayTexture.NO_OVERLAY,pose,buffer,e.level(),e.getId());
            pose.popPose();
            if(kind==SupportCalls.Kind.FIRE&&landed)area(e,partial,pose,buffer);
        }
        /**
         * The area box, drawn exactly where the shells act, as edges only: a filled translucent box writes depth and hid everything behind it
         * (the shop and platform next to a flare vanished). Red for the damaging types, green for healing, violet for curses.
         */
        private void area(SupportFlares.FlareEntity e,float partial,PoseStack pose,MultiBufferSource buffer){
            double r=e.radius();
            AABB box=e.type()==CannonUpgrades.FireType.BUNKER?new AABB(-r,-r,-r,r,r,r):new AABB(-r,-0.5,-r,r,r,r);
            float[] c=switch(e.type()){case HEAL->new float[]{.2f,1f,.35f};case CURSE->new float[]{.7f,.25f,1f};case NARUKAMI->new float[]{1f,.85f,.2f};case ARROW->new float[]{1f,.55f,.15f};default->new float[]{1f,.1f,.1f};};
            float pulse=.75f+.25f*Mth.sin((e.tickCount+partial)*.2f);
            LevelRenderer.renderLineBox(pose,buffer.getBuffer(RenderType.lines()),box,c[0],c[1],c[2],pulse);
        }
        @Override public ResourceLocation getTextureLocation(SupportFlares.FlareEntity e){return TextureAtlas.LOCATION_BLOCKS;}
    }

    /**
     * A standing portal, two blocks tall, turned towards the camera, drawn from the Return portal sprite (four frames side by side). It grows in over
     * its first twelve ticks. {@code frameTicks} is how long one frame lasts; the colour tints the sprite. The player's Return portal draws it white
     * and fast, the Raider Gate red and slow, so the two can never be mistaken for each other.
     */
    static void drawPortal(PoseStack pose,MultiBufferSource buffer,net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher,float age,float frameTicks,int red,int green,int blue,int alpha){
        float grow=Math.min(1f,age/12f),pulse=1f+.04f*Mth.sin(age*.3f);
        int frame=(int)(age/frameTicks)%4;float u0=frame/4f,u1=(frame+1)/4f;
        pose.pushPose();
        pose.scale(grow*pulse,grow*pulse,grow*pulse);
        pose.mulPose(dispatcher.cameraOrientation());pose.mulPose(Axis.YP.rotationDegrees(180f));
        var last=pose.last();var m=last.pose();var n=last.normal();
        var vc=buffer.getBuffer(RenderType.entityTranslucentEmissive(FlareRenderer.PORTAL));
        corner(vc,m,n,-.5f,0f,u0,1f,red,green,blue,alpha);corner(vc,m,n,.5f,0f,u1,1f,red,green,blue,alpha);corner(vc,m,n,.5f,2f,u1,0f,red,green,blue,alpha);corner(vc,m,n,-.5f,2f,u0,0f,red,green,blue,alpha);
        pose.popPose();
    }
    private static void corner(com.mojang.blaze3d.vertex.VertexConsumer vc,org.joml.Matrix4f m,org.joml.Matrix3f n,float x,float y,float u,float v,int red,int green,int blue,int alpha){
        vc.vertex(m,x,y,0f).color(red,green,blue,alpha).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(n,0f,1f,0f).endVertex();
    }
    /** The gate a stuck raider channels beside: the Return portal sprite in red, at about half the speed. Nothing else is drawn. */
    static final class GateRenderer extends EntityRenderer<RaiderGate> {
        GateRenderer(EntityRendererProvider.Context ctx){super(ctx);shadowRadius=0f;}
        @Override public void render(RaiderGate e,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
            drawPortal(pose,buffer,entityRenderDispatcher,e.tickCount+partial,7f,255,45,45,235);
        }
        @Override public ResourceLocation getTextureLocation(RaiderGate e){return FlareRenderer.PORTAL;}
    }

    /**
     * The parcel and, while it falls, its parachute: a domed canopy of twelve alternating panels on twelve cords. The canopy is drawn from
     * vertices so it can be as big as it needs to be (about 5 blocks across) instead of being limited to a block model's size.
     */
    static final class ParcelRenderer extends EntityRenderer<SupportCrate.ParcelEntity> {
        static final ResourceLocation CHUTE=new ResourceLocation(ArsenalBeacon.ID,"textures/entity/parachute.png");
        static final int GORES=12,RINGS=5;
        /** Canopy size in blocks: half-width at the rim, dome height, and rim height above the top of the crate. */
        static final float RADIUS=2.5f,DOME=1.6f,RIM=3.6f,CRATE_TOP=14/16f;
        ParcelRenderer(EntityRendererProvider.Context ctx){super(ctx);shadowRadius=.4f;}
        @Override public void render(SupportCrate.ParcelEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
            var type=RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
            pose.pushPose();
            float age=e.tickCount+partial;
            float sway=e.falling()?(float)Math.sin(age*.12)*3f:0f;
            pose.mulPose(Axis.ZP.rotationDegrees(sway));
            pose.pushPose();pose.translate(-.5,0,-.5);
            draw(pose,buffer,type,PARCEL,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
            if(e.falling()){pose.pushPose();pose.translate(0,CRATE_TOP,0);canopy(pose,buffer,light,age,CHUTE);pose.popPose();}
            pose.popPose();
        }
        /** The canopy and its cords, drawn with its cords starting at the current origin. {@code texture} picks the colours (olive and sand for supplies, red and white for paratroopers). */
        static void canopy(PoseStack pose,MultiBufferSource buffer,int light,float age,ResourceLocation texture){
            pose.pushPose();
            var last=pose.last();var m=last.pose();var n=last.normal();
            var vc=buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
            float breathe=1f+.02f*Mth.sin(age*.15f);
            // canopy: rings from the rim up to the crown, panels alternating between two colours
            for(int i=0;i<GORES;i++){
                float a0=(float)(i*2*Math.PI/GORES),a1=(float)((i+1)*2*Math.PI/GORES);
                float u0=(i%2==0?0f:1f/3f)+.01f,u1=(i%2==0?1f/3f:2f/3f)-.01f;
                for(int j=0;j<RINGS;j++){
                    float p0=(float)(j*Math.PI/2/RINGS),p1=(float)((j+1)*Math.PI/2/RINGS);
                    float r0=RADIUS*breathe*Mth.cos(p0),r1=RADIUS*breathe*Mth.cos(p1),y0=RIM+DOME*Mth.sin(p0),y1=RIM+DOME*Mth.sin(p1);
                    float[] v00={Mth.cos(a0)*r0,y0,Mth.sin(a0)*r0},v10={Mth.cos(a1)*r0,y0,Mth.sin(a1)*r0},v11={Mth.cos(a1)*r1,y1,Mth.sin(a1)*r1},v01={Mth.cos(a0)*r1,y1,Mth.sin(a0)*r1};
                    float nx=Mth.cos((a0+a1)/2)*Mth.cos((p0+p1)/2),ny=Mth.sin((p0+p1)/2),nz=Mth.sin((a0+a1)/2)*Mth.cos((p0+p1)/2);
                    float vv0=.03f+.94f*(j/(float)RINGS),vv1=.03f+.94f*((j+1)/(float)RINGS);
                    vert(vc,m,n,v00,u0,vv0,nx,ny,nz,light);vert(vc,m,n,v10,u1,vv0,nx,ny,nz,light);vert(vc,m,n,v11,u1,vv1,nx,ny,nz,light);vert(vc,m,n,v01,u0,vv1,nx,ny,nz,light);
                }
                // the cord hangs from the middle of the panel's lower edge down to the crate's top edge
                float am=(a0+a1)/2;
                float[] top={Mth.cos(am)*RADIUS*breathe*.99f,RIM,Mth.sin(am)*RADIUS*breathe*.99f};
                float[] bot={Mth.clamp(Mth.cos(am)*.7f,-.44f,.44f),0f,Mth.clamp(Mth.sin(am)*.7f,-.44f,.44f)};
                cord(vc,m,n,bot,top,light);
            }
            pose.popPose();
        }
        static void vert(com.mojang.blaze3d.vertex.VertexConsumer vc,org.joml.Matrix4f m,org.joml.Matrix3f n,float[] p,float u,float v,float nx,float ny,float nz,int light){
            vc.vertex(m,p[0],p[1],p[2]).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,nx,ny,nz).endVertex();
        }
        /** A cord: two thin crossed strips so it shows from every side. */
        static void cord(com.mojang.blaze3d.vertex.VertexConsumer vc,org.joml.Matrix4f m,org.joml.Matrix3f n,float[] a,float[] b,int light){
            float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2],len=Mth.sqrt(dx*dx+dy*dy+dz*dz);if(len<1e-4f)return;
            dx/=len;dy/=len;dz/=len;
            float sx=dy*0-dz*1,sy=dz*0-dx*0,sz=dx*1-dy*0;   // dir x up-ish axis
            float sl=Mth.sqrt(sx*sx+sy*sy+sz*sz);if(sl<1e-4f){sx=1;sy=0;sz=0;sl=1;}
            sx/=sl;sy/=sl;sz/=sl;
            float tx=dy*sz-dz*sy,ty=dz*sx-dx*sz,tz=dx*sy-dy*sx;
            float w=.018f,u=.84f;
            for(float[] side:new float[][]{{sx,sy,sz},{tx,ty,tz}}){
                float ox=side[0]*w,oy=side[1]*w,oz=side[2]*w;
                vert(vc,m,n,new float[]{a[0]-ox,a[1]-oy,a[2]-oz},u,.1f,side[0],side[1],side[2],light);vert(vc,m,n,new float[]{a[0]+ox,a[1]+oy,a[2]+oz},u,.1f,side[0],side[1],side[2],light);
                vert(vc,m,n,new float[]{b[0]+ox,b[1]+oy,b[2]+oz},u,.9f,side[0],side[1],side[2],light);vert(vc,m,n,new float[]{b[0]-ox,b[1]-oy,b[2]-oz},u,.9f,side[0],side[1],side[2],light);
            }
        }
        @Override public ResourceLocation getTextureLocation(SupportCrate.ParcelEntity e){return TextureAtlas.LOCATION_BLOCKS;}
    }
}
