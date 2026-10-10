package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * The three flares. All of them are thrown and cost nothing to throw (the price is paid when buying them):
 * a Support Flare sends the platform's whole supply grid, a Return Flare opens a portal home, a Fire Support Flare shells its own spot.
 */
final class SupportFlares {
    private SupportFlares(){}

    /** The box fire support covers (red on screen): everything inside it is hit by every shell. */
    static AABB blastBox(Vec3 c){return blastBox(c,SupportRules.BLAST_RADIUS);}
    static AABB blastBox(Vec3 c,double r){return new AABB(c.x-r,c.y-0.5,c.z-r,c.x+r,c.y+r,c.z+r);}
    /** A Bunker Buster digs down as well as up: its box is a cube around the flare, and every block in it goes. */
    static AABB bunkerBox(Vec3 c,double r){return new AABB(c.x-r,c.y-r,c.z-r,c.x+r,c.y+r,c.z+r);}
    /** Napalm and frost lie on the ground: a flat box, {@link CannonUpgrades#GROUND_HEIGHT} blocks high. */
    static AABB groundBox(Vec3 c,double r){return new AABB(c.x-r,c.y-0.5,c.z-r,c.x+r,c.y+CannonUpgrades.GROUND_HEIGHT,c.z+r);}
    static AABB boxFor(CannonUpgrades.FireType type,Vec3 c,double r){return type==CannonUpgrades.FireType.BUNKER||type==CannonUpgrades.FireType.STARSHELL?bunkerBox(c,r):type.flat()?groundBox(c,r):blastBox(c,r);}
    /**
     * Solid blocks in the column above {@code at}, counted up to {@link SupportRules#COVER_BLOCKS} (leaves and other blocks that do not stop
     * movement do not count): a shell comes down an open pit or through a thin roof, not through rock.
     */
    static int cover(ServerLevel level,BlockPos at){
        int top=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ()),n=0;
        var pos=new BlockPos.MutableBlockPos();
        for(int y=at.getY()+1;y<top&&n<SupportRules.COVER_BLOCKS;y++){
            pos.set(at.getX(),y,at.getZ());var state=level.getBlockState(pos);
            if(state.blocksMotion()&&!state.is(net.minecraft.tags.BlockTags.LEAVES))n++;
        }
        return n;
    }
    /** Pure: the tipped-arrow potion an Arrow Cluster arrow carries: harmful to its target (an undead mob is hurt by healing, not by harming or poison). {@code roll} is any non-negative number. */
    static String arrowPotion(boolean undead,boolean aimed,int roll){
        var pick=!aimed?List.of("slowness","weakness"):undead?List.of("healing","slowness","weakness"):List.of("harming","poison","slowness","weakness");
        return pick.get(Math.floorMod(roll,pick.size()));
    }

    static final class FlareItem extends Item {
        final SupportCalls.Kind kind;
        FlareItem(SupportCalls.Kind kind){super(new Item.Properties().stacksTo(16).rarity(kind==SupportCalls.Kind.FIRE?Rarity.RARE:Rarity.UNCOMMON));this.kind=kind;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack stack=player.getItemInHand(hand);
            if(level.isClientSide)return InteractionResultHolder.success(stack);
            if(!(player instanceof ServerPlayer sp))return InteractionResultHolder.fail(stack);
            String problem=SupportCalls.problem(sp,kind);
            if(problem!=null){SupportCalls.refuse(sp,problem);return InteractionResultHolder.fail(stack);}
            // The call is always the THROWER's own: their upgrades and their chosen fire support, even for a flare somebody else gave them.
            var base=SupportData.get(sp.serverLevel()).of(sp.getUUID());
            var cfg=CannonUpgrades.Config.of(base!=null?base.up:new int[CannonUpgrades.Upgrade.values().length],base!=null?base.type():CannonUpgrades.FireType.EXPLOSION);
            ListTag cargo=kind==SupportCalls.Kind.SUPPLY?SupportCalls.takeGrid(sp):new ListTag();
            SupportCalls.announce(sp,kind==SupportCalls.Kind.FIRE?cfg.type().callId():kind.id);
            var flare=new FlareEntity(ArsenalBeacon.FLARE.get(),level);
            flare.setOwner(player);flare.setPos(player.getX(),player.getEyeY()-.1,player.getZ());
            flare.configure(kind,cargo,cfg);
            flare.shootFromRotation(player,player.getXRot(),player.getYRot(),0f,1.1f,1f);
            level.addFreshEntity(flare);
            level.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.FIREWORK_ROCKET_LAUNCH,SoundSource.PLAYERS,.8f,1.2f);
            if(!player.getAbilities().instabuild)stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag f){
            lines.add(Component.translatable("tooltip.arsenal_beacon.flare."+kind.id).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.arsenal_beacon.flare.free").withStyle(ChatFormatting.DARK_AQUA));
            if(kind==SupportCalls.Kind.FIRE){
                lines.add(Component.translatable("tooltip.arsenal_beacon.flare.calls",Component.translatable("gui.arsenal_beacon.cannon.type."+SupportHud.current().id)).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("tooltip.arsenal_beacon.flare.yours").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Players are never hurt by a flare (its shells, bombs and bolts) except by a Bunker Buster, which is the one support that can hurt them. */
    public static final class Safety {
        @net.minecraftforge.eventbus.api.SubscribeEvent public void protect(net.minecraftforge.event.entity.living.LivingAttackEvent e){
            if(e.getEntity() instanceof Player&&e.getSource().getDirectEntity() instanceof FlareEntity f&&f.type()!=CannonUpgrades.FireType.BUNKER)e.setCanceled(true);
        }
    }
    /** Blocks a Bunker Buster leaves alone: the beacon and the base's own blocks, and anything super hard (obsidian, crying obsidian, bedrock, reinforced deepslate, anchors, ancient debris...). */
    static boolean bunkerSpares(BlockState state){
        return ArsenalStructures.owns(state)||state.is(ArsenalBeacon.STRUCTURE_PART.get())||state.is(ArsenalBeacon.BEACON.get())
            ||state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)||state.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN)||state.is(net.minecraft.world.level.block.Blocks.BEDROCK)
            ||state.getBlock().getExplosionResistance()>=HARD_RESISTANCE;
    }
    /** Explosion resistance from which a block counts as super hard (obsidian is 1200). */
    static final float HARD_RESISTANCE=1200f;
    /** Layers of the crater dug per tick: the whole cube is gone in about this many ticks, so the ground seems to be eaten from the top down. */
    static final int CARVE_TICKS=12;

    /** A thrown flare. It lands, then runs its call: the cannon turns onto it and fires, then a parcel countdown, a portal, or the owner's chosen fire support. */
    static final class FlareEntity extends ThrowableItemProjectile {
        static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.INT);
        static final EntityDataAccessor<Integer> TYPE=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.INT);
        static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Boolean> LANDED=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.BOOLEAN);
        /** A Return Flare shows as a portal only once the cannon has fired it. */
        static final EntityDataAccessor<Boolean> OPEN=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.BOOLEAN);
        /** The most a call waits for a stuck turret (ticks) and the pause between settling and the shot. */
        static final int GUN_TIMEOUT=300,CHAMBER_TICKS=10;
        private ListTag cargo=new ListTag();
        private CannonUpgrades.Config cfg=CannonUpgrades.Config.of(new int[CannonUpgrades.Upgrade.values().length],CannonUpgrades.FireType.EXPLOSION);
        private int airTicks,landedTicks,startAt=-1,total,spawnAt,clear,life,arm,auraLeft=-1;
        /** Tick this flare took the cannon, tick the cannon shot for it, tick a Return portal opened, tick a supply flare's cannon fired. */
        private int turnStart=-1,gunAt=-1,openAt=-1,fireTick=-1;
        private boolean outside,spawned,planned,held,queued;private int cooldown;
        private int carveY=Integer.MIN_VALUE,carveFloor,carveX0,carveX1,carveZ0,carveZ1;
        /**
         * A lingering type's effect (cluster bomblets, burning ground, frost, gravity well, the burning star): the flare ticks it ran from and to,
         * and a shockwave's push window. The fire, snow and light blocks it put in the world are listed so they can be taken away again (saved).
         */
        private int effectFrom=-1,effectUntil=-1,pushUntil=-1;
        private final java.util.LinkedHashSet<Long> placed=new java.util.LinkedHashSet<>();
        /** Transient: the vortex or star seen in the sky exists (after a reload it is put back). */
        private boolean visual;
        private UUID parcelId;
        /** A "back" portal leads to this spot (in this dimension) instead of to the platform. */
        private Vec3 dest;private String destDim="";
        FlareEntity(EntityType<? extends FlareEntity> type,Level level){super(type,level);}
        void configure(SupportCalls.Kind kind,ListTag cargo,CannonUpgrades.Config cfg){
            entityData.set(KIND,kind.ordinal());entityData.set(TYPE,cfg.type().ordinal());entityData.set(RADIUS,(float)cfg.radius());
            this.cargo=cargo;this.cfg=cfg;life=CannonUpgrades.portalTicks(cfg.longPortal());
        }
        SupportCalls.Kind kind(){return SupportCalls.Kind.of(entityData.get(KIND));}
        CannonUpgrades.FireType type(){return CannonUpgrades.FireType.of(entityData.get(TYPE));}
        double radius(){return entityData.get(RADIUS);}
        boolean landed(){return entityData.get(LANDED);}
        boolean open(){return entityData.get(OPEN);}
        /** The red box of this flare's area: the same box hurts, digs and is drawn. */
        AABB box(){return boxFor(type(),position(),radius());}
        /** Opens a portal back to {@code to} that lasts {@code ticks}; owner only. */
        static FlareEntity backPortal(ServerLevel level,Player owner,Vec3 at,Vec3 to,String toDim,CannonUpgrades.Config cfg,int ticks){
            var f=new FlareEntity(ArsenalBeacon.FLARE.get(),level);
            f.setOwner(owner);f.configure(SupportCalls.Kind.RETURN,new ListTag(),cfg);f.life=ticks;f.dest=to;f.destDim=toDim;f.arm=60;f.openAt=0;
            f.setPos(at.x,at.y,at.z);f.setNoGravity(true);f.entityData.set(LANDED,true);f.entityData.set(OPEN,true);level.addFreshEntity(f);return f;
        }
        @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(KIND,0);entityData.define(TYPE,0);entityData.define(RADIUS,(float)SupportRules.BLAST_RADIUS);entityData.define(LANDED,false);entityData.define(OPEN,false);}
        @Override protected Item getDefaultItem(){return switch(kind()){case SUPPLY->ArsenalBeacon.SUPPLY_FLARE.get();case RETURN->ArsenalBeacon.RETURN_FLARE.get();case FIRE->ArsenalBeacon.FIRE_FLARE.get();};}
        @Override protected float getGravity(){return landed()?0f:0.05f;}
        @Override protected boolean canHitEntity(net.minecraft.world.entity.Entity e){return false;}
        @Override protected void onHitEntity(EntityHitResult r){}
        @Override protected void onHitBlock(BlockHitResult hit){
            if(landed())return;
            if(hit.getDirection()==Direction.UP){
                // it comes to rest exactly where it touched the ground: everything the call does is measured from this point
                Vec3 at=hit.getLocation();setPos(at.x,at.y,at.z);setDeltaMovement(Vec3.ZERO);setNoGravity(true);entityData.set(LANDED,true);
                if(level() instanceof ServerLevel server)Blasts.flareLanded(server,position(),switch(kind()){case SUPPLY->1.1f;case RETURN->1f;case FIRE->.9f;});
            }else{
                Vec3 m=getDeltaMovement();setDeltaMovement(-m.x*.2,Math.min(m.y,0)*.5,-m.z*.2);   // slides down walls and bounces off ceilings
            }
        }
        @Override public AABB getBoundingBoxForCulling(){return landed()&&kind()==SupportCalls.Kind.FIRE?box().inflate(1):super.getBoundingBoxForCulling();}
        @Override public void remove(RemovalReason reason){
            if(!level().isClientSide&&reason.shouldDestroy()&&!spawned&&!cargo.isEmpty()&&level() instanceof ServerLevel server&&getOwner()!=null){
                var items=new ArrayList<ItemStack>();for(var t:cargo)items.add(ItemStack.of((CompoundTag)t));
                cargo=new ListTag();SupportCalls.giveBack(server,getOwner().getUUID(),position(),items);
            }
            if(!level().isClientSide&&level() instanceof ServerLevel server){releaseGun(server);if(reason.shouldDestroy())clearPlaced(server);}
            super.remove(reason);
        }
        private ServerPlayer owner(ServerLevel server){return getOwner()==null?null:server.getServer().getPlayerList().getPlayer(getOwner().getUUID());}
        private SupportData.Base base(ServerLevel server){return getOwner()==null?null:SupportData.get(server).of(getOwner().getUUID());}

        // ---- the cannon's part of every call ---------------------------------------------------------------------------
        /**
         * Takes the owner's cannon (a call waits while another one has it), turns it onto this flare and waits for it to settle:
         * true once it is ready to shoot. Calls that cannot show the turret (another dimension, nobody near the base) wait the same time.
         */
        private boolean gunReady(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity live,int t){
            if(gunAt>=0)return true;
            var home=server.getServer().overworld();var base=base(server);long now=home.getGameTime();
            if(base!=null){
                if(!base.hold(getUUID(),now,kind()==SupportCalls.Kind.FIRE)){
                    if(!queued){queued=true;if(kind()==SupportCalls.Kind.FIRE&&base.barrage(now)){SupportCalls.notice(owner,"barrage",ChatFormatting.YELLOW);SupportCalls.noticeDetail(owner,"barrage.wait");}}
                    return false;
                }
                held=true;
            }
            if(turnStart<0){
                turnStart=t;
                if(live!=null)live.aimAt(home,position());
                if(base!=null&&SupportCalls.farFromCannon(owner,home,base.cannon))SupportCalls.notice(owner,"preparing",ChatFormatting.GRAY);
            }
            int waited=t-turnStart;
            boolean ready=live!=null?live.armed():waited>=SupportCannon.turnTicks(90f,CannonUpgrades.turnSpeed(base==null?0:base.up[CannonUpgrades.Upgrade.TRAVERSE.ordinal()]))+SupportCannon.SETTLE_TICKS;
            if((ready&&waited>=CHAMBER_TICKS)||waited>=GUN_TIMEOUT){gunAt=t;return true;}
            return false;
        }
        /** The cannon shoots (its turret recoils and roars); a player too far away to hear it is told. */
        private void shot(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity live,boolean first){
            var home=server.getServer().overworld();
            if(live!=null)live.fire(home);
            var base=base(server);
            if(first&&base!=null&&SupportCalls.farFromCannon(owner,home,base.cannon))SupportCalls.notice(owner,"heard",ChatFormatting.GOLD);
        }
        private void releaseGun(ServerLevel server){
            if(!held)return;held=false;
            var base=base(server);if(base!=null)base.release(getUUID());
        }

        @Override public void tick(){
            if(landed()){setDeltaMovement(Vec3.ZERO);baseTick();}else super.tick();
            if(!(level() instanceof ServerLevel server))return;
            if(!landed()){if(++airTicks>400)discard();return;}
            int t=landedTicks++;
            var kind=kind();var owner=owner(server);
            var live=getOwner()==null||dest!=null?null:SupportCalls.liveCannon(server.getServer().overworld(),getOwner().getUUID());
            if(held){var base=base(server);if(base!=null)base.hold(getUUID(),server.getServer().overworld().getGameTime(),kind==SupportCalls.Kind.FIRE);}
            if(t==0&&kind==SupportCalls.Kind.FIRE){
                if(cfg.type()==CannonUpgrades.FireType.BUNKER&&BaseZone.touches(server,box())){bunkerHome(server,owner);return;}
                if(!cfg.tunnel()&&server.dimensionType().hasSkyLight()&&SupportRules.underground(cover(server,blockPosition()))){underground(server,owner);return;}
            }
            if(t==0&&dest==null)SupportChatter.say(server,position(),kind,owner);   // the call is taken: the radio answers
            if(t%2==0)server.sendParticles(kind==SupportCalls.Kind.FIRE?ParticleTypes.FLAME:kind==SupportCalls.Kind.SUPPLY?ParticleTypes.END_ROD:ParticleTypes.WITCH,getX(),getY()+.25,getZ(),2,.06,.15,.06,.02);
            if(t%6==0)server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,getX(),getY()+.3,getZ(),0,0,.07,0,1);
            if(kind==SupportCalls.Kind.FIRE&&cfg.slow()&&t%10==0)slowField(server);
            if(carveY!=Integer.MIN_VALUE)carveStep(server);
            switch(kind){
                case SUPPLY->supply(server,owner,live,t);
                case FIRE->fire(server,owner,live,t);
                case RETURN->portal(server,owner,live,t);
            }
        }

        /** Without Quantum Tunneling the cannon cannot reach a flare that landed under cover (three solid blocks or more above it): the flare is handed back. */
        private void underground(ServerLevel server,ServerPlayer owner){
            if(getOwner()!=null){
                if(owner!=null)SupportCalls.refuse(owner,"underground");
                SupportCalls.giveBack(server,getOwner().getUUID(),position(),List.of(new ItemStack(ArsenalBeacon.FIRE_FLARE.get())));
            }
            server.sendParticles(ParticleTypes.POOF,getX(),getY()+.3,getZ(),10,.2,.2,.2,.02);discard();
        }
        /** A Bunker Buster whose box touches the protected zone never goes off: the flare comes back with a sharp word. */
        private void bunkerHome(ServerLevel server,ServerPlayer owner){
            if(getOwner()!=null){
                if(owner!=null)SupportCalls.refuse(owner,"bunker_home");
                SupportCalls.giveBack(server,getOwner().getUUID(),position(),List.of(new ItemStack(ArsenalBeacon.FIRE_FLARE.get())));
            }
            server.sendParticles(ParticleTypes.POOF,getX(),getY()+.3,getZ(),10,.2,.2,.2,.02);discard();
        }
        private void slowField(ServerLevel server){
            var area=type()==CannonUpgrades.FireType.STARSHELL?blastBox(position(),CannonUpgrades.radius(CannonUpgrades.FireType.EXPLOSION,cfg.aoe())):box();
            for(LivingEntity e:server.getEntitiesOfClass(LivingEntity.class,area,e->e instanceof Enemy&&e.isAlive()))
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,1,true,true));
            if(landedTicks%20==0){double r=radius();for(int i=0;i<16;i++){double a=i*Math.PI/8;server.sendParticles(ParticleTypes.SNOWFLAKE,getX()+Math.cos(a)*r,getY()+.2,getZ()+Math.sin(a)*r,1,0,0,0,0);}}
        }

        // ---- supply: the cannon turns and fires, then the countdown runs, the parcel arrives, an optional healing aura ----
        private void supply(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity live,int t){
            if(!planned){
                planned=true;clear=SupportCalls.clearAbove(server,blockPosition());
                outside=server.canSeeSky(blockPosition())&&clear>=SupportRules.PARCEL_MIN_DROP;
            }
            if(fireTick<0){
                // the delivery time only starts once the cannon has turned onto the flare and fired: the turning is on top of it
                if(!gunReady(server,owner,live,t))return;
                fireTick=t;shot(server,owner,live,true);releaseGun(server);
                total=fireTick+SupportRules.supplyTicks(outside);spawnAt=fireTick+(outside?SupportRules.parcelSpawnTick(clear):SupportRules.supplyTicks(false));
            }
            if(owner!=null&&(t-fireTick)%20==0&&t<total){
                int seconds=(total-t+19)/20;
                if(seconds>0)owner.sendSystemMessage(Component.translatable("gui.arsenal_beacon.support.countdown",seconds).withStyle(ChatFormatting.YELLOW));
            }
            if(!spawned&&t>=spawnAt){
                spawned=true;parcelId=SupportCalls.deliver(server,owner,position(),cargo,outside,clear);cargo=new ListTag();
            }
            if(cfg.aura())aura(server,t);
            if(t>=total){
                if(!cfg.aura()){discard();return;}
                // the aura outlives the delivery: it ends 30 seconds after somebody empties the parcel
                boolean gone=parcelId==null||server.getEntity(parcelId)==null||t>=total+12000;
                if(gone&&auraLeft<0)auraLeft=CannonUpgrades.AURA_AFTER_PICKUP_TICKS;
                if(auraLeft>=0&&--auraLeft<0)discard();
            }
        }
        private void aura(ServerLevel server,int t){
            if(t%20==0)for(ServerPlayer p:server.getEntitiesOfClass(ServerPlayer.class,new AABB(position(),position()).inflate(CannonUpgrades.AURA_RADIUS,4,CannonUpgrades.AURA_RADIUS),pl->pl.distanceToSqr(this)<=CannonUpgrades.AURA_RADIUS*(double)CannonUpgrades.AURA_RADIUS))
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,60,1,true,true));
            if(t%10==0){double r=CannonUpgrades.AURA_RADIUS;for(int i=0;i<18;i++){double a=i*Math.PI/9+t*.03;server.sendParticles(ParticleTypes.HAPPY_VILLAGER,getX()+Math.cos(a)*r,getY()+.3,getZ()+Math.sin(a)*r,1,0,.1,0,0);}server.sendParticles(ParticleTypes.HEART,getX(),getY()+1,getZ(),1,.4,.3,.4,0);}
        }

        // ---- fire support: the owner's chosen shells, all measured from exactly this spot -------------------------
        private void fire(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity live,int t){
            if(startAt<0){
                // the gun fires the moment the lock sound ends (the cannon is armed then)
                if(!gunReady(server,owner,live,t))return;
                startAt=t;
            }
            int rel=t-startAt,interval=cfg.interval(),n=cfg.volleys(),flight=SupportRules.BLAST_FLIGHT_TICKS;
            if(rel>=0&&rel%interval==0&&rel/interval<n){shot(server,owner,live,rel==0);launch(server,flight);}
            int land=rel-flight;
            if(land>=0&&land%interval==0&&land/interval<n)resolve(server,t);
            linger(server,t);
            if(carveY==Integer.MIN_VALUE&&land>interval*(n-1)+20&&effectUntil<0&&pushUntil<0)discard();
        }
        /**
         * The cannon has fired: the round is seen falling from high over the flare and lands exactly when it resolves. Nothing flies from the
         * cannon to the target. Lightning needs no round; a starshell, a cluster shell and an arrow cluster burst in the air above the flare.
         */
        private void launch(ServerLevel server,int flight){
            var type=type();Vec3 c=position();double r=radius(),h=Ordnance.DROP_HEIGHT;
            switch(type){
                case NARUKAMI->{}
                case BUNKER->Ordnance.drop(server,c,Ordnance.BOMB,type,flight,h,CARVE_TICKS,Math.max(1,Mth.ceil(box().getYsize()/2)));
                case ARROW->Ordnance.drop(server,c.add(0,r*2,0),Ordnance.SHELL,type,flight,h-r*2,0,0);
                case CLUSTER->Ordnance.drop(server,c.add(0,CannonUpgrades.CLUSTER_BURST_HEIGHT,0),Ordnance.SHELL,type,flight,h-CannonUpgrades.CLUSTER_BURST_HEIGHT,0,0);
                case STARSHELL->Ordnance.drop(server,c.add(0,CannonUpgrades.STAR_BURST_HEIGHT,0),Ordnance.SHELL,type,flight,h,0,0);
                default->Ordnance.drop(server,c,Ordnance.SHELL,type,flight,h,0,0);
            }
        }
        private List<LivingEntity> enemies(ServerLevel level,AABB area){return level.getEntitiesOfClass(LivingEntity.class,area,e->e instanceof Enemy&&e.isAlive());}
        private void resolve(ServerLevel level,int t){
            Vec3 c=position();var type=cfg.type();double r=radius();var box=box();float dmg=SupportRules.FIRE_DAMAGE*cfg.damage();
            switch(type){
                case EXPLOSION->{
                    Blasts.shell(level,c,r);
                    for(LivingEntity e:enemies(level,box))e.hurt(level.damageSources().explosion(this,getOwner()),dmg);
                }
                case ARROW->{
                    level.sendParticles(ParticleTypes.FIREWORK,c.x,c.y+r*2,c.z,30,r*.5,.4,r*.5,.05);
                    level.playSound(null,c.x,c.y+r,c.z,SoundEvents.FIREWORK_ROCKET_BLAST,SoundSource.BLOCKS,3f,.8f);
                    // a shower of tipped arrows: three come down on every hostile mob in the box (a potion that harms that mob), the rest fall at random
                    record Spot(double x,double z,LivingEntity target){}
                    var spots=new ArrayList<Spot>();int count=0;
                    for(LivingEntity e:enemies(level,box)){if(count++>=10)break;for(int k=0;k<3;k++)spots.add(new Spot(e.getX()+(random.nextDouble()-.5)*.5,e.getZ()+(random.nextDouble()-.5)*.5,e));}
                    int extra=8+(int)Math.round((r-SupportRules.BLAST_RADIUS)*2);
                    for(int k=0;k<extra;k++)spots.add(new Spot(c.x+(random.nextDouble()*2-1)*r,c.z+(random.nextDouble()*2-1)*r,null));
                    for(var at:spots){
                        double y=c.y+r*2+random.nextDouble()*2;
                        var arrow=new Arrow(level,at.x(),y,at.z()){@Override protected boolean canHitEntity(net.minecraft.world.entity.Entity e){return e instanceof Enemy&&super.canHitEntity(e);}};
                        arrow.setOwner(getOwner());arrow.getPersistentData().putBoolean("arsenalDefensiveWeapon",true);arrow.pickup=AbstractArrow.Pickup.DISALLOWED;arrow.setBaseDamage(2.5*cfg.damage());
                        boolean undead=at.target()!=null&&at.target().getMobType()==net.minecraft.world.entity.MobType.UNDEAD;
                        var potion=net.minecraft.core.registries.BuiltInRegistries.POTION.get(new net.minecraft.resources.ResourceLocation(arrowPotion(undead,at.target()!=null,random.nextInt(12))));
                        arrow.setEffectsFromItem(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW),potion));
                        arrow.setDeltaMovement((random.nextDouble()-.5)*.05,-1.8,(random.nextDouble()-.5)*.05);level.addFreshEntity(arrow);
                    }
                }
                case NARUKAMI->{
                    int struck=0;
                    for(LivingEntity e:enemies(level,box)){
                        if(struck++>=12)break;
                        bolt(level,e.getX(),e.getY(),e.getZ());RaidAdaptation.weaponDamage(e,level.damageSources().lightningBolt(),dmg);RaidAdaptation.weaponFire(e,60);e.setSecondsOnFire(3);
                    }
                    if(struck==0)bolt(level,c.x+(random.nextDouble()*2-1)*r,c.y,c.z+(random.nextDouble()*2-1)*r);
                }
                case BUNKER->{
                    Blasts.bunker(level,c,r);
                    for(LivingEntity e:enemies(level,box))e.hurt(level.damageSources().explosion(this,getOwner()),dmg*2);
                    // the one support that hurts players: half their health, armor or not
                    for(Player p:level.getEntitiesOfClass(Player.class,box,Player::isAlive))p.hurt(level.damageSources().indirectMagic(this,getOwner()),p.getMaxHealth()*SupportRules.BUNKER_PLAYER_SHARE);
                    beginCarve(level);
                }
                case HEAL->{
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,c.x,c.y+.6,c.z,70,r*.4,.6,r*.4,.1);level.sendParticles(ParticleTypes.HEART,c.x,c.y+1,c.z,14,r*.3,.5,r*.3,0);
                    level.sendParticles(ParticleTypes.EXPLOSION,c.x,c.y+.5,c.z,1,0,0,0,0);
                    level.playSound(null,c.x,c.y,c.z,SoundEvents.SPLASH_POTION_BREAK,SoundSource.BLOCKS,3f,1f);level.playSound(null,c.x,c.y,c.z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,1.5f,1.4f);
                    for(Player p:level.getEntitiesOfClass(Player.class,box,Player::isAlive)){p.addEffect(new MobEffectInstance(MobEffects.HEAL,1,1));p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100,1,true,true));}
                }
                case CURSE->{
                    level.sendParticles(ParticleTypes.SOUL,c.x,c.y+.6,c.z,50,r*.4,.6,r*.4,.04);level.sendParticles(ParticleTypes.SQUID_INK,c.x,c.y+.6,c.z,40,r*.4,.6,r*.4,.05);
                    level.sendParticles(ParticleTypes.EXPLOSION,c.x,c.y+.5,c.z,1,0,0,0,0);level.playSound(null,c.x,c.y,c.z,SoundEvents.WITHER_SPAWN,SoundSource.BLOCKS,.9f,1.6f);
                    float k=cfg.damage();
                    for(LivingEntity e:enemies(level,box)){
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER,(int)(160*k),1));e.addEffect(new MobEffectInstance(MobEffects.POISON,(int)(200*k),1));
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,(int)(300*k),1));e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,(int)(200*k),2));
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,(int)(200*k),0));e.addEffect(new MobEffectInstance(MobEffects.GLOWING,(int)(300*k),0));
                    }
                }
                case CLUSTER->{
                    // the shell bursts over the flare; from now on bomblets rain on the whole box (see clusterTick)
                    var burst=c.add(0,CannonUpgrades.CLUSTER_BURST_HEIGHT,0);
                    Blasts.far(level,ParticleTypes.FLASH,burst.x,burst.y,burst.z,1,0,0,0,0,Blasts.FAR);level.sendParticles(ParticleTypes.EXPLOSION,burst.x,burst.y,burst.z,3,.6,.4,.6,0);
                    Blasts.explode(level,burst,Blasts.FIRE_VOLUME,1.2f);
                    startEffect(t);
                }
                case CRYO->{
                    level.sendParticles(ParticleTypes.SNOWFLAKE,c.x,c.y+.6,c.z,140,r*.45,.6,r*.45,.05);level.sendParticles(ParticleTypes.ITEM_SNOWBALL,c.x,c.y+.6,c.z,40,r*.4,.4,r*.4,.1);
                    level.sendParticles(ParticleTypes.CLOUD,c.x,c.y+.4,c.z,30,r*.4,.2,r*.4,.02);Blasts.ring(level,c,ParticleTypes.SNOWFLAKE,24,.6,.3);
                    Blasts.sound(level,c,ArsenalSounds.CRYO.get(),Blasts.FIRE_VOLUME,1f);
                    startEffect(t);placeGround(level,Blocks.SNOW.defaultBlockState(),false);
                }
                case NAPALM->{
                    Blasts.far(level,ParticleTypes.FLASH,c.x,c.y+.5,c.z,1,0,0,0,0,Blasts.FAR);
                    level.sendParticles(ParticleTypes.FLAME,c.x,c.y+.3,c.z,160,r*.5,.3,r*.5,.08);level.sendParticles(ParticleTypes.LAVA,c.x,c.y+.3,c.z,30,r*.4,.2,r*.4,0);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,c.x,c.y+1,c.z,30,r*.4,.5,r*.4,.05);
                    Blasts.sound(level,c,ArsenalSounds.NAPALM.get(),Blasts.FIRE_VOLUME,1f);
                    startEffect(t);placeGround(level,Blocks.FIRE.defaultBlockState(),true);
                }
                case GRAVITY->{
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL,c.x,c.y+.6,c.z,90,r*.5,.5,r*.5,.05);level.sendParticles(ParticleTypes.PORTAL,c.x,c.y+.6,c.z,60,r*.4,.4,r*.4,1.2);
                    Blasts.sound(level,c,ArsenalSounds.GRAVITY_HUM.get(),Blasts.FIRE_VOLUME,1f);
                    startEffect(t);
                }
                case SHOCKWAVE->{
                    Blasts.far(level,ParticleTypes.FLASH,c.x,c.y+1,c.z,1,0,0,0,0,Blasts.FAR);level.sendParticles(ParticleTypes.SONIC_BOOM,c.x,c.y+1,c.z,1,0,0,0,0);
                    level.sendParticles(ParticleTypes.EXPLOSION,c.x,c.y+.5,c.z,3,r*.15,.3,r*.15,0);
                    Blasts.ring(level,c,ParticleTypes.CLOUD,48,2.2,.3);Blasts.ring(level,c,ParticleTypes.POOF,32,1.5,1.0);Blasts.debris(level,c,30,r*.2);
                    Blasts.sound(level,c,ArsenalSounds.SHOCKWAVE.get(),Blasts.SHELL_VOLUME,1f);
                    pushUntil=t+CannonUpgrades.SHOCK_PUSH_TICKS;
                }
                case STARSHELL->{
                    // the star bursts high over the flare and sinks slowly while it burns, lighting the whole cube
                    var burst=c.add(0,CannonUpgrades.STAR_BURST_HEIGHT,0);
                    Blasts.far(level,ParticleTypes.FLASH,burst.x,burst.y,burst.z,2,.3,.3,.3,0,Blasts.FAR);Blasts.far(level,ParticleTypes.FIREWORK,burst.x,burst.y,burst.z,70,.5,.5,.5,.25,Blasts.FAR);
                    level.playSound(null,burst.x,burst.y,burst.z,ArsenalSounds.STARSHELL.get(),SoundSource.BLOCKS,Blasts.FLARE_VOLUME,1f);
                    startEffect(t);placeLights(level);
                }
            }
        }
        // ---- what keeps going after the round has landed (0.0.18): bomblets, burning ground, frost, the well, the star, the shockwave's push ----
        private void startEffect(int t){effectFrom=t;effectUntil=t+cfg.lingerTicks();visual=false;}
        private void linger(ServerLevel l,int t){
            if(effectUntil>=0){
                if(t>=effectUntil)endEffect(l);
                else{
                    int age=t-effectFrom;
                    if(!visual)showEffect(l,t);
                    switch(type()){
                        case CLUSTER->clusterTick(l,age);
                        case NAPALM->napalmTick(l,age);
                        case CRYO->cryoTick(l,age);
                        case GRAVITY->gravityTick(l,t);
                        case STARSHELL->{if(age%CannonUpgrades.STAR_PULSE_TICKS==0)starPulse(l);}
                        default->{}
                    }
                }
            }
            if(pushUntil>=0){if(t>=pushUntil)pushUntil=-1;else shockPush(l);}
        }
        /** The gravity well's vortex and the burning star are entities that are never saved: they are put up when the effect starts and again after a reload. */
        private void showEffect(ServerLevel l,int t){
            visual=true;int left=effectUntil-t;Vec3 c=position();
            if(type()==CannonUpgrades.FireType.GRAVITY)Ordnance.well(l,c,type(),left,radius());
            if(type()==CannonUpgrades.FireType.STARSHELL)Ordnance.drop(l,c.add(0,1,0),Ordnance.STAR,type(),left,CannonUpgrades.STAR_BURST_HEIGHT-1,0,0);
        }
        private void endEffect(ServerLevel l){
            effectUntil=-1;
            if(type()==CannonUpgrades.FireType.GRAVITY){
                // the well closes: a puff of portal dust and a muffled thump, no damage (the crowd is what it was for)
                var c=position();l.sendParticles(ParticleTypes.REVERSE_PORTAL,c.x,c.y+.5,c.z,80,radius()*.3,.4,radius()*.3,.3);
                Blasts.explode(l,c,2f,1.4f);
            }
            clearPlaced(l);
        }
        /** Cluster Strike: a bomblet goes off somewhere in the box every few ticks (each with its own crack), and every pulse hurts every hostile mob inside. */
        private void clusterTick(ServerLevel l,int age){
            Vec3 c=position();double r=radius();
            if(age%3==0)Blasts.bomblet(l,c.x+(random.nextDouble()*2-1)*r*.9,c.y,c.z+(random.nextDouble()*2-1)*r*.9);
            if(age%CannonUpgrades.CLUSTER_PULSE_TICKS==0){
                for(int k=1;k<CannonUpgrades.BOMBLETS_PER_PULSE;k++)Blasts.bomblet(l,c.x+(random.nextDouble()*2-1)*r*.9,c.y,c.z+(random.nextDouble()*2-1)*r*.9);
                float hurt=CannonUpgrades.clusterPulse(cfg.damage());
                for(LivingEntity e:enemies(l,box()))e.hurt(l.damageSources().explosion(this,getOwner()),hurt);
            }
        }
        /** Napalm Carpet: the ground keeps burning; once a second every hostile mob in the box is hurt and set alight, and burnt-out fire is lit again. */
        private void napalmTick(ServerLevel l,int age){
            Vec3 c=position();double r=radius();
            if(age%4==0){l.sendParticles(ParticleTypes.FLAME,c.x,c.y+.2,c.z,14,r*.5,.1,r*.5,.02);l.sendParticles(ParticleTypes.SMOKE,c.x,c.y+.6,c.z,6,r*.4,.3,r*.4,.02);}
            if(age%20==0){
                for(LivingEntity e:enemies(l,box())){RaidAdaptation.weaponDamage(e,l.damageSources().inFire(),CannonUpgrades.napalm(cfg.damage()));RaidAdaptation.weaponFire(e,CannonUpgrades.NAPALM_BURN_SECONDS*20);e.setSecondsOnFire(CannonUpgrades.NAPALM_BURN_SECONDS);}
                if(age>0)placeGround(l,Blocks.FIRE.defaultBlockState(),true);   // burnt-out fire is lit again
            }
        }
        /** Cryo Shell: no damage; hostile mobs in the frost are slowed almost to a stop for as long as they stay in it. */
        private void cryoTick(ServerLevel l,int age){
            Vec3 c=position();double r=radius();
            if(age%5==0)l.sendParticles(ParticleTypes.SNOWFLAKE,c.x,c.y+.8,c.z,16,r*.5,.6,r*.5,.01);
            if(age%10==0)for(LivingEntity e:enemies(l,box())){
                boolean boss=e.getPersistentData().getBoolean("arsenalBoss");
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,boss?CannonUpgrades.CRYO_BOSS_AMPLIFIER:CannonUpgrades.CRYO_SLOW_AMPLIFIER));
                e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,30,2));
                l.sendParticles(ParticleTypes.SNOWFLAKE,e.getX(),e.getY()+e.getBbHeight()*.6,e.getZ(),4,.3,.4,.3,0);
            }
        }
        /** Gravity Well: every hostile mob in the box is dragged toward the flare (less with knockback resistance, much less a raid boss); no damage. */
        private void gravityTick(ServerLevel l,int t){
            Vec3 c=position();double r=radius();
            for(LivingEntity e:enemies(l,box())){
                double[] v=CannonUpgrades.pull(e.getX()-c.x,e.getZ()-c.z,CannonUpgrades.moveScale(e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE),e.getPersistentData().getBoolean("arsenalBoss")));
                e.setDeltaMovement(e.getDeltaMovement().add(v[0],0,v[1]));e.hurtMarked=true;
            }
            if(t%2==0){
                for(int i=0;i<8;i++){double a=t*.35+i*Math.PI/4,d=r*(1-(t%20)/20.0);l.sendParticles(ParticleTypes.REVERSE_PORTAL,c.x+Math.cos(a)*d,c.y+.4,c.z+Math.sin(a)*d,1,0,0,0,0);}
                l.sendParticles(ParticleTypes.PORTAL,c.x,c.y+.6,c.z,12,r*.4,.3,r*.4,.8);
            }
        }
        /** Shockwave: every hostile mob still inside the box is shoved outward, whatever it is, until it is past the edge; no damage. */
        private void shockPush(ServerLevel l){
            Vec3 c=position();double r=radius();
            for(LivingEntity e:enemies(l,box())){
                double dx=e.getX()-c.x,dz=e.getZ()-c.z,d=Math.sqrt(dx*dx+dz*dz);
                if(d>=r)continue;
                if(d<.2){double a=random.nextDouble()*Math.PI*2;dx=Math.cos(a);dz=Math.sin(a);d=1;}
                double v=CannonUpgrades.shockSpeed(d,r);var m=e.getDeltaMovement();
                e.setDeltaMovement(dx/d*v,e.onGround()?.3:m.y,dz/d*v);e.hurtMarked=true;
            }
        }
        /** While the star burns: hostile mobs in its cube glow, players in it work faster. Its light comes from hidden light blocks (placeLights). */
        private void starPulse(ServerLevel l){
            var area=box();
            for(LivingEntity e:enemies(l,area))e.addEffect(new MobEffectInstance(MobEffects.GLOWING,CannonUpgrades.STAR_GLOW_TICKS,0,true,false));
            for(Player p:l.getEntitiesOfClass(Player.class,area,Player::isAlive))p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,CannonUpgrades.STAR_HASTE_TICKS,1,true,false,true));
        }

        // ---- blocks a lingering effect puts in the world (and takes away again) -----------------------------------------------------
        /** The free spot on the ground in column x, z near height {@code cy}: the air block on the highest sturdy block within 3 blocks up or down; MIN_VALUE if none. */
        private static int groundY(ServerLevel l,BlockPos.MutableBlockPos pos,int x,int z,int cy){
            for(int y=cy+3;y>=cy-3;y--){
                pos.set(x,y,z);if(!l.getBlockState(pos).isAir())continue;
                pos.set(x,y-1,z);var below=l.getBlockState(pos);
                if(!below.isAir()&&below.isFaceSturdy(l,pos,Direction.UP))return y;
            }
            return Integer.MIN_VALUE;
        }
        /**
         * Covers the free ground of the box with {@code state} (fire or a snow layer) where it can stay, and lists every block it puts down.
         * Fire is never lit in or near the beacon zone ({@link CannonUpgrades#FIRE_ZONE_MARGIN}).
         */
        private void placeGround(ServerLevel l,BlockState state,boolean fire){
            var box=box();var zone=fire?BaseZone.box(l):null;var pos=new BlockPos.MutableBlockPos();int cy=blockPosition().getY();
            for(int x=Mth.floor(box.minX);x<Mth.ceil(box.maxX);x++)for(int z=Mth.floor(box.minZ);z<Mth.ceil(box.maxZ);z++){
                if(!l.hasChunkAt(pos.set(x,cy,z)))continue;
                int y=groundY(l,pos,x,z,cy);if(y==Integer.MIN_VALUE)continue;
                pos.set(x,y,z);
                if(zone!=null&&zone.inflate(CannonUpgrades.FIRE_ZONE_MARGIN).contains(x+.5,y+.5,z+.5))continue;
                BlockState put=fire?net.minecraft.world.level.block.BaseFireBlock.getState(l,pos):state;
                if(!put.canSurvive(l,pos))continue;
                l.setBlock(pos,put,Block.UPDATE_ALL);placed.add(pos.asLong());
            }
        }
        /** Hidden light blocks (light level 15) every few blocks over the star's cube, two blocks above the ground, so the whole area is lit. */
        private void placeLights(ServerLevel l){
            var box=box();var pos=new BlockPos.MutableBlockPos();int cy=blockPosition().getY(),step=CannonUpgrades.STAR_LIGHT_SPACING;
            var light=Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL,15);
            for(int x=Mth.floor(box.minX);x<Mth.ceil(box.maxX);x+=step)for(int z=Mth.floor(box.minZ);z<Mth.ceil(box.maxZ);z+=step){
                if(!l.hasChunkAt(pos.set(x,cy,z)))continue;
                int y=groundY(l,pos,x,z,cy);y=y==Integer.MIN_VALUE?cy+2:y+2;
                pos.set(x,y,z);if(!l.getBlockState(pos).isAir())continue;
                l.setBlock(pos,light,Block.UPDATE_ALL);placed.add(pos.asLong());
            }
        }
        /** Takes away the fire, snow and light this flare put down (only where they are still there). */
        private void clearPlaced(ServerLevel l){
            var pos=new BlockPos.MutableBlockPos();
            for(long at:placed){
                pos.set(at);if(!l.hasChunkAt(pos))continue;var state=l.getBlockState(pos);
                if(state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.SNOW)||state.is(Blocks.LIGHT))l.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            }
            placed.clear();
        }
        private void bolt(ServerLevel level,double x,double y,double z){
            var bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt==null)return;
            bolt.moveTo(x,y,z);bolt.setVisualOnly(true);if(getOwner() instanceof ServerPlayer sp)bolt.setCause(sp);level.addFreshEntity(bolt);
        }

        // ---- bunker buster: every block inside the red box goes, layer by layer from the top -----------------------------
        private void beginCarve(ServerLevel level){
            var b=box();
            carveX0=Mth.floor(b.minX);carveX1=Mth.ceil(b.maxX)-1;carveZ0=Mth.floor(b.minZ);carveZ1=Mth.ceil(b.maxZ)-1;
            carveY=Math.min(level.getMaxBuildHeight()-1,Mth.ceil(b.maxY)-1);carveFloor=Math.max(level.getMinBuildHeight(),Mth.floor(b.minY));
        }
        private void carveStep(ServerLevel level){
            int layers=carveY-carveFloor+1;if(layers<=0){carveY=Integer.MIN_VALUE;return;}
            int perTick=Math.max(1,(Mth.ceil(box().getYsize())+CARVE_TICKS-1)/CARVE_TICKS);
            var pos=new BlockPos.MutableBlockPos();
            for(int i=0;i<perTick&&carveY>=carveFloor;i++,carveY--){
                int dug=0;
                for(int x=carveX0;x<=carveX1;x++)for(int z=carveZ0;z<=carveZ1;z++){
                    pos.set(x,carveY,z);
                    if(!level.hasChunkAt(pos))continue;
                    var state=level.getBlockState(pos);
                    if(state.isAir()||!state.getFluidState().isEmpty()||state.getDestroySpeed(level,pos)<0||bunkerSpares(state))continue;
                    level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);dug++;
                }
                if(dug>0&&carveY%3==0)Blasts.dig(level,new Vec3(getX(),carveY+.5,getZ()),radius());
            }
            if(carveY<carveFloor){
                // let sand, water and torches around the hole notice it
                int top=Math.min(level.getMaxBuildHeight()-1,Mth.ceil(box().maxY)-1);
                for(int x=carveX0;x<=carveX1;x++)for(int z=carveZ0;z<=carveZ1;z++){touch(level,pos,x,carveFloor,z);touch(level,pos,x,top,z);}
                for(int y=carveFloor;y<=top;y++){
                    for(int x=carveX0;x<=carveX1;x++){touch(level,pos,x,y,carveZ0);touch(level,pos,x,y,carveZ1);}
                    for(int z=carveZ0;z<=carveZ1;z++){touch(level,pos,carveX0,y,z);touch(level,pos,carveX1,y,z);}
                }
                Blasts.deep(level,new Vec3(getX(),carveFloor+.5,getZ()),radius());
                carveY=Integer.MIN_VALUE;
            }
        }
        private static void touch(ServerLevel level,BlockPos.MutableBlockPos pos,int x,int y,int z){pos.set(x,y,z);if(level.hasChunkAt(pos))level.updateNeighborsAt(pos,Blocks.AIR);}

        // ---- return: a portal that only its owner can step through ---------------------------------------------
        private void portal(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity live,int t){
            if(openAt<0){
                // the portal only opens once the cannon has turned onto the flare and fired its shell
                if(!gunReady(server,owner,live,t))return;
                shot(server,owner,live,true);releaseGun(server);
                openAt=t;entityData.set(OPEN,true);
                server.playSound(null,getX(),getY(),getZ(),SoundEvents.ENDERMAN_TELEPORT,SoundSource.NEUTRAL,.7f,.6f);
            }
            int age=t-openAt;
            if(t%3==0)server.sendParticles(ParticleTypes.PORTAL,getX(),getY()+1,getZ(),4,.3,.8,.3,.3);
            if(age>=life){server.sendParticles(ParticleTypes.POOF,getX(),getY()+1,getZ(),12,.3,.8,.3,.02);discard();return;}
            if(arm>0){arm--;return;}
            if(cooldown>0){cooldown--;return;}
            if(owner!=null&&owner.level()==server&&Math.abs(owner.getX()-getX())<.7&&Math.abs(owner.getZ()-getZ())<.7&&owner.getY()>getY()-1&&owner.getY()<getY()+2){
                if(dest!=null){                                  // the way back to where the flare was thrown
                    var target=server.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(destDim)));
                    if(target!=null){SupportCalls.travel(owner,target,dest,owner.getYRot());discard();}
                    return;
                }
                var home=server.getServer().overworld();
                Vec3 back=cfg.longPortal()?SupportCalls.backPortalSpot(home,owner.getUUID()):null;
                Vec3 spot=SupportCalls.finishReturn(owner);
                if(spot==null){cooldown=100;return;}
                if(back!=null)backPortal(home,owner,back,position(),server.dimension().location().toString(),cfg,life-age);
                discard();
            }
        }

        @Override public void addAdditionalSaveData(CompoundTag n){
            super.addAdditionalSaveData(n);n.putInt("Kind",entityData.get(KIND));n.putBoolean("Landed",landed());n.putInt("LandedTicks",landedTicks);n.put("Cargo",cargo);
            n.putInt("StartAt",startAt);n.putBoolean("Spawned",spawned);n.putBoolean("Planned",planned);n.putBoolean("Outside",outside);n.putInt("Total",total);n.putInt("SpawnAt",spawnAt);n.putInt("Clear",clear);
            n.putInt("TurnStart",turnStart);n.putInt("GunAt",gunAt);n.putInt("OpenAt",openAt);n.putInt("FireTick",fireTick);
            n.putInt("EffectFrom",effectFrom);n.putInt("EffectUntil",effectUntil);n.putInt("PushUntil",pushUntil);
            if(!placed.isEmpty())n.putLongArray("Placed",placed.stream().mapToLong(Long::longValue).toArray());
            n.putInt("Type",cfg.type().ordinal());n.putInt("Volleys",cfg.volleys());n.putInt("VolleyLevel",cfg.volleyLevel());n.putInt("Interval",cfg.interval());n.putFloat("Damage",cfg.damage());n.putInt("Aoe",cfg.aoe());
            n.putBoolean("Tunnel",cfg.tunnel());n.putBoolean("Slow",cfg.slow());n.putBoolean("LongPortal",cfg.longPortal());n.putBoolean("Aura",cfg.aura());
            n.putInt("Life",life);n.putInt("AuraLeft",auraLeft);if(parcelId!=null)n.putUUID("Parcel",parcelId);
            if(carveY!=Integer.MIN_VALUE){n.putInt("CarveY",carveY);n.putInt("CarveFloor",carveFloor);n.putInt("CarveX0",carveX0);n.putInt("CarveX1",carveX1);n.putInt("CarveZ0",carveZ0);n.putInt("CarveZ1",carveZ1);}
            if(dest!=null){n.putDouble("DestX",dest.x);n.putDouble("DestY",dest.y);n.putDouble("DestZ",dest.z);n.putString("DestDim",destDim);}
        }
        @Override public void readAdditionalSaveData(CompoundTag n){
            super.readAdditionalSaveData(n);entityData.set(KIND,n.getInt("Kind"));entityData.set(LANDED,n.getBoolean("Landed"));landedTicks=n.getInt("LandedTicks");cargo=n.getList("Cargo",Tag.TAG_COMPOUND);
            startAt=n.contains("StartAt")?n.getInt("StartAt"):-1;spawned=n.getBoolean("Spawned");planned=n.getBoolean("Planned");outside=n.getBoolean("Outside");total=n.getInt("Total");spawnAt=n.getInt("SpawnAt");clear=n.getInt("Clear");
            turnStart=n.contains("TurnStart")?n.getInt("TurnStart"):-1;gunAt=n.contains("GunAt")?n.getInt("GunAt"):-1;openAt=n.contains("OpenAt")?n.getInt("OpenAt"):-1;fireTick=n.contains("FireTick")?n.getInt("FireTick"):-1;
            var type=CannonUpgrades.FireType.of(n.getInt("Type"));
            cfg=new CannonUpgrades.Config(type,n.contains("Volleys")?n.getInt("Volleys"):CannonUpgrades.volleys(type,0),n.contains("Interval")?n.getInt("Interval"):SupportRules.FIRE_INTERVAL_TICKS,n.contains("Damage")?n.getFloat("Damage"):1f,n.getBoolean("Tunnel"),n.getBoolean("Slow"),n.getBoolean("LongPortal"),n.getBoolean("Aura"),n.getInt("Aoe"),n.getInt("VolleyLevel"));
            effectFrom=n.contains("EffectFrom")?n.getInt("EffectFrom"):-1;effectUntil=n.contains("EffectUntil")?n.getInt("EffectUntil"):-1;pushUntil=n.contains("PushUntil")?n.getInt("PushUntil"):-1;
            placed.clear();for(long l:n.getLongArray("Placed"))placed.add(l);
            entityData.set(TYPE,type.ordinal());entityData.set(RADIUS,(float)cfg.radius());entityData.set(OPEN,openAt>=0);
            life=n.contains("Life")?n.getInt("Life"):CannonUpgrades.portalTicks(cfg.longPortal());auraLeft=n.contains("AuraLeft")?n.getInt("AuraLeft"):-1;if(n.hasUUID("Parcel"))parcelId=n.getUUID("Parcel");
            if(n.contains("CarveY")){carveY=n.getInt("CarveY");carveFloor=n.getInt("CarveFloor");carveX0=n.getInt("CarveX0");carveX1=n.getInt("CarveX1");carveZ0=n.getInt("CarveZ0");carveZ1=n.getInt("CarveZ1");}
            if(n.contains("DestX")){dest=new Vec3(n.getDouble("DestX"),n.getDouble("DestY"),n.getDouble("DestZ"));destDim=n.getString("DestDim");}
            setNoGravity(landed());
        }
        @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    }
}
