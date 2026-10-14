package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 0.0.42 list: the corpse crash, placement that never eats the item (red previews), and the return zone kept clear of falling blocks. */
final class Round0042Test {
    static String source(String name) throws Exception{return Round0035Test.source(name);}

    // ---- the corpse crash: Player Animator's fades on a body the world never ticks -----------------------------------------------------
    @Test void aStandInBodyIsMovedOnOncePerGameTickAsTheWorldMovesARealPlayer() throws Exception{
        String s=source("StandInAnimations.java");
        assertTrue(s.contains("@SubscribeEvent(priority=EventPriority.HIGHEST) public static void render(RenderPlayerEvent.Pre e)"),"before TaCZ adds this frame's fade");
        assertTrue(s.contains("level.getEntity(player.getId())==player"),"a player the world ticks is left alone");
        assertTrue(s.contains("ModList.get().isLoaded(\"playeranimator\")"),"nothing without Player Animator");
        assertTrue(s.contains("\"dev.kosmx.playerAnim.api.IPlayer\").getMethod(\"getAnimationStack\")")&&s.contains("\"dev.kosmx.playerAnim.api.layered.IAnimation\").getMethod(\"tick\")"),"Player Animator's own API, not its internals");
        assertTrue(s.contains("new WeakHashMap<>()"),"the Corpse mod's copies are let go");
        // one tick per game tick; a body out of sight a while is caught up, not replayed; a frame in the same tick moves nothing
        assertEquals(0,StandInAnimations.ticks(100,100));
        assertEquals(1,StandInAnimations.ticks(100,101));
        assertEquals(3,StandInAnimations.ticks(100,103));
        assertEquals(StandInAnimations.MOST_AT_ONCE,StandInAnimations.ticks(100,100_000));
        assertEquals(0,StandInAnimations.ticks(200,100),"time never runs backwards");
        assertTrue(StandInAnimations.MOST_AT_ONCE>=8,"at least a whole fade (TaCZ's last 8 ticks)");
    }

    // ---- placement: nothing in the way, and a refusal never costs the item ------------------------------------------------------------------
    @Test void everyPieceWithSomethingInTheWayIsRefusedOnBothSides() throws Exception{
        String guard=source("PlacementGuard.java");
        assertTrue(guard.contains("ClientboundContainerSetSlotPacket.PLAYER_INVENTORY"),"the hand slot is sent back as the server has it");
        assertTrue(guard.contains("!DefenseStructures.available(level,root,state)"),"every cell of the piece");
        assertTrue(guard.contains("level.isUnobstructed(state,root"),"and creatures in its own cell, as the game checks");
        // each block item of the mod checks before it places, on the client too, and sends the hand back on a refusal
        String zone=source("BaseZone.java"),fob=source("FobBlocks.java"),kitchen=source("KitchenBlock.java"),catapult=source("SupplyCatapult.java"),table=source("WeaponPlatform.java"),hub=source("LogisticsHub.java");
        assertTrue(zone.contains("if(PlacementGuard.blocked(c,state))")&&zone.contains("return PlacementGuard.settled(c,super.place(c));"),"Exchange, platforms, cannon, satellite, tables");
        assertFalse(zone.contains("return InteractionResult.FAIL;"),"no refusal in a zone item without the hand sent back");
        assertTrue(fob.contains("if(PlacementGuard.blocked(c,state))")&&fob.contains("FobOutlineClient.refused(c.getClickedPos())")&&fob.contains("return PlacementGuard.settled(c,super.place(c));"),"the FOB Beacon: the hole, the server's verdict, the hand");
        assertTrue(kitchen.contains("if(PlacementGuard.blocked(c,s))")&&kitchen.contains("return PlacementGuard.settled(c,super.place(c));"),"Mess Halls, Drink Stations, pots, dispensers, the MRE Station");
        assertTrue(catapult.contains("PlacementGuard.blocked(c,getBlock().getStateForPlacement(c))")&&catapult.contains("return PlacementGuard.refuse(c);"),"the Supply Catapult");
        assertTrue(table.contains("return PlacementGuard.settled(context,result);"),"the weapon tables");
        assertTrue(hub.contains("return free(level,anchor)?InteractionResult.SUCCESS:InteractionResult.FAIL;")&&hub.contains("if(!free(level,anchor))"),"the Hub: one check for both sides");
        // a placement cancelled by an event (the return zone, the raid lock) gets the hands sent after Forge put the count back
        assertTrue(guard.contains("TickEvent.ServerTickEvent")&&guard.contains("LATER"));
        assertTrue(source("ReturnZone.java").contains("e.setCanceled(true);PlacementGuard.resyncLater(e.getEntity());"));
        assertTrue(source("EternalDefense.java").contains("event.setCanceled(true);PlacementGuard.resyncLater(event.getEntity());"));
    }
    @Test void thePreviewsTurnRedWithSomethingInTheWay() throws Exception{
        String preview=source("PlacementPreviewClient.java"),fob=source("FobOutlineClient.java");
        assertTrue(preview.contains("record Ghost(List<BlockPos> cells,List<BlockState> states,AABB box,boolean beacon,boolean blocked)"));
        assertTrue(preview.contains("!RailGates.available(mc.level,anchor,facing)")&&preview.contains("!LogisticsHub.HubItem.free(mc.level,anchor)")&&preview.contains("PlacementGuard.blocked(ctx,wanted)"),"gates, the Hub and every other piece");
        assertTrue(preview.contains("int[] tint=ghost.blocked()?PlacementGuard.RED:CYAN;")&&preview.contains("ghostTint(ghost.blocked())")&&preview.contains("return PlacementGuard.tint(blocked,CYAN);"),"the outline and the model");
        assertTrue(fob.contains("static int[] colour(BlockPos at){return blocked(at)?PlacementGuard.RED:"),"the FOB Beacon's own preview");
        // a block model's colour only reaches its tinted faces: the ghost is turned red on every vertex
        assertTrue(fob.contains("to.color(Math.round(r*red),Math.round(g*green),Math.round(b*blue),Math.round(a*alpha))"));
        assertTrue(preview.contains("ghost.blocked()?FobOutlineClient.Faint.blocked(")&&fob.contains("var consumer=red?Faint.blocked("),"both previews");
        assertTrue(fob.contains("if(blocked(target()))return Ui.t(\"fob.preview.blocked\");"));
        assertTrue(Round0041Test.lang().contains("\"gui.eternal_defense.fob.preview.blocked\": \"Something is in the way: the FOB Beacon needs 2 free blocks, one on the other.\""));
        // red reads as red: strong red, weak green and blue
        float[] red=PlacementGuard.tint(true,PlacementGuard.RED),cyan=PlacementGuard.tint(false,new int[]{70,235,221});
        assertTrue(red[0]>.9f&&red[1]<.25f&&red[2]<.25f,java.util.Arrays.toString(red));
        assertTrue(cyan[1]>.9f&&cyan[2]>.9f);
    }

