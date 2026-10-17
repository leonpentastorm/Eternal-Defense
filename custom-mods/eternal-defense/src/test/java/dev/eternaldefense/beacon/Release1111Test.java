package dev.eternaldefense.beacon;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.11: raiders go for defenses and doors first, dig with cracks, bridge water and chasms, hit from the ring around the beacon, and join a parachute squadron instead of leaving. */
final class Release1111Test {
    static String source(String name) throws Exception{return Release114Test.source(name);}
    static String text(String key) throws Exception{return Release114Test.text(key);}
    static AABB mob(double x,double y,double z){return new AABB(x-.3,y,z-.3,x+.3,y+1.95,z+.3);}

    @Test void defensesAreATagForBlocksAndEntities() throws Exception{
        for(String kind:new String[]{"blocks","entity_types"}){
            var tag=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/eternal_defense/tags/"+kind+"/defenses.json"))).getAsJsonObject();
            assertFalse(tag.get("replace").getAsBoolean(),"other packs add to it");assertTrue(tag.has("values"));
        }
        String targets=source("RaidTargets.java");
        assertTrue(targets.contains("TagKey<Block> BLOCKS=TagKey.create(Registries.BLOCK,new ResourceLocation(EternalDefense.ID,\"defenses\"))"));
        assertTrue(targets.contains("TagKey<EntityType<?>> ENTITIES=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation(EternalDefense.ID,\"defenses\"))"));
        assertTrue(targets.contains("s.is(BlockTags.DOORS)")&&targets.contains("s.is(BlockTags.FENCE_GATES)&&!open(s)"),"closed doors and gates; trapdoors and piston doors are walls");
        assertTrue(targets.contains("\"tacz_turrets\".equals("),"turrets count as defenses");
    }
    @Test void aDefenseIsInTheWayWhenCloseAheadAndLevel(){
        assertTrue(RaidTargets.inTheWay(10,20,25,0));
        assertTrue(RaidTargets.inTheWay(16,28,25,6),"up to the slack farther out, and 6 up");
        assertFalse(RaidTargets.inTheWay(17,20,25,0),"out of sight");
        assertFalse(RaidTargets.inTheWay(10,30,25,0),"behind it: it does not turn back");
        assertFalse(RaidTargets.inTheWay(10,20,25,7),"too high up");
        assertFalse(RaidTargets.inTheWay(9,4,5,0),"past the beacon: a raider beside the beacon hits the beacon, not a bale on its far side");
        assertFalse(RaidTargets.givesUp(100,500));assertTrue(RaidTargets.givesUp(100,501),"20 seconds without a blow");
    }
    @Test void meleeRushesTheBeaconAndRangedFocusesPlayers() throws Exception{
        assertEquals(RaidTargets.Pick.PLAYER,RaidTargets.order(false,true,true),"a fighter hits a player who came close");
        assertEquals(RaidTargets.Pick.DEFENSE,RaidTargets.order(false,false,true),"then defenses in its way");
        assertEquals(RaidTargets.Pick.BEACON,RaidTargets.order(false,false,false),"then the beacon");
        assertEquals(RaidTargets.Pick.DEFENSE,RaidTargets.order(true,true,true),"an archer shoots the defense in its way first");
        assertEquals(RaidTargets.Pick.PLAYER,RaidTargets.order(true,true,false),"then the player");
        assertEquals(RaidTargets.Pick.BEACON,RaidTargets.order(true,false,false),"the beacon after");
        assertTrue(RaidTargets.meleeEngages(true,4,0,false));assertFalse(RaidTargets.meleeEngages(true,4.5,0,false),"only up close");
        assertTrue(RaidTargets.meleeEngages(true,10,0,true));assertFalse(RaidTargets.meleeEngages(true,10.5,0,true),"lets go past the leash");
        assertFalse(RaidTargets.meleeEngages(true,2,4,false),"a player out of reach above");
        assertFalse(RaidTargets.meleeEngages(false,1,0,false),"Creative and Spectator players are ignored");
        assertTrue(RaidTargets.rangedFocus(true,32,0,false));assertFalse(RaidTargets.rangedFocus(true,33,0,false));
        assertTrue(RaidTargets.rangedFocus(true,40,200,true));assertFalse(RaidTargets.rangedFocus(true,40,201,true),"10 seconds without seeing them");
        assertFalse(RaidTargets.rangedFocus(false,5,0,false));
        assertTrue(source("RaidTargets.java").contains("static boolean attackable(Player p){return p!=null&&p.isAlive()&&!p.isCreative()&&!p.isSpectator();}"));
    }
    @Test void blocksCrackAndBreakByHardness(){
        assertEquals(6,RaidDigging.hp(.5f),1e-6,"dirt");assertEquals(15,RaidDigging.hp(2f),1e-6,"planks and cobblestone");
        assertEquals(300,RaidDigging.hp(50f),1e-6,"obsidian, the most any block takes");assertEquals(-1,RaidDigging.hp(-1f),1e-6,"bedrock never breaks");
        assertEquals(1.5,RaidDigging.perSwing(3,1),1e-6,"half the attack damage a swing");assertEquals(.5,RaidDigging.perSwing(0,1),1e-6);
        assertEquals(7.5,RaidDigging.perSwing(3,5),1e-6,"a giant digs as hard as it is big");
        assertEquals(0,RaidDigging.stage(.1f,15));assertEquals(5,RaidDigging.stage(7.5f,15));assertEquals(9,RaidDigging.stage(14.9f,15));
        assertTrue(RaidDigging.crackId(123L)<0,"no entity has that id");assertNotEquals(RaidDigging.crackId(123L),RaidDigging.crackId(124L));
        assertEquals(10,RaidDigging.SWING_TICKS,"two swings a second");assertEquals(3f,RaidDigging.SHOT);assertEquals(160,RaidDigging.FORGET_TICKS);
    }
    @Test void meleeHitsFromTheRingAroundTheBeacon(){
        var beacon=new AABB(0,0,0,1,1,1);
        assertTrue(RaidDigging.inRing(mob(1.5,0,.5),beacon),"beside it");assertTrue(RaidDigging.inRing(mob(1.5,0,1.5),beacon),"at a corner of the 3x3");
        assertTrue(RaidDigging.inRing(mob(.5,1,.5),beacon),"on top");assertTrue(RaidDigging.inRing(mob(-.5,-1,.5),beacon),"a step lower");
        assertFalse(RaidDigging.inRing(mob(2.5,0,.5),beacon),"two blocks out");assertFalse(RaidDigging.inRing(mob(1.5,-2,.5),beacon),"under the floor");
        var mk4=new AABB(-1,0,-1,2,2,2);
        assertTrue(RaidDigging.inRing(mob(2.5,0,2.5),mk4),"the corner of the 5x5 around a Mk IV");assertFalse(RaidDigging.inRing(mob(3.5,0,.5),mk4));
        assertTrue(RaidDigging.reaches(mob(.5,1,.5),new AABB(0,0,0,1,1,1)),"the block under its feet");
        assertTrue(RaidDigging.reaches(mob(1.5,0,.5),new AABB(0,2,0,1,3,1)),"a block over its head");assertFalse(RaidDigging.reaches(mob(2.5,0,.5),new AABB(0,0,0,1,1,1)));
    }
    @Test void bridgesOverWaterLavaAndChasms() throws Exception{
        var S=RaidBridges.Floor.SOLID;var W=RaidBridges.Floor.FLUID;var A=RaidBridges.Floor.AIR;
        boolean[] open=new boolean[13];int[] none=new int[13];
        assertEquals(1,RaidBridges.edge(open,row(S,W,W,W,S),none,0),"water right ahead");
        assertEquals(3,RaidBridges.edge(open,row(S,S,S,W,W),none,0),"water three blocks ahead: it walks to the edge first");
        assertEquals(-1,RaidBridges.edge(open,row(S,S,S,S,W),none,0),"farther out it is looked at later");
        boolean[] wall=new boolean[13];wall[1]=true;assertEquals(-1,RaidBridges.edge(wall,row(S,W,W,S),none,0),"a wall: digging, not bridging");
        int[] fall=new int[13];fall[1]=2;assertEquals(-1,RaidBridges.edge(open,row(S,A,S,S),fall,0),"a two-block step down is walked");
        fall[1]=3;fall[2]=3;assertEquals(1,RaidBridges.edge(open,row(S,A,A,S),fall,0),"a chasm toward a target as high");
        assertEquals(1,RaidBridges.edge(open,row(S,A,A,S),fall,-10),"a moat: land comes back at the same height");
        int[] deep=new int[13];java.util.Arrays.fill(deep,3);
        assertEquals(-1,RaidBridges.edge(open,row(S,A,A,A,A,A,A,A,A),deep,-10),"a cliff down to a lower target is walked down");
        deep[1]=Integer.MAX_VALUE;assertEquals(1,RaidBridges.edge(open,row(S,A,A,A,A,A,A,A,A),deep,-10),"a drop into water");
        assertArrayEquals(new int[]{1,0},RaidBridges.axis(5,2));assertArrayEquals(new int[]{0,-1},RaidBridges.axis(1,-4));
        assertArrayEquals(new int[]{0},RaidBridges.widths(.6f));assertArrayEquals(new int[]{-1,0,1},RaidBridges.widths(3f),"a giant gets a wide bridge");
        assertEquals(24,RaidBridges.MAX_LENGTH);assertEquals(384,RaidBridges.LIMIT);assertEquals(3,RaidBridges.CHASM);assertEquals(3,RaidBridges.EDGE);
        assertTrue(source("BeaconCombat.java").contains("if(mob.tickCount%5==0&&RaidDigging.bridgeAhead(l,CampaignData.get(l),mob))return;"),"checked four times a second while marching");
    }
    /** Floor cells 0 to n, the last one repeated out to the planner's 12 */
    static RaidBridges.Floor[] row(RaidBridges.Floor... cells){var out=new RaidBridges.Floor[13];java.util.Arrays.fill(out,cells[cells.length-1]);System.arraycopy(cells,0,out,0,cells.length);return out;}
    @Test void stuckRaidersJoinTheSquadronInsteadOfLeaving() throws Exception{
        assertTrue(RaidMarch.dropNow(10,4,32),"under a sky base: straight to the squadron");
        assertFalse(RaidMarch.dropNow(10,3,32),"a step below is not a sky base");assertFalse(RaidMarch.dropNow(41,10,32),"far out a gate helps");
        assertTrue(RaidMarch.reachable(4));assertFalse(RaidMarch.reachable(4.5),"a defender on a tower out of reach does not keep a raider busy");
        String rescue=source("RaidRescue.java");
        assertFalse(rescue.contains("mob.discard()"),"no raider is taken out of the raid any more");
        assertTrue(rescue.contains("RaidTypes.parachute(mob);")&&rescue.contains("d.beacon.getY()+SQUADRON_HEIGHT"),"it jumps right above the beacon under a parachute");
        assertTrue(source("BeaconCombat.java").contains("else mob.getMoveControl().setWantedPosition(x,mob.getY()-1,z,1);"),"and steers at it on the way down instead of following a ground path");
        assertFalse(rescue.contains("lookAhead("),"water, lava and chasms are bridged instead of gated");
        assertEquals(24,RaidRescue.SQUADRON_HEIGHT);
        assertTrue(RaidRescue.landing(64,64)&&RaidRescue.landing(70,64),"beside a ground-level beacon, or on its cover");
        assertFalse(RaidRescue.landing(52,64),"past the edge of a sky island the column falls to the ground");
        assertTrue(text("raid.squadron.many").contains("%s stuck raiders joined the parachute squadron"));assertTrue(text("raid.squadron.one").contains("parachute squadron"));
        assertTrue(text("raid.no_ground").contains("parachute"),"no ground at all: they jump by parachute");
        assertTrue(source("RaidSpawns.java").contains("new Place(chuteDrop(l,d),false,true)"));
        assertTrue(source("EternalDefense.java").contains("if(!before.contains(id)&&l.getEntity(id) instanceof Mob mob)RaidTypes.parachute(mob);"));
    }
    @Test void theGoalsUseTheNewRules() throws Exception{
        String combat=source("BeaconCombat.java");
        assertTrue(combat.contains("target=RaidTargets.choose(l,CampaignData.get(l),mob,objective)"),"one place decides what a raider goes for");
        assertTrue(combat.contains("if(!RaidDigging.inRing(mob.getBoundingBox(),target.getBoundingBox())){"),"melee attacks start in the ring");
        assertTrue(combat.contains("if(--swing<=0&&!(mob instanceof Creeper)){swing=RaidDigging.SWING_TICKS;RaidDigging.swing(l,mob);}"),"and the attack animation never stops while it hits");
        assertTrue(combat.contains("mob.goalSelector.addGoal(-1,new RaidDigging.WorkGoal(mob));"));
        assertTrue(combat.contains("RaidDigging.hit(l,d,RaidTargets.base(state,hit.getBlockPos()),attacker,RaidDigging.SHOT,true)"),"arrows and bullets wear defenses down");
        assertTrue(combat.contains("arrow.shoot(dx,dy+lift(flat,dy,speed),dz,speed,0);"),"an arrow loosed at a defense flies straight at it");
        for(double flat:new double[]{4,7,11,16})for(double dy:new double[]{-2,-1.1,0,1.5}){
            double up=dy+BeaconCombat.lift(flat,dy,1.6);
            assertEquals(dy,BeaconCombat.heightAt(flat,up,1.6),.05,"an arrow aimed this way comes down on the block "+flat+" away, "+dy+" up");
        }
        String digging=source("RaidDigging.java");
        assertTrue(digging.contains("l.destroyBlockProgress(crack.id,pos,stage(crack.done,hp));"),"the crack shows like a player's");
        assertTrue(digging.contains("breakBlock(l,d,pos,state,by);RaidTargets.broken(l,pos);"),"a broken defense lets its raiders choose again at once");
        assertTrue(source("RaidTargets.java").contains("@Override public boolean isInvisible(){return true;}"));
        assertTrue(source("RaidSpawns.java").contains("depth<=12&&p.getY()-depth>=l.getMinBuildHeight()"),"a superflat world's floor is not an overhang");
        assertTrue(digging.contains("l.levelEvent(2001,pos,Block.getId(state));EternalDefense.damageBlock(l,d,pos);"),"inside the zone it is saved and put back after a win");
        assertTrue(source("EternalDefense.java").contains("!event.getState().is(RAIDER_BRIDGE.get())"),"players may break raider bridges inside the zone");
        assertTrue(source("BeaconClient.java").contains("e.registerEntityRenderer(EternalDefense.DEFENSE_POINT.get(),net.minecraft.client.renderer.entity.NoopRenderer::new)"));
        assertEquals("43",BeaconNetwork.PROTOCOL,"no packet changed");
    }
    @Test void theGuideQuotesTheRealNumbers() throws Exception{
        String detail=text("guide.raids.detail"),body=text("guide.raids.body");
        assertTrue(detail.contains("within "+(int)RaidTargets.SIGHT+" blocks of a raider and closer to it than the beacon, no farther from the beacon than the raider plus "+(int)RaidTargets.AHEAD_SLACK+" blocks, at most "+(int)RaidTargets.MAX_RISE+" blocks up or down"));
        assertTrue(detail.contains("cannot hurt one for "+RaidTargets.GIVE_UP/20+" seconds leaves it alone for a minute")&&RaidTargets.FORGET==1200);
        assertTrue(detail.contains("3 damage plus 6 per point of hardness, "+(int)RaidDigging.HP_CAP+" at most (dirt 6, planks and cobblestone 15, obsidian 300)"));
        assertTrue(detail.contains("Arrows and bullets do "+(int)RaidDigging.SHOT+" to a defense or door. Cracks heal after "+RaidDigging.FORGET_TICKS/20+" seconds."));
        assertTrue(detail.contains("within "+(int)RaidTargets.PLAYER_CLOSE+" blocks ("+(int)RaidTargets.MELEE_RISE+" up or down) and let go past "+(int)RaidTargets.PLAYER_LEASH+" blocks"));
        assertTrue(detail.contains("within "+(int)RaidTargets.FOCUS_RANGE+" blocks and keep on them within "+(int)RaidTargets.FOCUS_LEASH+" blocks for "+RaidTargets.FOCUS_MEMORY/20+" seconds"));
        assertTrue(detail.contains("falls of "+RaidBridges.CHASM+" blocks or more")&&detail.contains("Up to "+RaidBridges.MAX_LENGTH+" planks a bridge and "+RaidBridges.LIMIT+" a raid."));
        assertTrue(detail.contains("no progress for "+RaidMarch.STUCK_SECONDS+" seconds opens a gate. After "+RaidMarch.MAX_GATES+" gates, "+RaidMarch.DRY_LIMIT+" tries without a place, or "+RaidMarch.DROP_RISE+" blocks or more below the beacon within "+RaidMarch.DROP_NEAR+" blocks of the zone, it jumps from "+RaidRescue.SQUADRON_HEIGHT+" blocks above the beacon."));
        assertTrue(body.contains("Raiders ignore players in Creative.")&&body.contains("A creeper near your base blows the wall up instead."));
        assertTrue(body.contains("They hit it from any block around it, or from on top."));
    }
}
