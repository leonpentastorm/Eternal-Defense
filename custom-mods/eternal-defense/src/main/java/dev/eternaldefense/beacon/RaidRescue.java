package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** What the raid does about a raider that cannot get to the beacon: a gate, else the parachute squadron. */
final class RaidRescue {
    private RaidRescue(){}
    static boolean logging(){return Boolean.getBoolean("eternaldefense.stuckLog");}

    /** Raiders the tracker applies to. */
    static boolean eligible(Mob mob){
        var data=mob.getPersistentData();
        return data.getBoolean("eternalRaider")&&!RaidTypes.flyer(mob)&&!RaidTypes.glider(mob)&&!data.getBoolean("eternalChute")
            &&!data.getBoolean("eternalSpecialForces")&&!SpecialForcesRaids.soldier(mob);
    }

    /** Called once a second for each raider; true when it went through a gate or joined the squadron this second. */
    static boolean tick(ServerLevel l,CampaignData d,Mob mob,Iterator<UUID> it){
        if(!eligible(mob))return false;
        long now=l.getGameTime();UUID id=mob.getUUID();
        var place=RaiderGates.channel(id);
        if(place!=null){RaiderGates.advance(l,d,mob,place,now);return false;} // At a gate: its own clock, exempt from the progress timer
        double distance=distanceToBeacon(d,mob);
        var target=mob.getTarget();boolean aim=target!=null&&target.isAlive();
        boolean sees=aim&&mob.hasLineOfSight(target);double toTarget=aim?Math.sqrt(mob.distanceToSqr(target)):Double.MAX_VALUE;
        double reach=aim?Math.max(2.2,mob.getBbWidth()+target.getBbWidth()):0;
        boolean striking=aim&&RaidDigging.inRing(mob.getBoundingBox(),target.getBoundingBox());
        var defender=l.getNearestPlayer(mob.getX(),mob.getY(),mob.getZ(),RaidMarch.ENGAGED_RADIUS,p->p instanceof net.minecraft.world.entity.player.Player player&&RaidTargets.attackable(player));
        boolean busy=RaidMarch.busy(striking||RaidMarch.inMeleeReach(toTarget,reach,sees),RaidMarch.holdingRange(BeaconCombat.ranged(mob),toTarget,sees),
            RaidMarch.breachedRecently(RaidMarch.lastBreach(id),now),RaidMarch.fought(defender!=null&&RaidMarch.reachable(defender.getY()-mob.getY()),mob.getLastHurtByMob()!=null),!l.isPositionEntityTicking(mob.blockPosition()));
        var track=RaidMarch.observe(RaidMarch.track(id),distance,now,busy);RaidMarch.track(id,track);
        String reason=RaidMarch.stuck(track,now,busy)?"timer":null;
        // Deep in water and far from land toward its target, or swimming for a while already: through a gate at once
        long wet=RaidMarch.swimming(id,swimming(mob),now);
        if(reason==null&&wet>=0&&!striking&&!RaidMarch.inMeleeReach(toTarget,reach,sees)&&RaidMarch.graceOver(track,now)){
            if(RaidMarch.swumTooLong(wet,now)||RaidMarch.swimTooLong(RaidMarch.swimSeconds(RaidBridges.swim(l,mob,RaidBridges.goal(d,mob)),swimSpeed(mob))))reason="water";
        }
        if(reason==null)return false;

        var data=mob.getPersistentData();boolean boss=data.getBoolean("eternalBoss");int gates=data.getInt(RaidMarch.GATES);
        diagnose("TRIGGER",l,d,mob,track,gates,"-",reason,null);
        double flat=Math.sqrt(Math.pow(d.beacon.getX()+.5-mob.getX(),2)+Math.pow(d.beacon.getZ()+.5-mob.getZ(),2));
        if(RaidMarch.dropNow(flat,d.beacon.getY()-mob.getY(),d.radius()))return drop(l,d,mob,track,gates,"under the beacon");
        if(RaidMarch.next(gates,boss)==RaidMarch.Step.DROP)return drop(l,d,mob,track,gates,reason);
        BlockPos destination=findDestination(l,d,mob);
        if(destination==null){
            var dry=RaidMarch.dry(track,now);RaidMarch.track(id,dry);diagnose("NO_PLACE",l,d,mob,dry,gates,"-",reason,null);
            return RaidMarch.afterNoDestination(dry.dry(),boss)==RaidMarch.Step.DROP&&drop(l,d,mob,dry,gates,reason);
        }
        data.putInt(RaidMarch.GATES,gates+1);RaidDigging.stop(mob);
        RaiderGates.open(l,d,mob,destination,reason,now);
        if(boss)com.mojang.logging.LogUtils.getLogger().info("[Eternal Defense] The raid boss {} is stuck {} blocks from the beacon and opened gate {}.",
            mob.getType().getDescription().getString(),Math.round(distance),gates+1);
        return true;
    }

