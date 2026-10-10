package dev.createarsenal.beacon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;

/** The beacon supplies starter installations; each installation owns its upgrades. */
final class WorkshopFabrication {
    static final List<String> ITEMS=List.of("gun_platform","ammo_platform","attachment_platform","armor_platform",
        "mess_hall_mk1","cook_pot","bowl_dispenser","milk_dispenser","support_platform","support_cannon","exchange_shop","command_table","satellite_beacon");
    static ListTag state(ServerPlayer p){
        var rows=new ListTag();for(String id:ITEMS){var row=new CompoundTag();row.putString("id",id);row.put("item",new ItemStack(item(id)).save(new CompoundTag()));row.put("costs",WeaponPlatform.costTags(p,Economy.fabrication(id)));rows.add(row);}return rows;
    }
    static Item item(String id){return BuiltInRegistries.ITEM.get(new ResourceLocation(ArsenalBeacon.ID,id));}
    static String buy(ServerPlayer p,String id){
        var d=CampaignData.get(p.server.overworld());
        if(!ITEMS.contains(id)||!p.isAlive()||p.isSpectator()||!p.mayBuild()||!d.installed()||!ArsenalBeacon.near(p,d))return "Use Workshop Fabrication near your planted beacon.";
        return WeaponPlatform.transact(p,Economy.fabrication(id),new ItemStack(item(id)))?"Fabricated "+item(id).getDescription().getString()+". Place it inside your beacon zone.":"Missing materials or inventory space. Nothing consumed.";
    }
}
