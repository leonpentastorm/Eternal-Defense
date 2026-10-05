package dev.createarsenal.beacon;

import mezz.jei.api.*;
import mezz.jei.api.gui.handlers.*;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** JEI's own configured recipe/uses keys operate on our hovered ingredient icons. */
@JeiPlugin
public final class ArsenalJei implements IModPlugin {
    static mezz.jei.api.runtime.IJeiRuntime runtime;
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime value){runtime=value;}
    @Override public void onRuntimeUnavailable(){runtime=null;}
    @Override public ResourceLocation getPluginUid(){return new ResourceLocation(ArsenalBeacon.ID,"platform_controls");}
    @Override public void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration registration){registration.addRecipeCategories(new ArmorJeiCategory(registration.getJeiHelpers().getGuiHelper()),new ArmorJeiCategory(registration.getJeiHelpers().getGuiHelper(),true));}
    @Override public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration registration){registration.addRecipes(ArmorJeiCategory.TYPE,ArmorPlatform.fabrications());registration.addRecipes(ArmorJeiCategory.SUPPLIES,ArmorPlatform.supplies(""));}
    @Override public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration){registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(ArsenalBeacon.ARMOR_PLATFORM.get()),ArmorJeiCategory.TYPE);registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(ArsenalBeacon.GUN_PLATFORM.get()),ArmorJeiCategory.SUPPLIES);}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
        registration.addGuiScreenHandler(BeaconClient.ControlScreen.class,new Handler<>());
        registration.addGuiScreenHandler(PlatformScreen.class,new Handler<>());
    }
    private static final class Handler<T extends BeaconClient.PanelScreen> implements IScreenHandler<T>{
        @Override public IGuiProperties apply(T screen){return new IGuiProperties(){
            public Class<? extends net.minecraft.client.gui.screens.Screen> getScreenClass(){return screen.getClass();}
            public int getGuiLeft(){return screen.left;}public int getGuiTop(){return screen.top;}
            public int getGuiXSize(){return screen.pw;}public int getGuiYSize(){return screen.ph;}
            public int getScreenWidth(){return screen.width;}public int getScreenHeight(){return screen.height;}
        };}
        @Override public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory factory,T screen,double x,double y){
            var hover=screen.itemAt(x,y);return hover==null?Optional.empty():factory.createBuilder(hover.item()).buildWithArea(hover.x(),hover.y(),16,16);
        }
    }
}
