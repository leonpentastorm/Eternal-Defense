package dev.createarsenal.beacon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Uses the installed addon's live family registry, including extended and addon-specific magazines. */
final class MagazineBridge {
    static boolean isMagazine(ItemStack stack){return stack.getItem().getClass().getName().equals("com.raiiiden.taczmagazines.item.MagazineItem");}
    static Class<?> families()throws ClassNotFoundException{return Class.forName("com.raiiiden.taczmagazines.magazine.MagazineFamilySystem");}
    static String family(ItemStack stack){return stack.getOrCreateTag().getString("MagazineFamily");}
    static int capacity(ItemStack stack){return Math.max(1,stack.getOrCreateTag().getInt("MaxCapacity"));}
    static boolean valid(ItemStack stack){try{return !family(stack).isEmpty()&&((Set<?>)families().getMethod("getAllFamilies").invoke(null)).contains(family(stack));}catch(ReflectiveOperationException ex){return false;}}
    static boolean compatible(ItemStack gun,ItemStack magazine)throws ReflectiveOperationException{
        return (boolean)magazine.getItem().getClass().getMethod("isAmmoBoxOfGun",ItemStack.class,ItemStack.class).invoke(magazine.getItem(),gun,magazine);
    }
    @SuppressWarnings("unchecked") static Set<net.minecraft.resources.ResourceLocation> guns(ItemStack stack)throws ReflectiveOperationException{
        return (Set<net.minecraft.resources.ResourceLocation>)families().getMethod("getCompatibleGuns",String.class).invoke(null,family(stack));
    }
    static int extension(ItemStack stack)throws ReflectiveOperationException{return ((Number)families().getMethod("getExtLevelForFamily",String.class).invoke(null,family(stack))).intValue();}
    static WeaponPlatform.Gate gate(ItemStack stack)throws ReflectiveOperationException{
        int minimum=5,createStage=3;boolean ordinary=false;
        for(var gun:guns(stack)){var g=WeaponPlatform.indexedGun(gun.toString());var e=new WeaponPlatform.Entry(new net.minecraft.resources.ResourceLocation(g.id()),null,g,ItemStack.EMPTY);if(CreateUnlocks.armory(e))createStage=Math.min(createStage,CreateUnlocks.required(e));else{ordinary=true;minimum=Math.min(minimum,g.age());}}
        return new WeaponPlatform.Gate(Math.min(5,(ordinary?minimum:createStage)+extension(stack)),"ammo",(ordinary?"magazine/":"create_armorer:magazine/")+family(stack),stack.getHoverName().getString());
    }
    static boolean unlocked(ServerPlayer p,WeaponPlatform.Entry entry,int age){
        if(p.isCreative())return true;
        try{
            // A magazine shared with an earlier gun is available at that earlier Age; guns retain their own locks.
            if(!entry.gate().id().startsWith("create_armorer:")&&entry.gate().age()<=age)return true;
            for(var gun:guns(entry.output())){
                var g=WeaponPlatform.indexedGun(gun.toString());var weapon=new WeaponPlatform.Entry(entry.recipeId(),null,g,ItemStack.EMPTY);
                if(CreateUnlocks.armory(weapon)&&CreateUnlocks.level(p)>=Math.min(3,CreateUnlocks.required(weapon)+(extension(entry.output())>0?1:0)))return true;
            }
        }catch(ReflectiveOperationException ex){com.mojang.logging.LogUtils.getLogger().error("Could not resolve magazine progression",ex);}
        return false;
    }
    static int coinPrice(WeaponPlatform.Entry entry){return Math.max(2,entry.gate().age()*2+(capacity(entry.output())+19)/20);}
}
