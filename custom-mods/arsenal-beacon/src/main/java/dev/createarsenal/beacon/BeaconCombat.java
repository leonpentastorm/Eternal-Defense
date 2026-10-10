package dev.createarsenal.beacon;

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
    private static final Map<ServerLevel,Objective> objectives=new WeakHashMap<>();
    private static final Set<Mob> attached=Collections.newSetFromMap(new WeakHashMap<>());
    static Objective objective(ServerLevel level){return objectives.get(level);}
    @SubscribeEvent public void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){objectives.clear();attached.clear();}
    static boolean enabled(ServerLevel l,CampaignData d){return d.installed()&&d.health>0&&(d.phase.equals("preparation")||d.phase.equals("raid")||d.phase.equals("snapshot"))&&l.hasChunkAt(d.beacon)&&l.getBlockState(d.beacon).is(ArsenalBeacon.BEACON.get())&&!l.players().isEmpty();}
    static void tick(ServerLevel l,CampaignData d){
        if(l.getGameTime()%20==0)ArsenalStructures.syncBeacon(l,d);
        if(!enabled(l,d)){var old=objectives.remove(l);if(old!=null)old.discard();return;}
        Objective target=objectives.get(l);
        if(target==null||!target.isAlive()||target.serial!=d.campaignSerial||!target.blockPosition().equals(d.beacon)){
            if(target!=null)target.discard();target=ArsenalBeacon.OBJECTIVE.get().create(l);if(target==null)return;
            target.serial=d.campaignSerial;target.refreshDimensions();target.moveTo(d.beacon.getX()+.5,d.beacon.getY(),d.beacon.getZ()+.5,0,0);l.addFreshEntity(target);objectives.put(l,target);
        }
        target.setMark(ArsenalStructures.actualMark(l,d.beacon));target.setPos(d.beacon.getX()+.5,d.beacon.getY(),d.beacon.getZ()+.5);
        if(l.getGameTime()%20==0){int range=d.radius()+96;for(Mob mob:l.getEntitiesOfClass(Mob.class,new AABB(d.beacon).inflate(range),BeaconCombat::hostile)){attach(mob);if(d.phase.equals("raid")&&l.getGameTime()%40==0&&(!mob.getPersistentData().getBoolean("arsenalRaider")&&mob.getTarget()!=null)&&(mob.horizontalCollision||mob.getNavigation().isDone()||mob.getTarget() instanceof Objective&&!mob.hasLineOfSight(mob.getTarget()))&&needsBreach(mob,d))RaidBreaching.breach(l,d,mob);}}
    }
    static boolean hostile(Mob mob){return !SpecialForcesRaids.defeated(mob)&&!(mob instanceof Objective)&&(mob.getType().getCategory()==MobCategory.MONSTER||mob instanceof Monster||mob.getPersistentData().getBoolean("arsenalRaider"));}
    @SubscribeEvent public void join(EntityJoinLevelEvent event){if(!event.getLevel().isClientSide&&event.getEntity() instanceof Mob mob&&hostile(mob))attach(mob);}
    @SubscribeEvent public void projectile(net.minecraftforge.event.entity.ProjectileImpactEvent event){if(event.getRayTraceResult() instanceof BlockHitResult hit)blockImpact(event.getProjectile(),hit);}
    /**
     * TaCZ traces physical frames before entities; those real impacts hit the same bounded objective. TaCZ is a required mod, so this listens to
     * its block-impact event itself: a listener on the base event class would be called for every event the game posts (every entity tick,
     * every render pass), only to throw almost all of them away.
     */
    @SubscribeEvent public void gunBlockImpact(com.tacz.guns.api.event.server.AmmoHitBlockEvent event){
        if(event.getClass()!=com.tacz.guns.api.event.server.AmmoHitBlockEvent.class)return; // TaCZ's own event only, as before (not a subclass)
        blockImpact(event.getAmmo(),event.getHitResult());
    }
    static void blockImpact(net.minecraft.world.entity.projectile.Projectile shot,BlockHitResult hit){
        if(!(shot.level() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD||!(shot.getOwner() instanceof Mob attacker)||!hostile(attacker)||!ArsenalStructures.beacon(l,hit.getBlockPos()))return;
        var d=CampaignData.get(l);var target=objectives.get(l);if(target==null||!ArsenalStructures.anchor(l,hit.getBlockPos()).equals(d.beacon))return;
        target.hurt(l.damageSources().indirectMagic(shot,attacker),1);
    }
    /** Whether a mob got the beacon goals (for the tests: a hunt warband never does). */
    static boolean isAttached(Mob mob){return attached.contains(mob);}
    static void attach(Mob mob){
        if(HuntWarband.hunter(mob))return;   // a hunt warband fights the team where it stands, never the beacon
        if(!attached.add(mob))return;
        mob.targetSelector.addGoal(0,new TargetBeacon(mob));
        if(RaidTypes.glider(mob))return; // vexes and phantoms are steered straight at their target by RaidTypes
        mob.goalSelector.addGoal(-1,new RaiderGates.ChannelGoal(mob)); // outranks everything: a raider channeling at a gate cannot move
        Goal nativeRanged=mob.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(g->{String name=g.getClass().getName();return name.endsWith(".GunAttackGoal")||name.endsWith("$GuardianAttackGoal")||name.endsWith("$BlazeAttackGoal")||name.endsWith("$GhastShootFireballGoal");}).findFirst().orElse(null);
        if(nativeRanged!=null)mob.goalSelector.addGoal(0,new RangedBeacon(mob,nativeRanged));
        else if(mob instanceof AbstractSkeleton skeleton)mob.goalSelector.addGoal(0,new RangedBeacon(mob,new RangedBowAttackGoal<>(skeleton,1,40,10)));
        else if(mob instanceof Pillager pillager)mob.goalSelector.addGoal(0,new RangedBeacon(mob,new RangedCrossbowAttackGoal<>(pillager,1,10)));
        else if(mob instanceof RangedAttackMob ranged)mob.goalSelector.addGoal(0,new RangedBeacon(mob,new RangedAttackGoal(ranged,1,60,10)));
        else mob.goalSelector.addGoal(0,new MeleeBeacon(mob));
    }
    static boolean inDetectionRange(Mob mob,Objective target){return !SpecialForcesRaids.defeated(mob)&&(mob.getPersistentData().getBoolean("arsenalRaider")||mob.distanceToSqr(target)<=AMBIENT_RANGE*AMBIENT_RANGE&&mob.hasLineOfSight(target));}
    static boolean needsBreach(Mob mob,CampaignData d){boolean ranged=mob.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof RangedBeacon);return mob.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+1,d.beacon.getZ()+.5)>(ranged?196:9)||ranged&&mob.getTarget() instanceof Objective target&&!mob.hasLineOfSight(target);}
    static final class TargetBeacon extends Goal {
        final Mob mob;Objective target;
        TargetBeacon(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.TARGET));}
        @Override public boolean canUse(){
            if(!(mob.level() instanceof ServerLevel l))return false;target=objectives.get(l);
            if(target==null||!target.isAlive()||!inDetectionRange(mob,target))return false;
            // Close defenders can intercept attackers; a distant player cannot lure the whole raid away.
            return l.getNearestPlayer(mob,5)==null;
        }
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void start(){mob.setTarget(target);}
        @Override public void tick(){mob.setTarget(target);}
        @Override public void stop(){if(mob.getTarget() instanceof Objective)mob.setTarget(null);}
    }
    static boolean approach(Mob mob,double x,double y,double z,double speed){
        double dx=x-mob.getX(),dz=z-mob.getZ(),distance=Math.sqrt(dx*dx+dz*dz);
        // Small intermediate paths keep distant reinforcements within native pathfinding limits.
        if(distance>24){x=mob.getX()+dx/distance*16;z=mob.getZ()+dz/distance*16;
            if(mob.level() instanceof ServerLevel l){var p=net.minecraft.core.BlockPos.containing(x,mob.getY(),z);if(!l.hasChunkAt(p))return false;var surface=l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p);y=Math.abs(surface.getY()-mob.getY())<=12?surface.getY():mob.getY();}}
        if(mob.getNavigation().moveTo(x,y,z,speed)&&!mob.getNavigation().isDone())return true;
        // No usable path (walls, water, rock): head straight for the spot anyway; RaidBreaching digs when the way is really shut.
        RaidMarch.forcedMove(mob,mob.level().getGameTime());mob.getMoveControl().setWantedPosition(x,y,z,speed);return true;
    }
    static final class MeleeBeacon extends Goal {
        final Mob mob;int cooldown;
        MeleeBeacon(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return !SpecialForcesRaids.defeated(mob)&&mob.getTarget() instanceof Objective target&&target.isAlive();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){mob.setAggressive(true);}
        @Override public void stop(){mob.setAggressive(false);mob.getNavigation().stop();}
        @Override public void tick(){var target=mob.getTarget();if(target==null)return;mob.getLookControl().setLookAt(target,30,30);
            double reach=Math.max(2.2,mob.getBbWidth()+target.getBbWidth());
            if(mob.distanceToSqr(target)>reach*reach||!mob.hasLineOfSight(target)){if(mob.tickCount%10==0||mob.getNavigation().isDone())approach(mob,target.getX(),target.getY(),target.getZ(),1.15);}
            else{mob.getNavigation().stop();if(--cooldown<=0){cooldown=20;
                if(mob instanceof Creeper creeper)creeper.ignite(); // creepers have no melee attack: they blow up at the beacon
                else{mob.swing(InteractionHand.MAIN_HAND);mob.doHurtTarget(target);}}}
        }
    }
    static final class RangedBeacon extends Goal {
        final Mob mob;final Goal attack;boolean attacking;
        RangedBeacon(Mob mob,Goal attack){this.mob=mob;this.attack=attack;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return !SpecialForcesRaids.defeated(mob)&&mob.getTarget() instanceof Objective target&&target.isAlive();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var target=mob.getTarget();if(target==null)return;
            boolean ready=attack.canUse();if(ready&&!attacking){attack.start();attacking=true;}if(!ready&&attacking){attack.stop();attacking=false;}
            if(attacking)attack.tick();mob.getLookControl().setLookAt(target,30,30);
            double dx=mob.getX()-target.getX(),dz=mob.getZ()-target.getZ(),distance=Math.sqrt(dx*dx+dz*dz);
            if(distance>=9&&distance<=11&&mob.hasLineOfSight(target)){mob.getNavigation().stop();mob.getMoveControl().strafe(0,0);}
            else if(mob.tickCount%10==0){if(distance<.1){dx=1;dz=0;distance=1;}double x=target.getX()+dx/distance*10,z=target.getZ()+dz/distance*10;
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
        // Solid cover placed over or around it still participates in vanilla sight checks.
        @Override protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose,net.minecraft.world.entity.EntityDimensions size){return size.height+.05f;}
        long serial;final Map<UUID,Long> lastHit=new HashMap<>();
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
            d.health=Math.max(0,d.health-Rules.contactDamage(attacker.getPersistentData().getBoolean("arsenalBoss")?Math.min(5,d.raidTier+2):attacker.getPersistentData().getBoolean("arsenalRaider")?d.raidTier:0,d.defense));d.setDirty();
            l.sendParticles(ParticleTypes.CRIT,getX(),getY()+.3,getZ(),5,.25,.25,.25,.1);
            if(d.health==0&&d.phase.equals("preparation")){d.phase="disabled";l.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("[Create Arsenal] The beacon has been disabled by nearby enemies. Repair it with reinforced plating."),false);BeaconNetwork.syncNearby(l,d);}
            return true;
        }
        @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect){return false;}
    }
}
