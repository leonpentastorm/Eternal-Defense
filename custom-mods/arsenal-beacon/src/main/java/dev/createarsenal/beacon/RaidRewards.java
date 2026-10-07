package dev.createarsenal.beacon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import java.util.*;

/** One authoritative payout definition for previews and payment. Rewards belong to the shared team. */
final class RaidRewards {
    static int waveIron(int tier){return 4+Math.max(0,Math.min(5,tier))*2;}
    static void add(List<ItemStack> list,String id,int count){if(count<=0)return;Item item=BuiltInRegistries.ITEM.get(new ResourceLocation(id));if(item==Items.AIR)throw new IllegalStateException("Missing guaranteed reward "+id);list.add(new ItemStack(item,count));}
    static ItemStack tactical(ServerLevel l,String key,String id){
        for(var recipe:l.getRecipeManager().getRecipes())if(recipe.getClass().getName().equals("com.tacz.guns.crafting.GunSmithTableRecipe"))try{
            var item=(ItemStack)recipe.getClass().getMethod("getOutput").invoke(recipe);if(item.hasTag()&&item.getTag().getString(key).equals("lrtactical:"+id))return item.copyWithCount(1);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot read tactical supply recipe",e);}
        throw new IllegalStateException("Missing guaranteed tactical supply "+id);
    }
    static void supply(List<ItemStack> list,ServerLevel l,String key,String id,int count){if(count<=0)return;var stack=tactical(l,key,id);stack.setCount(count);list.add(stack);}
    static List<ItemStack> completion(ServerLevel l,int tier,boolean hard){
        if(BuildFlavor.STANDALONE)return StandaloneBalance.rewards(l,tier,hard);
        tier=Math.max(0,Math.min(10,tier));List<ItemStack> list=new ArrayList<>();int t=tier,b=Math.min(5,t),extra=Math.max(0,t-5);
        // Raw starter stone tapers off; later caches prioritize compact useful production inputs.
        add(list,"minecraft:andesite",new int[]{64,64,48,32,32,32,16,16,16,16,16}[t]);add(list,"minecraft:iron_ingot",Math.min(96,16+8*t));
        add(list,"minecraft:copper_ingot",16+4*t);add(list,"minecraft:gold_ingot",2+3*t);add(list,"minecraft:cooked_beef",8+2*t);
        add(list,"create:andesite_alloy",8+4*t);add(list,"kubejs:cartridge_case",32+16*t);add(list,"kubejs:propellant",16+8*t);
        if(t>=1){add(list,"create:brass_ingot",8+8*t);add(list,"create:electron_tube",2+2*t);}
        if(t>=2){add(list,"create:precision_mechanism",2+2*(t-2));add(list,"minecraft:diamond",2*(t-1));add(list,"create:brass_sheet",4*t);}
        if(t>=3){add(list,"minecraft:quartz",16+4*t);add(list,"create:sturdy_sheet",2*(t-2));}
        if(t>=4){add(list,"minecraft:blaze_rod",8+4*(t-4));add(list,"minecraft:ender_pearl",4+2*(t-4));add(list,"minecraft:netherite_scrap",2*(t-3));}
        if(t>=5){add(list,"minecraft:totem_of_undying",1+extra/3);add(list,"arsenal_beacon:reinforced_plating",4+2*extra);}
        if(t>=6){add(list,"minecraft:shulker_shell",2+2*((t-6)/2));add(list,"minecraft:phantom_membrane",4+2*(t-6));}
        if(t>=8)add(list,"minecraft:dragon_breath",2*(t-7));
        add(list,"arsenal_beacon:universal_ammo_coin",8+8*t);
        add(list,"arsenal_beacon:ardent_energy",1+t);   // the floor of the Ardent Energy income: farming and exchange sell-offs add to it
        // Bonus resources are 50% of the completion cache, rounded up; wave iron stays unchanged.
        if(hard){for(var s:list)s.setCount(s.getCount()+(s.getCount()+1)/2);if(t>=5)add(list,"minecraft:nether_star",1+extra/3);}
        supply(list,l,"ConsumableId","carfak",1+t+(hard?2+t:0));
        if(t>=1||hard)supply(list,l,"ThrowableId","m67",2*t+(hard?4+2*t:0));
        if(t>=2||hard)supply(list,l,"ThrowableId","smoke_grenade",t+(hard?2+t:0));
        if(t>=3||hard)supply(list,l,"ThrowableId","flash_grenade",Math.max(0,t-2)+(hard?2:0));
        return list;
    }
    static List<ItemStack> total(ServerLevel l,int tier,boolean hard){var list=new ArrayList<>(completion(l,tier,hard));list.get(1).grow(waveIron(tier)*Rules.waves(tier));return list;}
    static void queue(CampaignData d,List<ItemStack> stacks){for(var stack:stacks)for(int left=stack.getCount();left>0;){int count=Math.min(left,stack.getMaxStackSize());d.rewards.add(stack.copyWithCount(count).save(new CompoundTag()));left-=count;}d.setDirty();}
    static ListTag previews(ServerLevel l){var result=new ListTag();for(int tier=0;tier<=10;tier++){var row=new CompoundTag();row.putInt("tier",tier);row.putInt("interval",Rules.intervalDays(tier));row.putInt("waves",Rules.waves(tier));for(boolean hard:List.of(false,true)){var items=new ListTag();for(var stack:total(l,tier,hard)){var n=new CompoundTag();n.put("item",stack.copyWithCount(1).save(new CompoundTag()));n.putInt("count",stack.getCount());items.add(n);}row.put(hard?"hard":"normal",items);}result.add(row);}return result;}
}
