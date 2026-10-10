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
        needed.addAll(List.of("support.name.supply","support.name.return","support.called","popup.raid_start","popup.raid_hard","popup.raid_sub","popup.wave","popup.wave_sub","popup.victory","popup.victory_sub","popup.defeat","popup.defeat_sub","popup.support","warning.raid","warning.imminent","upgrade.reconnaissance.now","upgrade.reconnaissance.after","upgrade.reconnaissance.after_one","upgrade.reconnaissance.at_once","upgrade.buy_parts","upgrade.short_parts","rewards.head_normal","rewards.head_hard","platform.progression","platform.create_progress","platform.manage.upgrade.ammo","platform.manage.upgrade.attachment","platform.manage.upgrade.armor"));
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

    /** Keys that reach {@code Ui.t} through helper methods, and the full keys the server puts in messages, are invisible to the first test. */
    @Test void everyKitchenAndDispenserKeyNamedInTheSourceExists() throws Exception {
        var short_=Pattern.compile("\"((?:kitchen|bowls|milk|dispenser)\\.[a-z_]+(?:\\.[a-z_]+)*)\"");
        var full=Pattern.compile("\"(gui\\.arsenal_beacon\\.[a-z_]+(?:\\.[a-z_]+)*)\"");
        var missing=new TreeSet<String>();
        try(Stream<Path> files=Files.walk(Path.of("src/main/java/dev/createarsenal/beacon"))){
            for(Path file:files.filter(f->f.toString().endsWith(".java")).toList()){
                String text=Files.readString(file);
                Matcher a=short_.matcher(text);while(a.find())if(get(a.group(1))==null&&!a.group(1).endsWith("."))missing.add(a.group(1));
                Matcher b=full.matcher(text);while(b.find())if(!LANG.containsKey(b.group(1)))missing.add(b.group(1));
            }
        }
        assertTrue(missing.isEmpty(),"Language keys named in code but missing: "+missing);
    }
    @Test void theRaidsCardExplainsTheRedGateInPlainWords(){
        for(boolean standalone:new boolean[]{false,true}){
            String body=edition("raids","body",standalone);
            assertTrue(body.contains("red gate")&&body.contains("lava, a chasm or a wide lake")&&body.contains("You can shoot it"),"the gate is explained: what it is, what it does and what to do about it");
            assertTrue(body.lines().filter(l->l.contains("red gate")).allMatch(l->l.length()<=330),"one short line");
        }
    }
    @Test void kitchenFamiliesOfKeysAreComplete(){
        var needed=new ArrayList<String>();
        for(String p:List.of("ingredients","ingredient","staple","effects","pot","pot_full","output","sandwich_types","batch_types","quantity","selection","missing","slots","stew_locked"))needed.add("kitchen.problem."+p);
        for(String p:List.of("ready","gather","missing","ingredients","ingredient","staple","effects","selection","sandwich_types","batch_types","quantity","pot","pot_full","output","slots","stew_locked"))needed.add("kitchen.hint."+p);
        for(String k:List.of("replace.title","replace.body","replace.yes","replace.no","tip.cook_replace","notice.replaced"))needed.add("kitchen."+k);
        for(String n:List.of("cooked","made","gathered","gather_short","gather_choose","gather_bread","gather_missing","gather_blocked","order_full","order_tier","order_conflict","order_reset_stew","order_reset_sandwich","returned","table_empty","stew_locked"))needed.add("kitchen.notice."+n);
        for(String group:List.of("protein","fish","vegetables","fruit","grain","fungi")){needed.add("kitchen.group."+group);needed.add("kitchen.group_short."+group);}
        for(var e:MealRules.Effect.values()){needed.add("meal.effect."+e.id);needed.add("meal.bonus."+e.id);}
        var missing=needed.stream().filter(k->get(k)==null).toList();assertTrue(missing.isEmpty(),"Missing language keys: "+missing);
        for(var key:List.of("kitchen.notice.cooked","kitchen.notice.gather_short","milk.cleared"))assertTrue(get(key).contains("$"),key+" reorders its arguments");
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

    @Test void guideExplainsCurrentFoodUnlocksAndRaidBehavior(){
        for(boolean standalone:new boolean[]{false,true}){
            String kitchen=edition("kitchen","body",standalone),mixes=edition("mixes","body",standalone),raids=edition("raids","body",standalone);
            String detail=edition("kitchen","detail",standalone);
            assertTrue(kitchen.contains("Gather ingredients")&&kitchen.contains("Make sandwich")&&kitchen.contains("Cook stew"),"the kitchen page teaches the three steps");
            assertTrue(detail.contains("3/5/7/9 servings")&&detail.contains("Doubling unlocks at Mk III")&&detail.contains("EACH group")&&detail.contains("only Mk IV can double one"),"the double rule stays documented");
            assertTrue(kitchen.contains("Mk I: sandwiches only")&&kitchen.contains("Mk II: unlocks stew")&&kitchen.contains("Mk III: meals last 25 minutes")&&kitchen.contains("Mk IV: meals last 30 minutes"),"the four hall levels are spelled out");
            assertFalse(kitchen.contains("Mix guide")||detail.contains("Mix guide")||edition("mixes","body",standalone).contains("Mix guide"),"the Mix guide screen no longer exists");
            for(var mix:MealRules.MIXES)assertTrue(mixes.contains(get("meal.effect."+mix.effect().id)),"Missing legendary recipe "+mix.effect());
            assertTrue(raids.contains("sunlight only")&&raids.contains("25%%")&&raids.contains("Turrets"));
            assertTrue(edition("stations","body",standalone).contains("Workshop Fabrication"));
        }
    }
    @Test void resourceRegenerationDoesNotOwnGuideProse()throws Exception{
        String generator=Files.readString(Path.of("../../tools/kitchen/generate_resources.py"));
        assertFalse(generator.contains("'guide."),"Kitchen regeneration must preserve guide edits in the language source");
    }

    private static final Pattern SENTENCE_END=Pattern.compile("(?<=[.!?])\\s+");

    /** Every feature card tells a newcomer the same five things in the same order. */
    @Test void everyGuideCardFollowsTheSameFormat(){
        var broken=new ArrayList<String>();
        for(boolean standalone:new boolean[]{false,true})for(String id:GuideText.IDS){
            if(id.equals(GuideText.REFERENCE))continue;
            String heading="";var labels=new ArrayList<String>();
            var lines=new ArrayList<>(List.of(edition(id,"body",standalone).split("\n")));lines.add("# end");
            for(String line:lines){
                if(line.startsWith("# ")){
                    if(labels.contains("What it is")){
                        for(String needed:List.of("You get","How it works","How to unlock"))if(!labels.contains(needed))broken.add(id+"/"+heading+" lacks '"+needed+"'");
                        if(labels.indexOf("You get")<labels.indexOf("What it is")||labels.indexOf("How it works")<labels.indexOf("You get")||labels.indexOf("How to unlock")<labels.indexOf("How it works"))broken.add(id+"/"+heading+" has its lines out of order");
                    }
                    heading=line.substring(2);labels.clear();
                }else if(line.startsWith("- ")){
                    int colon=line.indexOf(": ");if(colon>0)labels.add(line.substring(2,colon));
                }
            }
        }
        assertTrue(broken.isEmpty(),"Guide cards out of format: "+broken);
    }

    /** The guide is for somebody who just spawned in: short lines, short sentences, no walls of text. */
    @Test void guideSentencesStayShortAndPlain(){
        var long_=new ArrayList<String>();
        for(boolean standalone:new boolean[]{false,true})for(String id:GuideText.IDS){
            if(id.equals(GuideText.REFERENCE))continue;
            for(String line:edition(id,"body",standalone).split("\n")){
                if(line.length()>330)long_.add(id+": line of "+line.length()+" characters: "+line.substring(0,Math.min(50,line.length())));
                for(String sentence:SENTENCE_END.split(line))if(sentence.split("\\s+").length>42)long_.add(id+": sentence over 42 words: "+sentence.substring(0,Math.min(60,sentence.length())));
            }
        }
        assertTrue(long_.isEmpty(),"Guide copy too dense for a new player: "+long_);
    }

    /** The side menu shows one item per page; a page added to the list without an icon would silently get the book. */
    @Test void everyGuidePageHasItsOwnMenuIcon()throws Exception{
        String source=Files.readString(Path.of("src/main/java/dev/createarsenal/beacon/BeaconClient.java"));
        int start=source.indexOf("static ItemStack icon(String id)");assertTrue(start>0,"the menu icon method moved");
        String method=source.substring(start,source.indexOf("private RichText text()",start));
        for(String id:GuideText.IDS)if(!id.equals(GuideText.REFERENCE))assertTrue(method.contains("case \""+id+"\"->"),"no menu icon for page "+id);
    }

    /** Add a block or an item and this fails until the guide has a card for it. */
    @Test void everyRegisteredFeatureHasAGuideCard()throws Exception{
        var cards=Map.ofEntries(Map.entry("defense_beacon","Defense Beacon"),Map.entry("beacon_controller","Recovery Shovel"),Map.entry("field_guide","Field Guide"),
            Map.entry("gun_platform","Weapon Platform"),Map.entry("ammo_platform","Ammo Platform"),Map.entry("attachment_platform","Attachment Platform"),Map.entry("armor_platform","Armor Platform"),
            Map.entry("universal_ammo_coin","Ammo Coins"),Map.entry("ardent_energy","Ardent Energy"),Map.entry("exchange_shop","Exchange Shop"),
            Map.entry("support_platform","Support Platform"),Map.entry("support_cannon","Support Cannon"),Map.entry("support_flare","Support Flare"),Map.entry("return_flare","Return Flare"),Map.entry("fire_support_flare","Fire Support Flare"),
            Map.entry("mess_hall_mk1","Mess Hall"),Map.entry("cook_pot","Cook Pot and stew"),Map.entry("prepared_sandwich","Sandwich"),Map.entry("bowl_dispenser","Bowl Dispenser"),
            Map.entry("milk_dispenser","Milk Dispenser and Milk Bottle"),Map.entry("milk_bottle","Milk Dispenser and Milk Bottle"));
        var ignored=Set.of("structure_part","mess_hall_mk2","mess_hall_mk3","mess_hall_mk4","reinforced_plating","logistics_module","resonance_coil","restoration_matrix");
        String source=Files.readString(Path.of("src/main/java/dev/createarsenal/beacon/ArsenalBeacon.java"));
        var registered=new TreeSet<String>();
        for(String regex:List.of("(?:BLOCKS|ITEMS)\\.register\\(\"([a-z0-9_]+)\"","part\\(\"([a-z_]+)\"\\)","kitchen\\(\"([a-z0-9_]+)\"")){Matcher m=Pattern.compile(regex).matcher(source);while(m.find())registered.add(m.group(1));}
        Matcher platforms=Pattern.compile("platform\\(\"([a-z]+)\"\\)").matcher(source);while(platforms.find())registered.add(platforms.group(1)+"_platform");
        var unknown=registered.stream().filter(id->!cards.containsKey(id)&&!ignored.contains(id)).toList();
        assertTrue(unknown.isEmpty(),"Registered but missing from the Field Guide (add a card, then list it here): "+unknown);
        for(boolean standalone:new boolean[]{false,true}){
            String all=Stream.of(GuideText.IDS).map(id->edition(id,"body",standalone)).collect(Collectors.joining("\n"));
            var headings=Stream.of(all.split("\n")).filter(l->l.startsWith("# ")).map(l->l.substring(2)).collect(Collectors.toSet());
            for(String heading:Stream.concat(cards.values().stream(),Stream.of("Gun Guide","Gun Displays")).collect(Collectors.toSet()))assertTrue(headings.contains(heading),(standalone?"standalone":"pack")+" guide has no card named '"+heading+"'");
        }
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
