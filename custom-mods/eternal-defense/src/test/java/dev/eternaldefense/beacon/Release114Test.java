package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 1.1.4 list: fair gun prices, no damage attachments, more names, Ardent drops, elites and Raid control. */
final class Release114Test {
    static String source(String name) throws Exception{return Files.readString(Path.of("src/main/java/dev/eternaldefense/beacon/"+name));}
    static com.google.gson.JsonObject lang() throws Exception{return com.google.gson.JsonParser.parseString(Round0035Test.lang()).getAsJsonObject();}
    static String text(String key) throws Exception{var j=lang();assertTrue(j.has("gui.eternal_defense."+key),key);return j.get("gui.eternal_defense."+key).getAsString();}

    // Crafting cost balancer
    @Test void damageOutputCountsTheMagazineAndTheReload(){
        assertEquals(10*20/(10*.5+2.0),CraftBalance.sustained(20,120,10,2,false,0),1e-9);
        assertEquals(5*30/(5*1.2+3.0),CraftBalance.sustained(30,600,5,3,true,1.2),1e-9,"a bolt action waits for its bolt");
        assertEquals(0,CraftBalance.sustained(0,600,30,2,false,0));
        assertTrue(CraftBalance.sustained(10,600,Integer.MAX_VALUE,2,false,0)>0,"an endless feed counts as a large drum");
        assertEquals(3,CraftBalance.median(List.of(5d,1d,3d)));assertEquals(2.5,CraftBalance.median(List.of(4d,1d,2d,3d)));assertEquals(0,CraftBalance.median(List.of()));
    }
    @Test void aGunFarAboveItsAgeNeedsRareParts(){
        var s=CraftBalance.settings();
        assertEquals(1.4,s.outlierFrom());assertEquals(.8,s.step());assertEquals(4,s.maxUnits());assertEquals(3,s.minPeers());
        assertEquals(List.of("minecraft:diamond","minecraft:emerald","minecraft:blaze_rod"),s.rare());
        assertEquals("minecraft:diamond",s.material().get(1));assertEquals("minecraft:diamond",s.material().get(2));assertEquals("minecraft:emerald",s.material().get(3));
        assertEquals("minecraft:blaze_rod",s.material().get(4));assertEquals("minecraft:blaze_rod",s.material().get(5));
        assertEquals(0,CraftBalance.units(1.39,1.4,.8,4));assertEquals(1,CraftBalance.units(1.47,1.4,.8,4),"the No. 3 Revolver: 1.47 times its Age");
        assertEquals(2,CraftBalance.units(2.2,1.4,.8,4));assertEquals(4,CraftBalance.units(40,1.4,.8,4));
    }
    @Test void anUnknownGunIsPlacedByItsDamage(){
        var medians=Map.of(1,13.05,2,30.4,3,40.0,4,44.9,5,57.7);
        assertEquals(1,CraftBalance.autoAge(5,medians));assertEquals(1,CraftBalance.autoAge(20,medians));assertEquals(2,CraftBalance.autoAge(31,medians));
        assertEquals(4,CraftBalance.autoAge(50,medians));assertEquals(5,CraftBalance.autoAge(900,medians));assertEquals(5,CraftBalance.autoAge(10,Map.of()),"no peers: the last Age");
    }
    @Test void anAgeIsOneLineInOneFile() throws Exception{
        assertEquals(1,CraftBalance.override("bf1:sw_model3"));assertNull(CraftBalance.override("tacz:ak47"));
        assertEquals(1,CraftBalance.override(null,"x:y","bf1:sw_model3"),"any of the item, recipe or gun id");
        assertTrue(source("RecipeRules.java").contains("CraftBalance.override(")&&source("ArmorPlatform.java").contains("CraftBalance.override("),"every table reads the same file");
        assertTrue(source("WeaponPlatform.java").contains("CraftBalance.aged(")&&source("WeaponPlatform.java").contains("CraftBalance.surcharge("));
        assertTrue(text("platform.strong").contains("%s%%"));
    }
    @Test void attachmentsThatRaiseDamageAreNotSold() throws Exception{
        assertTrue(AttachmentDamage.raises(1,0,1,null));assertTrue(AttachmentDamage.raises(0,.1,1,null));assertTrue(AttachmentDamage.raises(0,0,1.2,null));
        assertTrue(AttachmentDamage.raises(0,0,1,"return x*2"),"a scripted change counts as raising");
        assertFalse(AttachmentDamage.raises(0,0,1,null));assertFalse(AttachmentDamage.raises(-2,-.1,.8,""),"lowering damage is fine");
        assertTrue(source("WeaponPlatform.java").contains("!AttachmentDamage.sold(e.output,e.gate.age)"));
    }
    @Test void taczsOwnAttachmentsAreKept(){
        assertEquals(Set.of("tacz"),CraftBalance.settings().keptPacks(),"the rule is for add-on packs only");
        assertTrue(AttachmentDamage.kept("tacz:ammo_mod_hp",Set.of("tacz")));assertTrue(AttachmentDamage.kept("tacz:ammo_mod_he",Set.of("tacz")));
        assertFalse(AttachmentDamage.kept("bf1:ammo_mod_hp",Set.of("tacz")));assertFalse(AttachmentDamage.kept("taczaddon:x",Set.of("tacz")));
    }

