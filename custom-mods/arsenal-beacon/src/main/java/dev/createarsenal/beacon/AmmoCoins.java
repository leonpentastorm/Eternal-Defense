package dev.createarsenal.beacon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Same ammo always has the same price; holding a weak gun cannot discount stronger weapons' ammunition. */
final class AmmoCoins {
    private static final Map<net.minecraft.server.level.ServerLevel,Map<String,Double>> VALUES=new WeakHashMap<>();
    static Map<String,Double> damage(net.minecraft.server.level.ServerLevel level){return VALUES.computeIfAbsent(level,l->{
        Map<String,Double> result=new HashMap<>();
        try{var api=Class.forName("com.tacz.guns.api.TimelessAPI");for(Object entry:(Set<?>)api.getMethod("getAllCommonGunIndex").invoke(null)){
            var index=((Map.Entry<?,?>)entry).getValue();var gun=index.getClass().getMethod("getGunData").invoke(index);var ammoId=gun.getClass().getMethod("getAmmoId").invoke(gun);if(ammoId==null)continue;String ammo=ammoId.toString();
            var bullet=index.getClass().getMethod("getBulletData").invoke(index);if(bullet==null)continue;double value=((Number)bullet.getClass().getMethod("getDamageAmount").invoke(bullet)).doubleValue()*Math.max(1,((Number)bullet.getClass().getMethod("getBulletAmount").invoke(bullet)).intValue());
            var explosion=bullet.getClass().getMethod("getExplosionData").invoke(bullet);if(explosion!=null)value=Math.max(64,value*4);
            result.merge(ammo,Math.max(1,Math.min(256,value)),Math::max);
        }}catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot price native ammunition",ex);}return result;
    });}
    static int price(ServerPlayer p,WeaponPlatform.Entry entry){if(MagazineBridge.isMagazine(entry.output()))return MagazineBridge.coinPrice(entry);double power=damage(p.server.overworld()).getOrDefault(entry.gate().id(),8.0);return price(entry.gate().age(),entry.output().getCount(),power);}
    static int price(int age,int rounds,double damage){return Math.max(1,(int)Math.ceil(Math.max(1,rounds)*Math.max(1,age)*Math.max(1,damage/8)/32.0));}
    static int balance(ServerPlayer p){int count=0;for(var s:p.getInventory().items)if(s.is(ArsenalBeacon.AMMO_COIN.get()))count+=s.getCount();for(var s:p.getInventory().offhand)if(s.is(ArsenalBeacon.AMMO_COIN.get()))count+=s.getCount();return count;}
    static String buy(ServerPlayer p,BlockPos pos,net.minecraft.resources.ResourceLocation id){
        var state=p.level().getBlockState(pos);if(!(state.getBlock() instanceof WeaponPlatform.Station station)||!station.kind.equals("ammo"))return "Spend Ammo Coins at the ammo table.";
        var entry=WeaponPlatform.entries(p,"ammo","").stream().filter(e->e.recipeId().equals(id)).findFirst().orElse(null);
        if(entry==null||!WeaponPlatform.compatible(p,entry))return "Equip a compatible gun. Ammunition unavailable.";
        if(!CreateUnlocks.unlocked(p,entry,state.getValue(WeaponPlatform.AGE)))return "Upgrade your ammo table or unlock its Create milestone.";
        int amount=price(p,entry);var costs=List.of(WeaponPlatform.cost("arsenal_beacon:universal_ammo_coin",amount));
        return WeaponPlatform.transact(p,costs,entry.output())?"Purchased "+entry.output().getCount()+(MagazineBridge.isMagazine(entry.output())?" empty magazine(s) for ":" rounds for ")+(p.isCreative()?"free":amount+" Ammo Coins")+".":InventoryPayment.failure(p,costs);
    }
}
