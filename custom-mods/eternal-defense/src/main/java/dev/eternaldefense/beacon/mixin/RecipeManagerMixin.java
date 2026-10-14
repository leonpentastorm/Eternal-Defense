package dev.eternaldefense.beacon.mixin;

import com.google.gson.JsonElement;
import dev.eternaldefense.beacon.RecipeRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

/** Applies the mod's recipe rules on every recipe load. */
@Mixin(value=RecipeManager.class,priority=500)
public abstract class RecipeManagerMixin {
    @Inject(method="apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("HEAD"))
    private void eternal$rewrite(Map<ResourceLocation,JsonElement> recipes,ResourceManager resources,ProfilerFiller profiler,CallbackInfo ci){RecipeRules.apply(recipes);}
    @Inject(method="apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("TAIL"))
    private void eternal$remove(Map<ResourceLocation,JsonElement> recipes,ResourceManager resources,ProfilerFiller profiler,CallbackInfo ci){RecipeRules.remove((RecipeManager)(Object)this);}
}
