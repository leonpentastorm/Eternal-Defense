package dev.createarsenal.beacon;

import com.google.gson.*;

/** Early historic author weapons use factory receivers and ordinary metals, not diamond stacks. */
public final class HistoricalGuns {
    public static String normalize(String raw,int age){
        var json=JsonParser.parseString(raw).getAsJsonObject();if(!json.has("result"))return raw;
        var output=json.getAsJsonObject("result");if(!output.has("id")||!output.has("type")||!output.get("type").getAsString().equals("gun"))return raw;
        String id=output.get("id").getAsString();if(!id.startsWith("hamster:")&&!id.startsWith("ww:"))return raw;
        boolean heavy=id.matches(".*(mg\\d+|m1919|anm2|dp28|t96|t99|m1918.*|madsen|stinger).*"),automatic=id.matches(".*(mp\\d+|pps|sten|stg|as44|m192.*|m1a1|m1t|m28s|t100.*|m50|s1100).*"),pistol=id.contains("colt")||id.contains("nagant")||id.contains("webley")||id.contains("sw_")||id.contains("uppercut")||id.contains("m1879")||id.endsWith(":p08")||id.endsWith(":p38")||id.endsWith(":c96")||id.endsWith(":cph")||id.endsWith(":t14")||id.endsWith(":makarov");
        JsonArray materials=new JsonArray();if(json.has("materials"))for(var row:json.getAsJsonArray("materials")){
            String ingredient=row.getAsJsonObject().get("item").toString();if(ingredient.contains("GunId")||ingredient.contains("AttachmentId"))materials.add(row.deepCopy());
        }
        add(materials,"forge:ingots/iron",age==1?(pistol?16:24):heavy?64:automatic?48:32);add(materials,"forge:ingots/copper",age==1?6:12);add(materials,"minecraft:logs",age==1?4:6);
        json.add("materials",materials);return json.toString();
    }
    private static void add(JsonArray materials,String tag,int count){var row=new JsonObject();var ingredient=new JsonObject();ingredient.addProperty("tag",tag);row.add("item",ingredient);row.addProperty("count",count);materials.add(row);}
}