    // ---- the ammo table's magazines --------------------------------------------------------------------------------------------------------
    @Test void aMagazineIsListedNoLaterThanTheRoundsItHolds() throws Exception{
        String bridge=source("MagazineBridge.java"),table=source("WeaponPlatform.java");
        assertTrue(bridge.contains("if(ordinary){var held=ammo(stack);if(held!=null)minimum=Math.min(minimum,rounds.getOrDefault(held.toString(),5));}"),"the rounds' Age caps the standard magazine's");
        assertTrue(bridge.contains("getMethod(\"getAmmoTypeForFamily\",String.class)"),"TaCZ Magazines names the rounds a family holds");
        assertTrue(table.contains("rounds.merge(e.output.getTag().getString(\"AmmoId\"),e.gate.age,Math::min)"),"the rounds' Age as this table lists them (the recipes loaded, not the whole catalogue)");
        assertTrue(table.contains("MagazineBridge.gate((ItemStack)m[2],rounds)"),"magazines are worked out after the rounds");
        // extended magazines stay one Age per level above the standard one, never past Age 5
        assertEquals(2,MagazineBridge.age(2,0));assertEquals(3,MagazineBridge.age(2,1));assertEquals(5,MagazineBridge.age(3,3));assertEquals(5,MagazineBridge.age(5,0));
    }

    // ---- the return zone ------------------------------------------------------------------------------------------------------------------
    @Test void fallingBlocksAreBrokenOutOfTheReturnZone() throws Exception{
        String s=source("ReturnZone.java");
        assertTrue(s.contains("@SubscribeEvent public void tick(TickEvent.LevelTickEvent e)")&&s.contains("level.getGameTime()%SWEEP!=0"));
        assertEquals(10,ReturnZone.SWEEP,"twice a second");
        assertTrue(s.contains("level.destroyBlock(cell,true)"),"broken out and dropped as an item, never deleted");
        assertTrue(s.contains("!state.isAir()&&!state.canBeReplaced()&&!state.getCollisionShape(level,pos).isEmpty()"),"solid only: the platform's own rule for its zone");
        assertTrue(source("SupportPlatform.java").contains("if(!st.canBeReplaced()&&!st.getCollisionShape(level,cell).isEmpty())"),"the same rule the platform is placed by");
        assertTrue(s.contains("||ours(state)"),"the mod's own pieces are left alone");
        assertTrue(s.contains("state.getDestroySpeed(level,cell)<0"),"nothing unbreakable is broken");
        assertTrue(source("BaseZone.java").contains("for(var cell:DefenseStructures.cells(c.getClickedPos(),state))if(ReturnZone.inside(server,cell))"),"no cell of a cannon may stand in a zone");
        // the zone itself: two blocks in front, two tall
        var zone=SupportCalls.returnZone(net.minecraft.core.BlockPos.ZERO,net.minecraft.core.Direction.NORTH);
        assertEquals(List.of(new net.minecraft.core.BlockPos(0,0,-1),new net.minecraft.core.BlockPos(0,1,-1),new net.minecraft.core.BlockPos(0,0,-2),new net.minecraft.core.BlockPos(0,1,-2)),zone);
    }
}
