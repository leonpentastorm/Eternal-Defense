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
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import java.util.UUID;
import java.util.Optional;
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
            if(kind==SupportCalls.Kind.FIRE)lines.add(Component.translatable("tooltip.arsenal_beacon.flare.yours").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Players are never hurt by a flare (its shells, bombs and bolts); events cancel the damage because explosions have no player filter. */
    public static final class Safety {
        @net.minecraftforge.eventbus.api.SubscribeEvent public void protect(net.minecraftforge.event.entity.living.LivingAttackEvent e){
            if(e.getEntity() instanceof Player&&e.getSource().getDirectEntity() instanceof FlareEntity)e.setCanceled(true);
        }
    }
    /** Keeps the blocks of the base itself out of a Bunker Buster. */
    static final ExplosionDamageCalculator BUNKER_RULES=new ExplosionDamageCalculator(){
        @Override public Optional<Float> getBlockExplosionResistance(Explosion e,BlockGetter g,BlockPos pos,BlockState state,FluidState fluid){
            if(ArsenalStructures.owns(state)||state.is(ArsenalBeacon.STRUCTURE_PART.get())||state.is(ArsenalBeacon.BEACON.get()))return Optional.of(3600000f);
            return super.getBlockExplosionResistance(e,g,pos,state,fluid);
        }
    };

    /** A thrown flare. It lands, then runs its call: a parcel countdown, a portal, or the owner's chosen fire support. */
    static final class FlareEntity extends ThrowableItemProjectile {
        static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.INT);
        static final EntityDataAccessor<Integer> TYPE=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.INT);
        static final EntityDataAccessor<Integer> RADIUS=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> LANDED=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.BOOLEAN);
        private ListTag cargo=new ListTag();
        private CannonUpgrades.Config cfg=CannonUpgrades.Config.of(new int[CannonUpgrades.Upgrade.values().length],CannonUpgrades.FireType.EXPLOSION);
        private int airTicks,landedTicks,startAt=-1,total,spawnAt,clear,life,arm,auraLeft=-1;
        private boolean outside,fired,spawned,planned;private int cooldown;
        private UUID parcelId;
        /** A "back" portal leads to this spot (in this dimension) instead of to the platform. */
        private Vec3 dest;private String destDim="";
        FlareEntity(EntityType<? extends FlareEntity> type,Level level){super(type,level);}
        void configure(SupportCalls.Kind kind,ListTag cargo,CannonUpgrades.Config cfg){
            entityData.set(KIND,kind.ordinal());entityData.set(TYPE,cfg.type().ordinal());entityData.set(RADIUS,CannonUpgrades.radius(cfg.type()));
            this.cargo=cargo;this.cfg=cfg;life=CannonUpgrades.portalTicks(cfg.longPortal());
        }
        SupportCalls.Kind kind(){return SupportCalls.Kind.of(entityData.get(KIND));}
        CannonUpgrades.FireType type(){return CannonUpgrades.FireType.of(entityData.get(TYPE));}
        int radius(){return entityData.get(RADIUS);}
        boolean landed(){return entityData.get(LANDED);}
        /** Opens a portal back to {@code to} that lasts {@code ticks}; owner only. */
        static FlareEntity backPortal(ServerLevel level,Player owner,Vec3 at,Vec3 to,String toDim,CannonUpgrades.Config cfg,int ticks){
            var f=new FlareEntity(ArsenalBeacon.FLARE.get(),level);
            f.setOwner(owner);f.configure(SupportCalls.Kind.RETURN,new ListTag(),cfg);f.life=ticks;f.dest=to;f.destDim=toDim;f.arm=60;
            f.setPos(at.x,at.y,at.z);f.setNoGravity(true);f.entityData.set(LANDED,true);level.addFreshEntity(f);return f;
        }
        @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(KIND,0);entityData.define(TYPE,0);entityData.define(RADIUS,SupportRules.BLAST_RADIUS);entityData.define(LANDED,false);}
        @Override protected Item getDefaultItem(){return switch(kind()){case SUPPLY->ArsenalBeacon.SUPPLY_FLARE.get();case RETURN->ArsenalBeacon.RETURN_FLARE.get();case FIRE->ArsenalBeacon.FIRE_FLARE.get();};}
        @Override protected float getGravity(){return landed()?0f:0.05f;}
        @Override protected boolean canHitEntity(net.minecraft.world.entity.Entity e){return false;}
        @Override protected void onHitEntity(EntityHitResult r){}
        @Override protected void onHitBlock(BlockHitResult hit){
            if(landed())return;
            if(hit.getDirection()==Direction.UP){
                // it comes to rest exactly where it touched the ground: everything the call does is measured from this point
                Vec3 at=hit.getLocation();setPos(at.x,at.y,at.z);setDeltaMovement(Vec3.ZERO);setNoGravity(true);entityData.set(LANDED,true);
                if(!level().isClientSide)level().playSound(null,getX(),getY(),getZ(),SoundEvents.FLINTANDSTEEL_USE,SoundSource.NEUTRAL,1f,.8f);
            }else{
                Vec3 m=getDeltaMovement();setDeltaMovement(-m.x*.2,Math.min(m.y,0)*.5,-m.z*.2);   // slides down walls and bounces off ceilings
            }
        }
        @Override public AABB getBoundingBoxForCulling(){return landed()&&kind()==SupportCalls.Kind.FIRE?blastBox(position(),radius()).inflate(1):super.getBoundingBoxForCulling();}
        @Override public void remove(RemovalReason reason){
            if(!level().isClientSide&&reason.shouldDestroy()&&!spawned&&!cargo.isEmpty()&&level() instanceof ServerLevel server&&getOwner()!=null){
                var items=new ArrayList<ItemStack>();for(var t:cargo)items.add(ItemStack.of((CompoundTag)t));
                cargo=new ListTag();SupportCalls.giveBack(server,getOwner().getUUID(),position(),items);
            }
            super.remove(reason);
        }
        private ServerPlayer owner(ServerLevel server){return getOwner()==null?null:server.getServer().getPlayerList().getPlayer(getOwner().getUUID());}
        @Override public void tick(){
            if(landed()){setDeltaMovement(Vec3.ZERO);baseTick();}else super.tick();
            if(!(level() instanceof ServerLevel server))return;
            if(!landed()){if(++airTicks>400)discard();return;}
            int t=landedTicks++;
            var kind=kind();var owner=owner(server);
            var cannon=getOwner()==null||dest!=null?null:SupportCalls.cannon(server,getOwner().getUUID());
            if(t==0&&cannon!=null)cannon.aimAt(server.getServer().overworld(),position());
            if(t==0&&kind==SupportCalls.Kind.FIRE&&!cfg.tunnel()&&!server.canSeeSky(blockPosition())){underground(server,owner);return;}
            if(t%2==0)server.sendParticles(kind==SupportCalls.Kind.FIRE?ParticleTypes.FLAME:kind==SupportCalls.Kind.SUPPLY?ParticleTypes.END_ROD:ParticleTypes.WITCH,getX(),getY()+.25,getZ(),2,.06,.15,.06,.02);
            if(t%6==0)server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,getX(),getY()+.3,getZ(),0,0,.07,0,1);
            if(kind==SupportCalls.Kind.FIRE&&cfg.slow()&&t%10==0)slowField(server);
            switch(kind){
                case SUPPLY->supply(server,owner,cannon,t);
                case FIRE->fire(server,cannon,t);
                case RETURN->portal(server,owner,cannon,t);
            }
        }

        /** Without Quantum Tunneling the cannon cannot reach a flare that landed below ground: the flare is handed back. */
        private void underground(ServerLevel server,ServerPlayer owner){
            if(getOwner()!=null){
                if(owner!=null)SupportCalls.refuse(owner,"underground");
                SupportCalls.giveBack(server,getOwner().getUUID(),position(),List.of(new ItemStack(ArsenalBeacon.FIRE_FLARE.get())));
            }
            server.sendParticles(ParticleTypes.POOF,getX(),getY()+.3,getZ(),10,.2,.2,.2,.02);discard();
        }
        private void slowField(ServerLevel server){
            for(LivingEntity e:server.getEntitiesOfClass(LivingEntity.class,blastBox(position(),radius()),e->e instanceof Enemy&&e.isAlive()))
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,1,true,true));
            if(landedTicks%20==0){double r=radius();for(int i=0;i<16;i++){double a=i*Math.PI/8;server.sendParticles(ParticleTypes.SNOWFLAKE,getX()+Math.cos(a)*r,getY()+.2,getZ()+Math.sin(a)*r,1,0,0,0,0);}}
        }

        // ---- supply: countdown in chat, the cannon fires, the parcel arrives, an optional healing aura -------------
        private void supply(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity cannon,int t){
            if(!planned){
                planned=true;clear=SupportCalls.clearAbove(server,blockPosition());
                outside=server.canSeeSky(blockPosition())&&clear>=SupportRules.PARCEL_MIN_DROP;
                total=SupportRules.supplyTicks(outside);spawnAt=outside?SupportRules.parcelSpawnTick(clear):total;
            }
            if(owner!=null&&t%20==0&&t<total){
                int seconds=(total-t+19)/20;
                if(seconds>0)owner.sendSystemMessage(Component.translatable("gui.arsenal_beacon.support.countdown",seconds).withStyle(ChatFormatting.YELLOW));
            }
            if(!fired&&(cannon==null||cannon.armed()&&t>=10||t>=spawnAt-5)){fired=true;if(cannon!=null)cannon.fire(server.getServer().overworld());}
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
        private void fire(ServerLevel server,SupportCannon.CannonEntity cannon,int t){
            if(startAt<0&&(cannon==null||cannon.armed()||t>=240))startAt=t+10;
            if(startAt<0)return;
            int rel=t-startAt,interval=cfg.interval(),n=cfg.volleys(),flight=cfg.type()==CannonUpgrades.FireType.BUNKER?30:SupportRules.BLAST_FLIGHT_TICKS;
            if(rel>=0&&rel%interval==0&&rel/interval<n){if(cannon!=null)cannon.fire(server.getServer().overworld());if(cfg.type()==CannonUpgrades.FireType.BUNKER)server.playSound(null,getX(),getY(),getZ(),SoundEvents.FIREWORK_ROCKET_LAUNCH,SoundSource.BLOCKS,3f,.4f);}
            int land=rel-flight;
            if(land>=0&&land%interval==0&&land/interval<n)resolve(server);
            if(land>interval*(n-1)+20)discard();
        }
        private void resolve(ServerLevel level){
            Vec3 c=position();var type=cfg.type();double r=radius();var box=blastBox(c,r);float dmg=SupportRules.FIRE_DAMAGE*cfg.damage();
            switch(type){
                case EXPLOSION->{
                    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,c.x,c.y+.5,c.z,1,0,0,0,0);
                    level.sendParticles(ParticleTypes.FLAME,c.x,c.y+.4,c.z,60,r*.4,.5,r*.4,.1);
                    level.playSound(null,c.x,c.y,c.z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,3f,.9f);
                    for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,box,e->e instanceof Enemy&&e.isAlive()))e.hurt(level.damageSources().explosion(this,getOwner()),dmg);
                }
                case ARROW->{
                    level.sendParticles(ParticleTypes.FIREWORK,c.x,c.y+r*2,c.z,30,r*.5,.4,r*.5,.05);
                    level.playSound(null,c.x,c.y+r,c.z,SoundEvents.FIREWORK_ROCKET_BLAST,SoundSource.BLOCKS,3f,.8f);
                    // a shower: a few arrows come down on every hostile mob in the box, the rest fall at random
                    var spots=new ArrayList<double[]>();int count=0;
                    for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,box,e->e instanceof Enemy&&e.isAlive())){if(count++>=10)break;for(int k=0;k<3;k++)spots.add(new double[]{e.getX()+(random.nextDouble()-.5)*.5,e.getZ()+(random.nextDouble()-.5)*.5});}
                    for(int k=0;k<8;k++)spots.add(new double[]{c.x+(random.nextDouble()*2-1)*r,c.z+(random.nextDouble()*2-1)*r});
                    for(var at:spots){
                        double y=c.y+r*2+random.nextDouble()*2;
                        var arrow=new Arrow(level,at[0],y,at[1]){@Override protected boolean canHitEntity(net.minecraft.world.entity.Entity e){return e instanceof Enemy&&super.canHitEntity(e);}};
                        arrow.setOwner(getOwner());arrow.pickup=AbstractArrow.Pickup.DISALLOWED;arrow.setBaseDamage(2.5*cfg.damage());
                        arrow.setDeltaMovement((random.nextDouble()-.5)*.05,-1.8,(random.nextDouble()-.5)*.05);level.addFreshEntity(arrow);
                    }
                }
                case NARUKAMI->{
                    int struck=0;
                    for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,box,e->e instanceof Enemy&&e.isAlive())){
                        if(struck++>=12)break;
                        bolt(level,e.getX(),e.getY(),e.getZ());e.hurt(level.damageSources().lightningBolt(),dmg);e.setSecondsOnFire(3);
                    }
                    if(struck==0)bolt(level,c.x+(random.nextDouble()*2-1)*r,c.y,c.z+(random.nextDouble()*2-1)*r);
                }
                case BUNKER->{
                    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,c.x,c.y+1,c.z,3,2,1,2,0);
                    level.explode(this,level.damageSources().explosion(this,getOwner()),BUNKER_RULES,c.x,c.y,c.z,(float)r,false,Level.ExplosionInteraction.TNT);
                    for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,box,e->e instanceof Enemy&&e.isAlive()))e.hurt(level.damageSources().explosion(this,getOwner()),dmg*2);
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
                    for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,box,e->e instanceof Enemy&&e.isAlive())){
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER,(int)(160*k),1));e.addEffect(new MobEffectInstance(MobEffects.POISON,(int)(200*k),1));
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,(int)(300*k),1));e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,(int)(200*k),2));
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,(int)(200*k),0));e.addEffect(new MobEffectInstance(MobEffects.GLOWING,(int)(300*k),0));
                    }
                }
            }
        }
        private void bolt(ServerLevel level,double x,double y,double z){
            var bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt==null)return;
            bolt.moveTo(x,y,z);bolt.setVisualOnly(true);if(getOwner() instanceof ServerPlayer sp)bolt.setCause(sp);level.addFreshEntity(bolt);
        }

        // ---- return: a portal that only its owner can step through ---------------------------------------------
        private void portal(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity cannon,int t){
            if(dest==null&&!fired&&(cannon==null||cannon.armed()||t>=240)){fired=true;if(cannon!=null)cannon.fire(server.getServer().overworld());}
            if(t%3==0)server.sendParticles(ParticleTypes.PORTAL,getX(),getY()+1,getZ(),4,.3,.8,.3,.3);
            if(t>=life){server.sendParticles(ParticleTypes.POOF,getX(),getY()+1,getZ(),12,.3,.8,.3,.02);discard();return;}
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
                if(back!=null)backPortal(home,owner,back,position(),server.dimension().location().toString(),cfg,life-t);
                discard();
            }
        }

        @Override public void addAdditionalSaveData(CompoundTag n){
            super.addAdditionalSaveData(n);n.putInt("Kind",entityData.get(KIND));n.putBoolean("Landed",landed());n.putInt("LandedTicks",landedTicks);n.put("Cargo",cargo);
            n.putInt("StartAt",startAt);n.putBoolean("Fired",fired);n.putBoolean("Spawned",spawned);n.putBoolean("Planned",planned);n.putBoolean("Outside",outside);n.putInt("Total",total);n.putInt("SpawnAt",spawnAt);n.putInt("Clear",clear);
            n.putInt("Type",cfg.type().ordinal());n.putInt("Volleys",cfg.volleys());n.putInt("Interval",cfg.interval());n.putFloat("Damage",cfg.damage());
            n.putBoolean("Tunnel",cfg.tunnel());n.putBoolean("Slow",cfg.slow());n.putBoolean("LongPortal",cfg.longPortal());n.putBoolean("Aura",cfg.aura());
            n.putInt("Life",life);n.putInt("AuraLeft",auraLeft);if(parcelId!=null)n.putUUID("Parcel",parcelId);
            if(dest!=null){n.putDouble("DestX",dest.x);n.putDouble("DestY",dest.y);n.putDouble("DestZ",dest.z);n.putString("DestDim",destDim);}
        }
        @Override public void readAdditionalSaveData(CompoundTag n){
            super.readAdditionalSaveData(n);entityData.set(KIND,n.getInt("Kind"));entityData.set(LANDED,n.getBoolean("Landed"));landedTicks=n.getInt("LandedTicks");cargo=n.getList("Cargo",Tag.TAG_COMPOUND);
            startAt=n.contains("StartAt")?n.getInt("StartAt"):-1;fired=n.getBoolean("Fired");spawned=n.getBoolean("Spawned");planned=n.getBoolean("Planned");outside=n.getBoolean("Outside");total=n.getInt("Total");spawnAt=n.getInt("SpawnAt");clear=n.getInt("Clear");
            var type=CannonUpgrades.FireType.of(n.getInt("Type"));
            cfg=new CannonUpgrades.Config(type,n.contains("Volleys")?n.getInt("Volleys"):CannonUpgrades.volleys(type,0),n.contains("Interval")?n.getInt("Interval"):SupportRules.FIRE_INTERVAL_TICKS,n.contains("Damage")?n.getFloat("Damage"):1f,n.getBoolean("Tunnel"),n.getBoolean("Slow"),n.getBoolean("LongPortal"),n.getBoolean("Aura"));
            entityData.set(TYPE,type.ordinal());entityData.set(RADIUS,CannonUpgrades.radius(type));
            life=n.contains("Life")?n.getInt("Life"):CannonUpgrades.portalTicks(cfg.longPortal());auraLeft=n.contains("AuraLeft")?n.getInt("AuraLeft"):-1;if(n.hasUUID("Parcel"))parcelId=n.getUUID("Parcel");
            if(n.contains("DestX")){dest=new Vec3(n.getDouble("DestX"),n.getDouble("DestY"),n.getDouble("DestZ"));destDim=n.getString("DestDim");}
            setNoGravity(landed());
        }
        @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    }
}
