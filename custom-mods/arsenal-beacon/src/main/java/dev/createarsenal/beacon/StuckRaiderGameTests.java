package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

/**
 * Stuck raiders on a real server level (opt-in: start the game with {@code -Darsenal.stuckTests=true}; {@link Runner} starts them).
 * The tests build one big flat stone platform (13 x 13 chunks, force-loaded) around a fake beacon, so that a rescue has real staging ground,
 * and then drive {@link RaidRescue} and the real {@code ArsenalBeacon.raid} loop with raiders that cannot move. Nothing here touches the
 * real campaign: each test uses its own {@link CampaignData} instance. A raider that "never makes progress" is a husk with no AI and no
 * gravity, or one sealed in a stone cell.
 */
@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class StuckRaiderGameTests {
    /** The fake beacon stands on the platform, whose top layer is y=100. Chunk (750,0) is its centre. */
    static final BlockPos BEACON=new BlockPos(12008,101,8);
    private static final int CHUNKS=6,FLOOR=88,TOP=100;
    private static boolean built;

    private static int chunkX(){return BEACON.getX()>>4;}
    private static int chunkZ(){return BEACON.getZ()>>4;}
    /** Builds (once) a stone platform y 88..100 over 13 x 13 chunks and keeps them loaded; the sections are written directly (fast, no lighting). */
    static void ground(ServerLevel l){
        if(built)return;
        var stone=Blocks.STONE.defaultBlockState();
        for(int cx=chunkX()-CHUNKS;cx<=chunkX()+CHUNKS;cx++)for(int cz=chunkZ()-CHUNKS;cz<=chunkZ()+CHUNKS;cz++){
            l.setChunkForced(cx,cz,true);LevelChunk chunk=l.getChunk(cx,cz);
            for(int y=FLOOR;y<=TOP;y++){var section=chunk.getSection(chunk.getSectionIndex(y));for(int x=0;x<16;x++)for(int z=0;z<16;z++)section.setBlockState(x,y&15,z,stone,false);}
            Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.MOTION_BLOCKING,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,Heightmap.Types.OCEAN_FLOOR,Heightmap.Types.WORLD_SURFACE));
            chunk.setUnsaved(true);
        }
        built=true;
    }
    /** After the run: let the platform's chunks go. */
    static void release(ServerLevel l){
        if(!built)return;
        for(int cx=chunkX()-CHUNKS;cx<=chunkX()+CHUNKS;cx++)for(int cz=chunkZ()-CHUNKS;cz<=chunkZ()+CHUNKS;cz++)l.setChunkForced(cx,cz,false);
        built=false;
    }

    record Fixture(ServerLevel level,CampaignData campaign){}
    static Fixture fixture(GameTestHelper h){
        var l=h.getLevel();ground(l);
        var d=new CampaignData();d.beacon=BEACON;d.phase="raid";d.health=1000;d.raidType="normal";d.raidTier=0;d.wave=1;
        return new Fixture(l,d);
    }
    /** A raid husk that stays where it is (no AI, no gravity), or one with the normal AI when {@code frozen} is false. */
    static Mob raider(Fixture f,EntityType<? extends Mob> type,BlockPos at,boolean frozen){
        Mob mob=type.create(f.level());mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean("arsenalRaider",true);
        if(frozen){mob.setNoAi(true);mob.setNoGravity(true);}
        f.level().addFreshEntity(mob);f.campaign().raiders.add(mob.getUUID());return mob;
    }
    /** One second of the raid loop for one raider, as the real loop calls it. True when it was withdrawn. */
    static boolean second(Fixture f,Mob mob){
        var it=f.campaign().raiders.iterator();
        while(it.hasNext())if(it.next().equals(mob.getUUID()))return RaidRescue.tick(f.level(),f.campaign(),mob,it);
        return false;
    }
    static int chebyshev(BlockPos a,BlockPos b){return Math.max(Math.abs(a.getX()-b.getX()),Math.abs(a.getZ()-b.getZ()));}

    // ---- relocation ------------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="stuck_a",timeoutTicks=1200)
    public static void aRaiderSealedInAStoneCellIsMovedToTheStagingRingAfterTwentyFiveSeconds(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var cell=BEACON.east(40);
        for(var side:List.of(cell.east(),cell.west(),cell.north(),cell.south()))for(int y=0;y<2;y++)l.setBlock(side.above(y),Blocks.STONE.defaultBlockState(),2|16);
        l.setBlock(cell.above(2),Blocks.STONE.defaultBlockState(),2|16);
        var mob=raider(f,EntityType.HUSK,cell,false);long start=l.getGameTime();long[] movedAt={-1};
        h.onEachTick(()->{long t=l.getGameTime()-start;if(movedAt[0]<0&&t>0&&t%20==0){second(f,mob);if(!mob.blockPosition().equals(cell))movedAt[0]=t;}});
        h.succeedWhen(()->{
            h.assertTrue(movedAt[0]>=0,"the sealed raider has not been moved yet");
            long expected=RaidMarch.STUCK_SECONDS*20L;
            h.assertTrue(movedAt[0]>=expected-20&&movedAt[0]<=expected+160,"moved after "+movedAt[0]+" ticks, expected about "+expected);
            h.assertTrue(!f.campaign().inside(mob.blockPosition())&&chebyshev(BEACON,mob.blockPosition())>=f.campaign().radius()+RaidSpawns.CLEARANCE,"it lands outside the zone and its clearance: "+mob.blockPosition());
            h.assertTrue(mob.getPersistentData().getInt(RaidMarch.RESCUES)==1,"one rescue is counted on the mob itself");
            h.assertTrue(mob.fallDistance==0&&mob.getNavigation().isDone(),"no leftover fall or path");
            h.assertTrue(f.campaign().raiders.contains(mob.getUUID())&&mob.isAlive(),"a rescued raider stays part of the raid");
            h.assertTrue(RaidSpawns.wedgeOf(BEACON,mob.blockPosition())!=RaidSpawns.wedgeOf(BEACON,cell),"it was put in another sector than the one it was stuck in");
            mob.discard();for(var side:List.of(cell.east(),cell.west(),cell.north(),cell.south()))for(int y=0;y<2;y++)l.setBlock(side.above(y),Blocks.AIR.defaultBlockState(),2|16);l.setBlock(cell.above(2),Blocks.AIR.defaultBlockState(),2|16);
        });
    }
    @GameTest(template="empty3x3x3",batch="stuck_a",timeoutTicks=1200)
    public static void aRaiderOnAnIsolatedPillarIsMovedOffIt(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var foot=BEACON.north(35);for(int y=0;y<6;y++)l.setBlock(foot.above(y),Blocks.STONE.defaultBlockState(),2|16);
        var top=foot.above(6);var mob=raider(f,EntityType.HUSK,top,true);long start=l.getGameTime();long[] movedAt={-1};
        h.onEachTick(()->{long t=l.getGameTime()-start;if(movedAt[0]<0&&t>0&&t%20==0){second(f,mob);if(!mob.blockPosition().equals(top))movedAt[0]=t;}});
        h.succeedWhen(()->{
            h.assertTrue(movedAt[0]>=0,"the raider on the pillar has not been moved yet");
            h.assertTrue(movedAt[0]>=RaidMarch.STUCK_SECONDS*20L-20&&movedAt[0]<=RaidMarch.STUCK_SECONDS*20L+160,"moved after "+movedAt[0]+" ticks");
            h.assertTrue(chebyshev(BEACON,mob.blockPosition())>=f.campaign().radius()+RaidSpawns.CLEARANCE,"on the staging ring: "+mob.blockPosition());
            h.assertTrue(Math.abs(mob.getY()-BEACON.getY())<=1,"on the ground of the ring, not in the air: "+mob.getY());
            mob.discard();for(int y=0;y<6;y++)l.setBlock(foot.above(y),Blocks.AIR.defaultBlockState(),2|16);
        });
    }

    // ---- escalation ------------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="stuck_withdraw",timeoutTicks=3200)
    public static void afterThreeRescuesTheRaiderIsWithdrawnAndTheWaveCompletesWellBeforeTheTimeout(GameTestHelper h)throws Exception{
        var f=fixture(h);var l=f.level();RaidRescue.reset();var d=f.campaign();
        d.wave=1;d.spawnRemaining=0;d.hardRaid=false; // the first wave of several: finishing it needs no Create reward items
        var mob=raider(f,EntityType.HUSK,BEACON.south(40),true);
        var raid=ArsenalBeacon.class.getDeclaredMethod("raid",ServerLevel.class,CampaignData.class);raid.setAccessible(true);
        var clock=ArsenalBeacon.class.getDeclaredField("clock");clock.setAccessible(true);
        long start=l.getGameTime();long[] rescuedAt=new long[4];int[] seen={0};
        h.onEachTick(()->{
            if(d.wave!=1)return; // the wave is over: stop before the next one spawns real raiders
            try{int previous=clock.getInt(null);clock.setInt(null,20);try{raid.invoke(null,l,d);}finally{clock.setInt(null,previous);}}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            int rescues=mob.getPersistentData().getInt(RaidMarch.RESCUES);if(rescues>seen[0]){seen[0]=rescues;rescuedAt[rescues]=l.getGameTime()-start;}
        });
        h.succeedWhen(()->{
            h.assertTrue(d.wave==2,"the wave has not completed yet (rescues so far "+seen[0]+")");
            h.assertTrue(seen[0]==RaidMarch.MAX_RESCUES,"exactly "+RaidMarch.MAX_RESCUES+" rescues before the withdrawal, saw "+seen[0]);
            h.assertTrue(mob.isRemoved()&&!d.raiders.contains(mob.getUUID()),"the stuck raider was withdrawn from the world and the raid");
            h.assertTrue(!d.rewards.isEmpty(),"the cleared wave paid its supply reward");
            h.assertTrue(d.phase.equals("raid")&&d.victories==0,"the raid goes on with the next wave");
            h.assertTrue(d.raidTicks<24000L&&d.raidTicks<=4*RaidMarch.STUCK_SECONDS*20L+300,"the wave cleared after "+d.raidTicks+" raid ticks, far below the 24000 timeout");
            h.assertTrue(d.health==1000,"no timeout penalty: the beacon kept its health");
            h.assertTrue(RaidMarch.takeWithdrawn()==0,"the withdrawal was announced once and the count started over");
            h.assertTrue(RaidMarch.kept()==0,"nothing is kept for the withdrawn raider");
        });
    }
    @GameTest(template="empty3x3x3",batch="stuck_a",timeoutTicks=3200)
    public static void aBossIsMovedAgainAndAgainButNeverWithdrawn(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var mob=raider(f,EntityType.HUSK,BEACON.west(40),true);mob.getPersistentData().putBoolean("arsenalBoss",true);
        long[] stalls={0};long start=l.getGameTime();
        h.onEachTick(()->{long t=l.getGameTime()-start;if(t>0&&t%20==0){int before=mob.getPersistentData().getInt(RaidMarch.RESCUES);boolean withdrawn=second(f,mob);if(withdrawn)h.fail("a boss was withdrawn");int after=mob.getPersistentData().getInt(RaidMarch.RESCUES);if(after>before)stalls[0]++;}});
        h.succeedWhen(()->{
            h.assertTrue(stalls[0]>=RaidMarch.MAX_RESCUES+2,"the boss was moved "+stalls[0]+" times so far; it must be moved past the point where an ordinary raider is withdrawn");
            h.assertTrue(mob.isAlive()&&!mob.isRemoved()&&f.campaign().raiders.contains(mob.getUUID()),"the boss is still part of the raid");
            mob.discard();
        });
    }
    @GameTest(template="empty3x3x3",batch="stuck_a",timeoutTicks=1500)
    public static void flyersAndParachutistsAreLeftAlone(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var vex=raider(f,EntityType.VEX,BEACON.east(60).above(20),true);RaidTypes.prepare(vex,"air");
        var chute=raider(f,EntityType.HUSK,BEACON.east(50).above(40),true);RaidTypes.parachute(chute);
        var vexAt=vex.position();var chuteAt=chute.position();long start=l.getGameTime();
        h.onEachTick(()->{long t=l.getGameTime()-start;if(t>0&&t%20==0){second(f,vex);second(f,chute);}});
        h.runAfterDelay(RaidMarch.STUCK_SECONDS*20L*2+100,()->{
            h.assertTrue(vex.position().equals(vexAt)&&chute.position().equals(chuteAt),"neither was moved");
            h.assertTrue(RaidMarch.track(vex.getUUID())==null&&RaidMarch.track(chute.getUUID())==null,"neither was even tracked");
            h.assertTrue(vex.getPersistentData().getInt(RaidMarch.RESCUES)==0&&chute.getPersistentData().getInt(RaidMarch.RESCUES)==0,"no rescue was counted");
            h.assertTrue(f.campaign().raiders.contains(vex.getUUID())&&f.campaign().raiders.contains(chute.getUUID())&&vex.isAlive()&&chute.isAlive(),"both still belong to the raid");
            vex.discard();chute.discard();h.succeed();
        });
    }

    // ---- corridors ---------------------------------------------------------------------------------------------------------------------
    private static void water(ServerLevel l,int distanceFrom,int distanceTo,java.util.function.IntPredicate wedge,boolean on){
        var state=on?Blocks.WATER.defaultBlockState():Blocks.STONE.defaultBlockState();
        for(int dx=-distanceTo;dx<=distanceTo;dx++)for(int dz=-distanceTo;dz<=distanceTo;dz++){
            int ring=Math.max(Math.abs(dx),Math.abs(dz));if(ring<distanceFrom||!wedge.test(RaidSpawns.wedgeOf(dx,dz)))continue;
            l.setBlock(new BlockPos(BEACON.getX()+dx,TOP,BEACON.getZ()+dz),state,2|16);
        }
    }
    @GameTest(template="empty3x3x3",batch="stuck_lakes",timeoutTicks=200)
    public static void aSectorCrossingALakeIsNeverChosenWhileCleanSectorsExist(GameTestHelper h){
        var f=fixture(h);var l=f.level();var d=f.campaign();RaidSpawns.forgetCorridors();int lake=3;
        water(l,36,48,w->w==lake,true);
        try{
            for(int w=0;w<RaidSpawns.WEDGES;w++){var score=RaidSpawns.corridor(l,d,w).score(88);h.assertTrue((w==lake)==(score.violations()>0),"wedge "+w+" violations "+score.violations());}
            for(int i=0;i<60;i++){var p=RaidSpawns.find(l,d);h.assertTrue(p!=null,"a spawn place exists");h.assertTrue(RaidSpawns.wedgeOf(BEACON,p)!=lake,"spawn "+i+" at "+p+" is in the lake sector");}
            h.assertTrue(RaidSpawns.probesThisRaid()<=RaidSpawns.WEDGES,"at most one probe per sector per raid, saw "+RaidSpawns.probesThisRaid());
        }finally{water(l,36,48,w->w==lake,false);RaidSpawns.forgetCorridors();}
        h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="stuck_lakes",timeoutTicks=200)
    public static void whenEverySectorHasAWaterCrossingTheOneWithTheFewestViolationsIsUsed(GameTestHelper h){
        var f=fixture(h);var l=f.level();var d=f.campaign();RaidSpawns.forgetCorridors();int best=7;
        water(l,36,48,w->true,true);water(l,41,48,w->w==best,false); // sector 7 only keeps the nearer half of the water
        try{
            for(int w=0;w<RaidSpawns.WEDGES;w++)h.assertTrue(!RaidSpawns.corridor(l,d,w).score(88).clean(),"no sector is clean: "+w);
            for(int i=0;i<40;i++){var p=RaidSpawns.find(l,d);h.assertTrue(p!=null,"spawning still works when every sector is imperfect");h.assertTrue(RaidSpawns.wedgeOf(BEACON,p)==best,"spawn "+i+" at "+p+" is not in the sector with the fewest violations");}
        }finally{water(l,36,48,w->true,false);RaidSpawns.forgetCorridors();}
        h.succeed();
    }

    // ---- opt-in runner --------------------------------------------------------------------------------------------------------------
    /** {@code -Darsenal.stuckTests=true}: adds the {@code /stuck-raider-test} command; {@link #start} runs everything and logs STUCK_TEST lines. */
    public static final class Runner {
        private static Collection<GameTestInfo> infos=List.of();private static MultipleTestTracker tracker;private static ServerLevel level;
        /** Result of the last finished run (for the QA harness). */
        public static volatile int lastPass=-1,lastFail=-1;
        @SubscribeEvent public void register(net.minecraftforge.event.RegisterCommandsEvent e){
            ensureRegistered();
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("stuck-raider-test").requires(s->s.hasPermission(2)).executes(c->{start(c.getSource().getLevel());return 1;}));
        }
        /** The tests are registered by Forge when a development run has game tests enabled; register them otherwise, but never twice. */
        public static void ensureRegistered(){
            if(GameTestRegistry.getAllTestFunctions().stream().noneMatch(t->t.getBatchName().startsWith("stuck_")))GameTestRegistry.register(StuckRaiderGameTests.class);
        }
        public static void start(ServerLevel target){
            if(tracker!=null)throw new IllegalStateException("A stuck-raider test run is already active");
            level=target;ensureRegistered();var names=new HashSet<String>();
            var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getBatchName().startsWith("stuck_")&&names.add(t.getTestName())).toList();
            infos=GameTestRunner.runTests(tests,new BlockPos(0,190,0),Rotation.NONE,target,GameTestTicker.SINGLETON,1);tracker=new MultipleTestTracker(infos);
        }
        @SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
            if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;
            if(!net.minecraftforge.gametest.ForgeGameTestHooks.isGametestEnabled())GameTestTicker.SINGLETON.tick(); // a development run already ticks the tests itself (MinecraftServer.tickChildren)
            if(!tracker.isDone())return;
            var log=com.mojang.logging.LogUtils.getLogger();int pass=0,fail=0;
            for(var info:infos){if(info.hasFailed()){fail++;log.error("STUCK_TEST_FAIL {}: {}",info.getTestName(),info.getError()==null?"?":info.getError().getMessage());}else{pass++;log.info("STUCK_TEST_PASS {}",info.getTestName());}}
            log.info("STUCK_TESTS_DONE pass={} fail={}",pass,fail);
            lastPass=pass;lastFail=fail;release(level);tracker=null;
        }
        public static boolean running(){return tracker!=null;}
    }
}
