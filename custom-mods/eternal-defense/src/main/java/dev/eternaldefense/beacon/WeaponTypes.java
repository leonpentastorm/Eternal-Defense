package dev.eternaldefense.beacon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

final class WeaponTypes {
    static String of(WeaponPlatform.Entry entry){
        if(!entry.gate().kind().equals("gun"))return "other";
        try{
            var api=Class.forName("com.tacz.guns.api.TimelessAPI");
            var index=((Optional<?>)api.getMethod("getCommonGunIndex",ResourceLocation.class).invoke(null,new ResourceLocation(entry.gate().id()))).orElse(null);
            return index==null?"other":WeaponBrowser.nativeType((String)index.getClass().getMethod("getType").invoke(index));
        }catch(ReflectiveOperationException|ClassCastException ex){Compat.warn("weapon types from TaCZ",ex);return "other";}
    }
}
