package dev.createarsenal.beacon;

import dev.createarsenal.beacon.RouteRules.Loot;
import dev.createarsenal.beacon.RouteRules.Slot;
import dev.createarsenal.beacon.RouteRules.Specialty;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 0.0.32 list, the parts that need no world: dragon's breath at S, village research, the monster hunter guild, the icons, the
 *  Command Table's names switch. The 30-minute balance is in {@link RouteTest}. */
final class Round0032Test {
    static Loot find(List<Loot> list,String id){return list.stream().filter(l->l.id().equals(id)).findFirst().orElseThrow(()->new AssertionError(id));}

    @Test void dragonsBreathIsSAndOnlyThere(){
        assertEquals(5,find(RouteRules.POTION_LOOT,"minecraft:dragon_breath").tier());
        for(var list:List.of(RouteRules.EMERALD_LOOT,RouteRules.MINE_LOOT,RouteRules.FARM_LOOT,RouteRules.FISHING_LOOT,RouteRules.HUSBANDRY_LOOT,RouteRules.TRADER_LOOT,RouteRules.MONSTER_LOOT))
            assertTrue(list.stream().noneMatch(l->l.id().equals("minecraft:dragon_breath")),"dragon's breath only with the potion makers");
        int last=0;for(var l:RouteRules.POTION_LOOT){assertTrue(l.tier()>=last,"the potion makers' list runs D to S: "+l.id());last=l.tier();}
    }
    @Test void theMonsterHunterGuildHasEveryMonsterDropButTheBosses(){
        var ids=new HashSet<String>();for(var l:RouteRules.MONSTER_LOOT)assertTrue(ids.add(l.id()),"twice: "+l.id());
        for(String drop:List.of("rotten_flesh","bone","string","arrow","gunpowder","spider_eye","slime_ball","magma_cream","ender_pearl","blaze_rod",
                "ghast_tear","phantom_membrane","shulker_shell","prismarine_shard","prismarine_crystals","glowstone_dust","wither_skeleton_skull","totem_of_undying"))
            assertTrue(ids.contains("minecraft:"+drop),"no "+drop);
        for(String boss:List.of("nether_star","dragon_egg","dragon_head","elytra"))assertFalse(ids.contains("minecraft:"+boss),boss+" is a boss's");
        assertEquals(4,find(RouteRules.MONSTER_LOOT,"minecraft:ender_pearl").tier(),"ender pearls, which the pack's recipes need, are A");
        assertSame(RouteRules.MONSTER_LOOT,Specialty.MONSTERS.options());
        assertTrue(RouteRules.MONSTER_LOOT.size()<=Routes.CHOICES);
    }
    @Test void researchOpensAAtLevel2AndSAtLevel4(){
        assertArrayEquals(new int[]{0,16,48,96,160,240},RouteRules.RESEARCH_XP);
        assertEquals(0,RouteRules.level(15));assertEquals(1,RouteRules.level(16));assertEquals(4,RouteRules.level(160));assertEquals(5,RouteRules.level(10_000));
        var pearl=find(RouteRules.MONSTER_LOOT,"minecraft:ender_pearl");var tear=find(RouteRules.POTION_LOOT,"minecraft:ghast_tear");var sugar=find(RouteRules.POTION_LOOT,"minecraft:sugar");
        assertTrue(RouteRules.unlocked(sugar,0));assertFalse(RouteRules.unlocked(pearl,1));assertTrue(RouteRules.unlocked(pearl,2));
        assertFalse(RouteRules.unlocked(tear,3));assertTrue(RouteRules.unlocked(tear,4));
        // a locked choice falls back to the first item, and the farm, the mine and emeralds have nothing locked
        int tearAt=RouteRules.POTION_LOOT.indexOf(tear);
        assertEquals(0,RouteRules.effectiveChoice(Slot.SPECIALTY,Specialty.POTIONS,tearAt,3));assertEquals(tearAt,RouteRules.effectiveChoice(Slot.SPECIALTY,Specialty.POTIONS,tearAt,4));
        for(var slot:List.of(Slot.EMERALD,Slot.MINE,Slot.FARM))for(var l:RouteRules.options(slot,Specialty.POTIONS))assertTrue(RouteRules.unlocked(l,0),l.id());
        assertEquals(2.25,RouteRules.slotMultiplier(Slot.SPECIALTY,5),1e-9);assertEquals(1.25,RouteRules.slotMultiplier(Slot.FARM,5),1e-9);
        assertEquals(1,RouteRules.XP_EMERALD);assertEquals(4,RouteRules.XP_DIAMOND);assertEquals(2,RouteRules.XP_ARDENT);
    }
    @Test void theResearchButtonsAreTheirOwn(){
        var ids=new HashSet<Integer>(List.of(Fobs.BUY_SUPPLY,Fobs.BUY_RETURN,Fobs.BUY_FIRE,Fobs.CALL_RAID,Fobs.BUY_GATE,Fobs.REPAIR,Fobs.OUTLINE));
        for(int i=0;i<2*Fobs.RESEARCH_TEN;i++)assertTrue(ids.add(Fobs.RESEARCH+i),"research button "+i);
        assertEquals(Fobs.RESEARCH_TEN,Fobs.RESEARCH_ITEMS.size());
        String fobs=SosTest.source("Fobs.java");
        assertTrue(fobs.contains("InventoryPayment.plan"),"research takes the items like every other payment");
        assertTrue(SosTest.source("Routes.java").contains("\"gate.result.locked\""),"the server refuses a locked choice");
    }
    /** 0.0.32: a round medallion with a villager at the job for every loot slot, every specialty and research; the first flat icons kept. */
    @Test void everySlotAndSpecialtyHasItsIcon() throws Exception{
        var names=new java.util.ArrayList<String>(List.of("slot_emerald","slot_mine","slot_farm","slot_specialty","research","lock"));
        for(var spec:Specialty.values())names.addAll(spec.icons());   // 0.0.34: fishing and animal husbandry share one specialty, both icons
        for(String name:names){
            Path icon=Path.of(EnchantTest.ASSETS+"textures/gui/tribute/"+name+".png"),flat=Path.of(EnchantTest.ASSETS+"textures/gui/tribute/flat/"+name+".png");
            var img=javax.imageio.ImageIO.read(icon.toFile());
            assertEquals(32,img.getWidth(),name);assertEquals(32,img.getHeight(),name);
            assertTrue(Files.exists(flat),"the flat "+name+" is kept");
            if(!name.equals("lock")){assertEquals(0,img.getRGB(0,0)>>>24,name+" is round: its corner is clear");assertEquals(255,img.getRGB(16,16)>>>24,name);}
        }
        assertTrue(SosTest.source("GateClient.java").contains("g.blit(tributeTexture(name),x,y,16,16,0,0,32,32,32,32)"),"drawn at 16 GUI pixels");
    }
    @Test void theMapCanHideTheFobNames() throws Exception{
        String table=SosTest.source("TacticalClient.java");
        assertTrue(table.contains("static boolean fobNames=true"));
        assertEquals(2,table.split("if\\(!fobNames\\)continue;",-1).length-1,"the flags' names and the arrows' names");
        String lang=Files.readString(Path.of(EnchantTest.ASSETS+"lang/en_us.json"));
        for(String key:List.of("tactical.fob.names","tactical.fob.names.tip","fob.tab.research","fob.research.title","gate.specialty.monsters","gate.result.locked","gate.pick.locked"))
            assertTrue(lang.contains("\"gui.arsenal_beacon."+key+"\""),"no line for "+key);
        // Fobs.announce puts "[FOB] " in front of every line: the real client read "[FOB] [FOB] North Gate reached research level 1."
        assertFalse(lang.contains("research.levelup\": \"[FOB]")||lang.contains("research.levelup.a\": \"[FOB]")||lang.contains("research.levelup.s\": \"[FOB]"),"one prefix only");
    }
    /** The SOS raid started before a teleported player's village had its villagers ("Keep at least 0 of its 0 villagers alive"). */
    @Test void anSosAnswerWaitsForTheVillage(){
        assertEquals(3,SosRules.ARRIVE_SECONDS);
        String sos=SosTest.source("FobSos.java");
        assertTrue(sos.contains("settled=here!=null&&villagersLoaded(l,open)?settled+1:0;")&&sos.contains("settled>=SosRules.ARRIVE_SECONDS"),"seconds in a row, the village loaded");
    }
}
