package dev.createarsenal.beacon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;

/** The three flares. Support and Fire Support are thrown; the Return Flare is lit by holding use. */
final class SupportFlares {
    private SupportFlares(){}

    static final class FlareItem extends Item {
        final SupportCalls.Kind kind;
        FlareItem(SupportCalls.Kind kind){super(new Item.Properties().stacksTo(16).rarity(kind==SupportCalls.Kind.FIRE?Rarity.RARE:Rarity.UNCOMMON));this.kind=kind;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack stack=player.getItemInHand(hand);
            if(kind==SupportCalls.Kind.RETURN){
                if(level.isClientSide){player.startUsingItem(hand);return InteractionResultHolder.consume(stack);}
                if(!(player instanceof ServerPlayer sp))return InteractionResultHolder.fail(stack);
                String problem=SupportCalls.problem(sp,kind);
                if(problem!=null){SupportCalls.refuse(sp,problem,kind);return InteractionResultHolder.fail(stack);}
                player.startUsingItem(hand);return InteractionResultHolder.consume(stack);
            }
            if(level.isClientSide)return InteractionResultHolder.success(stack);
            if(!(player instanceof ServerPlayer sp))return InteractionResultHolder.fail(stack);
            ListTag snapshot=new ListTag();
            if(kind==SupportCalls.Kind.SUPPLY){snapshot=SupportCalls.beginSupply(sp);if(snapshot==null)return InteractionResultHolder.fail(stack);}
            else{
                String problem=SupportCalls.problem(sp,kind);if(problem!=null){SupportCalls.refuse(sp,problem,kind);return InteractionResultHolder.fail(stack);}
                if(!ArdentEnergy.spend(sp,SupportRules.FIRE_CALL_PRICE)){SupportCalls.refuse(sp,"no_energy",kind);return InteractionResultHolder.fail(stack);}
                SupportCalls.announce(sp,kind);SupportCalls.cannonFire(sp.serverLevel(),sp.position(),false);
                if(!sp.isCreative())sp.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.paid",SupportRules.FIRE_CALL_PRICE),true);
            }
            var flare=new FlareEntity(ArsenalBeacon.FLARE.get(),level);
            flare.setOwner(player);flare.setPos(player.getX(),player.getEyeY()-.1,player.getZ());
            flare.configure(kind,snapshot,new ItemStack(this));
            flare.shootFromRotation(player,player.getXRot(),player.getYRot(),0f,1.1f,1f);
            level.addFreshEntity(flare);
            level.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.FIREWORK_ROCKET_LAUNCH,SoundSource.PLAYERS,.8f,1.2f);
            // paying rebuilt the inventory stacks, so shrink what is held now, not the pre-payment reference
            ItemStack held=player.getItemInHand(hand);
            if(!player.getAbilities().instabuild)held.shrink(1);
            return InteractionResultHolder.consume(held);
        }
        @Override public int getUseDuration(ItemStack s){return kind==SupportCalls.Kind.RETURN?SupportRules.RETURN_CHANNEL_TICKS:0;}
        @Override public UseAnim getUseAnimation(ItemStack s){return kind==SupportCalls.Kind.RETURN?UseAnim.TOOT_HORN:UseAnim.NONE;}
        @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity user){
            if(kind==SupportCalls.Kind.RETURN&&!level.isClientSide&&user instanceof ServerPlayer sp){
                SupportCalls.finishReturn(sp);
                ItemStack held=sp.getItemInHand(sp.getUsedItemHand());
                if(!held.is(this))held=stack;
                if(!sp.isCreative())held.shrink(1);
                return held;
            }
            return stack;
        }
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag f){
            lines.add(Component.translatable("tooltip.arsenal_beacon.flare."+kind.id).withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.arsenal_beacon.flare.price",switch(kind){case SUPPLY->SupportRules.SUPPLY_CALL_FEE;case RETURN->SupportRules.RETURN_CALL_PRICE;case FIRE->SupportRules.FIRE_CALL_PRICE;}).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        }
    }

    /** A thrown flare. It lands, then either calls the parcel (Support) or shells the area (Fire Support). */
    static final class FlareEntity extends ThrowableItemProjectile {
        private SupportCalls.Kind kind=SupportCalls.Kind.SUPPLY;private ListTag snapshot=new ListTag();
        private boolean landed;private int landedTicks,blasts;
        FlareEntity(EntityType<? extends FlareEntity> type,Level level){super(type,level);}
        void configure(SupportCalls.Kind kind,ListTag snapshot,ItemStack stack){this.kind=kind;this.snapshot=snapshot;setItem(stack.copyWithCount(1));}
        @Override protected Item getDefaultItem(){return switch(kind){case SUPPLY->ArsenalBeacon.SUPPLY_FLARE.get();case RETURN->ArsenalBeacon.RETURN_FLARE.get();case FIRE->ArsenalBeacon.FIRE_FLARE.get();};}
        @Override protected float getGravity(){return landed?0f:0.05f;}
        @Override protected boolean canHitEntity(net.minecraft.world.entity.Entity e){return false;}
        @Override protected void onHitEntity(EntityHitResult r){}
        @Override protected void onHitBlock(BlockHitResult hit){
            if(landed)return;
            landed=true;Vec3 at=hit.getLocation();setPos(at.x,at.y+.05,at.z);setDeltaMovement(Vec3.ZERO);setNoGravity(true);
            if(!level().isClientSide)level().playSound(null,getX(),getY(),getZ(),SoundEvents.FLINTANDSTEEL_USE,SoundSource.NEUTRAL,1f,.8f);
        }
        @Override public void tick(){
            if(landed){setDeltaMovement(Vec3.ZERO);baseTick();}else super.tick();
            if(!(level() instanceof ServerLevel server))return;
            if(!landed)return;
            landedTicks++;
            var smoke=kind==SupportCalls.Kind.FIRE?ParticleTypes.FLAME:kind==SupportCalls.Kind.SUPPLY?ParticleTypes.END_ROD:ParticleTypes.WITCH;
            if(landedTicks%2==0)server.sendParticles(smoke,getX(),getY()+.25,getZ(),2,.06,.15,.06,.02);
            if(landedTicks%6==0)server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,getX(),getY()+.3,getZ(),0,0,.07,0,1);
            if(kind==SupportCalls.Kind.SUPPLY&&landedTicks>=SupportRules.SUPPLY_DELAY_TICKS){
                SupportCalls.deliver(server,getOwner() instanceof ServerPlayer p?p:null,position(),snapshot);discard();
            }else if(kind==SupportCalls.Kind.FIRE){
                if(landedTicks%SupportRules.FIRE_INTERVAL_TICKS==0&&blasts<SupportRules.FIRE_BLASTS){blast(server);blasts++;}
                if(blasts>=SupportRules.FIRE_BLASTS&&landedTicks>SupportRules.FIRE_INTERVAL_TICKS*SupportRules.FIRE_BLASTS+10)discard();
            }
        }
        private final double spin=Math.random()*Math.PI*2;
        private void blast(ServerLevel level){
            // first shell on the flare, the rest spread round a ring so the whole area is covered, not left to chance
            int ring=Math.max(1,SupportRules.FIRE_BLASTS-1);
            double angle=blasts==0?0:spin+(blasts-1)*Math.PI*2/ring,r=blasts==0?0:SupportRules.FIRE_RADIUS*.65;
            Vec3 point=new Vec3(getX()+Math.cos(angle)*r,getY(),getZ()+Math.sin(angle)*r);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,point.x,point.y+.5,point.z,1,0,0,0,0);
            level.sendParticles(ParticleTypes.FLAME,point.x,point.y+.3,point.z,25,1.2,.4,1.2,.08);
            level.playSound(null,point.x,point.y,point.z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,2f,.9f);
            var box=new net.minecraft.world.phys.AABB(point,point).inflate(SupportRules.BLAST_RADIUS);
            for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,box,e->e instanceof Enemy&&e.isAlive()&&e.distanceToSqr(point)<=SupportRules.BLAST_RADIUS*(double)SupportRules.BLAST_RADIUS)){
                e.hurt(level.damageSources().explosion(this,getOwner()),SupportRules.FIRE_DAMAGE);
            }
            SupportCalls.cannonFire(level,point,true);
        }
        @Override public void addAdditionalSaveData(CompoundTag n){super.addAdditionalSaveData(n);n.putInt("Kind",kind.ordinal());n.putBoolean("Landed",landed);n.putInt("LandedTicks",landedTicks);n.putInt("Blasts",blasts);n.put("Snapshot",snapshot);}
        @Override public void readAdditionalSaveData(CompoundTag n){super.readAdditionalSaveData(n);kind=SupportCalls.Kind.values()[Math.max(0,Math.min(2,n.getInt("Kind")))];landed=n.getBoolean("Landed");landedTicks=n.getInt("LandedTicks");blasts=n.getInt("Blasts");snapshot=n.getList("Snapshot",Tag.TAG_COMPOUND);setNoGravity(landed);}
        @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    }
}
