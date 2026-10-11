package dev.createarsenal.beacon;

import com.google.gson.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Expand input wood forms through native tags; outputs and quantities remain authored. */
public final class UniversalWood {
    private static final Map<String,String> FORMS=load("wood-ingredients.json"),TAGS=load("wood-tags.json");
    private static final Set<String> OUTPUTS=Set.of("result","results","output","outputs","transitionalItem","nbt","conditions");
    private static Map<String,String> load(String filename){
        try(var stream=UniversalWood.class.getResourceAsStream("/"+filename)){
            if(stream==null)throw new IOException("Missing wood catalogue");
            Map<String,String> forms=new HashMap<>();JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->forms.put(e.getKey(),e.getValue().getAsString()));return forms;
        }catch(IOException e){throw new IllegalStateException("Cannot load universal wood forms",e);}
    }
    public static String normalizeJson(String json){
        JsonElement original=JsonParser.parseString(json),updated=original.deepCopy();rewrite(updated);
        return original.equals(updated)?json:updated.toString();
    }
    private static void rewrite(JsonElement element){
        if(element.isJsonArray()){for(var entry:element.getAsJsonArray())rewrite(entry);return;}
        if(!element.isJsonObject())return;var obj=element.getAsJsonObject();
        // An ingredient is an item/tag object. Nested TaCZ counted materials retain their wrapper.
        if(obj.has("item")&&obj.get("item").isJsonPrimitive()&&!obj.has("nbt")){
            String tag=FORMS.get(obj.get("item").getAsString());
            if(tag!=null){obj.remove("item");obj.addProperty("tag",tag);}
        }
        if(obj.has("tag")&&obj.get("tag").isJsonPrimitive()){String form=TAGS.get(obj.get("tag").getAsString());if(form!=null)obj.addProperty("tag",form);}
        for(var entry:obj.entrySet())if(!OUTPUTS.contains(entry.getKey()))rewrite(entry.getValue());
    }
    static Ingredient normalize(Ingredient ingredient){
        ItemStack[] choices=ingredient.getItems();String form=null;
        for(var item:choices){String next=FORMS.get(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());if(next==null||(form!=null&&!form.equals(next)))return ingredient;form=next;}
        if(form==null)return ingredient;
        Ingredient expanded=Ingredient.of(TagKey.create(Registries.ITEM,new ResourceLocation(form)));return expanded.isEmpty()?ingredient:expanded;
    }
    /**
     * The name a cost row shows for a wood ingredient ("Wood (any log)"), or "" when it is not one. 0.0.28 (owner): a gun that needs dark oak logs
     * showed "Dark Oak Log" although any log pays. The expanded tag held logs the catalogue does not list (a modded log, a nether stem), and every
     * item had to be listed; now an item counts when the catalogue lists it or it is in the form's own tag, and a tag ingredient is read directly.
     */
    static String label(Ingredient ingredient){
        String form=tagForm(ingredient);
        if(form==null){
            for(var item:ingredient.getItems()){String next=FORMS.get(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());if(next!=null){if(form!=null&&!form.equals(next))return "";form=next;}}
            if(form==null)return "";
            var tag=TagKey.create(Registries.ITEM,new ResourceLocation(form));
            for(var item:ingredient.getItems())if(!FORMS.containsKey(BuiltInRegistries.ITEM.getKey(item.getItem()).toString())&&!item.is(tag))return "";
        }
        return name(form);
    }
    /** The wood form of an ingredient that is a single tag (as the pack's script and {@link #normalize} write it), or null. */
    private static String tagForm(Ingredient ingredient){
        try{
            var json=ingredient.toJson();
            if(json.isJsonObject()&&json.getAsJsonObject().has("tag")){String tag=json.getAsJsonObject().get("tag").getAsString();return FORMS.containsValue(tag)?tag:TAGS.get(tag);}
        }catch(RuntimeException ignored){}
        return null;
    }
    /** Pure: what a wood form is called on a cost row: logs are "Wood (any log)", planks "Planks (any wood)", the rest "Any wood stairs" and so on. */
    static String name(String form){
        String path=form.substring(form.indexOf(':')+1);
        return switch(path){case "logs"->"Wood (any log)";case "planks"->"Planks (any wood)";default->"Any "+path.replace("wooden_","wood ").replace('_',' ');};
    }
}
