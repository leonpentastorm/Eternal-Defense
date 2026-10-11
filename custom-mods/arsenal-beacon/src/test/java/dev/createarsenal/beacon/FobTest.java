package dev.createarsenal.beacon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Forward Operating Bases, slice 1 (0.0.23): the rules, names, saved data, meal zone, Ardent rule, model footprint and texts, without a world. */
final class FobTest {
    static final String SRC="src/main/java/dev/createarsenal/beacon/",ASSETS="src/main/resources/assets/arsenal_beacon/";
    static String source(String name) throws Exception{return Files.readString(Path.of(SRC+name));}
    private static final Map<String,String> LANG=load();
    private static Map<String,String> load(){
        try(var in=FobTest.class.getResourceAsStream("/assets/arsenal_beacon/lang/en_us.json")){
            JsonObject json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var map=new LinkedHashMap<String,String>();json.entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));return map;
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static String gui(String key){return LANG.get("gui.arsenal_beacon."+key);}
    private static String guide(String part,boolean standalone){return GuideText.raw("fob",part,standalone,key->LANG.get("gui.arsenal_beacon."+key));}

    // ---- planting ------------------------------------------------------------------------------------------------------------------------------
    @Test void theOwnersLimitsAreTheConstants(){
        assertEquals(5,FobRules.MAX_FOBS);assertEquals(3,FobRules.MIN_VILLAGERS);assertEquals(32,FobRules.NAME_MAX);
        assertEquals(1,FobRules.VILLAGE_SECTIONS,"vanilla's isVillage: within one section of a bed, workstation or bell");
        assertEquals("arsenal_beacon_fobs_v1",FobData.NAME);
    }
    @Test void plantingIsRefusedInTheBriefsOrder(){
        assertEquals("no_beacon",FobRules.plantProblem(false,5,false,false,true,0),"no beacon comes first");
        assertEquals("max",FobRules.plantProblem(true,5,false,false,true,0));
        assertEquals("max",FobRules.plantProblem(true,9,true,true,false,9),"five or more placed, whatever their state");
        assertEquals("no_raids",FobRules.plantProblem(true,4,false,true,false,9));
        assertEquals("not_village",FobRules.plantProblem(true,4,true,false,false,9));
        assertEquals("taken",FobRules.plantProblem(true,0,true,true,true,9));
        assertEquals("villagers",FobRules.plantProblem(true,0,true,true,false,2));
        assertNull(FobRules.plantProblem(true,0,true,true,false,3),"three villagers are enough");
        assertNull(FobRules.plantProblem(true,4,true,true,false,40));
    }

    // ---- the raid ------------------------------------------------------------------------------------------------------------------------------
    @Test void theRaidIsLostBelowHalfOfTheVillagersAtItsStart(){
        assertFalse(FobRules.raidFailed(7,4),"7 at the start: 4 alive holds");
        assertTrue(FobRules.raidFailed(7,3),"7 at the start: 3 alive loses");
        assertFalse(FobRules.raidFailed(6,3),"exactly half holds");assertTrue(FobRules.raidFailed(6,2));
        assertFalse(FobRules.raidFailed(3,2));assertTrue(FobRules.raidFailed(3,1));
        assertTrue(FobRules.raidFailed(1,0));assertFalse(FobRules.raidFailed(0,0),"no villagers counted at the start: never lost by this rule");
        // what the raid announcement asks to keep, (start + 1) / 2, is always the smallest number that holds
        for(int start=1;start<=40;start++){int keep=(start+1)/2;assertFalse(FobRules.raidFailed(start,keep));assertTrue(FobRules.raidFailed(start,keep-1));}
    }
    @Test void aRaidsStatusMapsToAStateChange(){
        assertEquals(FobRules.Outcome.NONE,FobRules.outcome(false,false,false,false),"gone from the raid list without a result");
        assertEquals(FobRules.Outcome.WON,FobRules.outcome(true,true,false,true),"victory wins over the stop that follows the celebration");
        assertEquals(FobRules.Outcome.LOST,FobRules.outcome(true,false,true,true));
        assertEquals(FobRules.Outcome.NONE,FobRules.outcome(true,false,false,true),"stopped (Peaceful, timeout): no result");
        assertEquals(FobRules.Outcome.RUNNING,FobRules.outcome(true,false,false,false));
        assertEquals(FobRules.State.ACTIVE,FobRules.after(FobRules.State.PLANTED,FobRules.Outcome.WON));
        assertEquals(FobRules.State.DOWN,FobRules.after(FobRules.State.PLANTED,FobRules.Outcome.LOST));
        assertEquals(FobRules.State.PLANTED,FobRules.after(FobRules.State.PLANTED,FobRules.Outcome.NONE),"no result: the raid can be called again");
        assertEquals(FobRules.State.DOWN,FobRules.after(FobRules.State.DOWN,FobRules.Outcome.NONE));
        assertTrue(FobRules.working(FobRules.State.ACTIVE));assertFalse(FobRules.working(FobRules.State.PLANTED));assertFalse(FobRules.working(FobRules.State.DOWN));
        assertTrue(FobRules.mayCallRaid(FobRules.State.PLANTED,false));assertFalse(FobRules.mayCallRaid(FobRules.State.PLANTED,true));
        assertFalse(FobRules.mayCallRaid(FobRules.State.ACTIVE,false));assertFalse(FobRules.mayCallRaid(FobRules.State.DOWN,false),"DOWN is retried by replanting (slice 3 repairs)");
        assertEquals(FobRules.State.PLANTED,FobRules.State.of(-1));assertEquals(FobRules.State.DOWN,FobRules.State.of(99),"a broken save clamps");
    }
    @Test void villagersAreImmortalOnlyOutsideTheirFobsRaid(){
        assertTrue(Fobs.protect(true,false,false));
        assertFalse(Fobs.protect(true,true,false),"during the FOB's raid they can die");
        assertFalse(Fobs.protect(true,false,true),"/kill and the void still work");
        assertFalse(Fobs.protect(false,false,false),"a villager of a deleted FOB is mortal again");
    }

