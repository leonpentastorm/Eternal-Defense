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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

/**
 * Raider gates on a real server level (opt-in: start the game with {@code -Darsenal.stuckTests=true}; {@link Runner} starts them).
 * The tests build one big flat stone platform (13 x 13 chunks, force-loaded) around a fake beacon, so that a gate has real staging ground
 * for its destination, and then drive {@link RaidRescue} and the real {@code ArsenalBeacon.raid} loop with raiders that cannot move. Nothing
 * here touches the real campaign: each test uses its own {@link CampaignData} instance. A raider that "never makes progress" is a husk with no
 * AI and no gravity, or one sealed in a stone cell.
 */
@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class RaiderGateGameTests {
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
    /** One second of the raid loop for one raider, as the real loop calls it (and the clean-up it runs afterwards). True when it was withdrawn. */
    static boolean second(Fixture f,Mob mob){
        var it=f.campaign().raiders.iterator();boolean withdrawn=false;
        while(it.hasNext())if(it.next().equals(mob.getUUID())){withdrawn=RaidRescue.tick(f.level(),f.campaign(),mob,it);break;}
        RaiderGates.cleanup(f.level(),f.campaign());return withdrawn;
    }
    static List<? extends RaiderGate> gatesNear(ServerLevel l,BlockPos at,double r){return RaiderGates.gates(l).stream().filter(g->g.isAlive()&&g.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<=r*r).toList();}
    static void fill(ServerLevel l,BlockPos from,BlockPos to,net.minecraft.world.level.block.state.BlockState state){for(var p:BlockPos.betweenClosed(from,to))l.setBlock(p,state,2|16);}
    static int chebyshev(BlockPos a,BlockPos b){return Math.max(Math.abs(a.getX()-b.getX()),Math.abs(a.getZ()-b.getZ()));}

    // ---- the gate: trigger, channel, teleport --------------------------------------------------------------------------------------------
    private static final int SEC=20;
    @GameTest(template="empty3x3x3",batch="gate_01",timeoutTicks=1500)
    public static void aRaiderSealedInAStoneCellOpensAGateChannelsAndComesOutAtTheRing(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var cell=BEACON.east(40);
        for(var side:List.of(cell.east(),cell.west(),cell.north(),cell.south()))for(int y=0;y<2;y++)l.setBlock(side.above(y),Blocks.STONE.defaultBlockState(),2|16);
        l.setBlock(cell.above(2),Blocks.STONE.defaultBlockState(),2|16);
        var mob=raider(f,EntityType.HUSK,cell,false);BeaconCombat.attach(mob);long start=l.getGameTime();long[] channelAt={-1},outAt={-1};var seenAt=new Vec3[]{null};
        h.onEachTick(()->{long t=l.getGameTime()-start;if(t>0&&t%SEC==0){second(f,mob);
            if(channelAt[0]<0&&RaiderGates.channeling(mob.getUUID())){channelAt[0]=t;seenAt[0]=mob.position();}
            if(channelAt[0]>=0&&outAt[0]<0&&!mob.blockPosition().equals(cell))outAt[0]=t;}});
        h.succeedWhen(()->{
            h.assertTrue(channelAt[0]>=0,"the sealed raider has not started to channel yet");
            h.assertTrue(Math.abs(channelAt[0]-RaidMarch.STUCK_SECONDS*SEC)<=SEC*8,"it started to channel after "+channelAt[0]+" ticks, expected about "+RaidMarch.STUCK_SECONDS*SEC);
            h.assertTrue(outAt[0]>=0,"the raider has not come out yet");
            long channelled=outAt[0]-channelAt[0];
            h.assertTrue(channelled>=RaidMarch.CHANNEL_SECONDS*SEC-SEC&&channelled<=RaidMarch.CHANNEL_SECONDS*SEC+SEC*2,"it channelled for "+channelled+" ticks, expected "+RaidMarch.CHANNEL_SECONDS*SEC);
            h.assertTrue(!f.campaign().inside(mob.blockPosition())&&chebyshev(BEACON,mob.blockPosition())>=f.campaign().radius()+RaidSpawns.CLEARANCE,"it comes out outside the zone plus clearance: "+mob.blockPosition());
            h.assertTrue(RaidSpawns.wedgeOf(BEACON,mob.blockPosition())!=RaidSpawns.wedgeOf(BEACON,cell),"in another sector than the one it was stuck in");
            h.assertTrue(mob.getPersistentData().getInt(RaidMarch.GATES)==1,"one gate is counted on the mob itself");
            h.assertTrue(mob.fallDistance==0&&mob.getNavigation().isDone()&&!RaiderGates.channeling(mob.getUUID()),"no leftover fall, path or channel");
            h.assertTrue(f.campaign().raiders.contains(mob.getUUID())&&mob.isAlive(),"it stays part of the raid");
            h.assertTrue(gatesNear(l,cell,12).isEmpty(),"the gate is gone once nobody is assigned to it");
            mob.discard();for(var side:List.of(cell.east(),cell.west(),cell.north(),cell.south()))for(int y=0;y<2;y++)l.setBlock(side.above(y),Blocks.AIR.defaultBlockState(),2|16);l.setBlock(cell.above(2),Blocks.AIR.defaultBlockState(),2|16);
        });
    }
    @GameTest(template="empty3x3x3",batch="gate_02",timeoutTicks=1200)
    public static void aChannelingRaiderStandsBesideARedGateAndCannotMove(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var at=BEACON.north(30);var mob=raider(f,EntityType.HUSK,at,false);BeaconCombat.attach(mob);
        var channelStart=new Vec3[]{null};long t0=l.getGameTime();long[] channelAt={-1};double[] drift={0};
        h.onEachTick(()->{
            long t=l.getGameTime()-t0;
            if(channelAt[0]>=0)mob.getNavigation().moveTo(mob.getX()+15,mob.getY(),mob.getZ()+15,1.2); // the AI keeps trying to walk off
            if(t>0&&t%SEC==0){second(f,mob);if(channelAt[0]<0&&RaiderGates.channeling(mob.getUUID())){channelAt[0]=t;channelStart[0]=mob.position();}}
            if(channelAt[0]>=0)drift[0]=Math.max(drift[0],Math.hypot(mob.getX()-channelStart[0].x,mob.getZ()-channelStart[0].z));
        });
        // a lava pool 4 to 8 blocks ahead on its line to the beacon (the raider is 30 blocks north of it): the look-ahead opens the gate within a second, not after 25
        fill(l,new BlockPos(BEACON.getX()-4,TOP,BEACON.getZ()-26),new BlockPos(BEACON.getX()+4,TOP,BEACON.getZ()-22),Blocks.LAVA.defaultBlockState());
        h.runAfterDelay(SEC*7,()->{
            try{
                h.assertTrue(channelAt[0]>=0&&channelAt[0]<=SEC*3,"the probe opened the gate at once: channel started at tick "+channelAt[0]);
                h.assertTrue(RaiderGates.channel(mob.getUUID()).reason.equals("probe:lava"),"reason "+RaiderGates.channel(mob.getUUID()).reason);
                h.assertTrue(drift[0]<.6,"the channeler did not move although the AI kept asking: it drifted "+drift[0]+" blocks");
                var gates=gatesNear(l,at,4);h.assertTrue(gates.size()==1,"exactly one gate stands beside it, saw "+gates.size());
                h.assertTrue(RaiderGates.channelingAt(gates.get(0).getUUID())==1,"it is the only channeler at the gate");
            }finally{mob.discard();second(f,mob);fill(l,new BlockPos(BEACON.getX()-4,TOP,BEACON.getZ()-26),new BlockPos(BEACON.getX()+4,TOP,BEACON.getZ()-22),Blocks.STONE.defaultBlockState());}
            h.succeed();
        });
    }
    /** Terrain set across a raider's straight line to the beacon, {@code from} to {@code to} blocks ahead of it, {@code half} blocks to each side. */
    private static List<BlockPos> across(ServerLevel l,BlockPos raider,int from,int to,int half,net.minecraft.world.level.block.state.BlockState state,int depth){
        double dx=BEACON.getX()-raider.getX(),dz=BEACON.getZ()-raider.getZ(),len=Math.sqrt(dx*dx+dz*dz);dx/=len;dz/=len;
        var done=new LinkedHashSet<BlockPos>();
        for(double s=from;s<=to;s+=.5)for(int w=-half;w<=half;w++){
            var column=new BlockPos((int)Math.floor(raider.getX()+.5+dx*s-dz*w),TOP,(int)Math.floor(raider.getZ()+.5+dz*s+dx*w));
            for(int y=0;y<depth;y++){l.setBlock(column.below(y),state,2|16);}
            done.add(column);
        }
        return List.copyOf(done);
    }
    private static void restore(ServerLevel l,List<BlockPos> columns,int depth){for(var column:columns)for(int y=0;y<depth;y++)l.setBlock(column.below(y),Blocks.STONE.defaultBlockState(),2|16);}
    @GameTest(template="empty3x3x3",batch="gate_03",timeoutTicks=400)
    public static void lavaMagmaAChasmAndAWideLakeTriggerTheProbeButAShortPondDoesNot(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        // five raiders on five different lines to the beacon, each with its own terrain across the line, 4 to 12 blocks ahead of it
        record Case(String name,BlockPos raider,int from,int to,net.minecraft.world.level.block.state.BlockState state,int depth,String expect){}
        var cases=List.of(
            new Case("lava",BEACON.east(40).above(0),4,8,Blocks.LAVA.defaultBlockState(),1,"probe:lava"),
            new Case("magma",BEACON.north(40),4,8,Blocks.MAGMA_BLOCK.defaultBlockState(),1,"probe:lava"),
            new Case("chasm",BEACON.south(40),4,6,Blocks.AIR.defaultBlockState(),7,"probe:drop"),
            new Case("wide lake",BEACON.west(40),1,12,Blocks.WATER.defaultBlockState(),1,"probe:fluid"),
            new Case("short pond",BEACON.offset(30,0,30),4,6,Blocks.WATER.defaultBlockState(),1,null));
        var mobs=new ArrayList<Mob>();var changed=new ArrayList<List<BlockPos>>();
        for(var c:cases){changed.add(across(l,c.raider,c.from,c.to,2,c.state,c.depth));mobs.add(raider(f,EntityType.HUSK,c.raider,true));}
        h.onEachTick(()->{if(l.getGameTime()%SEC==0)for(var m:mobs)second(f,m);});
        h.runAfterDelay(SEC*6,()->{
            try{
                for(int i=0;i<cases.size();i++){
                    var c=cases.get(i);var ch=RaiderGates.channel(mobs.get(i).getUUID());
                    if(c.expect==null)h.assertTrue(ch==null&&RaidMarch.track(mobs.get(i).getUUID())!=null,c.name+": a short pond a zombie can wade through must not open a gate");
                    else h.assertTrue(ch!=null&&ch.reason.equals(c.expect),c.name+": expected "+c.expect+" but "+(ch==null?"no gate":ch.reason));
                    if(c.expect!=null)h.assertTrue(RaidSpawns.lookAhead(l,f.campaign(),mobs.get(i))!=TerrainProbe.Hazard.NONE,c.name+": the probe still sees it");
                }
            }finally{
                for(var m:mobs){m.discard();second(f,m);}
                for(int i=0;i<cases.size();i++)restore(l,changed.get(i),cases.get(i).depth);
            }
            h.succeed();
        });
    }
    @GameTest(template="empty3x3x3",batch="gate_04",timeoutTicks=700)
    public static void damageDelaysTheChannelButTheTotalDelayIsCapped(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var mob=raider(f,EntityType.HUSK,BEACON.west(36),true);mob.setHealth(mob.getMaxHealth());long t0=l.getGameTime();long[] channelAt={-1},outAt={-1};var from=mob.blockPosition();
        h.onEachTick(()->{
            long t=l.getGameTime()-t0;if(t<=0||t%SEC!=0)return;
            if(channelAt[0]>=0&&outAt[0]<0&&mob.blockPosition().equals(from)&&t>channelAt[0]+SEC)mob.hurt(l.damageSources().generic(),1f); // hurt every second while it channels
            second(f,mob);
            if(channelAt[0]<0&&RaiderGates.channeling(mob.getUUID()))channelAt[0]=t;
            if(channelAt[0]>=0&&outAt[0]<0&&!mob.blockPosition().equals(from))outAt[0]=t;
        });
        // a lava pool ahead so that the channel starts at once
        fill(l,new BlockPos(BEACON.getX()-30,TOP,BEACON.getZ()-4),new BlockPos(BEACON.getX()-26,TOP,BEACON.getZ()+4),Blocks.LAVA.defaultBlockState());
        h.succeedWhen(()->{
            h.assertTrue(outAt[0]>=0,"the hurt raider has not come out yet");
            long channelled=outAt[0]-channelAt[0];long max=(RaidMarch.CHANNEL_SECONDS+RaidMarch.MAX_DELAY_SECONDS)*SEC;
            h.assertTrue(channelled>RaidMarch.CHANNEL_SECONDS*SEC+SEC,"damage delayed it: channelled "+channelled+" ticks instead of "+RaidMarch.CHANNEL_SECONDS*SEC);
            h.assertTrue(channelled<=max+SEC,"the delay is capped at "+RaidMarch.MAX_DELAY_SECONDS+" s: channelled "+channelled+" ticks, limit "+max);
            h.assertTrue(mob.isAlive(),"it survived the fire");
            mob.discard();second(f,mob);fill(l,new BlockPos(BEACON.getX()-30,TOP,BEACON.getZ()-4),new BlockPos(BEACON.getX()-26,TOP,BEACON.getZ()+4),Blocks.STONE.defaultBlockState());
        });
    }
    @GameTest(template="empty3x3x3",batch="gate_05",timeoutTicks=500)
    public static void aKillCancelsTheChannelAndTheEmptyGateGoesAway(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var at=BEACON.south(34);var mob=raider(f,EntityType.HUSK,at,true);long t0=l.getGameTime();long[] killed={-1};
        h.onEachTick(()->{long t=l.getGameTime()-t0;if(t<=0||t%SEC!=0)return;
            if(killed[0]<0&&RaiderGates.channeling(mob.getUUID())&&t>SEC*2){mob.kill();killed[0]=t;}
            second(f,mob);if(!mob.isAlive())f.campaign().raiders.remove(mob.getUUID());RaiderGates.cleanup(l,f.campaign());});
        fill(l,new BlockPos(BEACON.getX()-4,TOP,BEACON.getZ()+26),new BlockPos(BEACON.getX()+4,TOP,BEACON.getZ()+30),Blocks.LAVA.defaultBlockState());
        h.succeedWhen(()->{
            h.assertTrue(killed[0]>=0,"the channeler has not been killed yet");
            h.assertTrue(RaiderGates.channel(mob.getUUID())==null,"killing it cancelled the channel");
            h.assertTrue(RaiderGates.assigned()==0&&gatesNear(l,at,12).isEmpty(),"the gate is gone with its last raider");
            h.assertTrue(!f.campaign().inside(mob.blockPosition())&&mob.blockPosition().distSqr(at)<4,"it never went through the gate");
            fill(l,new BlockPos(BEACON.getX()-4,TOP,BEACON.getZ()+26),new BlockPos(BEACON.getX()+4,TOP,BEACON.getZ()+30),Blocks.STONE.defaultBlockState());
        });
    }
    @GameTest(template="empty3x3x3",batch="gate_06",timeoutTicks=1500)
    public static void nineStuckRaidersShareOneGateEightChannelAndTheNinthWaits(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var base=BEACON.east(44);var mobs=new ArrayList<Mob>();
        for(int i=0;i<9;i++)mobs.add(raider(f,EntityType.HUSK,base.offset(i%3,0,i/3),true));
        long t0=l.getGameTime();int[] peak={0},waiting={0};boolean[] sharedOne={true};long[] firstOut={-1},allOut={-1};
        h.onEachTick(()->{long t=l.getGameTime()-t0;if(t<=0||t%SEC!=0)return;
            for(var m:mobs)second(f,m);
            var gates=gatesNear(l,base,12);int channeling=(int)mobs.stream().filter(m->RaiderGates.channeling(m.getUUID())).count();
            if(gates.size()>1)sharedOne[0]=false;
            if(channeling>peak[0]){peak[0]=channeling;waiting[0]=(int)mobs.stream().filter(m->RaiderGates.channel(m.getUUID())!=null&&!RaiderGates.channeling(m.getUUID())).count();}
            long out=mobs.stream().filter(m->m.blockPosition().distSqr(base)>400).count();
            if(out>0&&firstOut[0]<0)firstOut[0]=t;if(out==9&&allOut[0]<0)allOut[0]=t;});
        h.succeedWhen(()->{
            h.assertTrue(allOut[0]>=0,"not every raider has come out yet");
            h.assertTrue(sharedOne[0],"never more than one gate stood at the cluster");
            h.assertTrue(peak[0]==RaidMarch.GATE_CAPACITY,"at most "+RaidMarch.GATE_CAPACITY+" channelled at once, peak "+peak[0]);
            h.assertTrue(waiting[0]==1,"the ninth waited its turn, waiting "+waiting[0]);
            h.assertTrue(allOut[0]-firstOut[0]>=RaidMarch.CHANNEL_SECONDS*SEC-SEC,"the ninth came out a whole channel after the first: "+(allOut[0]-firstOut[0]));
            h.assertTrue(gatesNear(l,base,12).isEmpty()&&RaiderGates.assigned()==0,"the gate went away when the last raider left");
            for(var m:mobs)m.discard();
        });
    }

    // ---- escalation ------------------------------------------------------------------------------------------------------------------
    @GameTest(template="empty3x3x3",batch="gate_07",timeoutTicks=3600)
    public static void afterThreeGatesTheRaiderIsWithdrawnAndTheWaveCompletesWellBeforeTheTimeout(GameTestHelper h)throws Exception{
        var f=fixture(h);var l=f.level();RaidRescue.reset(l);var d=f.campaign();
        d.wave=1;d.spawnRemaining=0;d.hardRaid=false; // the first wave of several: finishing it needs no Create reward items
        var mob=raider(f,EntityType.HUSK,BEACON.south(40),true);
        var raid=ArsenalBeacon.class.getDeclaredMethod("raid",ServerLevel.class,CampaignData.class);raid.setAccessible(true);
        var clock=ArsenalBeacon.class.getDeclaredField("clock");clock.setAccessible(true);
        int[] seen={0};long start=l.getGameTime();
        h.onEachTick(()->{
            if(d.wave!=1)return; // the wave is over: stop before the next one spawns real raiders
            try{int previous=clock.getInt(null);clock.setInt(null,20);try{raid.invoke(null,l,d);}finally{clock.setInt(null,previous);}}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            seen[0]=Math.max(seen[0],mob.getPersistentData().getInt(RaidMarch.GATES));
        });
        h.succeedWhen(()->{
            h.assertTrue(d.wave==2,"the wave has not completed yet (gates opened so far "+seen[0]+")");
            h.assertTrue(seen[0]==RaidMarch.MAX_GATES,"exactly "+RaidMarch.MAX_GATES+" gates before the withdrawal, saw "+seen[0]);
            h.assertTrue(mob.isRemoved()&&!d.raiders.contains(mob.getUUID()),"the stuck raider was withdrawn from the world and the raid");
            h.assertTrue(!d.rewards.isEmpty(),"the cleared wave paid its supply reward");
            h.assertTrue(d.phase.equals("raid")&&d.victories==0,"the raid goes on with the next wave");
            h.assertTrue(d.raidTicks<24000L&&d.raidTicks<=4*(RaidMarch.STUCK_SECONDS+RaidMarch.CHANNEL_SECONDS)*SEC+600,"the wave cleared after "+d.raidTicks+" raid ticks, far below the 24000 timeout");
            h.assertTrue(d.health==1000,"no timeout penalty: the beacon kept its health");
            h.assertTrue(RaidMarch.takeWithdrawn()==0,"the withdrawal was announced once and the count started over");
            h.assertTrue(RaiderGates.assigned()==0&&gatesNear(l,BEACON,300).isEmpty(),"no channel and no gate is left");
        });
    }
    @GameTest(template="empty3x3x3",batch="gate_08",timeoutTicks=3800)
    public static void aBossGoesThroughGateAfterGateAndIsNeverWithdrawn(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var mob=raider(f,EntityType.HUSK,BEACON.west(40),true);mob.getPersistentData().putBoolean("arsenalBoss",true);long t0=l.getGameTime();
        h.onEachTick(()->{long t=l.getGameTime()-t0;if(t>0&&t%SEC==0&&second(f,mob))h.fail("a boss was withdrawn");});
        h.succeedWhen(()->{
            int gates=mob.getPersistentData().getInt(RaidMarch.GATES);
            h.assertTrue(gates>=RaidMarch.MAX_GATES+1,"the boss opened "+gates+" gates so far; it must go past the point where an ordinary raider is withdrawn");
            h.assertTrue(mob.isAlive()&&!mob.isRemoved()&&f.campaign().raiders.contains(mob.getUUID()),"the boss is still part of the raid");
            mob.discard();second(f,mob);
        });
    }
    @GameTest(template="empty3x3x3",batch="gate_09",timeoutTicks=1500)
    public static void flyersAndParachutistsAreLeftAlone(GameTestHelper h){
        var f=fixture(h);var l=f.level();RaidSpawns.forgetCorridors();
        var vex=raider(f,EntityType.VEX,BEACON.east(60).above(20),true);RaidTypes.prepare(vex,"air");
        var chute=raider(f,EntityType.HUSK,BEACON.east(50).above(40),true);RaidTypes.parachute(chute);
        var vexAt=vex.position();var chuteAt=chute.position();long start=l.getGameTime();
        h.onEachTick(()->{long t=l.getGameTime()-start;if(t>0&&t%SEC==0){second(f,vex);second(f,chute);}});
        h.runAfterDelay(RaidMarch.STUCK_SECONDS*SEC*2+100,()->{
            h.assertTrue(vex.position().equals(vexAt)&&chute.position().equals(chuteAt),"neither was moved");
            h.assertTrue(RaidMarch.track(vex.getUUID())==null&&RaidMarch.track(chute.getUUID())==null,"neither was even tracked");
            h.assertTrue(RaiderGates.channel(vex.getUUID())==null&&RaiderGates.channel(chute.getUUID())==null&&gatesNear(l,BEACON,300).isEmpty(),"no gate was opened for either");
            h.assertTrue(vex.getPersistentData().getInt(RaidMarch.GATES)==0&&chute.getPersistentData().getInt(RaidMarch.GATES)==0,"no gate was counted");
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
    @GameTest(template="empty3x3x3",batch="gate_10",timeoutTicks=200)
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
    @GameTest(template="empty3x3x3",batch="gate_11",timeoutTicks=200)
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
            if(GameTestRegistry.getAllTestFunctions().stream().noneMatch(t->t.getBatchName().startsWith("gate_")))GameTestRegistry.register(RaiderGateGameTests.class);
        }
        public static void start(ServerLevel target){
            if(tracker!=null)throw new IllegalStateException("A stuck-raider test run is already active");
            level=target;ensureRegistered();var names=new HashSet<String>();
            var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getBatchName().startsWith("gate_")&&names.add(t.getTestName())).toList();
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
