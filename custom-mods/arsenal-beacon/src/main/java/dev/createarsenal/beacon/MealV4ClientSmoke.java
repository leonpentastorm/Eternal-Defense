package dev.createarsenal.beacon;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Isolated real client/server fixture; inert unless explicitly enabled at launch. */
public final class MealV4ClientSmoke {
    private ServerPlayer player;private int ticks,reloadTicks;private boolean started,done;
    private static final BlockPos ROOT=new BlockPos(20480,101,0);
    @SubscribeEvent public void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){player=p;ticks=0;}}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||player==null||done)return;
        if(++ticks==80){
            var l=player.serverLevel();UpgradeGameTests.arena(l,ROOT.below());for(var pos:BlockPos.betweenClosed(ROOT.offset(59,-1,-1),ROOT.offset(61,-1,1)))l.setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);var d=CampaignData.get(l);d.resetProgress();d.damage.clear();d.destroyedTurrets.clear();d.core=3;d.phase="preparation";d.beacon=ROOT.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.connection.teleport(ROOT.getX()+.5,ROOT.getY(),.5,0,0);player.getInventory().clearContent();var stack=MessHallV4GameTests.gun(player,"tacz:ak47");var gun=(com.tacz.guns.api.item.IGun)stack.getItem();gun.setCurrentAmmoCount(stack,0);gun.setBulletInBarrel(stack,false);
            PlayerMeals.eat(player,new MealData(false,java.util.List.of(new MealData.Bonus(MealRules.Effect.QUICK_HANDS,1)),MealRules.FIELD_TICKS));com.mojang.logging.LogUtils.getLogger().info("V4_CLIENT_FIXTURE_READY");
        }
        if(ticks<80)return;
        boolean reloading=IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading();
        if(reloading)started=true;if(!started)return;
        reloadTicks++;
        if(reloadTicks==10)player.connection.teleport(ROOT.getX()+60.5,ROOT.getY(),.5,0,0);
        if(reloadTicks==20)PlayerMeals.clear(player);
        if(reloadTicks==30)PlayerMeals.eat(player,new MealData(false,java.util.List.of(new MealData.Bonus(MealRules.Effect.QUICK_HANDS,1)),MealRules.FIELD_TICKS));
        if(reloadTicks==40)player.connection.teleport(ROOT.getX()+.5,ROOT.getY(),.5,0,0);
        if(!reloading&&reloadTicks>40){
            var stack=player.getMainHandItem();int ammo=((com.tacz.guns.api.item.IGun)stack.getItem()).getCurrentAmmoCount(stack);
            if(ammo<29)throw new IllegalStateException("Native client reload did not feed ammunition: "+ammo);
            com.mojang.logging.LogUtils.getLogger().info("V4_SERVER_RELOAD_PASS ticks={} ammo={}",reloadTicks,ammo);done=true;
        }
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ReloadSmokeClient {
        private static boolean joined,requested,seen,done;private static int diagnosticTicks;private static int ready,joinTicks,observations;
        private static final java.util.Set<Integer> rates=new java.util.HashSet<>();
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void observeReload(TickEvent.ClientTickEvent e){
            if(!Boolean.getBoolean("arsenal.v4ClientTests")||e.phase!=TickEvent.Phase.END||done)return;
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(++diagnosticTicks%100==0)com.mojang.logging.LogUtils.getLogger().info("V4_CLIENT_PROGRESS screen={} joined={} requested={} player={}",mc.screen==null?"none":mc.screen.getClass().getSimpleName(),joined,requested,mc.player!=null);
            if(!joined&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&++joinTicks==60){joined=true;var server=new net.minecraft.client.multiplayer.ServerData("V4 isolated test","127.0.0.1:25577",false);net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.ip),server,false);}
            var p=mc.player;if(p==null||mc.screen!=null)return;
            var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(p.getMainHandItem());if(gun==null)return;
            if(!requested&&p.hasEffect(MealEffects.HOME.get())&&++ready==50){requested=true;com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(p).reload();}
            boolean reloading=IGunOperator.fromLivingEntity(p).getSynReloadState().getStateType().isReloading();if(reloading)seen=true;
            var display=com.tacz.guns.api.TimelessAPI.getGunDisplay(p.getMainHandItem()).orElse(null);var state=display==null?null:display.getAnimationStateMachine();
            if(state!=null&&state.isInitialized()){
                var tracks=state.getContext().getTrackArray();var controller=state.getAnimationController();float expected=reloading?(float)MealGunBackend.reloadRate(p):1;
                for(int i=0;i<tracks.getTrackLineSize();i++)for(int pointer:tracks.getByIndex(i)){
                    var runner=controller.getAnimation(pointer);if(runner==null||!runner.getAnimation().name.startsWith("reload"))continue;
                    float actual=((com.github.leopoko.tacz_attributes.api.ISpeedModifiable)runner).tacz_attributes$getSpeedMultiplier();
                    if(Math.abs(actual-expected)>.001)throw new IllegalStateException("Reload clip speed mismatch: "+actual+" expected "+expected);
                    if(reloading&&runner.isRunning()){rates.add(Math.round(actual*100));observations++;}
                }
            }
            if(seen&&!reloading&&gun.getCurrentAmmoCount(p.getMainHandItem())>=29){
                if(!rates.containsAll(java.util.Set.of(100,110,120))||observations<15)throw new IllegalStateException("Missing live reload rates: "+rates+" samples="+observations);
                com.mojang.logging.LogUtils.getLogger().info("V4_CLIENT_RELOAD_PASS rates={} observations={} ammo={}",rates,observations,gun.getCurrentAmmoCount(p.getMainHandItem()));done=true;
            }
        }
    }
}