    // ---- names ---------------------------------------------------------------------------------------------------------------------------------
    @Test void namesAreGeneratedUniqueAndRenamable(){
        assertEquals(FobRules.codename(7),FobRules.codename(7),"the same id gives the same name");
        assertTrue(FobRules.codename(7).startsWith("FOB "));
        var seen=new HashSet<String>();for(long id=1;id<=200;id++)seen.add(FobRules.codename(id));
        assertTrue(seen.size()>150,"generated names spread: "+seen.size());
        String first=FobRules.codename(3);
        String other=FobRules.freeCodename(3,List.of(first.toLowerCase(Locale.ROOT)));
        assertNotEquals(first.toLowerCase(Locale.ROOT),other.toLowerCase(Locale.ROOT),"a free name is free in any case");
        assertEquals(first,FobRules.freeCodename(3,List.of("FOB SOMETHING ELSE")));
        assertEquals("empty",FobRules.nameProblem("   ",List.of()));
        assertEquals("too_long",FobRules.nameProblem("x".repeat(FobRules.NAME_MAX+1),List.of()));
        assertNull(FobRules.nameProblem("x".repeat(FobRules.NAME_MAX),List.of()));
        assertEquals("duplicate",FobRules.nameProblem("  north   gate ",List.of("North Gate")),"names are unique in any case, spaces tidied");
        assertNull(FobRules.nameProblem("North Gate",List.of("South Gate")));
        assertEquals("North Gate",FobRules.clean("  North    Gate "));
    }

    // ---- saved data ----------------------------------------------------------------------------------------------------------------------------
    @Test void fobsSaveAndLoadAndOneBrokenRecordIsSkipped(){
        var d=new FobData();var a=d.add(new BlockPos(10,64,-20),123L,500L);var b=d.add(new BlockPos(-300,70,40),456L,900L);
        a.state=FobRules.State.ACTIVE;a.activated=true;b.raidId=17;b.raidVillagers=7;b.name="North Gate";
        b.stockpile.putInt("slice2",1);var dead=new CompoundTag();dead.putString("name","Bob");b.deadVillagers.add(dead);
        assertNotEquals(a.name.toLowerCase(Locale.ROOT),b.name.toLowerCase(Locale.ROOT));
        var tag=d.save(new CompoundTag());
        // a broken entry and a second record with the same name (any case) are fixed on load
        var list=tag.getList("fobs",10);var broken=new CompoundTag();broken.putString("name","no id");list.add(broken);
        var twin=list.getCompound(1).copy();twin.putLong("id",9);twin.putString("name","NORTH GATE");twin.putLong("pos",new BlockPos(5,5,5).asLong());list.add(twin);
        var back=FobData.load(tag);
        assertEquals(3,back.fobs.size(),"the broken entry is skipped, the others load");
        assertEquals(10,back.nextId,"ids are never reused");
        var la=back.byId(a.id);assertEquals(FobRules.State.ACTIVE,la.state);assertTrue(la.activated);assertEquals(a.name,la.name);assertEquals(new BlockPos(10,64,-20),la.pos);assertEquals(123L,la.villageSection);
        var lb=back.byId(b.id);assertEquals("North Gate",lb.name);assertEquals(17,lb.raidId);assertEquals(7,lb.raidVillagers);assertTrue(lb.raidRunning());
        assertEquals(1,lb.stockpile.getInt("slice2"),"slice 2 data is kept as it is");assertEquals("Bob",lb.deadVillagers.getCompound(0).getString("name"));
        assertNotEquals("north gate",back.byId(9).name.toLowerCase(Locale.ROOT),"a duplicate name gets a generated one");
        assertSame(lb,back.at(new BlockPos(-300,70,40)),"found by position");
        assertEquals(1,back.count(FobRules.State.ACTIVE));assertEquals(2,back.count(FobRules.State.PLANTED));
    }

