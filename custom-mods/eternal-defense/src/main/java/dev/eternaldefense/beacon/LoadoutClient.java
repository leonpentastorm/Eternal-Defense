package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client side of the Loadout Station and Capsule: the moving parts, steam and sparks, and the local player's walk while held. */
public final class LoadoutClient {
    private LoadoutClient(){}
    static final ResourceLocation DOOR_LEFT=model("loadout_station_door_left"),DOOR_RIGHT=model("loadout_station_door_right"),RING=model("loadout_station_ring"),
        ARM_A=model("loadout_station_arm_a"),ARM_B=model("loadout_station_arm_b"),CAPSULE=model("loadout_capsule"),CAPSULE_DOOR=model("loadout_capsule_door"),CAPSULE_ITEM=model("loadout_capsule_item");
    private static ResourceLocation model(String name){return new ResourceLocation(EternalDefense.ID,"block/"+name);}

    @Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void loadoutModels(ModelEvent.RegisterAdditional e){for(var m:new ResourceLocation[]{DOOR_LEFT,DOOR_RIGHT,RING,ARM_A,ARM_B,CAPSULE,CAPSULE_DOOR,CAPSULE_ITEM})e.register(m);}
        @SubscribeEvent public static void loadoutRenderers(EntityRenderersEvent.RegisterRenderers e){
            e.registerBlockEntityRenderer(EternalDefense.LOADOUT_STATION_ENTITY.get(),StationRenderer::new);
            e.registerBlockEntityRenderer(EternalDefense.LOADOUT_CAPSULE_ENTITY.get(),CapsuleRenderer::new);
            e.registerEntityRenderer(EternalDefense.CAPSULE_DROP.get(),DropRenderer::new);
        }
        @SubscribeEvent public static void loadoutSetup(FMLClientSetupEvent e){
            LoadoutClientHooks.tick=LoadoutClient::tick;
            e.enqueueWork(()->{
                net.minecraft.client.gui.screens.MenuScreens.register(EternalDefense.LOADOUT_MENU.get(),LoadoutScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(EternalDefense.LOADOUT_CAPSULE_MENU.get(),CapsuleScreen::new);
            });
        }
    }

    // Where a print stands at a moment
    static float elapsed(LoadoutStation.StationEntity be,float partial){var mc=Minecraft.getInstance();return mc.level==null?-1:be.elapsed(mc.level.getGameTime(),partial);}
    static Vec3 at(LoadoutStation.StationEntity be,float t){var p=LoadoutRules.position(be.timeline(),t,be.from,be.stand,be.exit);return new Vec3(p[0],p[1],p[2]);}
    static float yaw(LoadoutStation.StationEntity be,float t){return LoadoutRules.yaw(be.timeline(),t,be.from,be.stand,LoadoutModel.standingYaw(be.facing()));}

