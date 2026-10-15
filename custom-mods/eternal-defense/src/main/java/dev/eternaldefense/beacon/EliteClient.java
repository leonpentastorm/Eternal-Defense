package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;

/** Elites on screen: a Badass at its size, and for every raid mob its mark, parachute, aura and health bar, whatever renders the mob */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
public final class EliteClient {
    private EliteClient(){}
    // Colours live in the textures: shader packs drop a vertex tint
    static final ResourceLocation FILL=new ResourceLocation(EternalDefense.ID,"textures/entity/elite/bar_fill.png"),TRACK=new ResourceLocation(EternalDefense.ID,"textures/entity/elite/bar_track.png");
    private static final java.util.Map<String,ResourceLocation> RINGS=new java.util.HashMap<>();
    static ResourceLocation ring(int code){return RINGS.computeIfAbsent(Elites.ring(code),n->new ResourceLocation(EternalDefense.ID,"textures/entity/elite/ring_"+n+".png"));}
    /** Where the ring and the spark strip sit in a ring texture (64 x 72) */
    static final float RING_V=64/72f,SPARK_V0=66.5f/72,SPARK_V1=69.5f/72;
    static final double RANGE=96;
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1<<16));
    /** Entities whose draw is scaled right now (draws can nest) */
    private static final ArrayDeque<Entity> SCALED=new ArrayDeque<>();
    /** What the last frame drew: auras, health bars and raid marks */
    static int auras,bars,marks;

    static Float scale(Entity e){Float s=Elites.SCALES.get(e.getId());return s==null||s<=0||s==1?null:s;}
    /** How far over the hitbox the name, bar and mark float: a grown model reaches higher above its hitbox too */
    static float lift(Entity e){Float s=scale(e);return s==null?1:s;}
    /** Around any renderer's draw of an entity: a grown elite is drawn at its size */
    public static void begin(Entity e,PoseStack pose){Float s=scale(e);if(s==null)return;pose.pushPose();pose.scale(s,s,s);SCALED.push(e);}
    public static void end(Entity e,PoseStack pose){if(!SCALED.isEmpty()&&SCALED.peek()==e){SCALED.pop();pose.popPose();}}
    // The name tag already sits above the grown hitbox: undo the scale for it, keep the gap in proportion
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void nameTag(RenderNameTagEvent e){
        if(!SCALED.isEmpty()&&SCALED.peek()==e.getEntity()){float s=lift(e.getEntity());e.getPoseStack().scale(1/s,1/s,1/s);e.getPoseStack().translate(0,.5f*(s-1),0);}
    }

    @SubscribeEvent public static void overlay(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();auras=bars=marks=0;
        if(mc.level==null||RaidTypes.RAIDERS.isEmpty()&&Elites.KINDS.isEmpty())return;
        var cam=e.getCamera().getPosition();float partial=e.getPartialTick();var pose=e.getPoseStack();var frustum=e.getFrustum();
        var dispatcher=mc.getEntityRenderDispatcher();var facing=dispatcher.cameraOrientation();
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof LivingEntity mob)||!mob.isAlive()||mob.isInvisible())continue;
            int id=mob.getId();boolean raider=RaidTypes.RAIDERS.contains(id);Integer code=Elites.KINDS.get(id);
            if(!raider&&code==null||mob.distanceToSqr(cam)>RANGE*RANGE)continue;
            if(frustum!=null&&!frustum.isVisible(mob.getBoundingBoxForCulling().inflate(1,4,1)))continue;
            float age=mob.tickCount+partial,top=mob.getBbHeight(),lift=lift(mob);boolean chute=RaidTypes.CHUTED.contains(id)&&!mob.onGround();
            pose.pushPose();
            pose.translate(Mth.lerp(partial,mob.xo,mob.getX())-cam.x,Mth.lerp(partial,mob.yo,mob.getY())-cam.y,Mth.lerp(partial,mob.zo,mob.getZ())-cam.z);
            if(chute){pose.pushPose();pose.translate(0,top*.8,0);SupportClient.ParcelRenderer.canopy(pose,BUFFER,dispatcher.getPackedLightCoords(mob,partial),age,SupportClient.TrooperChutes.RED);pose.popPose();}
            if(code!=null){aura(pose,mob,age,code);bar(pose,mob,top+.56f*lift,facing);auras++;bars++;}
            if(raider){SupportClient.TrooperChutes.mark(pose,BUFFER,mob,chute?top+3.6f:top+(code!=null?.8f:.75f)*lift,age,facing);marks++;}
            pose.popPose();
        }
        BUFFER.endBatch();
    }

    /** A glowing ring at the feet in the elite's colour, turning, with a few sparks rising from it */
    static void aura(PoseStack pose,LivingEntity mob,float age,int code){
        float r=Math.max(.7f,mob.getBbWidth()*.85f);int pulse=(int)(255*(.75f+.25f*Mth.sin(age*.15f)));
        VertexConsumer glow=BUFFER.getBuffer(RenderType.eyes(ring(code)));
        pose.pushPose();pose.translate(0,.03,0);pose.mulPose(Axis.YP.rotationDegrees(age*3));
        flat(pose,glow,r,pulse);
        pose.mulPose(Axis.YP.rotationDegrees(-age*5));flat(pose,glow,r*(1.15f+.1f*Mth.sin(age*.1f)),pulse/2);
        pose.popPose();
        for(int i=0;i<4;i++){
            float t=((age*.02f+i*.25f)%1f),angle=i*1.57f+age*.03f,size=.06f*(1-t)+.02f;
            pose.pushPose();pose.translate(Mth.cos(angle)*r*.9f,t*Math.max(1.2f,mob.getBbHeight()*.8f),Mth.sin(angle)*r*.9f);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            quad(pose,glow,-size,-size,size,size,.4f,SPARK_V0,.6f,SPARK_V1,255,255);pose.popPose();
        }
    }
    /** A ring texture flat on the ground, seen from above and below; brightness dims it where the tint is kept */
    static void flat(PoseStack pose,VertexConsumer v,float r,int brightness){
        var m=pose.last().pose();var n=pose.last().normal();int light=15728880,b=brightness;
        float[][] c={{-r,-r,0,0},{-r,r,0,RING_V},{r,r,1,RING_V},{r,-r,1,0}};
        for(int i=0;i<4;i++){var p=c[i];v.vertex(m,p[0],0,p[1]).color(b,b,b,255).uv(p[2],p[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,1,0).endVertex();}
        for(int i=3;i>=0;i--){var p=c[i];v.vertex(m,p[0],0,p[1]).color(b,b,b,255).uv(p[2],p[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,-1,0).endVertex();}
    }
    /** The elite's health over its head, facing the camera: a dark track and a red fill */
    static void bar(PoseStack pose,LivingEntity mob,float height,org.joml.Quaternionf facing){
        float w=Mth.clamp(mob.getBbWidth()*.8f,.9f,2.4f),h=.09f,left=-w/2,fraction=Mth.clamp(mob.getHealth()/Math.max(1,mob.getMaxHealth()),0,1);
        pose.pushPose();pose.translate(0,height,0);pose.mulPose(facing);
        quad(pose,BUFFER.getBuffer(RenderType.entityCutoutNoCull(TRACK)),left-.03f,-.03f,-left+.03f,h+.03f,0,0,1,1,255,255);
        pose.translate(0,0,-.002);
        if(fraction>0)quad(pose,BUFFER.getBuffer(RenderType.entityCutoutNoCull(FILL)),left,0,left+w*fraction,h,0,0,fraction,1,255,255);
        pose.popPose();
    }
    /** A quad with its own texture area, tinted grey (white leaves the texture as it is) */
    static void quad(PoseStack pose,VertexConsumer v,float x0,float y0,float x1,float y1,float u0,float v0,float u1,float v1,int grey,int alpha){
        var m=pose.last().pose();var n=pose.last().normal();int light=15728880,g=grey;
        v.vertex(m,x0,y0,0).color(g,g,g,alpha).uv(u0,v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
        v.vertex(m,x1,y0,0).color(g,g,g,alpha).uv(u1,v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
        v.vertex(m,x1,y1,0).color(g,g,g,alpha).uv(u1,v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
        v.vertex(m,x0,y1,0).color(g,g,g,alpha).uv(u0,v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
    }
}
