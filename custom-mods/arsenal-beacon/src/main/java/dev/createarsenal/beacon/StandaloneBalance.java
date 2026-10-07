package dev.createarsenal.beacon;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Generic acquisition uses loaded native indexes, never the presence of the author's crafting recipe. */
public final class StandaloneBalance {
    private static final Map<ServerLevel,List<WeaponPlatform.Entry>> CACHE=new WeakHashMap<>();
    private static JsonObject overrides=new JsonObject();
    record Generic(double power,String weaponType){}
    @SubscribeEvent public void starting(ServerAboutToStartEvent e){
        CACHE.clear();if(!BuildFlavor.STANDALONE)return;
        var path=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("arsenal-beacon-standalone.json");
        try{
            if(!java.nio.file.Files.exists(path))java.nio.file.Files.writeString(path,"{\n  \"ages\": {},\n  \"costs\": {}\n}\n");
            overrides=JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
            for(String section:List.of("ages","costs"))if(!overrides.has(section))overrides.add(section,new JsonObject());
            for(var entry:overrides.getAsJsonObject("ages").entrySet()){int age=entry.getValue().getAsInt();if(age<1||age>5)throw new IllegalArgumentException("Age must be 1..5: "+entry.getKey());}
            for(var entry:overrides.getAsJsonObject("costs").entrySet())for(var row:entry.getValue().getAsJsonArray()){
                var c=row.getAsJsonObject();if(c.get("count").getAsInt()<1||c.get("count").getAsInt()>4096||BuiltInRegistries.ITEM.get(new ResourceLocation(c.get("item").getAsString()))==Items.AIR)throw new IllegalArgumentException("Invalid cost: "+entry.getKey());
            }
        }catch(Exception ex){throw new IllegalStateException("Fix "+path+"; no invalid recipes were installed",ex);}
    }
    @SubscribeEvent public void reloaded(OnDatapackSyncEvent e){if(e.getPlayer()==null)CACHE.clear();}
    static String material(String id){return switch(id){
        case "create:iron_sheet","create:sturdy_sheet","kubejs:pressed_receiver"->"minecraft:iron_ingot";
        case "create:andesite_alloy","create:cogwheel","create:brass_sheet","create:brass_ingot","kubejs:cartridge_case"->"minecraft:copper_ingot";
        case "create:precision_mechanism","kubejs:hardened_receiver"->"minecraft:diamond";
        case "create:electron_tube"->"minecraft:redstone";
        case "kubejs:propellant"->"minecraft:gunpowder";
        case "kubejs:exotic_receiver"->"minecraft:netherite_scrap";
        default->id;};}
    static Object call(Object o,String method)throws ReflectiveOperationException{return o.getClass().getMethod(method).invoke(o);}
    @SuppressWarnings("unchecked") static Set<Map.Entry<ResourceLocation,Object>> indexes(String kind)throws ReflectiveOperationException{
        return (Set<Map.Entry<ResourceLocation,Object>>)Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getAllCommon"+kind+"Index").invoke(null);
    }
    static int age(String kind,String id,int fallback){var ages=overrides.getAsJsonObject("ages");return ages!=null&&ages.has(kind+"/"+id)?ages.get(kind+"/"+id).getAsInt():fallback;}
    static int gunAge(String id,Object index)throws ReflectiveOperationException{
        var known=WeaponPlatform.indexedGun(id);int a;
        if(!known.name().equals(id))a=known.age();
        else if(ArmorPlatform.matches(id,"gauss","railgun","laser","plasma","cyber","emx","helldiver","energy"))a=5;
        else if(ArmorPlatform.matches(id,"flintlock","musket","revolver","winchester","lever","colt1873","no3"))a=1;
        else if(ArmorPlatform.matches(id,"garand","thompson","mosin","mp40","mg42","m1911","kar98","lee_enfield"))a=2;
        else {var data=call(index,"getGunData");a=power(index)>=64||((Number)call(data,"getAmmoAmount")).intValue()>75?4:3;}
        return age("gun",id,a);
    }
    static double power(Object index)throws ReflectiveOperationException{
        Object bullet=call(index,"getBulletData");if(bullet==null)return 1;double value=((Number)call(bullet,"getDamageAmount")).doubleValue()*Math.max(1,((Number)call(bullet,"getBulletAmount")).intValue());
        return Math.max(1,Math.min(256,call(bullet,"getExplosionData")!=null?Math.max(64,value*4):value));
    }
    static ItemStack indexed(String kind,ResourceLocation id,int count)throws ReflectiveOperationException{
        String itemId=kind.equals("Gun")?"modern_kinetic_gun":kind.toLowerCase(Locale.ROOT);
        var item=BuiltInRegistries.ITEM.get(new ResourceLocation("tacz",itemId));var stack=new ItemStack(item,count);
        item.getClass().getMethod("set"+kind+"Id",ItemStack.class,ResourceLocation.class).invoke(item,stack,id);return stack;
    }
    static List<WeaponPlatform.Entry> catalogue(ServerPlayer player){return CACHE.computeIfAbsent(player.serverLevel(),level->{
        var all=new ArrayList<WeaponPlatform.Entry>();Map<String,Integer> ammoAges=new HashMap<>();Map<String,Double> ammoPower=new HashMap<>();
        try{
            for(var entry:indexes("Gun")){
                var id=entry.getKey();var index=entry.getValue();var output=indexed("Gun",id,1);int a=gunAge(id.toString(),index);double power=power(index);
                var data=call(index,"getGunData");Object ammo=call(data,"getAmmoId");if(ammo!=null){ammoAges.merge(ammo.toString(),a,Math::min);ammoPower.merge(ammo.toString(),power,Math::max);}
                var fireModes=(List<?>)call(data,"getFireModeSet");if(!fireModes.isEmpty())output.getOrCreateTag().putString("GunFireMode",fireModes.get(0).toString());
                output.getOrCreateTag().putInt("GunCurrentAmmoCount",0);
                all.add(entry("gun",id,output,a,new Generic(power,(String)call(index,"getType"))));
            }
            for(var entry:indexes("Ammo")){var id=entry.getKey();all.add(entry("ammo",id,indexed("Ammo",id,32),age("ammo",id.toString(),ammoAges.getOrDefault(id.toString(),3)),new Generic(ammoPower.getOrDefault(id.toString(),8.0),"")));}
            for(var entry:indexes("Attachment")){var id=entry.getKey();int a=ArmorPlatform.matches(id.toString(),"thermal","digital","night","smart")?4:ArmorPlatform.matches(id.toString(),"scope","laser")?3:2;
                all.add(entry("attachment",id,indexed("Attachment",id,1),age("attachment",id.toString(),a),new Generic(1,"")));}
            // Native consumable/throwable and magazine items need their original tags; preserve them.
            for(var recipe:level.getRecipeManager().getRecipes())if(recipe.getClass().getName().equals("com.tacz.guns.crafting.GunSmithTableRecipe")){
                var output=(ItemStack)call(recipe,"getOutput");if(output.isEmpty()||!WeaponPlatform.validIndex(output))continue;
                if(MagazineBridge.isMagazine(output)){all.add(new WeaponPlatform.Entry(recipe.getId(),recipe,MagazineBridge.gate(output),output));continue;}
                var n=output.getOrCreateTag();if(n.contains("GunId")||n.contains("AmmoId")||n.contains("AttachmentId"))continue;
                all.add(new WeaponPlatform.Entry(recipe.getId(),new Generic(8,""),new WeaponPlatform.Gate(1,"supply",recipe.getId().toString(),output.getHoverName().getString()),output));
            }
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot import installed TaCZ indexes",ex);}
        com.mojang.logging.LogUtils.getLogger().info("Standalone Weapon Platform imported {} native entries",all.size());return List.copyOf(all);
    });}
    private static WeaponPlatform.Entry entry(String kind,ResourceLocation id,ItemStack output,int age,Generic value){return new WeaponPlatform.Entry(new ResourceLocation(ArsenalBeacon.ID,"generic/"+kind+"/"+id.getNamespace()+"/"+id.getPath()),value,new WeaponPlatform.Gate(age,kind,id.toString(),output.getHoverName().getString()),output);}
    static List<WeaponPlatform.Entry> entries(ServerPlayer p,String kind,String query){String search=query.toLowerCase(Locale.ROOT);var list=catalogue(p).stream().filter(e->e.gate().kind().equals(kind)||kind.equals("attachment")&&e.gate().kind().equals("supply")).toList();if(kind.equals("ammo"))list=AmmoCatalogue.canonical(list);return list.stream().filter(e->search.isBlank()||e.gate().id().toLowerCase(Locale.ROOT).contains(search)||e.gate().name().toLowerCase(Locale.ROOT).contains(search)).sorted(Comparator.comparingInt((WeaponPlatform.Entry e)->e.gate().age()).thenComparing(e->e.gate().id())).toList();}
    static List<WeaponPlatform.Cost> upgrades(int age){return switch(age){
        case 1->List.of(WeaponPlatform.cost("minecraft:iron_ingot",32),WeaponPlatform.cost("minecraft:copper_ingot",16),WeaponPlatform.cost("minecraft:redstone",16));
        case 2->List.of(WeaponPlatform.cost("minecraft:gold_ingot",16),WeaponPlatform.cost("minecraft:quartz",32),WeaponPlatform.cost("minecraft:blaze_rod",4),WeaponPlatform.cost("minecraft:diamond",4));
        case 3->List.of(WeaponPlatform.cost("minecraft:diamond",16),WeaponPlatform.cost("minecraft:netherite_scrap",8),WeaponPlatform.cost("arsenal_beacon:resonance_coil",8));
        case 4->List.of(WeaponPlatform.cost("minecraft:netherite_ingot",4),WeaponPlatform.cost("minecraft:end_rod",16),WeaponPlatform.cost("minecraft:nether_star",1),WeaponPlatform.cost("arsenal_beacon:restoration_matrix",8));
        default->List.of();};}
    static List<WeaponPlatform.Cost> gearCosts(ItemStack output,int age){int scale=ArmorPlatform.type(output).equals("vest")?3:ArmorPlatform.type(output).equals("legs")?2:1;var list=new ArrayList<>(List.of(WeaponPlatform.cost("minecraft:leather",2*scale),WeaponPlatform.cost("minecraft:iron_ingot",age*2*scale)));if(age>=2)list.add(WeaponPlatform.cost("minecraft:copper_ingot",2*scale));if(age>=3)list.add(WeaponPlatform.cost("minecraft:quartz",4*scale));if(age>=4)list.add(WeaponPlatform.cost("minecraft:diamond",scale));if(age>=5)list.add(WeaponPlatform.cost("minecraft:netherite_scrap",scale));return list;}
    static List<WeaponPlatform.Cost> costs(WeaponPlatform.Entry e){
        var custom=overrides.getAsJsonObject("costs");String key=e.gate().kind()+"/"+e.gate().id();
        if(custom!=null&&custom.has(key)){var list=new ArrayList<WeaponPlatform.Cost>();for(var row:custom.getAsJsonArray(key)){var c=row.getAsJsonObject();list.add(WeaponPlatform.cost(c.get("item").getAsString(),c.get("count").getAsInt()));}return List.copyOf(list);}
        if(DisplayRacks.entry(e))return DisplayRacks.costs(e);
        if(CreateUnlocks.turret(e))return List.of(WeaponPlatform.cost("minecraft:iron_ingot",32),WeaponPlatform.cost("minecraft:redstone",16),WeaponPlatform.cost("minecraft:diamond",4));
        int age=e.gate().age();double power=e.recipe() instanceof Generic g?g.power():8;
        if(MagazineBridge.isMagazine(e.output()))return List.of(WeaponPlatform.cost("minecraft:iron_ingot",2+age),WeaponPlatform.cost("minecraft:copper_ingot",2));
        if(e.gate().kind().equals("supply"))return switch(ArmorPlatform.type(e.output())){case "explosives"->List.of(WeaponPlatform.cost("minecraft:gunpowder",8),WeaponPlatform.cost("minecraft:iron_ingot",4));case "medical"->List.of(WeaponPlatform.cost("minecraft:honey_bottle",2),WeaponPlatform.cost("minecraft:paper",8));default->List.of(WeaponPlatform.cost("minecraft:iron_ingot",4),WeaponPlatform.cost("minecraft:redstone",2));};
        if(e.gate().kind().equals("ammo")){int n=Math.max(1,(int)Math.ceil(power/8));return List.of(WeaponPlatform.cost("minecraft:copper_ingot",2+n),WeaponPlatform.cost("minecraft:gunpowder",2+n),WeaponPlatform.cost("minecraft:iron_ingot",1+age/2));}
        var list=new ArrayList<WeaponPlatform.Cost>();int n=e.gate().kind().equals("gun")?8+age*6+(int)Math.ceil(power/4):2+age;
        list.add(WeaponPlatform.cost("minecraft:iron_ingot",n));list.add(WeaponPlatform.cost("minecraft:copper_ingot",4+age*2));
        if(age>=2)list.add(WeaponPlatform.cost("minecraft:redstone",4*age));if(age>=3)list.add(WeaponPlatform.cost("minecraft:quartz",4*age));if(age>=4)list.add(WeaponPlatform.cost("minecraft:diamond",age-2));if(age>=5)list.add(WeaponPlatform.cost("minecraft:netherite_scrap",4));return List.copyOf(list);
    }
    static List<ItemStack> rewards(ServerLevel l,int tier,boolean hard){int t=Math.max(0,Math.min(10,tier));var list=new ArrayList<ItemStack>();
        RaidRewards.add(list,"minecraft:andesite",t<=1?64:16);RaidRewards.add(list,"minecraft:iron_ingot",16+8*t);RaidRewards.add(list,"minecraft:copper_ingot",16+4*t);RaidRewards.add(list,"minecraft:gold_ingot",2+3*t);RaidRewards.add(list,"minecraft:cooked_beef",8+2*t);RaidRewards.add(list,"minecraft:gunpowder",16+4*t);RaidRewards.add(list,"arsenal_beacon:universal_ammo_coin",8+8*t);RaidRewards.add(list,"arsenal_beacon:ardent_energy",2+2*t);
        if(t>=2){RaidRewards.add(list,"minecraft:redstone",8+4*t);RaidRewards.add(list,"minecraft:diamond",t-1);RaidRewards.add(list,"minecraft:golden_apple",1+t/3);}
        if(t>=3)RaidRewards.add(list,"minecraft:quartz",16+4*t);if(t>=4){RaidRewards.add(list,"minecraft:blaze_rod",8+4*(t-4));RaidRewards.add(list,"minecraft:ender_pearl",4+2*(t-4));RaidRewards.add(list,"minecraft:netherite_scrap",2*(t-3));}
        if(t>=5)RaidRewards.add(list,"arsenal_beacon:reinforced_plating",4+2*(t-5));if(t>=6)RaidRewards.add(list,"minecraft:shulker_shell",2+2*((t-6)/2));if(hard){for(var s:list)s.setCount(s.getCount()+(s.getCount()+1)/2);if(t>=5)RaidRewards.add(list,"minecraft:nether_star",1);}
        var p=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(l);var tactical=catalogue(p).stream().filter(e->e.gate().kind().equals("supply")).toList();
        for(String type:List.of("medical","explosives")){var entry=tactical.stream().filter(e->ArmorPlatform.type(e.output()).equals(type)).findFirst();if(entry.isPresent()&&(type.equals("medical")||t>=1||hard))list.add(entry.get().output().copyWithCount(type.equals("medical")?1+t/2:2+t/2));}
        if(tactical.stream().noneMatch(e->ArmorPlatform.type(e.output()).equals("medical"))){var potion=new ItemStack(Items.POTION,1+(hard?1:0));net.minecraft.world.item.alchemy.PotionUtils.setPotion(potion,net.minecraft.world.item.alchemy.Potions.HEALING);list.add(potion);}return list;
    }
    static void grantStarter(ServerPlayer p){if(p.getPersistentData().getBoolean("arsenalStarterGranted"))return;
        try{var guns=catalogue(p).stream().filter(e->e.gate().kind().equals("gun")).sorted(Comparator.comparingInt((WeaponPlatform.Entry e)->e.gate().age()).thenComparingDouble(e->((Generic)e.recipe()).power())).toList();if(guns.isEmpty())return;
            var chosen=guns.get(0);var api=Class.forName("com.tacz.guns.api.TimelessAPI");var index=((Optional<?>)api.getMethod("getCommonGunIndex",ResourceLocation.class).invoke(null,new ResourceLocation(chosen.gate().id()))).orElseThrow();var ammo=(ResourceLocation)call(call(index,"getGunData"),"getAmmoId");
            ArsenalBeacon.give(p,chosen.output().copy());ArsenalBeacon.give(p,indexed("Ammo",ammo,64));p.getPersistentData().putBoolean("arsenalStarterGranted",true);ArsenalBeacon.feedback(p,"[Create Arsenal] Starter weapon and 64 matching rounds supplied. Your Gun Guide shows the current reload key.");
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot prepare standalone starter kit",ex);}
    }
}
