package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.15: raiders walk and extend the bridges already laid; a long swim goes through a gate at once. */
final class Release1115Test {
    @Test void aRaiderAtAnEdgeUsesABridgeThatLeadsItsWay(){
        // A bridge running west (-x) toward a goal 30 blocks west of its start
        assertEquals(8,RaidBridges.reuseCost(8,0,1,1,-1,0,-30,0),1e-9,"8 blocks along the shore: it walks there");
        assertEquals(-1,RaidBridges.reuseCost(16.5,0,1,1,-1,0,-30,0),"too far along the shore");
        assertEquals(16,RaidBridges.reuseCost(16,0,1,1,-1,0,-30,0),1e-9,"16 blocks is still in reach");
        assertEquals(-1,RaidBridges.reuseCost(4,0,1,1,1,0,-30,0),"a bridge leading away from the goal");
        assertEquals(-1,RaidBridges.reuseCost(4,0,3,1,-1,0,-30,0),"a giant does not take a one-plank bridge");
        assertEquals(4,RaidBridges.reuseCost(4,0,1,3,-1,0,-30,0),1e-9,"a small raider takes a giant's wide bridge");
        assertEquals(-1,RaidBridges.reuseCost(4,2.5,1,1,-1,0,-30,0),"a raider already past its start (on it, or across) does not turn back");
        assertEquals(2,RaidBridges.reuseCost(2,2,1,1,-1,0,-30,0),1e-9,"standing at its start is fine");
        assertEquals(16,RaidBridges.REUSE_RANGE);assertEquals(24,RaidBridges.MAX_LENGTH,"each raider still lays at most 24 planks");
    }
    @Test void aLongSwimGoesThroughAGateAtOnce(){
        double zombie=.23;
        assertEquals(1/(zombie*1.6),RaidMarch.swimSeconds(1,zombie),1e-9);
        assertFalse(RaidMarch.swimTooLong(RaidMarch.swimSeconds(1,zombie)),"one block of water to the shore: it swims");
        assertTrue(RaidMarch.swimTooLong(RaidMarch.swimSeconds(2,zombie)),"two blocks take a zombie more than 5 seconds");
        assertTrue(RaidMarch.swimTooLong(RaidMarch.swimSeconds(RaidMarch.SWIM_LOOK,.35)),"no land in sight: a gate");
        assertFalse(RaidMarch.swimTooLong(5),"5 seconds exactly is still swum");assertTrue(RaidMarch.swimTooLong(5.01));
        assertEquals(0,RaidMarch.swimSeconds(0,zombie));
        assertEquals(5,RaidMarch.SWIM_SECONDS);assertEquals(1.6,RaidMarch.SWIM_PER_SPEED);
        assertFalse(RaidMarch.swumTooLong(-1,1000),"not swimming");assertFalse(RaidMarch.swumTooLong(0,100),"5 seconds in the water");assertTrue(RaidMarch.swumTooLong(0,101),"longer: a gate even when the swim looked short");
        assertTrue(RaidMarch.graceOver(null,0));assertFalse(RaidMarch.graceOver(new RaidMarch.Track(10,0,200,0),100),"not again within the grace window");
    }
    @Test void wiring() throws Exception{
        String bridges=Release114Test.source("RaidBridges.java"),rescue=Release114Test.source("RaidRescue.java"),digging=Release114Test.source("RaidDigging.java");
        assertTrue(bridges.contains("var used=nearest(l,mob,goal,floorY);if(used!=null)return used;\n        if(d.bridges.size()>=LIMIT)return null;"),"an existing bridge first; the plank limit only stops new ones");
        assertTrue(bridges.contains("while(job.step<LINE_MAX&&plank(l,plan.cell(job.step,0)))job.step++;"),"planks already down are walked");
        assertTrue(bridges.contains("if(!open(l,next)){line.landed=true;return false;}"),"land ends the job");
        assertTrue(bridges.contains("if(job.step==1&&off>2){if(mob.getNavigation().isDone()||now%20==0)mob.getNavigation().moveTo("),"to a bridge's start a path is walked");
        assertTrue(digging.contains("static void reset(){CRACKS.clear();JOBS.clear();RaidBridges.forget();}"),"the bridges of one raid are forgotten with it");
        assertTrue(rescue.contains("if(reason==null&&wet>=0&&!striking&&!RaidMarch.inMeleeReach(toTarget,reach,sees)&&RaidMarch.graceOver(track,now)){"),"checked once a second, never while it fights its target");
        assertTrue(rescue.contains("if(RaidMarch.swumTooLong(wet,now)||RaidMarch.swimTooLong(RaidMarch.swimSeconds(RaidBridges.swim(l,mob,RaidBridges.goal(d,mob)),swimSpeed(mob))))reason=\"water\";"),"either already 5 s in the water or a swim that would take longer");
        assertTrue(rescue.contains("mob instanceof net.minecraft.world.entity.monster.Drowned||mob.getMobType()==net.minecraft.world.entity.MobType.WATER"),"drowned and water mobs swim on");
        assertTrue(rescue.contains("return mob.isInWater()&&(mob.isUnderWater()||!mob.onGround());"),"wading in shallow water is walking");
        String raids=Release114Test.text("guide.raids.body"),detail=Release114Test.text("guide.raids.detail");
        assertTrue(raids.contains("walk a bridge already there and lay on from its end"));
        assertTrue(raids.contains("more than "+RaidMarch.SWIM_SECONDS+" seconds"));
        assertTrue(detail.contains("starts within "+RaidBridges.REUSE_RANGE+" blocks"));
        assertTrue(detail.contains("more than "+RaidMarch.SWIM_SECONDS+" seconds at its best swimming speed (a zombie: about "+String.format(java.util.Locale.ROOT,"%.2f",.23*RaidMarch.SWIM_PER_SPEED)+" blocks a second"),"the zombie's speed quoted is the rule's");
    }
}
