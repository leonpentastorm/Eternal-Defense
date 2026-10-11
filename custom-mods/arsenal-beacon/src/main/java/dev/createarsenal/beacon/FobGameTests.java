package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

/**
 * Forward Operating Bases, slice 1 (0.0.23), on a real server level. Opt-in: start the game with {@code -Darsenal.fobTests=true}, then
 * {@code /fob-test}, or let tools/qa QaFob start them. THROWAWAY WORLDS ONLY: the tests clear the world's FOB records, need the campaign's
 * beacon (they borrow the hunt tests' base when none is planted) and move the first online player, because a FOB is planted, raided and
 * shopped at by a real player. A small village (a bell, three composters, two beds and NoAI villagers) is built on a stone platform high in
 * the sky, wide enough ({@value #CHUNKS} chunks each way) for vanilla to find raid spawn spots 64 blocks out.
 */
@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class FobGameTests {
    static final int CHUNKS=5;
    static final BlockPos VILLAGE=new BlockPos(30008,HuntGameTests.TOP+1,8);
    static final BlockPos PLANT=VILLAGE.offset(0,0,-3),PLANT2=VILLAGE.offset(-3,0,-3),STAND=VILLAGE.offset(-2,0,2),AWAY=VILLAGE.offset(64,0,0);
    private static boolean built;

    static void ground(ServerLevel l){if(built)return;HuntGameTests.platform(l,VILLAGE,CHUNKS);built=true;}
    static void release(ServerLevel l){
        if(!built)return;
        for(int cx=(VILLAGE.getX()>>4)-CHUNKS;cx<=(VILLAGE.getX()>>4)+CHUNKS;cx++)for(int cz=(VILLAGE.getZ()>>4)-CHUNKS;cz<=(VILLAGE.getZ()>>4)+CHUNKS;cz++)l.setChunkForced(cx,cz,false);
        built=false;
    }

    record Fixture(ServerLevel l,ServerPlayer p,List<Villager> villagers){}
    /** A fresh village with {@code count} villagers and no FOB, and the first player standing in it. Plant after a few ticks (vanilla files the beds and workstations a tick late). */
    static Fixture village(GameTestHelper h,int count){
        var l=h.getLevel();
        if(!CampaignData.get(l).installed())HuntGameTests.fixture(h);
        ground(l);clear(l);
        // the ground of the village is laid again (an earlier test may have blown a hole in it)
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            var at=VILLAGE.offset(x,-1,z);if(!l.getBlockState(at).is(Blocks.STONE))l.setBlock(at,Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<4;y++){var above=VILLAGE.offset(x,y,z);if(!l.getBlockState(above).isAir())l.setBlock(above,Blocks.AIR.defaultBlockState(),2);}
        }
        // the village: a bell, three composters, two beds (vanilla's points of interest)
        l.setBlock(VILLAGE,Blocks.BELL.defaultBlockState(),3);
        for(var at:List.of(VILLAGE.offset(-5,0,-5),VILLAGE.offset(5,0,-5),VILLAGE.offset(-5,0,5)))l.setBlock(at,Blocks.COMPOSTER.defaultBlockState(),3);
        for(int x:new int[]{3,5}){
            var foot=VILLAGE.offset(x,0,4);var bed=Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
            l.setBlock(foot,bed.setValue(BedBlock.PART,BedPart.FOOT),3);l.setBlock(foot.south(),bed.setValue(BedBlock.PART,BedPart.HEAD),3);
        }
        var villagers=new ArrayList<Villager>();
        for(int i=0;i<count;i++)villagers.add(villager(l,VILLAGE.offset(-4+i%4*2,0,-1+i/4*2)));
        var p=HuntGameTests.player(h);goTo(p,l,STAND);p.removeEffect(MobEffects.BAD_OMEN);
        return new Fixture(l,p,villagers);
    }
    /**
     * Vanilla counts a section as a village only when a villager has claimed a bed, workstation or bell in or near it (an occupied point of
     * interest). The test villagers have no AI and claim nothing, so the village's points of interest are claimed here, as a villager's
     * brain would (run a few ticks after the blocks are placed: vanilla files them a tick late).
     */
    static void occupy(ServerLevel l){
        var poi=l.getPoiManager();int n=0;
        for(var at:List.of(VILLAGE,VILLAGE.offset(-5,0,-5),VILLAGE.offset(5,0,-5),VILLAGE.offset(-5,0,5),VILLAGE.offset(3,0,5),VILLAGE.offset(5,0,5)))
            if(poi.take(h->true,(h,p)->p.equals(at),at,1).isPresent())n++;
        com.mojang.logging.LogUtils.getLogger().info("[fob-test] claimed {} points of interest; the village section is {} section(s) from a village",n,l.sectionsToVillage(net.minecraft.core.SectionPos.of(PLANT)));
    }
    static Villager villager(ServerLevel l,BlockPos at){
        var v=EntityType.VILLAGER.create(l);v.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);v.setNoAi(true);v.setPersistenceRequired();l.addFreshEntity(v);return v;
    }
    /** No FOB, no raid, no mob, no FOB Beacon left at the village. */
    static void clear(ServerLevel l){
        var d=FobData.get(l);
        for(var f:List.copyOf(d.fobs.values()))if(f.raidRunning()){var raid=l.getRaids().get(f.raidId);if(raid!=null)raid.stop();}
        d.fobs.clear();d.setDirty();FobZone.clear();
        var raid=l.getRaidAt(VILLAGE);if(raid!=null)raid.stop();
        for(var e:l.getEntitiesOfClass(Entity.class,new AABB(VILLAGE).inflate(100,40,100),e->!(e instanceof Player)))e.discard();
        for(var at:List.of(PLANT,PLANT2))if(l.getBlockState(at).is(ArsenalBeacon.FOB_BEACON.get()))l.setBlock(at,Blocks.AIR.defaultBlockState(),3);
        for(var at:List.of(VILLAGE.offset(-70,1,30),VILLAGE.offset(70,1,30),VILLAGE.offset(8,1,-9)))if(l.getBlockState(at).getBlock() instanceof RailGates.GateBlock)l.setBlock(at,Blocks.AIR.defaultBlockState(),3);
    }
    static void goTo(ServerPlayer p,ServerLevel l,BlockPos at){p.teleportTo(l,at.getX()+.5,at.getY(),at.getZ()+.5,p.getYRot(),p.getXRot());}
    /** The player plants a FOB Beacon on the block below {@code at}, as the item does it (the player's own placement checks and placer). */
    static InteractionResult plant(Fixture f,BlockPos at){
        var stack=new ItemStack(ArsenalBeacon.FOB_BEACON_ITEM.get());
        var hit=new BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false);
        return ((BlockItem)stack.getItem()).place(new BlockPlaceContext(f.l(),f.p(),InteractionHand.MAIN_HAND,stack,hit));
    }
    static FobData.Fob fob(ServerLevel l){return FobData.get(l).at(PLANT);}
    static int command(ServerLevel l,ServerPlayer p,String command){return l.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(),command);}
    static int count(ServerPlayer p,Item item){int n=0;for(var s:p.getInventory().items)if(s.is(item))n+=s.getCount();return n;}
    static void setArdent(ServerPlayer p,int amount){
        var inv=p.getInventory();for(int i=0;i<inv.items.size();i++)if(inv.items.get(i).is(ArsenalBeacon.ARDENT_ENERGY.get()))inv.items.set(i,ItemStack.EMPTY);
        if(amount>0)inv.add(new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get(),amount));
    }
    /** Ardent Energy dropped by {@code mob} over {@code tries} deaths at the player's hand (the drop rule alone, nothing is killed). */
    static int ardentDrops(ServerLevel l,net.minecraft.world.entity.LivingEntity mob,ServerPlayer p,int tries){
        int n=0;
        for(int i=0;i<tries;i++){var drops=new ArrayList<ItemEntity>();new ArdentEnergy().drops(new net.minecraftforge.event.entity.living.LivingDropsEvent(mob,l.damageSources().playerAttack(p),drops,0,true));
            for(var e:drops)if(e.getItem().is(ArsenalBeacon.ARDENT_ENERGY.get()))n+=e.getItem().getCount();}
        return n;
    }

    // ---- planting ---------------------------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="fob_01",timeoutTicks=400)
    public static void plantingFollowsTheRulesAndCallsTheRaid(GameTestHelper h){
        var f=village(h,2);var l=f.l();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            var nether=l.getServer().getLevel(Level.NETHER);
            if(nether!=null)h.assertTrue("overworld".equals(Fobs.plantProblem(nether,new BlockPos(0,64,0))),"the Nether is refused");
            h.assertTrue("not_village".equals(Fobs.plantProblem(l,AWAY)),"open ground 64 blocks from the village is no village: "+Fobs.plantProblem(l,AWAY));
            h.assertTrue("villagers".equals(Fobs.plantProblem(l,PLANT)),"two villagers are too few: "+Fobs.plantProblem(l,PLANT));
            h.assertTrue(plant(f,PLANT)==InteractionResult.FAIL&&l.getBlockState(PLANT).isAir(),"the item refuses and places nothing");
            villager(l,VILLAGE.offset(2,0,1));
        }).thenIdle(2).thenExecute(()->{
            h.assertTrue(Fobs.plantProblem(l,PLANT)==null,"three villagers are enough: "+Fobs.plantProblem(l,PLANT));
            var d=FobData.get(l);for(int i=0;i<FobRules.MAX_FOBS;i++)d.add(new BlockPos(-90000-i*500,70,0),0,0);
            h.assertTrue("max".equals(Fobs.plantProblem(l,PLANT)),"with five FOBs placed a sixth is refused");
            d.fobs.clear();
            h.assertTrue(plant(f,PLANT).consumesAction(),"the FOB Beacon is planted");
            var fob=fob(l);
            h.assertTrue(fob!=null&&fob.state==FobRules.State.PLANTED&&!fob.activated&&fob.name.startsWith("FOB "),"a PLANTED FOB with a generated name");
            h.assertTrue(l.getBlockState(PLANT.above()).is(ArsenalBeacon.STRUCTURE_PART.get()),"two blocks tall: the cell above is a structure part");
            var raid=l.getRaids().get(fob.raidId);
            h.assertTrue(fob.raidRunning()&&raid!=null&&!raid.isStopped(),"planting started a vanilla raid");
            h.assertTrue(raid.getBadOmenLevel()==1,"Bad Omen I: the lowest raid ("+raid.getBadOmenLevel()+")");
            h.assertFalse(f.p().hasEffect(MobEffects.BAD_OMEN),"the Bad Omen went into the raid");
            h.assertTrue(fob.raidVillagers==3,"three villagers counted at the start: "+fob.raidVillagers);
            h.assertTrue("taken".equals(Fobs.plantProblem(l,PLANT2)),"one FOB per village");
            h.assertTrue(plant(f,PLANT2)==InteractionResult.FAIL&&l.getBlockState(PLANT2).isAir(),"a second FOB Beacon in the village is refused");
            h.assertTrue(l.getBlockState(PLANT).getDestroySpeed(l,PLANT)<0,"the FOB Beacon cannot be mined");
            h.assertTrue(l.getBlockState(PLANT.above()).getDestroyProgress(f.p(),l,PLANT.above())==0,"nor its upper cell");
            clear(l);
        }).thenSucceed();
    }

    // ---- the raid ---------------------------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="fob_02",timeoutTicks=600)
    public static void theRaidIsLostWhenHalfTheVillagersDie(GameTestHelper h){
        var f=village(h,4);var l=f.l();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");
            h.assertTrue(fob(l).raidVillagers==4,"four villagers at the start");
            var v=f.villagers().get(0);float before=v.getHealth();v.hurt(l.damageSources().playerAttack(f.p()),4);
            h.assertTrue(v.getHealth()<before,"during the FOB's raid a villager can be hurt");
            f.villagers().get(0).kill();f.villagers().get(1).kill();
        }).thenIdle(45).thenExecute(()->{
            h.assertTrue(fob(l).state==FobRules.State.PLANTED&&fob(l).raidRunning(),"two of four alive is half: the raid goes on");
            f.villagers().get(2).kill();
        }).thenWaitUntil(()->h.assertTrue(fob(l).state==FobRules.State.DOWN,"one of four alive: the FOB goes DOWN"))
        .thenExecute(()->{
            var fob=fob(l);
            h.assertTrue(!fob.activated&&!fob.raidRunning(),"never activated, and no raid followed any more");
            var raid=l.getRaidAt(VILLAGE);h.assertTrue(raid==null||raid.isStopped(),"the vanilla raid was stopped (its bar goes away)");
            var last=f.villagers().get(3);float hp=last.getHealth();last.hurt(l.damageSources().playerAttack(f.p()),4);
            h.assertTrue(last.getHealth()==hp,"once the raid is over the last villager is protected again");
            clear(l);
        }).thenSucceed();
    }
    /** A whole vanilla raid at Bad Omen I: every raider is killed by the player as it comes; the victory makes the FOB ACTIVE. */
    @GameTest(template="empty3x3x3",batch="fob_03",timeoutTicks=7000)
    public static void winningTheVanillaRaidMakesTheFobActive(GameTestHelper h){
        var f=village(h,3);var l=f.l();int[] killed={0},waves={0,0};
        h.onEachTick(()->{
            var fob=fob(l);if(fob==null||!fob.raidRunning())return;
            var raid=l.getRaids().get(fob.raidId);if(raid!=null){waves[0]=Math.max(waves[0],raid.getGroupsSpawned());waves[1]=raid.getNumGroups(l.getDifficulty());}
            for(var r:l.getEntitiesOfClass(Raider.class,new AABB(VILLAGE).inflate(120,60,120),r->r.isAlive()&&r.getCurrentRaid()!=null&&r.getCurrentRaid().getId()==fob.raidId)){
                r.hurt(l.damageSources().playerAttack(f.p()),10000);if(!r.isAlive())killed[0]++;
            }
        });
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->h.assertTrue(plant(f,PLANT).consumesAction(),"planted"))
        .thenWaitUntil(()->h.assertTrue(fob(l)!=null&&fob(l).state==FobRules.State.ACTIVE,"the FOB turns ACTIVE when the raid is won ("+killed[0]+" raiders killed so far)"))
        .thenExecute(()->{
            var fob=fob(l);
            h.assertTrue(fob.activated&&!fob.raidRunning(),"activated, and the raid is no longer followed");
            h.assertTrue(waves[1]>0&&waves[0]==waves[1],"every wave came: "+waves[0]+" of "+waves[1]);
            h.assertTrue(killed[0]>=waves[1],"and was killed: "+killed[0]+" raiders");
            var raid=l.getRaidAt(VILLAGE);h.assertTrue(raid==null||raid.isVictory(),"vanilla calls it a victory");
            int ardent=0;for(var e:l.getEntitiesOfClass(ItemEntity.class,new AABB(VILLAGE).inflate(120,60,120)))if(e.getItem().is(ArsenalBeacon.ARDENT_ENERGY.get()))ardent+=e.getItem().getCount();
            h.assertTrue(ardent==0,"the village raiders the player killed dropped no Ardent Energy ("+killed[0]+" killed, "+ardent+" dropped)");
            h.assertTrue(f.villagers().stream().allMatch(Villager::isAlive),"every villager lived");
            com.mojang.logging.LogUtils.getLogger().info("[fob-test] the raid was won after {} raiders, {} waves",killed[0],waves[0]);
            clear(l);
        }).thenSucceed();
    }

    // ---- protection, the Ardent rule, the operator command ---------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="fob_04",timeoutTicks=400)
    public static void activeVillagersAreImmortalAndVillageRaidersPayNoArdent(GameTestHelper h){
        var f=village(h,3);var l=f.l();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");
            h.assertTrue(command(l,f.p(),"arsenal fob complete")>0,"/arsenal fob complete runs");
            var fob=fob(l);h.assertTrue(fob.state==FobRules.State.ACTIVE&&fob.activated&&!fob.raidRunning(),"the operator command makes it ACTIVE and stops its raid");
            var v=f.villagers().get(0);float hp=v.getHealth();
            v.hurt(l.damageSources().playerAttack(f.p()),6);v.hurt(l.damageSources().inFire(),6);v.hurt(l.damageSources().fall(),6);v.hurt(l.damageSources().mobAttack(EntityType.ZOMBIE.create(l)),6);
            h.assertTrue(v.getHealth()==hp,"a player, fire, a fall and a zombie hurt no villager of an ACTIVE FOB");
            l.explode(null,PLANT.getX()+.5,PLANT.getY()+1.5,PLANT.getZ()+1.5,3,Level.ExplosionInteraction.TNT);
            var bolt=EntityType.LIGHTNING_BOLT.create(l);var struck=f.villagers().get(2);bolt.moveTo(struck.getX(),struck.getY(),struck.getZ());bolt.setVisualOnly(false);l.addFreshEntity(bolt);
        }).thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(f.villagers().get(2).isAlive()&&!f.villagers().get(2).isRemoved()&&l.getEntitiesOfClass(net.minecraft.world.entity.monster.Witch.class,new AABB(VILLAGE).inflate(16)).isEmpty(),"lightning turns no villager of a FOB into a witch");
            h.assertTrue(l.getBlockState(PLANT).is(ArsenalBeacon.FOB_BEACON.get())&&l.getBlockState(PLANT.above()).is(ArsenalBeacon.STRUCTURE_PART.get()),"a blast beside it leaves the FOB Beacon standing, its upper cell whole");
            for(var v:f.villagers())h.assertTrue(v.isAlive()&&v.getHealth()==v.getMaxHealth(),"the blast hurt no villager");
            var v=f.villagers().get(1);v.kill();h.assertFalse(v.isAlive(),"/kill still works on them");
            var pillager=EntityType.PILLAGER.create(l);pillager.moveTo(AWAY.getX()+.5,AWAY.getY(),AWAY.getZ()+.5,0,0);
            pillager.setWave(1);int village=ardentDrops(l,pillager,f.p(),300);
            pillager.setWave(0);int patrol=ardentDrops(l,pillager,f.p(),300);
            pillager.setWave(1);pillager.getPersistentData().putBoolean("arsenalRaider",true);int ours=ardentDrops(l,pillager,f.p(),300);
            h.assertTrue(village==0,"a raider from a village raid wave drops no Ardent Energy ("+village+" in 300 deaths)");
            h.assertTrue(patrol>0,"the same pillager outside a raid does ("+patrol+" in 300; the check works)");
            h.assertTrue(ours>0,"a beacon raid mob still does ("+ours+" in 300)");
            clear(l);
        }).thenSucceed();
    }

    // ---- meals and the status card ----------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="fob_05",timeoutTicks=300)
    public static void anActiveFobZoneKeepsMealsLikeHome(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");PlayerMeals.clear(p);
            h.assertTrue(MealZone.at(p)==MealZone.Kind.NONE,"a PLANTED FOB's zone is not home yet");
            var n=new CompoundTag();Fobs.status(p,n);var here=n.getCompound("fobHere");
            h.assertTrue(here.getString("name").equals(fob(l).name)&&here.getString("state").equals("planted")&&here.getBoolean("raid")&&here.getInt("villagers")==3,"the status card names the FOB, its state, the raid and the villagers: "+here);
            command(l,p,"arsenal fob complete");
            h.assertTrue(MealZone.at(p)==MealZone.Kind.FOB,"an ACTIVE FOB's zone counts as home");
            PlayerMeals.eat(p,new MealData(false,List.of(new MealData.Bonus(MealRules.Effect.FORTUNE,1)),MealRules.FIELD_TICKS));
            var inst=p.getEffect(MealEffects.of(MealRules.Effect.FORTUNE));
            h.assertTrue(inst!=null&&inst.isInfiniteDuration(),"the meal timer is frozen in the FOB zone");
            h.assertTrue(p.hasEffect(MealEffects.FOB.get())&&!p.hasEffect(MealEffects.HOME.get()),"the FOB Zone effect shows, not the Home Zone one");
            var luck=p.getAttribute(Attributes.LUCK).getModifier(PlayerMeals.modifier(MealRules.Effect.FORTUNE));
            h.assertTrue(luck!=null&&luck.getAmount()==2,"the bonus is doubled as at home");
            goTo(p,l,AWAY);PlayerMeals.tick(p);
            inst=p.getEffect(MealEffects.of(MealRules.Effect.FORTUNE));
            h.assertTrue(inst!=null&&!inst.isInfiniteDuration(),"outside the zone it counts down again");
            h.assertFalse(p.hasEffect(MealEffects.FOB.get()),"and the FOB Zone effect is gone");
            n=new CompoundTag();Fobs.status(p,n);
            h.assertTrue(!n.contains("fobHere")&&n.getInt("fobPlaced")==1&&n.getInt("fobActive")==1,"away from it the card only counts the FOBs");
            PlayerMeals.clear(p);goTo(p,l,STAND);
            clear(l);
        }).thenSucceed();
    }

    // ---- the shop ---------------------------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="fob_06",timeoutTicks=300)
    public static void theShopSellsFlaresOnlyWhileActive(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();var mode=p.gameMode.getGameModeForPlayer();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");
            p.setGameMode(GameType.SURVIVAL);setArdent(p,20);
            try{
                Fobs.open(p,PLANT);h.assertTrue(p.containerMenu instanceof FobBlocks.FobMenu,"the FOB screen opens");
                var fire=ArsenalBeacon.FIRE_FLARE.get();int flares=count(p,fire);
                Fobs.button(p,PLANT,Fobs.BUY_FIRE);
                h.assertTrue(ArdentEnergy.balance(p)==20&&count(p,fire)==flares,"a PLANTED FOB sells nothing");
                command(l,p,"arsenal fob complete");
                Fobs.button(p,PLANT,Fobs.BUY_FIRE);
                h.assertTrue(ArdentEnergy.balance(p)==20-SupportRules.FIRE_FLARE_PRICE&&count(p,fire)==flares+1,"an ACTIVE FOB sells a Fire Support Flare for "+SupportRules.FIRE_FLARE_PRICE+": "+ArdentEnergy.balance(p));
                int supply=count(p,ArsenalBeacon.SUPPLY_FLARE.get()),back=count(p,ArsenalBeacon.RETURN_FLARE.get());
                Fobs.button(p,PLANT,Fobs.BUY_SUPPLY);Fobs.button(p,PLANT,Fobs.BUY_RETURN);
                h.assertTrue(ArdentEnergy.balance(p)==20-SupportRules.FIRE_FLARE_PRICE-SupportRules.SUPPLY_FLARE_PRICE-SupportRules.RETURN_FLARE_PRICE
                    &&count(p,ArsenalBeacon.SUPPLY_FLARE.get())==supply+1&&count(p,ArsenalBeacon.RETURN_FLARE.get())==back+1,"Supply and Return Flares at the Support Platform's prices");
                setArdent(p,1);Fobs.button(p,PLANT,Fobs.BUY_FIRE);
                h.assertTrue(ArdentEnergy.balance(p)==1&&count(p,fire)==flares+1,"short of Ardent Energy: nothing is taken, nothing given");
                var state=Fobs.state(p,fob(l),"");
                h.assertTrue(state.getInt("price.supply")==SupportRules.SUPPLY_FLARE_PRICE&&state.getInt("price.return")==SupportRules.RETURN_FLARE_PRICE&&state.getInt("price.fire")==SupportRules.FIRE_FLARE_PRICE,"the screen shows those prices");
                h.assertTrue(state.getString("state").equals("active")&&state.getInt("villagers")==3&&state.getInt("beds")==2&&state.getInt("jobs")==3,"the screen shows the village: "+state);
            }finally{p.closeContainer();p.setGameMode(mode);setArdent(p,0);}
            clear(l);
        }).thenSucceed();
    }

    // ---- names ------------------------------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="fob_07",timeoutTicks=300)
    public static void aRenamedNameTagRenamesTheFob(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();var mode=p.gameMode.getGameModeForPlayer();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");p.setGameMode(GameType.SURVIVAL);
            try{
                var other=FobData.get(l).add(new BlockPos(-90000,70,0),0,0);other.name="South Post";
                var tag=new ItemStack(Items.NAME_TAG,3);tag.setHoverName(Component.literal("  North   Gate "));
                Fobs.rename(p,PLANT,tag);
                h.assertTrue(fob(l).name.equals("North Gate")&&tag.getCount()==2,"renamed (spaces tidied), the Name Tag used up: "+fob(l).name);
                tag.setHoverName(Component.literal("south POST"));Fobs.rename(p,PLANT,tag);
                h.assertTrue(fob(l).name.equals("North Gate")&&tag.getCount()==2,"a name another FOB has, in any case, is refused and costs nothing");
                tag.setHoverName(Component.literal("x".repeat(FobRules.NAME_MAX+1)));Fobs.rename(p,PLANT,tag);
                h.assertTrue(fob(l).name.equals("North Gate"),"a name over "+FobRules.NAME_MAX+" characters is refused");
                var blank=new ItemStack(Items.NAME_TAG);Fobs.rename(p,PLANT,blank);
                h.assertTrue(fob(l).name.equals("North Gate")&&blank.getCount()==1,"a Name Tag never renamed at an anvil does nothing");
                h.assertTrue(FobData.load(FobData.get(l).save(new CompoundTag())).at(PLANT).name.equals("North Gate"),"the name is saved");
            }finally{p.setGameMode(mode);}
            clear(l);
        }).thenSucceed();
    }

    // ---- a raid without a result, the commands, the shovel ----------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="fob_08",timeoutTicks=400)
    public static void theRaidCanBeCalledAgainAndTheShovelTakesTheFobBack(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();var hand=p.getMainHandItem().copy();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");
            l.getRaids().get(fob(l).raidId).stop();   // a raid that ends without a result (Peaceful, a timeout)
        }).thenIdle(45).thenExecute(()->{
            var fob=fob(l);
            h.assertTrue(fob.state==FobRules.State.PLANTED&&!fob.raidRunning()&&FobRules.mayCallRaid(fob.state,fob.raidRunning()),"no result: still PLANTED, the raid can be called again");
            Fobs.open(p,PLANT);Fobs.button(p,PLANT,Fobs.CALL_RAID);p.closeContainer();
            h.assertTrue(fob.raidRunning()&&l.getRaids().get(fob.raidId)!=null,"Call the raid starts a new one");
            h.assertTrue(command(l,p,"arsenal fob complete")>0&&fob.state==FobRules.State.ACTIVE,"complete");
            h.assertTrue(command(l,p,"arsenal fob list")==1&&command(l,p,"arsenal fob status")==1,"list and status");
            h.assertTrue(command(l,p,"arsenal fob reset")==1&&fob.state==FobRules.State.PLANTED&&!fob.activated,"reset: PLANTED again, never activated");
            // the Recovery Shovel: a confirmation, then the FOB is gone and its FOB Beacon comes back
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ArsenalBeacon.CONTROLLER.get()));
            int beacons=count(p,ArsenalBeacon.FOB_BEACON_ITEM.get());
            Fobs.requestRemoval(p,PLANT.above());
            String token=BeaconActions.pendingToken(p);
            h.assertTrue(!token.isEmpty()&&fob(l)!=null,"the shovel asks first (on the upper cell too); nothing is removed yet");
            h.assertTrue(BeaconActions.confirm(p,token),"confirmed");
            BeaconNetwork.sendState(p,"close","","");   // as the confirm button's answer does (the client's confirmation screen closes)
            h.assertTrue(fob(l)==null&&FobData.get(l).fobs.isEmpty(),"the FOB is deleted");
            h.assertTrue(l.getBlockState(PLANT).isAir()&&l.getBlockState(PLANT.above()).isAir(),"its block and upper cell are gone");
            h.assertTrue(count(p,ArsenalBeacon.FOB_BEACON_ITEM.get())==beacons+1,"the FOB Beacon is back in the inventory");
            var v=f.villagers().get(0);float hp=v.getHealth();v.hurt(l.damageSources().playerAttack(p),2);
            h.assertTrue(v.getHealth()<hp,"its villagers can be hurt again");
        }).thenExecute(()->{p.setItemInHand(InteractionHand.MAIN_HAND,hand);clear(l);}).thenSucceed();
    }

    // ---- the supply rail (0.0.24, FOB slice 2) ---------------------------------------------------------------------------------------------------
    /** A straight rail along X, 30 blocks south of the village, 150 blocks long; the Receiving Gate at its west end, the Delivery Gate at its east end. */
    static final int RAIL_Z=38,RAIL_FROM=-78,RAIL_TO=78;
    static final BlockPos RECEIVING=VILLAGE.offset(-70,1,RAIL_Z-VILLAGE.getZ()),DELIVERY=VILLAGE.offset(70,1,RAIL_Z-VILLAGE.getZ());
    /** An ACTIVE FOB with a rail and both gates linked to it (no ride yet). */
    static FobData.Fob route(Fixture f){
        var l=f.l();
        for(int x=RAIL_FROM-2;x<=RAIL_TO+2;x++)for(int dz=-1;dz<=1;dz++)for(int y=0;y<3;y++){
            var at=new BlockPos(VILLAGE.getX()+x,VILLAGE.getY()+y,RAIL_Z+dz);
            if(!l.getBlockState(at.below()).isSolid()&&y==0)l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
            var want=y==0&&dz==0?Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST):Blocks.AIR.defaultBlockState();
            if(l.getBlockState(at)!=want&&!(l.getBlockState(at).getBlock() instanceof RailGates.GateBlock)&&!(l.getBlockState(at).getBlock() instanceof RailGates.Part))l.setBlock(at,want,2);
        }
        var fob=fob(l);var d=FobData.get(l);
        for(var kind:RailGates.Kind.values()){
            var at=kind==RailGates.Kind.DELIVERY?DELIVERY:RECEIVING;
            var block=(kind==RailGates.Kind.DELIVERY?ArsenalBeacon.DELIVERY_GATE:ArsenalBeacon.RECEIVING_GATE).get();
            if(!l.getBlockState(at).is(block))l.setBlock(at,block.defaultBlockState().setValue(RailGates.FACING,Direction.EAST),3);
            Routes.link(l,d,fob,kind,at,(RailGates.GateEntity)l.getBlockEntity(at));
        }
        return fob;
    }
    static void clearRoute(ServerLevel l){
        for(var at:List.of(RECEIVING,DELIVERY))if(l.getBlockState(at).getBlock() instanceof RailGates.GateBlock)l.setBlock(at,Blocks.AIR.defaultBlockState(),3);
        Routes.clear();
    }
    static RailGates.Lamp lamp(ServerLevel l,BlockPos at){return l.getBlockState(at).getValue(RailGates.SIGNAL);}
    static int queued(ServerLevel l){return CampaignData.get(l.getServer().overworld()).rewards.size();}
    static void trimRewards(ServerLevel l,int size){var d=CampaignData.get(l.getServer().overworld());while(d.rewards.size()>size)d.rewards.remove(d.rewards.size()-1);d.setDirty();}

    /** The owner's test: a vanilla plain minecart with a seated player rolls through both gates without touching them; the ride opens the route and the way back delivers. */
    @GameTest(template="empty3x3x3",batch="fob_09",timeoutTicks=2400)
    public static void aRiddenMinecartOpensTheRouteAndARoundTripDelivers(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();var cart=new net.minecraft.world.entity.vehicle.Minecart[1];int[] dir={1};int before[]={0};
        double[] slowest={9};
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"arsenal fob complete");
            var fob=route(f);for(int i=0;i<fob.stock.length;i++)fob.stock[i]=8;fob.workers=3;
            h.assertTrue(fob.hasRoute()&&!fob.commissioned&&lamp(l,RECEIVING)==RailGates.Lamp.RED,"both gates linked, no route yet: red");
            // a chest minecart rolling through counts for nothing (it cannot carry a rider)
            var chest=new net.minecraft.world.entity.vehicle.MinecartChest(l,RECEIVING.getX()-3.5,RECEIVING.getY()-1,RECEIVING.getZ()+.5);l.addFreshEntity(chest);chest.setDeltaMovement(.4,0,0);
            var c=new net.minecraft.world.entity.vehicle.Minecart(net.minecraft.world.entity.EntityType.MINECART,l);c.setPos(RECEIVING.getX()-5.5,RECEIVING.getY()-1,RECEIVING.getZ()+.5);l.addFreshEntity(c);cart[0]=c;
            goTo(p,l,RECEIVING.offset(-5,-1,0));p.startRiding(c,true);before[0]=queued(l);
        }).thenIdle(20).thenExecute(()->h.assertTrue(Routes.RUNS.isEmpty(),"the chest minecart started nothing"))
        .thenExecute(()->h.onEachTick(()->{var c=cart[0];if(c==null||!c.isAlive())return;
            if(dir[0]>0&&c.getX()>DELIVERY.getX()+4)dir[0]=-1;
            c.setDeltaMovement(.4*dir[0],c.getDeltaMovement().y,0);
            // the speed through and between the gates (not at the start or the turn): a gate in the way would stop the cart
            if(c.getX()>RECEIVING.getX()-1&&c.getX()<DELIVERY.getX()+1&&c.tickCount>40)slowest[0]=Math.min(slowest[0],Math.abs(c.getX()-c.xo));}))
        .thenWaitUntil(()->h.assertTrue(fob(l).commissioned,"the ride through the Delivery Gate opens the route"))
        .thenExecute(()->{
            var fob=fob(l);
            h.assertTrue(fob.baseline>=Math.ceil(fob.separation()/RouteRules.MAX_SPEED)&&fob.clock==RouteRules.CLOCK,"the one-way time is the route's T ("+fob.baseline+" ticks), the inspection full");
            h.assertTrue(Routes.RUNS.containsKey(fob.id)&&Routes.RUNS.get(fob.id).stage==RouteRules.Stage.RETURN,"the ride goes on home with a load");
        }).thenIdle(25).thenExecute(()->h.assertTrue(lamp(l,DELIVERY)==RailGates.Lamp.AMBER&&lamp(l,RECEIVING)==RailGates.Lamp.AMBER,"both lights amber while the ride is under way"))
        .thenWaitUntil(()->h.assertTrue(Routes.RUNS.isEmpty()&&"ride".equals(fob(l).lastKind),"back through the Receiving Gate: the round trip delivers"))
        .thenExecute(()->{
            var fob=fob(l);
            h.assertTrue(queued(l)>before[0],"the tribute is in the reward chest's queue: "+(queued(l)-before[0])+" stacks");
            h.assertTrue(fob.lastItems==Math.min(RouteRules.activeCapacity(fob.separation()),64)&&fob.clock==RouteRules.CLOCK,"it carried a ride's load ("+fob.lastItems+") and renewed the inspection");
            h.assertTrue(p.getVehicle()==cart[0],"the rider stayed seated the whole way");
            h.assertTrue(slowest[0]>.25,"the cart never slowed against the gates (slowest step "+slowest[0]+" blocks; vanilla moves a ridden cart 0.75 of its speed, 0.3 here)");
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.GREEN&&lamp(l,DELIVERY)==RailGates.Lamp.GREEN,"after the round trip both lights are green");
            p.stopRiding();if(cart[0]!=null)cart[0].discard();trimRewards(l,before[0]);clearRoute(l);clear(l);goTo(p,l,STAND);
        }).thenSucceed();
    }
    /** Passive tribute arrives in the reward chest with its chat line, and stops when the inspection runs out. */
    @GameTest(template="empty3x3x3",batch="fob_10",timeoutTicks=1200)
    public static void passiveTributeArrivesAndStopsWhenTheClockRunsOut(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();int[] before={0},after={0};
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"arsenal fob complete");
            var fob=route(f);var d=FobData.get(l);
            fob.commissioned=true;fob.baseline=60;fob.clock=RouteRules.CLOCK;fob.nextPassive=0;fob.workers=3;for(int i=0;i<fob.stock.length;i++)fob.stock[i]=10;
            fob.lastNoteAt=d.onlineTicks-RouteRules.NOTE_TICKS;before[0]=queued(l);
        }).thenWaitUntil(()->h.assertTrue("passive".equals(fob(l).lastKind),"a delivery by itself every 2T/0.8 = "+RouteRules.period(60)+" ticks of play"))
        .thenIdle(25).thenExecute(()->{
            var fob=fob(l);
            h.assertTrue(queued(l)>before[0],"tribute queued for the reward chest");
            h.assertTrue(fob.lastItems==RouteRules.capacity(fob.separation()),"a passive trip carries C = "+RouteRules.capacity(fob.separation())+": "+fob.lastItems);
            int notes=0;for(int n:fob.note)notes+=n;h.assertTrue(notes==0,"the chat line went out and the note was cleared");
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.GREEN,"green while it runs");
            command(l,p,"arsenal fob overdue");h.assertTrue(fob.clock==20,"/arsenal fob overdue: the inspection runs out within a second");
        }).thenIdle(60).thenExecute(()->{
            h.assertTrue(fob(l).clock==0&&lamp(l,RECEIVING)==RailGates.Lamp.RED&&lamp(l,DELIVERY)==RailGates.Lamp.RED,"the inspection ran out: both lights red");
            after[0]=queued(l);
        }).thenIdle(300).thenExecute(()->{
            h.assertTrue(queued(l)==after[0],"no delivery while the inspection is overdue");
            trimRewards(l,before[0]);clearRoute(l);clear(l);
        }).thenSucceed();
    }
    /** A round trip renews the inspection even when the reward chest is backed up, but carries nothing; passive deliveries wait (amber). */
    @GameTest(template="empty3x3x3",batch="fob_11",timeoutTicks=600)
    public static void theBacklogPausesDeliveriesButARideStillRenews(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();int[] before={0};
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"arsenal fob complete");
            var fob=route(f);fob.commissioned=true;fob.baseline=60;fob.clock=RouteRules.CLOCK;fob.workers=3;for(int i=0;i<fob.stock.length;i++)fob.stock[i]=10;
            before[0]=queued(l);var campaign=CampaignData.get(l.getServer().overworld());
            for(int i=queued(l);i<RouteRules.BACKLOG_CAP;i++)RaidRewards.queue(campaign,List.of(new ItemStack(Items.DIRT)));
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.AMBER,"amber: deliveries wait on the backlog");
            var fob=fob(l);fob.lastKind="";
            var cart=new net.minecraft.world.entity.vehicle.Minecart(net.minecraft.world.entity.EntityType.MINECART,l);cart.setPos(RECEIVING.getX()-2.5,RECEIVING.getY()-1,RECEIVING.getZ()+.5);l.addFreshEntity(cart);p.startRiding(cart,true);
            var d=FobData.get(l);
            Routes.pass(l,d,fob,RailGates.Kind.RECEIVING,p,cart);Routes.pass(l,d,fob,RailGates.Kind.DELIVERY,p,cart);Routes.pass(l,d,fob,RailGates.Kind.RECEIVING,p,cart);
            h.assertTrue(fob.clock==RouteRules.CLOCK,"the round trip renewed the inspection");
            h.assertTrue(fob.lastKind.isEmpty()&&queued(l)==RouteRules.BACKLOG_CAP,"and delivered nothing while the chest is backed up");
            p.stopRiding();cart.discard();
        }).thenIdle(200).thenExecute(()->{
            h.assertTrue(queued(l)==RouteRules.BACKLOG_CAP&&fob(l).lastKind.isEmpty(),"no passive delivery either");
            trimRewards(l,before[0]);
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.GREEN,"chest emptied: green again");
            clearRoute(l);clear(l);
        }).thenSucceed();
    }
    /** The Delivery Gate is sold once per FOB, stands only over a rail in the FOB's village, the Receiving Gate only at home; a FOB's removal unlinks its gates. */
    @GameTest(template="empty3x3x3",batch="fob_12",timeoutTicks=600)
    public static void gatesArePlacedAndSoldByTheRules(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();var mode=p.gameMode.getGameModeForPlayer();
        BlockPos rail=VILLAGE.offset(8,0,-9);
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");
            p.setGameMode(GameType.SURVIVAL);
            try{
                for(var s:List.of(new ItemStack(Items.IRON_INGOT,64),new ItemStack(Items.COPPER_INGOT,64),new ItemStack(Items.REDSTONE,64),new ItemStack(ArsenalBeacon.LOGISTICS.get(),8),new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get(),16)))p.getInventory().add(s);
                Fobs.open(p,PLANT);
                Fobs.button(p,PLANT,Fobs.BUY_GATE);
                h.assertTrue(count(p,ArsenalBeacon.DELIVERY_GATE_ITEM.get())==0,"a PLANTED FOB sells no gate");
                command(l,p,"arsenal fob complete");
                Fobs.button(p,PLANT,Fobs.BUY_GATE);
                h.assertTrue(count(p,ArsenalBeacon.DELIVERY_GATE_ITEM.get())==1,"an ACTIVE FOB sells its Supply Delivery Gate");
                p.closeContainer();
                for(int i=0;i<p.getInventory().items.size();i++)if(p.getInventory().items.get(i).is(ArsenalBeacon.DELIVERY_GATE_ITEM.get()))p.getInventory().items.set(i,ItemStack.EMPTY);   // it goes on the rail below
                l.setBlock(rail,Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST),2);
                var gate=new ItemStack(ArsenalBeacon.DELIVERY_GATE_ITEM.get());
                h.assertTrue(useOn(f,gate,VILLAGE.offset(10,-1,-4))==InteractionResult.FAIL,"not over a rail: refused");
                h.assertTrue(useOn(f,gate,rail).consumesAction(),"over the rail in the village: placed");
                var anchor=rail.above();
                h.assertTrue(l.getBlockState(anchor).is(ArsenalBeacon.DELIVERY_GATE.get())&&fob(l).deliveryGate!=null&&fob(l).deliveryGate.equals(anchor),"it stands over the rail and links to the FOB by itself");
                for(var part:RailGates.parts(anchor,l.getBlockState(anchor).getValue(RailGates.FACING)))h.assertTrue(l.getBlockState(part).is(ArsenalBeacon.GATE_PART.get()),"its frame at "+part);
                h.assertTrue(l.getBlockState(rail).is(Blocks.RAIL),"the rail stays the player's");
                Fobs.open(p,PLANT);Fobs.button(p,PLANT,Fobs.BUY_GATE);p.closeContainer();
                h.assertTrue(count(p,ArsenalBeacon.DELIVERY_GATE_ITEM.get())==0,"a second gate for the same FOB is refused");
                var home=new ItemStack(ArsenalBeacon.RECEIVING_GATE_ITEM.get());
                l.setBlock(rail.offset(0,0,-6),Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST),2);
                h.assertTrue(useOn(f,home,rail.offset(0,0,-6))==InteractionResult.FAIL,"a Receiving Gate outside the base zone: refused");
                // the shovel takes it back, and the item remembers its FOB
                Routes.pickUp(p,anchor);
                h.assertTrue(l.getBlockState(anchor).isAir()&&fob(l).deliveryGate==null,"the shovel takes the gate back");
                var back=p.getInventory().items.stream().filter(s->s.is(ArsenalBeacon.DELIVERY_GATE_ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
                h.assertTrue(back.hasTag()&&back.getTag().getLong("Fob")==fob(l).id,"the item remembers its FOB");
                // a FOB's removal turns its gates UNLINKED
                var fob=route(f);
                FobData.get(l).fobs.remove(fob.id);
            }finally{p.setGameMode(mode);}
        }).thenIdle(30).thenExecute(()->{
            h.assertTrue(l.getBlockEntity(RECEIVING) instanceof RailGates.GateEntity be&&be.fobId==0,"its FOB gone, the gate is UNLINKED");
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.RED,"and red");
            l.setBlock(rail,Blocks.AIR.defaultBlockState(),2);l.setBlock(rail.offset(0,0,-6),Blocks.AIR.defaultBlockState(),2);
            clearRoute(l);clear(l);
        }).thenSucceed();
    }
    static InteractionResult useOn(Fixture f,ItemStack stack,BlockPos rail){
        var hit=new BlockHitResult(Vec3.atCenterOf(rail).add(0,.5,0),Direction.UP,rail,false);
        return ((BlockItem)stack.getItem()).place(new BlockPlaceContext(f.l(),f.p(),InteractionHand.MAIN_HAND,stack.copy(),hit));
    }

    public static final class Runner {
        private static Collection<GameTestInfo> infos=List.of();private static MultipleTestTracker tracker;private static ServerLevel level;
        /** Result of the last finished run (for the QA harness). */
        public static volatile int lastPass=-1,lastFail=-1;
        @SubscribeEvent public void fobRegister(net.minecraftforge.event.RegisterCommandsEvent e){
            ensureRegistered();
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("fob-test").requires(s->s.hasPermission(2)).executes(c->{start(c.getSource().getLevel());return 1;}));
        }
        public static void ensureRegistered(){
            if(GameTestRegistry.getAllTestFunctions().stream().noneMatch(t->t.getBatchName().startsWith("fob_")))GameTestRegistry.register(FobGameTests.class);
        }
        public static void start(ServerLevel target){
            if(tracker!=null)throw new IllegalStateException("A FOB test run is already active");
            level=target;ensureRegistered();var names=new HashSet<String>();
            var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getBatchName().startsWith("fob_")&&names.add(t.getTestName())).toList();
            infos=GameTestRunner.runTests(tests,new BlockPos(0,200,0),Rotation.NONE,target,GameTestTicker.SINGLETON,1);tracker=new MultipleTestTracker(infos);
        }
        @SubscribeEvent public void fobTick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
            if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;
            if(!net.minecraftforge.gametest.ForgeGameTestHooks.isGametestEnabled())GameTestTicker.SINGLETON.tick();
            if(!tracker.isDone())return;
            var log=com.mojang.logging.LogUtils.getLogger();int pass=0,fail=0;
            for(var info:infos){if(info.hasFailed()){fail++;log.error("FOB_TEST_FAIL {}: {}",info.getTestName(),info.getError()==null?"?":info.getError().getMessage());}else{pass++;log.info("FOB_TEST_PASS {}",info.getTestName());}}
            log.info("FOB_TESTS_DONE pass={} fail={}",pass,fail);
            lastPass=pass;lastFail=fail;tracker=null;
        }
        public static boolean running(){return tracker!=null;}
        public static void release(){if(level!=null)FobGameTests.release(level);}
    }
}
