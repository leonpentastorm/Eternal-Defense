package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

/** Server side of the Support Cannon menu: choosing the fire support type and buying upgrades with Ardent Energy. */
final class CannonControl {
    private CannonControl(){}
    static final int SELECT=0,UPGRADE=1;

    /** The cannon the player is allowed to operate at {@code pos}, or null (the reason is sent to the player). */
    private static SupportCannon.CannonEntity operable(ServerPlayer p,BlockPos pos){
        var level=p.serverLevel();
        if(!level.hasChunkAt(pos)||!level.getBlockState(pos).is(ArsenalBeacon.SUPPORT_CANNON.get()))return null;
        if(p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>144)return null;
        if(!(level.getBlockEntity(pos) instanceof SupportCannon.CannonEntity be))return null;
        if(BaseZone.disabled(level,pos,p))return null;
        if(be.owner==null||!be.owner.equals(p.getUUID())){BaseZone.say(p,be.owner==null?"no_owner":"not_yours",be.ownerName);return null;}
        return be;
    }
    static void open(ServerPlayer p,BlockPos pos,String message){
        if(operable(p,pos)==null)return;
        var base=SupportData.get(p.serverLevel()).ensure(p.getUUID());
        var data=new CompoundTag();
        data.putInt("energy",ArdentEnergy.balance(p));data.putBoolean("creative",p.isCreative());data.putInt("fire",base.fire);data.putIntArray("up",base.up);
        data.putBoolean("linked",SupportData.near(base.platform,pos));data.putInt("range",SupportRules.CANNON_RANGE);
        BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Open(pos,data,message));
    }
    static void act(ServerPlayer p,BlockPos pos,int kind,int arg){
        var be=operable(p,pos);if(be==null)return;
        var level=p.serverLevel();var data=SupportData.get(level);var base=data.ensure(p.getUUID());
        if(kind==SELECT){
            if(arg<0||arg>=CannonUpgrades.FireType.values().length)return;
            base.fire=arg;data.setDirty();open(p,pos,"selected");return;
        }
        if(kind==UPGRADE){
            if(arg<0||arg>=CannonUpgrades.Upgrade.values().length)return;
            var up=CannonUpgrades.Upgrade.values()[arg];int level_=base.up[arg],price=up.price(level_);
            if(price<0){open(p,pos,"maxed");return;}
            if(!ArdentEnergy.spend(p,price)){open(p,pos,"no_energy");return;}
            base.up[arg]=level_+1;data.setDirty();
            if(up==CannonUpgrades.Upgrade.TRAVERSE)be.setTraverse(base.up[arg]);
            level.playSound(null,pos,net.minecraft.sounds.SoundEvents.ANVIL_USE,net.minecraft.sounds.SoundSource.BLOCKS,.8f,1.3f);
            open(p,pos,"bought");
        }
    }

    record Open(BlockPos pos,CompoundTag data,String message){
        static void encode(Open p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeNbt(p.data);b.writeUtf(p.message,32);}
        static Open decode(FriendlyByteBuf b){var pos=b.readBlockPos();var n=b.readNbt();return new Open(pos,n==null?new CompoundTag():n,b.readUtf(32));}
        static void handle(Open p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->CannonScreen.receive(p)));ctx.get().setPacketHandled(true);}
    }
    record Act(BlockPos pos,int kind,int arg){
        static void encode(Act p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeVarInt(p.kind);b.writeVarInt(p.arg);}
        static Act decode(FriendlyByteBuf b){return new Act(b.readBlockPos(),b.readVarInt(),b.readVarInt());}
        static void handle(Act p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)act(c.getSender(),p.pos,p.kind,p.arg);});c.setPacketHandled(true);}
    }
}
