package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * What the raid does about a raider that cannot get to the beacon. Once a second, for every ground raider, it asks two questions: is the way
 * ahead blocked by lava, a chasm or a wide lake ({@link RaidSpawns#lookAhead}), and has the raider made no real progress for
 * {@link RaidMarch#STUCK_SECONDS} while not busy ({@link RaidMarch#stuck})? Either one opens a {@link RaiderGate}: the raider channels beside it
 * and comes out at the edge of the staging ring ({@link RaiderGates}). It can do that {@link RaidMarch#MAX_GATES} times; the next time it is
 * withdrawn so that one unreachable mob cannot time the raid out. A raid boss goes through a gate every time and is never withdrawn (that would
 * fail the raid with "Boss was not defeated"). Raiders keep marching straight: nothing here changes how they move.
 * <p>Not covered, on purpose: flyers and gliders, paratroopers while their parachute is on, and Special Forces soldiers.
 * <p>Diagnostics: start the game with {@code -Darsenal.stuckLog=true} and every trigger, channel, interruption, teleport and withdrawal is logged
 * with the terrain around the raider (see {@link #line}).
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
        var place=RaiderGates.channel(id);
        if(place!=null){RaiderGates.advance(l,d,mob,place,now);return false;} // at a gate: its own clock, exempt from the progress timer
        double distance=distanceToBeacon(d,mob);
        var objective=BeaconCombat.objective(l);boolean aim=objective!=null&&objective.isAlive();
        boolean sees=aim&&mob.hasLineOfSight(objective);double toTarget=aim?Math.sqrt(mob.distanceToSqr(objective)):Double.MAX_VALUE;
        double reach=aim?Math.max(2.2,mob.getBbWidth()+objective.getBbWidth()):0;
        boolean ranged=mob.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof BeaconCombat.RangedBeacon);
        boolean busy=RaidMarch.busy(RaidMarch.inMeleeReach(toTarget,reach,sees),RaidMarch.holdingRange(ranged,toTarget,sees),
            RaidMarch.breachedRecently(RaidMarch.lastBreach(id),now),RaidMarch.fought(l.getNearestPlayer(mob,RaidMarch.ENGAGED_RADIUS)!=null,mob.getLastHurtByMob()!=null),!l.isPositionEntityTicking(mob.blockPosition()));
        var track=RaidMarch.observe(RaidMarch.track(id),distance,now,busy);RaidMarch.track(id,track);
        String reason=null;
        if(RaidMarch.stuck(track,now,busy))reason="timer";
        else if(!busy&&now>=track.graceUntil()){var hazard=RaidSpawns.lookAhead(l,d,mob);if(hazard!=TerrainProbe.Hazard.NONE)reason="probe:"+hazard.name().toLowerCase(Locale.ROOT);}
        if(reason==null)return false;

        var data=mob.getPersistentData();boolean boss=data.getBoolean("arsenalBoss");int gates=data.getInt(RaidMarch.GATES);
        diagnose("TRIGGER",l,d,mob,track,gates,"-",reason,null);
        if(RaidMarch.next(gates,boss)==RaidMarch.Step.WITHDRAW)return withdraw(l,d,mob,it,track,gates,reason);
        BlockPos destination=findDestination(l,d,mob);
        if(destination==null){
            var dry=RaidMarch.dry(track,now);RaidMarch.track(id,dry);diagnose("NO_PLACE",l,d,mob,dry,gates,"-",reason,null);
            return RaidMarch.afterNoDestination(dry.dry(),boss)==RaidMarch.Step.WITHDRAW&&withdraw(l,d,mob,it,dry,gates,reason);
        }
        data.putInt(RaidMarch.GATES,gates+1);
        RaiderGates.open(l,d,mob,destination,reason,now);
        if(boss)com.mojang.logging.LogUtils.getLogger().info("[Create Arsenal] The raid boss {} is stuck {} blocks from the beacon and opened gate {}.",
            mob.getType().getDescription().getString(),Math.round(distance),gates+1);
        return false;
    }

    static double distanceToBeacon(CampaignData d,Mob mob){return Math.sqrt(mob.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+1,d.beacon.getZ()+.5));}
    /** Positions of every player that could see a raider appear (spectators do not count). */
    static List<Vec3> players(ServerLevel l){var out=new ArrayList<Vec3>();for(var p:l.players())if(!p.isSpectator())out.add(p.position());return out;}
    /** Does the raider's box fit at {@code to} without touching a block (a boss is bigger than a spawn point)? */
    static boolean fits(ServerLevel l,Mob mob,BlockPos to){
        var move=new Vec3(to.getX()+.5-mob.getX(),to.getY()-mob.getY(),to.getZ()+.5-mob.getZ());return l.noCollision(mob,mob.getBoundingBox().move(move));
    }
    /** A place for the raider to come out: see {@link RaidSpawns#findRescue}; null when there is none (or the raider would not fit). */
    static BlockPos findDestination(ServerLevel l,CampaignData d,Mob mob){
        var found=RaidSpawns.findRescue(l,d,RaidSpawns.wedgeOf(d.beacon,mob.blockPosition()),players(l));
        return found!=null&&fits(l,mob,found)?found:null;
    }
    /** A gate found nowhere to send a raider: look again after the grace window, and count the attempt. */
    static void noPlace(ServerLevel l,CampaignData d,Mob mob,long now){
        var t=RaidMarch.track(mob.getUUID());if(t==null)t=RaidMarch.start(distanceToBeacon(d,mob),now);RaidMarch.track(mob.getUUID(),RaidMarch.dry(t,now));
    }

    /** Puts a raider on its new spot without any leftover movement, path or fall. Nothing is placed anywhere. */
    static void relocate(Mob mob,BlockPos to){
        mob.stopRiding();mob.getNavigation().stop();
        mob.moveTo(to.getX()+.5,to.getY(),to.getZ()+.5,mob.getYRot(),mob.getXRot());
        mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
        mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
    }

    private static boolean withdraw(ServerLevel l,CampaignData d,Mob mob,Iterator<UUID> it,RaidMarch.Track track,int gates,String reason){
        diagnose("WITHDRAW",l,d,mob,track,gates,"-",reason,null);
        it.remove();mob.discard();d.setDirty();RaidMarch.withdrew(); // discard: no death, no drops, no Ardent Energy
        return true;
    }

    /** At the end of a wave: one chat line for the raiders withdrawn during it. */
    static void announceWithdrawn(ServerLevel l){
        int n=RaidMarch.takeWithdrawn();
        if(n>0)ArsenalBeacon.announce(l,RaidMarch.withdrawnMessage(n)+".");
    }
    /** The server stops: forget every tracker, corridor and channel (gates are never saved, so they are gone with the world). */
    static void reset(){RaidMarch.forget();RaidSpawns.forgetCorridors();RaiderGates.reset();}
    /** A raid starts or ends, or the campaign is packed up: forget everything and take the gates out of the world. */
    static void reset(ServerLevel l){RaidMarch.forget();RaidSpawns.forgetCorridors();RaiderGates.reset(l);}

    // ---- diagnostics --------------------------------------------------------------------------------------------------------------
    /** Pure: one log line, {@code [stuck] event=... key=value ...}. */
    static String line(String event,LinkedHashMap<String,String> fields){
        var out=new StringBuilder("[stuck] event=").append(event);fields.forEach((k,v)->out.append(' ').append(k).append('=').append(v));return out.toString();
    }
    private static String block(ServerLevel l,BlockPos p){return BuiltInRegistries.BLOCK.getKey(l.getBlockState(p).getBlock()).toString();}
    /**
     * Logs one event of a stuck raider (only with {@code -Darsenal.stuckLog=true}). {@code track} may be null; {@code reason} is "timer" or "probe:lava|drop|fluid"
     * for a trigger, the cause for an interruption or cancellation.
     */
    static void diagnose(String event,ServerLevel l,CampaignData d,Mob mob,RaidMarch.Track track,int gates,String destination,String reason,UUID gate){
        if(!logging())return;
        var feet=mob.blockPosition();var toward=new Vec3(d.beacon.getX()+.5-mob.getX(),0,d.beacon.getZ()+.5-mob.getZ());toward=toward.lengthSqr()<1e-6?Vec3.ZERO:toward.normalize();
        var ahead=BlockPos.containing(mob.getX()+toward.x,mob.getY(),mob.getZ()+toward.z);
        long forced=RaidMarch.lastForcedMove(mob.getUUID()),now=l.getGameTime();
        var f=new LinkedHashMap<String,String>();
        f.put("type",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        var data=mob.getPersistentData();
        f.put("role",data.getBoolean("arsenalBoss")?"boss":data.getString("arsenalRole").isEmpty()?"-":data.getString("arsenalRole"));
        f.put("id",mob.getUUID().toString().substring(0,8));f.put("wave",d.wave+"/"+Rules.waves(d.raidTier));f.put("raid",d.raidType);
        f.put("pos",feet.getX()+","+feet.getY()+","+feet.getZ());f.put("dist",String.format(Locale.ROOT,"%.1f",distanceToBeacon(d,mob)));
        f.put("wedge",Integer.toString(RaidSpawns.wedgeOf(d.beacon,feet)));
        f.put("feet",block(l,feet));f.put("below",block(l,feet.below()));f.put("ahead",block(l,ahead));f.put("aheadUp",block(l,ahead.above()));
        f.put("fluid",BuiltInRegistries.FLUID.getKey(l.getFluidState(feet).getType()).toString());
        f.put("biome",l.getBiome(feet).unwrapKey().map(k->k.location().toString()).orElse("?"));
        f.put("onGround",Boolean.toString(mob.onGround()));f.put("inWater",Boolean.toString(mob.isInWater()));
        f.put("navDone",Boolean.toString(mob.getNavigation().isDone()));f.put("hasPath",Boolean.toString(mob.getNavigation().getPath()!=null));
        f.put("forcedMove",forced<0||now-forced>RaidMarch.STUCK_SECONDS*RaidMarch.TICKS_PER_SECOND*2?"no":(now-forced)/RaidMarch.TICKS_PER_SECOND+"s ago");
        f.put("stalled",track==null?"-":(now-track.improvedAt())/RaidMarch.TICKS_PER_SECOND+"s");f.put("best",track==null?"-":String.format(Locale.ROOT,"%.1f",track.best()));
        f.put("reason",reason==null?"-":reason.replace(' ','_'));f.put("gate",gate==null?"-":gate.toString().substring(0,8));
        f.put("gates",gates+"/"+RaidMarch.MAX_GATES);f.put("dry",track==null?"-":Integer.toString(track.dry()));f.put("dest",destination);
        com.mojang.logging.LogUtils.getLogger().info(line(event,f));
    }
}
