package dev.createarsenal.beacon;

import java.util.*;

/**
 * Ardent Energy price of a supply drop. Educated defaults: rarity sets the base, bulk building blocks cost a tenth,
 * and well-known valuable items are listed explicitly. Admins can override any item in
 * config/arsenal-beacon-support-costs.txt ("minecraft:diamond = 6", "minecraft:arrow x16 = 4").
 */
final class SupportCosts {
    private SupportCosts(){}
    /** One stack in the grid. {@code rarity}: 0 common, 1 uncommon, 2 rare, 3 epic. */
    record Entry(String id,int count,int rarity){}
    static final double[] RARITY={1,3,8,20};
    static final double BULK=0.1;
    private static final Set<String> BULK_WORDS=Set.of("cobble","dirt","gravel","sand","stone","planks","log","stick","netherrack","deepslate","andesite","diorite","granite","wool","glass","torch","seeds","wheat","bone_meal","string","feather","flint","leather","rotten_flesh","sapling","leaves","clay","terracotta","concrete","brick","tuff","dripstone","coarse","podzol","mud","cactus","bamboo","kelp","sugar_cane");
    /** Known item values (energy per single item). */
    static final Map<String,Double> KNOWN=Map.ofEntries(
        Map.entry("minecraft:copper_ingot",0.5),Map.entry("minecraft:iron_ingot",1.0),Map.entry("minecraft:gold_ingot",2.0),Map.entry("minecraft:redstone",0.4),
        Map.entry("minecraft:lapis_lazuli",0.6),Map.entry("minecraft:quartz",0.6),Map.entry("minecraft:coal",0.4),Map.entry("minecraft:charcoal",0.3),
        Map.entry("minecraft:diamond",6.0),Map.entry("minecraft:emerald",4.0),Map.entry("minecraft:netherite_scrap",6.0),Map.entry("minecraft:ancient_debris",8.0),
        Map.entry("minecraft:netherite_ingot",16.0),Map.entry("minecraft:ender_pearl",4.0),Map.entry("minecraft:blaze_rod",3.0),Map.entry("minecraft:gunpowder",0.8),
        Map.entry("minecraft:golden_apple",8.0),Map.entry("minecraft:enchanted_golden_apple",30.0),Map.entry("minecraft:totem_of_undying",40.0),
        Map.entry("minecraft:elytra",50.0),Map.entry("minecraft:nether_star",60.0),Map.entry("minecraft:beacon",45.0),Map.entry("minecraft:shulker_shell",12.0),
        Map.entry("minecraft:trident",30.0),Map.entry("minecraft:iron_block",9.0),Map.entry("minecraft:gold_block",18.0),Map.entry("minecraft:diamond_block",54.0),
        Map.entry("minecraft:bread",0.4),Map.entry("minecraft:cooked_beef",0.5),Map.entry("minecraft:arrow",0.15),
        Map.entry("arsenal_beacon:universal_ammo_coin",6.0),Map.entry("arsenal_beacon:ardent_energy",2.0),
        Map.entry("arsenal_beacon:reinforced_plating",3.0),Map.entry("arsenal_beacon:logistics_module",5.0),Map.entry("arsenal_beacon:resonance_coil",6.0),Map.entry("arsenal_beacon:restoration_matrix",8.0));

    /** Energy per single item. {@code overrides} (id -> per item) always wins. */
    static double unit(String id,int rarity,Map<String,Double> overrides){
        Double o=overrides.get(id);if(o!=null)return o;
        Double k=KNOWN.get(id);if(k!=null)return k;
        String path=id.substring(id.indexOf(':')+1);
        if(id.startsWith("tacz:")){
            if(path.contains("gun"))return 45;
            if(path.contains("attachment"))return 12;
            if(path.contains("ammo"))return 0.35;
            return 5;
        }
        double base=RARITY[Math.max(0,Math.min(3,rarity))];
        if(rarity==0)for(String w:BULK_WORDS)if(path.contains(w))return BULK;
        if(rarity==0&&(path.contains("diamond")||path.contains("netherite")))return Math.max(base,6);
        return base;
    }
    /** Total cost of delivering a grid; empty grids cost nothing (and cannot be called). */
    static int cost(List<Entry> grid,Map<String,Double> overrides){
        double sum=0;boolean any=false;
        for(var e:grid){if(e.count()<=0)continue;any=true;sum+=unit(e.id(),e.rarity(),overrides)*e.count();}
        return any?SupportRules.SUPPLY_CALL_FEE+(int)Math.ceil(sum):0;
    }
    /** Overrides file rows -> per-item values ("x16 = 4" means 0.25 each). */
    static Map<String,Double> overridesFrom(List<TextTable.Row> rows){
        var map=new HashMap<String,Double>();for(var r:rows)map.put(r.id(),r.value()/(double)Math.max(1,r.count()));return map;
    }
}
