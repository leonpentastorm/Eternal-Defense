package dev.createarsenal.beacon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The two blocks in front of a Support Platform (two tall) are a no-build zone for as long as the platform stands, so a Return Flare
 * never delivers anybody into a wall. Remove the platform and the zone is gone.
 */
public final class ReturnZone {
    static boolean inside(ServerLevel level,net.minecraft.core.BlockPos pos){
        var data=SupportData.get(level);
        for(var base:data.bases.values()){
            if(base.platform==null||!level.isLoaded(base.platform))continue;
            var state=level.getBlockState(base.platform);
            if(!state.is(ArsenalBeacon.SUPPORT_PLATFORM.get()))continue;
            if(SupportCalls.returnZone(base.platform,state.getValue(SupportPlatform.FACING)).contains(pos))return true;
        }
        return false;
    }
    @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent e){
        if(!(e.getLevel() instanceof ServerLevel level)||level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)return;
        if(e.getPlacedBlock().is(ArsenalBeacon.SUPPORT_PLATFORM.get()))return;
        if(!inside(level,e.getPos()))return;
        e.setCanceled(true);
        if(e.getEntity() instanceof Player p)p.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.keep_clear").withStyle(net.minecraft.ChatFormatting.RED),true);
    }
}