    /** In water with its head under, or off the ground: swimming (a mob at home in water never is) */
    static boolean swimming(Mob mob){
        if(mob instanceof net.minecraft.world.entity.monster.Drowned||mob.getMobType()==net.minecraft.world.entity.MobType.WATER)return false;
        return mob.isInWater()&&(mob.isUnderWater()||!mob.onGround());
    }
    /** Its move speed times its swim speed */
    static double swimSpeed(Mob mob){
        double speed=mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        var swim=mob.getAttribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get());return swim==null?speed:speed*swim.getValue();
    }
    static double distanceToBeacon(CampaignData d,Mob mob){return Math.sqrt(mob.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+1,d.beacon.getZ()+.5));}
    /** Positions of every player that could see a raider appear (spectators do not count). */
    static List<Vec3> players(ServerLevel l){var out=new ArrayList<Vec3>();for(var p:l.players())if(!p.isSpectator())out.add(p.position());return out;}
    /** Does the raider's box fit at to without touching a block (a boss is bigger than a spawn point)? */
    static boolean fits(ServerLevel l,Mob mob,BlockPos to){
        var move=new Vec3(to.getX()+.5-mob.getX(),to.getY()-mob.getY(),to.getZ()+.5-mob.getZ());return l.noCollision(mob,mob.getBoundingBox().move(move));
    }
    /** A place for the raider to come out: see RaidSpawns.findRescue */
    static BlockPos findDestination(ServerLevel l,CampaignData d,Mob mob){
        var found=RaidSpawns.findRescue(l,d,RaidSpawns.wedgeOf(d.beacon,mob.blockPosition()),players(l));
        return found!=null&&fits(l,mob,found)?found:null;
    }
    /** A gate found nowhere to send a raider: look again after the grace window, and count the attempt. */
    static void noPlace(ServerLevel l,CampaignData d,Mob mob,long now){
        var t=RaidMarch.track(mob.getUUID());if(t==null)t=RaidMarch.start(distanceToBeacon(d,mob),now);RaidMarch.track(mob.getUUID(),RaidMarch.dry(t,now));
    }

    /** Puts a raider on its new spot without any leftover movement, path or fall. */
    static void relocate(Mob mob,BlockPos to){
        mob.stopRiding();mob.getNavigation().stop();
        mob.moveTo(to.getX()+.5,to.getY(),to.getZ()+.5,mob.getYRot(),mob.getXRot());
        mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
        mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
    }

    /** Height over the beacon a squadron raider jumps from, and over the highest block above it */
    static final int SQUADRON_HEIGHT=24,SQUADRON_CLEARANCE=12;
    /** The stuck raider joins the parachute squadron: it is flown in and jumps right above the beacon (null spot: tried again next time) */
    static boolean drop(ServerLevel l,CampaignData d,Mob mob,RaidMarch.Track track,int gates,String reason){
        var spot=squadronSpot(l,d,mob);if(spot==null)return false;
        diagnose("DROP",l,d,mob,track,gates,spot.x+","+spot.y+","+spot.z,reason,null);
        var from=mob.position();
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,from.x,from.y+1,from.z,30,.4,.8,.4,.05);
        l.playSound(null,from.x,from.y,from.z,net.minecraft.sounds.SoundEvents.PHANTOM_FLAP,net.minecraft.sounds.SoundSource.HOSTILE,1f,.6f);
        RaidDigging.stop(mob);mob.stopRiding();mob.getNavigation().stop();
        mob.moveTo(spot.x,spot.y,spot.z,mob.getYRot(),mob.getXRot());mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
        RaidTypes.parachute(mob);
        var data=mob.getPersistentData();data.putInt(RaidMarch.DROPS,data.getInt(RaidMarch.DROPS)+1);
        RaidMarch.track(mob.getUUID(),RaidMarch.rescued(distanceToBeacon(d,mob),l.getGameTime()));RaidMarch.dropped();d.setDirty();
        return true;
    }
    /** Where a squadron raider jumps: right above the beacon, over something to land on at the beacon's level (on a sky island, never past its edge) */
    static Vec3 squadronSpot(ServerLevel l,CampaignData d,Mob mob){
        var objective=BeaconCombat.objective(l);double half=(objective==null?1:objective.getBbWidth())/2+1;
        for(int tries=0;tries<10;tries++){
            double spread=tries<8?half:.3;   // Last tries: straight over the beacon itself
            double x=d.beacon.getX()+.5+(l.random.nextDouble()*2-1)*spread,z=d.beacon.getZ()+.5+(l.random.nextDouble()*2-1)*spread;
            int top=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,net.minecraft.util.Mth.floor(x),net.minecraft.util.Mth.floor(z));
            if(!landing(top,d.beacon.getY())&&tries<8)continue;
            int y=Math.min(l.getMaxBuildHeight()-2-(int)Math.ceil(mob.getBbHeight()),Math.max(d.beacon.getY()+SQUADRON_HEIGHT,top+SQUADRON_CLEARANCE));
            if(l.noCollision(mob,mob.getBoundingBox().move(x-mob.getX(),y-mob.getY(),z-mob.getZ())))return new Vec3(x,y,z);
        }
        return null;
    }
    /** Pure: a column whose top is at the beacon's level or above has something to land on (the ground beside it, its cover, the beacon) */
    static boolean landing(int top,int beaconY){return top>=beaconY;}
    /** Once a second: chat names the raiders that joined the squadron. */
    static void announceDrops(ServerLevel l){
        int n=RaidMarch.takeDropped();
        if(n>0)EternalDefense.announce(l,net.minecraft.network.chat.Component.translatable(n==1?"gui.eternal_defense.raid.squadron.one":"gui.eternal_defense.raid.squadron.many",n));
    }
    /** The server stops: forget every tracker, corridor, channel, crack and target */
    static void reset(){RaidMarch.forget();RaidSpawns.forgetCorridors();RaiderGates.reset();RaidDigging.reset();RaidTargets.reset();}
    /** A raid starts or ends, or the campaign is packed up: forget everything, take the gates, stand-ins and bridges out of the world, heal the cracks. */
    static void reset(ServerLevel l){RaidMarch.forget();RaidSpawns.forgetCorridors();RaiderGates.reset(l);RaidDigging.reset(l);RaidTargets.reset(l);if(l.dimension()==net.minecraft.world.level.Level.OVERWORLD)RaidBridges.remove(l,CampaignData.get(l),Integer.MAX_VALUE);}

    // Diagnostics
    /** One stuck-raider log line. */
    static String line(String event,LinkedHashMap<String,String> fields){
        var out=new StringBuilder("[stuck] event=").append(event);fields.forEach((k,v)->out.append(' ').append(k).append('=').append(v));return out.toString();
    }
    private static String block(ServerLevel l,BlockPos p){return BuiltInRegistries.BLOCK.getKey(l.getBlockState(p).getBlock()).toString();}
    /** Logs one event of a stuck raider (only with -Deternaldefense.stuckLog=true). */
    static void diagnose(String event,ServerLevel l,CampaignData d,Mob mob,RaidMarch.Track track,int gates,String destination,String reason,UUID gate){
        if(!logging())return;
        var feet=mob.blockPosition();var toward=new Vec3(d.beacon.getX()+.5-mob.getX(),0,d.beacon.getZ()+.5-mob.getZ());toward=toward.lengthSqr()<1e-6?Vec3.ZERO:toward.normalize();
        var ahead=BlockPos.containing(mob.getX()+toward.x,mob.getY(),mob.getZ()+toward.z);
        long forced=RaidMarch.lastForcedMove(mob.getUUID()),now=l.getGameTime();
        var f=new LinkedHashMap<String,String>();
        f.put("type",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        var data=mob.getPersistentData();
        f.put("role",data.getBoolean("eternalBoss")?"boss":data.getString("eternalRole").isEmpty()?"-":data.getString("eternalRole"));
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
