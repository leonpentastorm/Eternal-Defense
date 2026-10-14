package dev.eternaldefense.beacon;

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

/** A mission's warband in the world. */
final class HuntWarband {
    private HuntWarband(){}
    /** The mission id a hunt mob belongs to, its warband's serial */
    static final String TAG="eternalHunt",SERIAL="eternalHuntSerial",COUNTED="eternalHuntCounted",CAPTAIN="eternalHuntCaptain",ANCHOR="eternalHuntAnchor";
    /** One mob a tick: the whole warband is out within a second or so, with no burst of work in one tick. */
    static final int SPAWNS_PER_TICK=1;
    /** Columns tried on the ring for one mob before it comes out at the anchor itself */
    static final int RING_TRIES=6,RING_HEIGHT=8;
    /** Seconds in a row the live count must stay short before lost mobs are spawned again */
    static final int LOST_SECONDS=10;
    /** An engaged warband whose ground is not loaded yet waits this long before looking again. */
    static final int RETRY_TICKS=200;
    /** Speed while rushing in to the anchor (with the spawn's Speed II burst on top). */
    static final double RUSH_SPEED=1.2;

    static boolean hunter(Entity e){return e.getPersistentData().contains(TAG);}

    // Transient state (one shared mission, so one warband at a time)
    private static final List<int[]> ANCHOR_ORDER=TacticalRules.anchorOrder(TacticalRules.ANCHOR_SEARCH);
    private static BlockPos anchor;
    private static long key=Long.MIN_VALUE,retryAt;
    private static int lostStreak;
    /** The running warband's mobs (spawned or loaded), for the red marks. */
    private static final java.util.Set<java.util.UUID> live=new java.util.HashSet<>();
    static void forget(){anchor=null;key=Long.MIN_VALUE;retryAt=0;lostStreak=0;live.clear();}
    /** The one block the running warband rushes to (null before arrival). */
    static BlockPos anchor(){return anchor;}

    static void tick(ServerLevel l,CampaignData d,HuntData h,long clock){
        var b=h.board;var m=b.mission;
        if(m==null||m.state==HuntBoard.State.CLEARED){if(key!=Long.MIN_VALUE)forget();return;}
        long now=m.offer.id*1_000_003L+m.serial;
        if(now!=key){forget();key=now;}
        if(m.state==HuntBoard.State.ACTIVE||m.pending()>0)approachAndSpawn(l,d,h,m,clock);
        if(b.mission!=m||!m.engaged()||clock%20!=0)return;
        if(anchor!=null&&l.isPositionEntityTicking(anchor))zone(l,anchor);
        // Nobody near an engaged warband for too long: it scatters, the mission waits for the team to come back
        if(b.away(nobodyWithin(l,m.offer,TacticalRules.AWAY),20)){scatter(l,h,m);return;}
        h.setDirty();
        // Mobs lost without dying (removed by another mod, converted) are spawned again, never written off
        if(m.pending()==0&&approached(l,m.offer)&&areaTicking(l,m.offer)){
            int alive=count(l,m);
            if(alive<m.expectedAlive()){if(++lostStreak>=LOST_SECONDS){lostStreak=0;int n=b.lost(alive);if(n>0){h.setDirty();log("{}: {} warband mobs were lost without dying; spawning them again",m.offer.codename,n);}}}
            else lostStreak=0;
        }
    }

