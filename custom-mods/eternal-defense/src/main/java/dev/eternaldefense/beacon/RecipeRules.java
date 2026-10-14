package dev.eternaldefense.beacon;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Recipe changes applied while recipes load: factory gates on gun smith recipes, universal wood, native stations removed. */
public final class RecipeRules {
    private RecipeRules(){}
    static final String GUN_SMITH="tacz:gun_smith_table_crafting";
    private static final String PART=EternalDefense.ID+":";
    private static final Pattern HEAVY=Pattern.compile("rocket|40mm|grenade|missile|rpg");
    private static final Pattern MODERN=Pattern.compile("nether|blaze|ender|brass|precision_mechanism|hardened|exotic");
    /** Extra station items whose recipes are removed. */
    static final List<String> STATIONS=List.of("tacz_turrets:turret","omegarecon:armory","omegarecon:armory_block","caps_awim_tactical_gear_rework:crafttable","caps_awim_tactical_gear_rework:craft_table");
    private static Map<String,Integer> weaponAges,componentAges;

    /** Rewrites the raw recipe files before they are parsed. */
    public static void apply(Map<ResourceLocation,JsonElement> recipes){
        int factory=0,wood=0,conversions=0;
        for(var entry:recipes.entrySet()){
            if(!entry.getValue().isJsonObject())continue;
            JsonObject json=entry.getValue().getAsJsonObject();
            boolean smith=json.has("type")&&json.get("type").isJsonPrimitive()&&GUN_SMITH.equals(json.get("type").getAsString());
            try{
                if(smith){JsonObject gated=factory(json);if(gated!=null){json=gated;factory++;}}
                if(UniversalWood.rewrite(json))wood++;
                if(smith){String before=json.toString(),after=RecipePolicy.balanceConversion(before);if(!after.equals(before)){json=JsonParser.parseString(after).getAsJsonObject();conversions++;}}
            }catch(RuntimeException e){LogUtils.getLogger().warn("Recipe {} left unchanged: {}",entry.getKey(),e.toString());continue;}
            entry.setValue(json);
        }
        LogUtils.getLogger().info("[Eternal Defense] Factory gates on {} gun smith recipes, universal wood on {}, conversions reduced in {}.",factory,wood,conversions);
    }

    /** Removes the recipes of native stations and items the platforms sell, once recipes are parsed. */
    public static void remove(RecipeManager manager){
        Set<Item> removed=new HashSet<>();
        for(String id:RecipePolicy.nativeStations())item(id).ifPresent(removed::add);
        for(String id:ArmorPlatform.managedItems())item(id).ifPresent(removed::add);
        for(String id:STATIONS)item(id).ifPresent(removed::add);
        List<Recipe<?>> kept=new ArrayList<>();int dropped=0;
        for(Recipe<?> recipe:manager.getRecipes()){
            ItemStack output;
            try{output=recipe.getResultItem(RegistryAccess.EMPTY);}catch(RuntimeException e){output=ItemStack.EMPTY;}
            if(output!=null&&!output.isEmpty()&&removed.contains(output.getItem()))dropped++;else kept.add(recipe);
        }
        if(dropped==0)return;
        manager.replaceRecipes(kept);
        LogUtils.getLogger().info("[Eternal Defense] Removed {} native station and armor recipes.",dropped);
    }

    /** Removal again once the server runs and after every reload, for loaders that replace the vanilla recipe pass. */
    public static final class Events {
        @net.minecraftforge.eventbus.api.SubscribeEvent public void starting(net.minecraftforge.event.server.ServerStartingEvent e){remove(e.getServer().getRecipeManager());}
        @net.minecraftforge.eventbus.api.SubscribeEvent public void reloaded(net.minecraftforge.event.OnDatapackSyncEvent e){if(e.getPlayer()==null)remove(e.getPlayerList().getServer().getRecipeManager());}
    }

    private static Optional<Item> item(String id){var rl=ResourceLocation.tryParse(id);return rl==null?Optional.empty():BuiltInRegistries.ITEM.getOptional(rl);}

    /** Factory parts by Age on one gun smith recipe; null when it stays as it is. */
    static JsonObject factory(JsonObject json){
        if(!json.has("result")||!json.has("materials")||!json.get("result").isJsonObject())return null;
        var result=json.getAsJsonObject("result");
        String id=string(result,"id");
        if(id.startsWith("create_armorer:")||id.equals("lrl:db_long_super"))return null;
        int age=Math.max(1,Math.min(5,age(string(result,"type"),id)));
        json=JsonParser.parseString(HistoricalGuns.normalize(json.toString(),age)).getAsJsonObject();
        result=json.getAsJsonObject("result");
        String type=string(result,"type");
        JsonArray materials=json.getAsJsonArray("materials");
        switch(type){
            case "gun"->materials.add(row(PART+(age<=2?"pressed_receiver":age<=4?"hardened_receiver":"exotic_receiver"),age==2?2:1));
            case "ammo"->{
                JsonArray kept=new JsonArray();
                for(var m:materials){String s=itemText(m);if(!s.contains("gunpowder")&&!s.contains("ingots/copper"))kept.add(m);}
                int batch=Math.max(1,result.has("count")?result.get("count").getAsInt():1);
                kept.add(row(PART+"cartridge_case",AmmoInputs.casings(batch,id)));
                kept.add(row(PART+"propellant",AmmoInputs.propellant(batch,id)));
                if(HEAVY.matcher(id).find())kept.add(row(PART+"hardened_alloy",2));
                json.add("materials",materials=kept);
            }
            case "attachment"->materials.add(row("create:iron_sheet",2));
            default->{return null;}
        }
        if(age<=2){
            JsonArray early=new JsonArray();
            for(var m:materials){var row=m.getAsJsonObject();
                if(MODERN.matcher(itemText(m)).find())early.add(row("create:iron_sheet",Math.max(1,row.has("count")?row.get("count").getAsInt():1)));else early.add(row);}
            json.add("materials",early);
        }
        return json;
    }

    /** The Age of a gun smith result: its component entry, else its weapon entry, else 5. */
    static int age(String type,String id){
        if(weaponAges==null){weaponAges=load("weapon_ages.json");componentAges=load("component_ages.json");}
        int component=componentAges.getOrDefault(type+"|"+id,0);
        if(component!=0)return component;
        int weapon=weaponAges.getOrDefault(id,0);
        return weapon!=0?weapon:5;
    }

    private static Map<String,Integer> load(String name){
        try(var in=RecipeRules.class.getResourceAsStream("/data/eternal_defense/"+name)){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,Integer> ages=new HashMap<>();root.entrySet().forEach(e->ages.put(e.getKey(),e.getValue().getAsInt()));return Map.copyOf(ages);
        }catch(Exception e){throw new IllegalStateException("Cannot load "+name,e);}
    }

    private static String itemText(JsonElement row){return row.isJsonObject()&&row.getAsJsonObject().has("item")?row.getAsJsonObject().get("item").toString():"";}

    private static String string(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonPrimitive()?o.get(key).getAsString():"";}

    private static JsonObject row(String item,int count){
        var ingredient=new JsonObject();ingredient.addProperty("item",item);
        var row=new JsonObject();row.add("item",ingredient);row.addProperty("count",count);return row;
    }
}