    // Names
    @Test void hundredsOfNamePartsInTwoSeparateLists(){
        assertTrue(CodeNames.FIRST.length>=300&&CodeNames.SECOND.length>=300);
        var first=new HashSet<>(List.of(CodeNames.FIRST));var second=new HashSet<>(List.of(CodeNames.SECOND));
        assertEquals(CodeNames.FIRST.length,first.size(),"no repeats");assertEquals(CodeNames.SECOND.length,second.size(),"no repeats");
        first.retainAll(second);assertTrue(first.isEmpty(),"no word in both lists: "+first);
        for(String w:CodeNames.FIRST)assertTrue(w.matches("[A-Z]+"),w);for(String w:CodeNames.SECOND)assertTrue(w.matches("[A-Z]+"),w);
        assertSame(CodeNames.FIRST,TacticalRules.FIRST);
    }
    @Test void theFirstFobIsNotAlwaysTheSameName(){
        var names=new HashSet<String>();for(long salt=1;salt<=200;salt++)names.add(FobRules.codename(salt,1));
        assertTrue(names.size()>150,"each world names its first FOB differently: "+names.size());
        assertEquals(FobRules.codename(42,7),FobRules.codename(42,7),"a world keeps its names");
        String taken=FobRules.codename(9,3);assertNotEquals(taken,FobRules.freeCodename(9,3,List.of(taken)));
    }

    // Ardent Energy
    @Test void onlyMarkedMobsAndBigBossesDropArdent(){
        assertEquals(0,ArdentEnergy.drop(false,0,.0667,2,3,.01,false,12,40,0),"an ordinary wild mob drops none");
        assertEquals(0,ArdentEnergy.drop(false,0,.0667,2,0,.01,true,12,299,0));assertEquals(1,ArdentEnergy.drop(false,.99,.0667,2,0,.01,true,12,300,0),"a 300 health boss drops one");
        assertEquals(1,ArdentEnergy.drop(true,.09,.0667,1.5,0,.01,false,12,20,0),"marked mobs roll at 1.5 times the chance");assertEquals(0,ArdentEnergy.drop(true,.11,.0667,1.5,0,.01,false,12,20,0));
        assertEquals(2,ArdentEnergy.drop(true,.99,.0667,2,0,.01,false,12,20,2),"an elite always drops its own");
        assertEquals(1.5,ArdentEnergy.multiplier(1.5,0,0),1e-9);assertEquals(2.0,ArdentEnergy.multiplier(1.5,50,0),1e-9);assertEquals(1.875,ArdentEnergy.multiplier(1.5,0,1),1e-9);
        assertEquals(1.5,DefenseConfig.MARKED_MULTIPLIER.getDefault(),1e-9);
    }
    @Test void raidsPayAQuarterMoreAndSendMoreMobs(){
        assertEquals(2,Rules.raidArdent(0));assertEquals(14,Rules.raidArdent(10));
        assertEquals(2,Rules.boosted(1),"at least one more");assertEquals(12,Rules.boosted(10));assertEquals(115,Rules.boosted(100));
        for(int t=0;t<=10;t++)for(int w=1;w<=8;w++)assertTrue(Rules.waveEnemies(t,w,1,0,false)>=Rules.boosted(1));
    }