    private static void approachAndSpawn(ServerLevel l,CampaignData d,HuntData h,HuntBoard.Mission m,long clock){
        var b=h.board;var o=m.offer;
        if(anchor==null){
            if(clock%10!=0||clock<retryAt||!approached(l,o))return;
            var found=findAnchor(l,o);
            if(found.anchor()==null){
                // Nothing to stand on near the X: the team gets a way home and the offer is replaced
                if(found.complete()&&m.state==HuntBoard.State.ACTIVE){noGround(l,d,h,m);return;}
                retryAt=clock+(found.complete()?RETRY_TICKS:40);return;
            }
            anchor=found.anchor();
            if(m.state==HuntBoard.State.ACTIVE){
                int online=l.getServer().getPlayerList().getPlayerCount();
                int total=TacticalRules.warband(o.kind,l.random.nextInt(1000),online)+(o.kind==TacticalRules.Kind.WARLORD?1:0);
                b.engage(total);h.setDirty();contact(l,h,m);zone(l,anchor);
            }
        }
        if(!m.engaged())return;
        for(int n=0;n<SPAWNS_PER_TICK&&m.pending()>0;n++){
            if(!l.isPositionEntityTicking(anchor))return;   // The team left before it was all out: the rest comes when they are back
            var spot=ringSpot(l,anchor);
            boolean captain=o.kind==TacticalRules.Kind.WARLORD&&m.spawned==0;
            if(spawn(l,d,m,spot,anchor,captain)){b.spawnedOne();h.setDirty();RaiderGates.spawnGate(l,spot,net.minecraft.world.phys.Vec3.atBottomCenterOf(anchor));}
            else if(spot.equals(anchor)){retryAt=clock+20;return;}   // Not even the anchor took it this tick (something in the way): try again soon
        }
        if(m.pending()==0)Hunts.sync(l,h,null);
    }

