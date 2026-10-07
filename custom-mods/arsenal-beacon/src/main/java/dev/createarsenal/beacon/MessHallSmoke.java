package dev.createarsenal.beacon;

/** Optional real-client kitchen checks, scheduled after the existing standalone UI smoke. */
public final class MessHallSmoke {
    private net.minecraft.server.level.ServerPlayer player;private int ticks,stage;
    @net.minecraftforge.eventbus.api.SubscribeEvent public void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p){player=p;ticks=stage=0;}}
    @net.minecraftforge.eventbus.api.SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||player==null)return;ticks++;
        var l=player.serverLevel();var root=new net.minecraft.core.BlockPos(0,100,0);
        if(stage==0&&ticks==560){
            player.closeContainer();UpgradeGameTests.arena(l,root.below());l.setDayTime(6000);var d=CampaignData.get(l);d.resetProgress();d.phase="preparation";d.core=3;d.beacon=root.west(4);d.setDirty();l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
            l.setBlock(root,ArsenalBeacon.MESS_HALL_IV.get().defaultBlockState(),3);l.setBlock(root.east(3),ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);
            l.setBlock(root.west(9),ArsenalBeacon.MESS_HALL_I.get().defaultBlockState(),3);l.setBlock(root.west(6),ArsenalBeacon.MESS_HALL_II.get().defaultBlockState(),3);l.setBlock(root.east(6),ArsenalBeacon.MESS_HALL_III.get().defaultBlockState(),3);
            var hall=(MessHall.HallEntity)l.getBlockEntity(root);hall.ingredients.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,16));hall.ingredients.setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF,16));hall.ingredients.setItem(2,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE,16));hall.discover();
            player.teleportTo(l,1.5,100,-2.5,0,15);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL));
            net.minecraftforge.network.NetworkHooks.openScreen(player,hall,b->{b.writeBlockPos(root);b.writeVarInt(4);});stage=1;
        }else if(stage==1&&player.containerMenu instanceof MessHallMenu menu&&menu.stew&&menu.ingredients.getItem(0).is(net.minecraft.world.item.Items.BREAD)){menu.ingredients.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PUFFERFISH,16));menu.broadcastChanges();}
        else if(stage==1&&PlayerMeals.get(l).players.containsKey(player.getUUID())){stage=2;ticks=0;}
        else if(stage==2&&ticks==100){player.teleportTo(l,30.5,100,-2.5,0,15);stage=3;ticks=0;}
        else if(stage==3&&ticks==100){player.teleportTo(l,1.5,100,-2.5,0,15);stage=4;}
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class KitchenSmokeClient {
        private static int stage,ticks;
        private static boolean homeEnhanced(net.minecraft.client.Minecraft mc){return mc.player.hasEffect(MealEffects.HOME.get());}
        private static int mealEffects(net.minecraft.client.Minecraft mc){int n=0;for(var e:MealRules.Effect.values())if(mc.player.hasEffect(MealEffects.of(e)))n++;return n;}
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
            if(!Boolean.getBoolean("arsenal.kitchenSmoke")||e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
            if(stage==0&&mc.screen instanceof MessHallScreen screen&&MealData.load(screen.getMenu().view.getCompound("Meal"))!=null){if(++ticks==20)com.mojang.logging.LogUtils.getLogger().info("MESS_HALL_SANDWICH_UI_PASS");if(ticks==60){mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId,1);stage=1;ticks=0;}}
            else if(stage==1&&mc.screen instanceof MessHallScreen screen&&screen.getMenu().view.getBoolean("StewMode")){if(++ticks==20)com.mojang.logging.LogUtils.getLogger().info("MESS_HALL_STEW_UI_PASS");if(ticks==60){mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId,2);stage=2;ticks=0;}}
            else if(stage==2&&mc.screen instanceof MessHallScreen screen){var pots=screen.getMenu().view.getList("Pots",net.minecraft.nbt.Tag.TAG_COMPOUND);if(!pots.isEmpty()&&pots.getCompound(0).getInt("Servings")==16&&++ticks==40){com.mojang.logging.LogUtils.getLogger().info("MESS_HALL_STOCKED_UI_PASS");mc.player.closeContainer();stage=3;ticks=0;}}
            else if(stage==3&&mc.screen==null&&++ticks==20){var pos=new net.minecraft.core.BlockPos(3,100,0);mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.NORTH,pos,false));stage=4;ticks=0;}
            else if(stage==4&&homeEnhanced(mc)&&mealEffects(mc)==3&&++ticks==40){com.mojang.logging.LogUtils.getLogger().info("MESS_HALL_HOME_EFFECTS_PASS");stage=5;ticks=0;}
            else if(stage==5&&mealEffects(mc)>0&&!homeEnhanced(mc)&&++ticks==30){com.mojang.logging.LogUtils.getLogger().info("MESS_HALL_FIELD_EFFECTS_PASS");stage=6;ticks=0;}
            else if(stage==6&&homeEnhanced(mc)&&mealEffects(mc)>0&&++ticks==30){com.mojang.logging.LogUtils.getLogger().info("MESS_HALL_CLIENT_SMOKE_PASS");stage=7;}
        }
    }
}
