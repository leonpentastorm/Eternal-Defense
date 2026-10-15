package dev.eternaldefense.beacon;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws a Badass elite at its size */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
final class EliteClient {
    private EliteClient(){}
    static Float scale(net.minecraft.world.entity.Entity e){Float s=Elites.SCALES.get(e.getId());return s==null||s<=0||s==1?null:s;}
    // Last, so a renderer that cancels the draw never leaves a pose open
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void pre(RenderLivingEvent.Pre<?,?> e){
        Float s=scale(e.getEntity());if(s==null)return;
        e.getPoseStack().pushPose();e.getPoseStack().scale(s,s,s);
    }
    // First, so the raid mark and the parachute are drawn at the real size
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void post(RenderLivingEvent.Post<?,?> e){
        if(scale(e.getEntity())!=null)e.getPoseStack().popPose();
    }
    // The name tag already sits above the grown hitbox: undo the model's scale for it
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void nameTag(RenderNameTagEvent e){
        Float s=scale(e.getEntity());if(s!=null&&e.getEntity() instanceof net.minecraft.world.entity.LivingEntity)e.getPoseStack().scale(1/s,1/s,1/s);
    }
}
