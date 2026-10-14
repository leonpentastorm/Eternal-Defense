package dev.eternaldefense.beacon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** The mod's own factory items, recipes and recipe rules; the displays as a separate mod. */
final class Round0101Test {
    static final Path MAIN=Path.of("src/main"),DISPLAYS=Path.of("../eternal-defense-displays/src/main");

    static JsonObject smith(String type,String id,int count,String... materials){
        var json=new JsonObject();json.addProperty("type",RecipeRules.GUN_SMITH);
        var result=new JsonObject();result.addProperty("type",type);result.addProperty("id",id);result.addProperty("count",count);json.add("result",result);
        var rows=new JsonArray();
        for(String m:materials){var row=new JsonObject();var item=new JsonObject();if(m.startsWith("#"))item.addProperty("tag",m.substring(1));else item.addProperty("item",m);row.add("item",item);row.addProperty("count",3);rows.add(row);}
        json.add("materials",rows);return json;
    }
    static Map<String,Integer> materials(JsonObject json){
        Map<String,Integer> out=new LinkedHashMap<>();
        for(var m:json.getAsJsonArray("materials")){var row=m.getAsJsonObject();var item=row.getAsJsonObject("item");out.merge(item.has("item")?item.get("item").getAsString():"#"+item.get("tag").getAsString(),row.get("count").getAsInt(),Integer::sum);}
        return out;
    }

