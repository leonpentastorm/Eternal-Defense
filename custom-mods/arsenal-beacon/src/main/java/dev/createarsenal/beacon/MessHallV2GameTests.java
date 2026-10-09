package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Opt-in real TaCZ comparisons: -Darsenal.messHallTests=true, /mess-hall-v2-test. */
@net.minecraftforge.gametest.GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class MessHallV2GameTests {
    private record Fixture(ServerLevel level,BlockPos floor,BlockPos root,MessHall.HallEntity hall,CookPot.PotEntity pot,ServerPlayer player){}
    private static Fixture fixture(GameTestHelper h,int mk,int offset){
        var l=h.getLevel();var floor=new BlockPos(8192+offset*64,100,0);UpgradeGameTests.arena(l,floor);var root=floor.above();
        var d=CampaignData.get(l);d.resetProgress();d.core=3;d.phase="preparation";d.beacon=root.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        var block=switch(mk){case 2->ArsenalBeacon.MESS_HALL_II;case 3->ArsenalBeacon.MESS_HALL_III;case 4->ArsenalBeacon.MESS_HALL_IV;default->ArsenalBeacon.MESS_HALL_I;};l.setBlock(root,block.get().defaultBlockState(),3);
        l.setBlock(root.east(3),ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);var pot=(CookPot.PotEntity)l.getBlockEntity(root.east(3));pot.connect();
        var p=UpgradeGameTests.player(h,"meal-v2-"+offset);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(root.getX()+.5,root.getY(),root.getZ()-1);
        return new Fixture(l,floor,root,(MessHall.HallEntity)l.getBlockEntity(root),pot,p);
    }
    private static void clean(Fixture f){PlayerMeals.clear(f.player);f.player.closeContainer();UpgradeGameTests.release(f.level,f.floor);CampaignData.get(f.level).phase="unplaced";}
    private static MealData meal(MealRules.Effect... effects){return new MealData(true,Arrays.stream(effects).map(e->new MealData.Bonus(e,1)).toList(),MealRules.FIELD_TICKS);}
    private static void batchFood(Fixture f,int count){f.hall.ingredients.clearContent();f.hall.ingredients.setItem(0,new ItemStack(Items.COOKED_BEEF,count));f.hall.ingredients.setItem(1,new ItemStack(Items.HONEY_BOTTLE,count));f.hall.ingredients.setItem(2,new ItemStack(Items.PUFFERFISH,count));}
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v2TierBatchCostsContainersAndComposition(GameTestHelper h){
        MealData composition=null;
        for(int mk=4;mk<=4;mk++){var f=fixture(h,mk,mk);try{ // 0.0.11: six food slots exist only at Mk IV, and stew needs Mk II
            batchFood(f,16);var preview=IngredientTraits.compose(f.hall.ingredients,true);h.assertTrue(preview.meal()!=null,"Bundled vanilla foods compose a meal");if(composition==null)composition=preview.meal();else h.assertTrue(composition.equals(preview.meal()),"Tier and quantity never increase composition strength");
            h.assertTrue(preview.meal().bonuses().stream().map(MealData.Bonus::effect).toList().equals(List.of(MealRules.Effect.MIGHT,MealRules.Effect.RECOVERY,MealRules.Effect.SWIM)),"Ordinary defaults preserve all three food bonuses: "+preview.meal().bonuses());
            var menu=new MessHallMenu(mk,f.player.getInventory(),f.hall.ingredients,f.root,mk,f.hall);menu.clickMenuButton(f.player,1);var batch=IngredientTraits.batch(f.hall.ingredients,preview,true,mk);
            h.assertTrue(menu.prepare()&&f.pot.servings==4*mk,"Tier creates its configured servings");int left=0;for(int i=0;i<6;i++)left+=f.hall.ingredients.getItem(i).getCount();h.assertTrue(left==48-MealRules.tier(mk).ingredients(),"Exact 4/7/10/12 batch ingredient cost");
            h.assertTrue(f.player.getInventory().countItem(Items.GLASS_BOTTLE)==batch.spent()[1],"Every consumed honey bottle returns a container");
            batchFood(f,1);h.assertTrue(IngredientTraits.compose(f.hall.ingredients,true).meal().equals(composition),"Stack size does not change strength");
        }finally{clean(f);}}
        h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v2FailedAndCompetingCooksDoNotConsumeFood(GameTestHelper h){
        var f=fixture(h,4,5);try{
            batchFood(f,1);var menu=new MessHallMenu(1,f.player.getInventory(),f.hall.ingredients,f.root,4,f.hall);menu.clickMenuButton(f.player,1);var before=f.hall.saveWithoutMetadata();
            h.assertTrue(!menu.prepare()&&before.equals(f.hall.saveWithoutMetadata())&&f.pot.empty(),"Insufficient food leaves all inputs and pot unchanged");
            batchFood(f,4);var other=UpgradeGameTests.player(h,"v2-competing-cook");other.setPos(f.player.position());var second=new MessHallMenu(2,other.getInventory(),f.hall.ingredients,f.root,4,f.hall);second.clickMenuButton(other,1);
            h.assertTrue(menu.prepare()&&!second.prepare()&&f.pot.servings==16,"Two cooks serialize the last exact batch");
            batchFood(f,1);f.hall.ingredients.setItem(3,new ItemStack(Items.COOKED_BEEF,16));h.assertTrue(IngredientTraits.compose(f.hall.ingredients,true).meal().equals(f.pot.stew),"Duplicate slots do not increase scores");
            f.hall.ingredients.clearContent();f.hall.ingredients.setItem(0,new ItemStack(Items.COOKED_BEEF,32));f.hall.ingredients.setItem(1,new ItemStack(Items.COOKED_BEEF,32));h.assertTrue(IngredientTraits.compose(f.hall.ingredients,true).meal()==null,"Split stacks are not two ingredient types");
            f.hall.ingredients.clearContent();f.hall.ingredients.setItem(0,new ItemStack(Items.BREAD,64));f.hall.ingredients.setItem(1,new ItemStack(Items.COOKED_BEEF,64));f.hall.ingredients.setItem(2,new ItemStack(Items.HONEY_BOTTLE,16));f.hall.ingredients.setItem(7,new ItemStack(Items.BREAD,1));menu.clickMenuButton(f.player,0);
            h.assertTrue(menu.prepare()&&f.hall.ingredients.getItem(0).getCount()==63&&f.hall.ingredients.getItem(1).getCount()==63&&f.hall.ingredients.getItem(2).getCount()==15&&f.hall.ingredients.getItem(6).getCount()==1,"One sandwich consumes separate base bread plus its three chosen fillings");
            for(var entry:Map.of(Items.COOKED_PORKCHOP,MealRules.Effect.FIREPOWER,Items.GOLDEN_CARROT,MealRules.Effect.RECOIL_CONTROL).entrySet()){f.hall.ingredients.clearContent();f.hall.ingredients.setItem(0,new ItemStack(Items.BREAD));f.hall.ingredients.setItem(1,new ItemStack(entry.getKey()));h.assertTrue(IngredientTraits.compose(f.hall.ingredients,false,1,EnumSet.of(entry.getValue())).meal().bonuses().get(0).effect()==entry.getValue(),"Two-group recipes replace direct ingredient gun bonuses");}
        }finally{clean(f);}h.succeed();
    }
    private static ItemStack gun(String id)throws Exception{
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:modern_kinetic_gun")));stack.getItem().getClass().getMethod("setGunId",ItemStack.class,ResourceLocation.class).invoke(stack.getItem(),stack,new ResourceLocation(id));return stack;
    }
    private static Object index(String id)throws Exception{return ((Optional<?>)Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex",ResourceLocation.class).invoke(null,new ResourceLocation(id))).orElseThrow();}
    private static Entity bullet(Fixture f,String id)throws Exception{
        var gun=gun(id);f.player.setItemInHand(InteractionHand.MAIN_HAND,gun);var index=index(id);var data=index.getClass().getMethod("getGunData").invoke(index);var bulletData=index.getClass().getMethod("getBulletData").invoke(index);
        var cacheClass=Class.forName("com.tacz.guns.resource.modifier.AttachmentCacheProperty");var cache=cacheClass.getConstructor().newInstance();cacheClass.getMethod("eval",ItemStack.class,data.getClass()).invoke(cache,gun,data);
        var api=Class.forName("com.tacz.guns.api.entity.IGunOperator");var operator=api.getMethod("fromLivingEntity",LivingEntity.class).invoke(null,f.player);api.getMethod("updateCacheProperty",cacheClass).invoke(operator,cache);
        var cls=Class.forName("com.tacz.guns.entity.EntityKineticBullet");var ctor=Arrays.stream(cls.getConstructors()).filter(c->c.getParameterCount()==8).findFirst().orElseThrow();return (Entity)ctor.newInstance(f.level,f.player,gun,data.getClass().getMethod("getAmmoId").invoke(data),new ResourceLocation(id),false,data,bulletData);
    }
    private static LivingEntity target(Fixture f){var cow=EntityType.COW.create(f.level);cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);cow.setHealth(1000);cow.moveTo(f.root.east(1),0,0);f.level.addFreshEntity(cow);return cow;}
    private static float hit(Fixture f,LivingEntity target)throws Exception{
        var bullet=bullet(f,"tacz:ak47");var cls=bullet.getClass();var resultClass=Class.forName("com.tacz.guns.entity.EntityKineticBullet$EntityResult");var hitClass=Class.forName("com.tacz.guns.util.TacHitResult");var method=cls.getDeclaredMethod("onHitEntity",hitClass,Vec3.class,Vec3.class);method.setAccessible(true);
        var body=target.position().add(0,.8,0);bullet.setPos(body.add(-10,0,0));var result=resultClass.getConstructor(Entity.class,Vec3.class,boolean.class).newInstance(target,body,false);target.invulnerableTime=0;target.setHealth(1000);method.invoke(bullet,hitClass.getConstructor(resultClass).newInstance(result),body.add(-1,0,0),body);bullet.discard();return 1000-target.getHealth();
    }
    public static final class BashCapture {
        LivingEntity target;final List<Float> strengths=new ArrayList<>();
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void knockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent e){if(e.getEntity()==target)strengths.add(e.getStrength());}
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v2ActualGunHitsBashAndOrdinaryMelee(GameTestHelper h)throws Exception{
        var f=fixture(h,1,6);var target=target(f);var capture=new BashCapture();capture.target=target;MinecraftForge.EVENT_BUS.register(capture);try{
            float base=hit(f,target);PlayerMeals.eat(f.player,meal(MealRules.Effect.FIREPOWER));float home=hit(f,target);h.assertTrue(base>0&&Math.abs(home/base-(1+MealRules.Effect.FIREPOWER.amount*2))<.002,"Identical native body hit gains the doubled home bonus");
            f.player.setPos(f.root.getX()+30,f.root.getY(),f.root.getZ());PlayerMeals.tick(f.player);float field=hit(f,target);h.assertTrue(Math.abs(field/base-(1+MealRules.Effect.FIREPOWER.amount))<.002,"Identical native body hit gains the field bonus");
            var bash=gun("tacz:ak47").getItem().getClass().getDeclaredMethod("doPerLivingHurt",LivingEntity.class,LivingEntity.class,float.class,float.class,List.class);bash.setAccessible(true);PlayerMeals.clear(f.player);
            target.setHealth(1000);target.invulnerableTime=0;target.setDeltaMovement(Vec3.ZERO);capture.strengths.clear();bash.invoke(null,f.player,target,1f,8f,List.of());float baseDamage=1000-target.getHealth();float baseKnockback=capture.strengths.get(0);
            PlayerMeals.eat(f.player,meal(MealRules.Effect.BRAWLER,MealRules.Effect.HEAVY_HAND));target.setHealth(1000);target.invulnerableTime=0;target.setDeltaMovement(Vec3.ZERO);capture.strengths.clear();bash.invoke(null,f.player,target,1f,8f,List.of());
            h.assertTrue(Math.abs((1000-target.getHealth())/baseDamage-(1+MealRules.Effect.BRAWLER.amount))<.002&&Math.abs(capture.strengths.get(0)/baseKnockback-(1+MealRules.Effect.HEAVY_HAND.amount))<.002,"Native gun bash gains only its damage and knockback bonuses");
            target.setHealth(1000);target.invulnerableTime=0;target.setDeltaMovement(Vec3.ZERO);capture.strengths.clear();target.hurt(f.level.damageSources().playerAttack(f.player),8);target.knockback(1,0,1);h.assertTrue(1000-target.getHealth()==8&&capture.strengths.get(capture.strengths.size()-1)==1,"Ordinary melee damage and knockback stay unchanged while holding a gun");
            com.mojang.logging.LogUtils.getLogger().info("MEAL_V2_COMBAT_PASS bullet base={} field={} home={} bash={} knockback={}",base,field,home,baseDamage,baseKnockback);
        }finally{MinecraftForge.EVENT_BUS.unregister(capture);target.discard();clean(f);}h.succeed();
    }
    public static final class RadiusCapture {
        float radius;int seen,vanilla;
        @SubscribeEvent public void start(ExplosionEvent.Start e){try{var field=e.getExplosion().getClass().getDeclaredField("radius");field.setAccessible(true);radius=field.getFloat(e.getExplosion());seen++;e.setCanceled(true);}catch(NoSuchFieldException ignored){try{var field=Arrays.stream(net.minecraft.world.level.Explosion.class.getDeclaredFields()).filter(f->f.getType()==float.class).findFirst().orElseThrow();field.setAccessible(true);radius=field.getFloat(e.getExplosion());vanilla++;e.setCanceled(true);}catch(IllegalAccessException ex){throw new IllegalStateException(ex);}}catch(IllegalAccessException ex){throw new IllegalStateException(ex);}}
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v2NativeLauncherRadiusAndUnrelatedExplosions(GameTestHelper h)throws Exception{
        var f=fixture(h,1,7);var capture=new RadiusCapture();MinecraftForge.EVENT_BUS.register(capture);try{
            var util=Class.forName("com.tacz.guns.util.ExplodeUtil").getMethod("createExplosion",Entity.class,Entity.class,float.class,float.class,boolean.class,boolean.class,Vec3.class);var grenade=bullet(f,"tacz:m320");
            util.invoke(null,f.player,grenade,20f,4f,false,false,f.root.getCenter());h.assertTrue(capture.radius==4,"Native unbuffed M320 radius");PlayerMeals.eat(f.player,meal(MealRules.Effect.DEMOLITION));util.invoke(null,f.player,grenade,20f,4f,false,false,f.root.getCenter());h.assertTrue(Math.abs(capture.radius-4*(1+MealRules.Effect.DEMOLITION.amount*2))<.001,"Native M320 radius gains the doubled home bonus");
            f.player.setPos(f.root.getX()+30,f.root.getY(),f.root.getZ());PlayerMeals.tick(f.player);util.invoke(null,f.player,grenade,20f,4f,false,false,f.root.getCenter());h.assertTrue(Math.abs(capture.radius-4*(1+MealRules.Effect.DEMOLITION.amount))<.001,"Native M320 radius gains the field bonus");
            var rifle=bullet(f,"tacz:ak47");util.invoke(null,f.player,rifle,20f,4f,false,false,f.root.getCenter());h.assertTrue(capture.radius==4,"Nonexplosive rifle projectile stays unchanged even through the explosion utility");
            var explosive=rifle.getClass().getDeclaredField("explosion");explosive.setAccessible(true);explosive.setBoolean(rifle,true);util.invoke(null,f.player,rifle,20f,4f,false,false,f.root.getCenter());h.assertTrue(Math.abs(capture.radius-4*(1+MealRules.Effect.DEMOLITION.amount))<.001,"Explosive rifle behavior gains radius without an RPG category");
            var other=UpgradeGameTests.player(h,"v4-explosion-other");util.invoke(null,other,rifle,20f,4f,false,false,f.root.getCenter());h.assertTrue(capture.radius==4,"Mismatched explosion owner cannot borrow a meal");
            var creeper=EntityType.CREEPER.create(f.level);var tnt=new net.minecraft.world.entity.item.PrimedTnt(f.level,f.root.getX(),101,0,f.player);util.invoke(null,f.player,tnt,20f,4f,false,false,f.root.getCenter());h.assertTrue(capture.radius==4,"Non-TaCZ explosives stay unchanged even through TaCZ utility");
            int seen=capture.seen;f.level.explode(tnt,f.root.getX(),101,0,4,false,net.minecraft.world.level.Level.ExplosionInteraction.NONE);h.assertTrue(capture.radius==4,"Actual vanilla TNT radius unchanged");f.level.explode(creeper,f.root.getX(),101,0,4,false,net.minecraft.world.level.Level.ExplosionInteraction.NONE);h.assertTrue(capture.seen==seen&&capture.vanilla==2&&capture.radius==4,"Actual creeper radius unchanged; vanilla explosions never enter the TaCZ hook");grenade.discard();rifle.discard();
        }finally{MinecraftForge.EVENT_BUS.unregister(capture);clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v2AllFiveBuffsShareLifecycleAndPlayerOwnership(GameTestHelper h){
        var f=fixture(h,1,8);try{
            var other=UpgradeGameTests.player(h,"v2-other-diner");var effects=List.of(MealRules.Effect.FIREPOWER,MealRules.Effect.QUICK_HANDS,MealRules.Effect.BRAWLER,MealRules.Effect.HEAVY_HAND,MealRules.Effect.DEMOLITION);
            for(var effect:effects){PlayerMeals.eat(f.player,meal(effect));var s=PlayerMeals.get(f.level).players.get(f.player.getUUID());int remaining=s.remaining;h.assertTrue(MealGunCompat.bonus(f.player,effect.id)==effect.amount*2&&MealGunCompat.bonus(other,effect.id)==0,"Each new effect belongs only to its diner at home");
                for(int t=0;t<20;t++)PlayerMeals.tick(f.player);h.assertTrue(s.remaining==remaining,"Home freezes every gun meal");f.player.setPos(f.root.getX()+30,101,0);PlayerMeals.tick(f.player);h.assertTrue(MealGunCompat.bonus(f.player,effect.id)==effect.amount&&s.remaining==remaining-1,"Field normalizes and spends a tick");f.player.setPos(f.root.getCenter());PlayerMeals.tick(f.player);h.assertTrue(s.remaining==remaining-1&&MealGunCompat.bonus(f.player,effect.id)==effect.amount*2,"Re-entry doubles without refreshing");
                var loaded=PlayerMeals.load(PlayerMeals.get(f.level).save(new net.minecraft.nbt.CompoundTag())).players.get(f.player.getUUID());h.assertTrue(loaded.meal.equals(s.meal)&&loaded.remaining==s.remaining,"All new IDs persist");PlayerMeals.clear(f.player);h.assertTrue(MealGunCompat.bonus(f.player,effect.id)==0,"Clearing removes each hook's bonus");}
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=4000) // the reload is measured in wall-clock time; an un-throttled test server runs far more than 20 ticks a second
    public static void v2ActualReloadTimingAndAmmunition(GameTestHelper h)throws Exception{
        var f=fixture(h,1,9);var api=Class.forName("com.tacz.guns.api.entity.IGunOperator");var operator=api.getMethod("fromLivingEntity",LivingEntity.class).invoke(null,f.player);var data=api.getMethod("getDataHolder").invoke(operator);
        var reloadClass=Class.forName("com.tacz.guns.entity.shooter.LivingEntityReload");var reload=reloadClass.getConstructors()[0].newInstance(f.player,data,null,null);var tick=reloadClass.getMethod("tickReloadState");var stamp=data.getClass().getField("reloadTimestamp");var state=data.getClass().getField("reloadStateType");var current=data.getClass().getField("currentGunItem");
        var feeding=Class.forName("com.tacz.guns.api.entity.ReloadState$StateType").getField("EMPTY_RELOAD_FEEDING").get(null);
        var ammo=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:ammo")),64);ammo.getItem().getClass().getMethod("setAmmoId",ItemStack.class,ResourceLocation.class).invoke(ammo.getItem(),ammo,new ResourceLocation("tacz:762x39"));for(int i=0;i<3;i++)f.player.getInventory().setItem(i,ammo.copy());
        long[] duration=new long[3],start={0};int[] round={0};ItemStack[] held={ItemStack.EMPTY};
        Runnable begin=()->{try{
            PlayerMeals.clear(f.player);if(round[0]>0){f.player.setPos(round[0]==1?f.root.getCenter():f.root.east(30).getCenter());PlayerMeals.eat(f.player,meal(MealRules.Effect.QUICK_HANDS));}
            held[0]=gun("tacz:ak47");f.player.setItemInHand(InteractionHand.MAIN_HAND,held[0]);current.set(data,(java.util.function.Supplier<ItemStack>)()->held[0]);state.set(data,feeding);start[0]=System.currentTimeMillis();stamp.setLong(data,start[0]);
            var item=held[0].getItem();item.getClass().getMethod("setCurrentAmmoCount",ItemStack.class,int.class).invoke(item,held[0],0);item.getClass().getMethod("setBulletInBarrel",ItemStack.class,boolean.class).invoke(item,held[0],false);item.getClass().getMethod("startReload",data.getClass(),ItemStack.class,LivingEntity.class).invoke(item,data,held[0],f.player);
        }catch(Exception ex){h.fail("Reload initialization: "+ex);}};
        h.runAtTickTime(60,begin);h.onEachTick(()->{try{
            if(round[0]>=3||start[0]==0)return;var result=tick.invoke(reload);var type=result.getClass().getMethod("getStateType").invoke(result);if((boolean)type.getClass().getMethod("isReloading").invoke(type))return;
            duration[round[0]]=System.currentTimeMillis()-start[0];int count=(int)held[0].getItem().getClass().getMethod("getCurrentAmmoCount",ItemStack.class).invoke(held[0].getItem(),held[0]);h.assertTrue(count>=29,"Native reload transfers magazine ammunition");round[0]++;
            if(round[0]<3){tick.invoke(reload);begin.run();return;}
            com.mojang.logging.LogUtils.getLogger().info("MEAL_V2_RELOAD_MEASURED baseline={}ms home={}ms field={}ms",duration[0],duration[1],duration[2]);
            h.assertTrue(duration[1]<duration[0]&&duration[2]<duration[0],"Both home and field reloads complete faster");h.assertTrue(Math.abs((double)duration[0]/duration[1]-(1+MealRules.Effect.QUICK_HANDS.amount*2))<.06&&Math.abs((double)duration[0]/duration[2]-(1+MealRules.Effect.QUICK_HANDS.amount))<.06,"Native reload timing matches the home and field Quick Hands speed");
            com.mojang.logging.LogUtils.getLogger().info("MEAL_V2_RELOAD_PASS baseline={}ms home={}ms field={}ms",duration[0],duration[1],duration[2]);clean(f);h.succeed();
        }catch(Exception ex){clean(f);h.fail("Native reload comparison: "+ex);}});
    }
    public static final class KitchenV2TestRunner {
        private MultipleTestTracker tracker;
        @SubscribeEvent public void commands(net.minecraftforge.event.RegisterCommandsEvent e){GameTestRegistry.register(MessHallV2GameTests.class);e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-v2-test").requires(s->s.hasPermission(2)).executes(c->{var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getTestName().toLowerCase(Locale.ROOT).contains("v2")).toList();if(tests.size()!=6)throw new IllegalStateException("Expected six Mess Hall v2 tests, got "+tests.size());tracker=new MultipleTestTracker(GameTestRunner.runTests(tests,new BlockPos(0,160,0),Rotation.NONE,c.getSource().getLevel(),GameTestTicker.SINGLETON,4));return 1;}));}
        @SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;GameTestTicker.SINGLETON.tick();if(!tracker.isDone())return;for(var t:tracker.getFailedRequired())com.mojang.logging.LogUtils.getLogger().error("Mess Hall v2 test failed: "+t.getTestName(),t.getError());if(tracker.getFailedRequiredCount()>0)com.mojang.logging.LogUtils.getLogger().error("{} mess hall v2 tests failed",tracker.getFailedRequiredCount());else com.mojang.logging.LogUtils.getLogger().info("All {} mess hall v2 tests passed",tracker.getTotalCount());tracker=null;}
    }
}
