package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EternalDefense.ID)
@PrefixGameTestTemplate(false)
public final class BeaconGameTests {
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void reconnaissanceAndWaveTimerSurviveReload(GameTestHelper h) {
        CampaignData d=new CampaignData();d.waveTicks=1199;d.reconnaissance=0;
        d=CampaignData.load(d.save(new CompoundTag()));
        h.assertTrue(!Rules.highlightAttackers(d.reconnaissance,d.waveTicks),"Attackers remain unmarked until one minute");
        d.waveTicks++;h.assertTrue(Rules.highlightAttackers(d.reconnaissance,d.waveTicks),"Attackers are revealed at 1200 ticks");
        d.reconnaissance=1;d.waveTicks=0;d=CampaignData.load(d.save(new CompoundTag()));
        h.assertTrue(Rules.highlightAttackers(d.reconnaissance,d.waveTicks),"Purchased reconnaissance persists and applies immediately");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void starterKitIsGrantedOncePerPlayer(GameTestHelper h) throws Exception {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("tacz")){h.succeed();return;}
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());player.getInventory().clearContent();player.getPersistentData().remove("eternalStarterGranted");
        EternalDefense.grantStarter(player);EternalDefense.grantStarter(player);
        int guns=0,rounds=0;
        for(ItemStack stack:player.getInventory().items) {
            if(stack.isEmpty())continue;String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if(item.equals("tacz:modern_kinetic_gun")) {
                guns+=stack.getCount();var id=stack.getItem().getClass().getMethod("getGunId",ItemStack.class).invoke(stack.getItem(),stack);
                h.assertTrue(id.toString().equals("tacz:taurus943"),"Starter must be the .22 Model 943 revolver");
                var gunIndex=(java.util.Optional<?>)Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,id);
                h.assertTrue(gunIndex.isPresent(),"The revolver must come with TaCZ itself");
            } else if(item.equals("tacz:ammo")) {
                var id=stack.getItem().getClass().getMethod("getAmmoId",ItemStack.class).invoke(stack.getItem(),stack);
                h.assertTrue(id.toString().equals("tacz:22wmr"),"Starter rounds must match the revolver");rounds+=stack.getCount();
            }
        }
        h.assertTrue(guns==1&&rounds==64,"Logging in again must not duplicate the starter kit");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void repairsKeepSpentAmmunitionSpent(GameTestHelper h) {
        var level=h.getLevel();BlockPos beacon=h.absolutePos(new BlockPos(1,1,1)),chestPos=beacon.east();
        CampaignData d=new CampaignData();d.beacon=beacon;d.phase="raid";
        level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),3);
        ChestBlockEntity chest=(ChestBlockEntity)level.getBlockEntity(chestPos);
        chest.setItem(0,new ItemStack(Items.ARROW,64));
        d.snapshot.put(chestPos.asLong(),level.getBlockState(chestPos));
        // The supply used before destruction must not be recreated by victory repairs.
        chest.removeItem(0,17);
        EternalDefense.damageBlock(level,d,chestPos);
        h.assertTrue(level.getBlockState(chestPos).isAir(),"Raid breach should remove the chest");
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(chestPos).inflate(2)).isEmpty(),"Journaled containers must not spill duplicate loot");
        // Simulate a save/reload between damage and repair.
        d=CampaignData.load(d.save(new CompoundTag()));d.phase="restore";
        EternalDefense.restore(level,d);
        ChestBlockEntity restored=(ChestBlockEntity)level.getBlockEntity(chestPos);
        h.assertTrue(restored!=null&&restored.getItem(0).getCount()==47,"Repairs must preserve 47 arrows, not reset to 64");
        h.assertTrue(d.damage.isEmpty(),"Completed repairs must clear the damage journal");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=40)
    public static void repairsLeaveSurvivingContainersAlone(GameTestHelper h) {
        var level=h.getLevel();BlockPos beacon=h.absolutePos(new BlockPos(1,1,1)),chestPos=beacon.east(),wall=beacon.west();
        CampaignData d=new CampaignData();d.beacon=beacon;d.phase="raid";
        level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),3);level.setBlock(wall,Blocks.STONE_BRICKS.defaultBlockState(),3);
        ((ChestBlockEntity)level.getBlockEntity(chestPos)).setItem(0,new ItemStack(Items.ARROW,31));
        d.snapshot.put(wall.asLong(),level.getBlockState(wall));d.snapshot.put(chestPos.asLong(),level.getBlockState(chestPos));
        EternalDefense.damageBlock(level,d,wall);d.phase="restore";EternalDefense.restore(level,d);
        h.assertTrue(level.getBlockState(wall).is(Blocks.STONE_BRICKS),"Damaged wall must return");
        h.assertTrue(((ChestBlockEntity)level.getBlockEntity(chestPos)).getItem(0).getCount()==31,"An undamaged container must keep its current supplies");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=60)
    public static void createTankKeepsConsumedFluidSpent(GameTestHelper h) {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("create")){h.succeed();return;}
        var level=h.getLevel();BlockPos beacon=h.absolutePos(new BlockPos(1,1,1)),p=beacon.east();
        var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("create:fluid_tank"));
        level.setBlock(p,block.defaultBlockState(),3);
        var cap=level.getBlockEntity(p).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).orElseThrow(()->new IllegalStateException("Create tank fluid capability missing"));
        cap.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA,2000),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        cap.drain(750,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        CampaignData d=new CampaignData();d.beacon=beacon;d.phase="raid";d.snapshot.put(p.asLong(),level.getBlockState(p));
        EternalDefense.damageBlock(level,d,p);d=CampaignData.load(d.save(new CompoundTag()));d.phase="restore";EternalDefense.restore(level,d);
        var restored=level.getBlockEntity(p).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).orElseThrow(()->new IllegalStateException("Rebuilt Create tank fluid capability missing"));
        h.assertTrue(restored.getFluidInTank(0).getAmount()==1250,"Create tank must retain 1250 mB; the consumed 750 mB stays spent");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=60)
    public static void turretDeathKeepsSpentMagazineRoundsSpent(GameTestHelper h) throws Exception {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("tacz_turrets")){h.succeed();return;}
        var level=h.getLevel();BlockPos beacon=h.absolutePos(new BlockPos(1,1,1)),p=beacon.east();
        CampaignData d=CampaignData.get(level);d.beacon=beacon;d.phase="raid";
        level.setBlock(beacon,EternalDefense.BEACON.get().defaultBlockState(),3);
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(new net.minecraft.resources.ResourceLocation("tacz_turrets:turret"));
        var turret=(net.minecraft.world.entity.LivingEntity)type.create(level);turret.moveTo(p.getX()+0.5,p.getY(),p.getZ()+0.5,0,0);level.addFreshEntity(turret);
        var gun=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("tacz:modern_kinetic_gun")));
        var gunItem=gun.getItem();gunItem.getClass().getMethod("setGunId",ItemStack.class,net.minecraft.resources.ResourceLocation.class).invoke(gunItem,gun,new net.minecraft.resources.ResourceLocation("tacz:ak47"));
        gunItem.getClass().getMethod("setCurrentAmmoCount",ItemStack.class,int.class).invoke(gunItem,gun,30);
        turret.getClass().getMethod("setGunStack",ItemStack.class).invoke(turret,gun);
        var loaded=(ItemStack)turret.getClass().getMethod("getGunStack").invoke(turret);
        gunItem.getClass().getMethod("setCurrentAmmoCount",ItemStack.class,int.class).invoke(gunItem,loaded,19);
        var id=turret.getUUID();turret.setHealth(0);var death=new net.minecraftforge.event.entity.living.LivingDeathEvent(turret,level.damageSources().generic());net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(death);
        h.assertTrue(death.isCanceled()&&turret.isRemoved()&&d.destroyedTurrets.size()==1,"Turret death must be journaled without loot drops");
        var saved=CampaignData.load(d.save(new CompoundTag()));saved.phase="restore";
        h.runAfterDelay(2,()->{
            EternalDefense.restore(level,saved);var rebuilt=level.getEntity(id);
            h.assertTrue(rebuilt!=null,"Turret must be rebuilt with its original UUID");
            try {
                var remaining=(ItemStack)rebuilt.getClass().getMethod("getGunStack").invoke(rebuilt);
                int count=(Integer)gunItem.getClass().getMethod("getCurrentAmmoCount",ItemStack.class).invoke(gunItem,remaining);
                h.assertTrue(count==19,"Rebuilt turret must retain 19 rounds, not reset to 30");
            } catch(Exception e){throw new IllegalStateException(e);}
            rebuilt.discard();d.phase="unplaced";d.destroyedTurrets.clear();level.setBlock(beacon,Blocks.AIR.defaultBlockState(),3);h.succeed();
        });
    }
    @GameTest(template="empty3x3x3",batch="lifecycle",timeoutTicks=60)
    public static void confirmationsPreventCreativeBreakAndResetExactlyOnce(GameTestHelper h) {
        var level=h.getLevel();var d=CampaignData.get(level);d.finishDecommission();
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("eternal-defense-confirmations".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"BeaconTests"));
        p.getInventory().clearContent();BlockPos ground=h.absolutePos(new BlockPos(1,0,1));level.setBlock(ground,Blocks.STONE.defaultBlockState(),3);
        p.moveTo(ground.getX()+2.5,ground.getY()+1,ground.getZ()+.5,0,0);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(EternalDefense.BEACON_ITEM.get()));
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0,.5,0),net.minecraft.core.Direction.UP,ground,false);
        var context=new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        EternalDefense.BEACON_ITEM.get().useOn(context);
        h.assertTrue(d.phase.equals("unplaced")&&!level.getBlockState(ground.above()).is(EternalDefense.BEACON.get()),"Requesting placement must not start a campaign or place a block");
        h.assertTrue(!BeaconActions.confirm(p,"wrong-token")&&d.phase.equals("unplaced"),"Invalid confirmation must do nothing");
        BeaconActions.requestPlacement(p,context);String plant=BeaconActions.pendingToken(p);
        h.assertTrue(!plant.isEmpty(),"Placement has no one-minute login cooldown");
        BeaconActions.requestPlacement(p,context);h.assertTrue(plant.equals(BeaconActions.pendingToken(p)),"Automatic startup retry retains the same valid confirmation token");
        h.assertTrue(BeaconActions.confirm(p,plant)&&(d.phase.equals("snapshot")||d.phase.equals("preparation")),"Confirmed placement starts the campaign");
        BlockPos beacon=d.beacon;h.assertTrue(level.getBlockState(beacon).is(EternalDefense.BEACON.get()),"Confirmed beacon must exist physically");
        int before=0;for(ItemStack s:p.getInventory().items)if(s.is(EternalDefense.BEACON_ITEM.get()))before+=s.getCount();
        p.getAbilities().instabuild=true;var breaking=new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,beacon,level.getBlockState(beacon),p);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breaking);p.getAbilities().instabuild=false;
        h.assertTrue(breaking.isCanceled(),"Creative breaking must be blocked until removal is confirmed");
        d.core=3;d.logistics=2;d.defense=2;d.restoration=1;d.reconnaissance=1;d.rewardTier=5;d.victories=9;d.rewards.add(new ItemStack(Items.DIAMOND,8).save(new CompoundTag()));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(EternalDefense.CONTROLLER.get()));
        BeaconActions.requestRemoval(p);String remove=BeaconActions.pendingToken(p);
        h.assertTrue(d.core==3&&level.getBlockState(beacon).is(EternalDefense.BEACON.get()),"Requesting removal must not reset anything");
        h.assertTrue(BeaconActions.confirm(p,remove),"Confirmed controller removal succeeds");
        h.assertTrue(d.phase.equals("unplaced")&&d.core==0&&d.logistics==0&&d.defense==0&&d.restoration==0&&d.reconnaissance==0&&d.rewardTier==0&&d.victories==0&&d.rewards.isEmpty(),"Every campaign upgrade and reward must reset");
        h.assertTrue(level.getBlockState(beacon).isAir(),"Removed beacon must leave the world");
        int after=0;for(ItemStack s:p.getInventory().items)if(s.is(EternalDefense.BEACON_ITEM.get()))after+=s.getCount();
        h.assertTrue(after==before+1&&!BeaconActions.confirm(p,remove),"Removal returns exactly one beacon; replaying confirmation cannot duplicate it");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(EternalDefense.BEACON_ITEM.get()));
        BeaconActions.requestPlacement(p,context);h.assertTrue(BeaconActions.confirm(p,BeaconActions.pendingToken(p))&&d.phase.equals("preparation"),"A packed beacon can be planted again without creating duplicates or stale state");
        EternalDefense.decommission(level,d,null);p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="lifecycle",timeoutTicks=60)
    public static void missingLegacyBeaconRetainsRepairJournalAcrossReset(GameTestHelper h) {
        var level=h.getLevel();var d=CampaignData.get(level);d.finishDecommission();BlockPos beacon=h.absolutePos(new BlockPos(1,1,1)),chest=beacon.east();
        level.setBlock(beacon,Blocks.AIR.defaultBlockState(),3);level.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);
        ((ChestBlockEntity)level.getBlockEntity(chest)).setItem(0,new ItemStack(Items.ARROW,47));
        d.beacon=beacon;d.phase="raid";d.core=3;d.snapshot.put(chest.asLong(),level.getBlockState(chest));EternalDefense.damageBlock(level,d,chest);
        h.assertTrue(EternalDefense.reconcileMissing(level,d)&&d.phase.equals("decommissioning")&&d.damage.size()==1&&d.core==0,"Missing beacon clears progression while retaining unprocessed machine journals");
        var saved=CampaignData.load(d.save(new CompoundTag()));EternalDefense.restore(level,saved);
        h.assertTrue(saved.phase.equals("unplaced")&&saved.damage.isEmpty(),"Reset can finish only after journal recovery");
        h.assertTrue(((ChestBlockEntity)level.getBlockEntity(chest)).getItem(0).getCount()==47,"Reset repairs preserve spent ammunition");
        d.damage.clear();d.destroyedTurrets.clear();d.finishDecommission();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="lifecycle",timeoutTicks=40)
    public static void earlyRaidersLoseHeavyGearAndModdedAttributes(GameTestHelper h) {
        var mob=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.NETHERITE_HELMET));
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new ItemStack(Items.NETHERITE_CHESTPLATE));
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Items.NETHERITE_SWORD));
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));mob.setBaby(true);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);mob.setHealth(500);
        EternalDefense.balanceEarly(mob,0,2);
        h.assertTrue(mob.getMaxHealth()==24&&mob.getHealth()==24&&!mob.isBaby(),"Early wave two uses bounded adult zombie health");
        for(var slot:net.minecraft.world.entity.EquipmentSlot.values())h.assertTrue(mob.getItemBySlot(slot).isEmpty(),"Early zombies cannot retain heavy armor, weapons or offhand gear");
        h.assertTrue(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==2,"Early melee damage is controlled by the campaign");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="lifecycle",timeoutTicks=40)
    public static void guideAndControllerAreGrantedOnceAndStateMatchesTheZone(GameTestHelper h) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("eternal-defense-support".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"GuideTests"));
        p.getInventory().clearContent();p.getPersistentData().remove("eternalSupportGranted03");EternalDefense.grantSupport(p);EternalDefense.grantSupport(p);
        int guides=0,controllers=0;for(var stack:p.getInventory().items){if(stack.is(EternalDefense.GUIDE.get()))guides+=stack.getCount();if(stack.is(EternalDefense.CONTROLLER.get()))controllers+=stack.getCount();}
        h.assertTrue(guides==1&&controllers==1,"Existing and new players receive the guide and controller once");
        var d=new CampaignData();d.beacon=h.absolutePos(new BlockPos(1,1,1));d.phase="disabled";d.health=0;d.core=2;p.moveTo(d.beacon.getX()+.5,d.beacon.getY()+1,d.beacon.getZ()+.5,0,0);
        h.assertTrue(d.inside(d.beacon.offset(18,d.above(),0))&&!d.inside(d.beacon.offset(19,0,0))&&!d.inside(d.beacon.above(d.above()+1)),"Scoring volume matches the displayed radius and height");
        var state=BeaconNetwork.state(p,d);h.assertTrue(state.getInt("radius")==18&&state.getInt("health")==0&&state.getBoolean("near")&&state.getBoolean("installed"),"Menu and outline receive authoritative campaign state");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());BeaconNetwork.State.encode(new BeaconNetwork.State(state,"menu","",""),buffer);var decoded=BeaconNetwork.State.decode(buffer);buffer.release();
        h.assertTrue(decoded.data().equals(state)&&decoded.screen().equals("menu"),"Menu state survives network encoding");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="lifecycle",timeoutTicks=40)
    public static void packedCampaignCannotReloadOldAttackers(GameTestHelper h) {
        var d=CampaignData.get(h.getLevel());d.finishDecommission();var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());zombie.getPersistentData().putBoolean("eternalRaider",true);
        var loaded=new net.minecraftforge.event.entity.EntityJoinLevelEvent(zombie,h.getLevel(),true);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(loaded);
        h.assertTrue(loaded.isCanceled()&&zombie.isRemoved(),"Raid attackers from unloaded chunks must disappear after their campaign is packed up");h.succeed();
    }
}
