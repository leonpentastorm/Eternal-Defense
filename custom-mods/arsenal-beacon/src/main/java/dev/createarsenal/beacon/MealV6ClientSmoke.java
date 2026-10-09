package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Disposable-world UI checks; never enabled unless explicitly launched for QA. */
public final class MealV6ClientSmoke {
    static final BlockPos ROOT=new BlockPos(36864,101,0);
    private boolean ready;private int ticks;
    @SubscribeEvent public void setupV6(net.minecraftforge.event.TickEvent.ServerTickEvent e)throws Exception{
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||ready)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();var p=server.getPlayerList().getPlayerByName("ArsenalDev");if(p==null||++ticks<60)return;
        var l=p.serverLevel();UpgradeGameTests.arena(l,ROOT.below());var d=CampaignData.get(l);d.resetProgress();d.damage.clear();d.core=3;d.phase="preparation";d.beacon=ROOT.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        l.setBlock(ROOT.east(3),ArsenalBeacon.MESS_HALL_I.get().defaultBlockState(),3);l.setBlock(ROOT.east(6),ArsenalBeacon.MESS_HALL_IV.get().defaultBlockState(),3);
        l.setBlock(ROOT.east(9),ArsenalBeacon.BOWL_DISPENSER.get().defaultBlockState(),3);
        l.setBlock(ROOT.east(3).south(3),ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);l.setBlock(ROOT.east(6).south(3),ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);
        int index=0;for(String kind:List.of("gun","ammo","attachment","armor"))l.setBlock(ROOT.offset((index++)*3,0,7),ArmorPlatform.station(kind).get().defaultBlockState().setValue(WeaponPlatform.AGE,index),3);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getInventory().clearContent();MessHallV4GameTests.gun(p,"tacz:ak47");l.setBlock(ROOT.east(12),ArsenalBeacon.MILK_DISPENSER.get().defaultBlockState(),3);p.getInventory().setItem(4,PreparedSandwich.create(new MealData(false,List.of(new MealData.Bonus(MealRules.Effect.VITALITY,1)),36000)));p.getInventory().setItem(5,new ItemStack(ArsenalBeacon.MILK_BOTTLE.get()));ready=true;open(p,"sandwich");
        com.mojang.logging.LogUtils.getLogger().info("V6_UI_FIXTURE_READY");
    }
    @SubscribeEvent public void registerUiFixture(net.minecraftforge.event.RegisterCommandsEvent e){
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-v6-ui").then(net.minecraft.commands.Commands.argument("phase",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{open(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"phase"));return 1;})));
    }
    private static void open(ServerPlayer p,String phase){
        var l=p.serverLevel();p.connection.teleport(ROOT.getX()+.5,ROOT.getY(),ROOT.getZ()-2.5,0,0);
        if(List.of("sandwich","normal","legendary").contains(phase)){
            var pos=ROOT.east(phase.equals("sandwich")?3:6);var hall=(MessHall.HallEntity)l.getBlockEntity(pos);hall.ingredients.clearContent();
            var foods=phase.equals("sandwich")?new Item[]{Items.COOKED_CHICKEN,Items.COOKED_MUTTON}:phase.equals("legendary")?new Item[]{Items.COOKED_CHICKEN,Items.COOKED_MUTTON,Items.BREAD,Items.WHEAT,Items.COD,Items.SALMON}:new Item[]{Items.CARROT,Items.POTATO,Items.BREAD,Items.WHEAT,Items.COOKED_COD,Items.COOKED_SALMON};
            for(int i=0;i<foods.length;i++)hall.ingredients.setItem(i,new ItemStack(foods[i],16));hall.discover();
            p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-2.5,0,0);net.minecraftforge.network.NetworkHooks.openScreen(p,hall,b->{b.writeBlockPos(pos);b.writeVarInt(hall.mk());});
        }else if(phase.equals("milk")){
            var pos=ROOT.east(12);var dispenser=(MilkDispenser.DispenserEntity)l.getBlockEntity(pos);dispenser.tank.fill(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.common.ForgeMod.MILK.get(),5750),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-2.5,0,0);net.minecraftforge.network.NetworkHooks.openScreen(p,dispenser,b->b.writeBlockPos(pos));
        }else if(phase.equals("art")){p.closeContainer();p.connection.teleport(ROOT.getX()+9,ROOT.getY()+1.5,ROOT.getZ()-6,0,12);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ArsenalBeacon.CONTROLLER.get()));}
        else if(phase.equals("bowl")){
            var pos=ROOT.east(9);var dispenser=(BowlDispenser.DispenserEntity)l.getBlockEntity(pos);dispenser.stock.setItem(0,new ItemStack(Items.BOWL,64));p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-2.5,0,0);
            net.minecraftforge.network.NetworkHooks.openScreen(p,dispenser,b->b.writeBlockPos(pos));
        }else if(phase.equals("beacon")){p.closeContainer();BeaconNetwork.open(p,"menu","");}
        else if(List.of("gun","ammo","attachment","armor").contains(phase)){
            var pos=ROOT.offset(List.of("gun","ammo","attachment","armor").indexOf(phase)*3,0,7);p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-2.5,0,0);WeaponPlatform.open(p,pos);
        }else if(phase.equals("done")){p.closeContainer();com.mojang.logging.LogUtils.getLogger().info("V6_SERVER_UI_PASS");}
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class KitchenV6ScreenObserver {
        private static int stage,ticks;private static boolean done;
        private static final String[] PHASES={"sandwich","normal","legendary","milk","bowl","beacon","art","gun","ammo","attachment","armor"};
        @SubscribeEvent public static void inspectV6Screen(net.minecraftforge.event.TickEvent.ClientTickEvent event){
            if(!Boolean.getBoolean("arsenal.v6ClientTests")||event.phase!=net.minecraftforge.event.TickEvent.Phase.END||done)return;
            var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null)return;var screen=mc.screen;var phase=PHASES[stage];
            boolean kitchen=stage<3,weapon=stage>=7;
            boolean expected=kitchen?screen instanceof MessHallScreen:phase.equals("milk")?screen instanceof MilkDispenserScreen:phase.equals("bowl")?screen instanceof BowlDispenserScreen:phase.equals("beacon")?screen instanceof BeaconClient.ControlScreen:phase.equals("art")?screen==null:screen instanceof PlatformScreen;
            if(!expected)return;
            if(++ticks==20){
                if(screen instanceof MessHallScreen s&&!phase.equals("sandwich"))s.stew.onPress();
                else if(phase.equals("beacon")||weapon)for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(phase.equals("beacon")?"Workshop Fabrication":Ui.plain("platform.tab.upgrade"))){b.onPress();break;}
            }
            if(ticks==35&&screen instanceof MessHallScreen s&&phase.equals("legendary")){
                mc.gameMode.handleInventoryButtonClick(s.currentMenu().containerId,40+MealRules.Effect.FIREPOWER.ordinal());mc.gameMode.handleInventoryButtonClick(s.currentMenu().containerId,40+MealRules.Effect.SWIM.ordinal());
            }
            if(ticks==55){
                if(screen instanceof MessHallScreen s){
                    var meal=MealData.load(s.currentMenu().view.getCompound("Meal"));if(meal==null)throw new IllegalStateException("Missing meal preview "+phase);
                    if(phase.equals("sandwich")){if(!s.currentMenu().getSlot(7).isActive()||meal.bonuses().stream().anyMatch(b->b.effect().gun())||!s.currentMenu().ingredients.getItem(7).isEmpty())throw new IllegalStateException("Separate ghost bread / ordinary sandwich");}
                    else if(s.currentMenu().getSlot(6).isActive()||s.currentMenu().getSlot(7).isActive()||meal.bonuses().stream().anyMatch(b->!b.pair()||(phase.equals("legendary")?b.effect()!=MealRules.Effect.FIREPOWER&&b.effect()!=MealRules.Effect.SWIM:b.effect().gun())))throw new IllegalStateException("Incorrect stew mode / selected recipes "+phase);
                    cursor(mc,s.uiLeft()+18,s.uiTop()+58);
                }else if(screen instanceof MilkDispenserScreen){if(!(mc.player.containerMenu instanceof MilkDispenser.DispenserMenu m)||m.data.get(0)!=5750)throw new IllegalStateException("Milk level synchronization");}
                else if(screen instanceof BeaconClient.ControlScreen s){if(s.tab!=2||s.fabricationButtons.size()!=11)throw new IllegalStateException("Full compact fabrication catalogue");cursor(mc,s.left+48,s.top+66);}
                else if(weapon){for(var child:screen.children())if(child instanceof Ui.UiButton b&&b.getMessage().getString().equals(Ui.plain("platform.upgrade_age"))){cursor(mc,b.getX()+8,b.getY()+8);break;}}
            }
            if(ticks==(stage==0?260:80)){
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"v6-"+phase+".png",mc.getMainRenderTarget(),message->{});
                com.mojang.logging.LogUtils.getLogger().info("V6_UI_SCREEN_PASS {}",phase);
            }
            if(ticks==(stage==0?300:100)){
                if(++stage==PHASES.length){
                    net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(),true,mc.level.registryAccess());var items=ArsenalBeacon.ARSENAL_TAB.get().getDisplayItems();long halls=items.stream().filter(s->s.getItem() instanceof KitchenBlock.KitchenItem&&((KitchenBlock.KitchenItem)s.getItem()).getBlock() instanceof MessHall.HallBlock).count();if(halls!=1||items.stream().noneMatch(s->s.is(ArsenalBeacon.MILK_DISPENSER_ITEM.get())))throw new IllegalStateException("Creative catalogue");
                    mc.player.connection.sendCommand("mess-hall-v6-ui done");com.mojang.logging.LogUtils.getLogger().info("V6_CLIENT_UI_PASS screens=11 creativeHalls={}",halls);done=true;
                }else{ticks=0;cursor(mc,5,5);mc.player.connection.sendCommand("mess-hall-v6-ui "+PHASES[stage]);}
            }
        }
        private static void cursor(net.minecraft.client.Minecraft mc,double x,double y){
            int[] px=new int[1],py=new int[1];org.lwjgl.glfw.GLFW.glfwGetWindowPos(mc.getWindow().getWindow(),px,py);org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());
            // The retained client forces AWT headless; the external X11 runner positions its pointer.
            com.mojang.logging.LogUtils.getLogger().info("V6_QA_CURSOR {} {}",px[0]+(int)(x*mc.getWindow().getGuiScale()),py[0]+(int)(y*mc.getWindow().getGuiScale()));
        }
    }
}
