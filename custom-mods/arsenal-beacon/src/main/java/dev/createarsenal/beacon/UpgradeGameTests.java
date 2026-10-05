package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class UpgradeGameTests {
    static ServerPlayer player(GameTestHelper h,String id){var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"UpgradeTests"));p.getInventory().clearContent();return p;}
    static void supply(ServerPlayer p,List<WeaponPlatform.Cost> costs){for(var cost:costs){var item=cost.ingredient().getItems()[0];for(int left=cost.count();left>0;left-=Math.min(left,item.getMaxStackSize()))p.getInventory().add(item.copyWithCount(Math.min(left,item.getMaxStackSize())));}}
    @GameTest(template="empty3x3x3",batch="newplatform",timeoutTicks=100)
    public static void createArmoryAndTurretsHaveRealSharedMilestones(GameTestHelper h)throws Exception{
        var p=player(h,"create-unlocks");var registry=PlatformRegistry.get(h.getLevel());var saved=new HashSet<>(registry.createMilestones);registry.createMilestones.clear();
        try{
            var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5),3);
            var armory=WeaponPlatform.specialEntries(p,"",7);h.assertTrue(armory.size()==14,"All thirteen installed Create Armory weapons and DB LONG-SUPER have their own category");
            h.assertTrue(WeaponPlatform.weaponEntries(p,"",false).stream().noneMatch(CreateUnlocks::armory),"Create Armory does not appear in the normal Age catalogue");
            var gun=armory.stream().filter(e->e.gate().id().contains("torque")).findFirst().orElseThrow();supply(p,WeaponPlatform.costs(gun));var before=p.getInventory().save(new ListTag());
            h.assertTrue(!WeaponPlatform.craft(p,pos,gun.recipeId()).startsWith("Crafted")&&before.equals(p.getInventory().save(new ListTag())),"Exotic Age cannot bypass Create machinery milestones or spend inputs");
            for(String id:List.of("mechanical_press","mechanical_mixer")){var a=p.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation("create",id));h.assertTrue(a!=null,"Create author advancement exists");net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent(p,a));}
            h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);
            h.assertTrue(WeaponPlatform.craft(p,pos,gun.recipeId()).startsWith("Crafted"),"Workshop milestone unlocks Create weapon even on an Age 1 station");
            for(String id:List.of("deployer","precision_mechanism")){var a=p.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation("create",id));net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent(p,a));}
            var turret=WeaponPlatform.specialEntries(p,"",8).get(0);supply(p,WeaponPlatform.costs(turret));h.assertTrue(WeaponPlatform.craft(p,pos,turret.recipeId()).startsWith("Crafted"),"Separate turret category consumes factory inputs at Age 1");
            h.assertTrue(p.getInventory().items.stream().anyMatch(s->s.is(turret.output().getItem())),"The actual TaCZ turret is obtained");
            h.assertTrue(PlatformRegistry.load(registry.save(new CompoundTag())).createMilestones.equals(registry.createMilestones),"Shared Create unlocks persist across reload");
            var steam=armory.stream().filter(e->e.gate().id().contains("cannon")).findFirst().orElseThrow();h.assertTrue(!CreateUnlocks.unlocked(p,steam,5),"Heavy Create weapons require steam independently of Exotic Age");
            registry.createMilestones.add("steam_engine");h.assertTrue(CreateUnlocks.unlocked(p,steam,1),"Steam production unlocks heavy Create weapon independently of station Age");
            for(var e:armory)h.assertTrue(WeaponPlatform.costs(e).stream().noneMatch(c->Arrays.stream(c.ingredient().getItems()).anyMatch(s->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("kubejs"))),"Create Armory retains authored materials without normal receiver gates");
        }finally{registry.createMilestones.clear();registry.createMilestones.addAll(saved);p.getInventory().clearContent();}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="newplatform",timeoutTicks=100)
    public static void allThreeAgesAreSentIndependently(GameTestHelper h){
        var p=player(h,"age-badges");var pos=h.absolutePos(new BlockPos(1,1,1));
        h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,4),3);
        h.getLevel().setBlock(pos.east(),ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,2),3);
        h.getLevel().setBlock(pos.west(),ArsenalBeacon.ATTACHMENT_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,3),3);
        var state=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,""));
        h.assertTrue(state.getInt("gunAge")==4&&state.getInt("ammoAge")==2&&state.getInt("attachmentAge")==3,"Three prominent badges carry actual independent station Ages");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="beaconcombat",timeoutTicks=500)
    public static void nativeMeleeAnimationAndSkeletonArrowsDamageBeacon(GameTestHelper h){
        var l=h.getLevel();var d=CampaignData.get(l);var pos=new BlockPos(512,102,0);var p=player(h,"combat-observer");
        var previousDifficulty=l.getDifficulty();l.getServer().setDifficulty(net.minecraft.world.Difficulty.HARD,true);
        long previousDay=l.getDayTime();l.setDayTime(18000); // Sunburn and an old world's clock must not interrupt the bow fixture.
        arena(l,pos.below());
        d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.beacon=pos;d.phase="preparation";d.campaignSerial++;l.setBlock(pos,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        // Fake observer stays outside interception range. It never joins the server's real player list.
        p.moveTo(pos.getX()+20,pos.getY()+1,pos.getZ()+20,0,0);l.addNewPlayer(p);BeaconCombat.tick(l,d);
        var objective=BeaconCombat.objective(l);h.assertTrue(objective!=null,"An enabled planted beacon creates its attack objective");
        var zombie=EntityType.ZOMBIE.create(l);zombie.moveTo(pos.getX()+2,pos.getY()+1,pos.getZ()+.5,0,0);zombie.setNoGravity(true);l.addFreshEntity(zombie);zombie.setTarget(objective);
        h.assertTrue(!BeaconCombat.inDetectionRange(zombie,objective)==false,"Nearby ambient zombie detects the beacon");
        var far=EntityType.ZOMBIE.create(l);far.moveTo(pos.getX()+30,pos.getY()+1,pos.getZ(),0,0);h.assertTrue(!BeaconCombat.inDetectionRange(far,objective),"Ambient detection is limited to 24 blocks");far.getPersistentData().putBoolean("arsenalRaider",true);h.assertTrue(BeaconCombat.inDetectionRange(far,objective),"Raid attackers still pursue from beyond ambient range");
        var cow=EntityType.COW.create(l);h.assertTrue(!BeaconCombat.hostile(cow),"Passive animals never acquire the beacon objective");
        int original=d.health;var animated=new java.util.concurrent.atomic.AtomicBoolean();var bowUser=new java.util.concurrent.atomic.AtomicReference<Skeleton>();var afterMelee=new java.util.concurrent.atomic.AtomicInteger();
        for(int tick=1;tick<80;tick++)h.runAtTickTime(tick,()->{if(zombie.swinging||zombie.attackAnim>0)animated.set(true);});
        h.runAtTickTime(80,()->{
            h.assertTrue(animated.get(),"Melee attacker performs a real hand-swing animation: ticks="+zombie.tickCount+" target="+zombie.getTarget()+" phase="+d.phase+" health="+d.health);h.assertTrue(d.health<original,"Actual melee strike damages beacon");zombie.discard();
            h.assertTrue(BeaconNetwork.state(p,d).getBoolean("underAttack"),"Actual ambient strikes activate the global warning during preparation");
            afterMelee.set(d.health);var skeleton=EntityType.SKELETON.create(l);skeleton.getRandom().setSeed(2026);skeleton.moveTo(pos.getX()+9.5,pos.getY(),pos.getZ()+.5,0,0);skeleton.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));l.addFreshEntity(skeleton);skeleton.setTarget(objective);bowUser.set(skeleton);
        });
        h.runAtTickTime(480,()->{
                var skeleton=bowUser.get();h.assertTrue(skeleton!=null,"Bow user is initialized");
                h.assertTrue(d.health<afterMelee.get(),"Native skeleton bow releases real arrows that hit the beacon: health="+d.health+" target="+skeleton.getTarget()+" pos="+skeleton.position()+" ticks="+skeleton.tickCount+" LOS="+skeleton.hasLineOfSight(objective));h.assertTrue(Math.abs(skeleton.getX()-objective.getX())>=8,"Ranged attacker stays near ten-block stand-off distance");skeleton.discard();
                int health=d.health;var arrowSource=l.damageSources().arrow(new net.minecraft.world.entity.projectile.Arrow(l,0,0,0),p);h.assertTrue(!objective.hurt(arrowSource,999)&&d.health==health,"Defender fire cannot damage its own beacon");
                l.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);BeaconCombat.tick(l,d);h.assertTrue(!objective.isAlive(),"Offline cleanup removes the attackable objective");d.phase="unplaced";release(l,pos);l.setDayTime(previousDay);l.getServer().setDifficulty(previousDifficulty,true);h.succeed();
        });
    }
    @GameTest(template="empty3x3x3",batch="beaconfirearms",timeoutTicks=500)
    public static void specialForcesFireRealTaczBulletsAtBeacon(GameTestHelper h)throws Exception{
        var l=h.getLevel();var d=CampaignData.get(l);var pos=new BlockPos(768,102,0);var p=player(h,"gun-observer");
        arena(l,pos.below());
        d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.beacon=pos;d.phase="raid";d.raidTier=4;d.spawnRemaining=1;d.campaignSerial++;
        for(var floor:BlockPos.betweenClosed(pos.offset(-12,-1,-12),pos.offset(12,-1,12)))l.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        l.setBlock(pos,ArsenalBeacon.BEACON.get().defaultBlockState(),3);p.moveTo(pos.getX()+20,pos.getY()+1,pos.getZ()+20,0,0);l.addNewPlayer(p);BeaconCombat.tick(l,d);
        var objective=BeaconCombat.objective(l);h.assertTrue(objective!=null,"An enabled planted beacon creates its attack objective");
        h.assertTrue(SpecialForcesRaids.spawn(l,d,pos.offset(10,1,0),false),"Owned assaulter spawns with actual authored weapon");
        var soldierRef=new java.util.concurrent.atomic.AtomicReference<Mob>();var gunRef=new java.util.concurrent.atomic.AtomicReference<ItemStack>();var initialAmmo=new java.util.concurrent.atomic.AtomicInteger();var beforeShots=new java.util.concurrent.atomic.AtomicInteger();var spent=new java.util.concurrent.atomic.AtomicBoolean();
        h.runAtTickTime(20,()->{
            var soldier=(Mob)l.getEntity(d.raiders.iterator().next());h.assertTrue(soldier!=null,"Soldier is indexed after the fresh arena chunks load");soldier.setNoGravity(true);soldier.setTarget(objective);soldierRef.set(soldier);
            var gun=soldier.getMainHandItem();gunRef.set(gun);beforeShots.set(d.health);initialAmmo.set(gun.getOrCreateTag().getInt("GunCurrentAmmoCount"));
        });
        for(int tick=21;tick<250;tick++)h.runAtTickTime(tick,()->{var gun=gunRef.get();if(gun!=null&&gun.getOrCreateTag().getInt("GunCurrentAmmoCount")<initialAmmo.get())spent.set(true);});
        h.runAtTickTime(250,()->{
            var soldier=soldierRef.get();var gun=gunRef.get();h.assertTrue(soldier!=null&&gun!=null,"Native soldier and magazine are initialized");
            h.assertTrue(d.health<beforeShots.get(),"Real Special Forces gunfire damages the objective: health="+d.health+" target="+soldier.getTarget()+" ticks="+soldier.tickCount+" ammo="+gun.getOrCreateTag());
            h.assertTrue(spent.get(),"Native TaCZ shooting consumes soldier magazine ammunition");
            soldier.discard();d.raiders.clear();l.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);BeaconCombat.tick(l,d);d.phase="unplaced";release(l,pos);h.succeed();
        });
    }
    static void arena(net.minecraft.server.level.ServerLevel level,BlockPos pos){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setChunkForced((pos.getX()>>4)+x,(pos.getZ()>>4)+z,true);
        for(var old:level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(14)))old.discard(); // Dispose entities left by a previous failed disposable-server run.
        for(var p:BlockPos.betweenClosed(pos.offset(-12,0,-12),pos.offset(12,6,12)))level.setBlock(p,p.getY()==pos.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
    }
    static void release(net.minecraft.server.level.ServerLevel level,BlockPos pos){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setChunkForced((pos.getX()>>4)+x,(pos.getZ()>>4)+z,false);}
}
