package dev.eternaldefense.beacon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** A real attackable objective lets native melee, arrows, crossbows and TaCZ bullets hit the beacon. */
final class BeaconCombat {
    static final int AMBIENT_RANGE=24;
    /** During a raid cover no longer hides the beacon: an ordinary hostile this many blocks past the zone's edge */
    static final int RAID_ATTRACT_BEYOND=48;
    /** Pure: does a hostile go for the beacon? */
    static boolean attracted(boolean raider,boolean raid,double distance,int zoneRadius,boolean seen){
        if(raider)return true;
        return raid?distance<=zoneRadius+RAID_ATTRACT_BEYOND:distance<=AMBIENT_RANGE&&seen;
    }
    private static final Map<ServerLevel,Objective> objectives=new WeakHashMap<>();
    private static final Set<Mob> attached=Collections.newSetFromMap(new WeakHashMap<>());
    static Objective objective(ServerLevel level){return objectives.get(level);}
    @SubscribeEvent public void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){objectives.clear();attached.clear();}
    static boolean enabled(ServerLevel l,CampaignData d){return d.installed()&&d.health>0&&(d.phase.equals("preparation")||d.phase.equals("raid")||d.phase.equals("snapshot"))&&l.hasChunkAt(d.beacon)&&l.getBlockState(d.beacon).is(EternalDefense.BEACON.get())&&!l.players().isEmpty();}
    static void tick(ServerLevel l,CampaignData d){
        if(l.getGameTime()%20==0)DefenseStructures.syncBeacon(l,d);
        if(!enabled(l,d)){var old=objectives.remove(l);if(old!=null)old.discard();return;}
        Objective target=objectives.get(l);
        if(target==null||!target.isAlive()||target.serial!=d.campaignSerial||!target.blockPosition().equals(d.beacon)){
            if(target!=null)target.discard();target=EternalDefense.OBJECTIVE.get().create(l);if(target==null)return;
            target.serial=d.campaignSerial;target.refreshDimensions();target.moveTo(d.beacon.getX()+.5,d.beacon.getY(),d.beacon.getZ()+.5,0,0);l.addFreshEntity(target);objectives.put(l,target);
        }
        target.setMark(DefenseStructures.actualMark(l,d.beacon));target.raid=d.phase.equals("raid");target.zoneRadius=d.radius();target.setPos(d.beacon.getX()+.5,d.beacon.getY(),d.beacon.getZ()+.5);
        if(l.getGameTime()%20==0){int range=d.radius()+96;for(Mob mob:l.getEntitiesOfClass(Mob.class,new AABB(d.beacon).inflate(range),BeaconCombat::hostile)){attach(mob);if(d.phase.equals("raid")&&l.getGameTime()%40==0&&(!mob.getPersistentData().getBoolean("eternalRaider")&&mob.getTarget()!=null))RaidDigging.think(l,d,mob,mob.horizontalCollision||mob.getNavigation().isDone()||!mob.hasLineOfSight(mob.getTarget()));}}
    }
    static boolean hostile(Mob mob){return !SpecialForcesRaids.defeated(mob)&&!(mob instanceof Objective)&&(mob.getType().getCategory()==MobCategory.MONSTER||mob instanceof Monster||mob.getPersistentData().getBoolean("eternalRaider"));}
    @SubscribeEvent public void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide)return;
        if(event.getEntity() instanceof Mob mob&&hostile(mob))attach(mob);
        else if(event.getEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow&&arrow.getOwner() instanceof Mob archer&&archer.getTarget() instanceof RaidTargets.Point point)aim(arrow,point);
    }
    /** An arrow loosed at a defense flies straight at it: the block does not move, so the archer's usual scatter is left out */
    static void aim(net.minecraft.world.entity.projectile.AbstractArrow arrow,RaidTargets.Point point){
        var at=Vec3.atCenterOf(point.block);double dx=at.x-arrow.getX(),dy=at.y-arrow.getY(),dz=at.z-arrow.getZ(),flat=Math.sqrt(dx*dx+dz*dz);
        float speed=(float)Math.max(1.6,arrow.getDeltaMovement().length());
        arrow.shoot(dx,dy+lift(flat,dy,speed),dz,speed,0);
    }
    /** Pure: how much higher than the target to point an arrow so that it comes down on it (arrows slow by 1% and fall 0.05 a tick) */
    static double lift(double flat,double dy,double speed){
        double lo=-flat,hi=flat*2+4;
        for(int i=0;i<30;i++){double mid=(lo+hi)/2;if(heightAt(flat,dy+mid,speed)>dy)hi=mid;else lo=mid;}
        return (lo+hi)/2;
    }
    /** Pure: an arrow's height when it has flown flat blocks, shot along (flat, up) at this speed */
    static double heightAt(double flat,double up,double speed){
        double len=Math.sqrt(flat*flat+up*up);if(len<1e-9)return 0;double vx=flat/len*speed,vy=up/len*speed,x=0,y=0;
        for(int t=0;t<400;t++){double nx=x+vx,ny=y+vy;if(nx>=flat)return y+(ny-y)*(flat-x)/Math.max(1e-9,nx-x);x=nx;y=ny;vx*=.99;vy=vy*.99-.05;}
        return Double.NEGATIVE_INFINITY;
    }
    @SubscribeEvent public void projectile(net.minecraftforge.event.entity.ProjectileImpactEvent event){if(event.getRayTraceResult() instanceof BlockHitResult hit)blockImpact(event.getProjectile(),hit);}
    /** TaCZ traces physical frames before entities; those real impacts hit the same bounded objective. */
    @SubscribeEvent public void gunBlockImpact(com.tacz.guns.api.event.server.AmmoHitBlockEvent event){
        if(event.getClass()!=com.tacz.guns.api.event.server.AmmoHitBlockEvent.class)return; // TaCZ's own event only, as before (not a subclass)
        blockImpact(event.getAmmo(),event.getHitResult());
    }
    static void blockImpact(net.minecraft.world.entity.projectile.Projectile shot,BlockHitResult hit){
        if(!(shot.level() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD||!(shot.getOwner() instanceof Mob attacker)||!hostile(attacker))return;
        var d=CampaignData.get(l);
        if(!DefenseStructures.beacon(l,hit.getBlockPos())){
            // A raider's arrow or bullet on a defense or a closed door wears it down
            var state=l.getBlockState(hit.getBlockPos());
            if(d.phase.equals("raid")&&RaidTargets.target(l.getBlockState(RaidTargets.base(state,hit.getBlockPos()))))RaidDigging.hit(l,d,RaidTargets.base(state,hit.getBlockPos()),attacker,RaidDigging.SHOT,true);
            return;
        }
        var target=objectives.get(l);if(target==null||!DefenseStructures.anchor(l,hit.getBlockPos()).equals(d.beacon))return;
        target.hurt(l.damageSources().indirectMagic(shot,attacker),1);
    }
    /** Whether a mob got the beacon goals (for the tests: a hunt warband never does). */
    static boolean isAttached(Mob mob){return attached.contains(mob);}
    /** A boss (the bosses tag, or 150+ default health) that is not one of the raid's own mobs */
    static boolean bossOutsideRaid(Mob mob){return !mob.getPersistentData().getBoolean("eternalRaider")&&(mob.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)||ArdentEnergy.defaultHealth(mob)>=ArdentEnergy.BOSS_BASE);}
    private static final Set<Mob> RANGED=Collections.newSetFromMap(new WeakHashMap<>());
    /** Was given a ranged attack on the beacon (archers, gunners, blazes) */
    static boolean ranged(Mob mob){return RANGED.contains(mob);}
    static void attach(Mob mob){
        if(HuntWarband.hunter(mob))return;   // A hunt warband fights the team where it stands, never the beacon
        if(bossOutsideRaid(mob))return;   // Another mod's boss keeps its own fight
        if(!attached.add(mob))return;
        mob.targetSelector.addGoal(0,new TargetBeacon(mob));
        if(RaidTypes.glider(mob))return; // Vexes and phantoms are steered straight at their target by RaidTypes
        mob.goalSelector.addGoal(-1,new RaiderGates.ChannelGoal(mob)); // Outranks everything: a raider channeling at a gate cannot move
        mob.goalSelector.addGoal(-1,new RaidDigging.WorkGoal(mob));   // Digging and bridging, until a gate channel stops it
        Goal nativeRanged=mob.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(g->{String name=g.getClass().getName();return name.endsWith(".GunAttackGoal")||name.endsWith("$GuardianAttackGoal")||name.endsWith("$BlazeAttackGoal")||name.endsWith("$GhastShootFireballGoal");}).findFirst().orElse(null);
        if(nativeRanged!=null)mob.goalSelector.addGoal(0,new RangedBeacon(mob,nativeRanged));
        // A bow or crossbow attack never starts without the weapon
        else if(mob instanceof AbstractSkeleton skeleton&&skeleton.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem)mob.goalSelector.addGoal(0,new RangedBeacon(mob,new RangedBowAttackGoal<>(skeleton,1,40,10)));
        else if(mob instanceof Pillager pillager&&pillager.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem)mob.goalSelector.addGoal(0,new RangedBeacon(mob,new RangedCrossbowAttackGoal<>(pillager,1,10)));
        else if(mob instanceof AbstractSkeleton||mob instanceof Pillager)mob.goalSelector.addGoal(0,new MeleeBeacon(mob));
        else if(mob instanceof RangedAttackMob ranged)mob.goalSelector.addGoal(0,new RangedBeacon(mob,new RangedAttackGoal(ranged,1,60,10)));
        else mob.goalSelector.addGoal(0,new MeleeBeacon(mob));
        if(mob.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof RangedBeacon))RANGED.add(mob);
    }
    static boolean inDetectionRange(Mob mob,Objective target){
        if(SpecialForcesRaids.defeated(mob))return false;
        boolean raider=mob.getPersistentData().getBoolean("eternalRaider");double distance=Math.sqrt(mob.distanceToSqr(target));
        // The sight check (a ray cast) only when it can matter: between raids and within reach
        return attracted(raider,target.raid,distance,target.zoneRadius,!raider&&!target.raid&&distance<=AMBIENT_RANGE&&mob.hasLineOfSight(target));
    }
    /** It cannot hit what it is after from where it stands: not in the ring around it (melee), or out of range or sight (ranged) */
    static boolean needsBreach(Mob mob,CampaignData d){
        var target=mob.getTarget();
        if(target==null||!target.isAlive())return mob.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+1,d.beacon.getZ()+.5)>9;
        if(ranged(mob))return mob.distanceToSqr(target)>196||!mob.hasLineOfSight(target);
        return !RaidDigging.inRing(mob.getBoundingBox(),target.getBoundingBox());
    }
    /** What the beacon goals act on: the beacon, a defense's stand-in, or a defense entity (players are left to the mob's own attack) */
    static boolean assault(net.minecraft.world.entity.LivingEntity t){return t!=null&&t.isAlive()&&(t instanceof Objective||t instanceof RaidTargets.Point||RaidTargets.defenseEntity(t));}
    static final class TargetBeacon extends Goal {
        final Mob mob;net.minecraft.world.entity.LivingEntity target;
        TargetBeacon(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.TARGET));}
        @Override public boolean canUse(){
            if(!(mob.level() instanceof ServerLevel l))return false;var objective=objectives.get(l);
            if(objective==null||!objective.isAlive()||!inDetectionRange(mob,objective)){target=null;return false;}
            if(objective.raid){target=RaidTargets.choose(l,CampaignData.get(l),mob,objective);return target!=null;}
            // Between raids close defenders can intercept attackers; a distant player cannot lure them away.
            target=l.getNearestPlayer(mob.getX(),mob.getY(),mob.getZ(),5,e->e instanceof net.minecraft.world.entity.player.Player p&&RaidTargets.attackable(p))==null?objective:null;
            return target!=null;
        }
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void start(){mob.setTarget(target);}
        @Override public void tick(){if(mob.getTarget()!=target)mob.setTarget(target);}
        @Override public void stop(){var t=mob.getTarget();if(t instanceof Objective||t instanceof RaidTargets.Point)mob.setTarget(null);}
    }
    static boolean approach(Mob mob,double x,double y,double z,double speed){
        if(steerChute(mob,x,z))return true;
        var w=waypoint(mob,x,y,z);if(w==null)return false;
        if(mob.getNavigation().moveTo(w[0],w[1],w[2],speed)&&!mob.getNavigation().isDone())return true;
        // No usable path (walls, water, rock): head straight for the spot anyway
        push(mob,w,speed);return true;
    }
    /** Straight toward the spot with no path search (between searches, while the mob has no path). */
    static boolean step(Mob mob,double x,double y,double z,double speed){if(steerChute(mob,x,z))return true;var w=waypoint(mob,x,y,z);if(w==null)return false;push(mob,w,speed);return true;}
    /** Under a parachute a raider steers straight at its target: a ground path from up there leads anywhere */
    static boolean steerChute(Mob mob,double x,double z){
        if(mob.onGround()||!mob.getPersistentData().getBoolean("eternalChute"))return false;
        mob.getNavigation().stop();double dx=x-mob.getX(),dz=z-mob.getZ();
        // Aimed a little below it (a point above makes the move control "jump" and keep its old heading); still once over the target
        if(dx*dx+dz*dz<.25)mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY()-1,mob.getZ(),0);else mob.getMoveControl().setWantedPosition(x,mob.getY()-1,z,1);
        return true;
    }
    private static void push(Mob mob,double[] w,double speed){RaidMarch.forcedMove(mob,mob.level().getGameTime());mob.getMoveControl().setWantedPosition(w[0],w[1],w[2],speed);}
    /** The spot itself, or a point 16 blocks toward it on the ground when it is far (paths stay short) */
    private static double[] waypoint(Mob mob,double x,double y,double z){
        double dx=x-mob.getX(),dz=z-mob.getZ(),distance=Math.sqrt(dx*dx+dz*dz);
        if(distance>24){x=mob.getX()+dx/distance*16;z=mob.getZ()+dz/distance*16;
            if(mob.level() instanceof ServerLevel l){var p=net.minecraft.core.BlockPos.containing(x,mob.getY(),z);if(!l.hasChunkAt(p))return null;var surface=l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p);y=Math.abs(surface.getY()-mob.getY())<=12?surface.getY():mob.getY();}}
        return new double[]{x,y,z};
    }
    static final class MeleeBeacon extends Goal {
        final Mob mob;int cooldown,swing;
        MeleeBeacon(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return !SpecialForcesRaids.defeated(mob)&&assault(mob.getTarget());}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){mob.setAggressive(true);}
        @Override public void stop(){mob.setAggressive(false);mob.getNavigation().stop();}
        @Override public void tick(){var target=mob.getTarget();if(target==null||!(mob.level() instanceof ServerLevel l))return;mob.getLookControl().setLookAt(target,30,30);
            if(!RaidDigging.inRing(mob.getBoundingBox(),target.getBoundingBox())){
                // A path search every 10 ticks; in between, a mob without a path keeps walking straight
                if(mob.tickCount%5==0&&RaidDigging.bridgeAhead(l,CampaignData.get(l),mob))return;   // Water or a chasm coming up: stop at the edge and bridge it
                if(mob.tickCount%10==0)approach(mob,target.getX(),target.getY(),target.getZ(),1.15);
                else if(mob.getNavigation().isDone())step(mob,target.getX(),target.getY(),target.getZ(),1.15);
                return;
            }
            mob.getNavigation().stop();
            if(target instanceof RaidTargets.Point point){RaidDigging.dig(mob,point.block,l.getGameTime());return;}   // The work goal hacks at it
            // Anywhere in the ring around it: the attack animation never stops while it hits
            if(--swing<=0&&!(mob instanceof Creeper)){swing=RaidDigging.SWING_TICKS;RaidDigging.swing(l,mob);}
            if(--cooldown<=0){cooldown=20;
                if(mob instanceof Creeper creeper)creeper.ignite(); // Creepers have no melee attack: they blow up at the beacon
                else mob.doHurtTarget(target);}
        }
    }
    static final class RangedBeacon extends Goal {
        final Mob mob;final Goal attack;boolean attacking;
        RangedBeacon(Mob mob,Goal attack){this.mob=mob;this.attack=attack;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return !SpecialForcesRaids.defeated(mob)&&assault(mob.getTarget());}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var target=mob.getTarget();if(target==null)return;
            if(target instanceof RaidTargets.Point point&&RaidDigging.reaches(mob.getBoundingBox(),new AABB(point.block))){RaidDigging.dig(mob,point.block,mob.level().getGameTime());return;}   // Up close it hacks at it
            boolean ready=attack.canUse();if(ready&&!attacking){attack.start();attacking=true;}if(!ready&&attacking){attack.stop();attacking=false;}
            if(attacking)attack.tick();mob.getLookControl().setLookAt(target,30,30);
            double dx=mob.getX()-target.getX(),dz=mob.getZ()-target.getZ(),distance=Math.sqrt(dx*dx+dz*dz);
            if(distance>=9&&distance<=11&&mob.hasLineOfSight(target)){mob.getNavigation().stop();mob.getMoveControl().strafe(0,0);}
            else if(mob.tickCount%10==0&&!steerChute(mob,target.getX(),target.getZ())){if(distance<.1){dx=1;dz=0;distance=1;}double x=target.getX()+dx/distance*10,z=target.getZ()+dz/distance*10;
                var surface=((ServerLevel)mob.level()).getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,net.minecraft.core.BlockPos.containing(x,mob.getY(),z));
                double y=Math.abs(surface.getY()-mob.getY())<=12?surface.getY():mob.getY();
                if(mob instanceof FlyingMob||mob instanceof Blaze)mob.getMoveControl().setWantedPosition(x,target.getY()+1,z,1.1);
                else approach(mob,x,y,z,1.1);
            }
        }
        @Override public void stop(){if(attacking)attack.stop();attacking=false;mob.getNavigation().stop();}
    }
    public static final class Objective extends Mob {
        int mark=1;
        void setMark(int value){if(mark!=value||getBbWidth()!=(value==4?3:1)||getBbHeight()!=(value==4?2:1)){mark=value;refreshDimensions();}}
        @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose){return net.minecraft.world.entity.EntityDimensions.scalable(mark==4?3:1,mark==4?2:1);}
        // Sight ends just above the installation, so its own frame cannot hide it.
        @Override protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose,net.minecraft.world.entity.EntityDimensions size){return size.height+.05f;}
        long serial;final Map<UUID,Long> lastHit=new HashMap<>();
        /** Set each tick from the campaign: a raid runs (cover no longer hides the beacon) and the zone's radius. */
        boolean raid;int zoneRadius;
        Objective(EntityType<? extends Mob> type,Level level){super(type,level);setNoAi(true);setNoGravity(true);setInvisible(true);noPhysics=true;}
        @Override public boolean shouldBeSaved(){return false;}
        @Override public boolean isPushable(){return false;}
        @Override public void push(Entity entity){}
        @Override public void travel(Vec3 movement){setDeltaMovement(Vec3.ZERO);}
        @Override public boolean hurt(DamageSource source,float amount){
            if(!(level() instanceof ServerLevel l)||!(source.getEntity() instanceof Mob attacker)||!hostile(attacker))return false;
            var d=CampaignData.get(l);if(serial!=d.campaignSerial||!enabled(l,d)||!inDetectionRange(attacker,this))return false;
            long now=l.getGameTime();boolean first=!d.underAttack(now);d.lastAttackTick=now;if(first)BeaconNetwork.syncNearby(l,d);if(now-lastHit.getOrDefault(attacker.getUUID(),now-60)<60)return true;
            lastHit.put(attacker.getUUID(),now);lastHit.entrySet().removeIf(e->now-e.getValue()>1200);
            int hit=Rules.contactDamage(attacker.getPersistentData().getBoolean("eternalBoss")?Math.min(5,d.raidTier+2):attacker.getPersistentData().getBoolean("eternalRaider")?d.raidTier:0,d.defense);if(RaidModifiers.on(d,"exposed_beacon"))hit=(int)Math.ceil(hit*RaidModifiers.EXPOSED_BEACON);d.health=Math.max(0,d.health-hit);d.setDirty();
            l.sendParticles(ParticleTypes.CRIT,getX(),getY()+.3,getZ(),5,.25,.25,.25,.1);
            if(d.health==0&&d.phase.equals("preparation")){d.phase="disabled";l.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("[Eternal Defense] The beacon has been disabled by nearby enemies. Repair it with reinforced plating."),false);BeaconNetwork.syncNearby(l,d);}
            return true;
        }
        @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect){return false;}
    }
}
