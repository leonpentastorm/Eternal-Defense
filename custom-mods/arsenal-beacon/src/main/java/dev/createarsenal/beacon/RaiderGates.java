package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/**
 * Raider Gates. A ground raider that cannot get to the beacon (it walked up to lava, a chasm or a wide lake, or made no progress for 25
 * seconds, see {@link RaidRescue}) does not vanish silently: it stops, channels for {@link RaidMarch#CHANNEL_SECONDS} seconds beside a red
 * {@link RaiderGate} and then appears at the edge of the staging ring, where it marches again. While it channels it cannot move (the
 * {@link ChannelGoal}), it shows a ring of red particles, and it can be shot: every second it was hurt in delays the end by
 * {@link RaidMarch#DAMAGE_DELAY_SECONDS} seconds, to at most {@link RaidMarch#MAX_DELAY_SECONDS}; killing it cancels the channel. Three seconds
 * before it appears the destination shows particles and a portal sound.
 * <p>One gate serves an obstacle: a raider that gets stuck within {@link RaidMarch#GATE_JOIN_RADIUS} blocks of a gate uses it; a gate has room for
 * {@link RaidMarch#GATE_CAPACITY} channelers, the others wait their turn. A gate goes away when nobody is assigned to it any more. Nothing is
 * saved: after a reload the raiders fall back to the progress timer. All state lives here and is cleared when a raid starts or ends.
 */
final class RaiderGates {
    private RaiderGates(){}
    enum Phase{WAITING,CHANNELING}
    /** One raider's place at a gate. */
    static final class Channel {
        final UUID gate;final Vec3 gatePos;final String reason;Phase phase=Phase.WAITING;
        long startedAt,completeAt;int delayTicks;float health;BlockPos destination;boolean telegraphed;
        Channel(UUID gate,Vec3 gatePos,String reason,BlockPos destination){this.gate=gate;this.gatePos=gatePos;this.reason=reason;this.destination=destination;}
    }
    private static final Map<UUID,Channel> CHANNELS=new HashMap<>();
    /** The gates this class made (so a world with no gate is never searched for one). */
    private static final Set<UUID> KNOWN=new HashSet<>();

    // ---- pure decisions --------------------------------------------------------------------------------------------------------------
    /** Pure: the tick the channel ends, given when it started and how long damage has delayed it. */
    static long completeAt(long startedAt,int delayTicks){return startedAt+RaidMarch.CHANNEL_SECONDS*RaidMarch.TICKS_PER_SECOND+delayTicks;}
    /** Pure: the total delay after one more hurt second: {@link RaidMarch#DAMAGE_DELAY_SECONDS} longer, never more than {@link RaidMarch#MAX_DELAY_SECONDS}. */
    static int delayedBy(int delayTicks){return Math.min(RaidMarch.MAX_DELAY_SECONDS*(int)RaidMarch.TICKS_PER_SECOND,delayTicks+RaidMarch.DAMAGE_DELAY_SECONDS*(int)RaidMarch.TICKS_PER_SECOND);}
    /** Pure: it lost health since the last look. */
    static boolean hurt(float before,float now){return now<before-.001f;}
    /** Pure: the destination starts to show itself. */
    static boolean telegraphDue(long now,long completeAt){return now>=completeAt-RaidMarch.TELEGRAPH_SECONDS*RaidMarch.TICKS_PER_SECOND;}
    /** Pure: the nearest gate within {@code radius} of {@code at} (index into {@code gates}), or -1 when a new gate is needed. */
    static int nearestGate(List<Vec3> gates,Vec3 at,double radius){
        int best=-1;double bestDistance=radius*radius;
        for(int i=0;i<gates.size();i++){double dist=gates.get(i).distanceToSqr(at);if(dist<=bestDistance){best=i;bestDistance=dist;}}
        return best;
    }
    /** Pure: a gate with this many channelers can take another one. */
    static boolean hasRoom(int channeling){return channeling<RaidMarch.GATE_CAPACITY;}
    /** Pure: where a gate stands for a raider at {@code raider} that is marching towards {@code beacon}: 1.6 blocks to its side, at its feet. */
    static Vec3 gateSpot(Vec3 raider,Vec3 beacon){
        double dx=beacon.x-raider.x,dz=beacon.z-raider.z,len=Math.sqrt(dx*dx+dz*dz);if(len<1e-6){dx=1;dz=0;len=1;}
        return new Vec3(raider.x-dz/len*1.6,raider.y,raider.z+dx/len*1.6);
    }

    // ---- state ---------------------------------------------------------------------------------------------------------------------------
    static Channel channel(UUID raider){return CHANNELS.get(raider);}
    static boolean channeling(UUID raider){var c=CHANNELS.get(raider);return c!=null&&c.phase==Phase.CHANNELING;}
    static int channelingAt(UUID gate){int n=0;for(var c:CHANNELS.values())if(c.gate.equals(gate)&&c.phase==Phase.CHANNELING)n++;return n;}
    static int assigned(){return CHANNELS.size();}
    /** A raid starts or ends, the server stops: forget every channel. Use {@link #reset(ServerLevel)} to take the gates out of a world too. */
    static void reset(){CHANNELS.clear();KNOWN.clear();}
    static void reset(ServerLevel l){for(var gate:gates(l))gate.discard();CHANNELS.clear();KNOWN.clear();}
    static List<? extends RaiderGate> gates(ServerLevel l){return KNOWN.isEmpty()?List.of():l.getEntities(EntityTypeTest.forClass(RaiderGate.class),g->true);}

