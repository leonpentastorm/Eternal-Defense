package dev.createarsenal.beacon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Reuse TaCZ's configured interact key without registering a conflicting H binding. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
final class PlatformKeys {
    static boolean matches(int key,int scan){
        for(var binding:Minecraft.getInstance().options.keyMappings)if(binding.getName().equals("key.tacz.interact.desc"))return binding.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(key,scan));
        return false;
    }
    @SubscribeEvent public static void key(InputEvent.Key event){
        var mc=Minecraft.getInstance();if(event.getAction()!=1||mc.screen!=null||mc.level==null||mc.player==null||!matches(event.getKey(),event.getScanCode()))return;
        if(mc.hitResult instanceof BlockHitResult hit&&mc.level.getBlockState(ArsenalStructures.anchor(mc.level,hit.getBlockPos())).getBlock() instanceof WeaponPlatform.Station)BeaconNetwork.CHANNEL.sendToServer(new WeaponPlatform.Interact(hit.getBlockPos()));
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event){
        var mc=Minecraft.getInstance();if(event.getAction()!=1||mc.screen!=null||mc.level==null||mc.player==null)return;
        var input=com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        boolean matches=false;for(var binding:mc.options.keyMappings)if(binding.getName().equals("key.tacz.interact.desc")&&binding.isActiveAndMatches(input)){matches=true;break;}
        if(matches&&mc.hitResult instanceof BlockHitResult hit&&mc.level.getBlockState(ArsenalStructures.anchor(mc.level,hit.getBlockPos())).getBlock() instanceof WeaponPlatform.Station){BeaconNetwork.CHANNEL.sendToServer(new WeaponPlatform.Interact(hit.getBlockPos()));event.setCanceled(true);}
    }
}
