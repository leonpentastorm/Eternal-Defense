package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** What a raider goes for: the closest defense or closed door in its way, then the beacon (melee) or a player (ranged). */
final class RaidTargets {
    private RaidTargets(){}
    /** The defense tags, eternal_defense:defenses for blocks (sandbags, trap towers and the like) and for entities (turrets) */
    static final class Tags {
        static final TagKey<Block> BLOCKS=TagKey.create(Registries.BLOCK,new ResourceLocation(EternalDefense.ID,"defenses"));
        static final TagKey<EntityType<?>> ENTITIES=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation(EternalDefense.ID,"defenses"));
    }
    /** A defense is in a raider's way within this many blocks, no farther from the beacon than the raider plus the slack, at most this much higher or lower */
    static final double SIGHT=16,AHEAD_SLACK=4,MAX_RISE=6;
    /** A melee raider turns on a player this close (and this much higher or lower at most), and lets go beyond the leash */
    static final double PLAYER_CLOSE=4,PLAYER_LEASH=10,MELEE_RISE=3;
    /** A ranged raider focuses a player it can see this close, and keeps on them within the leash while it saw them lately */
    static final double FOCUS_RANGE=32,FOCUS_LEASH=40;
    static final int FOCUS_MEMORY=200;
    /** A raider that has not hurt its defense for this long gives up on it for a while */
    static final int GIVE_UP=400,FORGET=1200;
    /** The choice is made again this often (ticks) */
    static final int CHOICE_TICKS=10;

    // Pure rules
    /** Pure: a defense this far from the raider, at this distance from the beacon (the raider at its own), this much higher, is in the raider's way:
     *  closer to it than the beacon, not behind it, not far above or below. */
    static boolean inTheWay(double toRaider,double defenseToBeacon,double raiderToBeacon,double rise){
        return toRaider<=SIGHT&&toRaider<raiderToBeacon&&defenseToBeacon<=raiderToBeacon+AHEAD_SLACK&&Math.abs(rise)<=MAX_RISE;
    }
    /** Pure: a melee raider fights a player only up close, and keeps on them within the leash. */
    static boolean meleeEngages(boolean attackable,double distance,double rise,boolean already){
        return attackable&&Math.abs(rise)<=MELEE_RISE&&distance<=(already?PLAYER_LEASH:PLAYER_CLOSE);
    }
    /** Pure: a ranged raider focuses a player it has seen within FOCUS_MEMORY ticks. */
    static boolean rangedFocus(boolean attackable,double distance,long sinceSeen,boolean already){
        return attackable&&sinceSeen>=0&&sinceSeen<=FOCUS_MEMORY&&distance<=(already?FOCUS_LEASH:FOCUS_RANGE);
    }
    enum Pick{PLAYER,DEFENSE,BEACON}
    /** Pure: what a raider goes for. Melee: a close player, a defense in its way, the beacon. Ranged: a defense in its way, a player, the beacon. */
    static Pick order(boolean ranged,boolean player,boolean defense){
        if(ranged)return defense?Pick.DEFENSE:player?Pick.PLAYER:Pick.BEACON;
        return player?Pick.PLAYER:defense?Pick.DEFENSE:Pick.BEACON;
    }
    /** Pure: it has not hurt its defense since then. */
    static boolean givesUp(long since,long now){return now-since>GIVE_UP;}
    /** Raiders never go for players in Creative or Spectator mode. */
    static boolean attackable(Player p){return p!=null&&p.isAlive()&&!p.isCreative()&&!p.isSpectator();}

    // What counts
    static boolean defense(BlockState s){return s.is(Tags.BLOCKS);}
    /** A closed door (its lower half) or fence gate; trapdoors and piston doors are walls. */
    static boolean door(BlockState s){
        if(s.is(BlockTags.DOORS))return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER&&!open(s);
        return s.is(BlockTags.FENCE_GATES)&&!open(s);
    }
    private static boolean open(BlockState s){return s.hasProperty(BlockStateProperties.OPEN)&&s.getValue(BlockStateProperties.OPEN);}
    static boolean target(BlockState s){return defense(s)||door(s);}
    /** Kept in the index whether open or not (a door may close later) */
    static boolean indexed(BlockState s){
        return defense(s)||s.is(BlockTags.FENCE_GATES)||s.is(BlockTags.DOORS)&&s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER;
    }
    /** The block a hit on pos counts for (the lower half of a door) */
    static BlockPos base(BlockState s,BlockPos pos){
        return s.is(BlockTags.DOORS)&&s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
    }
    /** A turret or other defense that is an entity */
    static boolean defenseEntity(Entity e){
        return e instanceof LivingEntity&&e.isAlive()&&!(e instanceof Player)&&(e.getType().is(Tags.ENTITIES)||"tacz_turrets".equals(EntityType.getKey(e.getType()).getNamespace()));
    }

    // The defenses of the saved base
    private static long indexRaid=Long.MIN_VALUE;
    private static List<BlockPos> index=List.of();
    /** Defense blocks and doors of the zone, read from the raid's snapshot once per raid */
    static List<BlockPos> blocks(CampaignData d){
        long raid=d.campaignSerial*1_000_003L+d.raidsStarted;
        if(raid!=indexRaid){
            var found=new ArrayList<BlockPos>();for(var e:d.snapshot.entrySet())if(indexed(e.getValue()))found.add(BlockPos.of(e.getKey()));
            index=found;indexRaid=raid;
        }
        return index;
    }
    private static List<LivingEntity> turrets=List.of();
    private static long turretsAt=Long.MIN_VALUE;
    /** Defense entities in and around the zone, looked up once a second */
    static List<LivingEntity> turrets(ServerLevel l,CampaignData d){
        long now=l.getGameTime();
        if(now-turretsAt>=20||now<turretsAt){turretsAt=now;turrets=new ArrayList<>(l.getEntitiesOfClass(LivingEntity.class,new AABB(d.beacon).inflate(d.radius()+SIGHT,Math.max(d.below(),d.above())+MAX_RISE,d.radius()+SIGHT),RaidTargets::defenseEntity));}
        return turrets;
    }

    // Each raider's mind
    static final class Mind {
        BlockPos defense;LivingEntity turret;long since,worked;
        LivingEntity choice;long chosenAt=Long.MIN_VALUE;
        UUID player;long seen=Long.MIN_VALUE;
        final Map<Long,Long> skip=new HashMap<>();
    }
    private static final Map<Mob,Mind> MINDS=new WeakHashMap<>();
    static Mind mind(Mob mob){return MINDS.computeIfAbsent(mob,k->new Mind());}
    /** The raider just hurt its defense, or dug or bridged its way toward it. */
    static void worked(Mob mob,long now){var m=MINDS.get(mob);if(m!=null)m.worked=now;}

    /** The raider's target now (decided again every CHOICE_TICKS) */
    static LivingEntity choose(ServerLevel l,CampaignData d,Mob mob,BeaconCombat.Objective objective){
        var m=mind(mob);long now=l.getGameTime();
        if(m.choice!=null&&m.choice.isAlive()&&now-m.chosenAt<CHOICE_TICKS&&now>=m.chosenAt){if(m.choice instanceof Point p)p.wanted=now;return m.choice;}
        m.chosenAt=now;
        boolean ranged=BeaconCombat.ranged(mob);
        Player player=ranged?focus(l,mob,m,now):close(l,mob,m);
        Object defense=defense(l,d,mob,m,now);
        LivingEntity chosen=switch(order(ranged,player!=null,defense!=null)){
            case PLAYER->player;
            case DEFENSE->defense instanceof BlockPos pos?point(l,pos,now):(LivingEntity)defense;
            case BEACON->objective;
        };
        m.choice=chosen==null?objective:chosen;return m.choice;
    }
    /** Melee: the closest attackable player within reach, kept while within the leash */
    private static Player close(ServerLevel l,Mob mob,Mind m){
        var current=m.player==null?null:l.getPlayerByUUID(m.player);
        if(current!=null&&meleeEngages(attackable(current),mob.distanceTo(current),current.getY()-mob.getY(),true))return current;
        Player best=null;double bestDistance=Double.MAX_VALUE;
        for(var p:l.players()){double distance=mob.distanceTo(p);if(distance<bestDistance&&meleeEngages(attackable(p),distance,p.getY()-mob.getY(),false)){best=p;bestDistance=distance;}}
        m.player=best==null?null:best.getUUID();return best;
    }
    /** Ranged: the attackable player it saw last, else the closest one it can see */
    private static Player focus(ServerLevel l,Mob mob,Mind m,long now){
        var current=m.player==null?null:l.getPlayerByUUID(m.player);
        if(current!=null&&attackable(current)){
            double distance=mob.distanceTo(current);
            if(distance<=FOCUS_LEASH&&mob.hasLineOfSight(current))m.seen=now;
            if(rangedFocus(true,distance,now-m.seen,true))return current;
        }
        Player best=null;double bestDistance=Double.MAX_VALUE;
        for(var p:l.players()){double distance=mob.distanceTo(p);if(distance<bestDistance&&rangedFocus(attackable(p),distance,0,false)&&mob.hasLineOfSight(p)){best=p;bestDistance=distance;}}
        if(best!=null){m.player=best.getUUID();m.seen=now;}else m.player=null;
        return best;
    }
    /** The defense the raider is after (a block position or an entity), or null */
    private static Object defense(ServerLevel l,CampaignData d,Mob mob,Mind m,long now){
        if(m.defense!=null){
            if(target(l.getBlockState(m.defense))&&!givesUp(Math.max(m.since,m.worked),now))return m.defense;
            m.skip.put(m.defense.asLong(),now+FORGET);m.defense=null;
        }
        if(m.turret!=null){
            if(defenseEntity(m.turret)&&!givesUp(Math.max(m.since,m.worked),now))return m.turret;
            m.skip.put(-1L-m.turret.getId(),now+FORGET);m.turret=null;
        }
        m.skip.values().removeIf(until->until<=now);
        double bx=d.beacon.getX()+.5,by=d.beacon.getY()+.5,bz=d.beacon.getZ()+.5;
        double raiderToBeacon=Math.sqrt(mob.distanceToSqr(bx,by,bz));
        if(Math.max(Math.abs(mob.getX()-bx),Math.abs(mob.getZ()-bz))>d.radius()+SIGHT+2)return null;
        BlockPos bestBlock=null;LivingEntity bestTurret=null;double best=Double.MAX_VALUE;
        for(var pos:blocks(d)){
            double dx=pos.getX()+.5-mob.getX(),dy=pos.getY()+.5-mob.getY(),dz=pos.getZ()+.5-mob.getZ();
            if(Math.abs(dx)>SIGHT||Math.abs(dz)>SIGHT||Math.abs(dy)>SIGHT)continue;
            double toRaider=Math.sqrt(dx*dx+dy*dy+dz*dz);if(toRaider>=best)continue;
            double toBeacon=Math.sqrt(Vec3.atCenterOf(pos).distanceToSqr(bx,by,bz));
            if(!inTheWay(toRaider,toBeacon,raiderToBeacon,pos.getY()-mob.getY())||m.skip.containsKey(pos.asLong())||!l.hasChunkAt(pos)||!target(l.getBlockState(pos)))continue;
            bestBlock=pos;best=toRaider;
        }
        for(var turret:turrets(l,d)){
            double toRaider=mob.distanceTo(turret);if(toRaider>=best||!turret.isAlive())continue;
            if(!inTheWay(toRaider,Math.sqrt(turret.distanceToSqr(bx,by,bz)),raiderToBeacon,turret.getY()-mob.getY())||m.skip.containsKey(-1L-turret.getId()))continue;
            bestTurret=turret;bestBlock=null;best=toRaider;
        }
        if(bestTurret!=null){m.turret=bestTurret;m.since=now;return bestTurret;}
        if(bestBlock!=null){m.defense=bestBlock;m.since=now;return bestBlock;}
        return null;
    }

    // Stand-ins: what a raider aims at when it goes for a defense block or door
    private static final Map<Long,UUID> POINTS=new HashMap<>();
    static Point point(ServerLevel l,BlockPos pos,long now){
        var id=POINTS.get(pos.asLong());
        if(id!=null&&l.getEntity(id) instanceof Point p&&p.isAlive()){p.wanted=now;return p;}
        var p=EternalDefense.DEFENSE_POINT.get().create(l);if(p==null)return null;
        p.block=pos.immutable();p.wanted=now;p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        if(!l.addFreshEntity(p))return null;POINTS.put(pos.asLong(),p.getUUID());return p;
    }
    /** Once a second: stand-ins nobody wants any more, or whose block is gone, are taken away. */
    static void cleanup(ServerLevel l){
        if(POINTS.isEmpty())return;long now=l.getGameTime();
        for(var it=POINTS.entrySet().iterator();it.hasNext();){
            var e=it.next();var entity=l.getEntity(e.getValue());
            if(!(entity instanceof Point p)||!p.isAlive()||now-p.wanted>60||!target(l.getBlockState(p.block))){if(entity!=null)entity.discard();it.remove();}
        }
    }
    static int points(){return POINTS.size();}
    /** A defense block or door just broke: its stand-in goes at once, so its raiders choose again */
    static void broken(ServerLevel l,BlockPos pos){var id=POINTS.remove(pos.asLong());if(id!=null&&l.getEntity(id) instanceof Point p)p.discard();}
    /** A raid starts or ends: every stand-in and mind is dropped. */
    static void reset(ServerLevel l){for(var id:POINTS.values()){var e=l.getEntity(id);if(e!=null)e.discard();}reset();}
    static void reset(){POINTS.clear();MINDS.clear();indexRaid=Long.MIN_VALUE;index=List.of();turrets=List.of();turretsAt=Long.MIN_VALUE;}

    /** The invisible stand-in on a defense block or door: raiders aim at it, and their blows crack the block. */
    public static final class Point extends Mob {
        BlockPos block=BlockPos.ZERO;long wanted;
        Point(EntityType<? extends Mob> type,Level level){super(type,level);setNoAi(true);setNoGravity(true);setInvisible(true);setSilent(true);noPhysics=true;}
        @Override public boolean shouldBeSaved(){return false;}
        @Override public boolean removeWhenFarAway(double distance){return false;}
        @Override public boolean isInvisible(){return true;}
        @Override public boolean isPushable(){return false;}
        @Override public boolean isPickable(){return false;}
        @Override public void push(Entity entity){}
        @Override public void travel(Vec3 movement){setDeltaMovement(Vec3.ZERO);}
        // Sight ends just above the block, so the block itself cannot hide it.
        @Override protected float getStandingEyeHeight(Pose pose,EntityDimensions size){return size.height+.05f;}
        @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect){return false;}
        @Override public boolean isInvulnerableTo(DamageSource source){return !(source.getEntity() instanceof Mob attacker&&BeaconCombat.hostile(attacker));}
        @Override public boolean hurt(DamageSource source,float amount){
            if(!(level() instanceof ServerLevel l)||!(source.getEntity() instanceof Mob attacker)||!BeaconCombat.hostile(attacker)||source.is(DamageTypeTags.IS_EXPLOSION))return false;
            RaidDigging.hit(l,CampaignData.get(l),block,attacker,Math.max(1,amount));return true;
        }
    }
}
