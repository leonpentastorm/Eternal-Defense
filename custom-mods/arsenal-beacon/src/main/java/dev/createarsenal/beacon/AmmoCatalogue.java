package dev.createarsenal.beacon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Multiple author tables can craft the same cartridge. Batch size is not a new ammunition type. */
final class AmmoCatalogue {
    record Identity(ResourceLocation item,CompoundTag data){}
    static Identity identity(WeaponPlatform.Entry e){return new Identity(BuiltInRegistries.ITEM.getKey(e.output().getItem()),e.output().hasTag()?e.output().getTag().copy():new CompoundTag());}
    private static int owner(WeaponPlatform.Entry e){String id=e.output().getOrCreateTag().getString("AmmoId");if(id.isEmpty())return 1;var ammo=ResourceLocation.tryParse(id);return ammo!=null&&ammo.getNamespace().equals(e.recipeId().getNamespace())?0:1;}
    static List<WeaponPlatform.Entry> canonical(List<WeaponPlatform.Entry> entries){
        // Prefer an equally accessible original recipe over an addon's alternate bulk recipe.
        // Keep all meaningful NBT, including magazine family, capacity and loaded ammunition.
        var order=Comparator.comparingInt((WeaponPlatform.Entry e)->e.gate().age()).thenComparingInt(AmmoCatalogue::owner).thenComparing(e->e.recipeId().toString());
        var selected=new LinkedHashMap<Identity,WeaponPlatform.Entry>();
        for(var entry:entries)selected.merge(identity(entry),entry,(a,b)->order.compare(a,b)<=0?a:b);
        var result=new ArrayList<>(selected.values());result.sort(order);return result;
    }
}
