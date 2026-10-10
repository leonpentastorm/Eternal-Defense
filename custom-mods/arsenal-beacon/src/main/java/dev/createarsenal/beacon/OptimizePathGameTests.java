package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

/**
 * The Optimize Path (project 0.0.15) on a real server (opt-in: {@code -Darsenal.optimizeTests=true}; {@link Runner} starts them). Each faster
 * path is run next to a verbatim copy of the code it replaced, on the same input, and must give the same answer; the times of both are logged
 * as OPT_BENCH lines. Nothing here touches a real campaign except {@link #aGunHitOnTheBeaconStillCounts}, which needs a world with no beacon
 * and puts it back the way it found it.
 */
@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class OptimizePathGameTests {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();

    // ---- base score -------------------------------------------------------------------------------------------------------------------
    /** The faster scoring gives exactly the old score on many different bases: every zone size, every height, a beacon at negative coordinates. */
    @GameTest(template="empty3x3x3",batch="optimize_1",timeoutTicks=200)
    public static void theBaseScoreIsExactlyTheSame(GameTestHelper h){
        int compared=0;
        for(long seed=1;seed<=6;seed++)for(int core=0;core<=3;core++){
            var d=campaign(core,(int)(seed%5),seed%2==0?new BlockPos(-37,70,-1205):new BlockPos(410,-12,96));
            var placed=new HashSet<Long>();var blocks=base(d,seed*31+core,4+(int)seed*2,30+(int)seed*25,placed);
            for(var chosen:List.<Set<Long>>of(placed,Set.of(),blocks.keySet())){
                var before=legacyAnalyze(d,blocks,chosen);var after=BaseScoring.analyze(d,blocks,chosen);
                h.assertTrue(before.equals(after),"seed "+seed+" core "+core+": old "+before+" new "+after);compared++;
            }
        }
        // a base big enough to reach the floor cap (72 floors at core 0): the break at the cap must stop on the same floor
        var d=campaign(0,4,new BlockPos(5,64,5));var placed=new HashSet<Long>();var blocks=base(d,99,3,20,placed);hall(d,blocks,placed);
        var before=legacyAnalyze(d,blocks,placed);int floors=legacyFloors;var after=BaseScoring.analyze(d,blocks,placed);
        h.assertTrue(before.equals(after),"floor cap: old "+before+" new "+after);
        h.assertTrue(floors>=72,"the cap case really stops at the cap of 72 floors: "+floors);
        LOG.info("OPT_CHECK base score identical on {} bases (6 seeds x 4 zone sizes x 3 placed sets, plus a floor-cap base)",compared+1);
        h.succeed();
    }
    /** Time of both versions on the biggest zone (Mk-4 core, full height): what a survey costs every 20 seconds between raids and once at raid start. */
    @GameTest(template="empty3x3x3",batch="optimize_2",timeoutTicks=400)
    public static void theBaseScoreIsFaster(GameTestHelper h){
        var d=campaign(3,4,new BlockPos(-300,64,800));var placed=new HashSet<Long>();var blocks=base(d,7,30,300,placed);
        long[] old=new long[7],now=new long[7];BaseScoring.Score a=null,b=null;
        for(int i=0;i<10;i++){
            long t0=System.nanoTime();a=legacyAnalyze(d,blocks,placed);long t1=System.nanoTime();b=BaseScoring.analyze(d,blocks,placed);long t2=System.nanoTime();
            if(i>=3){old[i-3]=t1-t0;now[i-3]=t2-t1;}
        }
        Arrays.sort(old);Arrays.sort(now);
        h.assertTrue(a.equals(b),"same score: "+a+" / "+b);
        LOG.info("OPT_BENCH base_score blocks={} score={} legacy_median_ms={} new_median_ms={} speedup={}x",blocks.size(),a.total(),
            String.format(Locale.ROOT,"%.2f",old[3]/1e6),String.format(Locale.ROOT,"%.2f",now[3]/1e6),String.format(Locale.ROOT,"%.1f",old[3]/(double)Math.max(1,now[3])));
        h.assertTrue(now[3]<=old[3],"the new scoring is not slower: "+now[3]+" ns against "+old[3]+" ns");
        h.succeed();
    }

    // ---- TaCZ block impacts -------------------------------------------------------------------------------------------------------------
    /** A TaCZ bullet from a hostile mob that hits the beacon block still reaches the beacon through the typed listener (and damages it). */
    @GameTest(template="empty3x3x3",batch="optimize_3",timeoutTicks=100)
    public static void aGunHitOnTheBeaconStillCounts(GameTestHelper h){
        var l=h.getLevel();var d=CampaignData.get(l);
        h.assertTrue(d.phase.equals("unplaced"),"needs a world without a beacon (the test borrows the campaign), found "+d.phase);
        h.assertTrue(!l.players().isEmpty(),"the beacon only takes hits while a player is online");
        var pos=h.absolutePos(new BlockPos(1,1,1));net.minecraft.world.entity.Mob husk=null;
        try{
            l.setBlock(pos,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
            d.beacon=pos.immutable();d.phase="preparation";d.health=d.maximumHealth();int full=d.health;
            BeaconCombat.tick(l,d);h.assertTrue(BeaconCombat.objective(l)!=null,"the beacon's objective stands");
            husk=EntityType.HUSK.create(l);husk.moveTo(pos.getX()+3.5,pos.getY(),pos.getZ()+.5,90,0);husk.setNoAi(true);husk.setNoGravity(true);l.addFreshEntity(husk);
            var bullet=new com.tacz.guns.entity.EntityKineticBullet(com.tacz.guns.init.ModEntities.BULLET.get(),l);bullet.setOwner(husk);bullet.moveTo(pos.getX()+2.5,pos.getY()+.5,pos.getZ()+.5);
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.EAST,pos,false);
            MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.server.AmmoHitBlockEvent(l,hit,l.getBlockState(pos),bullet));
            h.assertTrue(d.lastAttackTick==l.getGameTime(),"the hit was registered as an attack on the beacon");
            h.assertTrue(d.health==full-Rules.contactDamage(0,d.defense),"the beacon lost "+(full-d.health)+" HP, expected "+Rules.contactDamage(0,d.defense));
            LOG.info("OPT_CHECK TaCZ AmmoHitBlockEvent from a husk took {} HP from the beacon through the typed listener",full-d.health);
        }finally{
            if(husk!=null)husk.discard();l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            d.resetProgress();d.snapshot.clear();d.beacon=BlockPos.ZERO;d.phase="unplaced";d.setDirty();BeaconCombat.tick(l,d);
        }
        h.assertTrue(BeaconCombat.objective(l)==null,"the borrowed campaign is put back (no objective left)");
        h.succeed();
    }
    /**
     * How often the old catch-all listener was called: every event the game posts on the Forge bus during 200 ticks of this world is counted, by
     * thread, and the cost of one call of the old listener is measured on a private bus. Informational (the numbers depend on the world).
     */
    @GameTest(template="empty3x3x3",batch="optimize_4",timeoutTicks=400)
    public static void eventBusTraffic(GameTestHelper h){
        var server=new java.util.concurrent.atomic.AtomicLong();var other=new java.util.concurrent.atomic.AtomicLong();
        java.util.function.Consumer<Event> count=e->{if(Thread.currentThread().getName().equals("Server thread"))server.incrementAndGet();else other.incrementAndGet();};
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,true,Event.class,count);
        long start=h.getLevel().getGameTime();
        h.runAfterDelay(200,()->{
            MinecraftForge.EVENT_BUS.unregister(count);long ticks=Math.max(1,h.getLevel().getGameTime()-start);
            var husk=EntityType.HUSK.create(h.getLevel());
            var withLegacy=net.minecraftforge.eventbus.api.BusBuilder.builder().build();withLegacy.register(new LegacyImpact());
            var without=net.minecraftforge.eventbus.api.BusBuilder.builder().build();without.addListener((net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e)->{});
            withLegacy.addListener((net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e)->{});
            int n=2_000_000;long legacyNs=0,plainNs=0;
            for(int round=0;round<4;round++){
                long t0=System.nanoTime();for(int i=0;i<n;i++)withLegacy.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(husk));long t1=System.nanoTime();
                for(int i=0;i<n;i++)without.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(husk));long t2=System.nanoTime();
                if(round>0){legacyNs+=t1-t0;plainNs+=t2-t1;}
            }
            double perCall=(legacyNs-plainNs)/(3.0*n);
            LOG.info("OPT_BENCH event_bus server_events_per_tick={} other_thread_events_per_tick={} legacy_listener_ns_per_event={} (all posted events reached it)",
                server.get()/ticks,other.get()/ticks,String.format(Locale.ROOT,"%.1f",perCall));
            h.succeed();
        });
    }
    /** Without the optional Special Forces mod no soldier can exist: the once-a-second sweep over every Overworld entity is skipped. */
    @GameTest(template="empty3x3x3",batch="optimize_5",timeoutTicks=100)
    public static void theSpecialForcesSweepIsSkippedWithoutTheMod(GameTestHelper h){
        boolean present=net.minecraftforge.fml.ModList.get().isLoaded("taczsf");
        h.assertTrue(SpecialForcesRaids.installed()==present,"installed() follows the mod list");
        var husk=EntityType.HUSK.create(h.getLevel());h.assertTrue(!SpecialForcesRaids.soldier(husk),"a husk is never a soldier");
        long t0=System.nanoTime();for(int i=0;i<200;i++)SpecialForcesRaids.cleanup(h.getLevel());long ns=(System.nanoTime()-t0)/200;
        long t1=System.nanoTime();int seen=0;for(int i=0;i<200;i++)for(var e:h.getLevel().getAllEntities())if(e.getClass().getName().equals("su.uTa4u.specialforces.entities.SwatEntity"))seen++;long legacy=(System.nanoTime()-t1)/200;
        LOG.info("OPT_BENCH special_forces_sweep mod_present={} entities={} legacy_us={} new_us={} soldiers_seen={}",present,count(h.getLevel()),
            String.format(Locale.ROOT,"%.1f",legacy/1e3),String.format(Locale.ROOT,"%.1f",ns/1e3),seen);
        h.succeed();
    }
    private static int count(ServerLevel l){int n=0;for(var e:l.getAllEntities())n++;return n;}

    // ---- verbatim copies of the replaced code -------------------------------------------------------------------------------------------
    /** Floors counted by the last {@link #legacyAnalyze} (test bookkeeping only). */
    static int legacyFloors;
    /** BaseScoring.analyze as it was before 0.0.15, unchanged (apart from noting the floor count for the test). */
    static BaseScoring.Score legacyAnalyze(CampaignData d,Map<Long,BlockState> blocks,Set<Long> placed){
        Set<Long> designed=new HashSet<>();Map<String,Integer> furnishing=new HashMap<>(),machines=new HashMap<>();Set<String> palette=new HashSet<>();List<BlockPos> anchors=new ArrayList<>();int lights=0,details=0;int[] ages=new int[4];
        for(var e:blocks.entrySet()){
            var s=e.getValue();String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();if(s.is(ArsenalBeacon.STRUCTURE_PART.get()))continue;if(s.isAir()||!s.getFluidState().isEmpty()||s.is(ArsenalBeacon.BEACON.get()))continue;
            if(s.getBlock() instanceof WeaponPlatform.Station station){int kind=station.kind.equals("gun")?0:station.kind.equals("ammo")?1:station.kind.equals("attachment")?2:3;ages[kind]=Math.max(ages[kind],s.getValue(WeaponPlatform.AGE));continue;}
            if(BaseScoring.factory(s,id)){machines.merge(id,1,Integer::sum);continue;}
            if(placed.contains(e.getKey())||!BaseScoring.terrain(id))designed.add(e.getKey());
            if(BaseScoring.furniture(id)){furnishing.merge(id,1,Integer::sum);anchors.add(BlockPos.of(e.getKey()));}
            if(s.getLightEmission()>0&&!(s.getBlock() instanceof BaseFireBlock)){lights++;anchors.add(BlockPos.of(e.getKey()));}
        }
        int floors=0;Set<Integer> levels=new HashSet<>();
        if(!anchors.isEmpty())for(var e:blocks.entrySet()){
            BlockPos p=BlockPos.of(e.getKey());if(!d.inside(p.above(2))||BaseScoring.terrain(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).toString())&&p.getY()<d.beacon.getY()-15)continue;
            if(!e.getValue().isSolid()||blocks.containsKey(p.above().asLong())||blocks.containsKey(p.above(2).asLong()))continue;
            if(anchors.stream().noneMatch(a->a.distSqr(p)<=144))continue;
            BlockPos roof=null;for(int y=3;y<=10&&d.inside(p.above(y));y++)if(blocks.getOrDefault(p.above(y).asLong(),Blocks.AIR.defaultBlockState()).isSolid()){roof=p.above(y);break;}
            if(roof==null)continue;List<BlockPos> walls=new ArrayList<>();
            for(Direction dir:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))for(int distance=1;distance<=12;distance++){
                BlockPos wall=p.above().relative(dir,distance);if(!d.inside(wall))break;
                var s=blocks.get(wall.asLong());if(s!=null&&(s.isSolid()||s.getBlock() instanceof DoorBlock||s.getBlock() instanceof FenceBlock)){walls.add(wall);break;}
            }
            if(walls.size()<3)continue;floors++;levels.add(p.getY());designed.add(p.asLong());designed.add(roof.asLong());for(var wall:walls)for(int y=0;y<roof.getY()-wall.getY();y++)if(blocks.containsKey(wall.above(y).asLong()))designed.add(wall.above(y).asLong());
            if(floors>=72+(d.core>=3?4:Math.max(0,d.core))*180)break;
        }
        for(long p:designed){var s=blocks.get(p);if(s==null)continue;String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();if(s.is(ArsenalBeacon.STRUCTURE_PART.get()))continue;palette.add(id);if(BaseScoring.detail(id,s))details++;}
        int core=d.core>=3?4:Math.max(0,d.core);legacyFloors=floors;
        return new BaseScoring.Score(Math.min(400,designed.size()),Math.min(8,palette.size())*20,Math.min(120+core*30,details*2),Math.min(80+core*10,lights*4),Math.min(180+core*30,furnishing.values().stream().mapToInt(n->Math.min(6,n)*6).sum()),Math.min(180+core*355,floors*2+Math.min(3,levels.size())*24),Math.min(500,machines.values().stream().mapToInt(n->Math.min(4,n)*20).sum()),Arrays.stream(ages).map(Rules::platformScore).sum());
    }
    /** BeaconCombat's TaCZ listener as it was before 0.0.15: subscribed to the base event class, so every posted event reached it. */
    public static final class LegacyImpact {
        @SubscribeEvent public void gunBlockImpact(Event event){
            if(!event.getClass().getName().equals("com.tacz.guns.api.event.server.AmmoHitBlockEvent"))return;
            throw new IllegalStateException("not reached in the benchmark");
        }
    }

    // ---- a synthetic base -------------------------------------------------------------------------------------------------------------
    static CampaignData campaign(int core,int vertical,BlockPos beacon){var d=new CampaignData();d.core=core;d.vertical=vertical;d.beacon=beacon;d.phase="preparation";return d;}
    private static BlockState pick(Random r,Block... blocks){return blocks[r.nextInt(blocks.length)].defaultBlockState();}
    /**
     * Everything a survey of a lived-in base would see: layered ground (with ores and pockets of water), houses of one to three storeys with
     * floors, walls, windows, doors, roofs, lamps and furniture, lamp posts, fences, a lit and an unlit furnace, fire, weapon stations,
     * structure parts and the beacon. Fills {@code placed} with what a player would have placed (and a few ground blocks).
     */
    static Map<Long,BlockState> base(CampaignData d,long seed,int houses,int lamps,Set<Long> placed){
        var r=new Random(seed);var map=new HashMap<Long,BlockState>();var b=d.beacon;int rad=d.radius(),below=d.below(),above=d.above();
        for(int y=-below;y<0;y++)for(int x=-rad;x<=rad;x++)for(int z=-rad;z<=rad;z++){
            BlockState s=y==-1?Blocks.GRASS_BLOCK.defaultBlockState():y>=-4?Blocks.DIRT.defaultBlockState():r.nextInt(40)==0?Blocks.IRON_ORE.defaultBlockState():r.nextInt(3)==0?Blocks.DEEPSLATE.defaultBlockState():Blocks.STONE.defaultBlockState();
            if(y<-6&&r.nextInt(60)==0)s=Blocks.WATER.defaultBlockState();
            map.put(b.offset(x,y,z).asLong(),s);
        }
        for(int i=0;i<houses;i++){
            int w=4+r.nextInt(Math.max(1,Math.min(9,rad-2))),dp=4+r.nextInt(Math.max(1,Math.min(9,rad-2))),storeys=1+r.nextInt(3),hgt=3+r.nextInt(3);
            int x0=-rad+r.nextInt(Math.max(1,2*rad-w)),z0=-rad+r.nextInt(Math.max(1,2*rad-dp));
            var wall=pick(r,Blocks.STONE_BRICKS,Blocks.OAK_PLANKS,Blocks.COBBLESTONE,Blocks.BRICKS,Blocks.SPRUCE_PLANKS,Blocks.DEEPSLATE_TILES);
            var floor=pick(r,Blocks.OAK_PLANKS,Blocks.STONE_BRICKS,Blocks.POLISHED_ANDESITE,Blocks.SMOOTH_STONE,Blocks.DIRT);
            for(int s=0;s<storeys;s++){int fy=s*(hgt+1);if(fy+hgt+1>above)break;
                for(int x=x0;x<=x0+w;x++)for(int z=z0;z<=z0+dp;z++){
                    put(map,placed,b,x,fy,z,floor);put(map,placed,b,x,fy+hgt+1,z,s==storeys-1?pick(r,Blocks.STONE_BRICK_SLAB,Blocks.OAK_SLAB,Blocks.SPRUCE_STAIRS,Blocks.OAK_PLANKS):floor);
                    boolean edge=x==x0||x==x0+w||z==z0||z==z0+dp;
                    for(int y=fy+1;y<=fy+hgt;y++){
                        if(edge){var s2=y==fy+2&&r.nextInt(4)==0?pick(r,Blocks.GLASS,Blocks.GLASS_PANE,Blocks.WHITE_STAINED_GLASS):wall;if(y<=fy+2&&x==x0+w/2&&z==z0)s2=y==fy+1?Blocks.OAK_DOOR.defaultBlockState():Blocks.AIR.defaultBlockState();if(!s2.isAir())put(map,placed,b,x,y,z,s2);else map.remove(b.offset(x,y,z).asLong());}
                        else if(y==fy+1&&r.nextInt(14)==0)put(map,placed,b,x,y,z,pick(r,Blocks.CHEST,Blocks.BARREL,Blocks.BOOKSHELF,Blocks.CRAFTING_TABLE,Blocks.RED_BED,Blocks.WHITE_CARPET,Blocks.FLOWER_POT,Blocks.TORCH,Blocks.LANTERN));
                    }
                }
                put(map,placed,b,x0+1,fy+hgt,z0+1,pick(r,Blocks.LANTERN,Blocks.GLOWSTONE,Blocks.SEA_LANTERN));
            }
        }
        for(int i=0;i<lamps;i++){int x=-rad+r.nextInt(2*rad+1),z=-rad+r.nextInt(2*rad+1);put(map,placed,b,x,0,z,Blocks.OAK_FENCE.defaultBlockState());put(map,placed,b,x,1,z,Blocks.OAK_FENCE.defaultBlockState());put(map,placed,b,x,2,z,pick(r,Blocks.LANTERN,Blocks.TORCH,Blocks.SOUL_LANTERN));}
        for(int i=0;i<rad*2;i++){int x=-rad+r.nextInt(2*rad+1),z=-rad+r.nextInt(2*rad+1);put(map,placed,b,x,0,z,pick(r,Blocks.COBBLESTONE_WALL,Blocks.STONE_BRICK_STAIRS,Blocks.OAK_LEAVES,Blocks.SAND,Blocks.GRAVEL,Blocks.HAY_BLOCK));}
        put(map,placed,b,2,0,-3,Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT,true));put(map,placed,b,3,0,-3,Blocks.FURNACE.defaultBlockState());
        put(map,placed,b,-2,0,4,Blocks.FIRE.defaultBlockState());put(map,placed,b,-3,0,4,Blocks.LAVA.defaultBlockState());
        put(map,placed,b,4,0,4,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,1+r.nextInt(5)));put(map,placed,b,6,0,4,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,1+r.nextInt(5)));
        put(map,placed,b,4,1,4,ArsenalBeacon.STRUCTURE_PART.get().defaultBlockState());
        map.put(b.asLong(),ArsenalBeacon.BEACON.get().defaultBlockState());
        for(int i=0;i<rad;i++)placed.add(b.offset(-rad+r.nextInt(2*rad+1),-1,-rad+r.nextInt(2*rad+1)).asLong()); // a few player-placed ground blocks
        return map;
    }
    /** A two-storey hall over the whole zone (walls on its edge, lanterns inside): well over 72 usable floors, so the floor cap is reached. */
    static void hall(CampaignData d,Map<Long,BlockState> map,Set<Long> placed){
        var b=d.beacon;int e=d.radius()-1;
        for(int storey=0;storey<2;storey++){int fy=storey*5;
            for(int x=-e;x<=e;x++)for(int z=-e;z<=e;z++){
                put(map,placed,b,x,fy,z,Blocks.OAK_PLANKS.defaultBlockState());put(map,placed,b,x,fy+5,z,Blocks.STONE_BRICKS.defaultBlockState());
                boolean edge=Math.abs(x)==e||Math.abs(z)==e;
                for(int y=fy+1;y<=fy+4;y++){long p=b.offset(x,y,z).asLong();if(edge)put(map,placed,b,x,y,z,Blocks.STONE_BRICKS.defaultBlockState());else{map.remove(p);placed.remove(p);}}
            }
            for(int x=-e+2;x<=e-2;x+=4)for(int z=-e+2;z<=e-2;z+=4)put(map,placed,b,x,fy+4,z,Blocks.LANTERN.defaultBlockState());
        }
        map.put(b.asLong(),ArsenalBeacon.BEACON.get().defaultBlockState());
    }
    private static void put(Map<Long,BlockState> map,Set<Long> placed,BlockPos b,int x,int y,int z,BlockState s){long p=b.offset(x,y,z).asLong();map.put(p,s);placed.add(p);}

    // ---- opt-in runner --------------------------------------------------------------------------------------------------------------
    /** {@code -Darsenal.optimizeTests=true}: adds the {@code /optimize-path-test} command; {@link #start} runs everything and logs OPT_TEST lines. */
    public static final class Runner {
        private static Collection<GameTestInfo> infos=List.of();private static MultipleTestTracker tracker;
        /** Result of the last finished run (for the QA harness). */
        public static volatile int lastPass=-1,lastFail=-1;
        @SubscribeEvent public void register(net.minecraftforge.event.RegisterCommandsEvent e){
            ensureRegistered();
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("optimize-path-test").requires(s->s.hasPermission(2)).executes(c->{start(c.getSource().getLevel());return 1;}));
        }
        /** The tests are registered by Forge when a development run has game tests enabled; register them otherwise, but never twice. */
        public static void ensureRegistered(){
            if(GameTestRegistry.getAllTestFunctions().stream().noneMatch(t->t.getBatchName().startsWith("optimize_")))GameTestRegistry.register(OptimizePathGameTests.class);
        }
        public static void start(ServerLevel target){
            if(tracker!=null)throw new IllegalStateException("An optimize-path test run is already active");
            ensureRegistered();var names=new HashSet<String>();
            var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getBatchName().startsWith("optimize_")&&names.add(t.getTestName())).toList();
            infos=GameTestRunner.runTests(tests,new BlockPos(0,190,64),net.minecraft.world.level.block.Rotation.NONE,target,GameTestTicker.SINGLETON,1);tracker=new MultipleTestTracker(infos);
        }
        @SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
            if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;
            if(!net.minecraftforge.gametest.ForgeGameTestHooks.isGametestEnabled())GameTestTicker.SINGLETON.tick(); // a development run already ticks the tests itself
            if(!tracker.isDone())return;
            int pass=0,fail=0;
            for(var info:infos){if(info.hasFailed()){fail++;LOG.error("OPT_TEST_FAIL {}: {}",info.getTestName(),info.getError()==null?"?":info.getError().getMessage());}else{pass++;LOG.info("OPT_TEST_PASS {}",info.getTestName());}}
            LOG.info("OPT_TESTS_DONE pass={} fail={}",pass,fail);lastPass=pass;lastFail=fail;tracker=null;
        }
    }
}