    // ---- the life of a channel -----------------------------------------------------------------------------------------------------------
    /** A stuck raider gets a place at the nearest gate within reach, or at a new one beside it. It channels at once when the gate has room, else it waits. */
    static void open(ServerLevel l,CampaignData d,Mob mob,BlockPos destination,String reason,long now){
        var raiderAt=mob.position();var found=new ArrayList<RaiderGate>();for(var g:gates(l))if(g.isAlive())found.add(g);
        int index=nearestGate(found.stream().map(RaiderGate::position).toList(),raiderAt,RaidMarch.GATE_JOIN_RADIUS);
        RaiderGate gate;
        if(index>=0)gate=found.get(index);
        else{
            gate=ArsenalBeacon.RAIDER_GATE.get().create(l);var spot=gateSpot(raiderAt,Vec3.atCenterOf(d.beacon));
            gate.moveTo(spot.x,spot.y,spot.z,0,0);l.addFreshEntity(gate);KNOWN.add(gate.getUUID());
            l.playSound(null,spot.x,spot.y,spot.z,SoundEvents.PORTAL_TRIGGER,SoundSource.HOSTILE,.4f,1.6f);
        }
        var ch=new Channel(gate.getUUID(),gate.position(),reason,destination);CHANNELS.put(mob.getUUID(),ch);
        if(hasRoom(channelingAt(ch.gate)))begin(l,d,mob,ch,now);
        else RaidRescue.diagnose("WAIT",l,d,mob,null,mob.getPersistentData().getInt(RaidMarch.GATES),"-",reason,ch.gate);
    }

