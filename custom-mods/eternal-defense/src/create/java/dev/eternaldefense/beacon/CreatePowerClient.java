package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** The dynamos' turning parts, drawn the way Create draws its gearbox shafts and cogwheels */
final class CreatePowerClient {
    private CreatePowerClient(){}
    static final PartialModel COG=PartialModel.of(new ResourceLocation(EternalDefense.ID,"block/cog_dynamo_cog"));

    static void register(IEventBus bus){
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{
            e.registerBlockEntityRenderer(CreatePower.SHAFT_ENTITY.get(),c->new ShaftRenderer());
            e.registerBlockEntityRenderer(CreatePower.COG_ENTITY.get(),c->new CogRenderer());
        });
    }

    /** A shaft half on each of the four sides; the two halves of an axis turn opposite ways, like a gearbox */
    static final class ShaftRenderer implements BlockEntityRenderer<CreatePower.ShaftEntity> {
        @Override public void render(CreatePower.ShaftEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            var state=be.getBlockState();var pos=be.getBlockPos();
            float time=AnimationTickHolder.getRenderTime(be.getLevel());
            var out=buffers.getBuffer(RenderType.solid());
            for(var dir:Direction.Plane.HORIZONTAL){
                var axis=dir.getAxis();
                var shaft=CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF,state,dir);
                float offset=KineticBlockEntityRenderer.getRotationOffsetForPosition(be,pos,axis);
                float angle=(time*be.getSpeed()*3f/10)%360;
                if(be.getSpeed()!=0&&be.hasSource()){
                    var from=be.source.subtract(pos);
                    var sourceFacing=Direction.getNearest(from.getX(),from.getY(),from.getZ());
                    if(sourceFacing.getAxis()==axis)angle*=sourceFacing==dir?1:-1;
                    else if(sourceFacing.getAxisDirection()==dir.getAxisDirection())angle*=-1;
                }
                angle=(float)((angle+offset)/180*Math.PI);
                KineticBlockEntityRenderer.kineticRotationTransform(shaft,be,axis,angle,light).renderInto(pose,out);
            }
        }
    }

    /** The cog band turns about the upright axis, offset like Create's small cogwheels so the teeth mesh */
    static final class CogRenderer implements BlockEntityRenderer<CreatePower.CogEntity> {
        @Override public void render(CreatePower.CogEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            var cog=CachedBuffers.partial(COG,be.getBlockState());
            float angle=KineticBlockEntityRenderer.getAngleForBe(be,be.getBlockPos(),Direction.Axis.Y);
            KineticBlockEntityRenderer.kineticRotationTransform(cog,be,Direction.Axis.Y,angle,light).renderInto(pose,buffers.getBuffer(RenderType.cutoutMipped()));
        }
    }
}
