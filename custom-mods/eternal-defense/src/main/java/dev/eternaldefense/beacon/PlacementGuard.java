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

/** Nothing of the mod is placed with something in its way, and a refused placement never costs the item. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=EternalDefense.ID)
public final class PlacementGuard {
    private PlacementGuard(){}
    /** Red, for a placement preview with something in the way. */
    static final int[] RED={235,64,52};
    /** A preview model's tint: its own colours washed toward calm */
    static float[] tint(boolean blocked,int[] calm){
        if(blocked)return new float[]{.25f+.75f*RED[0]/255f,.25f*RED[1]/255f+.1f,.25f*RED[2]/255f+.1f};
        return new float[]{.55f+.45f*calm[0]/255f,.55f+.45f*calm[1]/255f,.55f+.45f*calm[2]/255f};
    }

    /** A refusal: on the server the hand slot is sent again */
    static InteractionResult refuse(BlockPlaceContext c){if(!c.getLevel().isClientSide)resync(c.getPlayer(),c.getHand());return InteractionResult.FAIL;}
    /** Sends the hand slot as the server has it. */
    static void resync(Player p,InteractionHand hand){
        if(!(p instanceof ServerPlayer sp)||sp.connection==null)return;
        int slot=hand==InteractionHand.MAIN_HAND?sp.getInventory().selected:Inventory.SLOT_OFFHAND;
        sp.connection.send(new ClientboundContainerSetSlotPacket(ClientboundContainerSetSlotPacket.PLAYER_INVENTORY,0,slot,sp.getItemInHand(hand).copy()));
    }
    /** Players whose hands are sent again at the end of this server tick */
    private static final java.util.Set<ServerPlayer> LATER=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    static void resyncLater(net.minecraft.world.entity.Entity who){if(who instanceof ServerPlayer sp)LATER.add(sp);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||LATER.isEmpty())return;
        for(var sp:java.util.List.copyOf(LATER)){resync(sp,InteractionHand.MAIN_HAND);resync(sp,InteractionHand.OFF_HAND);}
        LATER.clear();
    }
    /** After a placement went to the block item: a refusal on the server gets the hand slot sent again. */
    static InteractionResult settled(BlockPlaceContext c,InteractionResult result){if(!result.consumesAction()&&!c.getLevel().isClientSide)resync(c.getPlayer(),c.getHand());return result;}

    /** Is something in the way of state placed at root: no state (the block itself refused the spot) */
    static boolean blocked(Level level,BlockPos root,BlockState state,Player player){return state==null||!level.getBlockState(root).canBeReplaced()||obstructed(level,root,state,player);}
    /** As above for a placement being made */
    static boolean blocked(BlockPlaceContext c,BlockState state){return state==null||!c.canPlace()||obstructed(c.getLevel(),c.getClickedPos(),state,c.getPlayer());}
    private static boolean obstructed(Level level,BlockPos root,BlockState state,Player player){
        return !level.isUnobstructed(state,root,player==null?CollisionContext.empty():CollisionContext.of(player))||!DefenseStructures.available(level,root,state);
    }
}
