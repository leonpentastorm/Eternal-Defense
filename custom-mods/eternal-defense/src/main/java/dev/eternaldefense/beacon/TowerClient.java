package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Client only: the Heavy Cannon turning, lifting and recoiling, tesla bolts, a punji trap's stakes and a cannon shell in flight */
final class TowerClient {
    private TowerClient(){}
    static final ResourceLocation CANNON_HEAD=PowerClient.part("heavy_cannon","head"),CANNON_GUN=PowerClient.part("heavy_cannon","gun"),CANNON_BARREL=PowerClient.part("heavy_cannon","barrel"),
        SPIKES=new ResourceLocation(EternalDefense.ID,"block/punji_trap_spikes");
    static final ResourceLocation[] MODELS={CANNON_HEAD,CANNON_GUN,CANNON_BARREL,SPIKES};

    // Heavy Cannon
    /** The head turns about (x, z), the gun lifts about (y, z) (model pixels); how fast it swings; the barrel's kick (blocks) and its return (ticks) */
    static final float YAW_X=16,YAW_Z=16,PITCH_Y=20,PITCH_Z=18,CANNON_TURN=.12f,KICK=3/16f,KICK_TICKS=10,REST_PITCH=8;
    static void cannon(Turrets.TurretEntity be,PoseStack pose,MultiBufferSource buffers,int light,int overlay,float now){
        aim(be,now);
        pose.pushPose();
        pose.translate(YAW_X/16,0,YAW_Z/16);pose.mulPose(Axis.YP.rotation(be.drawYaw));pose.translate(-YAW_X/16,0,-YAW_Z/16);
        SupportClient.draw(pose,buffers,RenderType.cutout(),CANNON_HEAD,light,overlay);
        pose.translate(0,PITCH_Y/16,PITCH_Z/16);pose.mulPose(Axis.XP.rotationDegrees(be.drawPitch));pose.translate(0,-PITCH_Y/16,-PITCH_Z/16);
        SupportClient.draw(pose,buffers,RenderType.cutout(),CANNON_GUN,light,overlay);
        pose.translate(0,0,recoil(now-be.shotAt));
        SupportClient.draw(pose,buffers,RenderType.cutout(),CANNON_BARREL,light,overlay);
        pose.popPose();
    }
    /** Pure: how far back the barrel sits this many ticks after a shot (blocks): all the way at once, then easing home */
    static float recoil(float ticks){return ticks<0||ticks>=KICK_TICKS?0:KICK*(1-ticks/KICK_TICKS)*(1-ticks/KICK_TICKS);}
    /** The gun swings to the arc the server chose (flat or lobbed) for the target it named; without power it sinks to the deck */
    private static void aim(Turrets.TurretEntity be,float now){
        float step=Mth.clamp(now-be.drawnAt,0,10);be.drawnAt=now;
        float yaw=be.drawYaw,pitch=be.powered?REST_PITCH:(float)CannonShell.MIN_PITCH;
        if(be.powered&&be.targetId>=0&&be.getLevel().getEntity(be.targetId) instanceof LivingEntity target){
            var at=target.position().add(0,target.getBbHeight()*.4,0);
            var shot=be.high?CannonShell.Arc.solve(be.getBlockPos(),at,Vec3.ZERO,CannonShell.LOB_SPEED,true):CannonShell.Arc.solve(be.getBlockPos(),at,Vec3.ZERO,CannonShell.FLAT_SPEED,false);
            if(shot==null&&be.high)shot=CannonShell.Arc.solve(be.getBlockPos(),at,Vec3.ZERO,CannonShell.FAR_LOB_SPEED,true);
            if(shot!=null){yaw=shot.yaw();pitch=(float)Math.toDegrees(shot.pitch());}
        }
        float k=1-(float)Math.pow(1-CANNON_TURN,step);
        be.drawYaw+=Mth.wrapDegrees((float)Math.toDegrees(yaw-be.drawYaw))*(float)Math.PI/180*k;
        be.drawPitch+=(pitch-be.drawPitch)*k;
    }

