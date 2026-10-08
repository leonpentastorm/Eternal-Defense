package dev.createarsenal.beacon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import java.util.*;

/** Stable category order; upgraded installations are earned through their menus. */
final class CreativeCatalog {
    static final List<String> FIRST=List.of("defense_beacon","beacon_controller","field_guide",
        "gun_platform","ammo_platform","attachment_platform","armor_platform","exchange_shop",
        "mess_hall_mk1","cook_pot","bowl_dispenser","milk_dispenser","milk_bottle","prepared_sandwich",
        "support_platform","support_cannon","supply_flare","return_flare","fire_support_flare",
        "ardent_energy","universal_ammo_coin","reinforced_plating","logistics_module","resonance_coil","restoration_matrix");
    static boolean hidden(String id){return id.matches("mess_hall_mk[234]");}
    static void display(CreativeModeTab.Output output){
        var seen=new HashSet<Item>();
        for(String id:FIRST){var item=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(ArsenalBeacon.ID,id));if(item!=Items.AIR&&seen.add(item))output.accept(item);}
        ArsenalBeacon.RACKS.stream().sorted(Comparator.comparing(b->b.getId().toString())).forEach(b->{if(seen.add(b.get().asItem()))output.accept(b.get());});
        BuiltInRegistries.ITEM.stream().filter(item->{var id=BuiltInRegistries.ITEM.getKey(item);return (id.getNamespace().equals(ArsenalBeacon.ID)||id.getNamespace().equals("kubejs"))&&!hidden(id.getPath());})
            .sorted(Comparator.comparingInt(CreativeCatalog::category).thenComparing(item->BuiltInRegistries.ITEM.getKey(item).toString())).forEach(item->{if(seen.add(item))output.accept(item);});
    }
    private static int category(Item item){String id=BuiltInRegistries.ITEM.getKey(item).getPath();return id.contains("receiver")||id.contains("mechanism")?0:id.contains("barrel")||id.contains("component")||id.contains("circuit")?1:id.contains("ammo")||id.contains("casing")||id.contains("bullet")||id.contains("shell")?2:3;}
}
