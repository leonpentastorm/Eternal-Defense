package dev.createarsenal.beacon;

import com.google.gson.*;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Data-pack policy shared by native JEI recipes and the Universal Platforms. */
public final class RecipePolicy {
    public static boolean nativeStation(Item item){
        for(Class<?> c=item.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("com.tacz.guns.item.GunSmithTableItem"))return true;
        if(!(item instanceof BlockItem b)||b.getBlock() instanceof WeaponPlatform.Station)return false;
        for(Class<?> c=b.getBlock().getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("com.tacz.guns.block.AbstractGunSmithTableBlock"))return true;
        return false;
    }
    public static List<String> nativeStations(){var result=new ArrayList<String>();BuiltInRegistries.ITEM.forEach(i->{if(nativeStation(i))result.add(BuiltInRegistries.ITEM.getKey(i).toString());});return result;}
    public static String balanceConversion(String input){
        var json=JsonParser.parseString(input).getAsJsonObject();if(!json.has("result")||!json.has("materials")||!json.getAsJsonObject("result").get("type").getAsString().equals("gun"))return input;
        var materials=json.getAsJsonArray("materials");boolean conversion=false;
        for(var material:materials)if(gunIngredient(material.getAsJsonObject().get("item")))conversion=true;
        if(!conversion)return input;
        for(var material:materials){var row=material.getAsJsonObject();if(!gunIngredient(row.get("item")))row.addProperty("count",Math.max(1,(row.get("count").getAsInt()+3)/4));}
        return json.toString();
    }
    private static boolean gunIngredient(JsonElement item){String s=item.toString();return s.contains("GunId")||s.contains("modern_kinetic_gun")||s.contains("tacz:gun");}
}