    // The station's block entity, every client tick
    /** The station printing the local player, or null. */
    private static LoadoutStation.StationEntity held;
    static LoadoutStation.StationEntity held(){
        var mc=Minecraft.getInstance();
        if(held!=null&&(held.isRemoved()||mc.player==null||held.getLevel()!=mc.level||!mc.player.getUUID().equals(held.occupant)))held=null;
        return held;
    }
    static void tick(LoadoutStation.StationEntity be){
        var mc=Minecraft.getInstance();if(mc.player==null||be.getLevel()==null)return;
        if(mc.player.getUUID().equals(be.occupant))held=be;
        if(be.occupant==null)return;
        var level=be.getLevel();float t=be.elapsed(level.getGameTime(),0);var tl=be.timeline();float c=tl.cycleTick(t);
        var anchor=be.getBlockPos();var facing=be.facing();var random=level.random;
        if(tl.phase(t)==LoadoutRules.Phase.CYCLE&&LoadoutModel.sparking(c)){
            float dy=LoadoutModel.cycleRing(c);
            for(var n:LoadoutModel.NOZZLES){
                var p=LoadoutStation.world(anchor,facing,n.x(),n.y()+dy,n.z());var d=LoadoutModel.turnVector(n.dx(),n.dy(),n.dz(),facing);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,d[0]*.06+(random.nextDouble()-.5)*.02,(random.nextDouble()-.5)*.03,d[2]*.06+(random.nextDouble()-.5)*.02);
            }
        }
        if(c>=LoadoutModel.STEAM_AT&&be.steamFor!=be.start){
            be.steamFor=be.start;
            for(var v:LoadoutModel.VENTS){
                var p=LoadoutStation.world(anchor,facing,v.x(),v.y(),v.z());var d=LoadoutModel.turnVector(v.dx(),v.dy(),v.dz(),facing);
                for(int i=0;i<10;i++)level.addParticle(ParticleTypes.CLOUD,p.x,p.y,p.z,d[0]*.16+(random.nextDouble()-.5)*.05,d[1]*.16+(random.nextDouble()-.5)*.05,d[2]*.16+(random.nextDouble()-.5)*.05);
            }
        }
    }

    // The local player while held: no input, moved along the path so the walk animates, never more
    @Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
    public static final class Held {
        @SubscribeEvent public static void heldInput(MovementInputUpdateEvent e){
            var mc=Minecraft.getInstance();var be=held();if(be==null||e.getEntity()!=mc.player)return;
            float t=elapsed(be,0);if(!be.timeline().holding(t))return;
            var in=e.getInput();in.leftImpulse=0;in.forwardImpulse=0;in.up=in.down=in.left=in.right=false;in.jumping=false;in.shiftKeyDown=false;
            var p=mc.player;var next=step(p.position(),at(be,t+1));p.setDeltaMovement(next.x-p.getX(),next.y-p.getY(),next.z-p.getZ());
        }
        @SubscribeEvent public static void heldAfter(TickEvent.PlayerTickEvent e){
            var mc=Minecraft.getInstance();if(e.phase!=TickEvent.Phase.END||e.side.isServer()||e.player!=mc.player)return;
            var be=held();if(be==null)return;float t=elapsed(be,0);if(!be.timeline().holding(t))return;
            var p=mc.player;var to=step(new Vec3(p.xo,p.yo,p.zo),at(be,t+1));p.setPos(to.x,to.y,to.z);
            float yaw=yaw(be,t+1);p.yBodyRot=yaw;
            if(LoadoutCinematic.running()){p.setYRot(yaw);p.yHeadRot=yaw;p.setXRot(0);}
            p.fallDistance=0;
        }
        /** Where the held player goes this tick from where the tick began: the path, reached at no more than CATCH_UP blocks a tick. */
        private static Vec3 step(Vec3 from,Vec3 target){var n=LoadoutRules.toward(new double[]{from.x,from.y,from.z},new double[]{target.x,target.y,target.z});return new Vec3(n[0],n[1],n[2]);}
        @SubscribeEvent public static void heldInteract(InputEvent.InteractionKeyMappingTriggered e){if(held()!=null&&held().timeline().holding(elapsed(held(),0))){e.setCanceled(true);e.setSwingHand(false);}}
    }

    // Renderers
    static final class StationRenderer implements BlockEntityRenderer<LoadoutStation.StationEntity> {
        StationRenderer(BlockEntityRendererProvider.Context ctx){}
        @Override public void render(LoadoutStation.StationEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            var level=be.getLevel();if(level==null)return;long now=level.getGameTime();
            float door=be.door(now,partial),ring=0,arm=0;
            if(be.occupant!=null){float t=be.elapsed(now,partial);ring=be.timeline().ring(t);arm=be.timeline().arm(t);}
            var type=RenderType.cutout();
            pose.pushPose();
            pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(180f-be.facing().toYRot()));pose.translate(-.5,-.5,-.5);
            hinged(pose,buffer,type,DOOR_RIGHT,LoadoutModel.HINGE_RIGHT_X,door,light,overlay);
            hinged(pose,buffer,type,DOOR_LEFT,LoadoutModel.HINGE_LEFT_X,-door,light,overlay);
            pose.pushPose();pose.translate(0,ring/LoadoutModel.PX,0);SupportClient.draw(pose,buffer,type,RING,light,overlay);pose.popPose();
            tilted(pose,buffer,type,ARM_A,LoadoutModel.ARM_A_PIVOT,arm,light,overlay);
            tilted(pose,buffer,type,ARM_B,LoadoutModel.ARM_B_PIVOT,-arm,light,overlay);
            pose.popPose();
        }
        /** A door leaf turned a degrees about its vertical hinge (README l.56-59). */
        private static void hinged(PoseStack pose,MultiBufferSource buffer,RenderType type,ResourceLocation model,double hingeX,float a,int light,int overlay){
            double hx=hingeX/LoadoutModel.PX,hz=LoadoutModel.HINGE_Z/LoadoutModel.PX;
            pose.pushPose();pose.translate(hx,0,hz);pose.mulPose(Axis.YP.rotationDegrees(a));pose.translate(-hx,0,-hz);
            SupportClient.draw(pose,buffer,type,model,light,overlay);pose.popPose();
        }
        /** An arm tilted t degrees about its sideways pivot (README l.73-75). */
        private static void tilted(PoseStack pose,MultiBufferSource buffer,RenderType type,ResourceLocation model,double[] pivot,float t,int light,int overlay){
            double px=pivot[0]/LoadoutModel.PX,py=pivot[1]/LoadoutModel.PX,pz=pivot[2]/LoadoutModel.PX;
            pose.pushPose();pose.translate(px,py,pz);pose.mulPose(Axis.XP.rotationDegrees(t));pose.translate(-px,-py,-pz);
            SupportClient.draw(pose,buffer,type,model,light,overlay);pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(LoadoutStation.StationEntity be){return true;}
        @Override public int getViewDistance(){return 96;}
    }

    static final class CapsuleRenderer implements BlockEntityRenderer<LoadoutCapsule.CapsuleEntity> {
        CapsuleRenderer(BlockEntityRendererProvider.Context ctx){}
        @Override public void render(LoadoutCapsule.CapsuleEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            var level=be.getLevel();if(level==null)return;float now=level.getGameTime()+partial;
            float fold=LoadoutRules.capsuleFold(now,be.foldAt);if(fold>=1f)return;
            float dx=LoadoutRules.capsuleDoor(now,be.openAt,be.closeAt);var type=RenderType.cutout();
            pose.pushPose();
            pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(180f-be.facing().toYRot()));
            // Folding away: it sinks and narrows into the ground
            if(fold>0){float s=1f-fold;pose.scale(.4f+.6f*s,s,.4f+.6f*s);}
            pose.translate(-.5,0,-.5);
            SupportClient.draw(pose,buffer,type,CAPSULE,light,overlay);
            pose.translate(dx/LoadoutModel.PX,0,0);SupportClient.draw(pose,buffer,type,CAPSULE_DOOR,light,overlay);
            pose.popPose();
        }
    }

    /** The capsule falling: its item model under the supply parachute, hung from the lifting eye (README l.152). */
    static final class DropRenderer extends EntityRenderer<LoadoutCapsule.Drop> {
        DropRenderer(EntityRendererProvider.Context ctx){super(ctx);shadowRadius=.4f;}
        @Override public void render(LoadoutCapsule.Drop e,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
            float age=e.tickCount+partial;float sway=(float)Math.sin(age*.12)*3f;
            pose.pushPose();pose.mulPose(Axis.ZP.rotationDegrees(sway));
            pose.pushPose();pose.translate(-.5,0,-.5);SupportClient.draw(pose,buffer,RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS),CAPSULE_ITEM,light,OverlayTexture.NO_OVERLAY);pose.popPose();
            pose.translate((LoadoutModel.CHUTE_X-LoadoutModel.CENTRE)/LoadoutModel.PX,LoadoutModel.CHUTE_Y/LoadoutModel.PX,(LoadoutModel.CHUTE_Z-LoadoutModel.CENTRE)/LoadoutModel.PX);
            SupportClient.ParcelRenderer.canopy(pose,buffer,light,age,SupportClient.ParcelRenderer.CHUTE,.06f);
            pose.popPose();
        }
        @Override public ResourceLocation getTextureLocation(LoadoutCapsule.Drop e){return TextureAtlas.LOCATION_BLOCKS;}
    }
}
