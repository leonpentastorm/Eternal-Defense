package dev.createarsenal.beacon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import java.util.*;

/** Native items retain their armor, Curios, storage and plate behavior. Only acquisition changes. */
public final class ArmorPlatform {
    static final Set<String> MODS=Set.of("lrarmor","marbledsarsenal","fracturepoint","caps_awim_tactical_gear_rework","chovys_apocalypse_mod","omegarecon","tacticalvests","tactical_stuff");
    static final List<String> TYPES=List.of("all","head","vest","legs","boots","pouches","plates","vision","medical","explosives","melee","materials","field");
    private static List<WeaponPlatform.Entry> nativeEntries;
    private static Map<String,String> names;
    record Fabrication(List<WeaponPlatform.Cost> costs,String type){}
    private static String name(ResourceLocation id,ItemStack stack){
        if(names==null){var all=new HashMap<String,String>();
            try(var in=ArmorPlatform.class.getResourceAsStream("/data/arsenal_beacon/armor_names.json")){
                var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.entrySet().forEach(e->all.put(e.getKey(),e.getValue().getAsString()));
            }catch(Exception ex){throw new IllegalStateException("Armor search names could not load",ex);}
            names=Map.copyOf(all);
        }
        return names.getOrDefault(id.toString(),stack.getHoverName().getString());
    }
    static String normalize(String type){return TYPES.contains(type)?type:"all";}
    static String label(String type){return switch(type){case "head"->"Helmets & masks";case "vest"->"Vests & armor";case "legs"->"Leg protection";case "boots"->"Boots";case "pouches"->"Pouches & packs";case "plates"->"Armor plates";case "vision"->"Optics & NVGs";case "medical"->"Medical kits";case "explosives"->"Explosives";case "melee"->"Melee & tools";case "materials"->"Gear materials";case "field"->"Field equipment";default->"All gear types";};}
    static boolean component(String kind){return kind.equals("ammo")||kind.equals("attachment");}
    static net.minecraftforge.registries.RegistryObject<net.minecraft.world.level.block.Block> station(String kind){return switch(kind){case "ammo"->ArsenalBeacon.AMMO_PLATFORM;case "attachment"->ArsenalBeacon.ATTACHMENT_PLATFORM;case "armor"->ArsenalBeacon.ARMOR_PLATFORM;default->ArsenalBeacon.GUN_PLATFORM;};}
    static String key(Item item){return BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);}
    static boolean allowed(Item item){
        if(BuildFlavor.STANDALONE)return item instanceof ArmorItem||!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")&&!(item instanceof BlockItem)&&!(item instanceof SpawnEggItem)&&matches(key(item),"pouch","backpack","armor_plate","armorplate","nvg","night_vision","grenade","medkit","bandage","mre","ration");
        if(!MODS.contains(BuiltInRegistries.ITEM.getKey(item).getNamespace())||item instanceof BlockItem||item instanceof SpawnEggItem)return false;
        String id=key(item);
        if(id.endsWith("_tab")||id.contains("itemtab")||id.contains("logo")||id.contains("spawn_egg")||id.contains("test")||id.contains("tape_1")||id.contains("brokenwatch"))return false;
        if(item instanceof ArmorItem)return true;
        // Chovy's unfinished standalone firearms/rounds belong to a different weapon system.
        if(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("chovys_apocalypse_mod"))return matches(id,"bandage","antibiotic","riotshield","cloth","tape","lockpick","katana","bat","machete","fireaxe","sledge","pitchfork","wrench","ironstick","mre");
        if(item.isEdible()&&!matches(id,"mre","ration"))return false;
        return true;
    }
    static boolean matches(String value,String... fragments){for(String s:fragments)if(value.contains(s))return true;return false;}
    static String type(ItemStack stack){
        String id=key(stack.getItem());var n=stack.getOrCreateTag();
        if(matches(id,"armorplate","armor_plate","plate_","ceramic_tile","steel_plate"))return "plates";
        if(stack.getItem().isEdible()||n.contains("ConsumableId")||matches(id,"medkit","bandage","antibiotic","syringe","medical","first_aid"))return "medical";
        if(n.contains("ThrowableId")||matches(id,"grenade","explosive","detonator","airstrike","rocket","c4"))return "explosives";
        if(matches(id,"backpack","pouch","rucksack","shoulderpad","bag","rig"))return "pouches";
        if(matches(id,"nvg","night_vision","thermal","digital_vision","nods","goggle"))return "vision";
        if(matches(id,"armorplate","armor_plate","plate_","ceramic_tile","steel_plate"))return "plates";
        if(stack.getItem() instanceof ArmorItem a)return switch(a.getEquipmentSlot()){case HEAD->"head";case CHEST->"vest";case LEGS->"legs";case FEET->"boots";default->"field";};
        if(matches(id,"knife","bayonet","hammer","bat","katana","machete","axe","sledge","pitchfork","wrench","ironstick"))return "melee";
        if(matches(id,"fiber","kevlar","resin","rubber","cloth","webbing","thread","blend","compound","woven","sheet","composite","blade","handle"))return "materials";
        return "field";
    }
    static int age(ItemStack stack){
        String id=key(stack.getItem()),type=type(stack);
        if(matches(id,"exoskeleton","juggernaut","restorative","thermal","digital_vision"))return 5;
        if(matches(id,"heavy","defender","armored_chemical","reinforced","viper","redut","airstrike"))return 4;
        if(matches(id,"night_vision","nvg","nods","medical_armor","ceramic","composite"))return 3;
        if(stack.getItem() instanceof ArmorItem a){
            if(a.getToughness()>=3)return 5;
            if(a.getToughness()>2||a.getDefense()>(a.getEquipmentSlot()==EquipmentSlot.CHEST?8:a.getEquipmentSlot()==EquipmentSlot.LEGS?6:3))return 4;
            if(matches(id,"ghillie","capblack","capolive","caphaki","beret","panama","chemical_protective","scout")||a.getDefense()<=1)return 1;
            if(matches(id,"light","police","riot","paca","bump")||a.getToughness()==0)return 2;
            return 3;
        }
        return switch(type){case "vision"->3;case "plates"->matches(id,"lite","light")?2:3;case "pouches"->matches(id,"tactical","squad","leader")?3:2;case "medical"->matches(id,"bandage")?1:3;case "explosives"->3;case "materials"->matches(id,"kevlar","polyethylene","polymer")?3:1;default->matches(id,"gas_mask_filter","filter")?2:1;};
    }
    private static List<WeaponPlatform.Cost> materials(ItemStack output,int age){
        if(BuildFlavor.STANDALONE)return StandaloneBalance.gearCosts(output,age);
        String type=type(output);var c=new ArrayList<WeaponPlatform.Cost>();
        if(type.equals("materials"))return matches(key(output.getItem()),"plate","metal","steel","blade","handle")?List.of(WeaponPlatform.cost("create:iron_sheet",2),WeaponPlatform.cost("minecraft:coal",1)):List.of(new WeaponPlatform.Cost(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.WOOL),4),WeaponPlatform.cost("minecraft:slime_ball",2));
        if(type.equals("medical"))return List.of(new WeaponPlatform.Cost(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.WOOL),8),WeaponPlatform.cost(age>=3?"minecraft:golden_apple":"minecraft:honey_bottle",1));
        if(type.equals("explosives"))return List.of(WeaponPlatform.cost("minecraft:tnt",key(output.getItem()).contains("airstrike")?32:2),WeaponPlatform.cost("create:iron_sheet",4),WeaponPlatform.cost("minecraft:redstone",8),WeaponPlatform.cost("create:precision_mechanism",age>=4?2:1));
        if(type.equals("melee"))return List.of(WeaponPlatform.cost("create:iron_sheet",4),WeaponPlatform.cost("minecraft:stick",2),WeaponPlatform.cost("create:andesite_alloy",2));
        if(type.equals("vision"))return List.of(WeaponPlatform.cost("minecraft:quartz",4),WeaponPlatform.cost("minecraft:redstone",8),WeaponPlatform.cost("minecraft:amethyst_shard",4),WeaponPlatform.cost("create:precision_mechanism",age>=5?4:1));
        int scale=type.equals("vest")?3:type.equals("legs")?2:1;
        c.add(new WeaponPlatform.Cost(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.WOOL),2*scale));
        c.add(WeaponPlatform.cost("minecraft:leather",2*scale));
        c.add(WeaponPlatform.cost(age>=3?"arsenal_beacon:reinforced_plating":"create:iron_sheet",Math.max(1,age)*scale));
        if(age>=2)c.add(WeaponPlatform.cost("create:andesite_alloy",2*scale));
        if(age>=3)c.add(WeaponPlatform.cost("create:precision_mechanism",scale));
        if(age>=4)c.add(WeaponPlatform.cost("minecraft:netherite_scrap",scale));
        if(age>=5)c.add(WeaponPlatform.cost("arsenal_beacon:resonance_coil",2*scale));
        return List.copyOf(c);
    }
    private static List<WeaponPlatform.Entry> allFabrications(){
        if(nativeEntries==null){
            var all=new ArrayList<WeaponPlatform.Entry>();
            BuiltInRegistries.ITEM.forEach(item->{if(allowed(item)){
                var id=BuiltInRegistries.ITEM.getKey(item);var output=new ItemStack(item);int age=age(output);
                all.add(new WeaponPlatform.Entry(new ResourceLocation(ArsenalBeacon.ID,"armor/"+id.getNamespace()+"/"+id.getPath()),new Fabrication(materials(output,age),type(output)),new WeaponPlatform.Gate(age,armor(output)?"armor":"supply",id.toString(),name(id,output)),output));
            }});
            nativeEntries=List.copyOf(all);
            com.mojang.logging.LogUtils.getLogger().info("Armor Platform registered {} native tactical gear items",all.size());
        }
        return nativeEntries;
    }
    static boolean armor(ItemStack stack){return Set.of("head","vest","legs","boots","pouches","plates","vision").contains(type(stack));}
    static List<WeaponPlatform.Entry> fabrications(){return allFabrications().stream().filter(e->armor(e.output())).toList();}
    static List<WeaponPlatform.Entry> supplies(String query){return filter(allFabrications().stream().filter(e->!armor(e.output())).toList(),query);}
    static List<WeaponPlatform.Entry> entries(ServerPlayer p,String query){return filter(fabrications(),query);}
    private static List<WeaponPlatform.Entry> filter(List<WeaponPlatform.Entry> all,String query){
        String filter=query.toLowerCase(Locale.ROOT);
        return all.stream().filter(e->filter.isEmpty()||e.gate().id().toLowerCase(Locale.ROOT).contains(filter)||e.gate().name().toLowerCase(Locale.ROOT).contains(filter)||e.output().getHoverName().getString().toLowerCase(Locale.ROOT).contains(filter)).sorted(Comparator.comparingInt((WeaponPlatform.Entry e)->e.gate().age()).thenComparing(e->e.gate().id())).toList();
    }
    static List<WeaponPlatform.Cost> costs(WeaponPlatform.Entry e)throws ReflectiveOperationException{return e.recipe() instanceof Fabrication f?f.costs():WeaponPlatform.costs((WeaponPlatform.Entry)e.recipe());}
    public static List<String> managedItems(){return BuiltInRegistries.ITEM.stream().filter(ArmorPlatform::allowed).map(i->BuiltInRegistries.ITEM.getKey(i).toString()).toList();}
}
