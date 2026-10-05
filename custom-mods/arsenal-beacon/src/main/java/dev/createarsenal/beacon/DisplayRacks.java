package dev.createarsenal.beacon;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.ItemTags;
final class DisplayRacks {
    static boolean entry(WeaponPlatform.Entry e){return e.gate().kind().equals("display");}
    static List<WeaponPlatform.Entry> entries(String query){
        var result=new ArrayList<WeaponPlatform.Entry>();String search=query.toLowerCase(Locale.ROOT);
        for(var registered:ArsenalBeacon.RACKS){var id=registered.getId();var stack=new ItemStack(registered.get());
            if(search.isBlank()||stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(search)||id.toString().contains(search))result.add(new WeaponPlatform.Entry(id,null,new WeaponPlatform.Gate(0,"display",id.toString(),stack.getHoverName().getString()),stack));
        }return result;
    }
    static List<WeaponPlatform.Cost> costs(WeaponPlatform.Entry e){int size=((dev.createarsenal.displays.DisplayRacks.Rack)((BlockItem)e.output().getItem()).getBlock()).wide()?2:1;var result=new ArrayList<WeaponPlatform.Cost>(List.of(WeaponPlatform.cost("create:iron_sheet",4*size),new WeaponPlatform.Cost(Ingredient.of(ItemTags.PLANKS),4*size),WeaponPlatform.cost("create:andesite_alloy",size)));if(((dev.createarsenal.displays.DisplayRacks.Rack)((BlockItem)e.output().getItem()).getBlock()).glass())result.add(new WeaponPlatform.Cost(Ingredient.of(Items.GLASS),4*size));return result;}
}
