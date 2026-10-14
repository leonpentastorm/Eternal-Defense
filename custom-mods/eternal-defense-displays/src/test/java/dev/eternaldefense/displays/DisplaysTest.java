package dev.eternaldefense.displays;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** The stands' recipes, the interact-key tag, the tuner's limits and texts. */
class DisplaysTest {
    static final Path MAIN=Path.of("src/main"),DATA=MAIN.resolve("resources/data/eternal_defense_displays");
    static final DisplayTuning.Tune T=DisplayTuning.Tune.DEFAULT;

    static List<String> stands() throws Exception{
        var source=Files.readString(MAIN.resolve("java/dev/eternaldefense/displays/GunDisplays.java"));
        var out=new ArrayList<String>();var m=Pattern.compile("rack\\(\"([a-z_]+)\"").matcher(source);while(m.find())out.add(m.group(1));return out;
    }
    static JsonObject json(Path p) throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}

    @Test void elevenStandsAndTheTuner() throws Exception{
        assertEquals(11,stands().size(),stands().toString());
        assertTrue(Files.readString(MAIN.resolve("java/dev/eternaldefense/displays/GunDisplays.java")).contains("ITEMS.register(\"display_tuner\""));
    }

    @Test void everyStandAndTheTunerHasItsOwnShapedRecipe() throws Exception{
        var names=new ArrayList<>(stands());names.add("display_tuner");
        var seen=new HashMap<String,String>();
        for(var name:names){
            var r=json(DATA.resolve("recipes/"+name+".json"));
            assertEquals("minecraft:crafting_shaped",r.get("type").getAsString(),name+" is a shaped recipe");
            assertEquals("eternal_defense_displays:"+name,r.getAsJsonObject("result").get("item").getAsString());
            var pattern=new ArrayList<String>();r.getAsJsonArray("pattern").forEach(e->pattern.add(e.getAsString()));
            var key=r.getAsJsonObject("key");var used=new TreeSet<String>();
            for(var row:pattern)for(char c:row.toCharArray())if(c!=' ')used.add(String.valueOf(c));
            assertEquals(used,new TreeSet<>(key.keySet()),name+": every symbol has an ingredient and every ingredient is used");
            var ingredients=new TreeMap<String,String>();
            for(var k:key.keySet()){var i=key.getAsJsonObject(k);var id=i.has("item")?i.get("item").getAsString():"#"+i.get("tag").getAsString();
                assertTrue(id.startsWith("minecraft:")||id.startsWith("#minecraft:"),name+" needs only vanilla items: "+id);ingredients.put(k,id);}
            // The grid with its ingredients written in: two recipes with the same grid would clash
            var grid=new StringBuilder();for(var row:pattern){for(char c:row.toCharArray())grid.append(c==' '?"_":ingredients.get(String.valueOf(c))).append(',');grid.append('/');}
            var other=seen.put(grid.toString(),name);assertNull(other,name+" has the same grid as "+other);
            var unlock=json(DATA.resolve("advancements/recipes/"+name+".json"));
            assertEquals("eternal_defense_displays:"+name,unlock.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
        }
        try(Stream<Path> files=Files.list(DATA.resolve("recipes"))){assertEquals(names.size(),files.count(),"no recipe besides these");}
    }

    @Test void theStandsAreOnTaczInteractKeyList() throws Exception{
        var tag=json(MAIN.resolve("resources/data/tacz/tags/blocks/interact_key/whitelist.json"));
        assertFalse(tag.get("replace").getAsBoolean(),"adds to TaCZ's list instead of replacing it");
        var values=new TreeSet<String>();tag.getAsJsonArray("values").forEach(e->values.add(e.getAsString()));
        var expected=new TreeSet<String>();for(var s:stands())expected.add("eternal_defense_displays:"+s);
        assertEquals(expected,values);
    }

    @Test void tuningStaysInsideItsLimits(){
        assertEquals(.25f,T.with(DisplayTuning.Setting.SIZE,0).size());
        assertEquals(3f,T.with(DisplayTuning.Setting.SIZE,99).size());
        assertEquals(16f,T.with(DisplayTuning.Setting.RIGHT,40).right());
        assertEquals(-16f,T.with(DisplayTuning.Setting.OUT,-40).out());
        assertEquals(180f,T.with(DisplayTuning.Setting.TURN,720).turn());
        assertEquals(1f,T.with(DisplayTuning.Setting.SIZE,Float.NaN).size(),"an unreadable number is the default");
        assertEquals(0f,T.with(DisplayTuning.Setting.TILT,Float.POSITIVE_INFINITY).tilt());
        assertEquals(1.23f,T.with(DisplayTuning.Setting.SIZE,1.234f).size(),"size snaps to 1%");
        assertEquals(2.25f,T.with(DisplayTuning.Setting.UP,2.3f).up(),"moves snap to a quarter pixel");
        assertEquals(31f,T.with(DisplayTuning.Setting.ROLL,30.6f).roll(),"angles snap to a degree");
        var wild=new DisplayTuning.Tune(-5,99,-99,0,500,-500,Float.NaN).clamped();
        assertEquals(new DisplayTuning.Tune(.25f,16,-16,0,180,-180,0),wild);
        assertTrue(T.isDefault());assertFalse(T.with(DisplayTuning.Setting.UP,1).isDefault());
    }

    @Test void buttonsAndSlidersMoveByTheirSteps(){
        assertEquals(1.05f,T.nudge(DisplayTuning.Setting.SIZE,1).size());
        assertEquals(-1f,T.nudge(DisplayTuning.Setting.RIGHT,-1).right());
        assertEquals(15f,T.nudge(DisplayTuning.Setting.TURN,1).turn());
        var t=T;for(int i=0;i<20;i++)t=t.nudge(DisplayTuning.Setting.TILT,1);
        assertEquals(180f,t.tilt(),"the last step stops at the end");
        for(var s:DisplayTuning.Setting.values()){
            assertEquals(s.min,s.at(0));assertEquals(s.max,s.at(1));
            assertEquals(s.base,s.at(s.fraction(s.base)),.0001,s+" slider position of the default reads back as the default");
            assertTrue(s.min<=s.base&&s.base<=s.max);
        }
    }

    @Test void everyTextTheModShowsIsInTheLanguageFile() throws Exception{
        var lang=json(MAIN.resolve("resources/assets/eternal_defense_displays/lang/en_us.json"));
        var keys=new TreeSet<String>();
        try(Stream<Path> files=Files.walk(MAIN.resolve("java"))){
            for(var f:files.filter(p->p.toString().endsWith(".java")).toList()){
                var m=Pattern.compile("\"((?:tooltip|message|screen|item|block)\\.eternal_defense_displays[a-z_.]*)\"").matcher(Files.readString(f));while(m.find())keys.add(m.group(1));
                var t=Pattern.compile("\\bt\\(\"([a-z_.]+)\"").matcher(Files.readString(f));while(t.find())keys.add("screen.eternal_defense_displays.tuner."+t.group(1));
            }
        }
        for(var s:DisplayTuning.Setting.values())keys.add("screen.eternal_defense_displays.tuner."+s.key());
        keys.add("item.eternal_defense_displays.display_tuner");for(var s:stands())keys.add("block.eternal_defense_displays."+s);
        for(var k:keys)if(!k.endsWith("."))assertTrue(lang.has(k),"missing text "+k);
        assertTrue(lang.get("screen.eternal_defense_displays.tuner.size").getAsString().contains("%%"),"a percent sign is written %% in the language file");
        for(var k:List.of("insert","swap"))assertTrue(lang.get("tooltip.eternal_defense_displays."+k).getAsString().contains("%s"),k+" names the player's own interact key");
    }

    @Test void theIconAndModelExist(){
        assertTrue(Files.isRegularFile(MAIN.resolve("resources/assets/eternal_defense_displays/textures/item/display_tuner.png")));
        assertTrue(Files.isRegularFile(MAIN.resolve("resources/assets/eternal_defense_displays/models/item/display_tuner.json")));
    }
}
