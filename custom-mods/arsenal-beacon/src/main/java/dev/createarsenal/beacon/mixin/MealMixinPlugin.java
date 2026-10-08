package dev.createarsenal.beacon.mixin;

import dev.createarsenal.beacon.MealOptionalCompat;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

/** Select the optional adapter before its absent target class can be loaded on either side. */
public final class MealMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName){
        return !mixinClassName.endsWith(".MealGrenadeMixin")||MealOptionalCompat.lrtacticalSupported();
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
