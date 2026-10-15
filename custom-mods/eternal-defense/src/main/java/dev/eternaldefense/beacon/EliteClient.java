package dev.eternaldefense.beacon;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws a Badass elite at its size */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
final class EliteClient {
    private EliteClient(){}
    // Last, so a renderer that cancels the draw never leaves a pose open
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void pre(RenderLivingEvent.Pre<?,?> e){
        Float s=Elites.SCALES.get(e.getEntity().getId());if(s==null||s==1)return;
        e.getPoseStack().pushPose();e.getPoseStack().scale(s,s,s);
    }
    @SubscribeEvent public static void post(RenderLivingEvent.Post<?,?> e){
        Float s=Elites.SCALES.get(e.getEntity().getId());if(s==null||s==1)return;
        e.getPoseStack().popPose();
    }
}
