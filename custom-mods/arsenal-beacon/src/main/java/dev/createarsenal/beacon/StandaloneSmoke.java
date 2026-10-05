package dev.createarsenal.beacon;

/** Enabled only by an isolated test launch; never schedules work during normal play. */
public final class StandaloneSmoke {
    private net.minecraft.server.level.ServerPlayer player;private int ticks;
    @net.minecraftforge.eventbus.api.SubscribeEvent public void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p){player=p;ticks=0;}}
    @net.minecraftforge.eventbus.api.SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||player==null||++ticks!=80)return;
        var l=player.serverLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var pos=new net.minecraft.core.BlockPos(0,100,0);l.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);l.setBlock(pos.east().below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);l.setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5).setValue(ArsenalStructures.WIDE,true).setValue(ArsenalStructures.TALL,true),3);player.teleportTo(.5,100, -2.5);
        var gun=WeaponPlatform.entries(player,"gun","").get(0).output().copy();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);WeaponPlatform.open(player,pos);com.mojang.logging.LogUtils.getLogger().info("STANDALONE_SERVER_UI_OPEN");player=null;
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static int ticks,step,joinTicks;private static boolean joined;
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(!Boolean.getBoolean("arsenal.standaloneSmoke")||e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=net.minecraft.client.Minecraft.getInstance();
            if(Boolean.getBoolean("arsenal.smokeJoin")&&!joined&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&++joinTicks==80){joined=true;var data=new net.minecraft.client.multiplayer.ServerData("Isolated standalone test","127.0.0.1:25577",false);net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(data.ip),data,false);}
            if(mc.player==null)return;
            if(step==0&&mc.screen instanceof PlatformScreen){if(++ticks==40){com.mojang.logging.LogUtils.getLogger().info("STANDALONE_PLATFORM_UI_PASS");mc.setScreen(new BeaconClient.ControlScreen());step++;ticks=0;}}
            else if(step==1&&mc.screen instanceof BeaconClient.ControlScreen){if(++ticks==40){com.mojang.logging.LogUtils.getLogger().info("STANDALONE_CONTROL_UI_PASS");mc.setScreen(new BeaconClient.GuideScreen(null));step++;ticks=0;}}
            else if(step==2&&mc.screen instanceof BeaconClient.GuideScreen){if(++ticks==40){com.mojang.logging.LogUtils.getLogger().info("STANDALONE_GUIDE_UI_PASS");mc.setScreen(null);step++;ticks=0;}}
            else if(step==3&&mc.screen==null&&++ticks==180){com.mojang.logging.LogUtils.getLogger().info("STANDALONE_CLIENT_SMOKE_PASS");step++;}
        }
    }
}