    private static void begin(ServerLevel l,CampaignData d,Mob mob,Channel ch,long now){
        ch.phase=Phase.CHANNELING;ch.startedAt=now;ch.delayTicks=0;ch.completeAt=completeAt(now,0);ch.health=mob.getHealth();
        mob.getNavigation().stop();
        RaidRescue.diagnose("CHANNEL",l,d,mob,null,mob.getPersistentData().getInt(RaidMarch.GATES),dest(ch.destination),ch.reason,ch.gate);
    }
    /** Once a second for a raider that has a place at a gate. */
    static void advance(ServerLevel l,CampaignData d,Mob mob,Channel ch,long now){
        var gate=l.getEntity(ch.gate);
        if(!(gate instanceof RaiderGate)||!gate.isAlive()){cancel(l,d,mob,ch,"gate gone");return;}
        if(ch.phase==Phase.WAITING){if(hasRoom(channelingAt(ch.gate)))begin(l,d,mob,ch,now);return;}
        ring(l,mob);
        float health=mob.getHealth();
        if(hurt(ch.health,health)){
            int before=ch.delayTicks;ch.delayTicks=delayedBy(ch.delayTicks);ch.completeAt=completeAt(ch.startedAt,ch.delayTicks);
            RaidRescue.diagnose("INTERRUPT",l,d,mob,null,mob.getPersistentData().getInt(RaidMarch.GATES),dest(ch.destination),before==ch.delayTicks?"hurt (delay at its limit)":"hurt (+"+RaidMarch.DAMAGE_DELAY_SECONDS+"s)",ch.gate);
        }
        ch.health=health;
        if(telegraphDue(now,ch.completeAt)){
            if(!ch.telegraphed){
                if(!destinationOk(l,d,ch.destination)){ch.destination=RaidRescue.findDestination(l,d,mob);if(ch.destination==null){cancel(l,d,mob,ch,"no place");RaidRescue.noPlace(l,d,mob,now);return;}}
                ch.telegraphed=true;var at=Vec3.atBottomCenterOf(ch.destination);
                l.playSound(null,at.x,at.y,at.z,SoundEvents.PORTAL_AMBIENT,SoundSource.HOSTILE,1.2f,1.2f);
                RaidRescue.diagnose("TELEGRAPH",l,d,mob,null,mob.getPersistentData().getInt(RaidMarch.GATES),dest(ch.destination),ch.reason,ch.gate);
            }
            var at=Vec3.atBottomCenterOf(ch.destination);
            l.sendParticles(ParticleTypes.PORTAL,at.x,at.y+1,at.z,40,.5,1,.5,.6);l.sendParticles(ParticleTypes.REVERSE_PORTAL,at.x,at.y+.2,at.z,12,.4,.1,.4,.05);
        }
        if(now>=ch.completeAt)complete(l,d,mob,ch,now);
    }
    /** The destination still passes every rule, and nobody has walked within 24 blocks of it. */
    private static boolean destinationOk(ServerLevel l,CampaignData d,BlockPos dest){
        return dest!=null&&RaidSpawns.farFromPlayers(Vec3.atBottomCenterOf(dest),RaidRescue.players(l),RaidSpawns.RESCUE_PLAYER_DISTANCE)
            &&RaidSpawns.lineProbe(l,d,dest)==TerrainProbe.Hazard.NONE&&RaidSpawns.safe(l,d,dest);
    }
    private static void complete(ServerLevel l,CampaignData d,Mob mob,Channel ch,long now){
        var dest=ch.destination;
        if(!destinationOk(l,d,dest)||!RaidRescue.fits(l,mob,dest)){dest=RaidRescue.findDestination(l,d,mob);}
        if(dest==null){cancel(l,d,mob,ch,"no place");RaidRescue.noPlace(l,d,mob,now);return;}
        var from=mob.position();
        l.sendParticles(ParticleTypes.PORTAL,from.x,from.y+1,from.z,50,.4,.8,.4,.8);l.playSound(null,from.x,from.y,from.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.HOSTILE,.8f,.7f);
        RaidRescue.diagnose("TELEPORT",l,d,mob,null,mob.getPersistentData().getInt(RaidMarch.GATES),dest.getX()+","+dest.getY()+","+dest.getZ(),ch.reason,ch.gate); // logged where the raider stood
        CHANNELS.remove(mob.getUUID());
        RaidRescue.relocate(mob,dest);
        var to=mob.position();l.sendParticles(ParticleTypes.REVERSE_PORTAL,to.x,to.y+1,to.z,50,.4,.8,.4,.1);l.playSound(null,to.x,to.y,to.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.HOSTILE,.8f,1.1f);
        RaidMarch.track(mob.getUUID(),RaidMarch.rescued(Math.sqrt(mob.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+1,d.beacon.getZ()+.5)),now));
    }
    private static void cancel(ServerLevel l,CampaignData d,Mob mob,Channel ch,String why){
        CHANNELS.remove(mob.getUUID());
        RaidRescue.diagnose("CANCEL",l,d,mob,null,mob.getPersistentData().getInt(RaidMarch.GATES),dest(ch.destination),why,ch.gate);
    }
    private static String dest(BlockPos p){return p==null?"-":p.getX()+","+p.getY()+","+p.getZ();}
    /** A small ring of red particles on the channeler (the vanilla particle packet; nothing of ours). */
    private static void ring(ServerLevel l,Mob mob){
        var red=new DustParticleOptions(new Vector3f(1f,.1f,.1f),1.1f);double r=Math.max(.6,mob.getBbWidth()*.7),y=mob.getY()+.15;
        for(int i=0;i<12;i++){double a=i*Math.PI/6;l.sendParticles(red,mob.getX()+Math.cos(a)*r,y,mob.getZ()+Math.sin(a)*r,1,0,0,0,0);}
    }

    /**
     * Once a cycle after the raiders have been looked at: drop the places of raiders that died, were withdrawn or are gone ("death cancels it"),
     * and take away every gate nobody is assigned to.
     */
    static void cleanup(ServerLevel l,CampaignData d){
        if(CHANNELS.isEmpty()&&KNOWN.isEmpty())return;
        for(var it=CHANNELS.entrySet().iterator();it.hasNext();){
            var e=it.next();var entity=l.getEntity(e.getKey());
            if(d.raiders.contains(e.getKey())&&entity instanceof Mob mob&&mob.isAlive())continue;
            if(e.getValue().phase==Phase.CHANNELING&&entity instanceof Mob m)RaidRescue.diagnose("CANCEL",l,d,m,null,m.getPersistentData().getInt(RaidMarch.GATES),dest(e.getValue().destination),"raider died or left the raid",e.getValue().gate);
            it.remove();
        }
        for(var gate:gates(l)){
            var id=gate.getUUID();boolean used=false;for(var c:CHANNELS.values())if(c.gate.equals(id)){used=true;break;}
            if(!used){gate.discard();KNOWN.remove(id);}
        }
        KNOWN.removeIf(id->l.getEntity(id)==null);
    }

    /**
     * The raider cannot move while it channels: the goal outranks everything else (priority -1) and takes MOVE, LOOK and JUMP. It does not switch the AI
     * off ({@code setNoAi} belongs to the defeated Special Forces soldiers); the mob keeps its targets and still defends itself.
     */
    static final class ChannelGoal extends Goal {
        private final Mob mob;
        ChannelGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
        @Override public boolean canUse(){return channeling(mob.getUUID());}
        @Override public boolean canContinueToUse(){return channeling(mob.getUUID());}
        @Override public boolean isInterruptable(){return false;}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){hold();}
        @Override public void tick(){
            hold();var ch=CHANNELS.get(mob.getUUID());
            if(ch!=null)mob.getLookControl().setLookAt(ch.gatePos.x,ch.gatePos.y+1,ch.gatePos.z);
        }
        /** Cancels the path and any move a goal asked for before the channel started (the move control keeps walking to its last wanted spot). */
        private void hold(){
            mob.getNavigation().stop();mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
            mob.setZza(0);mob.setXxa(0);mob.setYya(0);mob.setJumping(false);
        }
    }
}
