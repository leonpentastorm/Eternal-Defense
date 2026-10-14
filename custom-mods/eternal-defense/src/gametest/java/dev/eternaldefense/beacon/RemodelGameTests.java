package dev.eternaldefense.beacon;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(EternalDefense.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class RemodelGameTests {
    public static final class DropCapture {
        final net.minecraft.server.level.ServerLevel level;final BlockPos root;final List<ItemEntity> items=new ArrayList<>();
        DropCapture(net.minecraft.server.level.ServerLevel level,BlockPos root){this.level=level;this.root=root;}
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public void join(net.minecraftforge.event.entity.EntityJoinLevelEvent event){if(event.getLevel()==level&&event.getEntity() instanceof ItemEntity item&&item.distanceToSqr(root.getX()+.5,root.getY()+.5,root.getZ()+.5)<16)items.add(item);}
    }
    @GameTest(template="empty3x3x3",batch="bench010",timeoutTicks=100)
    public static void allBenchesOccupyTwoRotatedCellsAndMineOnceWithAge(GameTestHelper h){
        var l=h.getLevel();var floor=new BlockPos(4096,101,0);UpgradeGameTests.arena(l,floor);var root=floor.above();var p=UpgradeGameTests.player(h,"benches010");p.setGameMode(GameType.SURVIVAL);p.setPos(root.getX(),root.getY()+1,root.getZ()-4);
        boolean previousDrops=l.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);l.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,l.getServer());var capture=new DropCapture(l,root);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(capture);
        try{for(var block:List.of(EternalDefense.GUN_PLATFORM.get(),EternalDefense.AMMO_PLATFORM.get(),EternalDefense.ATTACHMENT_PLATFORM.get()))for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
            var state=block.defaultBlockState().setValue(WeaponPlatform.AGE,4).setValue(DefenseStructures.FACING,facing).setValue(DefenseStructures.WIDE,true).setValue(DefenseStructures.TALL,true);var second=DefenseStructures.second(root,state);h.assertTrue(DefenseStructures.available(l,root,state),"Both cells are available before placement");l.setBlock(root,state,3);
            h.assertTrue(l.getBlockState(second).is(EternalDefense.STRUCTURE_PART.get())&&DefenseStructures.anchor(l,second).equals(root),"Second cell forwards to its rotated anchor: "+facing);
            h.assertTrue(!l.getBlockState(second).getCollisionShape(l,second).isEmpty(),"Second half has real model collision");h.assertTrue(l.getBlockEntity(root) instanceof WeaponPlatform.StationEntity,"Bench has a renderer entity");
            var local=DefenseStructures.full(state).bounds();h.assertTrue(Math.max(local.getXsize(),local.getZsize())>1.99,"Model and collision are doubled along bench width");
            var items=l.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(4));items.forEach(net.minecraft.world.entity.Entity::discard);
            capture.items.clear();
            var part=l.getBlockState(second);part.getBlock().playerWillDestroy(l,second,part,p);l.setBlock(second,Blocks.AIR.defaultBlockState(),3);
            var drops=capture.items.stream().filter(e->e.getItem().is(block.asItem())).toList();h.assertTrue(l.getBlockState(root).isAir()&&l.getBlockState(root.above()).isAir()&&l.getBlockState(second.above()).isAir()&&l.getBlockState(second).isAir()&&drops.size()==1&&drops.get(0).getItem().getCount()==1,"Mining either half emits exactly one real item entity: root="+l.getBlockState(root)+" part="+l.getBlockState(second)+" drops="+drops+" item="+block.asItem());h.assertTrue(drops.get(0).getItem().getTag().getCompound("BlockStateTag").getString("age").equals("4"),"The bench keeps its Age");drops.forEach(net.minecraft.world.entity.Entity::discard);
        }}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(capture);l.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(previousDrops,l.getServer());}UpgradeGameTests.release(l,floor);h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="space010",timeoutTicks=100)
    public static void benchSpaceChecksAndBeaconExpansionNeverOverwriteBlocks(GameTestHelper h){
        var l=h.getLevel();var floor=new BlockPos(4352,101,0);UpgradeGameTests.arena(l,floor);var root=floor.above();var d=new CampaignData();d.beacon=root;d.phase="preparation";
        l.setBlock(root,EternalDefense.BEACON.get().defaultBlockState(),3);
        for(int i=0;i<3;i++){d.core=i;h.assertTrue(DefenseStructures.syncBeacon(l,d)&&l.getBlockState(root).getValue(DefenseStructures.MK)==i+1,"Core level chooses exactly one Mk");}
        var obstruction=root.east();l.setBlock(obstruction,Blocks.CHEST.defaultBlockState(),3);d.core=3;h.assertTrue(!DefenseStructures.syncBeacon(l,d)&&l.getBlockState(obstruction).is(Blocks.CHEST)&&l.getBlockState(root).getValue(DefenseStructures.MK)==3,"Blocked legacy expansion keeps the old model and existing inventory block");l.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(DefenseStructures.syncBeacon(l,d)&&DefenseStructures.cells(root,l.getBlockState(root)).size()==17,"Mk-4 reserves the full 3x3x2 installation");for(var cell:DefenseStructures.cells(root,l.getBlockState(root)))h.assertTrue(l.getBlockState(cell).is(EternalDefense.STRUCTURE_PART.get())&&DefenseStructures.anchor(l,cell).equals(root),"Every Mk-4 cell has the correct owner");
        l.setBlock(root,Blocks.AIR.defaultBlockState(),3);for(var cell:BlockPos.betweenClosed(root.offset(-1,0,-1),root.offset(1,1,1)))h.assertTrue(!l.getBlockState(cell).is(EternalDefense.STRUCTURE_PART.get()),"Removing the beacon clears all footprint cells");
        var state=EternalDefense.GUN_PLATFORM.get().defaultBlockState().setValue(DefenseStructures.WIDE,true).setValue(DefenseStructures.TALL,true);l.setBlock(root.east(),Blocks.STONE.defaultBlockState(),3);h.assertTrue(!DefenseStructures.available(l,root,state),"A two-block bench rejects an occupied second cell");UpgradeGameTests.release(l,floor);h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="hurtbox010",timeoutTicks=100)
    public static void damageVolumeAndSavedToggleMatchAllFourMarks(GameTestHelper h){
        var root=h.absolutePos(new BlockPos(1,1,1));var target=EternalDefense.OBJECTIVE.get().create(h.getLevel());
        for(int mark=1;mark<=4;mark++){target.setMark(mark);target.setPos(root.getX()+.5,root.getY(),root.getZ()+.5);var expected=DefenseStructures.hurtbox(root,mark);var actual=target.getBoundingBox();h.assertTrue(actual.equals(expected),"Displayed damage outline exactly matches the attackable entity at Mk-"+mark+": "+actual+" vs "+expected);}
        var old=new CampaignData();old.core=4;var saved=old.save(new CompoundTag());saved.remove("showHurtbox");var migrated=CampaignData.load(saved);h.assertTrue(migrated.core==3&&migrated.maximumHealth()==2000&&migrated.radius()==24&&migrated.showHurtbox,"Old max Core migrates to Mk-4, preserves maximum stats, and enables its new outline");migrated.showHurtbox=false;h.assertTrue(!CampaignData.load(migrated.save(new CompoundTag())).showHurtbox,"Hurtbox visibility persists independently");h.assertTrue(Rules.branchMaximum("core")==3,"Only three Core upgrades exist");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="benchrepair010",timeoutTicks=100)
    public static void raidDamageToBenchSecondHalfJournalsOwnerAndRestoresBoth(GameTestHelper h){
        var l=h.getLevel();var floor=new BlockPos(4608,101,0);UpgradeGameTests.arena(l,floor);var root=floor.above();var state=EternalDefense.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5).setValue(DefenseStructures.WIDE,true).setValue(DefenseStructures.TALL,true);l.setBlock(root,state,3);var second=DefenseStructures.second(root,state);var d=new CampaignData();d.beacon=root.west(4);d.phase="raid";d.snapshot.put(root.asLong(),state);d.snapshot.put(second.asLong(),l.getBlockState(second));EternalDefense.damageBlock(l,d,second);
        h.assertTrue(l.getBlockState(root).isAir()&&l.getBlockState(second).isAir()&&d.damage.containsKey(root.asLong())&&d.damage.size()==1,"Damage to either half journals and removes one owner, without duplicate drops");d.phase="restore";d.victoryRestoration=true;EternalDefense.restore(l,d);
        h.assertTrue(l.getBlockState(root)==state&&l.getBlockState(second).is(EternalDefense.STRUCTURE_PART.get())&&l.getBlockEntity(root) instanceof WeaponPlatform.StationEntity,"Victory restores Age, entity and footprint as one station");UpgradeGameTests.release(l,floor);h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="core010",timeoutTicks=100)
    public static void obstructedPaidCoreUpgradeIsAtomicAndAllPartsUseConfirmation(GameTestHelper h){
        var l=h.getLevel();var floor=new BlockPos(4864,101,0);UpgradeGameTests.arena(l,floor);var root=floor.above();var d=CampaignData.get(l);d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.beacon=root;d.phase="preparation";d.core=2;var state=EternalDefense.BEACON.get().defaultBlockState().setValue(DefenseStructures.MK,3);l.setBlock(root,state,3);var p=UpgradeGameTests.player(h,"upgrade010");p.setGameMode(GameType.SURVIVAL);p.setPos(root.getX()+.5,root.getY(),root.getZ()-3);
        int count=Rules.upgradeCost(2);p.getInventory().add(new ItemStack(EternalDefense.PLATING.get(),count));l.setBlock(root.east(),Blocks.STONE.defaultBlockState(),3);EternalDefense.upgrade(p,"core");h.assertTrue(d.core==2&&p.getInventory().countItem(EternalDefense.PLATING.get())==count,"Blocked upgrade consumes no materials and no level");l.setBlock(root.east(),Blocks.AIR.defaultBlockState(),3);EternalDefense.upgrade(p,"core");h.assertTrue(d.core==3&&l.getBlockState(root).getValue(DefenseStructures.MK)==4&&p.getInventory().countItem(EternalDefense.PLATING.get())==0,"Clear paid expansion reaches Mk-4 in one atomic upgrade");
        p.getInventory().items.set(0,new ItemStack(EternalDefense.CONTROLLER.get()));var edge=root.east();l.getBlockState(edge).use(l,p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(edge),Direction.NORTH,edge,false));var token=BeaconActions.pendingToken(p);h.assertTrue(!token.isEmpty()&&d.installed(),"Shovel on outer cell asks for confirmation without removing yet");h.assertTrue(BeaconActions.confirm(p,token),"Confirmed removal succeeds from outer cell");for(var cell:BlockPos.betweenClosed(root.offset(-1,0,-1),root.offset(1,1,1)))h.assertTrue(!l.getBlockState(cell).is(EternalDefense.STRUCTURE_PART.get()),"Confirmed removal leaves no invisible parts");p.getInventory().clearContent();UpgradeGameTests.release(l,floor);h.succeed();
    }
}
