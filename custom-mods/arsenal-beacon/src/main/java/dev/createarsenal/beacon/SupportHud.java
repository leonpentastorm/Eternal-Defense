package dev.createarsenal.beacon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

/**
 * Which fire support the player has chosen. The server owns the choice and tells the client on login and whenever it changes;
 * {@link SupportHudClient} draws it (cannon menu, flare tooltip, and a banner above the hotbar while a Fire Support Flare is held).
 */
final class SupportHud {
    private SupportHud(){}
    /** Accent colour of each type (ARGB), in {@link CannonUpgrades.FireType} order. */
    static final int[] ACCENT={0xffff7a3d,0xffe8c17b,0xffffe14d,0xffed5560,0xff5fe07c,0xffb98cff,
        0xffffb347,0xff8fe6ff,0xffff5a1f,0xff9d6bff,0xfff4f19a,0xfffff4d0};
    /** The player's choice as the server last announced it (the default is the Explosion Barrage). */
    static int chosen;

    static ResourceLocation icon(CannonUpgrades.FireType type,int size){
        return new ResourceLocation(ArsenalBeacon.ID,"textures/gui/fire_support/"+type.id+(size>=64?"":"_"+size)+".png");
    }
    static int accent(CannonUpgrades.FireType type){return ACCENT[type.ordinal()];}
    static CannonUpgrades.FireType current(){return CannonUpgrades.FireType.of(chosen);}

    record Sync(int type){
        static void encode(Sync p,FriendlyByteBuf b){b.writeVarInt(p.type);}
        static Sync decode(FriendlyByteBuf b){return new Sync(b.readVarInt());}
        static void handle(Sync p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->chosen=p.type));ctx.get().setPacketHandled(true);}
    }
    /** Server: send the player's current choice (on login and whenever they change it). */
    static void send(ServerPlayer p){
        if(p instanceof net.minecraftforge.common.util.FakePlayer)return;
        var base=SupportData.get(p.serverLevel()).of(p.getUUID());
        BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Sync(base==null?0:base.fire));
    }
    public static final class Login {
        @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)send(p);}
    }
}
