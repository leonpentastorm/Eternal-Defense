package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;

/** Enabled only on the disposable integration server. Shared campaign fixtures run sequentially. */
final class IntegrationTests {
    private static MultipleTestTracker tracker;
    private static final Deque<TestFunction> waiting=new ArrayDeque<>();
    private static ServerLevel level;
    private static int completed,failed;
    static void start(ServerLevel target) {
        if(tracker!=null||!waiting.isEmpty())throw new IllegalStateException("Integration run already active");
        for(String mod:List.of("create","tacz","tacz_turrets"))
            if(!net.minecraftforge.fml.ModList.get().isLoaded(mod))throw new IllegalStateException("Integration dependency missing: "+mod);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.OnDatapackSyncEvent(target.getServer().getPlayerList(),null));
        level=target;completed=failed=0;
        waiting.addAll(GameTestRegistry.getAllTestFunctions().stream().filter(t->!Boolean.getBoolean("arsenal.rackTestsOnly")||t.getTestName().contains("racks")||t.getTestName().contains("gunplacement")||t.getTestName().contains("raidrepairsrestorespentgun")||t.getTestName().contains("allfourdisplays")).toList());next();
    }
    private static void next(){
        tracker=new MultipleTestTracker(GameTestRunner.runTests(List.of(waiting.removeFirst()),new BlockPos(0,100,0),Rotation.NONE,level,GameTestTicker.SINGLETON,4));
    }
    static void tick() {
        GameTestTicker.SINGLETON.tick();
        if(tracker==null||!tracker.isDone())return;
        var logger=com.mojang.logging.LogUtils.getLogger();completed+=tracker.getTotalCount();failed+=tracker.getFailedRequiredCount();
        for(var test:tracker.getFailedRequired())logger.error("Integration test failed: "+test.getTestName(),test.getError());
        // No fixture may leave a fake observer that invalidates another fixture's offline checks.
        for(var player:List.copyOf(level.players()))if(player instanceof net.minecraftforge.common.util.FakePlayer)
            level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        tracker=null;
        if(!waiting.isEmpty()){next();return;}
        if(failed>0)logger.error("{} required tests failed",failed);else logger.info("All {} required tests passed",completed);
    }
}
