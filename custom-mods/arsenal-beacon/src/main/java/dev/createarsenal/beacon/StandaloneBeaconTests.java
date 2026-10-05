package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Opt-in tests run with TaCZ, no Create/KubeJS, and optionally the display mod. */
@net.minecraftforge.gametest.GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class StandaloneBeaconTests {
    static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,String id){var p=UpgradeGameTests.player(h,"standalone-"+id);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);return p;}
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void genericRecipesNeedNoPackAndCoinsStayRaidOnly(GameTestHelper h){
        h.assertTrue(BuildFlavor.STANDALONE&&!net.minecraftforge.fml.ModList.get().isLoaded("create")&&!net.minecraftforge.fml.ModList.get().isLoaded("kubejs"),"Minimal standalone environment");
        for(String id:List.of("defense_beacon","beacon_controller","field_guide","gun_platform","reinforced_plating","logistics_module","resonance_coil","restoration_matrix")){
            var recipe=h.getLevel().getRecipeManager().byKey(new ResourceLocation(ArsenalBeacon.ID,id)).orElseThrow();
            h.assertTrue(!recipe.getIngredients().isEmpty()&&recipe.getIngredients().stream().filter(i->!i.isEmpty()).allMatch(i->i.getItems().length>0),"Real vanilla recipe: "+id);
        }
        for(String id:List.of("universal_ammo_coin","ammo_platform","attachment_platform","armor_platform"))h.assertTrue(h.getLevel().getRecipeManager().byKey(new ResourceLocation(ArsenalBeacon.ID,id)).isEmpty(),"Exclusive acquisition: "+id);h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void unrecognizedGunpacksAndOverridesImportWithoutNativeRecipe(GameTestHelper h)throws Exception{
        var p=player(h,"import");var guns=WeaponPlatform.entries(p,"gun","");
        var unseen=guns.stream().filter(e->e.gate().id().equals("arsenal_test:auto_rifle")).findFirst().orElseThrow();
        h.assertTrue(unseen.gate().age()==3&&unseen.recipe() instanceof StandaloneBalance.Generic,"Unseen rifle receives generic Modern recipe");
        var custom=guns.stream().filter(e->e.gate().id().equals("arsenal_test:override_rifle")).findFirst().orElseThrow();
        h.assertTrue(custom.gate().age()==5&&WeaponPlatform.costs(custom).size()==1&&WeaponPlatform.costs(custom).get(0).count()==7&&WeaponPlatform.costs(custom).get(0).ingredient().test(new ItemStack(Items.GOLD_INGOT)),"Admin Age and cost override applied");
        for(var e:guns)for(var c:WeaponPlatform.costs(e))h.assertTrue(c.count()>0&&c.ingredient().getItems().length>0&&Arrays.stream(c.ingredient().getItems()).allMatch(s->!s.isEmpty()),"No missing mod materials or Air");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void paidGunAndCompatibleAmmoCraftAtomically(GameTestHelper h)throws Exception{
        var p=player(h,"craft");var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,3),3);
        var gun=WeaponPlatform.entries(p,"gun","arsenal_test:auto_rifle").get(0);var before=p.getInventory().save(new ListTag());
        h.assertTrue(!WeaponPlatform.craft(p,pos,gun.recipeId()).startsWith("Crafted")&&before.equals(p.getInventory().save(new ListTag())),"Atomic missing material failure");
        UpgradeGameTests.supply(p,WeaponPlatform.costs(gun));h.assertTrue(WeaponPlatform.craft(p,pos,gun.recipeId()).startsWith("Crafted"),"Generic gun is craftable");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun.output().copy());var ammo=WeaponPlatform.entries(p,"ammo","").stream().filter(e->WeaponPlatform.compatible(p,e)).findFirst().orElseThrow();
        h.getLevel().setBlock(pos,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5),3);UpgradeGameTests.supply(p,WeaponPlatform.costs(ammo));
        h.assertTrue(WeaponPlatform.craft(p,pos,ammo.recipeId()).startsWith("Crafted"),"Native compatibility preserved for automatic ammo");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void allRewardsAndUpgradeCostsExistWithoutOptionalMods(GameTestHelper h){
        for(int t=0;t<=10;t++)for(boolean hard:List.of(false,true)){var reward=RaidRewards.total(h.getLevel(),t,hard);h.assertTrue(reward.stream().allMatch(s->!s.isEmpty()&&s.getCount()>0),"Every reward exists");h.assertTrue(reward.stream().anyMatch(s->s.is(ArsenalBeacon.AMMO_COIN.get())),"Coin payout at every tier");}
        var d=new CampaignData();for(var c:RaidRespite.costs(d))h.assertTrue(c.ingredient().getItems().length>0,"Generic raid breaks");for(int age=1;age<5;age++)for(var c:WeaponPlatform.upgrades(age))h.assertTrue(c.ingredient().getItems().length>0,"Generic Age upgrade materials");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void vanillaArmorAndOptionalDisplayAcquisitionWork(GameTestHelper h)throws Exception{
        var p=player(h,"armor");var armor=ArmorPlatform.entries(p,"");h.assertTrue(armor.stream().allMatch(e->ArmorPlatform.armor(e.output()))&&armor.stream().anyMatch(e->e.output().is(Items.IRON_CHESTPLATE)),"Armor autodiscovery includes vanilla protection");
        for(var e:armor)for(var c:WeaponPlatform.costs(e))h.assertTrue(c.ingredient().getItems().length>0,"Generic wearable materials");
        if(net.minecraftforge.fml.ModList.get().isLoaded("arsenal_displays")){
            h.assertTrue(DisplayRacks.entries("").size()==11,"All optional displays sold in Supplies");
            for(var e:DisplayRacks.entries(""))h.assertTrue(h.getLevel().getRecipeManager().byKey(new ResourceLocation("arsenal_displays",e.recipeId().getPath())).isEmpty(),"Standalone displays defer recipes when beacon is installed");
        }else h.assertTrue(ArsenalBeacon.RACKS.isEmpty(),"Beacon runs independently of displays");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void confirmationStartsIntroAndRemovalRecoversSafely(GameTestHelper h){
        var p=player(h,"campaign");var l=h.getLevel();var d=CampaignData.get(l);d.resetProgress();d.phase="unplaced";d.damage.clear();d.destroyedTurrets.clear();d.introCompleted=false;
        var floor=h.absolutePos(new BlockPos(1,0,1));l.setBlock(floor,Blocks.STONE.defaultBlockState(),3);p.setPos(floor.getX()+.5,floor.getY()+2,floor.getZ()+.5);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ArsenalBeacon.BEACON_ITEM.get()));
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor),net.minecraft.core.Direction.UP,floor,false);
        BeaconActions.requestPlacement(p,new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        h.assertTrue(d.phase.equals("unplaced")&&!BeaconActions.pendingToken(p).isEmpty(),"Placing requires confirmation");h.assertTrue(BeaconActions.confirm(p,BeaconActions.pendingToken(p)),"Confirmation succeeds");h.assertTrue(d.introRaid&&d.raidTier==0&&!d.phase.equals("preparation"),"Immediate introduction locked to tier zero");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ArsenalBeacon.CONTROLLER.get()));BeaconActions.requestRemoval(p);h.assertTrue(BeaconActions.confirm(p,BeaconActions.pendingToken(p)),"Removal confirmation succeeds");h.assertTrue(d.phase.equals("unplaced")&&d.core==0,"Safe reset without optional mods");l.setBlock(floor,Blocks.AIR.defaultBlockState(),3);h.succeed();
    }
    public static final class Runner {
        private MultipleTestTracker tracker;
        @SubscribeEvent public void commands(net.minecraftforge.event.RegisterCommandsEvent e){GameTestRegistry.register(StandaloneBeaconTests.class);e.getDispatcher().register(net.minecraft.commands.Commands.literal("beacon-standalone-test").requires(s->s.hasPermission(2)).executes(c->{var names=List.of("genericrecipesneednopack","unrecognizedgunpacks","paidgunandcompatible","allrewardsandupgrade","vanillaarmorandoptional","confirmationstartsintro");var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->names.stream().anyMatch(n->t.getTestName().toLowerCase(Locale.ROOT).contains(n))).toList();if(tests.size()!=6)throw new IllegalStateException("Expected six standalone tests, got "+tests.size());tracker=new MultipleTestTracker(GameTestRunner.runTests(tests,new BlockPos(0,100,0),Rotation.NONE,c.getSource().getLevel(),GameTestTicker.SINGLETON,4));return 1;}));}
        @SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;GameTestTicker.SINGLETON.tick();if(!tracker.isDone())return;var log=com.mojang.logging.LogUtils.getLogger();for(var t:tracker.getFailedRequired())log.error("Standalone beacon test failed: "+t.getTestName(),t.getError());if(tracker.getFailedRequiredCount()>0)log.error("{} standalone beacon tests failed",tracker.getFailedRequiredCount());else log.info("All {} standalone beacon tests passed",tracker.getTotalCount());tracker=null;}
    }
}