    // Elites
    @Test void elitesGrowFromOneARaidToSeveralAWave(){
        assertEquals(0,Elites.perWave(0));assertEquals(1,Elites.perWave(5));assertEquals(3,Elites.perWave(10),1e-9);
        assertEquals(1,Elites.count(0,2,2,.99,false),"below level 5 the chosen wave always has one");assertEquals(0,Elites.count(0,1,2,.0,false));
        for(int w=1;w<=8;w++)assertEquals(1,Elites.count(5,w,1,.99,false),"level 5: one every wave");
        assertEquals(3,Elites.count(10,1,1,.99,false));assertEquals(4,Elites.count(10,1,1,.99,true),"Elite Cadre adds one a wave");
        assertTrue(Elites.pick(1,1,.99),"the last spawns are certain");assertFalse(Elites.pick(0,5,0));
    }
    @Test void eliteRulesFollowTheOwnersNumbers(){
        for(int l=1;l<=5;l++)assertEquals(l,Elites.healthBonus(Elites.Kind.BADASS,l),"+100%% health a Badass level");
        assertEquals(-.5,Elites.healthBonus(Elites.Kind.MATRIOSKA,1));assertEquals(.5,Elites.healthBonus(Elites.Kind.FLAK_ARMOR,1));assertEquals(.5,Elites.healthBonus(Elites.Kind.BODY_ARMOR,1));
        assertEquals(1.5f,Elites.scale(Elites.Kind.BADASS,1),1e-6);assertEquals(5.0f,Elites.scale(Elites.Kind.BADASS,5),1e-6);assertEquals(1f,Elites.scale(Elites.Kind.HEALER,1));
        assertEquals(.4,Elites.damageFactor(Elites.Kind.FLAK_ARMOR,"explosion"));assertEquals(1.2,Elites.damageFactor(Elites.Kind.FLAK_ARMOR,"bullet"));assertEquals(1.2,Elites.damageFactor(Elites.Kind.FLAK_ARMOR,"melee"));
        assertEquals(.4,Elites.damageFactor(Elites.Kind.BODY_ARMOR,"bullet"));assertEquals(1.2,Elites.damageFactor(Elites.Kind.BODY_ARMOR,"explosion"));assertEquals(1.2,Elites.damageFactor(Elites.Kind.BODY_ARMOR,"melee"));
        assertEquals(1,Elites.damageFactor(Elites.Kind.HEALER,"bullet"));
        assertEquals(1,Elites.maxBadass(0));assertEquals(5,Elites.maxBadass(8));
        for(double r=0;r<1;r+=.01)assertTrue(Elites.badassLevel(2,r)<=2);
        int high=0;for(double r=0;r<1;r+=.001)if(Elites.badassLevel(10,r)==5)high++;assertTrue(high<100,"Badass V is the rarest");
        for(double r=0;r<1;r+=.01)assertNotEquals(Elites.Kind.LONG_SHOT,Elites.choose(r,false,false),"only a shooter can be a Long Shot");
        assertEquals(3,Elites.reward(Elites.Kind.BADASS,3,0));assertEquals(2,Elites.reward(Elites.Kind.MATRIOSKA,1,0));assertEquals(0,Elites.reward(Elites.Kind.MATRIOSKA,1,1),"copies drop none of their own");
        assertEquals(1,Elites.reward(Elites.Kind.SPEEDSTER,1,0));assertEquals(2.0,Elites.berserk(0,20),1e-9);assertEquals(1.0,Elites.berserk(20,20),1e-9);
        assertEquals(2,Elites.MATRIOSKA_COPIES);
    }

