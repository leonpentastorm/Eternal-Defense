package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class SpawnSixteenGameTests {
    @GameTest(template="empty3x3x3",batch="staging016",timeoutTicks=100)
    public static void stagingRingStaysFarOutsideEveryCoreAndRejectsRoofsAndPlayerGround(GameTestHelper h){
        var l=h.getLevel();var p=new BlockPos(9000,102,0);for(var corner:BlockPos.betweenClosed(p.offset(-6,0,-6),p.offset(6,0,6)))l.getChunkAt(corner);var d=new CampaignData();d.beacon=p.west(100);
        for(int core=0;core<=3;core++){d.core=core;for(int angle=0;angle<360;angle+=3)for(int spread:new int[]{0,8,16}){var candidate=RaidSpawns.ring(d.beacon,d.radius(),Math.toRadians(angle),spread);int edge=Math.max(Math.abs(candidate.getX()-d.beacon.getX()),Math.abs(candidate.getZ()-d.beacon.getZ()));h.assertTrue(edge==d.radius()+64+spread&&!d.inside(candidate),"No angle or Core level puts staging near or inside the protected zone");}}
        for(var cell:BlockPos.betweenClosed(p.offset(-6,0,-6),p.offset(6,8,6)))l.setBlock(cell,Blocks.AIR.defaultBlockState(),3);for(int depth=1;depth<=14;depth++)l.setBlock(p.below(depth),Blocks.STONE.defaultBlockState(),3);l.setBlock(p.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);h.assertTrue(RaidSpawns.environmentSafe(l,p),"Natural open ground passes the environmental check before adding a building");l.setBlock(p.below(),Blocks.OAK_PLANKS.defaultBlockState(),3);h.assertTrue(!RaidSpawns.environmentSafe(l,p),"A player-built roof cannot be used as an outdoor staging floor");l.setBlock(p.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);l.setBlock(p.above(3),Blocks.STONE.defaultBlockState(),3);h.assertTrue(!RaidSpawns.environmentSafe(l,p),"A roofed interior cannot stage a wave");l.setBlock(p.above(3),Blocks.AIR.defaultBlockState(),3);var ledger=BaseScoring.Ledger.get(l);ledger.placed.add(p.below().asLong());h.assertTrue(!RaidSpawns.environmentSafe(l,p),"Player-made dirt or stone ground is rejected too");ledger.placed.remove(p.below().asLong());
        var near=p.east(10);d.beacon=near;h.assertTrue(!RaidSpawns.safe(l,d,p),"Blocked far staging never permits a closer fallback");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="approach016",timeoutTicks=450)
    public static void farMeleeReinforcementActuallyTravelsTowardTheBeacon(GameTestHelper h){
        var l=h.getLevel();var floor=new BlockPos(8000,101,0);l.getChunkAt(floor);for(int chunk=0;chunk<=7;chunk++)for(int z=-1;z<=1;z++)l.setChunkForced((floor.getX()>>4)+chunk,z,true);for(int x=0;x<=110;x++)for(int z=-3;z<=3;z++){l.getChunkAt(floor.offset(x,0,z));l.setBlock(floor.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<=3;y++)l.setBlock(floor.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var mob=EntityType.HUSK.create(l);mob.moveTo(floor.getX()+96.5,floor.getY()+1,.5,0,0);mob.setPersistenceRequired();l.addFreshEntity(mob);var destination=floor.above();double start=mob.getX();
        // Exercise the same waypoint path used by both melee and ranged objective goals.
        h.onEachTick(()->{if(mob.tickCount%10==0)BeaconCombat.approach(mob,destination.getX()+.5,destination.getY(),destination.getZ()+.5,1.15);});
        h.runAfterDelay(350,()->{h.assertTrue(mob.getX()<start-32,"Native ground navigation carries a distant reinforcement toward the objective instead of freezing outside path range: "+mob.getX()+" vs "+start);mob.discard();for(int chunk=0;chunk<=7;chunk++)for(int z=-1;z<=1;z++)l.setChunkForced((floor.getX()>>4)+chunk,z,false);h.succeed();});
    }
}
