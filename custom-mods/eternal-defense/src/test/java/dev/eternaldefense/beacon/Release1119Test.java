package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.19: dynamos say how they run and what stress they carry, the Shaft Dynamo turns every side like its shaft, poles show their reach, turrets show their state and open by hand. */
final class Release1119Test {
    static PowerGrid.Summary net(boolean running,boolean over,float su){return new PowerGrid.Summary(2,2,32,16,running,over,1,1,su);}
    @Test void aDynamoNamesItsState(){
        assertEquals("no_pole",PowerKit.dynamoState(false,32,false,false));
        assertEquals("still",PowerKit.dynamoState(true,PowerKit.MIN_RPM-1,false,false));
        assertEquals("working",PowerKit.dynamoState(true,PowerKit.MIN_RPM,false,false));
        assertEquals("down_own",PowerKit.dynamoState(true,32,true,true),"its own Create network is the overstressed one");
        assertEquals("down_other",PowerKit.dynamoState(true,32,true,false),"another dynamo's network took the power network down");
    }
    @Test void stressUnitsAreTheShareTimesEachDynamosSpeed(){
        assertEquals(512,PowerKit.su(8,64),1e-3,"8 su per RPM on two dynamos at 32 RPM each");
        assertEquals(0,PowerKit.su(0,64),1e-6);
        assertEquals("524,288",PowerPoles.su(524288));assertEquals("256",PowerPoles.su(255.6));
    }
    @Test void theReadoutOnlyResendsWhenSomethingShownChanges(){
        var r=new PowerKit.Readout();
        assertFalse(r.take(null),"a dynamo with no pole starts that way");
        assertTrue(r.take(net(true,false,512)));assertTrue(r.pole&&r.running&&r.turrets==1&&r.boxes==1&&r.su==512);
        assertFalse(r.take(net(true,false,512)),"the same network again sends nothing");
        assertTrue(r.take(net(false,true,512)),"the network going down is sent");
        assertTrue(r.take(null),"losing the pole is sent");assertFalse(r.pole);
    }
    @Test void theRightClickLinesCoverStateOwnNetworkAndCreate(){
        var r=new PowerKit.Readout();r.take(net(true,false,512));
        var lines=r.lines(net.minecraft.network.chat.Component.literal("Shaft Dynamo"),32,8,false,512,524288,true);
        var keys=lines.stream().map(c->((net.minecraft.network.chat.contents.TranslatableContents)c.getContents()).getKey()).toList();
        assertEquals(java.util.List.of("gui.eternal_defense.power.dynamo.working","gui.eternal_defense.power.dynamo.own","gui.eternal_defense.power.dynamo.network","gui.eternal_defense.power.dynamo.create"),keys);
        var own=((net.minecraft.network.chat.contents.TranslatableContents)lines.get(1).getContents()).getArgs();
        assertEquals("256",own[0],"8 su per RPM at 32 RPM");
        r.take(null);
        assertEquals(2,r.lines(net.minecraft.network.chat.Component.literal("Cog Dynamo"),32,0,false,0,524288,true).size(),"no pole: the state and the Create network only");
    }
    @Test void wiring() throws Exception{
        String power=Files.readString(Path.of("src/create/java/dev/eternaldefense/beacon/CreatePower.java"));
        assertFalse(power.contains("GearboxBlock"),"not Create's gearbox: it turns the far side the other way");
        assertTrue(power.contains("class ShaftDynamo extends RotatedPillarKineticBlock")&&power.contains("class ShaftEntity extends KineticBlockEntity"));
        assertTrue(power.contains("return face.getAxis().isHorizontal();"),"shafts on the four sides");
        assertTrue(power.contains("if(!p.getItemInHand(hand).isEmpty())return InteractionResult.PASS;"),"a wrench or an item still works as before");
        assertTrue(power.contains("readout.goggles(tooltip,"),"Engineer's Goggles show the power network too");
        String client=Files.readString(Path.of("src/create/java/dev/eternaldefense/beacon/CreatePowerClient.java"));
        assertTrue(client.contains("KineticBlockEntityRenderer.getAngleForBe(be,pos,axis)"),"each half turns like a Create shaft on its axis");
        assertFalse(client.contains("angle*=-1"),"no gearbox reversal in the drawing");
        String grid=Release114Test.source("PowerGrid.java");
        assertTrue(grid.contains("for(var dy:net.dynamos)dy.report(summary);")&&grid.contains("gone.report(null);"));
        for(var key:new String[]{"power.dynamo.working","power.dynamo.still","power.dynamo.down_own","power.dynamo.down_other","power.dynamo.no_pole","power.dynamo.own","power.dynamo.network","power.dynamo.create",
            "power.goggles.title","power.goggles.working","power.goggles.still","power.goggles.down_own","power.goggles.down_other","power.goggles.no_pole","power.goggles.own","power.goggles.network","power.goggles.parts"})
            assertFalse(Release114Test.text(key).isBlank(),key);
        assertTrue(Release114Test.text("power.running").contains("%3$s su (%4$s su per RPM)"));
    }
    @Test void aTurretsLightIsReadyEmptyOrOff(){
        assertEquals("ready",Turrets.lightOf(true,1));
        assertEquals("empty",Turrets.lightOf(true,0));
        assertEquals("off",Turrets.lightOf(false,500),"no power wins over a full load");
    }
    @Test void theReachOutlineIsTheEdgeOfThePolesReach(){
        int r=PowerKit.AURA;var edges=PowerPoles.REACH_EDGES;
        assertFalse(edges.isEmpty());
        for(var e:edges){
            int x=e[0],z=e[1];boolean alongX=e[2]==1;
            // the two cells either side of the edge: exactly one is in reach
            boolean a=alongX?PowerKit.inAura(x,0,z-1):PowerKit.inAura(x-1,0,z),b=PowerKit.inAura(x,0,z);
            assertTrue(a!=b,"edge "+java.util.Arrays.toString(e)+" is on the boundary");
            assertTrue(Math.abs(x)<=r+1&&Math.abs(z)<=r+1);
        }
        long cells=0;for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(PowerKit.inAura(x,0,z))cells++;
        assertEquals(edges.stream().map(java.util.Arrays::toString).distinct().count(),edges.size(),"no edge twice");
        assertTrue(cells>150,"the reach is a disc 8 blocks around");
    }
    @Test void turretScreenAndLightWiring() throws Exception{
        String turrets=Release114Test.source("Turrets.java"),client=Release114Test.source("PowerClient.java"),poles=Release114Test.source("PowerPoles.java"),mod=Release114Test.source("EternalDefense.java");
        assertTrue(turrets.contains("NetworkHooks.openScreen(sp,")&&turrets.contains("new TurretMenu(id,inv,be.ammo,pos,be,line,mk)"),"the screen works on the turret's own slots, so ammo comes back out by hand");
        assertTrue(turrets.contains("public ItemStack extractItem(int slot,int amount,boolean simulate){return ItemStack.EMPTY;}"),"machines still only put ammo in");
        assertTrue(turrets.contains("return stack.is(Turrets.ammo(line));"),"a slot takes only the turret's own ammo");
        assertTrue(mod.contains("TURRET_MENU=MENUS.register(\"turret\""));
        assertTrue(client.contains("MenuScreens.register(EternalDefense.TURRET_MENU.get(),TurretScreen::new)"));
        assertTrue(client.contains("lamp(pose,buffers,block.mk,be.light(),now);"),"every turret draws its lamp");
        assertTrue(client.contains("be.status(be.loadedShown)"),"the crosshair line uses the shots the client heard");
        assertTrue(turrets.contains("t.putInt(\"Loaded\",loaded());")&&turrets.contains("loadedShown=tag.getInt(\"Loaded\")"));
        assertTrue(poles.contains("be.showReach=!be.showReach;")&&poles.contains("tag.putBoolean(\"ShowReach\",showReach)"),"the reach toggle is kept with the pole");
        assertTrue(client.contains("if((holding||pole.showReach)&&"),"shown while holding gear or when pinned");
        for(var key:new String[]{"power.reach.shown","power.reach.hidden","turret.screen.subtitle","turret.screen.ready","turret.screen.empty","turret.screen.off","turret.screen.loaded",
            "turret.screen.range","turret.screen.blocks","turret.screen.stress","turret.screen.per_rpm","turret.screen.ammo","turret.screen.tip","turret.screen.slot_tip"})
            assertFalse(Release114Test.text(key).isBlank(),key);
        String guide=Release114Test.text("guide.defense.body");
        assertFalse(guide.contains("cannot be taken back out"),"ammo comes back out in the screen now");
        assertTrue(guide.contains("green when it is ready, orange when it is out of ammo, red when no power reaches it"));
        assertTrue(guide.contains("Sneak and right-click a pole with both hands empty"));
    }
    @Test void theFlameIsABoxAlongTheAimAndStrongerTheMachineGunWeaker(){
        var from=new net.minecraft.world.phys.Vec3(0,1,0);var north=new net.minecraft.world.phys.Vec3(0,0,-1);
        java.util.function.BiFunction<Double,Double,net.minecraft.world.phys.AABB> mob=(x,z)->new net.minecraft.world.phys.AABB(x-.3,0,z-.3,x+.3,1.9,z+.3);
        assertTrue(Turrets.inJet(mob.apply(0d,-5d),from,north,8,1),"in front, inside the length");
        assertTrue(Turrets.inJet(mob.apply(1.2d,-5d),from,north,8,1),"beside the axis but within half the width");
        assertFalse(Turrets.inJet(mob.apply(2.5d,-5d),from,north,8,1),"too far to the side");
        assertFalse(Turrets.inJet(mob.apply(0d,-10d),from,north,8,1),"past the jet's end (a wall cuts the length)");
        assertFalse(Turrets.inJet(mob.apply(0d,4d),from,north,8,1),"behind the nozzle");
        var steep=Turrets.mount(new net.minecraft.world.phys.Vec3(0,5,-1).normalize());
        assertEquals(20,Math.toDegrees(Math.asin(steep.y)),1e-3,"the jet lifts no more than the mount");assertEquals(1,steep.length(),1e-6);
        float[] was={3,3.5f,4};
        for(int mk=1;mk<=3;mk++){
            assertEquals(was[mk-1]*.8f,Turrets.stats(Turrets.Line.MG,mk).damage(),1e-4,"machine gun damage 20% down");
            assertTrue(Turrets.dps(Turrets.Line.FLAME,mk)>=2*new float[]{6,8,10}[mk-1]-1e-3,"flame damage doubled");
            assertTrue(Turrets.stats(Turrets.Line.FLAME,mk).range()>new int[]{7,8,10}[mk-1],"and it reaches further");
        }
        assertTrue(Turrets.stats(Turrets.Line.SHOTGUN,1).range()>Turrets.stats(Turrets.Line.FLAME,1).range(),"still the shortest reach");
    }
    @Test void aBadassHasThreeTimesTheHealthBonus() throws Exception{
        for(int l=1;l<=5;l++)assertEquals(3*l,Elites.healthBonus(Elites.Kind.BADASS,l),1e-9,"level "+l+": +"+300*l+" percent health");
        assertEquals(.5,Elites.healthBonus(Elites.Kind.FLAK_ARMOR,1),1e-9,"other elites unchanged");
        assertTrue(Release114Test.text("guide.raids.body").contains("four times as much at I, sixteen times at V"));
    }
    @Test void theShotgunPushesAQuarterPunchAtMarkThree() throws Exception{
        assertEquals(Turrets.PUNCH/4,Turrets.knockback(Turrets.Line.SHOTGUN,3),1e-6,"a quarter of a player's punch at Mark III");
        assertEquals(.05f,Turrets.knockback(Turrets.Line.SHOTGUN,1),1e-6);assertEquals(.075f,Turrets.knockback(Turrets.Line.SHOTGUN,2),1e-6);
        assertEquals(0,Turrets.knockback(Turrets.Line.MG,3));assertEquals(0,Turrets.knockback(Turrets.Line.FLAME,3));
        assertTrue(Release114Test.source("Turrets.java").contains("if(m.hurt(source,e.getValue())&&push>0)m.knockback(push,-dir.x,-dir.z);"),"once a shot, only on a hit, away from the turret");
        assertTrue(Release114Test.text("guide.defense.body").contains("pushes every mob it hits back a little"));
    }
    @Test void aShockBoxMakesAtMostFifteenWireBlocksLiveNearestFirst(){
        assertEquals(15,PowerKit.SHOCK_REACH,"like a redstone signal");
        var line=new java.util.HashSet<net.minecraft.core.BlockPos>();for(int x=0;x<20;x++)line.add(new net.minecraft.core.BlockPos(x,0,0));
        var live=PowerGrid.spread(java.util.List.of(new net.minecraft.core.BlockPos(0,0,0)),line::contains,PowerKit.SHOCK_REACH);
        assertEquals(15,live.size());assertTrue(live.contains(new net.minecraft.core.BlockPos(14,0,0))&&!live.contains(new net.minecraft.core.BlockPos(15,0,0)),"the first 15 along the line");
        var both=PowerGrid.spread(java.util.List.of(new net.minecraft.core.BlockPos(0,0,0),new net.minecraft.core.BlockPos(19,0,0)),line::contains,PowerKit.SHOCK_REACH);
        assertEquals(15,both.size(),"two spans from one box still share the 15");assertTrue(both.contains(new net.minecraft.core.BlockPos(19,0,0))&&both.contains(new net.minecraft.core.BlockPos(7,0,0)));
        assertEquals(3,PowerGrid.spread(java.util.List.of(new net.minecraft.core.BlockPos(0,0,0)),p->p.getX()<3&&line.contains(p),15).size(),"a short run is all live");
    }
    @Test void soundsArtAndTheOtherModsWalls() throws Exception{
        String base="src/main/resources/assets/eternal_defense/";
        var sounds=com.google.gson.JsonParser.parseString(Files.readString(Path.of(base+"sounds.json"))).getAsJsonObject();
        var files=java.util.Map.of("turret.mg_shot","turret_lmg","turret.shotgun_shot","turret_shotgun","turret.flame_start","flame_start","turret.flame","flame_loop","turret.flame_end","flame_end");
        for(var e:files.entrySet()){
            var first=sounds.getAsJsonObject(e.getKey()).getAsJsonArray("sounds").get(0);
            assertEquals("eternal_defense:sfx/"+e.getValue(),first.isJsonObject()?first.getAsJsonObject().get("name").getAsString():first.getAsString(),e.getKey());
            assertTrue(Files.exists(Path.of(base+"sounds/sfx/"+e.getValue()+".ogg")),e.getValue());
            assertFalse(Release114Test.lang().get(sounds.getAsJsonObject(e.getKey()).get("subtitle").getAsString()).getAsString().isBlank());
        }
        String turrets=Release114Test.source("Turrets.java"),fx=Release114Test.source("TurretEffectsClient.java");
        assertTrue(turrets.contains("DefenseSounds.MG_SHOT.get()")&&turrets.contains("DefenseSounds.SHOTGUN_SHOT.get()"));
        assertFalse(turrets.contains("ParticleTypes.FLAME"),"the flame is drawn by each client, not sent as particles");
        assertTrue(turrets.contains("if(block().line!=Line.FLAME)l.blockEvent("),"shots (and their casings) for the guns only");
        assertTrue(turrets.contains("var be=l.getBlockEntity(pos);return be!=null&&be.triggerEvent(id,param);"),"the block passes the shot event to its entity, so it reaches the clients");
        assertTrue(fx.contains("DefenseSounds.FLAME_START")&&fx.contains("new Roar(be)")&&fx.contains("DefenseSounds.FLAME_END")&&fx.contains("looping=true"),"lights once, loops while it burns, goes out once");
        assertTrue(fx.contains("now-burn.litAt>=START_TICKS"),"the loop starts when the start sound ends");
        assertTrue(fx.contains("\"item/cartridge_case\"")&&Files.exists(Path.of(base+"textures/item/cartridge_case.png")),"casings use the plain case picture (the item model draws from a shared sheet)");
        for(var n:new String[]{"barbed_wire_post_live","barbed_wire_side_live"}){
            var img=javax.imageio.ImageIO.read(Path.of(base+"textures/block/"+n+".png").toFile());
            assertEquals(8*img.getWidth(),img.getHeight(),n+": eight frames");
            assertTrue(Files.readString(Path.of(base+"textures/block/"+n+".png.mcmeta")).contains("\"animation\""),n);
        }
        assertTrue(Release114Test.source("DefenseWalls.java").contains("ParticleTypes.ELECTRIC_SPARK"),"live wire sparks");
        String tag=Files.readString(Path.of("src/main/resources/data/diagonalwalls/tags/blocks/non_diagonal_walls.json"));
        assertTrue(tag.contains("\"eternal_defense:sandbag\"")&&tag.contains("\"eternal_defense:barricade\"")&&tag.contains("\"required\": false"),"Diagonal Walls leaves the sandbags and barricades alone");
        assertTrue(Release114Test.source("PlacementPreviewClient.java").contains("ModelData.EMPTY,null);"),"the ghost draws every part of a multipart model");
    }
}
