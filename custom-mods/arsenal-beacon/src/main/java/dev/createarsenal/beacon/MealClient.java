package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class MealClient {
    private MealClient(){}
    private static MealData meal;private static int remaining;private static boolean home;private static long received;
    static MealData current(){return meal;}
    static boolean active(){return meal!=null&&remaining>0;}
    static boolean homeEnhanced(){return active()&&home;}
    static int fieldRemaining(){return remaining;}
    static void receive(PlayerMeals.Sync p){meal=MealData.load(p.meal());remaining=p.remaining();home=p.home();var mc=Minecraft.getInstance();received=mc.level==null?0:mc.level.getGameTime();}
    static void preview(MessHallMenu.Preview p){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.containerMenu instanceof MessHallMenu menu&&menu.containerId==p.menu()&&menu.pos.equals(p.pos()))menu.view=p.data();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){meal=null;remaining=0;home=false;received=0;}
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class KitchenRegistration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.MESS_HALL_MENU.get(),MessHallScreen::new));}
        @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){e.register(KitchenRenderer.STEW);}
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(ArsenalBeacon.COOK_POT_ENTITY.get(),KitchenRenderer::new);}
        @SubscribeEvent public static void overlay(RegisterGuiOverlaysEvent e){e.registerAboveAll("prepared_meal",(gui,g,partial,w,h)->{
            var mc=Minecraft.getInstance();if(meal==null||mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui)return;
            int ticks=Math.max(0,remaining-(home?0:(int)Math.max(0,mc.level.getGameTime()-received)));if(ticks==0)return;
            int width=Math.min(230,w/2-12);Ui.card(g,6,6,width,46+meal.bonuses().size()*11,home?Ui.BRASS:Ui.CYAN);
            Ui.text(g,mc.font,meal.name(),14,12,Ui.INK,width-16);
            Ui.text(g,mc.font,Ui.t("meal.timer",String.format(java.util.Locale.ROOT,"%d:%02d",ticks/1200,(ticks/20)%60)),14,24,Ui.MUTED,width-16);
            Ui.text(g,mc.font,Ui.t(home?"meal.home":"meal.field"),14,36,home?Ui.BRASS:Ui.CYAN,width-16);
            int y=48;for(var bonus:meal.bonuses()){Ui.text(g,mc.font,bonus.description(home),14,y,Ui.INK,width-16);y+=11;}
        });}
    }
}