    // ---- meals and Ardent Energy ---------------------------------------------------------------------------------------------------------------
    @Test void anActiveFobZoneCountsAsHomeForMeals(){
        assertEquals(MealZone.Kind.HOME,MealZone.of(true,true),"the Home Zone wins where they overlap");
        assertEquals(MealZone.Kind.HOME,MealZone.of(true,false));
        assertEquals(MealZone.Kind.FOB,MealZone.of(false,true));
        assertEquals(MealZone.Kind.NONE,MealZone.of(false,false));
        assertTrue(MealZone.Kind.FOB.home());assertTrue(MealZone.Kind.HOME.home());assertFalse(MealZone.Kind.NONE.home());
    }
    @Test void vanillaRaidersAreNoArdentFaucet(){
        assertTrue(ArdentEnergy.vanillaRaider(true,1,false),"a raider of a village raid wave");
        assertFalse(ArdentEnergy.vanillaRaider(true,0,false),"a patrol pillager never joined a raid");
        assertFalse(ArdentEnergy.vanillaRaider(true,3,true),"the beacon's own raid mobs still pay");
        assertFalse(ArdentEnergy.vanillaRaider(false,2,false));
    }

    // ---- the code keeps the brief's traps --------------------------------------------------------------------------------------------------------
    @Test void theTrapsOfTheBriefAreRespected() throws Exception{
        String fobs=source("Fobs.java")+source("FobZone.java")+source("FobData.java")+source("FobBlocks.java")+source("MealZone.java");
        assertFalse(fobs.contains("putBoolean(\"arsenalRaider\""),"vanilla raiders are never tagged as the beacon's");
        assertFalse(fobs.contains("RaidRewards.queue")||fobs.contains(".setDirty();CampaignData")||fobs.contains("CampaignData.get(l).beacon="),"the FOB never writes CampaignData");
        assertTrue(source("Fobs.java").contains("CampaignData.get(l.getServer().overworld()).installed()"),"it only asks whether the beacon is planted");
        assertFalse(source("BaseZone.java").contains("Fob"),"BaseZone.problem stays the beacon zone's rule");
        assertFalse(source("FobZone.java").contains("BaseZone.problem"),"the FOB zone is never the beacon zone's rule");
        assertFalse(fobs.contains("setInvulnerable"),"villagers are protected by the damage event, not the invulnerable flag");
        assertTrue(source("ArsenalStructures.java").contains("state.is(ArsenalBeacon.FOB_BEACON.get())"),"the FOB Beacon is two tall like the shop");
        assertTrue(source("PlayerMeals.java").contains("MealZone.at(p)")&&!source("PlayerMeals.java").contains("BaseZone.problem"),"meals use MealZone");
        String net=source("BeaconNetwork.java");assertTrue(net.contains("\"31-standalone\"")&&net.contains("\"31-pack\""),"protocol 31 since 0.0.23");
    }
    /**
     * Forge names the class it generates for a {@code @SubscribeEvent} method after the SIMPLE name of its class, the method and the event's
     * simple name, in the class's package: two handlers that share all three load one class for both and crash at the first event (found
     * by the 0.0.23 real-client run: {@code Fobs.Events.attack} against {@code RaidAdaptation.Events.attack}, both on LivingAttackEvent).
     */
    @Test void noTwoEventHandlersShareTheBusGeneratedName() throws Exception{
        var cls=java.util.regex.Pattern.compile("\\bclass\\s+(\\w+)");
        var handler=java.util.regex.Pattern.compile("@(?:[\\w.]+\\.)?SubscribeEvent(?:\\([^)]*\\))?\\s*public\\s+(?:static\\s+)?void\\s+(\\w+)\\(\\s*([\\w.]+)\\s+\\w+\\)");
        var seen=new HashMap<String,String>();var duplicates=new ArrayList<String>();int handlers=0;
        try(var files=Files.walk(Path.of(SRC))){
            for(var file:files.filter(f->f.toString().endsWith(".java")).toList()){
                // the opt-in test runners (one -D switch each, never registered together) share Runner.tick and Runner.register; the FOB
                // runner's methods have their own names, and every handler the game registers is checked
                String name=file.getFileName().toString();boolean harness=(name.endsWith("GameTests.java")||name.endsWith("Tests.java"))&&!name.equals("FobGameTests.java");
                if(harness)continue;
                String text=Files.readString(file);var m=handler.matcher(text);
                while(m.find()){
                    // the class a handler sits in: the nearest class declared before it whose body is still open there
                    String owner=null;var c=cls.matcher(text);
                    while(c.find()&&c.start()<m.start()){int open=text.indexOf('{',c.end());if(open>=0&&open<m.start()&&depth(text,open,m.start())>0)owner=c.group(1);}
                    String event=m.group(2);event=event.substring(event.lastIndexOf('.')+1);
                    String key=owner+"_"+m.group(1)+"_"+event;handlers++;
                    var before=seen.put(key,file.getFileName().toString());if(before!=null)duplicates.add(key+" ("+before+" and "+file.getFileName()+")");
                }
            }
        }
        assertTrue(handlers>30,"the scan finds the handlers: "+handlers);
        assertTrue(seen.containsKey("FobEvents_attack_LivingAttackEvent")&&seen.containsKey("Events_attack_LivingAttackEvent"),"both attack handlers were found: "+seen.keySet());
        assertTrue(duplicates.isEmpty(),"handlers that would share one generated class: "+duplicates);
    }
    /** Brace depth of the block opened at {@code open}, measured at {@code at} (positive: still open). */
    private static int depth(String text,int open,int at){int d=0;for(int i=open;i<at;i++){char ch=text.charAt(i);if(ch=='{')d++;else if(ch=='}')d--;}return d;}
    /** The footprint comes from the owner's model: y 0..32 is two blocks, x and z stay inside one (the flag tip reaches 16.2). */
    @Test void theFootprintComesFromTheOwnersModel() throws Exception{
        var json=JsonParser.parseString(Files.readString(Path.of(ASSETS+"models/block/fob_station.json"))).getAsJsonObject();
        double x0=99,x1=-99,y0=99,y1=-99,z0=99,z1=-99;
        for(var e:json.getAsJsonArray("elements")){var o=e.getAsJsonObject();var from=o.getAsJsonArray("from");var to=o.getAsJsonArray("to");
            x0=Math.min(x0,Math.min(from.get(0).getAsDouble(),to.get(0).getAsDouble()));x1=Math.max(x1,Math.max(from.get(0).getAsDouble(),to.get(0).getAsDouble()));
            y0=Math.min(y0,Math.min(from.get(1).getAsDouble(),to.get(1).getAsDouble()));y1=Math.max(y1,Math.max(from.get(1).getAsDouble(),to.get(1).getAsDouble()));
            z0=Math.min(z0,Math.min(from.get(2).getAsDouble(),to.get(2).getAsDouble()));z1=Math.max(z1,Math.max(from.get(2).getAsDouble(),to.get(2).getAsDouble()));}
        assertEquals(FobBlocks.TALL,(int)Math.ceil(y1/16.0));assertTrue(y0>=0);
        assertEquals(FobBlocks.WIDE,(int)Math.ceil((x1-0.25)/16.0),"x reaches "+x1);assertEquals(FobBlocks.DEEP,(int)Math.ceil(z1/16.0));assertTrue(x0>=0&&z0>=0);
        var blockstate=Files.readString(Path.of(ASSETS+"blockstates/fob_beacon.json"));
        for(String f:List.of("north","east","south","west"))assertTrue(blockstate.contains("\"facing="+f+"\""));
        assertTrue(blockstate.contains("arsenal_beacon:block/fob_station"));
        assertTrue(Files.readString(Path.of(ASSETS+"models/item/fob_beacon.json")).contains("arsenal_beacon:block/fob_station"));
    }
    /** The owner's files are installed byte for byte: the model and the texture equal the copies in docs/art. */
    @Test void theOwnersArtIsUnchanged() throws Exception{
        Path art=Path.of("../../docs/art/Create-Arsenal-FOB-Mixer-Enchanting");
        assertEquals(sha(art.resolve("textures/block/fob_station.png")),sha(Path.of(ASSETS+"textures/block/fob_station.png")));
        assertEquals(sha(art.resolve("minecraft-models/block/fob_station.json")),sha(Path.of(ASSETS+"models/block/fob_station.json")));
    }
    private static String sha(Path p) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}

    // ---- what the player reads -----------------------------------------------------------------------------------------------------------------
    @Test void everyMessageTheFobCodeSendsExists(){
        var keys=new ArrayList<String>(List.of("fob.refused.overworld","fob.refused.no_room","fob.unregistered","fob.removed_plain","fob.planted","fob.raid_started","fob.won","fob.lost",
            "fob.lost_villagers","fob.raid_over","fob.renamed","fob.removed","fob.rename.blank","fob.result.closed","fob.result.no_energy","fob.result.raid_called",
            "confirm.fob_remove.head","confirm.fob_remove.body","confirm.fob_remove.go","hud.fob.title","hud.fob.here","hud.fob.here.raid","hud.fob.count"));
        for(String p:List.of("no_beacon","max","no_raids","not_village","taken","villagers"))keys.add("fob.refused."+p);
        for(String p:List.of("not_now","impossible","come_closer"))keys.add("fob.raid."+p);
        for(String p:List.of("empty","too_long","duplicate"))keys.add("fob.rename."+p);
        for(var s:FobRules.State.values())keys.add("fob.state."+s.id());
        for(var s:FobRules.State.values())keys.add("fob.screen.about."+s.id());
        for(String id:Fobs.SHOP)keys.add("fob.result.bought."+id);
        for(String k:keys)assertNotNull(gui(k),"missing "+k);
        assertNotNull(LANG.get("block.arsenal_beacon.fob_beacon"));assertNotNull(LANG.get("effect.arsenal_beacon.fob_zone"));assertNotNull(LANG.get("effect.arsenal_beacon.fob_zone.description"));
        assertTrue(gui("fob.rename.too_long").contains(FobRules.NAME_MAX+" characters"));
    }
    @Test void theFieldGuideQuotesTheRealNumbers(){
        for(boolean standalone:new boolean[]{false,true}){
            String text=guide("body",standalone)+"\n"+guide("detail",standalone);
            for(String expected:List.of("at least "+FobRules.MIN_VILLAGERS+" villagers","Up to "+FobRules.MAX_FOBS+" FOBs","fewer than half",
                    "Support Flare "+SupportRules.SUPPLY_FLARE_PRICE+", Return Flare "+SupportRules.RETURN_FLARE_PRICE+" and Fire Support Flare "+SupportRules.FIRE_FLARE_PRICE,
                    "at most "+FobRules.NAME_MAX+" characters","up to "+FobRules.ZONE_REACH+" sections sideways and "+FobRules.ZONE_REACH_Y+" up or down","With 7 villagers, keep "+(7+1)/2))
                assertTrue(text.contains(expected),(standalone?"standalone":"pack")+" guide does not say '"+expected+"'");
            String price=Economy.FOB_IRON+" iron, "+Economy.FOB_COPPER+" copper, "+Economy.FOB_REDSTONE+" redstone, "+Economy.FOB_GLASS+" glass"
                +(standalone?" and "+Economy.FOB_ARDENT+" Ardent Energy":", "+Economy.FOB_LOGISTICS+" logistics modules and "+Economy.FOB_PLATING+" reinforced plating");
            assertTrue(text.contains(price),(standalone?"standalone":"pack")+" price: "+price);
        }
        assertEquals(SupportRules.SUPPLY_FLARE_PRICE,Economy.fobFlare("supply"));assertEquals(SupportRules.RETURN_FLARE_PRICE,Economy.fobFlare("return"));assertEquals(SupportRules.FIRE_FLARE_PRICE,Economy.fobFlare("fire"));
    }
}
