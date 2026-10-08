package dev.createarsenal.beacon;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;

/** Field-radio fuel weapons cannot be refilled at our tables and have unsafe inspect/unjam animations. */
final class WeaponCompatibility {
    static boolean craftable(ItemStack stack){
        var gun=IGun.getIGunOrNull(stack);if(gun==null)return true;
        var id=gun.getGunId(stack);
        // Explicit fallback also protects a missing or replaced author index for the reported gun.
        if(id.toString().equals("bf1:ef46"))return false;
        return TimelessAPI.getCommonGunIndex(id).map(index->!index.getGunData().getReloadData().getType().name().equals("FUEL")).orElse(false);
    }
}