    @Test void gunsGetTheReceiverOfTheirAge(){
        assertEquals(2,RecipeRules.age("gun","tacz:kar98"));
        var kar=materials(RecipeRules.factory(smith("gun","tacz:kar98",1,"minecraft:iron_ingot","create:brass_sheet")));
        assertEquals(2,kar.get("eternal_defense:pressed_receiver"),"Age 2: two Pressed Receivers");
        assertFalse(kar.containsKey("create:brass_sheet"),"early guns take no modern parts");
        assertEquals(3,kar.get("create:iron_sheet"),"the brass sheet became iron sheets");
        assertEquals(5,RecipeRules.age("gun","unknown:pack_gun"),"unknown guns are Exotic");
        assertEquals(1,materials(RecipeRules.factory(smith("gun","unknown:pack_gun",1,"minecraft:iron_ingot"))).get("eternal_defense:exotic_receiver"));
        assertNull(RecipeRules.factory(smith("gun","create_armorer:rifle",1,"minecraft:iron_ingot")),"Create Armory guns keep their recipe");
    }
    @Test void ammoTakesCasesAndPropellantInsteadOfPowderAndCopper(){
        var acp=materials(RecipeRules.factory(smith("ammo","daffas_arsenal:acp45",30,"minecraft:gunpowder","#forge:ingots/copper","minecraft:iron_nugget")));
        assertFalse(acp.containsKey("minecraft:gunpowder")||acp.containsKey("#forge:ingots/copper"));
        assertEquals(AmmoInputs.casings(30,"daffas_arsenal:acp45"),acp.get("eternal_defense:cartridge_case"));
        assertEquals(AmmoInputs.propellant(30,"daffas_arsenal:acp45"),acp.get("eternal_defense:propellant"));
        assertTrue(acp.containsKey("minecraft:iron_nugget"),"other inputs stay");
        var grenade=materials(RecipeRules.factory(smith("ammo","daffas_arsenal:daffas_40mm",1,"minecraft:iron_ingot")));
        assertEquals(3,RecipeRules.age("ammo","daffas_arsenal:daffas_40mm"));
        assertEquals(2,grenade.get("eternal_defense:hardened_alloy"),"heavy rounds need Hardened Alloy");
        assertEquals(2,materials(RecipeRules.factory(smith("attachment","x:scope",1,"minecraft:glass"))).get("create:iron_sheet"),"attachments take two iron sheets");
    }
    @Test void allNineFactoryItemsAreMadeByTheModsRecipes() throws Exception{
        var outputs=new HashSet<String>();
        try(Stream<Path> files=Files.walk(MAIN.resolve("resources/data/eternal_defense/recipes"))){
            for(Path f:files.filter(p->p.toString().endsWith(".json")).toList()){
                var json=JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                if(json.has("results"))json.getAsJsonArray("results").forEach(r->outputs.add(r.getAsJsonObject().get("item").getAsString()));
                if(json.has("result")&&json.get("result").isJsonObject()&&json.getAsJsonObject("result").has("item"))outputs.add(json.getAsJsonObject("result").get("item").getAsString());
            }
        }
        for(String id:List.of("pressed_receiver","brass_receiver","hardened_receiver","exotic_receiver","hardened_alloy","cartridge_case","propellant","reinforced_plating","logistics_module","resonance_coil","restoration_matrix"))
            assertTrue(outputs.contains("eternal_defense:"+id),id);
    }
    @Test void nothingNeedsKubeJS() throws Exception{
        String toml=Files.readString(MAIN.resolve("resources/META-INF/mods.toml"));
        assertFalse(toml.contains("kubejs"),"no KubeJS requirement");
        try(Stream<Path> files=Files.walk(MAIN)){
            for(Path f:files.filter(p->p.toString().endsWith(".java")||p.toString().endsWith(".json")).toList())
                assertFalse(Files.readString(f).contains("kubejs:"),f+" names a KubeJS item");
        }
        assertFalse(Files.exists(Path.of("../../pack-integration/kubejs/server_scripts")),"no pack scripts left");
    }
    @Test void factoryTiersScaleWithinTheCaps(){
        assertEquals(60,FactoryTiers.health(20,3,240,900,4000),1e-9);
        assertEquals(240,FactoryTiers.health(40,8,240,900,4000),1e-9,"ordinary cap");
        assertEquals(800,FactoryTiers.health(100,8,240,900,4000),1e-9,"elite, under its cap");
        assertEquals(900,FactoryTiers.health(100,10,240,900,4000),1e-9,"elite cap");
        assertEquals(4000,FactoryTiers.health(300,20,240,900,4000),1e-9,"boss cap");
        assertEquals(5000,FactoryTiers.health(5000,3,240,900,4000),1e-9,"never below the mob's own health");
        assertEquals(Map.of("brass_receiver",1,"hardened_receiver",2,"exotic_receiver",3),FactoryTiers.MILESTONES);
    }
    @Test void displaysAreTheirOwnMod() throws Exception{
        String toml=Files.readString(DISPLAYS.resolve("resources/META-INF/mods.toml"));
        assertFalse(toml.contains("modId=\"eternal_defense\""),"Displays does not need Eternal Defense");
        String source=Files.readString(DISPLAYS.resolve("java/dev/eternaldefense/displays/GunDisplays.java"));
        assertFalse(source.contains("CONTENT_ID"),"blocks and items under the Displays mod id");
        assertTrue(Files.isDirectory(DISPLAYS.resolve("resources/assets/eternal_defense_displays")));
        assertFalse(Files.exists(DISPLAYS.resolve("resources/assets/eternal_defense")));
        try(Stream<Path> files=Files.list(DISPLAYS.resolve("resources/data/eternal_defense_displays/recipes"))){
            for(Path f:files.toList()){String json=Files.readString(f);
                assertFalse(json.contains("mod_loaded"),f+" is craftable by hand with or without Eternal Defense");
                assertTrue(json.contains("\"eternal_defense_displays:"),f.toString());}
        }
        String catalog=Files.readString(MAIN.resolve("java/dev/eternaldefense/beacon/CreativeCatalog.java"));
        assertFalse(catalog.contains("RACKS"),"the stands stay in their own creative tab");
        assertFalse(catalog.contains("\"kubejs\""),"no foreign items in the Eternal Defense tab");
        String mainToml=Files.readString(MAIN.resolve("resources/META-INF/mods.toml"));
        int at=mainToml.indexOf("modId=\"eternal_defense_displays\"");
        assertTrue(at>0&&mainToml.substring(at,mainToml.indexOf("side=",at)).contains("mandatory=false"),"Eternal Defense runs without the displays");
    }
}
