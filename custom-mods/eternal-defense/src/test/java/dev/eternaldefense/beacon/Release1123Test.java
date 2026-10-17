package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.23: the tesla coil, the Heavy Cannon, the Curse Gas Totem, the punji trap and the spotlight; flame turrets cost what the others do; Create recipes for the walls. */
final class Release1123Test {
    static final String BASE="src/main/resources/";
    @Test void theNewTowersAreTurretLines(){
        assertEquals(3,Turrets.Line.TESLA.marks);assertEquals(1,Turrets.Line.CANNON.marks);assertEquals(1,Turrets.Line.CURSE.marks);
        assertEquals("tesla_turret_mk3",Turrets.id(Turrets.Line.TESLA,3));assertEquals("heavy_cannon",Turrets.id(Turrets.Line.CANNON,1));assertEquals("curse_totem",Turrets.id(Turrets.Line.CURSE,1));
        assertFalse(Turrets.Line.TESLA.fed()||Turrets.Line.CURSE.fed(),"the coil and the totem need no ammo");
        assertTrue(Turrets.Line.CANNON.fed()&&Turrets.perItem(Turrets.Line.CANNON)==4,"the cannon loads shells, 4 a crate");
        assertEquals(0,Turrets.perItem(Turrets.Line.TESLA));
        int longest=0;for(var line:Turrets.Line.values())if(line!=Turrets.Line.CANNON)for(int mk=1;mk<=line.marks;mk++)longest=Math.max(longest,Turrets.stats(line,mk).range());
        assertTrue(Turrets.stats(Turrets.Line.CANNON,1).range()>longest,"the Heavy Cannon reaches furthest of all");
        for(var line:Turrets.Line.values())if(line!=Turrets.Line.CANNON)for(int mk=1;mk<=line.marks;mk++)assertTrue(Turrets.stats(Turrets.Line.CANNON,1).cooldown()>Turrets.stats(line,mk).cooldown(),"and it fires slowest: "+line);
        for(var line:Turrets.Line.values())if(line!=Turrets.Line.CANNON)for(int mk=1;mk<=line.marks;mk++)assertTrue(Turrets.stats(Turrets.Line.CANNON,1).damage()>3*Turrets.stats(line,mk).damage(),"with by far the biggest hit: "+line);
        assertTrue(Turrets.wide(Turrets.Line.CANNON,1)&&Turrets.wide(Turrets.Line.TESLA,3)&&!Turrets.wide(Turrets.Line.CURSE,1));
        assertEquals(new Vec3(1,2,1),Turrets.middle(Turrets.Line.TESLA,3),"the Mk III strike ball (16, 32, 16) px");
        assertEquals(30.5/16,Turrets.middle(Turrets.Line.TESLA,1).y,1e-9);
    }
    @Test void flameTurretsCostWhatTheOthersDoAndTheCannonWhatFlameCost(){
        var prices=new HashMap<String,Integer>();for(var p:DefenseDepot.PRODUCTS)prices.put(p.id(),p.price());
        for(int mk=1;mk<=3;mk++){
            assertEquals(Economy.TURRET_PRICE[mk-1],prices.get(Turrets.id(Turrets.Line.FLAME,mk)));
            assertEquals(prices.get(Turrets.id(Turrets.Line.MG,mk)),prices.get(Turrets.id(Turrets.Line.TESLA,mk)));
        }
        assertArrayEquals(new int[]{96,192,384},Economy.TURRET_PRICE);
        assertEquals(512,Economy.HEAVY_CANNON_PRICE,"what a Twin Flame Turret cost before");assertEquals(512,prices.get("heavy_cannon"));
        assertEquals(Economy.CURSE_TOTEM_PRICE,prices.get("curse_totem"));
        for(var id:List.of("heavy_cannon_shell","punji_trap","spotlight"))assertTrue(prices.containsKey(id),id);
        assertEquals(prices.size(),DefenseDepot.PRODUCTS.size(),"no product twice");
    }
    @Test void aTeslaZapJumpsAndWeakens(){
        assertEquals(6,Turrets.chainDamage(6,0),1e-6);assertEquals(6*.85f,Turrets.chainDamage(6,1),1e-5);assertEquals(6*.85f*.85f,Turrets.chainDamage(6,2),1e-5);
        for(int mk=1;mk<=3;mk++){var s=Turrets.stats(Turrets.Line.TESLA,mk);assertEquals(1+2*mk,s.pellets(),"3, 5 and 7 mobs a zap");}
        assertEquals("4",PowerPoles.fmt(Turrets.dps(Turrets.Line.TESLA,1)));assertEquals("7.3",PowerPoles.fmt(Turrets.dps(Turrets.Line.TESLA,3)));
    }
    @Test void aShellLandsWhereItIsAimed(){
        var root=new BlockPos(0,64,0);
        for(var aim:List.of(new Vec3(20.5,64.8,-6),new Vec3(-30,70,12),new Vec3(5,60,-35),new Vec3(38,64,3))){
            for(int k=0;k<3;k++){
                boolean high=k>0;double v=k==0?CannonShell.FLAT_SPEED:k==1?CannonShell.LOB_SPEED:CannonShell.FAR_LOB_SPEED;
                var shot=CannonShell.Arc.solve(root,aim,Vec3.ZERO,v,high);
                if(shot==null){assertTrue(high,"a flat shot reaches every target in range: "+aim);continue;}
                assertTrue(shot.at(shot.flight()).distanceTo(aim)<.02,"lands on "+aim+" (speed "+v+"), was "+shot.at(shot.flight()));
                assertTrue(Math.toDegrees(shot.pitch())>=CannonShell.MIN_PITCH&&Math.toDegrees(shot.pitch())<=90);
                assertEquals(v,new Vec3(shot.velocity().x,shot.velocity().y+CannonShell.G/2,shot.velocity().z).length(),1e-6,"its speed is the gun's speed");
            }
            var flat=CannonShell.Arc.solve(root,aim,Vec3.ZERO,CannonShell.FLAT_SPEED,false);var lob=CannonShell.Arc.solve(root,aim,Vec3.ZERO,CannonShell.LOB_SPEED,true);
            if(lob!=null)assertTrue(lob.pitch()>flat.pitch()+Math.toRadians(30)&&lob.flight()>flat.flight(),"a lob goes high and takes longer");
        }
        assertNull(CannonShell.Arc.solve(root,new Vec3(200,64,0),Vec3.ZERO,CannonShell.LOB_SPEED,true),"out of reach");
        var drift=new Vec3(.1,0,0);var led=CannonShell.Arc.solve(root,new Vec3(20,64,0),drift,CannonShell.FLAT_SPEED,false);
        assertEquals(20+.1*led.flight(),led.at(led.flight()).x,.05,"it leads a walking mob");
        var rest=CannonShell.Arc.muzzle(root,0,0);assertEquals(new Vec3(1,64+20/16.0,-15.75/16),rest,"the art's muzzle (16, 20, -15.75) px at rest");
        assertEquals(64+53.75/16,CannonShell.Arc.muzzle(root,0,Math.PI/2).y,1e-9,"53.75 px up when it points straight up");
        assertEquals(1-31.75/16,CannonShell.Arc.muzzle(root,(float)(Math.PI/2),0).x,1e-6,"turned west, the muzzle is west of the mount");
    }
    @Test void aBlastHurtsLessTowardItsEdge(){
        assertEquals(30,CannonShell.Arc.blastDamage(30,0,4),1e-6);assertEquals(12,CannonShell.Arc.blastDamage(30,4,4),1e-5);assertEquals(0,CannonShell.Arc.blastDamage(30,4.01,4));
        assertEquals(4,Turrets.stats(Turrets.Line.CANNON,1).spread(),"a 4-block blast");
    }
    @Test void theCurseSlowsLessAsRaidersLearn(){
        assertEquals(40,Turrets.cursedTicks(40,0));assertEquals(20,Turrets.cursedTicks(40,.5));assertEquals(0,Turrets.cursedTicks(40,1),"immune: no slowness");
        assertTrue(Turrets.inCloud(new Vec3(2.9,64,0),new Vec3(0,64,0),3));assertFalse(Turrets.inCloud(new Vec3(3.1,64,0),new Vec3(0,64,0),3));
        assertFalse(Turrets.inCloud(new Vec3(0,67,0),new Vec3(0,64,0),3),"not a mob high above it");
        assertEquals(1,Turrets.CURSE_SLOW_LEVEL,"Slowness II");assertEquals(2,Turrets.dps(Turrets.Line.CURSE,1),1e-6,"the gas a second");
    }
    @Test void punjiStakesShootUpFastAndSinkSlowly(){
        assertEquals(-PunjiTrap.TRAVEL,PunjiTrap.stakes(true,0),1e-6);assertEquals(0,PunjiTrap.stakes(true,PunjiTrap.RISE_TICKS),1e-6);
        assertTrue(Float.isNaN(PunjiTrap.stakes(true,PunjiTrap.RISE_TICKS+2)),"then the sprung model shows them");
        assertEquals(0,PunjiTrap.stakes(false,0),1e-6);assertEquals(-PunjiTrap.TRAVEL/2,PunjiTrap.stakes(false,PunjiTrap.SINK_TICKS/2f),1e-6);
        assertTrue(Float.isNaN(PunjiTrap.stakes(false,PunjiTrap.SINK_TICKS)));
        assertEquals(10.5f,PunjiTrap.TRAVEL,"the art's travel");
    }
    @Test void everyNewDamageIsATrapRaidersLearn() throws Exception{
        var kinds=java.util.Map.of("turret_tesla","lightning","turret_shell","explosion","turret_curse","magic","punji_spike","spikes");
        for(var e:kinds.entrySet()){
            assertTrue(Files.exists(Path.of(BASE+"data/eternal_defense/damage_type/"+e.getKey()+".json")),e.getKey());
            assertTrue(Files.readString(Path.of(BASE+"data/eternal_defense/tags/damage_type/trap/"+e.getValue()+".json")).contains("eternal_defense:"+e.getKey()),e.getKey()+" is a "+e.getValue()+" trap");
            assertFalse(Release114Test.lang().get("death.attack.eternal_defense."+e.getKey()).getAsString().isBlank());
        }
        assertTrue(Files.readString(Path.of(BASE+"data/eternal_defense/tags/damage_type/trap/magic.json")).contains("minecraft:magic"),"the totem's poison counts as magic too");
        String turrets=Release114Test.source("Turrets.java"),shell=Release114Test.source("CannonShell.java"),punji=Release114Test.source("PunjiTrap.java");
        assertTrue(turrets.contains("RaidAdaptation.resistance(m,RaidAdaptation.Kind.MAGIC)"),"the slowness shrinks with the raid's magic resistance");
        assertTrue(turrets.contains("new DamageSource(type,null,null,from)")&&shell.contains("new DamageSource(type,null,null,c)")&&punji.contains("new DamageSource(type,null,null,Vec3.atCenterOf(worldPosition))"),"no owner: the damage counts as a trap");
        assertTrue(turrets.contains("m.addEffect(new MobEffectInstance(MobEffects.POISON,CURSE_POISON_TICKS,0));"),"no source entity: the poison is a trap too");
        String defenses=Files.readString(Path.of(BASE+"data/eternal_defense/tags/blocks/defenses.json"));
        for(var id:List.of("tesla_turret_mk1","tesla_turret_mk3","heavy_cannon","curse_totem","spotlight"))assertTrue(defenses.contains("eternal_defense:"+id),id+" is a raid target");
        assertFalse(defenses.contains("punji_trap"),"raiders walk onto the trap, they do not hack at it");
    }
    @Test void theWallsAndShellsAreMadeInACreateFactory() throws Exception{
        for(var id:List.of("sandbag","barricade","barbed_wire","heavy_cannon_shell")){
            var recipe=com.google.gson.JsonParser.parseString(Files.readString(Path.of(BASE+"data/eternal_defense/recipes/defense/"+id+".json"))).getAsJsonObject();
            assertEquals("create:compacting",recipe.get("type").getAsString(),id);
            assertTrue(recipe.getAsJsonArray("ingredients").size()<=9,id+" fits a basin");
            assertEquals("eternal_defense:"+id,recipe.getAsJsonArray("results").get(0).getAsJsonObject().get("item").getAsString());
        }
        String body=Release114Test.text("guide.defense.body");
        assertTrue(body.contains("press 4 sand and 1 wool in a basin")&&body.contains("press 2 logs and 1 iron sheet in a basin")&&body.contains("press 1 iron ingot and 2 iron nuggets in a basin"));
        assertTrue(body.contains("press 2 cartridge cases, 4 propellant, 2 iron ingots and 1 gunpowder in a basin"));
    }
    @Test void theFieldGuideQuotesTheRealNumbers() throws Exception{
        String body=Release114Test.text("guide.defense.body"),detail=Release114Test.text("guide.defense.detail");
        var t=Economy.TURRET_PRICE;String three=t[0]+", "+t[1]+" and "+t[2]+" Universal Ammo Coins";
        assertEquals(4,body.split(java.util.regex.Pattern.quote(three),-1).length-1,"machine gun, shotgun, flame and tesla cost the same");
        assertFalse(body.contains("128, 256 and 512"),"the old flame prices are gone");
        var cannon=Turrets.stats(Turrets.Line.CANNON,1);var curse=Turrets.stats(Turrets.Line.CURSE,1);
        assertTrue(body.contains("The longest reach of any tower: "+cannon.range()+" blocks")&&body.contains("within "+(int)cannon.spread()+" blocks"));
        assertTrue(body.contains("for "+Economy.HEAVY_CANNON_PRICE+" Universal Ammo Coins")&&body.contains("closer than "+Turrets.CANNON_MIN+" blocks"));
        int[] mobs=new int[3],range=new int[3];for(int mk=1;mk<=3;mk++){mobs[mk-1]=Turrets.stats(Turrets.Line.TESLA,mk).pellets();range[mk-1]=Turrets.stats(Turrets.Line.TESLA,mk).range();}
        assertTrue(body.contains(mobs[0]+", "+mobs[1]+" and "+mobs[2]+" mobs a zap, "+range[0]+", "+range[1]+" and "+range[2]+" blocks out"));
        assertTrue(body.contains(Math.round((1-Turrets.CHAIN_FALLOFF)*100)+"%% softer"));
        assertTrue(body.contains("Every "+curse.cooldown()/20+" seconds it drops a gas cloud")&&body.contains("up to "+curse.range()+" blocks away"));
        assertTrue(body.contains("hurts them for "+(int)curse.damage()+" damage a second. A cloud is "+(int)(2*curse.spread())+" blocks across and lasts "+curse.burn()+" seconds."));
        assertTrue(body.contains("At most "+Turrets.CLOUDS+" clouds")&&body.contains("for "+Economy.CURSE_TOTEM_PRICE+" Universal Ammo Coins. It has one Mark."));
        assertTrue(body.contains("for "+(int)PunjiTrap.DAMAGE+" damage")&&body.contains("Buy 4 for "+Economy.DEPOT_PUNJI+" Universal Ammo Coins"));
        assertTrue(body.contains("Buy it at the Defense Depot for "+Economy.DEPOT_SPOTLIGHT+" Universal Ammo Coins")&&body.contains("It puts "+Spotlight.STRESS+" stress per RPM"));
        assertTrue(detail.contains("tesla "+Turrets.stats(Turrets.Line.TESLA,1).stress()+", "+Turrets.stats(Turrets.Line.TESLA,2).stress()+" and "+Turrets.stats(Turrets.Line.TESLA,3).stress()+", Heavy Cannon "+cannon.stress()+", Curse Gas Totem "+curse.stress()+", Industrial Spotlight "+Spotlight.STRESS));
        assertTrue(detail.contains("Tesla "+PowerPoles.fmt(Turrets.dps(Turrets.Line.TESLA,1))+", "+PowerPoles.fmt(Turrets.dps(Turrets.Line.TESLA,2))+" and "+PowerPoles.fmt(Turrets.dps(Turrets.Line.TESLA,3))));
        assertTrue(detail.contains("Heavy Cannon "+PowerPoles.fmt(Turrets.dps(Turrets.Line.CANNON,1))+" (a shell every "+cannon.cooldown()/20+" seconds)"));
        assertTrue(detail.contains((int)cannon.damage()+" damage at the middle of the blast, falling to "+Math.round(CannonShell.Arc.blastDamage(cannon.damage(),cannon.spread(),cannon.spread()))+" at its edge"));
        assertTrue(detail.contains("at "+Math.round(CannonShell.FLAT_SPEED*20)+" blocks a second, a lob at "+Math.round(CannonShell.LOB_SPEED*20)+" or "+Math.round(CannonShell.FAR_LOB_SPEED*20)));
        assertTrue(detail.contains("Slowness II for "+Turrets.CURSE_SLOW_TICKS/20+" seconds, Poison I for "+Turrets.CURSE_POISON_TICKS/20+" seconds"));
    }
    @Test void theOwnersArtIsWiredUp() throws Exception{
        String assets=BASE+"assets/eternal_defense/";
        for(var id:List.of("tesla_turret_mk1","tesla_turret_mk2","tesla_turret_mk3","heavy_cannon","curse_totem","punji_trap","spotlight")){
            assertTrue(Files.exists(Path.of(assets+"blockstates/"+id+".json"))&&Files.exists(Path.of(assets+"models/item/"+id+".json")),id);
            assertFalse(Release114Test.lang().get("block.eternal_defense."+id).getAsString().isBlank(),id);
        }
        for(var part:List.of("heavy_cannon_head","heavy_cannon_gun","heavy_cannon_barrel","punji_trap_spikes","tesla_turret_mk1_live","curse_totem_active","spotlight_on"))assertTrue(Files.exists(Path.of(assets+"models/block/"+part+".json")),part);
        assertTrue(Files.readString(Path.of(assets+"blockstates/tesla_turret_mk2.json")).contains("\"powered\": \"true\""),"the live arcs show while powered");
        assertTrue(Files.readString(Path.of(assets+"blockstates/curse_totem.json")).contains("\"active\": \"true\""));
        String client=Release114Test.source("TowerClient.java");
        assertTrue(client.contains("YAW_X=16,YAW_Z=16,PITCH_Y=20,PITCH_Z=18")&&client.contains("KICK=3/16f"),"the art's pivots and the barrel's 3 px kick");
        assertTrue(Release114Test.source("Turrets.java").contains("worldPosition.getY()+4"),"the barrel stays in view pointing straight up (3.4 blocks)");
        var sounds=com.google.gson.JsonParser.parseString(Files.readString(Path.of(assets+"sounds.json"))).getAsJsonObject();
        for(var event:List.of("turret.tesla_zap","turret.heavy_cannon","turret.curse_cloud","trap.punji_spring")){
            var first=sounds.getAsJsonObject(event).getAsJsonArray("sounds").get(0).getAsJsonObject();
            assertTrue(first.get("attenuation_distance").getAsInt()>=DefenseSounds.TURRET_RANGE,event+" carries like the other turrets");
            var file=first.get("name").getAsString().substring("eternal_defense:".length());
            assertTrue(Files.exists(Path.of(assets+"sounds/"+file+".ogg")),file);
            assertFalse(Release114Test.lang().get(sounds.getAsJsonObject(event).get("subtitle").getAsString()).getAsString().isBlank());
        }
    }
}
