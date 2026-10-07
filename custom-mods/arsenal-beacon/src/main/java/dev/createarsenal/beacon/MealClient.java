package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client hooks of the kitchen: the hall screen, pot renderer and stew model. Prepared meals show as ordinary potion effects. */
public final class MealClient {
    private MealClient(){}
    static void preview(MessHallMenu.Preview p){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.containerMenu instanceof MessHallMenu menu&&menu.containerId==p.menu()&&menu.pos.equals(p.pos()))menu.view=p.data();}
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class KitchenRegistration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.MESS_HALL_MENU.get(),MessHallScreen::new));}
        @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){e.register(KitchenRenderer.STEW);}
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(ArsenalBeacon.COOK_POT_ENTITY.get(),KitchenRenderer::new);}
    }
}
