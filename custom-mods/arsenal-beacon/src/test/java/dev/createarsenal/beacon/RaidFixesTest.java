package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's list of 0.0.20: gate spawns, the speed burst, the cannon menu's card, the Cryo Shell's reach. */
final class RaidFixesTest {
    @Test void reinforcementsComeThroughGatesOnlyAfterNaturalGroundFailedForAWhile(){
        assertFalse(RaidSpawns.gateDue(Long.MIN_VALUE,5000),"natural ground was found: no gates");
        assertFalse(RaidSpawns.gateDue(1000,1000+RaidSpawns.GATE_AFTER_TICKS-1));
        assertTrue(RaidSpawns.gateDue(1000,1000+RaidSpawns.GATE_AFTER_TICKS),"after "+RaidSpawns.GATE_AFTER_TICKS/20+" seconds without");
        var rings=RaidSpawns.gateRings(8,100);
        assertEquals(100,rings.get(0),"from the outer edge of the active chunks");
        assertEquals(8+RaidSpawns.GATE_BEYOND_ZONE,rings.get(rings.size()-1),"in to the closest allowed, never inside the zone");
        for(int i=1;i<rings.size();i++)assertTrue(rings.get(i)<rings.get(i-1));
        assertEquals(List.of(20),RaidSpawns.gateRings(8,10),"active chunks smaller than the margin: only the closest ring");
        assertTrue(RaidSpawns.GATE_BEYOND_ZONE<RaidSpawns.CLEARANCE,"gates come closer than natural spawns");
    }
    @Test void everyRaidEnemyGetsTheSpeedBurstAndComesOutOfAGate() throws Exception{
        assertEquals(10,RaidSpawns.RUSH_SECONDS);assertEquals(1,RaidSpawns.RUSH_AMPLIFIER,"Speed II");
        String base="src/main/java/dev/createarsenal/beacon/";
        for(String file:List.of("ArsenalBeacon.java","HardRaids.java","SpecialForcesRaids.java"))
            assertTrue(java.nio.file.Files.readString(java.nio.file.Path.of(base+file)).contains("RaidSpawns.rush(mob)"),file+" gives its raiders the speed burst");
        assertTrue(java.nio.file.Files.readString(java.nio.file.Path.of(base+"ArsenalBeacon.java")).contains("RaiderGates.spawnGate(l,spawnPos,"),"a gate spawn opens a gate");
        assertTrue(java.nio.file.Files.readString(java.nio.file.Path.of(base+"HuntWarband.java")).contains("RaiderGates.spawnGate(l,spot,"),"a hunt warband comes out of gates");
        assertFalse(java.nio.file.Files.readString(java.nio.file.Path.of(base+"RaidSpawns.java")).contains("Reinforcements are waiting"),"the old repeating line is gone");
    }
    @Test void aSpawnGateStandsBehindItsEnemyIsBiggerAndStaysLongEnoughToSee(){
        var spot=new net.minecraft.world.phys.Vec3(10.5,64,0.5);
        var g=RaiderGates.behind(spot,new net.minecraft.world.phys.Vec3(0.5,70,0.5),RaiderGates.SPAWN_GATE_BEHIND);
        assertEquals(10.5+RaiderGates.SPAWN_GATE_BEHIND,g.x,1e-9,"further from the base than its enemy");assertEquals(0.5,g.z,1e-9);assertEquals(64,g.y,1e-9,"level, on the ground");
        var eye=new net.minecraft.world.phys.Vec3(13.5,64,4.5);
        assertEquals(spot.distanceTo(eye)+2,RaiderGates.behind(spot,eye,2).distanceTo(eye),1e-9,"straight away from the one looking");
        assertSame(spot,RaiderGates.behind(spot,null,2),"nobody to be seen from: on the spot");assertSame(spot,RaiderGates.behind(spot,new net.minecraft.world.phys.Vec3(10.5,90,0.5),2));
        assertTrue(RaiderGates.SPAWN_GATE_BEHIND>0.6,"an enemy is 0.6 blocks wide: the gate shows beside it");
        assertTrue(RaiderGates.SPAWN_GATE_LINGER_TICKS>=100,"open 5 seconds after the last one");
        assertTrue(SupportClientScale.SPAWN>1.5f,"drawn bigger than a 1 x 2 channel gate");
    }
    /** The renderer's scale, read without loading the client class. */
    static final class SupportClientScale{static final float SPAWN;static{try{var src=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/createarsenal/beacon/SupportClient.java"));
        var m=java.util.regex.Pattern.compile("SPAWN_SCALE=([0-9.]+)f").matcher(src);SPAWN=m.find()?Float.parseFloat(m.group(1)):0;}catch(java.io.IOException e){throw new RuntimeException(e);}}}
    @Test void theCryoShellReachesThreeBlocksFurtherEveryWayButNotHigher(){
        assertEquals(4.8+3,CannonUpgrades.radius(CannonUpgrades.FireType.CRYO,0),1e-9);
        assertEquals(4.8,CannonUpgrades.radius(CannonUpgrades.FireType.NAPALM,0),1e-9,"napalm is unchanged");
        var f=CannonUpgrades.facts(CannonUpgrades.Config.of(new int[CannonUpgrades.Upgrade.values().length],CannonUpgrades.FireType.CRYO));
        assertEquals(15.6,f.width(),1e-9);assertEquals(CannonUpgrades.GROUND_HEIGHT,f.height(),1e-9,"still 2 blocks high");
    }
    @Test void theMenuCardShowsTheRealNumbers(){
        var none=new int[CannonUpgrades.Upgrade.values().length];
        var e=CannonUpgrades.facts(CannonUpgrades.Config.of(none,CannonUpgrades.FireType.EXPLOSION));
        assertEquals(6,e.rounds());assertEquals(2,e.every(),1e-9);assertEquals(25,e.damage(),1e-9);assertEquals(150,e.total(),1e-9);assertEquals(8,e.width(),1e-9);assertEquals(4.5,e.height(),1e-9);
        var c=CannonUpgrades.facts(CannonUpgrades.Config.of(none,CannonUpgrades.FireType.CLUSTER));
        assertEquals(5,c.special(),1e-9);assertEquals(120,c.total(),1e-9,"the guide's 120");assertEquals(12,c.lasts(),1e-9);assertEquals(14,c.width(),1e-9);
        var n=CannonUpgrades.facts(CannonUpgrades.Config.of(none,CannonUpgrades.FireType.NAPALM));
        assertEquals(5,n.special(),1e-9);assertEquals(50,n.total(),1e-9);assertEquals(10,n.lasts(),1e-9);
        var b=CannonUpgrades.facts(CannonUpgrades.Config.of(none,CannonUpgrades.FireType.BUNKER));
        assertEquals(50,b.special(),1e-9);assertEquals(16,b.width(),1e-9);assertEquals(16,b.height(),1e-9,"a cube");
        var s=CannonUpgrades.facts(CannonUpgrades.Config.of(none,CannonUpgrades.FireType.STARSHELL));
        assertEquals(30,s.lasts(),1e-9);assertEquals(32,s.width(),1e-9);
        assertEquals(5,CannonUpgrades.facts(CannonUpgrades.Config.of(none,CannonUpgrades.FireType.NARUKAMI)).rounds(),"one volley fewer");
        // upgrades show up in the numbers
        var up=none.clone();up[CannonUpgrades.Upgrade.DAMAGE.ordinal()]=2;up[CannonUpgrades.Upgrade.AOE.ordinal()]=1;up[CannonUpgrades.Upgrade.VOLLEY.ordinal()]=1;
        var e2=CannonUpgrades.facts(CannonUpgrades.Config.of(up,CannonUpgrades.FireType.EXPLOSION));
        assertEquals(37.5,e2.damage(),1e-9);assertEquals(11,e2.width(),1e-9);assertEquals(8,e2.rounds());
        assertEquals("25",CannonUpgrades.number(25));assertEquals("37.5",CannonUpgrades.number(37.5));assertEquals("4.8",CannonUpgrades.number(4.8000001));assertEquals("2",CannonUpgrades.number(1.99999));
        assertEquals(8,CannonUpgrades.factArgs(e).length);
    }
    @Test void everyFireSupportHasAnEffectDamageAndAreaLine() throws Exception{
        var lang=lang();
        for(var t:CannonUpgrades.FireType.values())for(String part:List.of("effect","damage","area")){
            String key="gui.arsenal_beacon.cannon.type."+t.id+"."+part;
            assertTrue(lang.containsKey(key),key);
            var m=java.util.regex.Pattern.compile("%(\\d)\\$s").matcher(lang.get(key));
            while(m.find())assertTrue(Integer.parseInt(m.group(1))<=8,key+" uses an argument the card does not pass");
            assertFalse(lang.get(key).matches(".*(?<!%)%(?![0-9%]).*"),key+": a percent sign must be written %%");
            assertFalse(lang.containsKey("gui.arsenal_beacon.cannon.type."+t.id+".desc"),"the old one-line description is gone");
        }
        assertTrue(lang.get("gui.arsenal_beacon.cannon.type.starshell.effect").contains("Haste II"),"the starshell gives Haste");
        assertFalse(lang.get("gui.arsenal_beacon.cannon.type.starshell.effect").contains("Regeneration"));
        assertTrue(lang.get("gui.arsenal_beacon.cannon.type.heal.damage").startsWith("None"),"the Healing Barrage does not hurt mobs");
        for(String key:List.of("raid.gate_spawn","raid.no_ground","raid.test_gate","cannon.card.effect","cannon.card.damage","cannon.card.area"))assertTrue(lang.containsKey("gui.arsenal_beacon."+key),key);
    }
    static Map<String,String> lang() throws Exception{
        try(var in=RaidFixesTest.class.getResourceAsStream("/assets/arsenal_beacon/lang/en_us.json")){
            var json=com.google.gson.JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
            var out=new HashMap<String,String>();for(var e:json.entrySet())out.put(e.getKey(),e.getValue().getAsString());return out;
        }
    }
}
