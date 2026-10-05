package dev.createarsenal.beacon;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ReleaseElevenGameTests {
    @GameTest(template="empty3x3x3",batch="intro011",timeoutTicks=100)
    public static void firstPlantStartsTierZeroAndAwardsOnlyOneSharedBench(GameTestHelper h)throws Exception{
        var l=h.getLevel();var floor=new BlockPos(5120,101,0);UpgradeGameTests.arena(l,floor);var p=UpgradeGameTests.player(h,"intro011");p.setPos(floor.getX(),floor.getY()+1,floor.getZ()-3);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().items.set(0,new ItemStack(ArsenalBeacon.BEACON_ITEM.get()));
        var d=CampaignData.get(l);d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.introCompleted=false;d.phase="unplaced";
        var hit=new BlockHitResult(Vec3.atCenterOf(floor).add(0,.5,0),Direction.UP,floor,false);BeaconActions.requestPlacement(p,new UseOnContext(p,InteractionHand.MAIN_HAND,hit));h.assertTrue(BeaconActions.confirm(p,BeaconActions.pendingToken(p))&&d.phase.equals("snapshot")&&d.introRaid,"Real confirmed first placement immediately snapshots for the introductory raid");
        d.logistics=5;d.raidsStarted=2;var scan=ArsenalBeacon.class.getDeclaredMethod("scan",net.minecraft.server.level.ServerLevel.class,CampaignData.class);scan.setAccessible(true);while(d.phase.equals("snapshot"))scan.invoke(null,l,d);h.assertTrue(d.phase.equals("raid")&&d.raidTier==0&&!d.hardRaid,"Introduction ignores base/Logistics/third-raid difficulty and remains tier zero");
        var raid=ArsenalBeacon.class.getDeclaredMethod("raid",net.minecraft.server.level.ServerLevel.class,CampaignData.class);raid.setAccessible(true);var clock=ArsenalBeacon.class.getDeclaredField("clock");clock.setAccessible(true);int previous=clock.getInt(null);
        try{clock.setInt(null,20);for(int wave=1;wave<=Rules.waves(0);wave++){d.wave=wave;d.spawnRemaining=0;d.raiders.clear();raid.invoke(null,l,d);}h.assertTrue(d.introCompleted&&d.rewards.stream().map(ItemStack::of).filter(s->s.is(ArsenalBeacon.GUN_PLATFORM.get().asItem())).mapToInt(ItemStack::getCount).sum()==1,"First successful introduction queues exactly one shared Weapon Platform");
            d.phase="raid";d.wave=Rules.waves(0);d.spawnRemaining=0;raid.invoke(null,l,d);h.assertTrue(d.rewards.stream().map(ItemStack::of).filter(s->s.is(ArsenalBeacon.GUN_PLATFORM.get().asItem())).mapToInt(ItemStack::getCount).sum()==1,"Later completions cannot duplicate the introduction table");
            d.resetProgress();h.assertTrue(d.introCompleted&&CampaignData.load(d.save(new CompoundTag())).introCompleted,"Packing up and reloading cannot farm the one-time bench reward");
        }finally{clock.setInt(null,previous);UpgradeGameTests.release(l,floor);}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="vertical011",timeoutTicks=100)
    public static void verticalUpgradesMatchBoundsWithoutChangingHpOrModel(GameTestHelper h){
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));l.setBlock(pos,ArsenalBeacon.BEACON.get().defaultBlockState(),3);var d=CampaignData.get(l);d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.beacon=pos;d.phase="preparation";var p=UpgradeGameTests.player(h,"vertical011");p.setPos(pos.getX()+3,pos.getY(),pos.getZ());p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
        h.assertTrue(d.below()==3&&d.above()==8&&d.height()==12,"New campaign has a compact twelve-block vertical span");
        for(int grade=0;grade<=4;grade++){h.assertTrue(d.inside(pos.below(d.below()))&&!d.inside(pos.below(d.below()+1))&&d.inside(pos.above(d.above()))&&!d.inside(pos.above(d.above()+1)),"Repair/scoring boundary matches both exact vertical faces");if(grade<4){p.getInventory().add(new ItemStack(ArsenalBeacon.LOGISTICS.get(),Rules.upgradeCost(grade)));ArsenalBeacon.upgrade(p,"vertical");h.assertTrue(d.vertical==grade+1&&d.health==1000&&d.maximumHealth()==1000&&l.getBlockState(pos).getValue(ArsenalStructures.MK)==1,"Vertical upgrade consumes its materials without changing HP or model");}}
        var old=d.save(new CompoundTag());old.remove("vertical");old.remove("introCompleted");var migrated=CampaignData.load(old);h.assertTrue(migrated.vertical==4&&migrated.height()==65&&migrated.introCompleted,"Existing saves retain old tall protection and do not replay introduction");h.assertTrue(CampaignData.load(d.save(new CompoundTag())).vertical==4,"New height progression persists");d.phase="unplaced";p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="respite011",timeoutTicks=100)
    public static void respiteStacksHundredDaysChargesAtomicallyAndConsumesBonusOnce(GameTestHelper h)throws Exception{
        var l=h.getLevel();var floor=new BlockPos(5376,101,0);UpgradeGameTests.arena(l,floor);var d=CampaignData.get(l);d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.introCompleted=true;d.beacon=floor.above();d.phase="preparation";l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);var p=UpgradeGameTests.player(h,"break011");p.setGameMode(GameType.SURVIVAL);p.setPos(floor.getX()+4,floor.getY()+1,floor.getZ());p.getInventory().clearContent();
        var before=p.getInventory().save(new ListTag());h.assertTrue(!RaidRespite.buy(p).startsWith("Added")&&before.equals(p.getInventory().save(new ListTag()))&&d.respiteTicks==0,"Insufficient payment consumes nothing and grants no pause");
        int last=0;for(int i=0;i<20;i++){var costs=RaidRespite.costs(d);int price=costs.stream().mapToInt(WeaponPlatform.Cost::count).sum();h.assertTrue(price>last,"Every stacked purchase costs more");last=price;UpgradeGameTests.supply(p,costs);h.assertTrue(RaidRespite.buy(p).startsWith("Added")&&p.getInventory().items.stream().allMatch(ItemStack::isEmpty),"Exact Survival payment buys five days");}
        h.assertTrue(d.respiteTicks==100*24000L&&d.respitePurchases==20&&d.nextRaidBonus==3&&RaidRespite.previewTier(d,9)==10,"One hundred days stack with a capped next-raid bonus");var saved=CampaignData.load(d.save(new CompoundTag()));h.assertTrue(saved.respiteTicks==d.respiteTicks&&saved.nextRaidBonus==3&&saved.respitePurchases==20,"Time, cost ladder and bonus survive reload");
        d.preparationTicks=200;h.assertTrue(RaidRespite.waitTick(d)&&d.preparationTicks==200&&d.respiteTicks==100*24000L-1,"Active break ticks freeze the normal schedule rather than hiding a due raid");
        var begin=ArsenalBeacon.class.getDeclaredMethod("begin",net.minecraft.server.level.ServerLevel.class,CampaignData.class);begin.setAccessible(true);begin.invoke(null,l,d);var scan=ArsenalBeacon.class.getDeclaredMethod("scan",net.minecraft.server.level.ServerLevel.class,CampaignData.class);scan.setAccessible(true);while(d.phase.equals("snapshot"))scan.invoke(null,l,d);h.assertTrue(d.raidTier==3&&d.nextRaidBonus==0&&d.respitePurchases==0&&d.respiteTicks==0,"Next started raid consumes bonus exactly once and resets its purchase ladder");UpgradeGameTests.release(l,floor);h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="render011",timeoutTicks=100)
    public static void tallBenchCollisionIsBoundedAndUsesStaticChunkRendering(GameTestHelper h){
        long start=System.nanoTime();int oldSize=ArsenalStructures.clippedShapes.size();
        for(var block:List.of(ArsenalBeacon.GUN_PLATFORM.get(),ArsenalBeacon.AMMO_PLATFORM.get(),ArsenalBeacon.ATTACHMENT_PLATFORM.get()))for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))for(int age=1;age<=5;age++){
            var state=block.defaultBlockState().setValue(WeaponPlatform.AGE,age).setValue(ArsenalStructures.WIDE,true).setValue(ArsenalStructures.TALL,true).setValue(ArsenalStructures.FACING,facing);var root=h.absolutePos(new BlockPos(1,1,1));h.assertTrue(ArsenalStructures.cells(root,state).size()==3&&state.getRenderShape()==net.minecraft.world.level.block.RenderShape.MODEL,"All tables reserve four physical cells and use static chunk-rendered geometry");
            var box=ArsenalStructures.full(state).bounds();h.assertTrue(box.getYsize()==2&&Math.max(box.getXsize(),box.getZsize())==2,"Visual installation envelope has exact 2x2x1 size");for(int j=0;j<100;j++)for(var p:ArsenalStructures.cells(root,state))h.assertTrue(!ArsenalStructures.cell(state,p.getX()-root.getX(),p.getY()-root.getY(),p.getZ()-root.getZ()).isEmpty(),"Upper and lower parts have collision");
        }
        h.assertTrue(ArsenalStructures.clippedShapes.size()-oldSize<=12,"Collision cache ignores table Age and kind instead of duplicating complex meshes");com.mojang.logging.LogUtils.getLogger().info("0.11 static bench collision benchmark: {} ms / 18000 cached cell queries",(System.nanoTime()-start)/1_000_000);h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="cache011",timeoutTicks=100)
    public static void lateCachesAreCompactIncludeFactoryAndExoticInputs(GameTestHelper h){
        for(int tier=0;tier<=10;tier++){var rewards=RaidRewards.completion(h.getLevel(),tier,false);h.assertTrue(rewards.stream().anyMatch(s->s.is(ArsenalBeacon.AMMO_COIN.get()))&&rewards.stream().anyMatch(s->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("kubejs:propellant")),"Every victory includes ammo coins and usable factory propellant");if(tier>=4)h.assertTrue(rewards.stream().anyMatch(s->s.is(Items.BLAZE_ROD)),"Nether-intensive tiers supply required blaze rods");if(tier>=6)h.assertTrue(rewards.stream().anyMatch(s->s.is(Items.SHULKER_SHELL)),"Late tiers supply portable storage materials");h.assertTrue(rewards.stream().filter(s->s.is(Items.ANDESITE)).mapToInt(ItemStack::getCount).sum()<=64,"Normal raids never flood more than one raw-andesite stack");}
        h.assertTrue(Rules.defensePercent(4)==60&&Rules.healingPercent(4)==25&&Rules.contactDamage(5,4)==(int)Math.ceil(Rules.contactDamage(5,0)*.4),"Displayed percentage rules equal actual damage and healing");h.succeed();
    }
}
