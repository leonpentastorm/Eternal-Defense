package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.18: power from Create, poles and wire, turrets, walls, the shock box and the Defense Depot. */
final class Release1118Test {
    @Test void poleReachAndWireSpans(){
        assertTrue(PowerKit.inAura(8,0,0)&&PowerKit.inAura(0,-4,0)&&PowerKit.inAura(0,6,0));
        assertFalse(PowerKit.inAura(9,0,0)||PowerKit.inAura(6,0,6)||PowerKit.inAura(0,-5,0)||PowerKit.inAura(0,7,0));
        var a=new net.minecraft.core.BlockPos(0,64,0);
        assertTrue(PowerKit.spanOk(a,a.offset(16,0,0)));
        assertFalse(PowerKit.spanOk(a,a.offset(17,0,0))||PowerKit.spanOk(a,a),"no span longer than 16 blocks, none to itself");
    }
    @Test void networksShareTheLoadAndStopWhenOverstressed(){
        assertEquals(10,PowerKit.share(30,3),1e-6);
        assertEquals(0,PowerKit.share(30,0),1e-6);
        assertTrue(PowerKit.running(1,false));
        assertFalse(PowerKit.running(2,true),"one overstressed dynamo takes the whole network down");
        assertFalse(PowerKit.running(0,false),"no turning dynamo, no power");
    }
    @Test void wireOnlyJoinsWhatItShould(){
        var P=PowerPoles.End.POLE;var B=PowerPoles.End.BOX;var W=PowerPoles.End.WIRE;
        assertEquals(0,PowerPoles.owner(P,P));assertEquals(0,PowerPoles.owner(P,B));assertEquals(1,PowerPoles.owner(B,P));
        assertEquals(0,PowerPoles.owner(B,W));assertEquals(1,PowerPoles.owner(W,B));
        assertEquals(-1,PowerPoles.owner(P,W));assertEquals(-1,PowerPoles.owner(W,P));assertEquals(-1,PowerPoles.owner(W,W));assertEquals(-1,PowerPoles.owner(B,B));
    }
    @Test void wallsResistTheirKindOfBlow(){
        assertEquals(.25f,DefenseWalls.through("sandbag",true));assertEquals(1f,DefenseWalls.through("sandbag",false));
        assertEquals(.4f,DefenseWalls.through("barricade",false));assertEquals(1f,DefenseWalls.through("barricade",true));
        assertEquals(1f,DefenseWalls.through("",true));
    }
    @Test void turretLinesGrowWithTheirMark(){
        for(var line:Turrets.Line.values())for(int mk=1;mk<3;mk++){
            var low=Turrets.stats(line,mk);var high=Turrets.stats(line,mk+1);
            assertTrue(high.range()>low.range()&&Turrets.dps(line,mk+1)>Turrets.dps(line,mk)&&high.stress()>low.stress(),line+" Mk "+mk);
        }
        assertTrue(Turrets.stats(Turrets.Line.MG,1).range()>Turrets.stats(Turrets.Line.SHOTGUN,1).range()&&Turrets.stats(Turrets.Line.SHOTGUN,1).range()>Turrets.stats(Turrets.Line.FLAME,1).range(),"machine gun reaches furthest, flamethrower least");
        assertEquals("mg_turret_mk1",Turrets.id(Turrets.Line.MG,1));assertEquals("flame_turret_mk3",Turrets.id(Turrets.Line.FLAME,3));
    }
    @Test void turretsAimLikeTheirModels(){
        assertEquals(0,Turrets.yaw(0,-1),1e-6,"a model faces north (-z)");
        assertEquals(Math.PI/2,Turrets.yaw(-1,0),1e-6,"a positive turn about +Y swings north toward west");
        assertTrue(Turrets.pitch(1,1)>0&&Turrets.pitch(-1,1)<0);
        assertEquals(20,Turrets.clampPitch(45));assertEquals(-15,Turrets.clampPitch(-60));
    }
    @Test void theDepotSellsTheKitButNotThePowerPlant() throws Exception{
        String economy=Release114Test.source("Economy.java");
        var ids=DefenseDepot.PRODUCTS.stream().map(DefenseDepot.Product::id).toList();
        for(var line:Turrets.Line.values())for(int mk=1;mk<=3;mk++)assertTrue(ids.contains(Turrets.id(line,mk)));
        assertTrue(ids.containsAll(List.of("mg_turret_ammo","shotgun_turret_ammo","flame_turret_fuel","sandbag","barricade","barbed_wire","shock_box","electric_wire")));
        for(var id:List.of("defense_depot","power_pole","shaft_dynamo","cog_dynamo")){
            assertFalse(ids.contains(id),id+" is fabricated, not sold");
            assertTrue(WorkshopFabrication.ITEMS.contains(id),id);assertTrue(economy.contains("\""+id+"\""),id+" has a price");
        }
        assertEquals(24,WorkshopFabrication.ITEMS.indexOf("defense_depot"),"the Defense group starts after the 24 earlier installations");
        assertTrue(DefenseDepot.PRODUCTS.stream().allMatch(p->p.price()>0&&p.count()>0));
    }
    @Test void theFieldGuideQuotesTheRealNumbers() throws Exception{
        String body=Release114Test.text("guide.defense.body"),detail=Release114Test.text("guide.defense.detail");
        int[] mg=range(Turrets.Line.MG),sg=range(Turrets.Line.SHOTGUN),fl=range(Turrets.Line.FLAME);
        assertTrue(body.contains(mg[0]+", "+mg[1]+" and "+mg[2]+" blocks out"));
        assertTrue(body.contains(sg[0]+", "+sg[1]+" and "+sg[2]+" blocks out"));
        assertTrue(body.contains(fl[0]+", "+fl[1]+" and "+fl[2]+" blocks long"));
        var t=Economy.TURRET_PRICE;var f=Economy.FLAME_TURRET_PRICE;
        assertTrue(body.contains(t[0]+", "+t[1]+" and "+t[2]+" Universal Ammo Coins")&&body.contains(f[0]+", "+f[1]+" and "+f[2]+" Universal Ammo Coins"));
        assertTrue(body.contains(Economy.DEPOT_MG_AMMO+", "+Economy.DEPOT_SHELLS+" and "+Economy.DEPOT_FUEL+" Universal Ammo Coins"));
        assertTrue(body.contains("for "+Economy.DEPOT_SHOCK_BOX+" Universal Ammo Coins"));
        assertTrue(body.contains(Turrets.perItem(Turrets.Line.MG)+" shots a can, "+Turrets.perItem(Turrets.Line.SHOTGUN)+" shots a box and "+Turrets.perItem(Turrets.Line.FLAME)+" bursts a bottle"));
        assertTrue(body.contains("within "+PowerKit.AURA+" blocks")&&body.contains(PowerKit.AURA_DOWN+" blocks down and "+PowerKit.AURA_UP+" blocks up"));
        assertTrue(body.contains("at most "+PowerKit.WIRE_MAX+" blocks")&&body.contains(PowerKit.MIN_RPM+" RPM or more"));
        assertTrue(body.contains(PowerKit.SHOCK_CHARGE/20+" more seconds")&&body.contains(PowerKit.SHOCK_STRESS+" stress per RPM"));
        assertTrue(body.contains(Economy.DEPOT_IRON+" iron ingots, "+Economy.DEPOT_COPPER+" copper ingots, "+Economy.DEPOT_CHESTS+" chests and "+Economy.DEPOT_PLATING+" Reinforced"));
        assertTrue(body.contains(Economy.POLE_IRON+" iron ingots and "+Economy.POLE_COPPER+" copper ingots"));
        assertTrue(body.contains(Economy.DYNAMO_COPPER+" copper ingots, "+Economy.DYNAMO_IRON+" iron ingots, "+Economy.DYNAMO_ALLOY+" andesite alloy and "+Economy.DYNAMO_REDSTONE+" redstone"));
        assertTrue(body.contains("cut for "+(int)DefenseWalls.WIRE_CUT+" damage")&&body.contains("shocks for "+(int)DefenseWalls.WIRE_SHOCK+" damage"));
        assertTrue(detail.contains(PowerPoles.fmt(Turrets.dps(Turrets.Line.MG,1))+", "+PowerPoles.fmt(Turrets.dps(Turrets.Line.MG,2))+" and "+PowerPoles.fmt(Turrets.dps(Turrets.Line.MG,3))));
        assertTrue(detail.contains(PowerPoles.fmt(Turrets.dps(Turrets.Line.SHOTGUN,1))+", "+PowerPoles.fmt(Turrets.dps(Turrets.Line.SHOTGUN,2))+" and "+PowerPoles.fmt(Turrets.dps(Turrets.Line.SHOTGUN,3))));
        assertTrue(detail.contains(PowerPoles.fmt(Turrets.dps(Turrets.Line.FLAME,1))+", "+PowerPoles.fmt(Turrets.dps(Turrets.Line.FLAME,2))+" and "+PowerPoles.fmt(Turrets.dps(Turrets.Line.FLAME,3))));
        var s=Turrets.stats(Turrets.Line.MG,1);var s3=Turrets.stats(Turrets.Line.FLAME,3);
        assertTrue(detail.contains("machine gun and shotgun "+s.stress()+", "+Turrets.stats(Turrets.Line.MG,2).stress()+" and "+Turrets.stats(Turrets.Line.MG,3).stress()));
        assertTrue(detail.contains("flame "+Turrets.stats(Turrets.Line.FLAME,1).stress()+", "+Turrets.stats(Turrets.Line.FLAME,2).stress()+" and "+s3.stress()));
        assertTrue(detail.contains("up to "+PowerKit.SHOCK_REACH+" joined")&&detail.contains("A pole holds "+PowerPoles.MAX_LINKS+" spans and a Shock Box "+ShockBoxes.MAX_LINKS));
        assertTrue(detail.contains("A turret holds "+Turrets.SLOTS+" stacks"));
        for(var line:Turrets.Line.values())if(line!=Turrets.Line.FLAME)for(int mk=1;mk<=3;mk++)assertEquals(Turrets.stats(line,mk).stress(),Turrets.stats(Turrets.Line.MG,mk).stress(),"machine gun and shotgun share their stress");
    }
    private static int[] range(Turrets.Line line){return new int[]{Turrets.stats(line,1).range(),Turrets.stats(line,2).range(),Turrets.stats(line,3).range()};}
    @Test void wiring() throws Exception{
        String digging=Release114Test.source("RaidDigging.java"),combat=Release114Test.source("BeaconCombat.java"),targets=Release114Test.source("RaidTargets.java");
        assertTrue(digging.contains("amount*=DefenseWalls.through(state,ranged);"),"walls resist their kind of blow");
        assertTrue(digging.contains("DefenseWalls.cut(l,by,"),"barbed wire bites the raider hacking at it");
        assertTrue(combat.contains("RaidDigging.SHOT,true)"),"a raider's arrow or bullet is a ranged blow");
        assertTrue(targets.contains("source.is(DamageTypeTags.IS_PROJECTILE)||source.getDirectEntity()!=attacker"));
        String main=Release114Test.source("EternalDefense.java");
        assertTrue(main.contains("new PowerGrid.GridEvents()"),"the grid is worked out on the level tick");
        String compat=Files.readString(Path.of("src/create/java/dev/eternaldefense/beacon/CreateArmCompat.java"));
        assertTrue(compat.contains("CreatePower.register(modBus)"),"the dynamos come with Create");
        String power=Files.readString(Path.of("src/create/java/dev/eternaldefense/beacon/CreatePower.java"));
        assertTrue(power.contains("calculateStressApplied(){lastStressApplied=stress(demand)"),"the network's demand is the dynamo's stress impact");
        assertTrue(power.contains("updateStressFor(this,calculateStressApplied())"),"a new demand reaches the Create network at once");
        String client=Release114Test.source("PowerClient.java");
        assertTrue(client.contains("handleInventoryButtonClick(menu.containerId,selected)"),"Buy uses the vanilla menu button packet, so the protocol is unchanged");
    }
    @Test void damageTypesAreTrapKinds() throws Exception{
        String base="src/main/resources/data/";
        for(var id:List.of("turret_bullet","turret_pellet","turret_flame","barbed_wire","electric_shock"))assertTrue(Files.exists(Path.of(base+"eternal_defense/damage_type/"+id+".json")),id);
        assertTrue(Files.readString(Path.of(base+"eternal_defense/tags/damage_type/trap/projectile.json")).contains("eternal_defense:turret_pellet"));
        assertTrue(Files.readString(Path.of(base+"eternal_defense/tags/damage_type/trap/fire.json")).contains("eternal_defense:turret_flame"));
        assertTrue(Files.readString(Path.of(base+"minecraft/tags/damage_type/is_fire.json")).contains("eternal_defense:turret_flame"),"fire-proof mobs shrug off the flamethrower");
        assertTrue(Files.readString(Path.of(base+"eternal_defense/tags/damage_type/trap/spikes.json")).contains("eternal_defense:barbed_wire"));
        assertTrue(Files.readString(Path.of(base+"eternal_defense/tags/damage_type/trap/lightning.json")).contains("eternal_defense:electric_shock"));
        String defenses=Files.readString(Path.of(base+"eternal_defense/tags/blocks/defenses.json"));
        for(var id:List.of("sandbag","barricade","barbed_wire","shock_box","mg_turret_mk3"))assertTrue(defenses.contains("eternal_defense:"+id),id+" is a raid target");
        for(var id:List.of("mg_turret_ammo","shotgun_turret_ammo","flame_turret_fuel"))assertTrue(Files.readString(Path.of(base+"eternal_defense/recipes/defense/"+id+".json")).contains("\"create:"),id+" is made in a Create factory");
    }
}
