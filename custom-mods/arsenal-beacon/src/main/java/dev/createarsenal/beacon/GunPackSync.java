package dev.createarsenal.beacon;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

/** Carries TaCZ's unchanged native message over bounded packets instead of its monolithic packet. */
public final class GunPackSync {
    private static final String MESSAGE="com.tacz.guns.network.message.ServerMessageSyncGunPack";
    static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(ArsenalBeacon.ID,"gunpack_sync"),()->"1","1"::equals,"1"::equals);
    private static final Map<Connection,ChunkedPayload.Assembler> RECEIVING=new WeakHashMap<>();
    public static void init(){CHANNEL.registerMessage(0,ChunkedPayload.Part.class,GunPackSync::encode,GunPackSync::decode,GunPackSync::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
    static void clearReceiving(){synchronized(RECEIVING){RECEIVING.clear();}}
    public static boolean intercept(Object message,Player player){
        if(message==null||!message.getClass().getName().equals(MESSAGE))return false;
        if(!(player instanceof ServerPlayer target))return false;
        for(var part:ChunkedPayload.split(nativeEncode(message)))CHANNEL.send(PacketDistributor.PLAYER.with(()->target),part);
        return true;
    }
    static byte[] nativeEncode(Object message){
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try{message.getClass().getMethod("encode",message.getClass(),FriendlyByteBuf.class).invoke(null,message,buf);byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);return bytes;}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot encode native TaCZ catalogue",e);}
        finally{buf.release();}
    }
    static Object nativeDecode(byte[] bytes){
        var buf=new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try{Object message=Class.forName(MESSAGE).getMethod("decode",FriendlyByteBuf.class).invoke(null,buf);if(buf.isReadable())throw new IllegalArgumentException("Trailing catalogue data");return message;}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot decode native TaCZ catalogue",e);}
        finally{buf.release();}
    }
    static void encode(ChunkedPayload.Part p,FriendlyByteBuf b){ChunkedPayload.validate(p);b.writeUUID(p.transfer());b.writeVarInt(p.rawBytes());b.writeVarInt(p.packedBytes());b.writeVarInt(p.index());b.writeByteArray(p.bytes());}
    static ChunkedPayload.Part decode(FriendlyByteBuf b){var p=new ChunkedPayload.Part(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(ChunkedPayload.CHUNK_BYTES));ChunkedPayload.validate(p);return p;}
    private static void handle(ChunkedPayload.Part p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.setPacketHandled(true);
        context.enqueueWork(()->{
            var connection=context.getNetworkManager();byte[] complete;
            synchronized(RECEIVING){
                var assembler=RECEIVING.computeIfAbsent(connection,key->new ChunkedPayload.Assembler());
                try{complete=assembler.accept(p);}catch(RuntimeException ex){RECEIVING.remove(connection);throw ex;}
                if(complete!=null)RECEIVING.remove(connection);
            }
            if(complete==null)return;
            Object message=nativeDecode(complete);
            try{
                // Keep TaCZ's integrated/remote connection handling and client index reload exactly once.
                message.getClass().getMethod("handle",message.getClass(),Supplier.class).invoke(null,message,supplier);
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot apply native TaCZ catalogue",e);}
        });
    }
}
