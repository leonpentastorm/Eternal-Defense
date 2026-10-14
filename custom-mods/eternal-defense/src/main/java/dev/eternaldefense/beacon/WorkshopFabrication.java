package dev.eternaldefense.beacon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;

/** The beacon supplies starter installations; each installation owns its upgrades. */
final class WorkshopFabrication {
    static final List<String> ITEMS=List.of("gun_platform","ammo_platform","attachment_platform","armor_platform",
        "mess_hall_mk1","cook_pot","bowl_dispenser","milk_dispenser","drink_mixer_mk1","drink_pot","cup_dispenser","mre_maker","support_platform","support_cannon","exchange_shop","command_table","satellite_beacon","fob_beacon","supply_delivery_gate","supply_receiving_gate","tactical_enchanting_table","supply_catapult","logistics_hub","loadout_station");
    static ListTag state(ServerPlayer p){
        var d=CampaignData.get(p.server.overworld());var w=Supply.at(p,d.installed()?d.beacon:p.blockPosition());
        var rows=new ListTag();for(String id:ITEMS){var row=new CompoundTag();row.putString("id",id);row.put("item",new ItemStack(item(id)).save(new CompoundTag()));row.put("costs",WeaponPlatform.costTags(w,Economy.fabrication(id)));if(empty(p,id))row.putBoolean("empty",true);rows.add(row);}return rows;
    }
    private static Object countedFor;private static final Map<String,Boolean> EMPTY=new HashMap<>();
    /** A table no installed mod gives anything to make; counted once per recipe load. */
    static boolean empty(ServerPlayer p,String id){
        String kind=switch(id){case "gun_platform"->"gun";case "ammo_platform"->"ammo";case "attachment_platform"->"attachment";case "armor_platform"->"armor";default->null;};
        if(kind==null)return false;
        var recipes=p.server.getRecipeManager();if(countedFor!=recipes){EMPTY.clear();countedFor=recipes;}
        return EMPTY.computeIfAbsent(kind,k->(k.equals("gun")?WeaponPlatform.weaponEntries(p,"",false):WeaponPlatform.entries(p,k,"")).isEmpty());
    }
    static Item item(String id){return BuiltInRegistries.ITEM.get(new ResourceLocation(EternalDefense.ID,id));}
    static String buy(ServerPlayer p,String id){
        var d=CampaignData.get(p.server.overworld());
        if(!ITEMS.contains(id)||!p.isAlive()||p.isSpectator()||!p.mayBuild()||!d.installed()||!EternalDefense.near(p,d))return "Use Workshop Fabrication near your planted beacon.";
        if(empty(p,id))return item(id).getDescription().getString()+": no installed mod adds anything to make there, so it is not fabricated. Nothing consumed.";
        String where=switch(id){
            case "fob_beacon"->". Plant it in a village with at least "+FobRules.MIN_VILLAGERS+" villagers, outside your base.";
            case "supply_delivery_gate"->". Use it on a straight rail in an ACTIVE FOB's village.";
            case "supply_receiving_gate"->". Use it on a straight rail inside your beacon zone.";
            case "supply_catapult"->". Place it in a FOB's village; it works once that FOB's research is at level "+CatapultRules.SELLER_LEVEL+".";
            default->". Place it inside your beacon zone.";
        };
        return WeaponPlatform.transact(Supply.at(p,d.beacon),Economy.fabrication(id),new ItemStack(item(id)))?"Fabricated "+item(id).getDescription().getString()+where:"Missing materials or inventory space. Nothing consumed.";
    }
}
