package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** The two blocks in front of a Support Platform (two tall) are a no-build zone for as long as the platform */
public final class ReturnZone {
    /** How often the zones are looked at (ticks). */
    static final int SWEEP=10;
    /** How far from the zone a player is warned when a block is broken out of it. */
    static final double WARN=16;

    static boolean inside(ServerLevel level,BlockPos pos){
        var data=SupportData.get(level);
        for(var base:data.bases.values()){
            if(base.platform==null||!level.isLoaded(base.platform))continue;
            var state=level.getBlockState(base.platform);
            if(!state.is(EternalDefense.SUPPORT_PLATFORM.get()))continue;
            if(SupportCalls.returnZone(base.platform,state.getValue(SupportPlatform.FACING)).contains(pos))return true;
        }
        return false;
    }
    /** The mod's own pieces are left alone: they are placed by checked paths only */
    static boolean ours(BlockState state){return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(EternalDefense.ID);}
    /** Would this block stop a returning player: solid, not something a block may simply replace */
    static boolean inTheWay(BlockGetter level,BlockPos pos,BlockState state){return !state.isAir()&&!state.canBeReplaced()&&!state.getCollisionShape(level,pos).isEmpty();}

    @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent e){
        if(!(e.getLevel() instanceof ServerLevel level)||level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)return;
        if(e.getPlacedBlock().is(EternalDefense.SUPPORT_PLATFORM.get()))return;
        if(!inside(level,e.getPos()))return;
        e.setCanceled(true);PlacementGuard.resyncLater(e.getEntity());   // The item the player's game spent comes back at once
        if(e.getEntity() instanceof Player p)p.displayClientMessage(Component.translatable("gui.eternal_defense.support.keep_clear").withStyle(net.minecraft.ChatFormatting.RED),true);
    }
    /** The sweep (see the class note). */
    @SubscribeEvent public void tick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel level)||level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||level.getGameTime()%SWEEP!=0)return;
        for(var base:SupportData.get(level).bases.values()){
            if(base.platform==null||!level.isLoaded(base.platform))continue;
            var platform=level.getBlockState(base.platform);
            if(!platform.is(EternalDefense.SUPPORT_PLATFORM.get()))continue;
            for(var cell:SupportCalls.returnZone(base.platform,platform.getValue(SupportPlatform.FACING)))clear(level,cell);
        }
    }
    /** Breaks a block in the way out of a zone cell (it drops as an item) and warns the players near it. */
    static boolean clear(ServerLevel level,BlockPos cell){
        if(!level.isLoaded(cell))return false;
        var state=level.getBlockState(cell);
        if(!inTheWay(level,cell,state)||state.getDestroySpeed(level,cell)<0||ours(state))return false;
        level.destroyBlock(cell,true);
        for(var p:level.players())if(p.distanceToSqr(cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5)<=WARN*WARN)
            p.displayClientMessage(Component.translatable("gui.eternal_defense.support.keep_clear").withStyle(net.minecraft.ChatFormatting.RED),true);
        com.mojang.logging.LogUtils.getLogger().info("[return zone] broke {} out of the return zone at {}",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),cell.toShortString());
        return true;
    }
}
