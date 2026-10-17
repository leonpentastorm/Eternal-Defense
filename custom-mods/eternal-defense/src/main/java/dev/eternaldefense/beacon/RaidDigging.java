package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Raiders break blocks the way a player does: they swing at a block and it cracks until it gives. */
final class RaidDigging {
    private RaidDigging(){}
    /** A swing every this many ticks; a crack nobody hits for FORGET_TICKS heals; a job is dropped after JOB_TICKS */
    static final int SWING_TICKS=10,FORGET_TICKS=160,JOB_TICKS=400,BRIDGE_JOB_TICKS=900;
    /** Share of the attack damage one swing does (two swings a second) */
    static final double SWING_SHARE=.5;
    /** The most damage any block can take, and what an arrow or bullet does to a defense */
    static final float HP_CAP=300,SHOT=3;

    // Pure rules
    /** Pure: damage a block takes before it breaks: 3 plus 6 per hardness point, at most HP_CAP; -1 when it never breaks */
    static float hp(float hardness){return hardness<0?-1:Math.min(HP_CAP,3+6*hardness);}
    /** Pure: one swing: half the attack damage (at least half a point), times the size of a grown mob */
    static float perSwing(double attack,float size){return (float)(Math.max(1,attack)*Math.max(1,size)*SWING_SHARE);}
    /** Pure: the crack shown, 0 to 9 */
    static int stage(float done,float hp){return hp<=0?-1:Math.max(0,Math.min(9,(int)(done/hp*10)));}
    /** Pure: the gap between two boxes along one axis (0 when they overlap) */
    static double gap(double aMin,double aMax,double bMin,double bMax){return Math.max(0,Math.max(aMin-bMax,bMin-aMax));}
    /** Pure: a block a raider can work on: at most a block away beside it, from below its feet to a block over its head */
    static boolean reaches(AABB mob,AABB block){
        return gap(mob.minX,mob.maxX,block.minX,block.maxX)<=1&&gap(mob.minZ,mob.maxZ,block.minZ,block.maxZ)<=1&&gap(mob.minY,mob.maxY,block.minY,block.maxY)<=1;
    }
    /** Pure: a melee raider attacks from anywhere in the ring one block around its target: beside it (a step lower at most) or on top of it */
    static boolean inRing(AABB mob,AABB target){
        return gap(mob.minX,mob.maxX,target.minX,target.maxX)<=1&&gap(mob.minZ,mob.maxZ,target.minZ,target.maxZ)<=1&&mob.minY>=target.minY-1.05&&mob.minY<=target.maxY+.05;
    }
    /** Pure: a breaker id for the crack overlay that no entity has (entity ids are never negative) */
    static int crackId(long pos){return -1-Math.floorMod(pos*0x9E3779B97F4A7C15L>>>17,1<<30);}

    // Cracks: damage done to one block so far, by everybody
    static final class Crack{float done;long last;final int id;Crack(int id){this.id=id;}}
    private static final Map<Long,Crack> CRACKS=new HashMap<>();
    static float done(BlockPos pos){var c=CRACKS.get(pos.asLong());return c==null?0:c.done;}
    /** One blow on a block (a swing, a shot, a hit on a defense); true when it broke */
    static boolean hit(ServerLevel l,CampaignData d,BlockPos pos,Mob by,float amount){return hit(l,d,pos,by,amount,false);}
    /** A blow (ranged: a shot) on a block: sandbags shrug off shots, the barricade blows, barbed wire bites back */
    static boolean hit(ServerLevel l,CampaignData d,BlockPos pos,Mob by,float amount,boolean ranged){
        if(!d.phase.equals("raid")||!RaidBreaching.breakable(l,d,pos))return false;
        if(d.inside(pos)&&!d.snapshot.containsKey(DefenseStructures.anchor(l,pos).asLong()))return false;   // Only blocks of the saved base can be put back
        var state=l.getBlockState(pos);float hp=hp(state.getDestroySpeed(l,pos));if(hp<0)return false;
        amount*=DefenseWalls.through(state,ranged);
        if(!ranged&&by!=null&&state.getBlock() instanceof DefenseWalls.BarbedWire)DefenseWalls.cut(l,by,state.getValue(DefenseWalls.BarbedWire.POWERED));
        long now=l.getGameTime();
        var crack=CRACKS.computeIfAbsent(pos.asLong(),k->new Crack(crackId(k)));crack.done+=Math.max(0,amount);crack.last=now;
        if(by!=null){RaidMarch.breached(by,now);RaidTargets.worked(by,now);}
        if(crack.done>=hp){
            CRACKS.remove(pos.asLong());l.destroyBlockProgress(crack.id,pos,-1);
            breakBlock(l,d,pos,state,by);RaidTargets.broken(l,pos);return true;
        }
        l.destroyBlockProgress(crack.id,pos,stage(crack.done,hp));
        var sound=state.getSoundType();l.playSound(null,pos,sound.getHitSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/4f,sound.getPitch()*.5f);
        return false;
    }
    /** The block gives: inside the zone it is saved to be put back, outside it drops like a mined block. */
    static void breakBlock(ServerLevel l,CampaignData d,BlockPos pos,BlockState state,Mob by){
        if(d.inside(pos)){l.levelEvent(2001,pos,Block.getId(state));EternalDefense.damageBlock(l,d,pos);}
        else l.destroyBlock(pos,true,by);
    }
    /** Once a second: cracks nobody hit lately heal. */
    static void tick(ServerLevel l){
        if(CRACKS.isEmpty())return;long now=l.getGameTime();
        for(var it=CRACKS.entrySet().iterator();it.hasNext();){var e=it.next();if(now-e.getValue().last>FORGET_TICKS||now<e.getValue().last){l.destroyBlockProgress(e.getValue().id,BlockPos.of(e.getKey()),-1);it.remove();}}
    }
    static int cracks(){return CRACKS.size();}

