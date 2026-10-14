package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 0.0.42 (owner): nothing of the mod is placed with something in its way, and a refused placement never costs the item.
 * <p>The game places a block on the player's screen the moment it is used, before the server has answered. When the server then refused
 * (a FOB Beacon outside a village or in a hole one block high, a base block outside the zone) the screen had already taken the item from
 * the hand, and nothing put it back until a chest-like screen sent the whole inventory again. So what the player's own game can see (a
 * block in the way of any cell of the piece) it now refuses too, the placement preview turning red; and for what only the server knows,
 * the server sends the hand slot back with its refusal.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=EternalDefense.ID)
public final class PlacementGuard {
    private PlacementGuard(){}
    /** Red, for a placement preview with something in the way. */
    static final int[] RED={235,64,52};
    /** A preview model's tint: its own colours washed toward {@code calm}, or strongly toward red when something is in the way. */
    static float[] tint(boolean blocked,int[] calm){
        if(blocked)return new float[]{.25f+.75f*RED[0]/255f,.25f*RED[1]/255f+.1f,.25f*RED[2]/255f+.1f};
        return new float[]{.55f+.45f*calm[0]/255f,.55f+.45f*calm[1]/255f,.55f+.45f*calm[2]/255f};
    }

    /** A refusal: on the server the hand slot is sent again, in case the player's game already spent the item guessing it would be placed. */
    static InteractionResult refuse(BlockPlaceContext c){if(!c.getLevel().isClientSide)resync(c.getPlayer(),c.getHand());return InteractionResult.FAIL;}
    /** Sends the hand slot as the server has it. */
    static void resync(Player p,InteractionHand hand){
        if(!(p instanceof ServerPlayer sp)||sp.connection==null)return;
        int slot=hand==InteractionHand.MAIN_HAND?sp.getInventory().selected:Inventory.SLOT_OFFHAND;
        sp.connection.send(new ClientboundContainerSetSlotPacket(ClientboundContainerSetSlotPacket.PLAYER_INVENTORY,0,slot,sp.getItemInHand(hand).copy()));
    }
    /** Players whose hands are sent again at the end of this server tick (a placement event cancelled after the item was used: Forge puts the
     *  count back only once the event is over, so the hands are sent when it is). */
    private static final java.util.Set<ServerPlayer> LATER=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    static void resyncLater(net.minecraft.world.entity.Entity who){if(who instanceof ServerPlayer sp)LATER.add(sp);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||LATER.isEmpty())return;
        for(var sp:java.util.List.copyOf(LATER)){resync(sp,InteractionHand.MAIN_HAND);resync(sp,InteractionHand.OFF_HAND);}
        LATER.clear();
    }
    /** After a placement went to the block item: a refusal on the server gets the hand slot sent again. */
    static InteractionResult settled(BlockPlaceContext c,InteractionResult result){if(!result.consumesAction()&&!c.getLevel().isClientSide)resync(c.getPlayer(),c.getHand());return result;}

    /**
     * Is something in the way of {@code state} placed at {@code root}: no state (the block itself refused the spot), a block or a creature
     * in its own cell, or one in any other cell of the piece (a FOB Beacon's top half, a Mess Hall's second column, a cannon's 3 x 3 x 2).
     */
    static boolean blocked(Level level,BlockPos root,BlockState state,Player player){return state==null||!level.getBlockState(root).canBeReplaced()||obstructed(level,root,state,player);}
    /** As above for a placement being made (its own cell as the game judges it: tall grass or one snow layer may be built over). */
    static boolean blocked(BlockPlaceContext c,BlockState state){return state==null||!c.canPlace()||obstructed(c.getLevel(),c.getClickedPos(),state,c.getPlayer());}
    private static boolean obstructed(Level level,BlockPos root,BlockState state,Player player){
        return !level.isUnobstructed(state,root,player==null?CollisionContext.empty():CollisionContext.of(player))||!DefenseStructures.available(level,root,state);
    }
}
