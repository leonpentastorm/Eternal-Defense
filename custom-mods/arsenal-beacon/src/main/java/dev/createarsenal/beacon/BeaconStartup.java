package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Set;

/** Reliable placement handshake and a real elapsed wait, with no artificial startup cooldown. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class BeaconStartup {
    private static BeaconNetwork.Placement request;
    private static BeaconNetwork.State deferred;
    private static long started,nextRetry,nextMessage;
    private static boolean cullingReady;
    static void begin(UseOnContext context){
        if(deferred!=null||request!=null&&request.hand()==context.getHand()&&request.hit().getBlockPos().equals(context.getClickedPos())&&request.hit().getDirection()==context.getClickedFace())return;
        request=new BeaconNetwork.Placement(new BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),context.isInside()),context.getHand());
        started=System.nanoTime();nextRetry=started+2_000_000_000L;nextMessage=0;
    }
    static boolean defer(BeaconNetwork.State packet){
        if(packet.screen().equals("placementFailed")){request=null;deferred=null;started=0;var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.literal(packet.message()),true);return false;}
        if(!packet.screen().equals("place"))return false;
        request=null;
        if(ready()){started=0;deferred=null;return false;}
        deferred=packet;if(started==0)started=System.nanoTime();return true;
    }
    private static boolean ready(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.player!=null&&mc.getOverlay()==null&&(mc.screen==null||mc.screen instanceof BeaconClient.ConfirmScreen);}
    static void clear(){request=null;deferred=null;started=nextRetry=nextMessage=0;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(!cullingReady&&mc.level!=null)exemptBeam();
        if(deferred!=null&&ready()){var packet=deferred;deferred=null;started=0;BeaconClient.receive(packet);return;}
        if(started==0||mc.player==null)return;long now=System.nanoTime();
        if(request!=null&&!mc.player.getItemInHand(request.hand()).is(ArsenalBeacon.BEACON_ITEM.get())){clear();return;}
        if(request!=null&&mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(request.hit().getBlockPos()))>64){clear();mc.player.displayClientMessage(Component.literal("[Create Arsenal] Placement canceled: move closer and use the beacon again."),true);return;}
        if(now>=nextMessage){
            String status=ready()?"Waiting for server confirmation":"Waiting for world loading";
            mc.player.displayClientMessage(Component.literal("[Create Arsenal] "+status+" — "+((now-started)/1_000_000_000L)+"s elapsed. Please wait...").withStyle(net.minecraft.ChatFormatting.AQUA),true);nextMessage=now+1_000_000_000L;
        }
        if(request!=null&&ready()&&mc.getConnection()!=null&&now>=nextRetry){BeaconNetwork.requestPlacement(request);nextRetry=now+2_000_000_000L;}
    }
    @SuppressWarnings("unchecked") private static void exemptBeam(){
        if(!net.minecraftforge.fml.ModList.get().isLoaded("entityculling")){cullingReady=true;return;}
        try{
            Class<?> api=Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");Object instance=api.getField("instance").get(null);if(instance==null)return;
            // The installed mod already exempts vanilla beacons. Extend its native whitelist only.
            ((Set<Object>)api.getField("blockEntityWhitelist").get(instance)).add(ArsenalBeacon.BEACON_ENTITY.get());
            Object config=api.getField("config").get(instance);
            ((Set<String>)config.getClass().getField("blockEntityWhitelist").get(config)).add("arsenal_beacon:defense_beacon");((Set<String>)config.getClass().getField("blockEntityWhitelist").get(config)).add("arsenal_beacon:station");cullingReady=true;
        }catch(ReflectiveOperationException e){cullingReady=true;com.mojang.logging.LogUtils.getLogger().warn("Beacon culling compatibility could not initialize",e);}
    }
}
