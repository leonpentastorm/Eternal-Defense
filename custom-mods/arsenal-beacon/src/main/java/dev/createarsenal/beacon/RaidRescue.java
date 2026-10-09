package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * What the raid does about a raider that cannot get to the beacon: after {@link RaidMarch#STUCK_SECONDS} without real progress it is moved
 * back to the edge of the staging ring ({@link RaidSpawns#findRescue}), up to {@link RaidMarch#MAX_RESCUES} times, and then withdrawn, so
 * that one unreachable mob cannot time the raid out. A raid boss is moved again each time it is stuck and never withdrawn (that would fail
 * the raid with "Boss was not defeated"). Raiders that march straight on are not changed: this only looks at the ones that have stopped.
 * <p>Not covered, on purpose: flyers and gliders, paratroopers while they still hang under their parachute, and Special Forces soldiers.
 * <p>Diagnostics: start the game with {@code -Darsenal.stuckLog=true} and every stuck, rescue and withdraw is logged with the terrain around
 * the raider (see {@link #line}).
 */
final class RaidRescue {
    private RaidRescue(){}
    static boolean logging(){return Boolean.getBoolean("arsenal.stuckLog");}

    /** Pure-ish: raiders the tracker applies to. */
    static boolean eligible(Mob mob){
        var data=mob.getPersistentData();
        return data.getBoolean("arsenalRaider")&&!RaidTypes.flyer(mob)&&!RaidTypes.glider(mob)&&!data.getBoolean("arsenalChute")
            &&!data.getBoolean("arsenalSpecialForces")&&!SpecialForcesRaids.soldier(mob);
    }

    /** Called once a second for each raider. Returns true when the raider was withdrawn (it was removed through {@code it}). */
    static boolean tick(ServerLevel l,CampaignData d,Mob mob,Iterator<UUID> it){
        if(!eligible(mob))return false;
        long now=l.getGameTime();UUID id=mob.getUUID();
        double distance=Math.sqrt(mob.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+1,d.beacon.getZ()+.5));
        var objective=BeaconCombat.objective(l);boolean aim=objective!=null&&objective.isAlive();
        boolean sees=aim&&mob.hasLineOfSight(objective);double toTarget=aim?Math.sqrt(mob.distanceToSqr(objective)):Double.MAX_VALUE;
        double reach=aim?Math.max(2.2,mob.getBbWidth()+objective.getBbWidth()):0;
        boolean ranged=mob.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof BeaconCombat.RangedBeacon);
        boolean busy=RaidMarch.busy(RaidMarch.inMeleeReach(toTarget,reach,sees),RaidMarch.holdingRange(ranged,toTarget,sees),
            RaidMarch.breachedRecently(RaidMarch.lastBreach(id),now),RaidMarch.fought(l.getNearestPlayer(mob,RaidMarch.ENGAGED_RADIUS)!=null,mob.getLastHurtByMob()!=null),!l.isPositionEntityTicking(mob.blockPosition()));
        var track=RaidMarch.observe(RaidMarch.track(id),distance,now,busy);RaidMarch.track(id,track);
        if(!RaidMarch.stuck(track,now,busy))return false;

        var data=mob.getPersistentData();boolean boss=data.getBoolean("arsenalBoss");int rescues=data.getInt(RaidMarch.RESCUES);
        diagnose("STUCK",l,d,mob,distance,track,rescues,"-");
        if(RaidMarch.next(rescues,boss)==RaidMarch.Step.WITHDRAW)return withdraw(l,d,mob,it,distance,track,rescues);
        var players=new ArrayList<Vec3>();for(var p:l.players())if(!p.isSpectator())players.add(p.position());
        BlockPos destination=RaidSpawns.findRescue(l,d,RaidSpawns.wedgeOf(d.beacon,mob.blockPosition()),players);
        if(destination!=null){ // a boss is bigger than a spawn point: its box has to fit
            var move=new Vec3(destination.getX()+.5-mob.getX(),destination.getY()-mob.getY(),destination.getZ()+.5-mob.getZ());
            if(!l.noCollision(mob,mob.getBoundingBox().move(move)))destination=null;
        }
        if(destination==null){
            var dry=RaidMarch.dry(track,now);RaidMarch.track(id,dry);diagnose("NO_PLACE",l,d,mob,distance,dry,rescues,"-");
            return RaidMarch.afterNoDestination(dry.dry(),boss)==RaidMarch.Step.WITHDRAW&&withdraw(l,d,mob,it,distance,dry,rescues);
        }
        relocate(mob,destination);data.putInt(RaidMarch.RESCUES,rescues+1);
        var moved=RaidMarch.rescued(Math.sqrt(mob.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+1,d.beacon.getZ()+.5)),now);RaidMarch.track(id,moved);
        diagnose("RESCUE",l,d,mob,distance,moved,rescues+1,destination.getX()+","+destination.getY()+","+destination.getZ());
        if(boss)com.mojang.logging.LogUtils.getLogger().info("[Create Arsenal] The raid boss {} was stuck {} blocks from the beacon and was moved to {} (move {}).",
            mob.getType().getDescription().getString(),Math.round(distance),destination.toShortString(),rescues+1);
        return false;
    }

    /** Puts a raider on its new spot without any leftover movement, path or fall. Nothing is placed anywhere. */
    static void relocate(Mob mob,BlockPos to){
        mob.stopRiding();mob.getNavigation().stop();
        mob.moveTo(to.getX()+.5,to.getY(),to.getZ()+.5,mob.getYRot(),mob.getXRot());
        mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
        mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
    }

    private static boolean withdraw(ServerLevel l,CampaignData d,Mob mob,Iterator<UUID> it,double distance,RaidMarch.Track track,int rescues){
        diagnose("WITHDRAW",l,d,mob,distance,track,rescues,"-");
        it.remove();mob.discard();d.setDirty();RaidMarch.withdrew(); // discard: no death, no drops, no Ardent Energy
        return true;
    }

    /** At the end of a wave: one chat line for the raiders withdrawn during it. */
    static void announceWithdrawn(ServerLevel l){
        int n=RaidMarch.takeWithdrawn();
        if(n>0)ArsenalBeacon.announce(l,RaidMarch.withdrawnMessage(n)+".");
    }
    /** A raid starts or ends, the server stops, the campaign is packed up: forget the trackers and the probed corridors. */
    static void reset(){RaidMarch.forget();RaidSpawns.forgetCorridors();}

    // ---- diagnostics --------------------------------------------------------------------------------------------------------------
    /** Pure: one log line, {@code [stuck] event=... key=value ...}. */
    static String line(String event,LinkedHashMap<String,String> fields){
        var out=new StringBuilder("[stuck] event=").append(event);fields.forEach((k,v)->out.append(' ').append(k).append('=').append(v));return out.toString();
    }
    private static String block(ServerLevel l,BlockPos p){return BuiltInRegistries.BLOCK.getKey(l.getBlockState(p).getBlock()).toString();}
    static void diagnose(String event,ServerLevel l,CampaignData d,Mob mob,double distance,RaidMarch.Track track,int rescue,String destination){
        if(!logging())return;
        var feet=mob.blockPosition();var toward=new Vec3(d.beacon.getX()+.5-mob.getX(),0,d.beacon.getZ()+.5-mob.getZ());toward=toward.lengthSqr()<1e-6?Vec3.ZERO:toward.normalize();
        var ahead=BlockPos.containing(mob.getX()+toward.x,mob.getY(),mob.getZ()+toward.z);
        long forced=RaidMarch.lastForcedMove(mob.getUUID()),now=l.getGameTime();
        var f=new LinkedHashMap<String,String>();
        f.put("type",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        var data=mob.getPersistentData();
        f.put("role",data.getBoolean("arsenalBoss")?"boss":data.getString("arsenalRole").isEmpty()?"-":data.getString("arsenalRole"));
        f.put("id",mob.getUUID().toString().substring(0,8));f.put("wave",d.wave+"/"+Rules.waves(d.raidTier));f.put("raid",d.raidType);
        f.put("pos",feet.getX()+","+feet.getY()+","+feet.getZ());f.put("dist",String.format(Locale.ROOT,"%.1f",distance));
        f.put("wedge",Integer.toString(RaidSpawns.wedgeOf(d.beacon,feet)));
        f.put("feet",block(l,feet));f.put("below",block(l,feet.below()));f.put("ahead",block(l,ahead));f.put("aheadUp",block(l,ahead.above()));
        f.put("fluid",BuiltInRegistries.FLUID.getKey(l.getFluidState(feet).getType()).toString());
        f.put("biome",l.getBiome(feet).unwrapKey().map(k->k.location().toString()).orElse("?"));
        f.put("onGround",Boolean.toString(mob.onGround()));f.put("inWater",Boolean.toString(mob.isInWater()));
        f.put("navDone",Boolean.toString(mob.getNavigation().isDone()));f.put("hasPath",Boolean.toString(mob.getNavigation().getPath()!=null));
        f.put("forcedMove",forced<0||now-forced>RaidMarch.STUCK_SECONDS*RaidMarch.TICKS_PER_SECOND*2?"no":(now-forced)/RaidMarch.TICKS_PER_SECOND+"s ago");
        f.put("stalled",(now-track.improvedAt())/RaidMarch.TICKS_PER_SECOND+"s");f.put("best",String.format(Locale.ROOT,"%.1f",track.best()));
        f.put("rescue",rescue+"/"+RaidMarch.MAX_RESCUES);f.put("dry",Integer.toString(track.dry()));f.put("dest",destination);
        com.mojang.logging.LogUtils.getLogger().info(line(event,f));
    }
}