    // Spots and spawning
    /** Someone is within TacticalRules.APPROACH blocks of the objective, and its chunk is entity-ticking. */
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
    /** Where the anchor search ended: the block (or null), and whether every column was loaded and looked at. */
    record Found(BlockPos anchor,boolean complete){}
    /** First standable column outward from the objective (loaded chunks only). */
    static Found findAnchor(ServerLevel l,HuntBoard.Offer o){
        boolean complete=true;
        for(int[] c:ANCHOR_ORDER){
            int x=o.x+c[0],z=o.z+c[1];
            if(!l.getWorldBorder().isWithinBounds(x,z))continue;
            if(!l.isPositionEntityTicking(new BlockPos(x,0,z))){complete=false;continue;}
            var p=new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
            if(standable(l,p))return new Found(p,true);
        }
        return new Found(null,complete);
    }
    /** One block a mob can stand on: a sturdy, dry top that is no tree, magma or cactus */
    static boolean standable(ServerLevel l,BlockPos p){
        var floor=p.below();var ground=l.getBlockState(floor);
        if(!ground.isFaceSturdy(l,floor,net.minecraft.core.Direction.UP)||!l.getFluidState(floor).isEmpty())return false;
        if(ground.is(net.minecraft.tags.BlockTags.LOGS)||ground.is(net.minecraft.tags.BlockTags.LEAVES)||ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
            ||ground.is(net.minecraft.world.level.block.Blocks.CACTUS)||ground.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW))return false;
        for(var cell:List.of(p,p.above()))if(!l.getBlockState(cell).getCollisionShape(l,cell).isEmpty()||!l.getFluidState(cell).isEmpty())return false;
        return true;
    }
    /** Where one mob comes out: a standable column on the ring just outside the small zone, not right on a player */
    static BlockPos ringSpot(ServerLevel l,BlockPos anchor){
        for(int i=0;i<RING_TRIES;i++){
            var off=TacticalRules.ringOffset(l.random.nextDouble()*Math.PI*2,l.random.nextDouble());
            int x=anchor.getX()+off[0],z=anchor.getZ()+off[1];
            if(!l.getWorldBorder().isWithinBounds(x,z)||!l.isPositionEntityTicking(new BlockPos(x,0,z)))continue;
            var p=new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
            if(Math.abs(p.getY()-anchor.getY())<=RING_HEIGHT&&standable(l,p)&&l.getNearestPlayer(p.getX()+.5,p.getY(),p.getZ()+.5,3,false)==null)return p;
        }
        return anchor;   // One block is all a warband needs
    }
    /** The small zone, drawn like the beacon's: a ring of light at its edge and a column over the anchor. */
    static void zone(ServerLevel l,BlockPos anchor){
        for(int a=0;a<360;a+=12){double t=Math.toRadians(a);
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,anchor.getX()+.5+Math.cos(t)*TacticalRules.MINI_ZONE,anchor.getY()+.2,anchor.getZ()+.5+Math.sin(t)*TacticalRules.MINI_ZONE,1,0,0,0,0);}
        for(int y=1;y<12;y+=2)l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,anchor.getX()+.5,anchor.getY()+y,anchor.getZ()+.5,1,0,0,0,0);
    }
    /** No ground within reach of the X: every player who came gets a return gate (five minutes) */
    static void noGround(ServerLevel l,CampaignData d,HuntData h,HuntBoard.Mission m){
        int gates=0;
        for(var p:l.getServer().getPlayerList().getPlayers())
            if(p.level()==l&&p.isAlive()&&!p.isSpectator()&&horizontal(p,m.offer)<=TacticalRules.APPROACH&&Hunts.returnGate(l,d,p,m.offer.codename))gates++;
        log("{}: no block to stand on within {} blocks of the objective; {} return gates, the offer is replaced",m.offer.codename,TacticalRules.ANCHOR_SEARCH,gates);
        Hunts.unreachable(l,d,h);
    }
    /** A hunt mob outside the small zone, with nobody to fight close by, runs in to the anchor. */
    static final class RushAnchor extends net.minecraft.world.entity.ai.goal.Goal {
        final Mob mob;final BlockPos anchor;
        RushAnchor(Mob mob,BlockPos anchor){this.mob=mob;this.anchor=anchor;setFlags(java.util.EnumSet.of(Flag.MOVE));}
        private boolean outside(){double r=TacticalRules.MINI_ZONE-1;return mob.distanceToSqr(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5)>r*r;}
        private boolean fighting(){var t=mob.getTarget();return t!=null&&t.isAlive()&&mob.distanceToSqr(t)<12*12;}
        @Override public boolean canUse(){return outside()&&!fighting();}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void start(){go();}
        @Override public void stop(){mob.getNavigation().stop();}
        @Override public void tick(){if(mob.tickCount%20==0||mob.getNavigation().isDone())go();}
        private void go(){
            if(!mob.getNavigation().moveTo(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5,RUSH_SPEED)||mob.getNavigation().isDone())
                mob.getMoveControl().setWantedPosition(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5,RUSH_SPEED);   // No path: straight at it
        }
    }
    static void rush(Mob mob,BlockPos anchor){mob.goalSelector.addGoal(1,new RushAnchor(mob,anchor));}
    /** Entity ids of the running warband's live mobs (they get the raid's red mark). */
    static int[] markIds(ServerLevel l){
        var out=new it.unimi.dsi.fastutil.ints.IntArrayList();
        for(var it=live.iterator();it.hasNext();){var id=it.next();var e=l.getEntity(id);if(e!=null&&e.isAlive())out.add(e.getId());else if(e!=null)it.remove();}
        return out.toIntArray();
    }
    /** One mob of the warband: a raid role and type at the current reward tier, balanced by RaidBalance */
    static boolean spawn(ServerLevel l,CampaignData d,HuntBoard.Mission m,BlockPos spot,BlockPos anchor,boolean captain){
        int tier=Hunts.tier(l,d);
        String role=captain?"heavy":RaidBalance.role(tier,l.random.nextInt(100));
        EntityType<?> type=RaidBalance.type(l,tier,role);
        if(!(type.create(l) instanceof Mob mob))return false;
        mob.moveTo(spot.getX()+.5+(l.random.nextDouble()-.5)*2,spot.getY(),spot.getZ()+.5+(l.random.nextDouble()-.5)*2,l.random.nextFloat()*360,0);
        if(!l.noCollision(mob))mob.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,mob.getYRot(),0);
        if(!l.noCollision(mob))return false;
        // Tagged before it joins the world, so the beacon's combat hook
        var n=mob.getPersistentData();n.putLong(TAG,m.offer.id);n.putLong(SERIAL,m.serial);n.putString("eternalRole",role);n.putLong(ANCHOR,anchor.asLong());if(captain)n.putBoolean(CAPTAIN,true);
        mob.setPersistenceRequired();
        mob.restrictTo(anchor,TacticalRules.LEASH);
        arm(mob);
        if(!l.addFreshEntity(mob))return false;
        RaidBalance.balance(mob,tier);
        if(captain)crown(mob,m.offer.codename,tier);
        mob.setHealth(mob.getMaxHealth());
        rush(mob,anchor);RaidSpawns.rush(mob);live.add(mob.getUUID());   // In to the anchor, with the raid's speed burst
        return true;
    }
    /** Ranged mobs get their weapon (no finalizeSpawn, which could add a jockey or random gear) */
    static void arm(Mob mob){
        RaidBalance.arm(mob);
        mob.setDropChance(EquipmentSlot.MAINHAND,0);mob.setDropChance(EquipmentSlot.OFFHAND,0);
    }
    /** The Warlord's captain: heavy, twice the health, glowing, named with the codename (no boss bar). */
    static void crown(Mob mob,String codename,int tier){
        var hp=mob.getAttribute(Attributes.MAX_HEALTH);
        if(hp!=null){hp.removeModifiers();hp.setBaseValue(RaidBalance.health("heavy",tier)*TacticalRules.CAPTAIN_HEALTH_FACTOR);}
        mob.setGlowingTag(true);mob.setCustomName(Component.literal(codename).withStyle(ChatFormatting.GOLD));mob.setCustomNameVisible(true);
    }

    // The warband's end
    /** Live (loaded, not yet dead) mobs of the current warband. */
    static int count(ServerLevel l,HuntBoard.Mission m){
        int n=0;
        for(var e:l.getAllEntities())if(e.isAlive()&&hunter(e)){var t=e.getPersistentData();if(t.getLong(TAG)==m.offer.id&&t.getLong(SERIAL)==m.serial&&!t.getBoolean(COUNTED))n++;}
        return n;
    }
    /** Removes every living loaded mob of a mission (any serial; one that is dying keeps its fall and drops). */
    static int discard(ServerLevel l,long missionId){
        var gone=new ArrayList<Entity>();
        for(var e:l.getAllEntities())if(e.isAlive()&&hunter(e)&&e.getPersistentData().getLong(TAG)==missionId)gone.add(e);
        for(var e:gone)e.discard();
        return gone.size();
    }
    static void scatter(ServerLevel l,HuntData h,HuntBoard.Mission m){
        h.board.scatter();discard(l,m.offer.id);h.setDirty();forget();
        Hunts.sync(l,h,null);
        Hunts.announce(l,Component.translatable("gui.eternal_defense.tactical.scattered",m.offer.codename));
        log("{}: nobody within {} blocks for {} ticks; the warband scattered",m.offer.codename,TacticalRules.AWAY,TacticalRules.AWAY_TICKS);
    }
    /** The warband is out: everyone hears it on the radio, and the status cards switch to "n/m left". */
    static void contact(ServerLevel l,HuntData h,HuntBoard.Mission m){
        Hunts.announce(l,Component.translatable("gui.eternal_defense.tactical.contact",m.offer.codename,m.total));
        for(ServerPlayer p:l.getServer().getPlayerList().getPlayers())Hunts.sound(p,DefenseSounds.TACTICAL_CONTACT.get());
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
            if(stale(l,event.getEntity())){event.setCanceled(true);event.getEntity().discard();return;}
            // Goals are not saved: a mob of the running warband loading from disk runs for its anchor again
            if(event.getEntity() instanceof Mob mob){var n=mob.getPersistentData();live.add(mob.getUUID());if(n.contains(ANCHOR))rush(mob,BlockPos.of(n.getLong(ANCHOR)));}
        }
        @SubscribeEvent public void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){forget();TacticalScan.forget();}
    }
}
