package dev.createarsenal.displays;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GunDisplays.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class DisplayRackClient {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(GunDisplays.DISPLAY_ENTITY.get(),Renderer::new);}
    private static final class Renderer implements BlockEntityRenderer<DisplayRacks.DisplayEntity> {
        Renderer(BlockEntityRendererProvider.Context context){}
        @Override public void render(DisplayRacks.DisplayEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            if(entity.gun().isEmpty())return;
            var state=entity.getBlockState();if(!(state.getBlock() instanceof DisplayRacks.Rack rack)||state.getValue(DisplayRacks.PART))return;
            pose.pushPose();pose.translate(.5,.5,.5);
            int rotation=switch(state.getValue(DisplayRacks.FACING)){case EAST->90;case SOUTH->180;case WEST->270;default->0;};pose.mulPose(Axis.YN.rotationDegrees(rotation));
            pose.translate(rack.wide?.5:0,rack.gunY()/16.0-.5,rack.gunZ()/16.0-.5);
            // Use TaCZ's native FIXED item path so skins and installed attachments render too.
            float scale=rack.gunScale();pose.scale(scale,scale,scale); // FIXED guns are already upright; no extra inversion.
            Minecraft.getInstance().getItemRenderer().renderStatic(entity.gun(),ItemDisplayContext.FIXED,light,overlay,pose,buffers,entity.getLevel(),0);pose.popPose();
        }
        @Override public int getViewDistance(){return 48;}
    }
}
