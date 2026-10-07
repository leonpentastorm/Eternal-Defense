package dev.createarsenal.beacon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** Keeps the redesigned screens honest: every shown string exists, formats safely, and fits its edition. */
final class LangKeysTest {
    private static final String PREFIX="gui.arsenal_beacon.";
    private static final Map<String,String> LANG=load();
    private static Map<String,String> load(){
        try(var in=LangKeysTest.class.getResourceAsStream("/assets/arsenal_beacon/lang/en_us.json")){
            JsonObject json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var map=new LinkedHashMap<String,String>();json.entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));return map;
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static String get(String key){return LANG.get(PREFIX+key);}

    @Test void everyLiteralKeyUsedByTheScreensExists() throws Exception {
        var pattern=Pattern.compile("Ui\\.(t|plain|edition)\\(\"([^\"]+)\"");
        var missing=new TreeSet<String>();
        try(Stream<Path> files=Files.walk(Path.of("src/main/java/dev/createarsenal/beacon"))){
            for(Path file:files.filter(f->f.toString().endsWith(".java")).toList()){
                Matcher m=pattern.matcher(Files.readString(file));
                while(m.find()){
                    String kind=m.group(1),key=m.group(2);
                    if(key.endsWith("."))continue; // dynamic prefix, covered by the lists below
                    if(kind.equals("edition")){for(String edition:List.of(".pack",".standalone"))if(get(key+edition)==null)missing.add(key+edition);}
                    else if(get(key)==null)missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(),"Missing language keys: "+missing);
    }

    @Test void dynamicallyBuiltKeysExist(){
        var needed=new ArrayList<String>();
        for(String tab:List.of("overview","upgrades","raid","settings"))needed.add("tab."+tab);
        for(String branch:List.of("core","logistics","defense","restoration","reconnaissance","vertical"))for(String part:List.of("name","next","done"))needed.add("upgrade."+branch+"."+part);
        for(String key:List.of("structure","palette","details","lighting","furnishings","layout","factory","platforms"))needed.add("score."+key);
        for(String key:List.of("outline","beam","hurtbox"))needed.add("settings."+key);
        for(String key:List.of("outline","beam","hurtbox"))needed.add("settings."+key+".note");
        for(String phase:List.of("unplaced","preparation","raid","disabled","restore","snapshot","decommissioning"))needed.add("phase."+phase);
        for(String b:List.of("core","logistics","defense","restoration","vertical"))needed.add("upgrade."+b+".now");
        needed.addAll(List.of("support.name.supply","support.name.return","support.called","popup.raid_start","popup.raid_hard","popup.raid_sub","popup.wave","popup.wave_sub","popup.victory","popup.victory_sub","popup.defeat","popup.defeat_sub","popup.support","warning.raid","warning.imminent","upgrade.reconnaissance.now_on","upgrade.reconnaissance.now_off","rewards.head_normal","rewards.head_hard","platform.progression","platform.create_progress","platform.manage.upgrade.ammo","platform.manage.upgrade.attachment","platform.manage.upgrade.armor"));
        for(var t:CannonUpgrades.FireType.values()){needed.add("support.name."+t.callId());needed.add("cannon.type."+t.id);needed.add("cannon."+"type."+t.id+".desc");}
        for(var u:CannonUpgrades.Upgrade.values()){needed.add("cannon.up."+u.id);needed.add("cannon.up."+u.id+".desc");}
        for(String code:List.of("selected","bought","no_energy","maxed"))needed.add("cannon.msg."+code);
        for(String key:List.of("barrage","barrage.wait","preparing","heard"))needed.add("support.notice."+key);
        needed.addAll(List.of("exchange.tab.buy","exchange.tab.sell","exchange.sell","exchange.gain","exchange.sell_hint","support.keep_clear","support.refused.return_blocked","cannon.msg.no_parts","rewards.box","raidtype.next","raidtype.normal","support.refused.bunker_home","platform.manage.buy_support","platform.manage.buy_exchange"));
        for(String key:List.of("owner","hud","current","fire","upgrades","shells","shell","max","title"))needed.add("cannon."+key);
        for(String t:RaidTypes.SPECIAL){needed.add("raidtype."+t);needed.add("raidtype.short."+t);needed.add("raidtype.tip."+t);}
        for(int age=0;age<=5;age++)needed.add("age."+age);
        for(String kind:List.of("gun","ammo","attachment","armor")){needed.add("platform.title."+kind);needed.add("platform.subtitle."+kind);needed.add("platform.station."+kind);needed.add("platform.browsing."+kind);}
        for(String type:WeaponBrowser.TYPES)needed.add("type.gun."+type);
        try{
            var m=Pattern.compile("TYPES=List\\.of\\(([^)]*)\\)").matcher(Files.readString(Path.of("src/main/java/dev/createarsenal/beacon/ArmorPlatform.java")));
            assertTrue(m.find());
            for(String type:m.group(1).split(","))needed.add("type.armor."+type.trim().replace("\"",""));
        }catch(java.io.IOException ex){fail(ex);}
        for(String id:GuideText.IDS){needed.add("guide."+id+".title");needed.add("guide."+id+".body");}
        var missing=needed.stream().filter(k->get(k)==null).toList();
        assertTrue(missing.isEmpty(),"Missing language keys: "+missing);
    }

    @Test void percentSignsCannotBreakMinecraftFormatting(){
        // A '%' must start %s, %1$s or %%. "25% more" is safe, "25%more" is not.
        var bad=new ArrayList<String>();
        var valid=Pattern.compile("%(?:\\d+\\$)?[sd%]");
        for(var entry:LANG.entrySet()){
            String text=entry.getValue();
            for(int i=0;i<text.length();i++){
                if(text.charAt(i)!='%')continue;
                Matcher m=valid.matcher(text).region(i,text.length());
                if(m.lookingAt()){i=m.end()-1;continue;}
                if(i+1>=text.length()||Character.isLetter(text.charAt(i+1))||Character.isDigit(text.charAt(i+1)))bad.add(entry.getKey());
            }
        }
        assertTrue(bad.isEmpty(),"Unsafe '%' in "+bad);
    }

    private static String edition(String id,String part,boolean standalone){return GuideText.raw(id,part,standalone,key->LANG.get(PREFIX+key));}

    @Test void everyGuidePageHasContentForBothEditions(){
        for(String id:GuideText.IDS)for(boolean standalone:new boolean[]{false,true}){
            assertFalse(edition(id,"body",standalone).isBlank(),id+" body for "+(standalone?"standalone":"pack"));
        }
    }

    @Test void standaloneGuideNeverSendsPlayersToPackOnlyMods(){
        for(String id:GuideText.IDS)for(String part:List.of("body","detail")){
            String text=edition(id,part,true);
            for(String forbidden:List.of("FTB","Essential","KubeJS","Create machines","Create recipes","Create precision","Create research","Create Armory","precision mechanism","brass","steam engine"))
                assertFalse(text.contains(forbidden),"standalone "+id+"."+part+" mentions "+forbidden);
        }
    }

    @Test void packGuideKeepsItsFactoryAndCoopInstructions(){
        String all=Stream.of(GuideText.IDS).flatMap(id->Stream.of(edition(id,"body",false),edition(id,"detail",false))).collect(Collectors.joining("\n"));
        for(String required:List.of("Essential","FTB Team","JEI shows the Create recipes","Create Armory","Precision","precision mechanisms","Park and disassemble moving Create contraptions"))assertTrue(all.contains(required),"pack guide lost: "+required);
    }

    @Test void standaloneGuideKeepsItsConfigAndInstallInstructions(){
        String all=Stream.of(GuideText.IDS).flatMap(id->Stream.of(edition(id,"body",true),edition(id,"detail",true))).collect(Collectors.joining("\n"));
        for(String required:List.of("config/arsenal-beacon-standalone.json","crafting table","same standalone beacon and TaCZ","vanilla Ravager","copper, diamonds and gold"))assertTrue(all.contains(required),"standalone guide lost: "+required);
    }

    @Test void guideNeverBakesInAShortcutKey(){
        var tokens=Pattern.compile("\\{([a-z]+)\\}");
        var allowed=Set.of("interact","jei","reload","emotes","shaders","backpack","quests","map");
        for(var entry:LANG.entrySet()){
            Matcher m=tokens.matcher(entry.getValue());
            while(m.find())assertTrue(allowed.contains(m.group(1)),entry.getKey()+" uses unknown token {"+m.group(1)+"}");
            assertFalse(entry.getValue().matches("(?s).*press \\[?[HRU]\\]?[ .].*"),entry.getKey()+" bakes in a key");
        }
    }
}
