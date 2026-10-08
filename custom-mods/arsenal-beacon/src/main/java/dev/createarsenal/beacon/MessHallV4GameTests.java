package dev.createarsenal.beacon;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.*;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.*;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Opt-in production Forge checks. Run one case at a time because campaign state is shared. */
@net.minecraftforge.gametest.GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class MessHallV4GameTests {
    private record Fixture(ServerLevel level,BlockPos floor,BlockPos root,ServerPlayer player,CampaignData campaign){}
    private static Fixture fixture(GameTestHelper h){
        var l=h.getLevel();var floor=new BlockPos(16384,100,0);UpgradeGameTests.arena(l,floor);var root=floor.above();
        var d=CampaignData.get(l);d.resetProgress();d.damage.clear();d.destroyedTurrets.clear();d.core=3;d.phase="preparation";d.beacon=root.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        var p=UpgradeGameTests.player(h,"meal-v4");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(root.getCenter());PlayerMeals.clear(p);
        return new Fixture(l,floor,root,p,d);
    }
    private static void clean(Fixture f){PlayerMeals.clear(f.player);f.campaign.phase="unplaced";f.campaign.raiders.clear();UpgradeGameTests.release(f.level,f.floor);}
    private static MealData meal(MealRules.Effect... effects){return new MealData(true,Arrays.stream(effects).map(e->new MealData.Bonus(e,1)).toList(),MealRules.FIELD_TICKS);}
    private static LivingEntity target(Fixture f,boolean raid){
        var mob=EntityType.COW.create(f.level);mob.setNoAi(true);mob.moveTo(f.root.getCenter());mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);
        if(raid){f.campaign.raiders.add(mob.getUUID());mob.getPersistentData().putBoolean("arsenalRaider",true);}return mob;
    }
    private static DamageSource source(Fixture f,ResourceKey<DamageType> type){return new DamageSource(f.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));}
    private static float hit(LivingEntity target,DamageSource source){target.setHealth(1000);target.invulnerableTime=0;target.hurt(source,20);return 1000-target.getHealth();}
    private static void close(GameTestHelper h,double actual,double expected,String label){h.assertTrue(Math.abs(actual-expected)<.001,label+": expected="+expected+", actual="+actual);}

    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v4TrapClassesWavesAndPersistence(GameTestHelper h){
        var f=fixture(h);try{
            var mob=target(f,true);f.campaign.phase="raid";
            var cases=Map.ofEntries(Map.entry(RaidAdaptation.Kind.FIRE,DamageTypes.LAVA),Map.entry(RaidAdaptation.Kind.FALL,DamageTypes.FALL),Map.entry(RaidAdaptation.Kind.DROWNING,DamageTypes.DROWN),Map.entry(RaidAdaptation.Kind.SUFFOCATION,DamageTypes.IN_WALL),Map.entry(RaidAdaptation.Kind.SPIKES,DamageTypes.CACTUS),Map.entry(RaidAdaptation.Kind.CRUSHING,DamageTypes.CRAMMING),Map.entry(RaidAdaptation.Kind.FREEZING,DamageTypes.FREEZE),Map.entry(RaidAdaptation.Kind.EXPLOSION,DamageTypes.EXPLOSION),Map.entry(RaidAdaptation.Kind.PROJECTILE,DamageTypes.ARROW),Map.entry(RaidAdaptation.Kind.MAGIC,DamageTypes.MAGIC),Map.entry(RaidAdaptation.Kind.LIGHTNING,DamageTypes.LIGHTNING_BOLT));
            for(var entry:cases.entrySet()){
                RaidAdaptation.begin(f.campaign);f.campaign.wave=2;var damage=source(f,entry.getValue());
                close(h,hit(mob,damage),20,entry.getKey()+" first damaging wave");close(h,hit(mob,damage),20,"Same-wave reuse stays effective");
                h.assertTrue(f.campaign.trapFirstWave.get(entry.getKey())==2,"Correct damage class learned: "+entry.getKey());
                for(int step=1;step<=4;step++){f.campaign.wave=2+step;RaidAdaptation.nextWave(f.level,f.campaign);close(h,hit(mob,damage),20*(1-.25*step),entry.getKey()+" resistance step "+step);}
                var loaded=CampaignData.load(f.campaign.save(new CompoundTag()));h.assertTrue(loaded.trapFirstWave.equals(f.campaign.trapFirstWave)&&loaded.wave==6,"Raid adaptation survives serialization");
                close(h,hit(target(f,false),damage),20,"Ambient mobs are unaffected");
            }
            RaidAdaptation.begin(f.campaign);f.campaign.wave=1;hit(mob,source(f,DamageTypes.LAVA));f.campaign.wave=3;close(h,hit(mob,source(f,DamageTypes.FALL)),20,"A newly introduced trap gets its own first wave");
            RaidAdaptation.begin(f.campaign);close(h,hit(mob,source(f,DamageTypes.LAVA)),20,"New raid forgets resistance");
            RaidAdaptation.begin(f.campaign);hit(mob,source(f,DamageTypes.STALAGMITE));h.assertTrue(f.campaign.trapFirstWave.containsKey(RaidAdaptation.Kind.SPIKES),"Pointed dripstone belongs to spikes even though vanilla also tags it as fall damage");
        }finally{clean(f);}h.succeed();
    }
    private static final class Shot extends Arrow {
        Shot(ServerLevel level){super(level,0,101,0);setBaseDamage(20);setDeltaMovement(1,0,0);}
        void impact(Entity target){super.onHitEntity(new EntityHitResult(target));}
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v4WeaponsRemainEffectiveAfterTrapImmunity(GameTestHelper h){
        var f=fixture(h);try{
            var mob=target(f,true);f.campaign.phase="raid";f.campaign.wave=5;for(var kind:RaidAdaptation.Kind.values())f.campaign.trapFirstWave.put(kind,1);
            var arrow=new Shot(f.level);arrow.setOwner(f.player);arrow.setSecondsOnFire(5);arrow.impact(mob);close(h,1000-mob.getHealth(),20,"Actual owned flame-arrow impact bypasses trap resistance");
            h.assertTrue(mob.isOnFire(),"Flame arrow ignites a raider");close(h,hit(mob,source(f,DamageTypes.ON_FIRE)),20,"Weapon burn ticks bypass learned fire traps");close(h,hit(mob,source(f,DamageTypes.LAVA)),0,"Direct lava remains a trap during weapon burning");
            mob.setHealth(1000);mob.invulnerableTime=0;var turret=new Shot(f.level);turret.getPersistentData().putBoolean("arsenalDefensiveWeapon",true);turret.impact(mob);close(h,1000-mob.getHealth(),20,"Marked ownerless turret/support shot is a weapon");
            mob.setHealth(1000);mob.invulnerableTime=0;new Shot(f.level).impact(mob);close(h,1000-mob.getHealth(),0,"Ownerless unmarked dispenser shot is a trap");
            mob.addEffect(new MobEffectInstance(MobEffects.POISON,200),f.player);close(h,hit(mob,source(f,DamageTypes.MAGIC)),20,"Weapon poison retains attribution");mob.removeEffect(MobEffects.POISON);mob.addEffect(new MobEffectInstance(MobEffects.POISON,200));close(h,hit(mob,source(f,DamageTypes.MAGIC)),0,"Ownerless potion trap adapts");
            mob.setHealth(1000);mob.invulnerableTime=0;RaidAdaptation.weaponDamage(mob,source(f,DamageTypes.LIGHTNING_BOLT),20);close(h,1000-mob.getHealth(),20,"Narukami weapon boundary preserves damage");
            RaidAdaptation.begin(f.campaign);close(h,hit(mob,f.level.damageSources().playerAttack(f.player)),20,"Player attacks remain weapons");h.assertTrue(f.campaign.trapFirstWave.isEmpty(),"Weapon hits never teach resistance");
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=140)
    public static void v4SunlightProtectionKeepsCombatFire(GameTestHelper h){
        var f=fixture(h);f.level.addNewPlayer(f.player);long day=f.level.getDayTime();f.level.setDayTime(6000);f.level.setWeatherParameters(6000,0,false,false);
        var raid=EntityType.ZOMBIE.create(f.level);var ambient=EntityType.ZOMBIE.create(f.level);
        for(var zombie:List.of(raid,ambient)){zombie.setNoAi(true);zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);zombie.setHealth(1000);zombie.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);zombie.moveTo(f.root.east(zombie==raid?2:5).getCenter());f.level.addFreshEntity(zombie);}
        raid.getPersistentData().putBoolean("arsenalRaider",true);
        h.runAtTickTime(40,()->{h.assertTrue(ambient.isOnFire(),"Control zombie ignites in daylight; ticks="+ambient.tickCount+", sky="+f.level.canSeeSky(ambient.blockPosition())+", day="+f.level.isDay()+", brightness="+ambient.getLightLevelDependentMagicValue());h.assertTrue(!raid.isOnFire(),"Raider is protected only from daylight ignition");raid.setSecondsOnFire(5);});
        h.runAtTickTime(90,()->{try{h.assertTrue(raid.isOnFire()&&raid.getHealth()<1000,"Combat fire remains lit and damages the raider over real server ticks");}finally{raid.discard();ambient.discard();f.level.removePlayerImmediately(f.player,Entity.RemovalReason.DISCARDED);f.level.setDayTime(day);clean(f);}h.succeed();});
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v4BackendModifiersPreserveOtherOwners(GameTestHelper h){
        var f=fixture(h);var damage=f.player.getAttribute(MealGunBackend.damage());var reload=f.player.getAttribute(MealGunBackend.reload());var external=UUID.fromString("023055ba-e2c8-4c39-a281-f456b4ff13d9");
        h.assertTrue(damage!=null&&reload!=null,"Backend attributes are installed on real server players");double oldDamage=damage.getBaseValue(),oldReload=reload.getBaseValue();
        try{
            damage.setBaseValue(1.3);reload.setBaseValue(1.25);damage.addTransientModifier(new AttributeModifier(external,"External fixture",.4,AttributeModifier.Operation.MULTIPLY_TOTAL));
            PlayerMeals.eat(f.player,meal(MealRules.Effect.FIREPOWER,MealRules.Effect.QUICK_HANDS));close(h,damage.getValue(),1.3*1.4*1.16,"One home Firepower factor retains external values");close(h,reload.getValue(),1.25*1.2,"One home reload factor");
            int ticks=PlayerMeals.get(f.level).players.get(f.player.getUUID()).remaining;for(int i=0;i<20;i++)PlayerMeals.tick(f.player);h.assertTrue(PlayerMeals.get(f.level).players.get(f.player.getUUID()).remaining==ticks,"Gun attributes share frozen home timer");
            f.player.setPos(f.root.east(60).getCenter());PlayerMeals.tick(f.player);close(h,damage.getValue(),1.3*1.4*1.08,"Field Firepower preserves external modifier");close(h,reload.getValue(),1.25*1.1,"Field reload factor");
            var other=UpgradeGameTests.player(h,"meal-v4-other");close(h,other.getAttributeValue(MealGunBackend.damage()),1,"Other diner has no leaked bonus");
            PlayerMeals.eat(f.player,meal(MealRules.Effect.QUICK_HANDS));close(h,damage.getValue(),1.3*1.4,"Replacement removes only former Firepower");
            PlayerMeals.get(f.level).players.get(f.player.getUUID()).remaining=1;PlayerMeals.tick(f.player);close(h,reload.getValue(),1.25,"Expiry removes only meal reload modifier");h.assertTrue(damage.getModifier(external)!=null,"External ownership survives replacement and expiry");
        }finally{PlayerMeals.clear(f.player);damage.removeModifier(external);damage.setBaseValue(oldDamage);reload.setBaseValue(oldReload);clean(f);}h.succeed();
    }
    static ItemStack gun(ServerPlayer player,String id){
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:modern_kinetic_gun")));var item=(AbstractGunItem)stack.getItem();item.setGunId(stack,new ResourceLocation(id));player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var data=TimelessAPI.getCommonGunIndex(new ResourceLocation(id)).orElseThrow().getGunData();var ammo=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:ammo")),64);com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(ammo).setAmmoId(ammo,data.getAmmoId());player.getInventory().setItem(1,ammo);var cache=new AttachmentCacheProperty();cache.eval(stack,data);IGunOperator.fromLivingEntity(player).updateCacheProperty(cache);return stack;
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v4NativeReloadRateTransitions(GameTestHelper h){
        var f=fixture(h);try{
            f.player.setPos(f.root.east(60).getCenter());PlayerMeals.eat(f.player,meal(MealRules.Effect.QUICK_HANDS));var stack=gun(f.player,"tacz:ak47");var item=(AbstractGunItem)stack.getItem();var data=IGunOperator.fromLivingEntity(f.player).getDataHolder();var helper=new LivingEntityReload(f.player,data,null,null);
            data.currentGunItem=()->stack;data.reloadStateType=ReloadState.StateType.EMPTY_RELOAD_FEEDING;item.setCurrentAmmoCount(stack,0);item.setBulletInBarrel(stack,false);data.reloadTimestamp=System.currentTimeMillis();item.startReload(data,stack,f.player);data.reloadTimestamp-=400;
            h.assertTrue(helper.tickReloadState().getStateType().isReloading(),"Native AK reload initialized");
            for(boolean home:new boolean[]{true,false,true}){
                double previous=MealGunBackend.reloadRate(f.player);f.player.setPos((home?f.root:f.root.east(60)).getCenter());PlayerMeals.tick(f.player);double rate=MealGunBackend.reloadRate(f.player);long now=System.currentTimeMillis();double progress=(now-data.reloadTimestamp)*previous;
                h.assertTrue(helper.tickReloadState().getStateType().isReloading(),"Rate change does not prematurely finish native reload");double after=(System.currentTimeMillis()-data.reloadTimestamp)*rate;h.assertTrue(Math.abs(after-progress)<35,"Native accumulated progress is continuous: before="+progress+" after="+after);
            }
            double previous=MealGunBackend.reloadRate(f.player),progress=(System.currentTimeMillis()-data.reloadTimestamp)*previous;PlayerMeals.clear(f.player);helper.tickReloadState();h.assertTrue(Math.abs((System.currentTimeMillis()-data.reloadTimestamp)-progress)<35,"Meal removal returns to neutral without a jump");
            data.reloadTimestamp=-1;helper.tickReloadState();h.assertTrue(data.reloadTimestamp==-1,"Inactive reload retains native sentinel");
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v4OptionalGrenadeOwnerAndRadius(GameTestHelper h)throws Exception{
        var f=fixture(h);var capture=new MessHallV2GameTests.RadiusCapture();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(capture);
        try{
            h.assertTrue(MealOptionalCompat.lrtacticalSupported(),"This optional case requires LesRaisins 0.4.3");
            var type=Class.forName("me.xjqsh.lrtactical.entity.GrenadeEntity");var grenade=(net.minecraft.world.entity.projectile.Projectile)type.getConstructor(LivingEntity.class,net.minecraft.world.level.Level.class,int.class).newInstance(f.player,f.level,100);
            var radius=type.getMethod("getRadius");var damage=type.getMethod("getDamage");var detonate=type.getMethod("onDeath",net.minecraft.world.phys.HitResult.class);
            float raw=((Number)radius.invoke(grenade)).floatValue();double power=((Number)damage.invoke(grenade)).doubleValue();
            detonate.invoke(grenade,(Object)null);close(h,capture.radius,raw,"Unbuffed shared grenade radius");PlayerMeals.eat(f.player,meal(MealRules.Effect.DEMOLITION));detonate.invoke(grenade,(Object)null);close(h,capture.radius,raw*1.2,"One home Demolition factor");
            f.player.setPos(f.root.east(60).getCenter());PlayerMeals.tick(f.player);detonate.invoke(grenade,(Object)null);close(h,capture.radius,raw*1.1,"Detonation reads current field contribution");
            var other=UpgradeGameTests.player(h,"v4-grenade-other");grenade.setOwner(other);detonate.invoke(grenade,(Object)null);close(h,capture.radius,raw,"Actual unbuffed owner does not inherit first player's meal");
            close(h,((Number)radius.invoke(grenade)).floatValue(),raw,"Stored radius was never changed");close(h,((Number)damage.invoke(grenade)).doubleValue(),power,"Damage power is unchanged");grenade.discard();
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(capture);clean(f);}h.succeed();
    }
    public static final class Runner {
        private MultipleTestTracker tracker;private String name;
        @SubscribeEvent public void commands(net.minecraftforge.event.RegisterCommandsEvent e){
            GameTestRegistry.register(MessHallV4GameTests.class);GameTestRegistry.register(MessHallV3GameTests.class);
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-v4-test").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.argument("case",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{
                if(tracker!=null)throw new IllegalStateException("A v4 test is already running");name=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"case").toLowerCase(Locale.ROOT);
                var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getTestName().toLowerCase(Locale.ROOT).endsWith(name)).toList();if(tests.size()!=1){com.mojang.logging.LogUtils.getLogger().error("V4_TEST_FAIL {} matched {} registered={}",name,tests.size(),GameTestRegistry.getAllTestFunctions().stream().map(TestFunction::getTestName).toList());throw new IllegalArgumentException("Expected exactly one test: "+name);}
                tracker=new MultipleTestTracker(GameTestRunner.runTests(tests,new BlockPos(0,180,0),Rotation.NONE,c.getSource().getLevel(),GameTestTicker.SINGLETON,1));return 1;
            })));
        }
        @SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
            if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;GameTestTicker.SINGLETON.tick();if(!tracker.isDone())return;
            for(var t:tracker.getFailedRequired())com.mojang.logging.LogUtils.getLogger().error("V4_TEST_FAIL "+name,t.getError());
            if(tracker.getFailedRequiredCount()==0)com.mojang.logging.LogUtils.getLogger().info("V4_TEST_PASS {}",name);tracker=null;
        }
    }
}
