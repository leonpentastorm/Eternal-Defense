package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ReleaseThirteenGameTests {
    @GameTest(template="empty3x3x3",batch="corpse013",timeoutTicks=100)
    public static void nativeDownedSoldierEndsWaveAndKeepsLootableCorpse(GameTestHelper h)throws Exception{
        var l=h.getLevel();var d=CampaignData.get(l);d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.phase="raid";d.raidTier=4;d.wave=1;d.spawnRemaining=1;d.beacon=h.absolutePos(new BlockPos(1,1,1));l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        Mob mob=null;var clock=ArsenalBeacon.class.getDeclaredField("clock");clock.setAccessible(true);int oldClock=clock.getInt(null);
        try{
            h.assertTrue(SpecialForcesRaids.spawn(l,d,d.beacon.east(2),false),"Spawn the actual installed soldier with native equipment");mob=(Mob)l.getEntity(d.raiders.iterator().next());
            h.assertTrue(mob.hurt(l.damageSources().fellOutOfWorld(),48)&&SpecialForcesRaids.nativeState(mob)==1,"Actual native damage enters DOWN rather than vanilla death");
            h.assertTrue(mob.isAlive()&&SpecialForcesRaids.defeated(mob)&&!BeaconCombat.hostile(mob),"Living downed body is no longer an attacker");
            d.spawnRemaining=0;clock.setInt(null,20);var tick=ArsenalBeacon.class.getDeclaredMethod("raid",net.minecraft.server.level.ServerLevel.class,CampaignData.class);tick.setAccessible(true);tick.invoke(null,l,d);
            h.assertTrue(d.wave==2&&!d.raiders.contains(mob.getUUID()),"Last downed soldier clears the wave and queues the next one");
            h.assertTrue(SpecialForcesRaids.corpse(mob)&&SpecialForcesRaids.nativeState(mob)==2&&mob.isAlive()&&mob instanceof Container&&((Container)mob).getContainerSize()==41&&!((Container)mob).isEmpty(),"Retired native DEAD corpse preserves all 41 loot slots");
            h.assertTrue(mob.isNoAi()&&mob.getTarget()==null&&!mob.isCurrentlyGlowing()&&mob.isInvulnerable(),"Corpse cannot shoot, breach, revive or flash as an attacker");
            var reload=new net.minecraftforge.event.entity.EntityJoinLevelEvent(mob,l,true);new SpecialForcesRaids().join(reload);h.assertTrue(!reload.isCanceled(),"Owned corpse remains lootable after a chunk reload outside the attacker set");
            mob.getPersistentData().putLong("arsenalCorpseUntil",l.getGameTime());SpecialForcesRaids.cleanup(l);h.assertTrue(mob.isRemoved(),"Expired corpses have bounded cleanup");
        }finally{clock.setInt(null,oldClock);if(mob!=null)mob.discard();d.raiders.clear();d.resetProgress();d.phase="unplaced";l.setBlock(d.beacon,Blocks.AIR.defaultBlockState(),3);}h.succeed();
    }
    static int amount(Container chest,Item item){int total=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(item))total+=chest.getItem(i).getCount();return total;}
    static int queued(CampaignData d,Item item){return d.rewards.stream().map(ItemStack::of).filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    @GameTest(template="empty3x3x3",batch="chest013",timeoutTicks=100)
    public static void beaconRewardBoxKeepsOverflowQueuedAndNeverDuplicates(GameTestHelper h){
        var d=new CampaignData();d.phase="preparation";d.beacon=h.absolutePos(new BlockPos(1,1,1));
        RaidRewards.queue(d,List.of(new ItemStack(Items.IRON_INGOT,56*64)));RewardCache.deposit(d,d.rewardBox);
        h.assertTrue(amount(d.rewardBox,Items.IRON_INGOT)==54*64&&queued(d,Items.IRON_INGOT)==128,"The beacon's 54 slots fill and the overflow stays queued");
        RewardCache.deposit(d,d.rewardBox);h.assertTrue(amount(d.rewardBox,Items.IRON_INGOT)==54*64&&queued(d,Items.IRON_INGOT)==128,"Repeated deposits cannot duplicate rewards");
        var loaded=CampaignData.load(d.save(new CompoundTag()));h.assertTrue(amount(loaded.rewardBox,Items.IRON_INGOT)==54*64&&queued(loaded,Items.IRON_INGOT)==128,"The box and the queue survive saving");
        h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="selection013",timeoutTicks=100)
    public static void survivalCanLowerLevelAndStartImmediatelyWithoutUpgradeLoss(GameTestHelper h)throws Exception{
        var l=h.getLevel();var floor=new BlockPos(7600,101,0);UpgradeGameTests.arena(l,floor);var d=CampaignData.get(l);d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.phase="preparation";d.introCompleted=true;d.beacon=floor.above();d.logistics=5;d.raidLimit=10;d.raidsStarted=2;d.respiteTicks=100*24000L;d.respitePurchases=20;d.nextRaidBonus=3;l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        var p=UpgradeGameTests.player(h,"level013");p.setGameMode(GameType.SURVIVAL);p.setPos(floor.getX()+2,floor.getY()+1,floor.getZ());
        try{
            h.assertTrue(RaidSelection.select(p,"2").contains("cap: 2")&&d.logistics==5&&RaidRespite.previewTier(d,10)==2,"Cap can lower below building and Logistics score without deleting upgrades; truce cannot bypass it");
            var loaded=CampaignData.load(d.save(new CompoundTag()));h.assertTrue(loaded.raidLimit==2&&loaded.logistics==5,"Chosen cap survives world reload");var legacy=d.save(new CompoundTag());legacy.remove("raidLimit");h.assertTrue(CampaignData.load(legacy).raidLimit==10,"Older saves default to their existing unrestricted progression");
            h.assertTrue(RaidSelection.start(p).startsWith("Raid requested")&&d.phase.equals("snapshot"),"Survival player starts immediately without operator permission");h.assertTrue(!RaidSelection.start(p).startsWith("Raid requested")&&!RaidSelection.select(p,"0").contains("cap:"),"Repeated start and in-progress cap changes are rejected");
            var scan=ArsenalBeacon.class.getDeclaredMethod("scan",net.minecraft.server.level.ServerLevel.class,CampaignData.class);scan.setAccessible(true);while(d.phase.equals("snapshot"))scan.invoke(null,l,d);
            h.assertTrue(d.phase.equals("raid")&&d.raidTier==2&&d.logistics==5&&d.hardRaid&&d.raidsStarted==3&&d.respiteTicks==0&&d.nextRaidBonus==0,"Snapshot locks the chosen level, consumes the paid pause once, and preserves every-third boss raids");h.assertTrue(BeaconNetwork.state(p,d).getInt("prospectiveTier")==2,"Active UI shows the actual locked raid payout");
        }finally{d.raiders.clear();d.resetProgress();d.phase="unplaced";UpgradeGameTests.release(l,floor);}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="guns013",timeoutTicks=150)
    public static void expandedGunsCraftAndAllHaveReachableAmmunition(GameTestHelper h)throws Exception{
        var p=UpgradeGameTests.player(h,"guns013");p.setGameMode(GameType.SURVIVAL);var guns=WeaponPlatform.entries(p,"gun","");var ammo=WeaponPlatform.entries(p,"ammo","");Map<String,WeaponPlatform.Entry> expanded=new HashMap<>();
        for(var e:guns)if(e.gate().id().matches("(hamster|ww|ronmc|sfms|erode|zeta):.*"))expanded.putIfAbsent(e.gate().id(),e);
        h.assertTrue(expanded.size()==258,"All 258 usable new weapons are available in the Universal Weapon Platform, with unfinished author variants excluded: "+expanded.size());
        Map<Integer,Integer> counts=new HashMap<>();int noAmmo=0;List<String> failures=new ArrayList<>();
        for(var e:expanded.values()){
            counts.merge(e.gate().age(),1,Integer::sum);p.getInventory().clearContent();UpgradeGameTests.supply(p,WeaponPlatform.costs(e));h.assertTrue(WeaponPlatform.transact(p,WeaponPlatform.costs(e),e.output()),"Every new gun crafts atomically: "+e.gate().id());
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,e.output().copy());boolean hasAmmo=ammo.stream().anyMatch(a->!MagazineBridge.isMagazine(a.output())&&WeaponPlatform.compatible(p,a)&&a.gate().age()<=e.gate().age());
            // Melee weapons and infinite-energy authors explicitly have no physical ammo requirement.
            var api=Class.forName("com.tacz.guns.api.TimelessAPI");var idx=((Optional<?>)api.getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,new net.minecraft.resources.ResourceLocation(e.gate().id()))).orElseThrow();var data=idx.getClass().getMethod("getGunData").invoke(idx);var ammoId=(net.minecraft.resources.ResourceLocation)data.getClass().getMethod("getAmmoId").invoke(data);
            if(ammoId!=null&&!ammoId.toString().equals("tacz:melee")){if(!hasAmmo)failures.add(e.gate().id()+" -> "+ammoId+" at Age "+e.gate().age());}else noAmmo++;
            if(e.gate().age()<=2)h.assertTrue(WeaponPlatform.costs(e).stream().noneMatch(cost->Arrays.stream(cost.ingredient().getItems()).anyMatch(s->s.is(Items.DIAMOND)||s.is(Items.NETHERITE_INGOT)||s.is(Items.BLAZE_ROD))),"Historic weapons use early factory inputs, not stacks of late-game materials");
        }
        h.assertTrue(failures.isEmpty(),"All new guns need reachable compatible ammo: "+failures);for(int age=1;age<=5;age++)h.assertTrue(counts.getOrDefault(age,0)>0,"Each Age gained new weapons");com.mojang.logging.LogUtils.getLogger().info("0.13 new weapon coverage: {} weapons; additions by Age {}; ammo-free melee {}",expanded.size(),counts,noAmmo);p.getInventory().clearContent();h.succeed();
    }
}