    // Raid control
    @Test void modifiersPayMoreForHarderRaids(){
        assertEquals(List.of("reinforcements","elite_cadre","hardened","forced_march","fog_of_war","exposed_beacon"),RaidModifiers.MODS.stream().map(RaidModifiers.Mod::id).toList());
        assertEquals(35,RaidModifiers.payoutPercent(List.of("reinforcements","elite_cadre","nonsense")));
        assertEquals(16,RaidModifiers.dropPercent(List.of("reinforcements"),"air"));assertEquals(8,RaidModifiers.dropPercent(List.of("reinforcements"),"normal"));
        assertEquals(13,RaidModifiers.raised(10,25));assertEquals(10,RaidModifiers.raised(10,0));
        assertEquals(26,RaidModifiers.attackers(20,"normal",List.of("reinforcements")));assertEquals(52,RaidModifiers.attackers(20,"swarm",List.of("reinforcements")));assertEquals(20,RaidModifiers.attackers(20,"normal",List.of()));
        assertEquals(2,RaidModifiers.elites(5,List.of("elite_cadre")),1e-9);
    }
    @Test void specialRaidsAreChosenNotRolled() throws Exception{
        String types=source("RaidTypes.java");
        assertFalse(types.contains("static String roll("),"no random roll any more");
        String mods=source("RaidModifiers.java");assertTrue(mods.contains("id.startsWith(\"special:\")")&&mods.contains("type.equals(\"normal\")"));
        assertTrue(source("BeaconActions.java").contains("raid-mod:"));
        assertEquals("43",BeaconNetwork.PROTOCOL);
        assertTrue(mods.contains("BeaconNetwork.announceOthers(p,\"next_raid\""),"the chooser sees the pick on the page, the others get the popup");
        assertTrue(Elites.LongShotGoal.PRIORITY<0,"the Long Shot's aim outranks the raid's own beacon attack (priority 0)");
        assertTrue(source("BeaconCombat.java").contains("mob.goalSelector.addGoal(0,new RangedBeacon("));
    }
    @Test void theRaidControlTabHasEveryText() throws Exception{
        assertEquals("Raid control",text("tab.raid"));
        for(String key:List.of("raid.page.level","raid.page.mods","raid.cap","raid.next_level","raid.sum","raid.stats.waves","raid.stats.attackers","raid.stats.ardent","raid.break.until","raid.break.bought","raid.special_title","raid.mods_title","raid.mods.sum","raid.mods.none","raid.mods.running","raidmod.bonus","raidmod.bonus.long","raidmod.active","raidmod.paid","raidtype.choose","elite.sighted","elite.paid","elite.badass_level"))text(key);
        for(String t:BeaconClient.ControlScreen.SPECIALS)text("raid.special."+t);
        for(var m:RaidModifiers.MODS)for(String part:List.of("",".short",".desc"))text("raidmod."+m.id()+part);
        for(var k:Elites.Kind.values())text("elite."+k.id);
    }
    @Test void theGuideQuotesTheRealNumbers() throws Exception{
        String control=text("guide.control.detail");
        for(var m:RaidModifiers.MODS)assertTrue(control.contains(Ui_name(m.id())+" +"+m.payout()+"%% and +"+m.drops()+"%%"),m.id());
        assertTrue(control.contains(RaidModifiers.SPECIAL_DROPS+"%%"));
        assertTrue(control.contains(RaidTypes.bonusEnergy(0)+" at level 0, "+RaidTypes.bonusEnergy(5)+" at level 5 and "+RaidTypes.bonusEnergy(10)+" at level 10"));
        String body=text("guide.control.body");
        assertTrue(body.contains("Reinforcements: "+Math.round((RaidModifiers.REINFORCEMENTS-1)*100)+"%% more"));
        assertTrue(body.contains("Hardened: raid mobs have "+Math.round(RaidModifiers.HARDENED_HEALTH*100)+"%% more health"));
        assertTrue(body.contains("Forced March: raid mobs move "+Math.round(RaidModifiers.FORCED_MARCH_SPEED*100)+"%% faster"));
        assertTrue(body.contains("do "+Math.round((RaidModifiers.EXPOSED_BEACON-1)*100)+"%% more damage"));
        assertTrue(text("guide.energy.body").contains((int)ArdentEnergy.BOSS_HEALTH+" health"));
        assertTrue(text("guide.raids.body").contains("# Elite mobs"));
    }
    private static String Ui_name(String id) throws Exception{return text("raidmod."+id);}
}
