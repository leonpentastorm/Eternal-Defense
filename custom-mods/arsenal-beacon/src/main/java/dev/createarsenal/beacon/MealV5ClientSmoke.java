package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Disposable-world UI checks; never enabled unless explicitly launched for QA. */
public final class MealV5ClientSmoke {
    static final BlockPos ROOT=new BlockPos(28672,101,0);
    private boolean ready;private int ticks;
    @SubscribeEvent public void setupV5(net.minecraftforge.event.TickEvent.ServerTickEvent e)throws Exception{
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||ready)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();var p=server.getPlayerList().getPlayerByName("ArsenalDev");if(p==null||++ticks<60)return;
        var l=p.serverLevel();UpgradeGameTests.arena(l,ROOT.below());var d=CampaignData.get(l);d.resetProgress();d.damage.clear();d.core=3;d.phase="preparation";d.beacon=ROOT.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        l.setBlock(ROOT.east(3),ArsenalBeacon.MESS_HALL_I.get().defaultBlockState(),3);l.setBlock(ROOT.east(6),ArsenalBeacon.MESS_HALL_IV.get().defaultBlockState(),3);
        l.setBlock(ROOT.east(9),ArsenalBeacon.BOWL_DISPENSER.get().defaultBlockState(),3);
        l.setBlock(ROOT.east(3).south(3),ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);l.setBlock(ROOT.east(6).south(3),ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);
        int index=0;for(String kind:List.of("gun","ammo","attachment","armor"))l.setBlock(ROOT.offset((index++)*3,0,7),ArmorPlatform.station(kind).get().defaultBlockState().setValue(WeaponPlatform.AGE,index),3);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getInventory().clearContent();MessHallV4GameTests.gun(p,"tacz:ak47");ready=true;open(p,"mk1");
        com.mojang.logging.LogUtils.getLogger().info("V5_UI_FIXTURE_READY");
    }
    @SubscribeEvent public void registerUiFixture(net.minecraftforge.event.RegisterCommandsEvent e){
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-v5-ui").then(net.minecraft.commands.Commands.argument("phase",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{open(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"phase"));return 1;})));
    }
    private static void open(ServerPlayer p,String phase){
        var l=p.serverLevel();p.connection.teleport(ROOT.getX()+.5,ROOT.getY(),ROOT.getZ()-2.5,0,0);
        if(phase.equals("mk1")||phase.equals("mk4")){
            var pos=ROOT.east(phase.equals("mk1")?3:6);var hall=(MessHall.HallEntity)l.getBlockEntity(pos);hall.ingredients.clearContent();
            var foods=phase.equals("mk1")?new Item[]{Items.CARROT,Items.BREAD,Items.COOKED_COD}:new Item[]{Items.CARROT,Items.POTATO,Items.BREAD,Items.COOKIE,Items.COOKED_COD,Items.COOKED_SALMON};
            for(int i=0;i<foods.length;i++)hall.ingredients.setItem(i,new ItemStack(foods[i],16));hall.discover();
            p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-2.5,0,0);net.minecraftforge.network.NetworkHooks.openScreen(p,hall,b->{b.writeBlockPos(pos);b.writeVarInt(hall.mk());});
        }else if(phase.equals("bowl")){
            var pos=ROOT.east(9);var dispenser=(BowlDispenser.DispenserEntity)l.getBlockEntity(pos);dispenser.stock.setItem(0,new ItemStack(Items.BOWL,64));p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-2.5,0,0);
            net.minecraftforge.network.NetworkHooks.openScreen(p,dispenser,b->b.writeBlockPos(pos));
        }else if(phase.equals("beacon")){p.closeContainer();BeaconNetwork.open(p,"menu","");}
        else if(List.of("gun","ammo","attachment","armor").contains(phase)){
            var pos=ROOT.offset(List.of("gun","ammo","attachment","armor").indexOf(phase)*3,0,7);p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-2.5,0,0);WeaponPlatform.open(p,pos);
        }else if(phase.equals("done")){p.closeContainer();com.mojang.logging.LogUtils.getLogger().info("V5_SERVER_UI_PASS");}
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class KitchenV5ScreenObserver {
        private static int stage,ticks;private static boolean done;
        private static final String[] PHASES={"mk1","mk4","bowl","beacon","gun","ammo","attachment","armor"};
        @SubscribeEvent public static void inspectV5Screen(net.minecraftforge.event.TickEvent.ClientTickEvent event){
            if(!Boolean.getBoolean("arsenal.v5ClientTests")||event.phase!=net.minecraftforge.event.TickEvent.Phase.END||done)return;
            var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null||mc.screen==null)return;
            if(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)return;
            var screen=mc.screen;var phase=PHASES[stage];
            boolean expected=stage<2?screen instanceof MessHallScreen s&&s.currentMenu().mk==(stage==0?1:4):stage==2?screen instanceof BowlDispenserScreen:stage==3?screen instanceof BeaconClient.ControlScreen:screen instanceof PlatformScreen;
            if(!expected)return;
            if(++ticks==20){
                if(screen instanceof MessHallScreen s)s.stew.onPress();
                else if(stage==3||stage>=4)for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(stage==3?"Workshop Fabrication":"Upgrade table")){b.onPress();break;}
            }
            if(ticks==45&&screen instanceof MessHallScreen)net.minecraft.client.Screenshot.grab(mc.gameDirectory,"v5-"+phase+"-links.png",mc.getMainRenderTarget(),message->{});
            if(ticks==55){
                if(screen instanceof MessHallScreen s){
                    if(!s.currentMenu().view.getBoolean("StewMode")||s.currentMenu().getSlot(6).isActive())throw new IllegalStateException("Stew output still active");
                    var meal=MealData.load(s.currentMenu().view.getCompound("Meal"));if(meal==null||meal.bonuses().stream().anyMatch(b->b.pair()!=(stage==1)))throw new IllegalStateException("Incorrect tier/pair preview");
                    cursor(mc,s.uiLeft()+18,s.uiTop()+58);
                }else if(stage==3){var s=(BeaconClient.ControlScreen)screen;cursor(mc,s.left+50,s.top+66);}
                else if(stage>=4){for(var child:screen.children())if(child instanceof Ui.UiButton b&&b.getMessage().getString().equals(Ui.plain("platform.upgrade_age"))){cursor(mc,b.getX()+8,b.getY()+8);break;}}
            }
            if(ticks==80){
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"v5-"+phase+".png",mc.getMainRenderTarget(),message->{});
                if(stage==2&&mc.player.containerMenu.slots.size()!=37)throw new IllegalStateException("Bowl menu slot count");
                if(stage==3&&((BeaconClient.ControlScreen)screen).fabricationButtons.size()!=6)throw new IllegalStateException("Fabrication tab missing");
                com.mojang.logging.LogUtils.getLogger().info("V5_UI_SCREEN_PASS {}",phase);
            }
            if(ticks==85&&screen instanceof MessHallScreen s)cursor(mc,s.uiLeft()+38,s.uiTop()+80);
            if(ticks==95&&screen instanceof MessHallScreen)net.minecraft.client.Screenshot.grab(mc.gameDirectory,"v5-"+phase+"-legendary.png",mc.getMainRenderTarget(),message->{});
            if(ticks==100){
                if(++stage==PHASES.length){
                    net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(),true,mc.level.registryAccess());
                    var items=ArsenalBeacon.ARSENAL_TAB.get().getDisplayItems();long halls=items.stream().filter(s->s.getItem() instanceof KitchenBlock.KitchenItem&&((KitchenBlock.KitchenItem)s.getItem()).getBlock() instanceof MessHall.HallBlock).count();
                    if(halls!=1||items.stream().filter(s->s.is(ArsenalBeacon.MESS_HALL_I.get().asItem())).noneMatch(s->s.getHoverName().getString().equals("Mess Hall")))throw new IllegalStateException("Creative hall clutter/name");
                    mc.player.connection.sendCommand("mess-hall-v5-ui done");com.mojang.logging.LogUtils.getLogger().info("V5_CLIENT_UI_PASS screens=8 creativeHalls={}",halls);done=true;
                }else{ticks=0;cursor(mc,5,5);mc.player.connection.sendCommand("mess-hall-v5-ui "+PHASES[stage]);}
            }
        }
        private static void cursor(net.minecraft.client.Minecraft mc,double x,double y){
            int[] px=new int[1],py=new int[1];org.lwjgl.glfw.GLFW.glfwGetWindowPos(mc.getWindow().getWindow(),px,py);org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());
            // The retained client forces AWT headless; the external X11 runner positions its pointer.
            com.mojang.logging.LogUtils.getLogger().info("V5_QA_CURSOR {} {}",px[0]+(int)(x*mc.getWindow().getGuiScale()),py[0]+(int)(y*mc.getWindow().getGuiScale()));
        }
    }
}