    // Jobs: the block a raider is digging, or the bridge it is laying
    static final class Job {
        final BlockPos dig;final RaidBridges.Line bridge;final long started;long lastSwing=Long.MIN_VALUE,moved;int step=1,laid,target;double best=Double.MAX_VALUE;
        Job(BlockPos dig,RaidBridges.Line bridge,long started){this.dig=dig;this.bridge=bridge;this.started=started;this.moved=started;}
    }
    private static final Map<Mob,Job> JOBS=new WeakHashMap<>();
    static Job job(Mob mob){return JOBS.get(mob);}
    static void dig(Mob mob,BlockPos pos,long now){var j=JOBS.get(mob);if(j!=null&&j.bridge==null&&pos.equals(j.dig))return;JOBS.put(mob,new Job(pos.immutable(),null,now));}
    static void bridge(Mob mob,RaidBridges.Line line,long now){JOBS.put(mob,new Job(null,line,now));}
    static void stop(Mob mob){JOBS.remove(mob);}
    /** A raid starts or ends: every crack heals and every job stops. */
    static void reset(ServerLevel l){for(var e:CRACKS.entrySet())l.destroyBlockProgress(e.getValue().id,BlockPos.of(e.getKey()),-1);reset();}
    static void reset(){CRACKS.clear();JOBS.clear();RaidBridges.forget();}

    /** The attack animation, for the mobs whose arms do not show a swing */
    static void swing(ServerLevel l,Mob mob){
        mob.swing(InteractionHand.MAIN_HAND);
        if(mob instanceof Ravager||mob instanceof IronGolem||mob instanceof Hoglin||mob instanceof Zoglin)l.broadcastEntityEvent(mob,(byte)4);
    }
    static double attack(Mob mob){var a=mob.getAttribute(Attributes.ATTACK_DAMAGE);return a==null?2:a.getValue();}

    /** Once a second for each raider: lay a bridge, break the defense or door it came for, or dig through what blocks it. */
    static void think(ServerLevel l,CampaignData d,Mob mob,boolean idle){
        if(RaidTypes.flyer(mob)||RaidTypes.glider(mob)||mob.getPersistentData().getBoolean("eternalChute")||RaiderGates.channel(mob.getUUID())!=null||SpecialForcesRaids.defeated(mob))return;
        if(JOBS.containsKey(mob))return;
        long now=l.getGameTime();
        if(mob.getTarget() instanceof RaidTargets.Point p&&reaches(mob.getBoundingBox(),new AABB(p.block))){dig(mob,p.block,now);return;}
        if(bridgeAhead(l,d,mob))return;
        if(idle&&BeaconCombat.needsBreach(mob,d)){var p=RaidBreaching.choose(l,d,mob);if(p!=null&&reaches(mob.getBoundingBox(),new AABB(p)))dig(mob,p,now);}
    }

    /** Water, lava or a chasm coming up: a bridge job (checked often while marching, so a walking raider stops at the edge) */
    static boolean bridgeAhead(ServerLevel l,CampaignData d,Mob mob){
        if(JOBS.containsKey(mob)||!d.phase.equals("raid")||RaidTypes.flyer(mob)||RaidTypes.glider(mob)||mob.getPersistentData().getBoolean("eternalChute")||RaiderGates.channel(mob.getUUID())!=null)return false;
        var line=RaidBridges.plan(l,d,mob);if(line==null)return false;
        bridge(mob,line,l.getGameTime());return true;
    }
    /** Works on the raider's job every tick; outranks its other goals while it has one (a gate channel stops it first) */
    static final class WorkGoal extends Goal {
        private final Mob mob;
        WorkGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
        @Override public boolean canUse(){return JOBS.containsKey(mob)&&!RaiderGates.channeling(mob.getUUID())&&!SpecialForcesRaids.defeated(mob);}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){mob.getNavigation().stop();mob.setAggressive(true);}
        @Override public void stop(){mob.setAggressive(false);}
        @Override public void tick(){
            var job=JOBS.get(mob);if(job==null||!(mob.level() instanceof ServerLevel l))return;
            var d=CampaignData.get(l);long now=l.getGameTime();
            if(!d.phase.equals("raid")||now-job.started>(job.bridge!=null?BRIDGE_JOB_TICKS:JOB_TICKS)||now<job.started){JOBS.remove(mob);return;}
            if(job.bridge!=null){if(!RaidBridges.work(l,d,mob,job,now))JOBS.remove(mob);return;}
            var pos=job.dig;
            if(!RaidBreaching.breakable(l,d,pos)||!reaches(mob.getBoundingBox(),new AABB(pos))){JOBS.remove(mob);return;}
            mob.getNavigation().stop();mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
            mob.getLookControl().setLookAt(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            if(job.lastSwing!=Long.MIN_VALUE&&now-job.lastSwing<SWING_TICKS)return;
            job.lastSwing=now;
            if(mob instanceof Creeper creeper){
                // Creepers have nothing to swing: near the base they blow the block up
                if(Math.max(Math.abs(pos.getX()-d.beacon.getX()),Math.abs(pos.getZ()-d.beacon.getZ()))<=d.radius()+RaidMarch.DIG_RANGE){creeper.ignite();JOBS.remove(mob);return;}
            }else swing(l,mob);
            if(hit(l,d,pos,mob,perSwing(attack(mob),Elites.size(mob))))JOBS.remove(mob);
        }
    }
}
