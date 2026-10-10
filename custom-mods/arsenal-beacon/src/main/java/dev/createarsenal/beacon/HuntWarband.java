package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A mission's warband in the world (0.0.20). Nothing exists until a player comes within {@link TacticalRules#APPROACH} blocks of the
 * objective and its chunk is entity-ticking: then spots on open ground within {@link TacticalRules#SPAWN_RADIUS} blocks are searched (a
 * bounded number of tries, never loading a chunk) and the warband spawns over several ticks. Every mob is persistent, leashed to the
 * objective, immune to sunlight and tagged with the mission id and the warband's serial ({@link #TAG}, {@link #SERIAL}); like every enemy
 * the mod places, it comes out of a red gate ({@link RaiderGates#spawnGate}). It is never a raider
 * (no {@code arsenalRaider} tag, not in the raid's list) and never attacks the beacon. Kills count down; the last one pays (once).
 */
final class HuntWarband {
    private HuntWarband(){}
    /** The mission id a hunt mob belongs to, its warband's serial, and the marks for a counted death and for the Warlord's captain. */
    static final String TAG="arsenalHunt",SERIAL="arsenalHuntSerial",COUNTED="arsenalHuntCounted",CAPTAIN="arsenalHuntCaptain";
    /** Spot search: tries per tick and in all, the spots wanted, and how many mobs spawn per tick. */
    static final int TRIES_PER_TICK=8,MAX_TRIES=240,SPOTS=6,SPAWNS_PER_TICK=2;
    /** A mob never appears closer than this to a player. */
    static final int KEEP_FROM_PLAYERS=8;
    /** Seconds in a row the live count must stay short before lost mobs are spawned again (chunks load their mobs a moment after the ground). */
    static final int LOST_SECONDS=10;
    /** An engaged warband whose ground has no spot left waits this long before searching again. */
    static final int RETRY_TICKS=200;

    static boolean hunter(Entity e){return e.getPersistentData().contains(TAG);}

    // ---- transient state (one shared mission, so one warband at a time) --------------------------------------------------------------------
    private static final List<BlockPos> spots=new ArrayList<>();
    private static long key=Long.MIN_VALUE,retryAt;
    private static int tries,lostStreak;
    private static boolean searching;
    static void forget(){spots.clear();key=Long.MIN_VALUE;retryAt=0;tries=0;lostStreak=0;searching=false;}
    static boolean searching(){return searching;}

    static void tick(ServerLevel l,CampaignData d,HuntData h,long clock){
        var b=h.board;var m=b.mission;
        if(m==null||m.state==HuntBoard.State.CLEARED){if(key!=Long.MIN_VALUE)forget();return;}
        long now=m.offer.id*1_000_003L+m.serial;
        if(now!=key){forget();key=now;}
        if(m.state==HuntBoard.State.ACTIVE||m.pending()>0)approachAndSpawn(l,d,h,m,clock);
        if(b.mission!=m||!m.engaged()||clock%20!=0)return;
        // nobody near an engaged warband for too long: it scatters, the mission waits for the team to come back
        if(b.away(nobodyWithin(l,m.offer,TacticalRules.AWAY),20)){scatter(l,h,m);return;}
        h.setDirty();
        // mobs lost without dying (removed by another mod, converted) are spawned again, never written off
        if(m.pending()==0&&approached(l,m.offer)&&areaTicking(l,m.offer)){
            int alive=count(l,m);
            if(alive<m.expectedAlive()){if(++lostStreak>=LOST_SECONDS){lostStreak=0;int n=b.lost(alive);if(n>0){h.setDirty();log("{}: {} warband mobs were lost without dying; spawning them again",m.offer.codename,n);}}}
            else lostStreak=0;
        }
    }

    private static void approachAndSpawn(ServerLevel l,CampaignData d,HuntData h,HuntBoard.Mission m,long clock){
        var b=h.board;var o=m.offer;
        if(!searching&&spots.isEmpty()){
            if(clock%10!=0||clock<retryAt||!approached(l,o))return;
            searching=true;tries=0;
        }
        if(searching){
            for(int i=0;i<TRIES_PER_TICK&&tries<MAX_TRIES&&spots.size()<SPOTS;i++,tries++){
                var p=trySpot(l,o);
                if(p!=null&&spots.stream().noneMatch(s->s.distSqr(p)<9))spots.add(p);
            }
            if(spots.size()<SPOTS&&tries<MAX_TRIES)return;
            searching=false;
            if(spots.isEmpty()){
                if(m.state==HuntBoard.State.ACTIVE){log("{}: no open ground for the warband after {} tries; the offer is replaced",o.codename,tries);Hunts.unreachable(l,d,h);return;}
                retryAt=clock+RETRY_TICKS;return;
            }
            if(m.state==HuntBoard.State.ACTIVE){
                int online=l.getServer().getPlayerList().getPlayerCount();
                int total=TacticalRules.warband(o.kind,l.random.nextInt(1000),online)+(o.kind==TacticalRules.Kind.WARLORD?1:0);
                b.engage(total);h.setDirty();contact(l,h,m);
            }
        }
        if(!m.engaged())return;
        for(int n=0;n<SPAWNS_PER_TICK&&m.pending()>0&&!spots.isEmpty();n++){
            var spot=spots.get(l.random.nextInt(spots.size()));
            if(!l.isPositionEntityTicking(spot))return;   // the team left before it was all out: the rest comes when they are back
            boolean captain=o.kind==TacticalRules.Kind.WARLORD&&m.spawned==0;
            if(spawn(l,d,m,spot,captain)){b.spawnedOne();h.setDirty();var seen=l.getNearestPlayer(spot.getX()+.5,spot.getY(),spot.getZ()+.5,128,false);RaiderGates.spawnGate(l,spot,seen==null?null:seen.position());}else spots.remove(spot);
        }
        if(m.pending()==0)Hunts.sync(l,h,null);
    }

    // ---- spots and spawning --------------------------------------------------------------------------------------------------------------
    /** Someone is within {@link TacticalRules#APPROACH} blocks of the objective, and its chunk is entity-ticking. */
    static boolean approached(ServerLevel l,HuntBoard.Offer o){
        if(!l.isPositionEntityTicking(new BlockPos(o.x,0,o.z)))return false;
        for(var p:l.players())if(p.isAlive()&&!p.isSpectator()&&horizontal(p,o)<=TacticalRules.APPROACH)return true;
        return false;
    }
    static boolean nobodyWithin(ServerLevel l,HuntBoard.Offer o,int blocks){
        for(var p:l.players())if(p.isAlive()&&!p.isSpectator()&&horizontal(p,o)<=blocks)return false;
        return true;
    }
    static double horizontal(Entity e,HuntBoard.Offer o){return Math.hypot(e.getX()-(o.x+.5),e.getZ()-(o.z+.5));}
    /** The whole ground the warband can roam is entity-ticking (so every mob of it that exists is loaded). */
    static boolean areaTicking(ServerLevel l,HuntBoard.Offer o){
        int r=TacticalRules.LEASH+TacticalRules.SPAWN_RADIUS;
        for(int x=-r;x<=r;x+=16)for(int z=-r;z<=r;z+=16)if(!l.isPositionEntityTicking(new BlockPos(o.x+x,0,o.z+z)))return false;
        return true;
    }
    /** One random column near the objective: open, natural ground at the surface, away from players and anything built. Null if not. */
    static BlockPos trySpot(ServerLevel l,HuntBoard.Offer o){
        double angle=l.random.nextDouble()*Math.PI*2,r=4+l.random.nextDouble()*(TacticalRules.SPAWN_RADIUS-4);
        int x=o.x+(int)Math.round(Math.cos(angle)*r),z=o.z+(int)Math.round(Math.sin(angle)*r);
        var column=new BlockPos(x,0,z);
        if(!l.isPositionEntityTicking(column)||!l.getWorldBorder().isWithinBounds(column))return null;
        var p=new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
        if(l.getNearestPlayer(p.getX()+.5,p.getY(),p.getZ()+.5,KEEP_FROM_PLAYERS,false)!=null)return null;
        return RaidSpawns.environmentSafe(l,p)&&RaidSpawns.openGround(l,p)?p:null;
    }
    /** One mob of the warband: a raid role and type at the current reward tier, balanced by {@link RaidBalance}; the captain is twice as tough. */
    static boolean spawn(ServerLevel l,CampaignData d,HuntBoard.Mission m,BlockPos spot,boolean captain){
        int tier=Math.max(0,Math.min(10,d.rewardTier));
        String role=captain?"heavy":RaidBalance.role(tier,l.random.nextInt(100));
        EntityType<?> type=RaidBalance.type(l,tier,role);
        if(!(type.create(l) instanceof Mob mob))return false;
        mob.moveTo(spot.getX()+.5+(l.random.nextDouble()-.5)*2,spot.getY(),spot.getZ()+.5+(l.random.nextDouble()-.5)*2,l.random.nextFloat()*360,0);
        if(!l.noCollision(mob))mob.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,mob.getYRot(),0);
        if(!l.noCollision(mob))return false;
        // tagged before it joins the world, so the beacon's combat hook (BeaconCombat.join) already sees it is not a raider
        var n=mob.getPersistentData();n.putLong(TAG,m.offer.id);n.putLong(SERIAL,m.serial);n.putString("arsenalRole",role);if(captain)n.putBoolean(CAPTAIN,true);
        mob.setPersistenceRequired();
        mob.restrictTo(new BlockPos(m.offer.x,spot.getY(),m.offer.z),TacticalRules.LEASH);
        arm(mob);
        if(!l.addFreshEntity(mob))return false;
        RaidBalance.balance(mob,tier);
        if(captain)crown(mob,m.offer.codename,tier);
        mob.setHealth(mob.getMaxHealth());
        return true;
    }
    /** Ranged mobs get their weapon (no {@code finalizeSpawn}, which could add a jockey or random gear); nothing they hold is dropped. */
    static void arm(Mob mob){
        if(mob instanceof AbstractSkeleton)mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        else if(mob instanceof Pillager)mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.CROSSBOW));
        mob.setDropChance(EquipmentSlot.MAINHAND,0);mob.setDropChance(EquipmentSlot.OFFHAND,0);
    }
    /** The Warlord's captain: heavy, twice the health, glowing, named with the codename (no boss bar). */
    static void crown(Mob mob,String codename,int tier){
        var hp=mob.getAttribute(Attributes.MAX_HEALTH);
        if(hp!=null){hp.removeModifiers();hp.setBaseValue(RaidBalance.health("heavy",tier)*TacticalRules.CAPTAIN_HEALTH_FACTOR);}
        mob.setGlowingTag(true);mob.setCustomName(Component.literal(codename).withStyle(ChatFormatting.GOLD));mob.setCustomNameVisible(true);
    }

    // ---- the warband's end ---------------------------------------------------------------------------------------------------------------
    /** Live (loaded, not yet dead) mobs of the current warband. */
    static int count(ServerLevel l,HuntBoard.Mission m){
        int n=0;
        for(var e:l.getAllEntities())if(e.isAlive()&&hunter(e)){var t=e.getPersistentData();if(t.getLong(TAG)==m.offer.id&&t.getLong(SERIAL)==m.serial&&!t.getBoolean(COUNTED))n++;}
        return n;
    }
    /**
     * Removes every living loaded mob of a mission (any serial; one that is dying keeps its fall and drops). Mobs in unloaded chunks are
     * discarded when they load (stale, see {@link Events#joined}).
     */
    static int discard(ServerLevel l,long missionId){
        var gone=new ArrayList<Entity>();
        for(var e:l.getAllEntities())if(e.isAlive()&&hunter(e)&&e.getPersistentData().getLong(TAG)==missionId)gone.add(e);
        for(var e:gone)e.discard();
        return gone.size();
    }
    static void scatter(ServerLevel l,HuntData h,HuntBoard.Mission m){
        h.board.scatter();discard(l,m.offer.id);h.setDirty();forget();
        Hunts.sync(l,h,null);
        Hunts.announce(l,Component.translatable("gui.arsenal_beacon.tactical.scattered",m.offer.codename));
        log("{}: nobody within {} blocks for {} ticks; the warband scattered",m.offer.codename,TacticalRules.AWAY,TacticalRules.AWAY_TICKS);
    }
    /** The warband is out: everyone hears it on the radio, and the status cards switch to "n/m left". */
    static void contact(ServerLevel l,HuntData h,HuntBoard.Mission m){
        Hunts.announce(l,Component.translatable("gui.arsenal_beacon.tactical.contact",m.offer.codename,m.total));
        for(ServerPlayer p:l.getServer().getPlayerList().getPlayers())Hunts.sound(p,ArsenalSounds.TACTICAL_CONTACT.get());
        Hunts.sync(l,h,null);
        log("{}: contact, {} mobs ({}) at {} {}",m.offer.codename,m.total,m.offer.kind.id,m.offer.x,m.offer.z);
    }
    /** Whether a hunt mob loading from disk belongs to the running warband; any other is stale. */
    static boolean stale(ServerLevel l,Entity e){
        if(l.dimension()!=Level.OVERWORLD)return true;
        var m=HuntData.get(l).board.mission;var n=e.getPersistentData();
        return m==null||!m.engaged()||m.offer.id!=n.getLong(TAG)||m.serial!=n.getLong(SERIAL);
    }
    private static void log(String text,Object... args){com.mojang.logging.LogUtils.getLogger().info("[hunts] "+text,args);}

    /** Forge events: kills, stale mobs loading, and a server stop. */
    static final class Events {
        /** Last in line, so a death another mod cancels is not counted. */
        @SubscribeEvent(priority=EventPriority.LOWEST) public void died(LivingDeathEvent event){
            var entity=event.getEntity();
            if(!(entity.level() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD||!hunter(entity))return;
            var n=entity.getPersistentData();if(n.getBoolean(COUNTED))return;n.putBoolean(COUNTED,true);
            var h=HuntData.get(l);var b=h.board;
            if(b.killed(n.getLong(TAG),n.getLong(SERIAL))){h.setDirty();Hunts.complete(l,CampaignData.get(l),h);}
            else if(b.mission!=null&&b.mission.offer.id==n.getLong(TAG)&&b.mission.serial==n.getLong(SERIAL)){h.setDirty();Hunts.sync(l,h,null);}
        }
        @SubscribeEvent public void joined(EntityJoinLevelEvent event){
            if(!event.loadedFromDisk()||!(event.getLevel() instanceof ServerLevel l)||!hunter(event.getEntity()))return;
            if(stale(l,event.getEntity())){event.setCanceled(true);event.getEntity().discard();}
        }
        @SubscribeEvent public void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){forget();TacticalScan.forget();}
    }
}
