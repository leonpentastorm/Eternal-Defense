package dev.eternaldefense.beacon;

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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

/**
 * Forward Operating Bases, slices 1 to 3 (0.0.23 to 0.0.25), on a real server level. Opt-in: start the game with {@code -Deternaldefense.fobTests=true}, then
 * {@code /fob-test}, or let tools/qa QaFob start them. THROWAWAY WORLDS ONLY: the tests clear the world's FOB records, need the campaign's
 * beacon (they borrow the hunt tests' base when none is planted) and move the first online player, because a FOB is planted, raided and
 * shopped at by a real player. A small village (a bell, three composters, two beds and NoAI villagers) is built on a stone platform high in
 * the sky, wide enough ({@value #CHUNKS} chunks each way) for vanilla to find raid spawn spots 64 blocks out.
 */
@GameTestHolder(EternalDefense.ID)
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
        d.fobs.clear();d.sosFob=0;d.sosLeft=0;d.sosClock=0;d.sosMap=null;d.setDirty();FobZone.clear();
        var raid=l.getRaidAt(VILLAGE);if(raid!=null)raid.stop();
        for(var e:l.getEntitiesOfClass(Entity.class,new AABB(VILLAGE).inflate(100,40,100),e->!(e instanceof Player)))e.discard();
        for(var at:List.of(PLANT,PLANT2))if(l.getBlockState(at).is(EternalDefense.FOB_BEACON.get()))l.setBlock(at,Blocks.AIR.defaultBlockState(),3);
        clearCatapults(l);
        for(var at:List.of(RECEIVING,DELIVERY))if(l.getBlockState(at).getBlock() instanceof RailGates.GateBlock)l.setBlock(at,Blocks.AIR.defaultBlockState(),3);
        for(var at:BlockPos.betweenClosed(GATE_RAIL.offset(-3,0,-3),GATE_RAIL.offset(3,0,3)))if(l.getBlockState(at).getBlock() instanceof RailGates.GateBlock)l.setBlock(at.immutable(),Blocks.AIR.defaultBlockState(),3);
    }
    static void goTo(ServerPlayer p,ServerLevel l,BlockPos at){p.teleportTo(l,at.getX()+.5,at.getY(),at.getZ()+.5,p.getYRot(),p.getXRot());}
    /** The player plants a FOB Beacon on the block below {@code at}, as the item does it (the player's own placement checks and placer). */
    static InteractionResult plant(Fixture f,BlockPos at){
        var stack=new ItemStack(EternalDefense.FOB_BEACON_ITEM.get());
        var hit=new BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false);
        return ((BlockItem)stack.getItem()).place(new BlockPlaceContext(f.l(),f.p(),InteractionHand.MAIN_HAND,stack,hit));
    }
    static FobData.Fob fob(ServerLevel l){return FobData.get(l).at(PLANT);}
    static int command(ServerLevel l,ServerPlayer p,String command){return l.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(),command);}
    static int count(ServerPlayer p,Item item){int n=0;for(var s:p.getInventory().items)if(s.is(item))n+=s.getCount();return n;}
    static void setArdent(ServerPlayer p,int amount){
        var inv=p.getInventory();for(int i=0;i<inv.items.size();i++)if(inv.items.get(i).is(EternalDefense.ARDENT_ENERGY.get()))inv.items.set(i,ItemStack.EMPTY);
        if(amount>0)inv.add(new ItemStack(EternalDefense.ARDENT_ENERGY.get(),amount));
    }
    /** Ardent Energy dropped by {@code mob} over {@code tries} deaths at the player's hand (the drop rule alone, nothing is killed). */
    static int ardentDrops(ServerLevel l,net.minecraft.world.entity.LivingEntity mob,ServerPlayer p,int tries){
        int n=0;
        for(int i=0;i<tries;i++){var drops=new ArrayList<ItemEntity>();new ArdentEnergy().drops(new net.minecraftforge.event.entity.living.LivingDropsEvent(mob,l.damageSources().playerAttack(p),drops,0,true));
            for(var e:drops)if(e.getItem().is(EternalDefense.ARDENT_ENERGY.get()))n+=e.getItem().getCount();}
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
            h.assertTrue(l.getBlockState(PLANT.above()).is(EternalDefense.STRUCTURE_PART.get()),"two blocks tall: the cell above is a structure part");
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
            int ardent=0;for(var e:l.getEntitiesOfClass(ItemEntity.class,new AABB(VILLAGE).inflate(120,60,120)))if(e.getItem().is(EternalDefense.ARDENT_ENERGY.get()))ardent+=e.getItem().getCount();
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
            h.assertTrue(command(l,f.p(),"eternaldefense fob complete")>0,"/eternaldefense fob complete runs");
            var fob=fob(l);h.assertTrue(fob.state==FobRules.State.ACTIVE&&fob.activated&&!fob.raidRunning(),"the operator command makes it ACTIVE and stops its raid");
            var v=f.villagers().get(0);float hp=v.getHealth();
            v.hurt(l.damageSources().playerAttack(f.p()),6);v.hurt(l.damageSources().inFire(),6);v.hurt(l.damageSources().fall(),6);v.hurt(l.damageSources().mobAttack(EntityType.ZOMBIE.create(l)),6);
            h.assertTrue(v.getHealth()==hp,"a player, fire, a fall and a zombie hurt no villager of an ACTIVE FOB");
            l.explode(null,PLANT.getX()+.5,PLANT.getY()+1.5,PLANT.getZ()+1.5,3,Level.ExplosionInteraction.TNT);
            var bolt=EntityType.LIGHTNING_BOLT.create(l);var struck=f.villagers().get(2);bolt.moveTo(struck.getX(),struck.getY(),struck.getZ());bolt.setVisualOnly(false);l.addFreshEntity(bolt);
        }).thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(f.villagers().get(2).isAlive()&&!f.villagers().get(2).isRemoved()&&l.getEntitiesOfClass(net.minecraft.world.entity.monster.Witch.class,new AABB(VILLAGE).inflate(16)).isEmpty(),"lightning turns no villager of a FOB into a witch");
            h.assertTrue(l.getBlockState(PLANT).is(EternalDefense.FOB_BEACON.get())&&l.getBlockState(PLANT.above()).is(EternalDefense.STRUCTURE_PART.get()),"a blast beside it leaves the FOB Beacon standing, its upper cell whole");
            for(var v:f.villagers())h.assertTrue(v.isAlive()&&v.getHealth()==v.getMaxHealth(),"the blast hurt no villager");
            var v=f.villagers().get(1);v.kill();h.assertFalse(v.isAlive(),"/kill still works on them");
            var pillager=EntityType.PILLAGER.create(l);pillager.moveTo(AWAY.getX()+.5,AWAY.getY(),AWAY.getZ()+.5,0,0);
            pillager.setWave(1);int village=ardentDrops(l,pillager,f.p(),300);
            pillager.setWave(0);int patrol=ardentDrops(l,pillager,f.p(),300);
            pillager.setWave(1);pillager.getPersistentData().putBoolean("eternalRaider",true);int ours=ardentDrops(l,pillager,f.p(),300);
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
            command(l,p,"eternaldefense fob complete");
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
                var fire=EternalDefense.FIRE_FLARE.get();int flares=count(p,fire);
                Fobs.button(p,PLANT,Fobs.BUY_FIRE);
                h.assertTrue(ArdentEnergy.balance(p)==20&&count(p,fire)==flares,"a PLANTED FOB sells nothing");
                command(l,p,"eternaldefense fob complete");
                Fobs.button(p,PLANT,Fobs.BUY_FIRE);
                h.assertTrue(ArdentEnergy.balance(p)==20-SupportRules.FIRE_FLARE_PRICE&&count(p,fire)==flares+1,"an ACTIVE FOB sells a Fire Support Flare for "+SupportRules.FIRE_FLARE_PRICE+": "+ArdentEnergy.balance(p));
                int supply=count(p,EternalDefense.SUPPLY_FLARE.get()),back=count(p,EternalDefense.RETURN_FLARE.get());
                Fobs.button(p,PLANT,Fobs.BUY_SUPPLY);Fobs.button(p,PLANT,Fobs.BUY_RETURN);
                h.assertTrue(ArdentEnergy.balance(p)==20-SupportRules.FIRE_FLARE_PRICE-SupportRules.SUPPLY_FLARE_PRICE-SupportRules.RETURN_FLARE_PRICE
                    &&count(p,EternalDefense.SUPPLY_FLARE.get())==supply+1&&count(p,EternalDefense.RETURN_FLARE.get())==back+1,"Supply and Return Flares at the Support Platform's prices");
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
            h.assertTrue(command(l,p,"eternaldefense fob complete")>0&&fob.state==FobRules.State.ACTIVE,"complete");
            h.assertTrue(command(l,p,"eternaldefense fob list")==1&&command(l,p,"eternaldefense fob status")==1,"list and status");
            h.assertTrue(command(l,p,"eternaldefense fob reset")==1&&fob.state==FobRules.State.PLANTED&&!fob.activated,"reset: PLANTED again, never activated");
            // the Recovery Shovel: a confirmation, then the FOB is gone and its FOB Beacon comes back
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(EternalDefense.CONTROLLER.get()));
            int beacons=count(p,EternalDefense.FOB_BEACON_ITEM.get());
            Fobs.requestRemoval(p,PLANT.above());
            String token=BeaconActions.pendingToken(p);
            h.assertTrue(!token.isEmpty()&&fob(l)!=null,"the shovel asks first (on the upper cell too); nothing is removed yet");
            h.assertTrue(BeaconActions.confirm(p,token),"confirmed");
            BeaconNetwork.sendState(p,"close","","");   // as the confirm button's answer does (the client's confirmation screen closes)
            h.assertTrue(fob(l)==null&&FobData.get(l).fobs.isEmpty(),"the FOB is deleted");
            h.assertTrue(l.getBlockState(PLANT).isAir()&&l.getBlockState(PLANT.above()).isAir(),"its block and upper cell are gone");
            h.assertTrue(count(p,EternalDefense.FOB_BEACON_ITEM.get())==beacons+1,"the FOB Beacon is back in the inventory");
            var v=f.villagers().get(0);float hp=v.getHealth();v.hurt(l.damageSources().playerAttack(p),2);
            h.assertTrue(v.getHealth()<hp,"its villagers can be hurt again");
        }).thenExecute(()->{p.setItemInHand(InteractionHand.MAIN_HAND,hand);clear(l);}).thenSucceed();
    }

    // ---- the supply rail (0.0.24, FOB slice 2) ---------------------------------------------------------------------------------------------------
    /**
     * The owner's round trip (0.0.30): two rails side by side along X, 30 blocks south of the village, 156 blocks long, joined by a curve at
     * each end, so a cart goes out on lane A (z 38) and comes home on lane B (z 39) without stopping. The Receiving Gate stands across both
     * near the west end, the Delivery Gate near the east end, both facing east: the anchor (right column) north of lane A, the left column south
     * of lane B.
     */
    static final int RAIL_Z=38,LANE_A=RAIL_Z,LANE_B=RAIL_Z+1,RAIL_FROM=-78,RAIL_TO=78;
    static final BlockPos RECEIVING=new BlockPos(VILLAGE.getX()-70,VILLAGE.getY(),LANE_A-1),DELIVERY=new BlockPos(VILLAGE.getX()+70,VILLAGE.getY(),LANE_A-1);
    /** The rail fob_12 places a Delivery Gate on. */
    static final BlockPos GATE_RAIL=VILLAGE.offset(8,0,-9);
    static BlockState rail(RailShape shape){return Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,shape);}
    /** The loop's rail at (x, lane): straight along X, and the two curves of each U-turn. */
    static BlockState loopRail(int x,int z){
        if(x==RAIL_TO)return rail(z==LANE_A?RailShape.SOUTH_WEST:RailShape.NORTH_WEST);
        if(x==RAIL_FROM)return rail(z==LANE_A?RailShape.SOUTH_EAST:RailShape.NORTH_EAST);
        return rail(RailShape.EAST_WEST);
    }
    /** An ACTIVE FOB with the loop and both gates linked to it (no ride yet). */
    static FobData.Fob route(Fixture f){
        var l=f.l();
        for(int x=RAIL_FROM-2;x<=RAIL_TO+2;x++)for(int z=LANE_A-2;z<=LANE_B+2;z++)for(int y=0;y<3;y++){
            var at=new BlockPos(VILLAGE.getX()+x,VILLAGE.getY()+y,z);
            if(!l.getBlockState(at.below()).isSolid()&&y==0)l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
            boolean lane=y==0&&(z==LANE_A||z==LANE_B)&&x>=RAIL_FROM&&x<=RAIL_TO;
            var want=lane?loopRail(x,z):Blocks.AIR.defaultBlockState();
            var b=l.getBlockState(at).getBlock();
            if(l.getBlockState(at)!=want&&!(b instanceof RailGates.GateBlock)&&!(b instanceof RailGates.Part)&&!(b instanceof RailGates.Column))l.setBlock(at,want,2);
        }
        // vanilla shapes a rail by its neighbours as it is placed (two lanes side by side would join up): the shapes are set again, unshaped
        for(int x=RAIL_FROM;x<=RAIL_TO;x++)for(int z:new int[]{LANE_A,LANE_B}){var at=new BlockPos(VILLAGE.getX()+x,VILLAGE.getY(),z);if(l.getBlockState(at)!=loopRail(x,z))l.setBlock(at,loopRail(x,z),2|16);}
        var fob=fob(l);var d=FobData.get(l);
        for(var kind:RailGates.Kind.values()){
            var at=kind==RailGates.Kind.DELIVERY?DELIVERY:RECEIVING;
            var block=(kind==RailGates.Kind.DELIVERY?EternalDefense.DELIVERY_GATE:EternalDefense.RECEIVING_GATE).get();
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

    /**
     * The owner's test, as a round trip (0.0.30): a vanilla plain minecart with a seated player goes out on lane A through both gates, turns at
     * the loop's east end and comes home on lane B through both gates again, without touching them; the ride opens the route on the way out
     * and delivers on the way home. The cart is kept at a steady 0.4 a tick along whatever way the rail takes it (as powered rails would).
     */
    @GameTest(template="empty3x3x3",batch="fob_09",timeoutTicks=2400)
    public static void aRiddenMinecartOpensTheRouteAndARoundTripDelivers(GameTestHelper h){
        var f=village(h,10);var l=f.l();var p=f.p();var cart=new net.minecraft.world.entity.vehicle.Minecart[1];var chestCart=new Entity[1];int before[]={0};boolean[] drive={false};
        double[] slowest={9},laneOut={0},laneHome={0};
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"eternaldefense fob complete");
            var fob=route(f);farmVillage(fob);
            h.assertTrue(fob.hasRoute()&&!fob.commissioned&&lamp(l,RECEIVING)==RailGates.Lamp.RED,"both gates linked, no route yet: red");
            for(var at:List.of(RECEIVING,DELIVERY)){
                h.assertTrue(l.getBlockState(RailGates.column(at,Direction.EAST)).getBlock() instanceof RailGates.Column,"each gate stands on its two columns");
                for(var lane:RailGates.lanes(at,Direction.EAST))h.assertTrue(l.getBlockState(lane).is(Blocks.RAIL),"a rail runs through each lane: "+lane.toShortString());
            }
            // a chest minecart rolling through counts for nothing (it cannot carry a rider)
            var chest=new net.minecraft.world.entity.vehicle.MinecartChest(l,RECEIVING.getX()-3.5,VILLAGE.getY(),LANE_A+.5);l.addFreshEntity(chest);chest.setDeltaMovement(.4,0,0);chestCart[0]=chest;
            var c=new net.minecraft.world.entity.vehicle.Minecart(net.minecraft.world.entity.EntityType.MINECART,l);c.setPos(RECEIVING.getX()-5.5,VILLAGE.getY(),LANE_A+.5);l.addFreshEntity(c);cart[0]=c;
            goTo(p,l,new BlockPos(RECEIVING.getX()-5,VILLAGE.getY(),LANE_A));p.startRiding(c,true);before[0]=queued(l);
        }).thenIdle(20).thenExecute(()->{h.assertTrue(Routes.RUNS.isEmpty(),"the chest minecart started nothing");chestCart[0].discard();drive[0]=true;cart[0].setDeltaMovement(.4,0,0);})
        .thenExecute(()->h.onEachTick(()->{var c=cart[0];if(c==null||!c.isAlive()||!drive[0])return;
            var v=c.getDeltaMovement();double speed=Math.hypot(v.x,v.z);
            if(speed<.01)c.setDeltaMovement(.4,v.y,0);else c.setDeltaMovement(v.x*.4/speed,v.y,v.z*.4/speed);
            // the speed through and between the gates (not at the start or the turns): a gate in the way would stop the cart
            if(c.getX()>RECEIVING.getX()-1&&c.getX()<DELIVERY.getX()+1&&c.tickCount>40)slowest[0]=Math.min(slowest[0],Math.hypot(c.getX()-c.xo,c.getZ()-c.zo));}))
        .thenWaitUntil(()->h.assertTrue(fob(l).commissioned,"the ride through the Delivery Gate opens the route"))
        .thenExecute(()->{
            var fob=fob(l);laneOut[0]=cart[0].getZ();
            h.assertTrue(fob.baseline>=Math.ceil(fob.separation()/RouteRules.MAX_SPEED)&&fob.clock==RouteRules.CLOCK,"the one-way time is the route's T ("+fob.baseline+" ticks), the inspection full");
            h.assertTrue(Routes.RUNS.containsKey(fob.id)&&Routes.RUNS.get(fob.id).stage==RouteRules.Stage.RETURN,"the ride goes on home with a load");
            h.assertTrue(Math.abs(laneOut[0]-(LANE_A+.5))<.3,"out on lane A (z "+laneOut[0]+")");
        }).thenIdle(25).thenExecute(()->h.assertTrue(lamp(l,DELIVERY)==RailGates.Lamp.AMBER&&lamp(l,RECEIVING)==RailGates.Lamp.AMBER,"both lights amber while the ride is under way"))
        .thenWaitUntil(()->h.assertTrue(Routes.RUNS.isEmpty()&&"ride".equals(fob(l).lastKind),"back through the Receiving Gate: the round trip delivers"))
        .thenExecute(()->{
            drive[0]=false;laneHome[0]=cart[0].getZ();cart[0].setDeltaMovement(0,0,0);   // stopped before the loop brings it round again
            var fob=fob(l);
            h.assertTrue(Math.abs(laneHome[0]-(LANE_B+.5))<.3,"home on lane B (z "+laneHome[0]+"): the cart came round the loop");
            int perTrip=melonsPerTrip(fob);
            h.assertTrue(queued(l)==before[0],"0.0.31: nothing goes to the reward chest's queue");
            h.assertTrue(fob.lastItems==RouteRules.ACTIVE_FACTOR*perTrip&&stored(fob,Items.MELON_SLICE)==RouteRules.ACTIVE_FACTOR*perTrip&&fob.clock==RouteRules.CLOCK,
                "the round trip brought two trips of melon slices ("+fob.lastItems+", "+perTrip+" a trip) into the gate's storage and renewed the inspection");
            h.assertTrue(p.getVehicle()==cart[0],"the rider stayed seated the whole way");
            h.assertTrue(slowest[0]>.25,"the cart never slowed against the gates (slowest step "+slowest[0]+" blocks; vanilla moves a ridden cart 0.75 of its speed, 0.3 here)");
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.GREEN&&lamp(l,DELIVERY)==RailGates.Lamp.GREEN,"after the round trip both lights are green");
            h.assertTrue(l.getBlockState(RailGates.column(RECEIVING,Direction.EAST)).getValue(RailGates.SIGNAL)==RailGates.Lamp.GREEN,"the left column's lamps too");
            p.stopRiding();if(cart[0]!=null)cart[0].discard();clearRoute(l);clear(l);goTo(p,l,STAND);
        }).thenSucceed();
    }
    /**
     * 0.0.31: the tribute is the FOB's loot slots. A farm village (emeralds, mine and specialty switched off, the farm on melon slices) gets the
     * whole trip in its one slot; a passive trip puts them into the Receiving Gate's storage with its chat line, and stops when the inspection
     * runs out. The storage is take only.
     */
    @GameTest(template="empty3x3x3",batch="fob_10",timeoutTicks=1200)
    public static void passiveTributeArrivesAndStopsWhenTheClockRunsOut(GameTestHelper h){
        var f=village(h,10);var l=f.l();var p=f.p();int[] before={0},after={0};
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"eternaldefense fob complete");
            var fob=route(f);var d=FobData.get(l);farmVillage(fob);
            fob.commissioned=true;fob.baseline=60;fob.clock=RouteRules.CLOCK;fob.nextPassive=d.onlineTicks+40;
            fob.lastNoteAt=d.onlineTicks-RouteRules.NOTE_TICKS;before[0]=queued(l);
            h.assertTrue(RouteRules.period(60)==RouteRules.MIN_PERIOD,"a short, fast route still waits 5 minutes between deliveries");
        }).thenWaitUntil(()->h.assertTrue("passive".equals(fob(l).lastKind),"a delivery by itself"))
        .thenIdle(25).thenExecute(()->{
            var fob=fob(l);int perTrip=melonsPerTrip(fob);
            var y=fob.yield();double alone=RouteRules.itemsPerTrip(RouteRules.Slot.FARM,fob.loot(RouteRules.Slot.FARM),y),shared=RouteRules.itemsPerTrip(RouteRules.Slot.FARM,fob.loot(RouteRules.Slot.FARM),new RouteRules.Yield(new boolean[]{true,true,true,true},y.distance(),y.villagers(),y.period(),y.level()));
            h.assertTrue(perTrip>=2&&Math.abs(alone-4*shared)<1e-9,"one slot on takes the whole trip: "+perTrip+" melon slices, four times its share with all four on ("+fob.workers+" villagers)");
            h.assertTrue(fob.lastItems==perTrip&&stored(fob,Items.MELON_SLICE)==perTrip,"they are in the Receiving Gate's storage ("+stored(fob,Items.MELON_SLICE)+")");
            h.assertTrue(queued(l)==before[0],"and not in the reward chest's queue");
            h.assertTrue(fob.note.isEmpty(),"the chat line went out and the note was cleared");
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.GREEN,"green while it runs");
            // the storage is take only: a player's stack never goes in
            var menu=new RailGates.GateMenu(0,p.getInventory(),RECEIVING,new CompoundTag(),true,fob.depot());
            h.assertTrue(!menu.slots.get(0).mayPlace(new ItemStack(Items.DIRT))&&menu.quickMoveStack(p,FobData.DEPOT_SLOTS)==ItemStack.EMPTY,"nothing can be put into the storage");
            command(l,p,"eternaldefense fob overdue");h.assertTrue(fob.clock==20,"/eternaldefense fob overdue: the inspection runs out within a second");
        }).thenIdle(60).thenExecute(()->{
            h.assertTrue(fob(l).clock==0&&lamp(l,RECEIVING)==RailGates.Lamp.RED&&lamp(l,DELIVERY)==RailGates.Lamp.RED,"the inspection ran out: both lights red");
            after[0]=stored(fob(l),Items.MELON_SLICE);fob(l).nextPassive=FobData.get(l).onlineTicks+20;
        }).thenIdle(100).thenExecute(()->{
            h.assertTrue(stored(fob(l),Items.MELON_SLICE)==after[0],"no delivery while the inspection is overdue");
            clearRoute(l);clear(l);
        }).thenSucceed();
    }
    /** 0.0.31: a full storage pauses deliveries (amber); a round trip still renews the inspection but brings nothing; emptied, it runs again. */
    @GameTest(template="empty3x3x3",batch="fob_11",timeoutTicks=600)
    public static void aFullStoragePausesDeliveriesButARideStillRenews(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"eternaldefense fob complete");
            var fob=route(f);farmVillage(fob);fob.commissioned=true;fob.baseline=60;fob.clock=RouteRules.CLOCK;
            for(int i=0;i<FobData.DEPOT_SLOTS;i++)fob.depot().setItem(i,new ItemStack(Items.DIRT,64));
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.AMBER,"amber: the storage is full");
            var fob=fob(l);fob.lastKind="";
            var cart=new net.minecraft.world.entity.vehicle.Minecart(net.minecraft.world.entity.EntityType.MINECART,l);cart.setPos(RECEIVING.getX()-2.5,VILLAGE.getY(),LANE_A+.5);l.addFreshEntity(cart);p.startRiding(cart,true);
            var d=FobData.get(l);
            Routes.pass(l,d,fob,RailGates.Kind.RECEIVING,p,cart);Routes.pass(l,d,fob,RailGates.Kind.DELIVERY,p,cart);Routes.pass(l,d,fob,RailGates.Kind.RECEIVING,p,cart);
            h.assertTrue(fob.clock==RouteRules.CLOCK,"the round trip renewed the inspection");
            h.assertTrue(fob.lastKind.isEmpty()&&stored(fob,Items.MELON_SLICE)==0,"and brought nothing while the storage is full");
            p.stopRiding();cart.discard();
            fob.nextPassive=d.onlineTicks+20;
        }).thenIdle(100).thenExecute(()->{
            h.assertTrue(stored(fob(l),Items.MELON_SLICE)==0&&fob(l).lastKind.isEmpty(),"no passive delivery either");
            var fob=fob(l);for(int i=0;i<FobData.DEPOT_SLOTS;i++)fob.depot().setItem(i,ItemStack.EMPTY);
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.GREEN,"storage emptied: green again");
            clearRoute(l);clear(l);
        }).thenSucceed();
    }
    /** A farm village: emeralds, the mine and the specialty switched off, the farm on melon slices (the villagers are the ones the FOB counts). */
    static void farmVillage(FobData.Fob fob){
        fob.slotOn[0]=false;fob.slotOn[1]=false;fob.slotOn[2]=true;fob.slotOn[3]=false;Arrays.fill(fob.tally,0);
        for(int i=0;i<RouteRules.FARM_LOOT.size();i++)if(RouteRules.FARM_LOOT.get(i).id().equals("minecraft:melon_slice"))fob.farm=i;
    }
    static int melonsPerTrip(FobData.Fob fob){return (int)Math.floor(RouteRules.itemsPerTrip(RouteRules.Slot.FARM,fob.loot(RouteRules.Slot.FARM),fob.yield())+1e-9);}
    static int stored(FobData.Fob fob,Item item){int n=0;for(int i=0;i<fob.depot().getContainerSize();i++)if(fob.depot().getItem(i).is(item))n+=fob.depot().getItem(i).getCount();return n;}
    /** The Delivery Gate is sold once per FOB, stands only over a rail in the FOB's village, the Receiving Gate only at home; a FOB's removal unlinks its gates. */
    @GameTest(template="empty3x3x3",batch="fob_12",timeoutTicks=600)
    public static void gatesArePlacedAndSoldByTheRules(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();var mode=p.gameMode.getGameModeForPlayer();
        BlockPos rail=GATE_RAIL,second=GATE_RAIL.north();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");
            p.setGameMode(GameType.SURVIVAL);
            try{
                for(var s:List.of(new ItemStack(Items.IRON_INGOT,64),new ItemStack(Items.COPPER_INGOT,64),new ItemStack(Items.REDSTONE,64),new ItemStack(Items.CHEST,8),new ItemStack(EternalDefense.LOGISTICS.get(),8),new ItemStack(EternalDefense.ARDENT_ENERGY.get(),16)))p.getInventory().add(s);
                Fobs.open(p,PLANT);
                Fobs.button(p,PLANT,Fobs.BUY_GATE);
                h.assertTrue(count(p,EternalDefense.DELIVERY_GATE_ITEM.get())==0,"a PLANTED FOB sells no gate");
                command(l,p,"eternaldefense fob complete");
                Fobs.button(p,PLANT,Fobs.BUY_GATE);
                h.assertTrue(count(p,EternalDefense.DELIVERY_GATE_ITEM.get())==1,"an ACTIVE FOB sells its Supply Delivery Gate");
                p.closeContainer();
                for(int i=0;i<p.getInventory().items.size();i++)if(p.getInventory().items.get(i).is(EternalDefense.DELIVERY_GATE_ITEM.get()))p.getInventory().items.set(i,ItemStack.EMPTY);   // it goes on the rails below
                // two rails side by side (0.0.30): the gate stands across both, whichever of them is clicked
                for(var at:List.of(rail,second))l.setBlock(at,rail(RailShape.EAST_WEST),2);
                for(var at:List.of(rail,second))l.setBlock(at,rail(RailShape.EAST_WEST),2|16);   // vanilla joined the two up as they were laid
                var gate=new ItemStack(EternalDefense.DELIVERY_GATE_ITEM.get());
                h.assertTrue(useOn(f,gate,VILLAGE.offset(10,-1,-4))==InteractionResult.FAIL,"not on a rail: refused");
                h.assertTrue(useOn(f,gate,rail).consumesAction(),"on the rails in the village: placed");
                var anchor=fob(l).deliveryGate;
                h.assertTrue(anchor!=null&&l.getBlockState(anchor).is(EternalDefense.DELIVERY_GATE.get()),"it stands across the rails and links to the FOB by itself");
                var facing=l.getBlockState(anchor).getValue(RailGates.FACING);
                h.assertTrue(new HashSet<>(RailGates.lanes(anchor,facing)).equals(Set.of(rail,second)),"its two lanes are the two rails: "+RailGates.lanes(anchor,facing));
                h.assertTrue(l.getBlockState(RailGates.column(anchor,facing)).is(EternalDefense.DELIVERY_GATE_LEFT.get()),"its left column");
                for(var part:RailGates.parts(anchor,facing))h.assertTrue(l.getBlockState(part).is(EternalDefense.GATE_PART.get()),"its beam at "+part);
                h.assertTrue(l.getBlockState(rail).is(Blocks.RAIL)&&l.getBlockState(second).is(Blocks.RAIL),"the rails stay the player's");
                Fobs.open(p,PLANT);Fobs.button(p,PLANT,Fobs.BUY_GATE);p.closeContainer();
                h.assertTrue(count(p,EternalDefense.DELIVERY_GATE_ITEM.get())==0,"a second gate for the same FOB is refused");
                var home=new ItemStack(EternalDefense.RECEIVING_GATE_ITEM.get());
                l.setBlock(rail.offset(0,0,-6),rail(RailShape.EAST_WEST),2);
                h.assertTrue(useOn(f,home,rail.offset(0,0,-6))==InteractionResult.FAIL,"a Receiving Gate outside the base zone: refused");
                // the shovel takes it back (from its left column too), and the item remembers its FOB
                h.assertTrue(anchor.equals(RailGates.anchor(l,RailGates.column(anchor,facing))),"a click on the left column is a click on the gate");
                Routes.pickUp(p,anchor);
                h.assertTrue(l.getBlockState(anchor).isAir()&&l.getBlockState(RailGates.column(anchor,facing)).isAir()&&fob(l).deliveryGate==null,"the shovel takes the whole gate back");
                var back=p.getInventory().items.stream().filter(s->s.is(EternalDefense.DELIVERY_GATE_ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
                h.assertTrue(back.hasTag()&&back.getTag().getLong("Fob")==fob(l).id,"the item remembers its FOB");
                // one rail still works (the other lane stays empty)
                l.setBlock(second,Blocks.AIR.defaultBlockState(),2);
                h.assertTrue(useOn(f,back,rail).consumesAction(),"on a lone rail: placed");
                var lone=fob(l).deliveryGate;
                h.assertTrue(lone!=null&&RailGates.lanes(lone,l.getBlockState(lone).getValue(RailGates.FACING)).contains(rail),"the lone rail runs through one of its lanes");
                Routes.pickUp(p,lone);
                // a FOB's removal turns its gates UNLINKED
                var fob=route(f);
                FobData.get(l).fobs.remove(fob.id);
            }finally{p.setGameMode(mode);}
        }).thenIdle(30).thenExecute(()->{
            h.assertTrue(l.getBlockEntity(RECEIVING) instanceof RailGates.GateEntity be&&be.fobId==0,"its FOB gone, the gate is UNLINKED");
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.RED,"and red");
            for(var at:List.of(rail,second,rail.offset(0,0,-6)))l.setBlock(at,Blocks.AIR.defaultBlockState(),2);
            clearRoute(l);clear(l);
        }).thenSucceed();
    }
    // ---- slice 3 (0.0.25): SOS, DOWN, repair --------------------------------------------------------------------------------------------------
    static int ardent(ServerPlayer p){return count(p,EternalDefense.ARDENT_ENERGY.get());}
    static boolean carriesSosMap(ServerPlayer p){var d=FobData.get(p.serverLevel());return FobSos.carries(p,d.sosSerial);}
    /** An SOS is called (operator command), the map handed out; a player in the village starts the raid; winning it ends the SOS and restarts the clock. */
    @GameTest(template="empty3x3x3",batch="fob_13",timeoutTicks=1200)
    public static void anSosIsAnsweredAndWon(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"eternaldefense fob complete");
            goTo(p,l,AWAY);
            var d=FobData.get(l);var fob=fob(l);
            h.assertTrue(command(l,p,"eternaldefense fob sos")==1&&d.sosFob==fob.id&&d.sosLeft==SosRules.RESPONSE_TICKS,"/eternaldefense fob sos calls an SOS at the FOB, 7 days to answer");
            h.assertTrue(carriesSosMap(p),"the SOS map with the red X is handed out");
            var card=new CompoundTag();Fobs.status(p,card);
            h.assertTrue(card.contains("fobSos")&&card.getCompound("fobSos").getString("name").equals(fob.name),"every status card names the FOB that calls");
            h.assertTrue(command(l,p,"eternaldefense fob sos")==0&&d.sosFob==fob.id,"a second SOS is impossible while one is open");
            h.assertTrue(!fob.raidRunning(),"nothing happens while nobody is there");
        }).thenIdle(45).thenExecute(()->{
            var d=FobData.get(l);
            h.assertTrue(d.sosLeft<SosRules.RESPONSE_TICKS&&d.sosLeft>=SosRules.RESPONSE_TICKS-60,"the response timer runs down by the second: "+d.sosLeft);
            goTo(p,l,STAND);
        }).thenWaitUntil(()->h.assertTrue(fob(l).raidRunning(),"a player in the village starts the SOS raid"))
        .thenExecute(()->{
            var fob=fob(l);var raid=l.getRaids().get(fob.raidId);
            h.assertTrue(raid!=null&&raid.getBadOmenLevel()==1,"a vanilla raid at Bad Omen I");
            h.assertTrue(!p.hasEffect(MobEffects.BAD_OMEN),"the Bad Omen went into the raid");
            h.assertTrue(fob.raidVillagers==3,"the villagers counted at its start");
            long left=FobData.get(l).sosLeft;
            command(l,p,"eternaldefense fob complete");
            var d=FobData.get(l);
            h.assertTrue(d.sosFob==0&&fob(l).state==FobRules.State.ACTIVE,"won: the SOS is over, the FOB stays ACTIVE");
            h.assertTrue(d.sosClock>=SosRules.SOS_MIN&&d.sosClock<=SosRules.SOS_MAX,"the clock starts again (10 to 12 hours of play): "+d.sosClock);
            h.assertTrue(!carriesSosMap(p),"the SOS map burnt");
        }).thenExecute(()->clear(l)).thenSucceed();
    }
    /** Nobody answers: DOWN (no shop, no tribute, the route's clock paused, the lights red); the repair's base price brings it back ACTIVE. */
    @GameTest(template="empty3x3x3",batch="fob_14",timeoutTicks=1200)
    public static void anUnansweredSosPutsItDownAndARepairBringsItBack(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();var mode=p.gameMode.getGameModeForPlayer();long[] clock={0};
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"eternaldefense fob complete");
            var fob=route(f);fob.commissioned=true;fob.baseline=60;fob.clock=RouteRules.CLOCK;fob.workers=3;
            goTo(p,l,AWAY);
            command(l,p,"eternaldefense fob sos");h.assertTrue(command(l,p,"eternaldefense fob sos expire")==1,"/eternaldefense fob sos expire");
        }).thenIdle(30).thenExecute(()->{
            var fob=fob(l);var d=FobData.get(l);
            h.assertTrue(fob.state==FobRules.State.DOWN&&fob.activated,"nobody came in time: DOWN (it had won before)");
            h.assertTrue(d.sosFob==0&&d.sosClock>=SosRules.SOS_MIN,"the SOS is over, the clock runs again");
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.RED&&lamp(l,DELIVERY)==RailGates.Lamp.RED,"both gates red");
            clock[0]=fob.clock;
            goTo(p,l,STAND);
        }).thenIdle(45).thenExecute(()->{
            var fob=fob(l);
            h.assertTrue(fob.clock==clock[0],"the route's inspection clock is paused while DOWN");
            h.assertTrue(!fob.raidRunning(),"no raid starts at a DOWN FOB");
            p.setGameMode(GameType.SURVIVAL);
            try{
                p.getInventory().add(new ItemStack(EternalDefense.ARDENT_ENERGY.get(),32));int before=ardent(p);
                Fobs.open(p,PLANT);Fobs.button(p,PLANT,Fobs.BUY_SUPPLY);
                h.assertTrue(count(p,EternalDefense.SUPPLY_FLARE.get())==0&&ardent(p)==before,"the shop sells nothing while DOWN");
                var state=Fobs.state(p,fob,"");
                h.assertTrue(state.getBoolean("mayRepair")&&state.getInt("repairTotal")==Economy.fobRepairBase()&&state.getList("lost",10).isEmpty(),"the screen offers the repair at its base price: "+state.getInt("repairTotal"));
                Fobs.button(p,PLANT,Fobs.REPAIR);p.closeContainer();
                h.assertTrue(fob(l).state==FobRules.State.ACTIVE&&ardent(p)==before-Economy.fobRepairBase(),"paid: ACTIVE again");
            }finally{p.setGameMode(mode);}
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(lamp(l,RECEIVING)==RailGates.Lamp.GREEN,"the route as it was: green");
            h.assertTrue(fob(l).clock<clock[0],"and its clock runs again");
            clearRoute(l);clear(l);
        }).thenSucceed();
    }
    /** A villager killed during a FOB's raid is recorded; the repair pays for it and brings it back with its name, profession, level and trades. */
    @GameTest(template="empty3x3x3",batch="fob_15",timeoutTicks=1200)
    public static void villagersLostInARaidComeBackWithTheirTrades(GameTestHelper h){
        var f=village(h,4);var l=f.l();var p=f.p();var mode=p.gameMode.getGameModeForPlayer();
        var trained=f.villagers().get(0);
        trained.setVillagerData(trained.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(3));trained.setVillagerXp(70);
        trained.setCustomName(Component.literal("Bertha"));
        trained.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new ItemStack(Items.EMERALD,3),new ItemStack(Items.BREAD,7),10,5,.05f));
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction()&&fob(l).raidRunning(),"planted: its raid runs");
            for(int i=0;i<3;i++){var v=f.villagers().get(i);v.hurt(l.damageSources().playerAttack(p),1000f);}
            h.assertTrue(!trained.isAlive(),"during the raid a villager can die");
        }).thenWaitUntil(()->h.assertTrue(fob(l).state==FobRules.State.DOWN,"1 of 4 alive: DOWN"))
        .thenExecute(()->{
            var fob=fob(l);
            h.assertTrue(fob.deadVillagers.size()==3,"three lost villagers recorded: "+fob.deadVillagers.size());
            var r=fob.deadVillagers.getCompound(0);
            h.assertTrue(r.getString("name").equals("Bertha")&&r.getString("profession").equals("minecraft:farmer")&&r.getInt("level")==3,"its name, profession and level: "+r);
            h.assertTrue(!fob.deadVillagers.getCompound(1).getString("name").isEmpty(),"one without a name tag gets a name");
            p.setGameMode(GameType.SURVIVAL);
            try{
                p.getInventory().add(new ItemStack(EternalDefense.ARDENT_ENERGY.get(),64));int before=ardent(p);
                Fobs.open(p,PLANT);var state=Fobs.state(p,fob,"");
                int expect=Economy.fobRepairBase()+Economy.fobRepairVillager(3)+2*Economy.fobRepairVillager(1);
                h.assertTrue(state.getList("lost",10).size()==3&&state.getInt("repairTotal")==expect,"the list prices each villager, the total is the sum: "+state.getInt("repairTotal")+" = "+expect);
                Fobs.button(p,PLANT,Fobs.REPAIR);p.closeContainer();
                h.assertTrue(ardent(p)==before-expect,"paid the total");
            }finally{p.setGameMode(mode);}
            var back=l.getEntitiesOfClass(Villager.class,new AABB(PLANT).inflate(8),v->v.isAlive()&&v.hasCustomName()&&v.getCustomName().getString().equals("Bertha"));
            h.assertTrue(back.size()==1,"Bertha is back");
            var b=back.get(0);
            h.assertTrue(b.getVillagerData().getProfession()==net.minecraft.world.entity.npc.VillagerProfession.FARMER&&b.getVillagerData().getLevel()==3&&b.getVillagerXp()==70,"a level 3 farmer with her experience");
            h.assertTrue(b.getOffers().stream().anyMatch(o->o.getResult().is(Items.BREAD)&&o.getResult().getCount()==7),"with her trades");
            h.assertTrue(b.getHealth()==b.getMaxHealth()&&b.getPersistentData().getLong(Fobs.VILLAGER_TAG)==fob(l).id,"healthy, and the FOB's again");
            h.assertTrue(fob(l).deadVillagers.isEmpty(),"the records are used up");
            h.assertTrue(fob(l).state==FobRules.State.PLANTED&&fob(l).raidRunning(),"it had never won: PLANTED, and its raid starts again");
        }).thenExecute(()->clear(l)).thenSucceed();
    }
    /** The shovel during an SOS calls it off and restarts the clock; the clock waits in the Home raid's warning day. */
    @GameTest(template="empty3x3x3",batch="fob_16",timeoutTicks=1200)
    public static void theSosClockAndItsEdgeCases(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();long[] at={0};
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"eternaldefense fob complete");
            goTo(p,l,AWAY);command(l,p,"eternaldefense fob sos");
            var d=FobData.get(l);var fob=fob(l);h.assertTrue(d.sosFob==fob.id,"an SOS is open");
            goTo(p,l,STAND);h.assertTrue(Fobs.remove(p,fob.id),"the shovel takes the FOB back (confirmed)");
            h.assertTrue(d.sosFob==0&&d.sosClock>=SosRules.SOS_MIN&&d.sosClock<=SosRules.SOS_MAX,"the SOS is called off and the clock starts again");
            h.assertTrue(!carriesSosMap(p),"its map burnt");
            p.getInventory().clearContent();
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted again");command(l,p,"eternaldefense fob complete");
            var d=FobData.get(l);var c=CampaignData.get(l.getServer().overworld());
            c.phase="preparation";c.preparationTicks=Rules.intervalDays(c.rewardTier)*24000L-TacticalRules.WARNING_TICKS;c.respiteTicks=0;c.setDirty();
            at[0]=d.sosClock;
        }).thenIdle(45).thenExecute(()->{
            var d=FobData.get(l);var c=CampaignData.get(l.getServer().overworld());
            h.assertTrue(d.sosClock==at[0],"the Home raid's warning day: the SOS clock waits");
            c.preparationTicks=0;c.setDirty();
        }).thenIdle(45).thenExecute(()->{
            h.assertTrue(FobData.get(l).sosClock<at[0],"and runs again after it");
            clear(l);
        }).thenSucceed();
    }
    // ---- 0.0.26 (owner): the handheld Return Flare needs the Support Cannon; an operation's return gate does not ---------------------------------
    @GameTest(template="empty3x3x3",batch="fob_17",timeoutTicks=200)
    public static void theReturnFlareNeedsTheCannonButAnOperationsGateDoesNot(GameTestHelper h){
        var hf=HuntGameTests.fixture(h);var l=hf.l();var p=HuntGameTests.player(h);
        BlockPos pad=HuntGameTests.BEACON.offset(0,0,-6),gun=HuntGameTests.BEACON.offset(-5,0,-5);
        h.startSequence().thenExecute(()->{
            var sd=SupportData.get(l);sd.setPlatform(p.getUUID(),pad);var base=sd.of(p.getUUID());BlockPos oldCannon=base.cannon;
            try{
                l.setBlock(pad,EternalDefense.SUPPORT_PLATFORM.get().defaultBlockState(),3);
                if(l.getBlockEntity(pad) instanceof SupportPlatform.PlatformEntity be){be.owner=p.getUUID();be.ownerName=p.getGameProfile().getName();}
                base.cannon=null;sd.setDirty();
                HuntGameTests.goTo(p,l,HuntGameTests.BEACON.offset(2,0,2));
                h.assertTrue("no_cannon".equals(SupportCalls.problem(p,SupportCalls.Kind.RETURN)),"a Return Flare without the cannon is refused: "+SupportCalls.problem(p,SupportCalls.Kind.RETURN));
                l.setBlock(gun,EternalDefense.SUPPORT_CANNON.get().defaultBlockState(),3);sd.setCannon(p.getUUID(),gun);
                h.assertTrue(SupportCalls.problem(p,SupportCalls.Kind.RETURN)==null,"with the cannon it goes: "+SupportCalls.problem(p,SupportCalls.Kind.RETURN));
                // an operation with no ground opens its own return gate, cannon or not
                base.cannon=null;sd.setDirty();
                h.assertTrue(Hunts.returnGate(l,hf.d(),p,"OP TEST"),"the operation's return gate opens without a cannon");
                h.assertTrue(!l.getEntitiesOfClass(SupportFlares.FlareEntity.class,new AABB(p.blockPosition()).inflate(4)).isEmpty(),"its portal stands in front of the player");
            }finally{
                for(var f:l.getEntitiesOfClass(SupportFlares.FlareEntity.class,new AABB(p.blockPosition()).inflate(8)))f.discard();
                l.setBlock(gun,Blocks.AIR.defaultBlockState(),3);l.setBlock(pad,Blocks.AIR.defaultBlockState(),3);
                base.platform=null;base.cannon=oldCannon;sd.setDirty();
            }
        }).thenSucceed();
    }
    // ---- slice 4 (0.0.27): the Tactical Enchanting Table -------------------------------------------------------------------------------------------
    /** Where the enchanting table goes in the hunt tests' base zone (facing north: its second column east of it). */
    static final BlockPos ENCHANT=HuntGameTests.BEACON.offset(-4,0,4);
    static void clearEnchanting(ServerLevel l){
        for(int dx=-1;dx<=2;dx++)for(int dy=0;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){var at=ENCHANT.offset(dx,dy,dz);var s=l.getBlockState(at);
            if(s.is(EternalDefense.ENCHANTING_TABLE.get())||s.is(EternalDefense.STRUCTURE_PART.get()))l.setBlock(at,Blocks.AIR.defaultBlockState(),3);}
        var d=TacticalEnchanting.EnchantData.get(l);d.table=null;d.mk=0;d.seals.clear();d.setDirty();
    }
    /** {@code n} ACTIVE FOB records (no blocks: only their count matters to the table). */
    static void activeFobs(ServerLevel l,int n){
        var d=FobData.get(l);d.fobs.clear();d.sosFob=0;
        for(int i=0;i<n;i++){var f=d.add(VILLAGE.offset(400+i*100,0,0),i,0);f.state=FobRules.State.ACTIVE;f.activated=true;}
        d.setDirty();
    }
    static Enchantment enchantment(String id){return net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(new net.minecraft.resources.ResourceLocation(id));}
    static int buy(ServerPlayer p,TacticalEnchanting.EnchantMenu menu,String id,int level){
        int index=TacticalEnchanting.catalogue().indexOf(enchantment(id));TacticalEnchanting.button(p,menu,TacticalEnchanting.buyButton(index,level));return index;
    }
    /** The table stands only in the zone, one per base, as an anchor and three parts; the shovel takes it back. */
    @GameTest(template="empty3x3x3",batch="fob_18",timeoutTicks=300)
    public static void theEnchantingTableIsOnePerBaseInTheZone(GameTestHelper h){
        var hf=HuntGameTests.fixture(h);var l=hf.l();var p=HuntGameTests.player(h);
        h.startSequence().thenExecute(()->{
            clearEnchanting(l);HuntGameTests.goTo(p,l,ENCHANT.south(3));
            var item=new ItemStack(EternalDefense.ENCHANTING_TABLE_ITEM.get());
            var hit=new BlockHitResult(Vec3.atCenterOf(ENCHANT.below()),Direction.UP,ENCHANT.below(),false);
            h.assertTrue(((BlockItem)item.getItem()).place(new BlockPlaceContext(l,p,InteractionHand.MAIN_HAND,item.copy(),hit)).consumesAction(),"placed in the zone");
            var state=l.getBlockState(ENCHANT);h.assertTrue(state.is(EternalDefense.ENCHANTING_TABLE.get()),"the table stands");
            for(var cell:DefenseStructures.cells(ENCHANT,state))h.assertTrue(l.getBlockState(cell).is(EternalDefense.STRUCTURE_PART.get()),"its part at "+cell);
            h.assertTrue(DefenseStructures.cells(ENCHANT,state).size()==3,"2 wide, 2 tall, 1 deep: the anchor and three parts");
            h.assertTrue(ENCHANT.equals(TacticalEnchanting.EnchantData.get(l).table),"the base knows its table");
            var second=ENCHANT.offset(-4,0,0);var hit2=new BlockHitResult(Vec3.atCenterOf(second.below()),Direction.UP,second.below(),false);
            h.assertTrue(((BlockItem)item.getItem()).place(new BlockPlaceContext(l,p,InteractionHand.MAIN_HAND,item.copy(),hit2))==InteractionResult.FAIL,"a second table refused");
            var far=HuntGameTests.BEACON.offset(80,0,0);var hit3=new BlockHitResult(Vec3.atCenterOf(far.below()),Direction.UP,far.below(),false);
            clearEnchanting(l);
            h.assertTrue(((BlockItem)item.getItem()).place(new BlockPlaceContext(l,p,InteractionHand.MAIN_HAND,item.copy(),hit3))==InteractionResult.FAIL,"outside the zone refused");
            h.assertTrue(((BlockItem)item.getItem()).place(new BlockPlaceContext(l,p,InteractionHand.MAIN_HAND,item.copy(),hit)).consumesAction(),"placed again");
            int before=count(p,EternalDefense.ENCHANTING_TABLE_ITEM.get());
            h.assertTrue(BaseZone.relocate(p,l,ENCHANT.above()),"the shovel on its upper part");
            h.assertTrue(l.getBlockState(ENCHANT).isAir()&&count(p,EternalDefense.ENCHANTING_TABLE_ITEM.get())==before+1,"takes it back as an item");
            h.assertTrue(TacticalEnchanting.EnchantData.get(l).table==null,"and the base forgets it");
            clearEnchanting(l);
        }).thenSucceed();
    }
    /** Enchants for Ardent Energy, levels gated by ACTIVE FOBs, conflicts refused, curses never offered, the discount, the treasure seals. */
    @GameTest(template="empty3x3x3",batch="fob_19",timeoutTicks=300)
    public static void enchantsFollowTheFobLevelGate(GameTestHelper h){
        var hf=HuntGameTests.fixture(h);var l=hf.l();var p=HuntGameTests.player(h);var mode=p.gameMode.getGameModeForPlayer();
        h.startSequence().thenExecute(()->{
            clearEnchanting(l);activeFobs(l,0);HuntGameTests.goTo(p,l,ENCHANT.south(2));
            l.setBlock(ENCHANT,EternalDefense.ENCHANTING_TABLE.get().defaultBlockState(),3);
            p.setGameMode(GameType.SURVIVAL);
            try{
                p.getInventory().clearContent();for(int s=0;s<3;s++)p.getInventory().add(new ItemStack(EternalDefense.ARDENT_ENERGY.get(),64));   // 0.0.29: the pack's buys here take 104
                TacticalEnchanting.open(p,ENCHANT);h.assertTrue(p.containerMenu instanceof TacticalEnchanting.EnchantMenu,"the screen opens");
                var menu=(TacticalEnchanting.EnchantMenu)p.containerMenu;
                for(var e:TacticalEnchanting.catalogue())h.assertTrue(!e.isCurse(),"never a curse: "+TacticalEnchanting.id(e));
                h.assertTrue(TacticalEnchanting.catalogue().contains(enchantment("minecraft:mending")),"the four treasure enchants are listed (locked until a seal)");
                h.assertTrue(!TacticalEnchanting.catalogue().contains(enchantment("minecraft:binding_curse")),"curses are not");
                h.assertTrue(!TacticalEnchanting.enchantable(new ItemStack(Items.BOOK)),"no books");
                var sword=new ItemStack(Items.DIAMOND_SWORD);menu.gear.setItem(0,sword);
                int before=ardent(p);var sharp=enchantment("minecraft:sharpness");
                buy(p,menu,"minecraft:sharpness",1);
                h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(sharp,menu.gear.getItem(0))==1,"Sharpness I with no FOB");
                h.assertTrue(ardent(p)==before-TacticalEnchanting.price(sharp,1,0),"paid its price: "+(before-ardent(p)));
                before=ardent(p);buy(p,menu,"minecraft:sharpness",2);
                h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(sharp,menu.gear.getItem(0))==1&&ardent(p)==before,"Sharpness II needs "+EnchantRules.fobsFor(2,5)+" ACTIVE FOBs: refused, nothing taken");
                activeFobs(l,2);buy(p,menu,"minecraft:sharpness",2);
                h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(sharp,menu.gear.getItem(0))==2,"with 2 ACTIVE FOBs: Sharpness II");
                buy(p,menu,"minecraft:sharpness",5);h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(sharp,menu.gear.getItem(0))==2,"Sharpness V needs all five");
                activeFobs(l,5);buy(p,menu,"minecraft:sharpness",5);h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(sharp,menu.gear.getItem(0))==5,"with five: Sharpness V");
                before=ardent(p);buy(p,menu,"minecraft:smite",1);
                h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(enchantment("minecraft:smite"),menu.gear.getItem(0))==0&&ardent(p)==before,"Smite conflicts with Sharpness: refused");
                buy(p,menu,"minecraft:protection",1);h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(enchantment("minecraft:protection"),menu.gear.getItem(0))==0,"Protection does not fit a sword");
                // the treasure: Mending needs a seal
                before=ardent(p);buy(p,menu,"minecraft:mending",1);
                h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(enchantment("minecraft:mending"),menu.gear.getItem(0))==0&&ardent(p)==before,"Mending is locked without a seal");
                TacticalEnchanting.button(p,menu,TacticalEnchanting.UNLOCK+EnchantRules.TREASURE.indexOf("minecraft:mending"));
                h.assertTrue(TacticalEnchanting.EnchantData.get(l).seals("minecraft:mending")==0,"no seal carried: nothing unlocked");
                p.getInventory().add(new ItemStack(EternalDefense.WARLORD_SEAL.get()));
                TacticalEnchanting.button(p,menu,TacticalEnchanting.UNLOCK+EnchantRules.TREASURE.indexOf("minecraft:mending"));
                h.assertTrue(TacticalEnchanting.EnchantData.get(l).seals("minecraft:mending")==1&&count(p,EternalDefense.WARLORD_SEAL.get())==0,"a seal unlocks Mending for the team and is used up");
                before=ardent(p);buy(p,menu,"minecraft:mending",1);
                h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(enchantment("minecraft:mending"),menu.gear.getItem(0))==1,"then it is bought like any other");
                h.assertTrue(ardent(p)==before-TacticalEnchanting.price(enchantment("minecraft:mending"),1,0),"at its treasure price");
                // the table's Mk: a discount for everyone
                var price=Economy.enchantingUpgrade(1);
                if(price.ardent())p.getInventory().add(new ItemStack(EternalDefense.ARDENT_ENERGY.get(),price.amount()));else p.getInventory().add(new ItemStack(price.item(),price.amount()));
                TacticalEnchanting.button(p,menu,TacticalEnchanting.UPGRADE);
                h.assertTrue(TacticalEnchanting.EnchantData.get(l).mk==1,"upgraded to Mk I");
                h.assertTrue(TacticalEnchanting.price(sharp,5,1)<TacticalEnchanting.price(sharp,5,0),"cheaper after it");
                // taking the gear out: the menu gives it back when it closes
                p.closeContainer();
                h.assertTrue(p.getInventory().items.stream().anyMatch(s->s.is(Items.DIAMOND_SWORD)&&EnchantmentHelper.getItemEnchantmentLevel(sharp,s)==5),"the sword comes back enchanted when the screen closes");
            }finally{p.closeContainer();p.setGameMode(mode);activeFobs(l,0);clearEnchanting(l);}   // a failed check never leaves the menu open for what runs next
        }).thenSucceed();
    }
    /** A cleared Warlord pays a Warlord Seal into the reward chest; a Patrol does not. */
    @GameTest(template="empty3x3x3",batch="fob_20",timeoutTicks=200)
    public static void aWarlordPaysASeal(GameTestHelper h){
        var hf=HuntGameTests.fixture(h);var l=hf.l();var d=hf.d();var hd=hf.h();
        h.startSequence().thenExecute(()->{
            int seals=HuntGameTests.rewards(d,EternalDefense.WARLORD_SEAL.get());
            var o=HuntGameTests.offer(hf,TacticalRules.Kind.PATROL);h.assertTrue(hd.board.accept(o.id,null)==null,"a Patrol accepted");
            var m=hd.board.mission;m.state=HuntBoard.State.ENGAGED;m.remaining=0;Hunts.complete(l,d,hd);
            h.assertTrue(HuntGameTests.rewards(d,EternalDefense.WARLORD_SEAL.get())==seals,"a Patrol pays no seal");
            var w=HuntGameTests.offer(hf,TacticalRules.Kind.WARLORD);h.assertTrue(hd.board.accept(w.id,null)==null,"a Warlord accepted");
            m=hd.board.mission;m.state=HuntBoard.State.ENGAGED;m.remaining=0;Hunts.complete(l,d,hd);
            h.assertTrue(HuntGameTests.rewards(d,EternalDefense.WARLORD_SEAL.get())==seals+1,"a cleared Warlord pays one Warlord Seal into the reward chest");
        }).thenSucceed();
    }
    // ---- 0.0.28: the owner's list, the parts that need a world (run with the FOB tests) -----------------------------------------------------
    /** Where the Exchange Shop goes in the hunt tests' base zone for the seal sale. */
    static final BlockPos SHOP=HuntGameTests.BEACON.offset(4,0,-4);
    @GameTest(template="empty3x3x3",batch="fob_21",timeoutTicks=200)
    public static void theOwnersListFor0028(GameTestHelper h){
        var hf=HuntGameTests.fixture(h);var l=hf.l();var d=hf.d();var hd=hf.h();var p=HuntGameTests.player(h);var mode=p.gameMode.getGameModeForPlayer();
        h.startSequence().thenExecute(()->{
            // a wood cost reads "Wood (any log)" whether the recipe names dark oak logs or the logs tag, and any log pays it
            var darkOak=UniversalWood.normalize(net.minecraft.world.item.crafting.Ingredient.of(Items.DARK_OAK_LOG));
            h.assertTrue("Wood (any log)".equals(UniversalWood.label(darkOak)),"dark oak logs: "+UniversalWood.label(darkOak));
            h.assertTrue(darkOak.test(new ItemStack(Items.BIRCH_LOG))&&darkOak.test(new ItemStack(Items.CRIMSON_STEM)),"any log pays it");
            h.assertTrue("Wood (any log)".equals(UniversalWood.label(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.LOGS))),"the logs tag");
            h.assertTrue(UniversalWood.label(net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT)).isEmpty(),"no label for iron");
            // a Warlord Seal sells at the Exchange Shop for 4 Ardent Energy
            p.setGameMode(GameType.SURVIVAL);
            try{
                HuntGameTests.goTo(p,l,SHOP.south(2));
                l.setBlock(SHOP,EternalDefense.EXCHANGE_SHOP.get().defaultBlockState(),3);
                p.getInventory().clearContent();p.getInventory().add(new ItemStack(EternalDefense.WARLORD_SEAL.get()));
                var sell=ExchangeShop.offers().sell();int index=-1;for(int i=0;i<sell.size();i++)if(sell.get(i).id().equals("eternal_defense:warlord_seal"))index=i;
                h.assertTrue(index>=0,"the shop buys Warlord Seals");
                int value=sell.get(index).value();h.assertTrue(value==4,"for "+value);
                ExchangeShop.buy(p,SHOP,index,true);
                h.assertTrue(count(p,EternalDefense.WARLORD_SEAL.get())==0&&ardent(p)==value,"sold: the seal gone, "+ardent(p)+" Ardent Energy");
            }finally{p.setGameMode(mode);l.setBlock(SHOP,Blocks.AIR.defaultBlockState(),3);DefenseStructures.remove(l,SHOP);p.getInventory().clearContent();}
            // a cleared Patrol pays twice what the shared formula gives it
            int before=HuntGameTests.rewards(d,EternalDefense.ARDENT_ENERGY.get()),coinsBefore=HuntGameTests.rewards(d,EternalDefense.AMMO_COIN.get());
            var o=HuntGameTests.offer(hf,TacticalRules.Kind.PATROL);h.assertTrue(hd.board.accept(o.id,null)==null,"a Patrol accepted");
            var m=hd.board.mission;m.state=HuntBoard.State.ENGAGED;m.remaining=0;Hunts.complete(l,d,hd);
            var pay=Economy.hunt(TacticalRules.Kind.PATROL,Hunts.tier(l,d));
            h.assertTrue(HuntGameTests.rewards(d,EternalDefense.ARDENT_ENERGY.get())-before==pay.ardent()&&HuntGameTests.rewards(d,EternalDefense.AMMO_COIN.get())-coinsBefore==pay.coins(),"the Patrol paid "+pay);
            h.assertTrue(pay.ardent()%2==0&&pay.coins()%2==0&&pay.ardent()>=2&&pay.coins()>=4,"twice the formula: "+pay);
        }).thenSucceed();
    }
    // ---- 0.0.30: where a FOB counts ------------------------------------------------------------------------------------------------------------
    /**
     * The planting preview answers as the item would (a good spot, then a village already taken), and the outline of a planted FOB is sent to
     * the players near it until its screen turns it off (a player holding a FOB Beacon still sees it, to know the village is taken).
     */
    @GameTest(template="empty3x3x3",batch="fob_22",timeoutTicks=300)
    public static void theZoneOutlineAndThePlantingPreview(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            var look=FobOutline.preview(l,PLANT);
            h.assertTrue(look.getString("problem").isEmpty(),"a good spot: nothing in the way ("+look.getString("problem")+")");
            h.assertTrue(look.getInt("villagers")==3&&look.getInt("beds")==2&&look.getInt("jobs")>=3,"it counts the village's 3 villagers, 2 beds and its workstations: "+look);
            h.assertTrue(look.getLongArray("sections").length>0&&java.util.Arrays.stream(look.getLongArray("sections")).anyMatch(x->x==net.minecraft.core.SectionPos.asLong(net.minecraft.core.SectionPos.blockToSectionCoord(PLANT.getX()),net.minecraft.core.SectionPos.blockToSectionCoord(PLANT.getY()),net.minecraft.core.SectionPos.blockToSectionCoord(PLANT.getZ()))),"and the sections the outline draws, the spot's own among them");
            h.assertTrue(Fobs.plantProblem(l,PLANT)==null,"the item agrees");
            var far=FobOutline.preview(l,AWAY);
            h.assertTrue(far.getString("problem").equals("not_village")&&far.getLongArray("sections").length==0,"64 blocks out: no village, nothing to outline");
            h.assertTrue(FobOutline.state(p,false).getList("zones",10).isEmpty(),"no FOB yet: no outline");
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");
            h.assertTrue(FobOutline.preview(l,PLANT2).getString("problem").equals("taken"),"next to it: the village is taken");
            var zones=FobOutline.state(p,false).getList("zones",10);
            h.assertTrue(zones.size()==1&&zones.getCompound(0).getLong("id")==fob(l).id&&zones.getCompound(0).getLongArray("sections").length>0,"the FOB's outline is sent to a player in its village");
            Fobs.open(p,PLANT);Fobs.button(p,PLANT,Fobs.OUTLINE);
            h.assertTrue(!fob(l).showZone&&FobOutline.state(p,false).getList("zones",10).isEmpty(),"its screen turns the outline off");
            h.assertTrue(FobOutline.state(p,true).getList("zones",10).size()==1,"a player holding a FOB Beacon still sees it");
            Fobs.button(p,PLANT,Fobs.OUTLINE);p.closeContainer();
            h.assertTrue(fob(l).showZone,"and on again");
            var here=p.position();p.setPos(here.x+FobOutline.NEAR+100,here.y,here.z);   // only for this check, within the tick
            boolean gone=FobOutline.state(p,false).getList("zones",10).isEmpty();p.setPos(here.x,here.y,here.z);
            h.assertTrue(gone,"far away: no outline");
            clear(l);
        }).thenSucceed();
    }

    // ---- the Supply Catapult and the specialty seller (0.0.33) -----------------------------------------------------------------------------------
    /** Where the tests put the catapult: inside the village's section, clear of the bell, the composters, the beds and the FOB Beacon. */
    static final BlockPos CATAPULT=VILLAGE.offset(2,0,-6),CATAPULT2=VILLAGE.offset(-3,0,-9);
    static InteractionResult placeCatapult(Fixture f,BlockPos at){
        var stack=new ItemStack(EternalDefense.SUPPLY_CATAPULT_ITEM.get());
        var hit=new BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false);
        return ((BlockItem)stack.getItem()).place(new BlockPlaceContext(f.l(),f.p(),InteractionHand.MAIN_HAND,stack,hit));
    }
    static void clearCatapults(ServerLevel l){for(var at:List.of(CATAPULT,CATAPULT2))if(l.getBlockState(at).is(EternalDefense.SUPPLY_CATAPULT.get()))l.setBlock(at,Blocks.AIR.defaultBlockState(),3);}
    /** Placed only in a FOB's village, one per FOB, a 3 x 3 x 2 structure; the seller opens at research level 5; taken down, it is unlinked. */
    @GameTest(template="empty3x3x3",batch="fob_23",timeoutTicks=300)
    public static void theCatapultStandsOnlyInAFobVillage(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            clearCatapults(l);
            h.assertTrue(SupplyCatapult.placeProblem(l,CATAPULT).equals("not_fob")&&!placeCatapult(f,CATAPULT).consumesAction(),"no FOB yet: refused");
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"eternaldefense fob complete");
            h.assertTrue(SupplyCatapult.placeProblem(l,AWAY).equals("not_fob"),"64 blocks out of the village: refused");
            h.assertTrue(placeCatapult(f,CATAPULT).consumesAction(),"placed in the FOB's village");
            var fob=fob(l);
            h.assertTrue(CATAPULT.equals(fob.catapult),"it serves the FOB");
            h.assertTrue(DefenseStructures.cells(CATAPULT,l.getBlockState(CATAPULT)).stream().allMatch(c->l.getBlockState(c).is(EternalDefense.STRUCTURE_PART.get())),"its 3 x 3 x 2 body stands");
            h.assertTrue(SupplyCatapult.placeProblem(l,CATAPULT2).equals("has_catapult"),"a second one in the same FOB: refused");
            h.assertTrue("research".equals(SupplyCatapult.closed(l,fob)),"research level 0: the seller is closed");
            fob.researchXp=RouteRules.RESEARCH_XP[RouteRules.MAX_LEVEL];
            h.assertTrue(SupplyCatapult.closed(l,fob)==null,"research level 5: open");
            var n=new CompoundTag();Hunts.fobs(l,CampaignData.get(l),p,n);var row=n.getList("fobs",10).getCompound(0);
            h.assertTrue(row.getBoolean("catapult")&&row.getString("seller").isEmpty()&&row.getInt("index")==0,"the Command Table is told: "+row);
            l.setBlock(CATAPULT,Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(fob.catapult==null&&"catapult".equals(SupplyCatapult.closed(l,fob)),"taken down: unlinked, the seller closed again");
            h.assertTrue(!l.getBlockState(CATAPULT.above()).is(EternalDefense.STRUCTURE_PART.get()),"its body went with it");
            clear(l);
        }).thenSucceed();
    }
    /** A purchase: Ardent Energy paid, the catapult turns to the beacon and throws, the box lands beside the beacon as a parcel; refusals. */
    @GameTest(template="empty3x3x3",batch="fob_24",timeoutTicks=1200)
    public static void theSellersBoxIsThrownHomeAndLandsAtTheBeacon(GameTestHelper h){
        var f=village(h,3);var l=f.l();var p=f.p();int tear=RouteRules.POTION_LOOT.indexOf(RouteRules.POTION_LOOT.stream().filter(x->x.id().equals("minecraft:ghast_tear")).findFirst().get());
        BlockPos beacon=CampaignData.get(l).beacon;
        h.startSequence().thenIdle(3).thenExecute(()->occupy(l)).thenIdle(2).thenExecute(()->{
            clearCatapults(l);
            for(var e:l.getEntitiesOfClass(SupportCrate.ParcelEntity.class,new AABB(beacon).inflate(12)))e.discard();
            h.assertTrue(plant(f,PLANT).consumesAction(),"planted");command(l,p,"eternaldefense fob complete");
            h.assertTrue(placeCatapult(f,CATAPULT).consumesAction(),"the catapult placed");
            var fob=fob(l);fob.specialty=RouteRules.Specialty.POTIONS;fob.researchXp=RouteRules.RESEARCH_XP[RouteRules.MAX_LEVEL];
            p.setGameMode(GameType.SURVIVAL);setArdent(p,0);
            h.assertTrue(SupplyCatapult.buy(p,l,SupplyCatapult.sellButton(0,tear,0)).equals("tactical.seller.short")&&fob.shipments.isEmpty(),"no Ardent Energy: refused, nothing queued");
            setArdent(p,10);
            h.assertTrue(SupplyCatapult.buy(p,l,SupplyCatapult.sellButton(0,tear,0)).equals("tactical.seller.bought"),"bought a lot of ghast tears");
            h.assertTrue(count(p,EternalDefense.ARDENT_ENERGY.get())==10-Economy.sellerLotPrice(),"paid "+Economy.sellerLotPrice()+" Ardent Energy");
            h.assertTrue(fob.shipments.size()==1&&fob.shipments.get(0).getString("stage").equals("wait")&&fob.shipments.get(0).getInt("count")==1,"one box waits at the catapult: "+fob.shipments);
        }).thenWaitUntil(()->h.assertTrue(fob(l).shipments.get(0).getString("stage").equals("flying"),"the catapult threw it"))
        .thenExecute(()->{
            var fob=fob(l);var be=(SupplyCatapult.CatapultEntity)l.getBlockEntity(CATAPULT);
            float aim=CatapultRules.yawTo(CATAPULT.getX()+.5,CATAPULT.getZ()+.5,beacon.getX()+.5,beacon.getZ()+.5);
            float diff=((be.target-aim)%360+540)%360-180;
            h.assertTrue(Math.abs(diff)<1&&be.throwsSeen==1,"it turned to face the beacon ("+be.target+" vs "+aim+") and threw once");
            h.assertTrue(!l.getEntitiesOfClass(SupplyCatapult.ThrownSupply.class,new AABB(CATAPULT).inflate(20)).isEmpty(),"the box flies off");
            var ship=fob.shipments.get(0);long due=ship.getLong("due"),spawnAt=ship.getLong("spawnAt"),now=l.getGameTime();
            h.assertTrue(due-now>=CatapultRules.FLIGHT_TICKS-30&&due-now<=CatapultRules.FLIGHT_TICKS,"it lands 12 s after the throw, as a supply drop: "+(due-now));
            h.assertTrue(spawnAt>=0&&spawnAt<=due&&ship.contains("spot"),"its landing spot is planned beside the beacon");
            // the countdown is not waited for here: the parcel appears now and lands a fall later
            ship.putLong("spawnAt",now);ship.putLong("due",now+(due-spawnAt));
        }).thenWaitUntil(()->h.assertTrue(fob(l).shipments.isEmpty()||fob(l).shipments.get(0).getBoolean("spawned"),"the parcel appears"))
        .thenExecute(()->{
            var fob=fob(l);if(fob.shipments.isEmpty())return;
            var ship=fob.shipments.get(0);var spot=BlockPos.of(ship.getLong("spot"));
            var parcels=l.getEntitiesOfClass(SupportCrate.ParcelEntity.class,new AABB(spot).inflate(2,48,2));
            h.assertTrue(parcels.size()==1,"one parcel over its spot: "+parcels.size());
            if(ship.getBoolean("outside"))h.assertTrue(parcels.get(0).getY()>=spot.getY()+SupportRules.PARCEL_MIN_DROP-1,"it appears high above the spot and comes down by parachute: y "+parcels.get(0).getY()+" over "+spot.getY());
        }).thenWaitUntil(()->h.assertTrue(fob(l).shipments.isEmpty(),"the box landed"))
        .thenExecute(()->{
            var m=fob(l).motion;float back=((m.target-m.rest)%360+540)%360-180;
            h.assertTrue(Math.abs(back)<1,"with nothing left to throw the catapult turns back to its rest heading ("+m.target+" vs "+m.rest+")");
        })
        .thenExecute(()->{
            var parcels=l.getEntitiesOfClass(SupportCrate.ParcelEntity.class,new AABB(beacon).inflate(8,48,8));
            h.assertTrue(parcels.size()==1&&parcels.get(0).box.countItem(Items.GHAST_TEAR)==1,"a parcel with the ghast tear beside the beacon: "+parcels.size());
            parcels.forEach(Entity::discard);
            // the catapult holds four boxes
            setArdent(p,64);var fob=fob(l);
            for(int i=0;i<CatapultRules.MAX_QUEUE;i++)h.assertTrue(SupplyCatapult.buy(p,l,SupplyCatapult.sellButton(0,0,1)).equals("tactical.seller.bought"),"box "+(i+1));
            h.assertTrue(SupplyCatapult.buy(p,l,SupplyCatapult.sellButton(0,0,0)).equals("tactical.seller.busy"),"a fifth waits: refused");
            h.assertTrue(fob.shipments.get(0).getInt("count")==CatapultRules.LOTS[1]*CatapultRules.lotSize(RouteRules.POTION_LOOT.get(0)),"four lots of sugar: "+fob.shipments.get(0).getInt("count"));
            fob.researchXp=0;h.assertTrue(SupplyCatapult.buy(p,l,SupplyCatapult.sellButton(0,0,0)).equals("tactical.seller.refused.research"),"below level 5: refused");
            fob.shipments.clear();p.setGameMode(GameType.CREATIVE);clearCatapults(l);clear(l);
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
