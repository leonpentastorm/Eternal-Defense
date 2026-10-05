package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class CampaignEightGameTests {
    static Map<Long,BlockState> house(BlockState material){var blocks=new HashMap<Long,BlockState>();for(var p:BlockPos.betweenClosed(new BlockPos(-3,0,-3),new BlockPos(3,4,3)))if(p.getY()==0||p.getY()==4||Math.abs(p.getX())==3||Math.abs(p.getZ())==3)blocks.put(p.asLong(),material);blocks.put(new BlockPos(2,2,0).asLong(),Blocks.TORCH.defaultBlockState());blocks.put(new BlockPos(0,1,0).asLong(),Blocks.BARREL.defaultBlockState());return blocks;}
    @GameTest(template="empty3x3x3",batch="design",timeoutTicks=100)
    public static void architectureIsMaterialNeutralCappedAndRewardsUsableSpace(GameTestHelper h){
        var d=new CampaignData();d.beacon=BlockPos.ZERO;var wood=house(Blocks.OAK_PLANKS.defaultBlockState());var stone=house(Blocks.STONE.defaultBlockState());var gold=house(Blocks.GOLD_BLOCK.defaultBlockState());
        var a=BaseScoring.analyze(d,wood,wood.keySet());var b=BaseScoring.analyze(d,stone,stone.keySet());var c=BaseScoring.analyze(d,gold,gold.keySet());
        h.assertTrue(a.equals(b)&&b.equals(c),"The exact same placed house scores identically in wood, stone and expensive gold");h.assertTrue(a.layout()>0&&a.lighting()>0&&a.furnishings()>0,"Covered usable rooms, light and furnishings have real score components");
        var box=new HashMap<Long,BlockState>();for(var p:BlockPos.betweenClosed(new BlockPos(-8,0,-8),new BlockPos(8,6,8)))box.put(p.asLong(),Blocks.DIRT.defaultBlockState());
        var spam=BaseScoring.analyze(d,box,box.keySet());h.assertTrue(spam.total()<500&&spam.layout()==0,"A huge placed dirt cube alone cannot unlock even one reward tier");h.assertTrue(BaseScoring.analyze(d,box,Set.of()).total()==0,"Undeveloped natural terrain provides no score");
        h.assertTrue(a.total()>BaseScoring.analyze(d,Map.of(),Set.of()).total(),"Architecture gives meaningful reward progress");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="rewardplans",timeoutTicks=100)
    public static void previewEqualsActualRewardsWithTierZeroAndTacticalBonuses(GameTestHelper h){
        var l=h.getLevel();var previews=RaidRewards.previews(l);int previous=0;
        for(int tier=0;tier<=10;tier++)for(boolean hard:List.of(false,true)){
            var completion=RaidRewards.completion(l,tier,hard);var d=new CampaignData();RaidRewards.queue(d,completion);var totals=new HashMap<String,Integer>();for(var n:d.rewards){var stack=ItemStack.of(n);h.assertTrue(!stack.isEmpty()&&stack.getCount()<=stack.getMaxStackSize(),"Every queued reward is a valid payable stack");totals.merge(stack.getItem()+"/"+stack.getTag(),stack.getCount(),Integer::sum);}
            for(var stack:completion)h.assertTrue(totals.get(stack.getItem()+"/"+stack.getTag())==stack.getCount(),"Split-stack payment preserves true quantities");
            var plan=previews.getCompound(tier).getList(hard?"hard":"normal",Tag.TAG_COMPOUND);var total=RaidRewards.total(l,tier,hard);h.assertTrue(plan.size()==total.size(),"Preview shows every resource and tactical item");
            for(int i=0;i<plan.size();i++){var row=plan.getCompound(i);h.assertTrue(row.getInt("count")==total.get(i).getCount()&&ItemStack.isSameItemSameTags(ItemStack.of(row.getCompound("item")),total.get(i)),"Preview and payout use the same items, NBT and exact quantities");}
            if(!hard){int count=completion.stream().mapToInt(ItemStack::getCount).sum();h.assertTrue(count>previous,"Each higher tier has a larger guaranteed cache");previous=count;}
            else h.assertTrue(completion.stream().anyMatch(s->s.hasTag()&&s.getTag().contains("ThrowableId"))&&completion.stream().anyMatch(s->s.hasTag()&&s.getTag().contains("ConsumableId")),"Every hard tier includes authored grenade and usable medical kit items");
        }
        var zero=RaidRewards.completion(l,0,false);h.assertTrue(zero.stream().anyMatch(s->s.is(Items.ANDESITE)&&s.getCount()==64)&&zero.stream().anyMatch(s->s.is(Items.IRON_INGOT)&&s.getCount()==16),"Tier 0 guarantees 64 andesite plus 16 completion iron in addition to wave rewards");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="hardcadence",timeoutTicks=100)
    public static void bossCadencePersistsAndSupplyCategoryUsesCreateInsteadOfAge(GameTestHelper h){
        var d=new CampaignData();for(int raid=1;raid<=9;raid++){HardRaids.start(d);h.assertTrue(d.hardRaid==(raid%3==0),"Only every third started raid is hard, even after losses");d=CampaignData.load(d.save(new CompoundTag()));}
        d.bossSpawned=true;d.bossId=UUID.randomUUID();d=CampaignData.load(d.save(new CompoundTag()));h.assertTrue(d.bossSpawned&&d.bossId!=null&&d.hardRaid,"Boss identity and spawn guard survive reload");d.finishDecommission();h.assertTrue(d.raidsStarted==0&&!d.hardRaid&&d.bossId==null,"Confirmed campaign reset clears boss progression");
        var p=UpgradeGameTests.player(h,"supply-catalogue");var registry=PlatformRegistry.get(h.getLevel());var saved=new HashSet<>(registry.createMilestones);
        try{
            registry.createMilestones.clear();var supply=WeaponPlatform.specialEntries(p,"",9);h.assertTrue(supply.size()>=10&&supply.stream().allMatch(CreateUnlocks::supply),"Dedicated Supplies category includes authored grenades, meds, melee gear and shields, without attachments");
            var grenade=supply.stream().filter(e->e.output().hasTag()&&e.output().getTag().getString("ThrowableId").equals("lrtactical:m67")).findFirst().orElseThrow();
            h.assertTrue(!CreateUnlocks.unlocked(p,grenade,5),"Exotic table Age cannot bypass supply research");registry.createMilestones.addAll(List.of("mechanical_press","mechanical_mixer"));h.assertTrue(CreateUnlocks.unlocked(p,grenade,1),"Workshop production unlocks grenades even at Frontier table Age");
            var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);var state=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,"",9,0));h.assertTrue(state.getInt("ageFilter")==9&&state.getInt("total")==supply.size(),"The Supplies button has a separate, paginated server catalogue");
        }finally{registry.createMilestones.clear();registry.createMilestones.addAll(saved);}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="paymentflow",timeoutTicks=100)
    public static void actualWaveCompletionPaysExactlyPreviewAndRejectsMissingBoss(GameTestHelper h)throws Exception{
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));l.setBlock(pos,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        var advance=ArsenalBeacon.class.getDeclaredMethod("raid",net.minecraft.server.level.ServerLevel.class,CampaignData.class);advance.setAccessible(true);
        var clock=ArsenalBeacon.class.getDeclaredField("clock");clock.setAccessible(true);int previous=clock.getInt(null);
        try{
            clock.setInt(null,20);
            for(int tier=0;tier<=10;tier++)for(boolean hard:List.of(false,true)){
                var d=new CampaignData();d.beacon=pos;d.phase="raid";d.health=1000;d.raidTier=tier;d.hardRaid=hard;d.bossSpawned=hard;d.bossKilled=hard;
                for(int wave=1;wave<=Rules.waves(tier);wave++){d.wave=wave;d.spawnRemaining=0;advance.invoke(null,l,d);}
                h.assertTrue(d.victories==1&&d.phase.equals("restore"),"Actual final wave wins and begins restoration");
                var actual=new HashMap<String,Integer>();for(var n:d.rewards){var stack=ItemStack.of(n);actual.merge(stack.getItem()+"/"+stack.getTag(),stack.getCount(),Integer::sum);}
                var expected=new HashMap<String,Integer>();for(var stack:RaidRewards.total(l,tier,hard))expected.merge(stack.getItem()+"/"+stack.getTag(),stack.getCount(),Integer::sum);
                h.assertTrue(actual.equals(expected),"Actual normal/hard completion plus every wave pays the entire exact preview at tier "+tier);
            }
            var missing=new CampaignData();missing.beacon=pos;missing.phase="raid";missing.health=1000;missing.hardRaid=true;missing.bossSpawned=true;missing.bossKilled=false;missing.wave=Rules.waves(0);advance.invoke(null,l,missing);
            h.assertTrue(missing.victories==0&&missing.rewards.stream().map(ItemStack::of).noneMatch(s->s.is(Items.ANDESITE)),"A vanished, undefeated boss cannot pay a completion cache");
        }finally{clock.setInt(null,previous);}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="realbosses",timeoutTicks=250)
    public static void everyBossIsRealModdedProjectileVulnerableAndCannotDuplicate(GameTestHelper h){
        var l=h.getLevel();var d=CampaignData.get(l);var pos=new BlockPos(1024,101,0);UpgradeGameTests.arena(l,pos);var p=UpgradeGameTests.player(h,"boss-defender");
        var boss=new java.util.concurrent.atomic.AtomicReference<Mob>();
        for(int tier=0;tier<=5;tier++){
            int t=tier;
            h.runAtTickTime(40+t*30,()->{
                d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.beacon=pos;l.setBlock(pos,ArsenalBeacon.BEACON.get().defaultBlockState(),3);d.phase="raid";d.hardRaid=true;d.raidTier=t;d.wave=Rules.waves(t);d.spawnRemaining=1;d.campaignSerial++;
                h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(new net.minecraft.resources.ResourceLocation(HardRaids.TYPES[t])),"Selected boss exists in actual installed mod registry: "+HardRaids.TYPES[t]);
                h.assertTrue(HardRaids.spawn(l,d,pos.above()),"Native modded boss can spawn in a clear arena");boss.set((Mob)l.getEntity(d.bossId));
            });
            h.runAtTickTime(60+t*30,()->{
                var mob=(Mob)l.getEntity(d.bossId);h.assertTrue(mob!=null,"Boss is indexed after chunk entity loading");boss.set(mob);h.assertTrue(mob.getMaxHealth()==Rules.bossHealth(t),"Native boss health is bounded after pack spawn handlers: "+HardRaids.TYPES[t]+" actual "+mob.getMaxHealth());
                h.assertTrue(!HardRaids.spawn(l,d,pos.offset(5,1,0)),"One boss per raid cannot duplicate on retry");
                // Approach from behind: native guardians can intentionally block front shots.
                var facing=mob.getViewVector(1);p.setPos(mob.getX()-facing.x*4,mob.getY(),mob.getZ()-facing.z*4);float before=mob.getHealth();var arrow=new net.minecraft.world.entity.projectile.Arrow(l,p.getX(),p.getY(),p.getZ());mob.hurt(l.damageSources().arrow(arrow,p),20);
                h.assertTrue(mob.getHealth()<before,"Boss takes ordinary player projectile damage: "+HardRaids.TYPES[t]);
                HardRaids.tick(l,d);mob.invulnerableTime=0;mob.hurt(l.damageSources().genericKill(),100000);h.assertTrue(d.bossKilled,"Only actual LivingDeathEvent records boss defeat");
                mob.discard();d.raiders.clear();d.phase="unplaced";HardRaids.tick(l,d);
            });
        }
        h.runAtTickTime(240,()->{UpgradeGameTests.release(l,pos);h.succeed();});
    }
}
