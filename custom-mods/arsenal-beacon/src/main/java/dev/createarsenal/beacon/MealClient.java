package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client hooks of the kitchen: the hall screen, pot renderer and stew model. Prepared meals show as ordinary potion effects. */
public final class MealClient {
    private MealClient(){}
    static void preview(MessHallMenu.Preview p){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.player.containerMenu instanceof MessHallMenu menu&&menu.containerId==p.menu()&&menu.pos.equals(p.pos())){
            var data=p.data();
            if(data.contains("Catalogue")){
                menu.catalogue.clear();
                for(var tag:data.getList("Catalogue",net.minecraft.nbt.Tag.TAG_COMPOUND)){var row=(net.minecraft.nbt.CompoundTag)tag;menu.catalogue.add(new MessHallMenu.CatalogueFood(row.getString("Id"),row.getString("Group"),row.getIntArray("Fx")));}
                data.remove("Catalogue");
            }
            menu.view=data;menu.stew=data.getBoolean("StewMode");
        }
    }
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
    public static final class KitchenTooltips {
        @SubscribeEvent public static void ingredient(net.minecraftforge.event.entity.player.ItemTooltipEvent e){
            var mc=Minecraft.getInstance();if(mc.screen instanceof MessHallScreen screen)screen.ingredientTooltip(e);
        }
    }
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class KitchenRegistration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.MESS_HALL_MENU.get(),MessHallScreen::new));e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.BOWL_DISPENSER_MENU.get(),BowlDispenserScreen::new));e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.MILK_DISPENSER_MENU.get(),MilkDispenserScreen::new));}
        @SubscribeEvent public static void tooltips(RegisterClientTooltipComponentFactoriesEvent e){e.register(Ui.MaterialTip.class,Ui.MaterialRenderer::new);e.register(Ui.InfoTip.class,Ui.InfoRenderer::new);}
        @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){e.register(KitchenRenderer.STEW);}
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(ArsenalBeacon.COOK_POT_ENTITY.get(),KitchenRenderer::new);}
    }
}
