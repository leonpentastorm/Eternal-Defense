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
import java.util.ArrayList;
import java.util.List;

/**
 * The three flares. All of them are thrown and cost nothing to throw (the price is paid when buying them):
 * a Support Flare sends the platform's whole supply grid, a Return Flare opens a portal home, a Fire Support Flare shells its own spot.
 */
final class SupportFlares {
    private SupportFlares(){}

    /** The red box fire support covers: every hostile mob touching it takes the full damage of every shell. */
    static AABB blastBox(Vec3 c){
        double r=SupportRules.BLAST_RADIUS;
        return new AABB(c.x-r,c.y-0.5,c.z-r,c.x+r,c.y+r,c.z+r);
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
            ListTag cargo=kind==SupportCalls.Kind.SUPPLY?SupportCalls.takeGrid(sp):new ListTag();
            SupportCalls.announce(sp,kind);
            var flare=new FlareEntity(ArsenalBeacon.FLARE.get(),level);
            flare.setOwner(player);flare.setPos(player.getX(),player.getEyeY()-.1,player.getZ());
            flare.configure(kind,cargo);
            flare.shootFromRotation(player,player.getXRot(),player.getYRot(),0f,1.1f,1f);
            level.addFreshEntity(flare);
            level.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.FIREWORK_ROCKET_LAUNCH,SoundSource.PLAYERS,.8f,1.2f);
            if(!player.getAbilities().instabuild)stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag f){
            lines.add(Component.translatable("tooltip.arsenal_beacon.flare."+kind.id).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.arsenal_beacon.flare.free").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** A thrown flare. It lands, then runs its call: a parcel countdown, a portal, or six exact shells. */
    static final class FlareEntity extends ThrowableItemProjectile {
        static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> LANDED=SynchedEntityData.defineId(FlareEntity.class,EntityDataSerializers.BOOLEAN);
        private ListTag cargo=new ListTag();
        private int airTicks,landedTicks,startAt=-1,total,spawnAt,clear;
        private boolean outside,fired,spawned,planned;private int cooldown;
        FlareEntity(EntityType<? extends FlareEntity> type,Level level){super(type,level);}
        void configure(SupportCalls.Kind kind,ListTag cargo){entityData.set(KIND,kind.ordinal());this.cargo=cargo;}
        SupportCalls.Kind kind(){return SupportCalls.Kind.of(entityData.get(KIND));}
        boolean landed(){return entityData.get(LANDED);}
        @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(KIND,0);entityData.define(LANDED,false);}
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
        @Override public AABB getBoundingBoxForCulling(){return landed()&&kind()==SupportCalls.Kind.FIRE?blastBox(position()).inflate(1):super.getBoundingBoxForCulling();}
        @Override public void remove(RemovalReason reason){
            if(!level().isClientSide&&reason.shouldDestroy()&&!spawned&&!cargo.isEmpty()&&level() instanceof ServerLevel server&&getOwner()!=null){
                var items=new ArrayList<ItemStack>();for(var t:cargo)items.add(ItemStack.of((CompoundTag)t));
                cargo=new ListTag();SupportCalls.giveBack(server,getOwner().getUUID(),position(),items);
            }
            super.remove(reason);
        }
        private ServerPlayer owner(ServerLevel server){return getOwner() instanceof ServerPlayer p&&p.isAlive()?server.getServer().getPlayerList().getPlayer(p.getUUID()):null;}
        @Override public void tick(){
            if(landed()){setDeltaMovement(Vec3.ZERO);baseTick();}else super.tick();
            if(!(level() instanceof ServerLevel server))return;
            if(!landed()){if(++airTicks>400)discard();return;}
            int t=landedTicks++;
            var kind=kind();var owner=owner(server);
            var cannon=getOwner()==null?null:SupportCalls.cannon(server,getOwner().getUUID());
            if(t==0&&cannon!=null)cannon.aimAt(server.getServer().overworld(),position());
            if(t%2==0)server.sendParticles(kind==SupportCalls.Kind.FIRE?ParticleTypes.FLAME:kind==SupportCalls.Kind.SUPPLY?ParticleTypes.END_ROD:ParticleTypes.WITCH,getX(),getY()+.25,getZ(),2,.06,.15,.06,.02);
            if(t%6==0)server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,getX(),getY()+.3,getZ(),0,0,.07,0,1);
            switch(kind){
                case SUPPLY->supply(server,owner,cannon,t);
                case FIRE->fire(server,cannon,t);
                case RETURN->portal(server,owner,cannon,t);
            }
        }

        // ---- supply: countdown in chat, the cannon fires, the parcel arrives ------------------------------
        private void supply(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity cannon,int t){
            if(!planned){
                planned=true;clear=SupportCalls.clearAbove(server,blockPosition());
                outside=server.canSeeSky(blockPosition())&&clear>=SupportRules.PARCEL_MIN_DROP;
                total=SupportRules.supplyTicks(outside);spawnAt=outside?SupportRules.parcelSpawnTick(clear):total;
            }
            if(owner!=null&&t%20==0){
                int seconds=(total-t+19)/20;
                if(seconds>0)owner.sendSystemMessage(Component.translatable("gui.arsenal_beacon.support.countdown",seconds).withStyle(ChatFormatting.YELLOW));
            }
            if(!fired&&(cannon==null||cannon.ready()&&t>=10||t>=spawnAt-5)){fired=true;if(cannon!=null)cannon.fire(server.getServer().overworld());}
            if(!spawned&&t>=spawnAt){
                spawned=true;SupportCalls.deliver(server,owner,position(),cargo,outside,clear);cargo=new ListTag();
            }
            if(t>=total)discard();
        }

        // ---- fire support: six shells on exactly this spot ---------------------------------------------------
        private void fire(ServerLevel server,SupportCannon.CannonEntity cannon,int t){
            if(startAt<0&&(cannon==null||cannon.ready()&&t>=10||t>=240))startAt=t+10;
            if(startAt<0)return;
            int rel=t-startAt;
            if(rel>=0&&rel%SupportRules.FIRE_INTERVAL_TICKS==0&&rel/SupportRules.FIRE_INTERVAL_TICKS<SupportRules.FIRE_BLASTS){if(cannon!=null)cannon.fire(server.getServer().overworld());}
            int land=rel-SupportRules.BLAST_FLIGHT_TICKS;
            if(land>=0&&land%SupportRules.FIRE_INTERVAL_TICKS==0&&land/SupportRules.FIRE_INTERVAL_TICKS<SupportRules.FIRE_BLASTS)blast(server);
            if(land>SupportRules.FIRE_INTERVAL_TICKS*(SupportRules.FIRE_BLASTS-1)+20)discard();
        }
        private void blast(ServerLevel level){
            Vec3 c=position();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,c.x,c.y+.5,c.z,1,0,0,0,0);
            level.sendParticles(ParticleTypes.FLAME,c.x,c.y+.4,c.z,60,SupportRules.BLAST_RADIUS*.4,.5,SupportRules.BLAST_RADIUS*.4,.1);
            level.playSound(null,c.x,c.y,c.z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,3f,.9f);
            for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,blastBox(c),e->e instanceof Enemy&&e.isAlive()))
                e.hurt(level.damageSources().explosion(this,getOwner()),SupportRules.FIRE_DAMAGE);
        }

        // ---- return: a portal that only its owner can step through ---------------------------------------------
        private void portal(ServerLevel server,ServerPlayer owner,SupportCannon.CannonEntity cannon,int t){
            if(!fired&&(cannon==null||cannon.ready()&&t>=10||t>=200)){fired=true;if(cannon!=null)cannon.fire(server.getServer().overworld());}
            if(t%3==0)server.sendParticles(ParticleTypes.PORTAL,getX(),getY()+1,getZ(),4,.3,.8,.3,.3);
            if(t>=SupportRules.PORTAL_LIFETIME_TICKS){server.sendParticles(ParticleTypes.POOF,getX(),getY()+1,getZ(),12,.3,.8,.3,.02);discard();return;}
            if(owner!=null&&owner.level()==server&&Math.abs(owner.getX()-getX())<.7&&Math.abs(owner.getZ()-getZ())<.7&&owner.getY()>getY()-1&&owner.getY()<getY()+2){
                if(cooldown>0)cooldown--;
                else if(SupportCalls.finishReturn(owner))discard();
                else cooldown=100;
            }
        }

        @Override public void addAdditionalSaveData(CompoundTag n){
            super.addAdditionalSaveData(n);n.putInt("Kind",entityData.get(KIND));n.putBoolean("Landed",landed());n.putInt("LandedTicks",landedTicks);n.put("Cargo",cargo);
            n.putInt("StartAt",startAt);n.putBoolean("Fired",fired);n.putBoolean("Spawned",spawned);n.putBoolean("Planned",planned);n.putBoolean("Outside",outside);n.putInt("Total",total);n.putInt("SpawnAt",spawnAt);n.putInt("Clear",clear);
        }
        @Override public void readAdditionalSaveData(CompoundTag n){
            super.readAdditionalSaveData(n);entityData.set(KIND,n.getInt("Kind"));entityData.set(LANDED,n.getBoolean("Landed"));landedTicks=n.getInt("LandedTicks");cargo=n.getList("Cargo",Tag.TAG_COMPOUND);
            startAt=n.contains("StartAt")?n.getInt("StartAt"):-1;fired=n.getBoolean("Fired");spawned=n.getBoolean("Spawned");planned=n.getBoolean("Planned");outside=n.getBoolean("Outside");total=n.getInt("Total");spawnAt=n.getInt("SpawnAt");clear=n.getInt("Clear");
            setNoGravity(landed());
        }
        @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    }
}
