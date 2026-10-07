package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Opt-in tests on real Forge servers: -Darsenal.messHallTests=true, then /mess-hall-test. */
@net.minecraftforge.gametest.GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class MessHallGameTests {
    private record Fixture(ServerLevel level,BlockPos floor,BlockPos root,MessHall.HallEntity hall,ServerPlayer player){}
    private static Fixture fixture(GameTestHelper h,int index){
        var l=h.getLevel();var floor=new BlockPos(6144+index*64,100,0);UpgradeGameTests.arena(l,floor);var root=floor.above();
        var d=CampaignData.get(l);d.resetProgress();d.damage.clear();d.destroyedTurrets.clear();d.core=3;d.phase="preparation";d.beacon=root.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        l.setBlock(root,ArsenalBeacon.MESS_HALL_I.get().defaultBlockState(),3);var p=UpgradeGameTests.player(h,"kitchen-"+index);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(root.getX()+.5,root.getY(),root.getZ()-1);
        return new Fixture(l,floor,root,(MessHall.HallEntity)l.getBlockEntity(root),p);
    }
    private static void finish(GameTestHelper h,Fixture f){PlayerMeals.clear(f.player);f.player.closeContainer();UpgradeGameTests.release(f.level,f.floor);CampaignData.get(f.level).phase="unplaced";h.succeed();}
    private static CookPot.PotEntity pot(Fixture f,int dx){var pos=f.root.east(dx);f.level.setBlock(pos,ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);return (CookPot.PotEntity)f.level.getBlockEntity(pos);}
    private static void inputs(MessHall.HallEntity hall,int count){hall.ingredients.clearContent();hall.ingredients.setItem(0,new ItemStack(Items.BREAD,count));hall.ingredients.setItem(1,new ItemStack(Items.COOKED_BEEF,count));hall.ingredients.setItem(2,new ItemStack(Items.CARROT,count));hall.ingredients.setItem(3,new ItemStack(Items.COOKED_COD,count));hall.ingredients.setItem(4,new ItemStack(Items.BROWN_MUSHROOM,count));}
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void kitchenFootprintsRotateAndBreakAsOne(GameTestHelper h){
        var f=fixture(h,0);var l=f.level;l.removeBlock(f.root,false);
        for(var direction:Direction.Plane.HORIZONTAL){
            for(var block:List.of(ArsenalBeacon.MESS_HALL_I.get(),ArsenalBeacon.MESS_HALL_II.get(),ArsenalBeacon.MESS_HALL_III.get(),ArsenalBeacon.MESS_HALL_IV.get(),ArsenalBeacon.COOK_POT.get())){
                var state=block.defaultBlockState().setValue(ArsenalStructures.FACING,direction);var cells=ArsenalStructures.cells(f.root,state);h.assertTrue(cells.size()==(block instanceof MessHall.HallBlock?3:1),"Correct horizontal footprint");
                l.setBlock(cells.get(0),Blocks.OBSIDIAN.defaultBlockState(),3);h.assertTrue(!ArsenalStructures.available(l,f.root,state),"Cannot overwrite an obstruction");l.removeBlock(cells.get(0),false);l.setBlock(f.root,state,3);
                for(var cell:cells){h.assertTrue(cell.getY()==f.root.getY()&&ArsenalStructures.anchor(l,cell).equals(f.root),"Owned cells resolve the anchor without reserving height");h.assertTrue(!l.getBlockState(cell).getCollisionShape(l,cell).isEmpty(),"Every footprint cell collides");}
                var part=cells.get(0);l.getBlockState(part).getBlock().playerWillDestroy(l,part,l.getBlockState(part),f.player);l.removeBlock(part,false);
                h.assertTrue(l.getBlockState(f.root).isAir(),"Breaking a part removes the anchor");for(var cell:cells)h.assertTrue(l.getBlockState(cell).isAir(),"No invisible cells remain");
            }
        }
        var pending=ArsenalBeacon.MESS_HALL_I.get().defaultBlockState();var blocked=ArsenalStructures.cells(f.root,pending).get(0);l.setBlock(blocked,Blocks.OBSIDIAN.defaultBlockState(),3);l.setBlock(f.root,pending,3);h.assertTrue(l.getBlockState(blocked).is(Blocks.OBSIDIAN),"Deferred footprint installation never overwrites an obstruction");l.removeBlock(blocked,false);pending.getBlock().tick(pending,l,f.root,l.random);for(var cell:ArsenalStructures.cells(f.root,pending))h.assertTrue(ArsenalStructures.anchor(l,cell).equals(f.root),"Deferred footprint rebuild succeeds when cells become available");finish(h,f);
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void cookingIsAtomicAcrossSharedMenusAndSandwichesAreEatable(GameTestHelper h){
        var f=fixture(h,1);h.assertTrue(!IngredientTraits.traits.isEmpty(),"Ingredient datapack loaded");inputs(f.hall,1);
        f.hall.ingredients.setItem(3,ItemStack.EMPTY);f.hall.ingredients.setItem(4,ItemStack.EMPTY);
        var a=new MessHallMenu(1,f.player.getInventory(),f.hall.ingredients,f.root,1,f.hall);var other=UpgradeGameTests.player(h,"kitchen-other");other.setPos(f.player.getX(),f.player.getY(),f.player.getZ());var b=new MessHallMenu(2,other.getInventory(),f.hall.ingredients,f.root,1,f.hall);
        h.assertTrue(a.prepare()&&!b.prepare(),"Only the first cook can consume the last batch");var stack=f.hall.ingredients.getItem(6).copy();var meal=PreparedSandwich.meal(stack);h.assertTrue(meal!=null&&meal.bonuses().size()==2&&!meal.stew(),"Portable item carries exactly two selected effects");
        h.assertTrue(PreparedSandwich.meal(ItemStack.of(stack.save(new CompoundTag()))).equals(meal),"Sandwich composition survives item serialization");
        var result=stack.finishUsingItem(f.level,f.player);h.assertTrue(result.isEmpty()&&PlayerMeals.get(f.level).players.get(f.player.getUUID()).meal.equals(meal),"Normal item eating consumes the ration and applies owned meal state");
        inputs(f.hall,2);for(int i=2;i<6;i++)f.hall.ingredients.setItem(i,ItemStack.EMPTY);f.hall.ingredients.setItem(6,new ItemStack(Items.STONE));int before=f.hall.ingredients.getItem(0).getCount();h.assertTrue(!a.prepare()&&f.hall.ingredients.getItem(0).getCount()==before,"Blocked output never consumes ingredients");
        f.hall.ingredients.setItem(6,ItemStack.EMPTY);f.hall.ingredients.setItem(5,new ItemStack(Items.MILK_BUCKET));h.assertTrue(a.prepare()&&f.player.getInventory().countItem(Items.BUCKET)==1,"Consumed ingredient returns its empty bucket");
        f.player.setPos(f.root.getX()+100,f.root.getY(),f.root.getZ());h.assertTrue(!a.clickMenuButton(f.player,2),"Remote menu actions are rejected");finish(h,f);
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void stewServingPersistenceAndLinkCleanup(GameTestHelper h){
        var f=fixture(h,2);var pot=pot(f,3);h.assertTrue(pot.connect()==f.hall,"Nearby pot connects");var extra=pot(f,6);h.assertTrue(extra.connect()==null,"Mk I enforces one linked pot");inputs(f.hall,1);
        f.hall.ingredients.setItem(4,ItemStack.EMPTY);
        var menu=new MessHallMenu(1,f.player.getInventory(),f.hall.ingredients,f.root,1,f.hall);menu.clickMenuButton(f.player,1);h.assertTrue(menu.prepare()&&pot.servings==4&&pot.stew.bonuses().size()==3,"Stew menu stocks three effects and four servings");
        var other=UpgradeGameTests.player(h,"kitchen-serving-other");other.setPos(pot.getBlockPos().getX(),pot.getBlockPos().getY(),pot.getBlockPos().getZ());f.player.setPos(other.getX(),other.getY(),other.getZ());
        f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOWL));other.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOWL));
        pot.use(f.player,InteractionHand.MAIN_HAND);pot.use(other,InteractionHand.MAIN_HAND);h.assertTrue(pot.servings==2&&f.player.getMainHandItem().is(Items.BOWL)&&other.getMainHandItem().is(Items.BOWL),"Independent players consume one serving each and keep empty bowls");
        var reloaded=new CookPot.PotEntity(pot.getBlockPos(),pot.getBlockState());reloaded.load(pot.saveWithoutMetadata());h.assertTrue(reloaded.servings==2&&reloaded.stew.equals(pot.stew)&&reloaded.belongs(f.hall),"Composition, strengths, servings and hall identity persist");
        var hallReload=new MessHall.HallEntity(f.root,f.hall.getBlockState());hallReload.load(f.hall.saveWithoutMetadata());h.assertTrue(hallReload.identity.equals(f.hall.identity)&&hallReload.links.equals(f.hall.links),"Hall reservations and identity persist");
        pot.use(f.player,InteractionHand.MAIN_HAND);pot.use(other,InteractionHand.MAIN_HAND);pot.use(other,InteractionHand.MAIN_HAND);h.assertTrue(pot.empty()&&pot.servings==0,"No negative servings or fifth meal");
        f.level.removeBlock(f.root,false);h.assertTrue(pot.linkedHall==null,"Removing a hall unlinks loaded pots safely");f.level.setBlock(f.root,ArsenalBeacon.MESS_HALL_I.get().defaultBlockState(),3);h.assertTrue(pot.connect()!=null,"Pot reconnects after hall replacement");
        var current=(MessHall.HallEntity)f.level.getBlockEntity(f.root);pot.fill(current,reloaded.stew,4);pot.use(f.player,InteractionHand.MAIN_HAND);
        var campaign=CampaignData.get(f.level);var part=ArsenalStructures.cells(pot.getBlockPos(),pot.getBlockState()).get(0);campaign.snapshot.put(pot.getBlockPos().asLong(),pot.getBlockState());campaign.snapshot.put(part.asLong(),f.level.getBlockState(part));campaign.phase="raid";
        ArsenalBeacon.damageBlock(f.level,campaign,part);ArsenalBeacon.restore(f.level,campaign);var repairedPot=(CookPot.PotEntity)f.level.getBlockEntity(pot.getBlockPos());h.assertTrue(repairedPot.servings==3&&repairedPot.validLink()==current,"Raid repair keeps live servings and restores the whole pot without refreshing eaten food");
        current.ingredients.setItem(0,new ItemStack(Items.BREAD,5));current.ingredients.removeItem(0,2);var hallPart=ArsenalStructures.cells(f.root,current.getBlockState()).get(0);campaign.snapshot.put(f.root.asLong(),current.getBlockState());campaign.snapshot.put(hallPart.asLong(),f.level.getBlockState(hallPart));campaign.phase="raid";
        ArsenalBeacon.damageBlock(f.level,campaign,hallPart);ArsenalBeacon.restore(f.level,campaign);current=(MessHall.HallEntity)f.level.getBlockEntity(f.root);h.assertTrue(current.ingredients.getItem(0).getCount()==3&&repairedPot.validLink()==current,"Raid repair preserves spent ingredients and hall identity");
        f.level.removeBlock(pot.getBlockPos(),false);current.prune();h.assertTrue(current.links.isEmpty(),"Removing a pot frees the capacity slot");PlayerMeals.clear(other);finish(h,f);
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void fieldTimerHomeStrengthAndPlayerLifecycle(GameTestHelper h){
        var f=fixture(h,3);var meal=new MealData(false,List.of(new MealData.Bonus(MealRules.Effect.VITALITY,1),new MealData.Bonus(MealRules.Effect.MOBILITY,1)),MealRules.FIELD_TICKS);PlayerMeals.eat(f.player,meal);var s=PlayerMeals.get(f.level).players.get(f.player.getUUID());
        h.assertTrue(s.home&&f.player.getMaxHealth()==28,"Home doubles the health meal modifier");int initial=s.remaining;for(int i=0;i<50;i++)PlayerMeals.tick(f.player);h.assertTrue(s.remaining==initial,"Home freezes duration");
        f.player.setPos(f.root.getX()+30,f.root.getY(),f.root.getZ());for(int i=0;i<240;i++)PlayerMeals.tick(f.player);h.assertTrue(!s.home&&s.remaining==initial-240&&f.player.getMaxHealth()==24,"Field timer decrements and strength normalizes");
        f.player.setPos(f.root.getX(),f.root.getY(),f.root.getZ());PlayerMeals.tick(f.player);h.assertTrue(s.remaining==initial-240&&s.home,"Re-entry freezes remaining time without resetting");
        var saved=PlayerMeals.get(f.level).save(new CompoundTag());var loaded=PlayerMeals.load(saved).players.get(f.player.getUUID());h.assertTrue(loaded.remaining==s.remaining&&loaded.meal.equals(meal),"Player meal survives save/restart without consuming offline time");
        new PlayerMeals.MealEvents().login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(f.player));h.assertTrue(s.remaining==initial-240&&f.player.getMaxHealth()==28,"Login re-applies transient ownership without advancing the timer");
        PlayerMeals.eat(f.player,new MealData(false,List.of(new MealData.Bonus(MealRules.Effect.FORTITUDE,1)),MealRules.FIELD_TICKS));h.assertTrue(f.player.getMaxHealth()==20,"Replacement removes old meal modifiers");
        new PlayerMeals.MealEvents().death(new net.minecraftforge.event.entity.living.LivingDeathEvent(f.player,f.level.damageSources().generic()));h.assertTrue(!PlayerMeals.get(f.level).players.containsKey(f.player.getUUID())&&f.player.getArmorValue()==0,"Death clears state and owned bonuses");finish(h,f);
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void tierUpgradePreservesFoodLinksAndDoesNotBypassPayment(GameTestHelper h){
        var f=fixture(h,4);var pot=pot(f,3);pot.connect();inputs(f.hall,3);var identity=f.hall.identity;var saved=f.hall.saveWithoutMetadata();
        h.assertTrue(!f.hall.upgrade(f.player)&&saved.equals(f.hall.saveWithoutMetadata()),"Missing currency leaves the hall unchanged");
        f.player.getInventory().add(new ItemStack(Economy.kitchen(1).item(),Economy.kitchen(1).amount()));h.assertTrue(f.hall.upgrade(f.player),"Paid upgrade succeeds");var upgraded=(MessHall.HallEntity)f.level.getBlockEntity(f.root);
        h.assertTrue(upgraded.mk()==2&&upgraded.identity.equals(identity)&&upgraded.ingredients.getItem(0).getCount()==3&&pot.validLink()==upgraded,"Tier change retains identity, ingredients and links");
        var second=pot(f,6);h.assertTrue(second.connect()==upgraded,"Mk II unlocks a second pot");for(int mk=2;mk<4;mk++){f.player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);upgraded.upgrade(f.player);upgraded=(MessHall.HallEntity)f.level.getBlockEntity(f.root);}
        h.assertTrue(upgraded.mk()==4&&upgraded.tier().servings()==16&&upgraded.tier().pots()==4,"Progression reaches communal Mk IV capacity");finish(h,f);
    }
    private static final BlockPos PERSIST=new BlockPos(7183,101,0);
    private static MessHall.HallEntity unloadingHall;
    static void chunkFixture(ServerLevel l,String mode){
        var log=com.mojang.logging.LogUtils.getLogger();
        if(mode.equals("unload")){unloadingHall=(MessHall.HallEntity)l.getBlockEntity(PERSIST);if(unloadingHall==null)throw new IllegalStateException("Seed or check the fixture first");CampaignData.get(l).phase="unplaced";UpgradeGameTests.release(l,PERSIST.below());log.info("MESS_HALL_CHUNKS_RELEASED");}
        else if(mode.equals("check_unloaded")){
            if(l.hasChunkAt(PERSIST)||l.hasChunkAt(PERSIST.east(3))){log.info("MESS_HALL_CHUNKS_PENDING");return;}
            int reserved=unloadingHall.links.size();unloadingHall.prune();if(reserved!=1||unloadingHall.links.size()!=reserved)throw new IllegalStateException("Unloaded reservation was pruned");log.info("MESS_HALL_CHUNK_UNLOAD_PASS");
        }else{l.getChunkAt(PERSIST);l.getChunkAt(PERSIST.east(3));persistence(l,false);log.info("MESS_HALL_CHUNK_RELOAD_PASS");}
    }
    private static UUID persistentPlayer(){return UUID.nameUUIDFromBytes("mess-hall-restart-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static void persistence(ServerLevel l,boolean seed){
        if(seed){UpgradeGameTests.arena(l,PERSIST.below());var d=CampaignData.get(l);d.core=3;d.phase="preparation";d.beacon=PERSIST.west(4);d.setDirty();l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);l.setBlock(PERSIST,ArsenalBeacon.MESS_HALL_IV.get().defaultBlockState(),3);var hall=(MessHall.HallEntity)l.getBlockEntity(PERSIST);inputs(hall,7);l.setBlock(PERSIST.east(3),ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);var pot=(CookPot.PotEntity)l.getBlockEntity(PERSIST.east(3));pot.connect();var meal=IngredientTraits.compose(hall.ingredients,true).meal();pot.fill(hall,meal,9);var meals=PlayerMeals.get(l);meals.players.put(persistentPlayer(),new PlayerMeals.State(meal,12345));meals.setDirty();}
        else{l.getChunkAt(PERSIST);var hall=(MessHall.HallEntity)l.getBlockEntity(PERSIST);var pot=(CookPot.PotEntity)l.getBlockEntity(PERSIST.east(3));var meal=PlayerMeals.get(l).players.get(persistentPlayer());if(hall==null||hall.mk()!=4||hall.ingredients.getItem(0).getCount()!=7||pot==null||pot.servings!=9||pot.validLink()!=hall||pot.stew.bonuses().size()!=3||meal==null||meal.remaining!=12345)throw new IllegalStateException("Persistent kitchen fixture did not survive server restart");}
        com.mojang.logging.LogUtils.getLogger().info(seed?"MESS_HALL_PERSIST_SEEDED":"MESS_HALL_PERSIST_PASS");
    }
    public static final class KitchenTestRunner {
        private MultipleTestTracker tracker;
        @net.minecraftforge.eventbus.api.SubscribeEvent public void commands(net.minecraftforge.event.RegisterCommandsEvent e){
            GameTestRegistry.register(MessHallGameTests.class);e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-test").requires(s->s.hasPermission(2)).executes(c->{var names=List.of("kitchenfootprintsrotate","cookingisatomic","stewservingpersistence","fieldtimerhome","tierupgradepreserves");var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->names.stream().anyMatch(n->t.getTestName().toLowerCase(Locale.ROOT).contains(n))).toList();if(tests.size()!=5)throw new IllegalStateException("Expected five Mess Hall tests, got "+tests.size());tracker=new MultipleTestTracker(GameTestRunner.runTests(tests,new BlockPos(0,130,0),Rotation.NONE,c.getSource().getLevel(),GameTestTicker.SINGLETON,4));return 1;}));
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-fixture").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.literal("seed").executes(c->{persistence(c.getSource().getLevel(),true);return 1;})).then(net.minecraft.commands.Commands.literal("check").executes(c->{persistence(c.getSource().getLevel(),false);return 1;})));
            for(String mode:List.of("unload","check_unloaded","reload"))e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-chunks").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.literal(mode).executes(c->{chunkFixture(c.getSource().getLevel(),mode);return 1;})));
        }
        @net.minecraftforge.eventbus.api.SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;GameTestTicker.SINGLETON.tick();if(!tracker.isDone())return;var log=com.mojang.logging.LogUtils.getLogger();for(var t:tracker.getFailedRequired())log.error("Mess Hall test failed: "+t.getTestName(),t.getError());if(tracker.getFailedRequiredCount()>0)log.error("{} mess hall tests failed",tracker.getFailedRequiredCount());else log.info("All {} mess hall tests passed",tracker.getTotalCount());tracker=null;}
    }
}