    // Tesla coil
    /** Ticks a bolt stays drawn; its core and glow half-widths (blocks); points along each jump */
    static final float ZAP_TICKS=8,CORE=.035f,GLOW=.11f;static final int KINKS=7;
    /** The bolts of the last zaps: from the strike ball to the first mob, then mob to mob, flickering as they fade */
    static void bolts(Turrets.TurretEntity be,PoseStack pose,MultiBufferSource buffers,float now,float partial){
        var level=be.getLevel();be.zaps.removeIf(z->now-z.at>ZAP_TICKS);
        if(be.zaps.isEmpty())return;
        var block=(Turrets.TurretBlock)be.getBlockState().getBlock();var origin=Vec3.atLowerCornerOf(be.getBlockPos());
        var vc=buffers.getBuffer(RenderType.lightning());var m=pose.last().pose();
        for(var z:be.zaps){
            float fade=Mth.clamp(1-(now-z.at)/ZAP_TICKS,0,1);var random=new java.util.Random(z.at*31+(long)now);
            Vec3 from=Turrets.middle(block.line,block.mk);
            for(int id:z.mobs){
                if(!(level.getEntity(id) instanceof LivingEntity e))continue;
                var to=e.getPosition(partial).add(0,e.getBbHeight()*.55,0).subtract(origin);
                bolt(vc,m,from,to,random,fade);from=to;
            }
        }
    }
    private static void bolt(VertexConsumer vc,Matrix4f m,Vec3 a,Vec3 b,java.util.Random random,float fade){
        var d=b.subtract(a);double len=d.length();if(len<1e-3)return;
        var dir=d.scale(1/len);var side=Math.abs(dir.y)>.95?new Vec3(1,0,0):dir.cross(new Vec3(0,1,0)).normalize();var up=dir.cross(side).normalize();
        double jag=Math.min(.6,len*.12);var points=new Vec3[KINKS+1];
        for(int i=0;i<=KINKS;i++){var p=a.add(d.scale(i/(double)KINKS));if(i>0&&i<KINKS)p=p.add(side.scale((random.nextDouble()-.5)*jag)).add(up.scale((random.nextDouble()-.5)*jag));points[i]=p;}
        for(int i=0;i<KINKS;i++){
            ribbon(vc,m,points[i],points[i+1],side,GLOW,.45f,.7f,1f,.35f*fade);ribbon(vc,m,points[i],points[i+1],up,GLOW,.45f,.7f,1f,.35f*fade);
            ribbon(vc,m,points[i],points[i+1],side,CORE,.85f,.95f,1f,.9f*fade);ribbon(vc,m,points[i],points[i+1],up,CORE,.85f,.95f,1f,.9f*fade);
        }
    }
    /** A flat strip from a to b, seen from both sides */
    private static void ribbon(VertexConsumer vc,Matrix4f m,Vec3 a,Vec3 b,Vec3 across,float half,float r,float g,float bl,float alpha){
        var o=across.scale(half);
        Vec3[] q={a.subtract(o),a.add(o),b.add(o),b.subtract(o)};
        for(int i=0;i<4;i++)vc.vertex(m,(float)q[i].x,(float)q[i].y,(float)q[i].z).color(r,g,bl,alpha).endVertex();
        for(int i=3;i>=0;i--)vc.vertex(m,(float)q[i].x,(float)q[i].y,(float)q[i].z).color(r,g,bl,alpha).endVertex();
    }

    /** Where a tower's status lamp floats (blocks from the anchor's corner): over the gun, over a coil's strike ball, beside the cannon, over the totem's crystal */
    static float[] lampAt(Turrets.Line line,int mk){
        return switch(line){
            case TESLA->mk>=3?new float[]{1,2.35f,1}:new float[]{.5f,2.25f,.5f};
            case CANNON->new float[]{.2f,1.9f,.2f};
            case CURSE->new float[]{.5f,2.25f,.5f};
            default->mk>=3?new float[]{1,PowerClient.LAMP_Y,1}:new float[]{.5f,PowerClient.LAMP_Y,.5f};
        };
    }

    /** A punji trap's stakes while they shoot up or sink back (at rest the block's own model shows them) */
    static final class PunjiRenderer implements BlockEntityRenderer<PunjiTrap.TrapEntity> {
        PunjiRenderer(BlockEntityRendererProvider.Context c){}
        @Override public void render(PunjiTrap.TrapEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            if(be.getLevel()==null)return;
            float down=PunjiTrap.stakes(be.rising,be.getLevel().getGameTime()+partial-be.movedAt);if(Float.isNaN(down))return;
            pose.pushPose();
            pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-DefenseStructures.angle(be.getBlockState().getValue(DefenseStructures.FACING))));pose.translate(-.5,down/16,-.5);
            SupportClient.draw(pose,buffers,RenderType.cutout(),SPIKES,light,overlay);
            pose.popPose();
        }
    }

    /** A Heavy Cannon shell: the fire support shell, smaller, nose along its flight */
    static final class ShellRenderer extends EntityRenderer<CannonShell> {
        ShellRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
        @Override public void render(CannonShell shell,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            var dir=CannonShell.Arc.heading(shell.velocity,shell.tickCount+partial).normalize();
            pose.pushPose();
            pose.mulPose(new Quaternionf().rotationTo(0,-1,0,(float)dir.x,(float)dir.y,(float)dir.z));
            pose.scale(.55f,.55f,.55f);pose.translate(-.5,-.5,-.5);
            SupportClient.draw(pose,buffers,RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS),SupportClient.SHELL,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        @Override public ResourceLocation getTextureLocation(CannonShell shell){return TextureAtlas.LOCATION_BLOCKS;}
    }
}
