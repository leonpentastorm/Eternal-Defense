package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;
import java.util.stream.Stream;

/**
 * Tactical Operations on a real server level (0.0.20; opt-in: start the game with {@code -Darsenal.huntTests=true}, then {@code /hunt-test},
 * or let tools/qa QaHunt start them). THROWAWAY WORLDS ONLY: the tests take over the world's campaign (a beacon on a stone platform at
 * {@link #BEACON}) and its Tactical Operations data, and they move the first online player around, because a warband only comes when a real
 * player approaches. Two stone platforms are built high above any terrain (y {@value #FLOOR} to {@value #TOP}) and kept loaded: one for the
 * base (beacon, Command Table, Satellite Beacon) and one for the objective, {@value #OBJECTIVE_DISTANCE} blocks east.
 */
@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class HuntGameTests {
    static final int FLOOR=275,TOP=290,OBJECTIVE_DISTANCE=224;
    static final BlockPos BEACON=new BlockPos(20008,TOP+1,8),OBJECTIVE=BEACON.offset(OBJECTIVE_DISTANCE,0,0);
    static final BlockPos TABLE=BEACON.offset(3,0,0),SATELLITE=BEACON.offset(6,0,0);
    private static boolean built;

    // ---- the ground ------------------------------------------------------------------------------------------------------------------------
    /**
     * Builds (once) both platforms and keeps their chunks loaded. The layers in whole sections below the top one are written straight into the
     * chunk (fast); the top section's layers are placed like any block, so the light and the height maps above the platform are right (the
     * satellite's sky check and the screenshots need them).
     */
    static void ground(ServerLevel l){
        if(built)return;
        platform(l,BEACON,2);platform(l,OBJECTIVE,3);built=true;
    }
    private static void platform(ServerLevel l,BlockPos center,int chunks){
        var stone=Blocks.STONE.defaultBlockState();
        for(int cx=(center.getX()>>4)-chunks;cx<=(center.getX()>>4)+chunks;cx++)for(int cz=(center.getZ()>>4)-chunks;cz<=(center.getZ()>>4)+chunks;cz++){
            l.setChunkForced(cx,cz,true);LevelChunk chunk=l.getChunk(cx,cz);
            // whole sections below the top one are written directly; the top one's blocks are placed (the first placement into an empty section
            // tells the light engine about it, so the light over the platform is right)
            int placedFrom=Math.max(FLOOR,TOP&~15);
            for(int y=FLOOR;y<placedFrom;y++){var section=chunk.getSection(chunk.getSectionIndex(y));for(int x=0;x<16;x++)for(int z=0;z<16;z++)section.setBlockState(x,y&15,z,stone,false);}
            chunk.setUnsaved(true);
            for(int y=placedFrom;y<=TOP;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++)l.setBlock(new BlockPos(cx*16+x,y,cz*16+z),stone,2|16);
        }
    }
    static void release(ServerLevel l){
        if(!built)return;
        for(var c:List.of(new int[]{BEACON.getX()>>4,BEACON.getZ()>>4,2},new int[]{OBJECTIVE.getX()>>4,OBJECTIVE.getZ()>>4,3}))
            for(int cx=c[0]-c[2];cx<=c[0]+c[2];cx++)for(int cz=c[1]-c[2];cz<=c[1]+c[2];cz++)l.setChunkForced(cx,cz,false);
        built=false;
    }

    // ---- the base ------------------------------------------------------------------------------------------------------------------------
    record Fixture(ServerLevel l,CampaignData d,HuntData h){}
    /** The campaign at the test beacon (preparation, reward tier 3, no raid due for days), a linked Mk I satellite and an empty board. */
    static Fixture fixture(GameTestHelper helper){
        var l=helper.getLevel();ground(l);
        var d=CampaignData.get(l);
        if(!d.installed()||!BEACON.equals(d.beacon)){d.beacon=BEACON;d.campaignSerial++;d.health=d.maximumHealth();}
        d.phase="preparation";d.rewardTier=3;d.preparationTicks=0;d.respiteTicks=0;d.introCompleted=true;d.setDirty();
        if(!l.getBlockState(BEACON).is(ArsenalBeacon.BEACON.get()))l.setBlock(BEACON,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        var h=HuntData.get(l);
        Hunts.reset(l);
        h.satelliteMk=1;
        if(!l.getBlockState(TABLE).is(ArsenalBeacon.COMMAND_TABLE.get()))l.setBlock(TABLE,ArsenalBeacon.COMMAND_TABLE.get().defaultBlockState().setValue(TacticalBlocks.FACING,Direction.NORTH),3);
        if(!l.getBlockState(SATELLITE).is(ArsenalBeacon.SATELLITE_BEACON.get()))l.setBlock(SATELLITE,ArsenalBeacon.SATELLITE_BEACON.get().defaultBlockState(),3);
        h.table=TABLE;h.satellite=SATELLITE;
        // the board is the test's own: filled (so the uplink adds nothing), today's offer already given (no dawn offer during a test)
        h.board.filledTo(TacticalRules.offers(1));h.board.lastDay=TacticalRules.day(l.getDayTime());h.scanReady=0;h.setDirty();
        return new Fixture(l,d,h);
    }
    /** One offer at the objective platform (always reachable ground). */
    static HuntBoard.Offer offer(Fixture f,TacticalRules.Kind kind){
        long id=f.h().board.nextId++;
        var o=new HuntBoard.Offer(id,kind,TacticalRules.codename(id*7919),OBJECTIVE.getX(),OBJECTIVE.getZ(),OBJECTIVE_DISTANCE,90);
        f.h().board.offers.add(o);f.h().setDirty();return o;
    }
    static int index(Fixture f,HuntBoard.Offer o){return f.h().board.offers.indexOf(o);}
    /** The first online player (a warband only comes for a real player). */
    static ServerPlayer player(GameTestHelper h){
        var players=h.getLevel().getServer().getPlayerList().getPlayers();
        if(players.isEmpty())throw new GameTestAssertException("needs an online player: run the hunt tests from the QA client or with a player on the server");
        return players.get(0);
    }
    static void goTo(ServerPlayer p,ServerLevel l,BlockPos at){p.teleportTo(l,at.getX()+.5,at.getY(),at.getZ()+.5,p.getYRot(),p.getXRot());}
    static List<Mob> mobs(ServerLevel l,long missionId){
        var out=new ArrayList<Mob>();
        for(var e:l.getAllEntities())if(e instanceof Mob m&&m.isAlive()&&HuntWarband.hunter(m)&&m.getPersistentData().getLong(HuntWarband.TAG)==missionId)out.add(m);
        return out;
    }
    static int rewards(CampaignData d,net.minecraft.world.item.Item item){int n=0;for(var t:d.rewards){var s=ItemStack.of(t);if(s.is(item))n+=s.getCount();}return n;}
    static int carried(ServerPlayer p,net.minecraft.world.item.Item item){int n=0;for(var s:p.getInventory().items)if(s.is(item))n+=s.getCount();return n;}
    static void dropMissionItems(ServerPlayer p){
        for(int i=0;i<p.getInventory().items.size();i++){var s=p.getInventory().items.get(i);if(s.is(Items.FILLED_MAP)||s.is(ArsenalBeacon.RETURN_FLARE.get()))p.getInventory().items.set(i,ItemStack.EMPTY);}
    }

    // ---- the warband ---------------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="hunt_01",timeoutTicks=1800)
    public static void aWarbandSpawnsOnApproachAndItsLastDeathPaysOnce(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();var d=f.d();var h=f.h();var p=player(helper);
        var o=offer(f,TacticalRules.Kind.PATROL);
        helper.assertTrue(h.board.accept(o.id,null)==null,"the offer is accepted");
        var pay=Economy.hunt(o.kind,d.rewardTier);
        int ardent=rewards(d,ArsenalBeacon.ARDENT_ENERGY.get()),coins=rewards(d,ArsenalBeacon.AMMO_COIN.get());
        goTo(p,l,BEACON.above());
        helper.startSequence()
            .thenIdle(60)
            .thenExecute(()->helper.assertTrue(h.board.mission.state==HuntBoard.State.ACTIVE&&mobs(l,o.id).isEmpty(),"nothing spawns while nobody is near (the player is "+OBJECTIVE_DISTANCE+" blocks away)"))
            .thenExecute(()->goTo(p,l,OBJECTIVE.above()))
            .thenWaitUntil(()->{var m=h.board.mission;helper.assertTrue(m!=null&&m.engaged()&&m.pending()==0,"the warband is out");})
            .thenExecute(()->{
                var m=h.board.mission;var band=mobs(l,o.id);
                helper.assertTrue(band.size()==m.total,"every mob of the warband is in the world: "+band.size()+" of "+m.total);
                int online=l.getServer().getPlayerList().getPlayerCount();
                helper.assertTrue(m.total>=TacticalRules.warband(o.kind,0,online)&&m.total<=TacticalRules.warband(o.kind,o.kind.max-o.kind.min,online),"a Patrol's size: "+m.total);
                var leash=new BlockPos(o.x,0,o.z);
                for(var mob:band){
                    var n=mob.getPersistentData();
                    helper.assertTrue(n.getLong(HuntWarband.SERIAL)==m.serial,"tagged with the warband's serial");
                    helper.assertTrue(!n.getBoolean("arsenalRaider")&&!d.raiders.contains(mob.getUUID()),"never a raider");
                    helper.assertTrue(mob.isPersistenceRequired(),"never despawns");
                    helper.assertTrue(mob.hasRestriction()&&mob.getRestrictRadius()==TacticalRules.LEASH&&mob.getRestrictCenter().getX()==leash.getX()&&mob.getRestrictCenter().getZ()==leash.getZ(),"leashed to the objective");
                    helper.assertTrue(Math.hypot(mob.getX()-o.x,mob.getZ()-o.z)<=TacticalRules.SPAWN_RADIUS+3,"spawned near the objective");
                    helper.assertFalse(BeaconCombat.isAttached(mob),"never sent at the beacon");
                }
                for(var mob:band)mob.kill();
            })
            .thenWaitUntil(()->helper.assertTrue(h.board.mission==null,"cleared and settled"))
            .thenExecute(()->{
                helper.assertTrue(rewards(d,ArsenalBeacon.ARDENT_ENERGY.get())==ardent+pay.ardent()&&rewards(d,ArsenalBeacon.AMMO_COIN.get())==coins+pay.coins(),
                    "the reward chest got "+pay.ardent()+" Ardent Energy and "+pay.coins()+" coins");
                Hunts.complete(l,d,h);
                helper.assertTrue(rewards(d,ArsenalBeacon.ARDENT_ENERGY.get())==ardent+pay.ardent(),"a second completion pays nothing");
                helper.assertTrue(h.board.offer(o.id)==null,"the cleared offer left the board");
                goTo(p,l,BEACON.above());
            })
            .thenSucceed();
    }
    @GameTest(template="empty3x3x3",batch="hunt_02",timeoutTicks=1800)
    public static void abandoningSendsTheWarbandAwayAndNeverGivesASecondFlare(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();var d=f.d();var h=f.h();var p=player(helper);
        var o=offer(f,TacticalRules.Kind.GARRISON);dropMissionItems(p);
        helper.assertTrue(Hunts.accept(p,l,d,h,index(f,o)).equals("tactical.result.accepted"),"accepted at the table");
        helper.assertTrue(Hunts.carriesMap(p,o.id),"the player got the mission map");
        helper.assertTrue(carried(p,ArsenalBeacon.RETURN_FLARE.get())==1,"and one Return Flare");
        var map=p.getInventory().items.stream().filter(s->s.is(Items.FILLED_MAP)).findFirst().orElseThrow();
        helper.assertTrue(map.getHoverName().getString().equals(o.codename),"the map is named after the operation");
        // a player who lost the map gets it back at login, but no second flare
        dropMissionItems(p);p.getInventory().add(new ItemStack(ArsenalBeacon.RETURN_FLARE.get()));Hunts.login(p);
        helper.assertTrue(Hunts.carriesMap(p,o.id)&&carried(p,ArsenalBeacon.RETURN_FLARE.get())==1,"login hands the map back, not another flare");
        goTo(p,l,OBJECTIVE.above());
        helper.startSequence()
            .thenWaitUntil(()->{var m=h.board.mission;helper.assertTrue(m!=null&&m.engaged()&&m.pending()==0,"the warband is out");})
            .thenExecute(()->{
                helper.assertTrue(!mobs(l,o.id).isEmpty(),"mobs before abandoning");
                helper.assertTrue(Hunts.abandon(p,l,d,h).equals("tactical.result.abandoned"),"abandoned");
                helper.assertTrue(mobs(l,o.id).isEmpty(),"the warband is gone");
                helper.assertTrue(h.board.mission==null&&h.board.offer(o.id)!=null,"the offer is back on the board");
                int flares=carried(p,ArsenalBeacon.RETURN_FLARE.get());
                helper.assertTrue(Hunts.accept(p,l,d,h,index(f,o)).equals("tactical.result.accepted"),"the same offer accepted again");
                helper.assertTrue(carried(p,ArsenalBeacon.RETURN_FLARE.get())==flares,"no second Return Flare for the same offer");
                Hunts.abandon(p,l,d,h);goTo(p,l,BEACON.above());
            })
            .thenSucceed();
    }
    @GameTest(template="empty3x3x3",batch="hunt_03",timeoutTicks=200)
    public static void aStaleHuntMobIsDiscardedWhenItsChunkLoads(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();var h=f.h();
        var o=offer(f,TacticalRules.Kind.PATROL);h.board.accept(o.id,null);h.board.engage(3);h.board.scatter();h.board.engage(3);
        helper.assertTrue(h.board.mission.serial==2,"the second warband");
        Mob stale=tagged(l,o.id,1,BEACON.offset(-5,0,5)),current=tagged(l,o.id,2,BEACON.offset(-5,0,-5)),orphan=tagged(l,987654,2,BEACON.offset(5,0,-5));
        // what a chunk does when it loads its saved entities
        l.addLegacyChunkEntities(Stream.of(stale,current,orphan));
        helper.startSequence().thenIdle(2).thenExecute(()->{
            helper.assertTrue(stale.isRemoved()&&l.getEntity(stale.getUUID())==null,"a mob of a scattered warband is discarded");
            helper.assertTrue(orphan.isRemoved(),"a mob of a mission that no longer runs is discarded");
            helper.assertTrue(!current.isRemoved()&&l.getEntity(current.getUUID())!=null,"a mob of the running warband stays");
            current.discard();Hunts.reset(l);
        }).thenSucceed();
    }
    static Mob tagged(ServerLevel l,long mission,long serial,BlockPos at){
        var mob=EntityType.ZOMBIE.create(l);mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);mob.setPersistenceRequired();
        mob.getPersistentData().putLong(HuntWarband.TAG,mission);mob.getPersistentData().putLong(HuntWarband.SERIAL,serial);return mob;
    }
    @GameTest(template="empty3x3x3",batch="hunt_04",timeoutTicks=600)
    public static void theTableRefusesWithoutUplinkDuringARaidAndInTheWarningWindow(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();var d=f.d();var h=f.h();var p=player(helper);goTo(p,l,BEACON.above());
        var o=offer(f,TacticalRules.Kind.PATROL);
        helper.assertTrue(Hunts.blocked(l,d,h)==null,"linked, no raid due: missions can be accepted");
        var far=BEACON.offset(-7,0,-7);   // inside the zone, more than 8 blocks from the table
        l.setBlock(SATELLITE,Blocks.AIR.defaultBlockState(),3);l.setBlock(far,ArsenalBeacon.SATELLITE_BEACON.get().defaultBlockState(),3);h.satellite=far;
        helper.assertTrue("too_far".equals(Hunts.uplinkProblem(l,d,h)),"satellite too far: "+Hunts.uplinkProblem(l,d,h));
        helper.assertTrue(Hunts.accept(p,l,d,h,index(f,o)).equals("tactical.result.no_uplink")&&h.board.mission==null,"NO UPLINK refuses");
        l.setBlock(far,Blocks.AIR.defaultBlockState(),3);l.setBlock(SATELLITE,ArsenalBeacon.SATELLITE_BEACON.get().defaultBlockState(),3);h.satellite=SATELLITE;
        var roof=SATELLITE.above(3);l.setBlock(roof,Blocks.STONE.defaultBlockState(),3);
        helper.startSequence()
            .thenWaitUntil(()->helper.assertTrue("no_sky".equals(Hunts.uplinkProblem(l,d,h)),"a roof over the dish breaks the uplink (problem "+Hunts.uplinkProblem(l,d,h)+", sky light at the dish "+l.getBrightness(net.minecraft.world.level.LightLayer.SKY,SATELLITE.above(2))+", roof "+l.getBlockState(roof)+")"))
            .thenExecute(()->{helper.assertTrue("no_uplink".equals(Hunts.blocked(l,d,h)),"no sky, no missions");l.setBlock(roof,Blocks.AIR.defaultBlockState(),3);})
            .thenWaitUntil(()->helper.assertTrue(Hunts.uplinkProblem(l,d,h)==null,"open sky again"))
            .thenExecute(()->{
                d.phase="raid";
                String raid=Hunts.accept(p,l,d,h,index(f,o));
                d.phase="preparation";
                helper.assertTrue(raid.equals("tactical.result.raid")&&h.board.mission==null,"RAID IN PROGRESS refuses: "+raid);
                d.preparationTicks=Rules.intervalDays(d.rewardTier)*24000L-TacticalRules.WARNING_TICKS;
                String warning=Hunts.accept(p,l,d,h,index(f,o));
                d.preparationTicks=0;
                helper.assertTrue(warning.equals("tactical.result.raid_warning")&&h.board.mission==null,"RAID WARNING refuses: "+warning);
                dropMissionItems(p);
                helper.assertTrue(Hunts.accept(p,l,d,h,index(f,o)).equals("tactical.result.accepted"),"accepted once all is clear");
                var second=offer(f,TacticalRules.Kind.PATROL);
                helper.assertTrue(Hunts.accept(p,l,d,h,index(f,second)).equals("tactical.result.busy"),"one mission at a time");
                // a running mission is never cancelled by a raid
                d.phase="raid";boolean kept=h.board.running();d.phase="preparation";
                helper.assertTrue(kept,"a raid does not end the running mission");
                Hunts.abandon(p,l,d,h);dropMissionItems(p);
            })
            .thenSucceed();
    }
    @GameTest(template="empty3x3x3",batch="hunt_05",timeoutTicks=100)
    public static void theBlocksOnlyGoInsideTheZoneAndOneOfEachPerBase(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();var h=f.h();
        var table=new ItemStack(ArsenalBeacon.COMMAND_TABLE_ITEM.get());var satellite=new ItemStack(ArsenalBeacon.SATELLITE_BEACON_ITEM.get());
        var outside=BEACON.offset(20,0,0);
        helper.assertTrue(place(l,table,outside)==InteractionResult.FAIL&&l.getBlockState(outside).isAir(),"no Command Table outside the zone");
        helper.assertTrue(place(l,satellite,outside)==InteractionResult.FAIL&&l.getBlockState(outside).isAir(),"no Satellite Beacon outside the zone");
        var inside=BEACON.offset(-4,0,-4);
        helper.assertTrue(place(l,table,inside)==InteractionResult.FAIL&&l.getBlockState(inside).isAir(),"a second Command Table is refused");
        helper.assertTrue(place(l,satellite,inside)==InteractionResult.FAIL&&l.getBlockState(inside).isAir(),"a second Satellite Beacon is refused");
        // with the base's table gone, a new one goes in, with its 2 x 2 x 2 frame
        l.setBlock(TABLE,Blocks.AIR.defaultBlockState(),3);
        helper.assertTrue(h.table==null,"the data forgets a removed table");
        helper.assertTrue(place(l,table,inside).consumesAction()&&l.getBlockState(inside).is(ArsenalBeacon.COMMAND_TABLE.get()),"a Command Table goes inside the zone");
        helper.assertTrue(inside.equals(h.table),"the base's table is the new one");
        var facing=l.getBlockState(inside).getValue(TacticalBlocks.FACING);var side=facing.getClockWise();var back=facing.getOpposite();
        for(var cell:List.of(inside.relative(side),inside.relative(back),inside.relative(side).relative(back),inside.above(),inside.relative(side).above(),inside.relative(back).above(),inside.relative(side).relative(back).above()))
            helper.assertTrue(l.getBlockState(cell).is(ArsenalBeacon.STRUCTURE_PART.get()),"the table fills its 2 x 2 x 2 footprint at "+cell);
        l.setBlock(inside,Blocks.AIR.defaultBlockState(),3);
        helper.assertTrue(l.getBlockState(inside.relative(side).above()).isAir(),"removing the table removes its frame");
        helper.succeed();
    }
    static InteractionResult place(ServerLevel l,ItemStack stack,BlockPos at){
        var hit=new BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false);
        return ((BlockItem)stack.getItem()).place(new BlockPlaceContext(l,null,InteractionHand.MAIN_HAND,stack.copy(),hit));
    }
    @GameTest(template="empty3x3x3",batch="hunt_06",timeoutTicks=400)
    public static void huntMobsDoNotBurnNeverAttackTheBeaconAndDropNoArdent(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();
        l.setDayTime(6000);
        Mob hunter=tagged(l,424242,1,BEACON.offset(-6,0,6));Mob control=EntityType.ZOMBIE.create(l);control.moveTo(BEACON.getX()-5.5,BEACON.getY(),BEACON.getZ()-5.5,0,0);
        l.addFreshEntity(hunter);l.addFreshEntity(control);
        helper.startSequence().thenIdle(80).thenExecute(()->{
            helper.assertTrue(control.isOnFire(),"the sun burns an ordinary zombie (the check works)");
            helper.assertFalse(hunter.isOnFire(),"a hunt zombie does not burn in daylight");
            helper.assertTrue(BeaconCombat.isAttached(control),"an ordinary hostile near the beacon gets the beacon goals");
            helper.assertFalse(BeaconCombat.isAttached(hunter),"a hunt mob never does");
            // a sure drop path (boss + raider credit), with and without the hunt tag
            for(var mob:List.of(hunter,control)){mob.getPersistentData().putBoolean("arsenalRaider",true);mob.getPersistentData().putBoolean("arsenalBoss",true);}
            var hunterDrops=new ArrayList<ItemEntity>();var controlDrops=new ArrayList<ItemEntity>();
            new ArdentEnergy().drops(new net.minecraftforge.event.entity.living.LivingDropsEvent(hunter,l.damageSources().generic(),hunterDrops,0,true));
            new ArdentEnergy().drops(new net.minecraftforge.event.entity.living.LivingDropsEvent(control,l.damageSources().generic(),controlDrops,0,true));
            helper.assertTrue(controlDrops.stream().anyMatch(e->e.getItem().is(ArsenalBeacon.ARDENT_ENERGY.get())),"the same mob without the tag drops Ardent Energy (the check works)");
            helper.assertTrue(hunterDrops.isEmpty(),"a hunt mob drops no Ardent Energy");
            hunter.discard();control.discard();
        }).thenSucceed();
    }
    @GameTest(template="empty3x3x3",batch="hunt_07",timeoutTicks=1800)
    public static void aWarbandLeftAloneScatters(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();var h=f.h();var p=player(helper);
        var o=offer(f,TacticalRules.Kind.PATROL);h.board.accept(o.id,null);goTo(p,l,OBJECTIVE.above());
        helper.startSequence()
            .thenWaitUntil(()->{var m=h.board.mission;helper.assertTrue(m!=null&&m.engaged()&&m.pending()==0,"the warband is out");})
            .thenExecute(()->{goTo(p,l,BEACON.above());h.board.mission.awayTicks=TacticalRules.AWAY_TICKS-60;})
            .thenWaitUntil(()->helper.assertTrue(h.board.mission.state==HuntBoard.State.ACTIVE,"scattered after "+TacticalRules.AWAY_TICKS+" ticks with nobody within "+TacticalRules.AWAY+" blocks"))
            .thenExecute(()->{
                helper.assertTrue(h.board.mission.serial==2,"a new serial: the old warband is stale");
                helper.assertTrue(mobs(l,o.id).isEmpty(),"its mobs are gone");
                helper.assertTrue(h.board.offer(o.id)!=null,"the mission waits for the team");
                Hunts.reset(l);
            })
            .thenSucceed();
    }
    @GameTest(template="empty3x3x3",batch="hunt_08",timeoutTicks=900)
    public static void theScanReadsOnlyLoadedChunksAndKeepsOneJobAtATime(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();var d=f.d();var h=f.h();var p=player(helper);goTo(p,l,BEACON.above());
        h.satelliteMk=3;   // the Region image reaches 1024 blocks: its outer rows lie in chunks nobody has loaded
        int version=h.scanVersion,loaded=l.getChunkSource().getLoadedChunksCount();
        // chunks under the image that are not loaded now must still not be loaded after the scan (the image's outer ring, 700 to 1000 blocks out)
        var far=new ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int a=0;a<360;a+=15)for(int r:new int[]{700,1000}){var c=new net.minecraft.world.level.ChunkPos(BlockPos.containing(BEACON.getX()+Math.cos(Math.toRadians(a))*r,0,BEACON.getZ()+Math.sin(Math.toRadians(a))*r));if(l.getChunkSource().getChunkNow(c.x,c.z)==null)far.add(c);}
        helper.assertTrue(far.size()>10,"enough unloaded chunks under the image to watch: "+far.size());
        helper.assertTrue(TacticalScan.start(p,l,d,h).equals("tactical.result.scan_started"),"the scan starts");
        helper.assertTrue(TacticalScan.start(p,l,d,h).equals("tactical.result.scan_busy"),"one scan at a time");
        helper.startSequence()
            .thenWaitUntil(()->helper.assertFalse(TacticalScan.running(),"the scan finishes"))
            .thenExecute(()->{
                int after=l.getChunkSource().getLoadedChunksCount();long nowLoaded=far.stream().filter(c->l.getChunkSource().getChunkNow(c.x,c.z)!=null).count();
                com.mojang.logging.LogUtils.getLogger().info("[hunts] scan test: {} watched chunks under the image, {} of them loaded after the scan; {} chunks loaded in all before, {} after (the player's own view loads some)",far.size(),nowLoaded,loaded,after);
                helper.assertTrue(nowLoaded==0,"the scan loaded none of the "+far.size()+" unloaded chunks under the image ("+nowLoaded+" loaded)");
                helper.assertTrue(h.scanVersion==version+1&&h.scannedAt>=0,"a new version");
                int half=TacticalRules.SCAN_SIZE/2;
                helper.assertTrue(h.base.fresh[half*TacticalRules.SCAN_SIZE+half]==ScanSampler.LIVE,"the beacon's own pixel is live");
                long live=0,other=0;for(byte b:h.region.fresh)if(b==ScanSampler.LIVE)live++;else other++;
                helper.assertTrue(live>0&&other>0,"the Region image mixes live pixels and older or biome-only ones ("+live+" live)");
                helper.assertTrue(TacticalScan.start(p,l,d,h).equals("tactical.result.scan_cooldown"),"then a cooldown");
                h.satelliteMk=1;
            })
            .thenSucceed();
    }
    @GameTest(template="empty3x3x3",batch="hunt_09",timeoutTicks=200)
    public static void aGarrisonLookupNeverLoadsAChunk(GameTestHelper helper){
        var f=fixture(helper);var l=f.l();
        int loaded=l.getChunkSource().getLoadedChunksCount();long started=System.nanoTime();
        var spot=Hunts.garrison(l,BEACON.getX(),BEACON.getZ(),8,TacticalRules.range(3),new ArrayList<>(),new Random(3));
        long ms=(System.nanoTime()-started)/1_000_000;int after=l.getChunkSource().getLoadedChunksCount();
        com.mojang.logging.LogUtils.getLogger().info("[hunts] garrison test: {} ms, {}, {} chunks loaded before, {} after",ms,spot==null?"no structure":spot,loaded,after);
        helper.assertTrue(after<=loaded,"the lookup loaded no chunk ("+loaded+" before, "+after+" after)");
        if(spot!=null)helper.assertTrue(spot.distance()>=TacticalRules.minDistance(8)&&spot.distance()<=TacticalRules.range(3),"inside the window");
        helper.succeed();
    }

    // ---- the runner ----------------------------------------------------------------------------------------------------------------------
    public static final class Runner {
        private static Collection<GameTestInfo> infos=List.of();private static MultipleTestTracker tracker;private static ServerLevel level;
        /** Result of the last finished run (for the QA harness). */
        public static volatile int lastPass=-1,lastFail=-1;
        @SubscribeEvent public void register(net.minecraftforge.event.RegisterCommandsEvent e){
            ensureRegistered();
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("hunt-test").requires(s->s.hasPermission(2)).executes(c->{start(c.getSource().getLevel());return 1;}));
        }
        public static void ensureRegistered(){
            if(GameTestRegistry.getAllTestFunctions().stream().noneMatch(t->t.getBatchName().startsWith("hunt_")))GameTestRegistry.register(HuntGameTests.class);
        }
        public static void start(ServerLevel target){
            if(tracker!=null)throw new IllegalStateException("A hunt test run is already active");
            level=target;ensureRegistered();var names=new HashSet<String>();
            var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getBatchName().startsWith("hunt_")&&names.add(t.getTestName())).toList();
            infos=GameTestRunner.runTests(tests,new BlockPos(0,200,0),Rotation.NONE,target,GameTestTicker.SINGLETON,1);tracker=new MultipleTestTracker(infos);
        }
        @SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
            if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;
            if(!net.minecraftforge.gametest.ForgeGameTestHooks.isGametestEnabled())GameTestTicker.SINGLETON.tick();
            if(!tracker.isDone())return;
            var log=com.mojang.logging.LogUtils.getLogger();int pass=0,fail=0;
            for(var info:infos){if(info.hasFailed()){fail++;log.error("HUNT_TEST_FAIL {}: {}",info.getTestName(),info.getError()==null?"?":info.getError().getMessage());}else{pass++;log.info("HUNT_TEST_PASS {}",info.getTestName());}}
            log.info("HUNT_TESTS_DONE pass={} fail={}",pass,fail);
            lastPass=pass;lastFail=fail;tracker=null;
        }
        public static boolean running(){return tracker!=null;}
        /** Lets the platforms' chunks go (the QA harness calls it when it has taken its pictures). */
        public static void release(){if(level!=null)HuntGameTests.release(level);}
    }
}
