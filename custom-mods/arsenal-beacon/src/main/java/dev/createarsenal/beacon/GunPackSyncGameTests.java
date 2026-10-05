package dev.createarsenal.beacon;

import java.util.*;
import java.lang.reflect.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class GunPackSyncGameTests {
    @GameTest(template="empty3x3x3",batch="sync0132",timeoutTicks=100)
    public static void actualLoginCatalogueUsesBoundedPacketsAndRoundTripsEveryEntry(GameTestHelper h)throws Exception{
        Class<?> assets=Class.forName("com.tacz.guns.resource.CommonAssetsManager");Object manager=assets.getMethod("getInstance").invoke(null);Object cache=assets.getMethod("getNetworkCache").invoke(manager);
        Class<?> type=Class.forName("com.tacz.guns.network.message.ServerMessageSyncGunPack");Object message=type.getConstructor(Map.class).newInstance(cache);byte[] raw=GunPackSync.nativeEncode(message);
        h.assertTrue(raw.length>1048576,"Reproduce the actual oversized catalogue that rejected singleplayer login: "+raw.length);
        var p=UpgradeGameTests.player(h,"sync0132");var oldConnection=p.connection;var sent=new ArrayList<Packet<?>>();
        var capture=new Connection(PacketFlow.SERVERBOUND){@Override public void send(Packet<?> packet){sent.add(packet);}};
        p.connection=new ServerGamePacketListenerImpl(p.server,capture,p);
        try{
            // Run the original TaCZ event listener, including the transformed native send method.
            assets.getMethod("OnDatapackSync",net.minecraftforge.event.OnDatapackSyncEvent.class).invoke(null,new net.minecraftforge.event.OnDatapackSyncEvent(p.server.getPlayerList(),p));
            h.assertTrue(!sent.isEmpty(),"Original datapack login listener sent our bounded catalogue packets");
            var assembler=new ChunkedPayload.Assembler();byte[] complete=null;int packed=0;
            for(var packet:sent){
                h.assertTrue(packet instanceof ClientboundCustomPayloadPacket,"Native Minecraft payload packets are constructed without the 1MB exception");var payload=(ClientboundCustomPayloadPacket)packet;
                h.assertTrue(payload.getIdentifier().toString().equals(ArsenalBeacon.ID+":gunpack_sync")&&payload.getData().readableBytes()<1048576,"Only catalogue sync is redirected, below the unchanged vanilla limit");
                var buf=new FriendlyByteBuf(payload.getData().copy());try{h.assertTrue(buf.readUnsignedByte()==0,"Our channel discriminator");var part=GunPackSync.decode(buf);h.assertTrue(!buf.isReadable(),"No trailing chunk bytes");packed+=part.bytes().length;complete=assembler.accept(part);}finally{buf.release();}
            }
            h.assertTrue(Arrays.equals(raw,complete),"Every native data byte survives wire encoding, compression and reassembly");
            Object decoded=GunPackSync.nativeDecode(complete);h.assertTrue(cache.equals(type.getMethod("getCache").invoke(decoded)),"All guns, ammo, attachment data, filters and attachment tags survive the original native decoder");
            com.mojang.logging.LogUtils.getLogger().info("0.13.2 native login sync regression: {} raw bytes, {} compressed bytes, {} bounded packets; complete native cache matches",raw.length,packed,sent.size());
        }finally{p.connection=oldConnection;}
        h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="sync0132",timeoutTicks=100)
    public static void futureIncompressibleCatalogueStaysBelowVanillaLimit(GameTestHelper h){
        byte[] raw=new byte[2*1024*1024+31];new Random(53).nextBytes(raw);var assembler=new ChunkedPayload.Assembler();byte[] result=null;int packets=0;
        for(var part:ChunkedPayload.split(raw)){
            var packet=(ClientboundCustomPayloadPacket)GunPackSync.CHANNEL.toVanillaPacket(part,net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
            h.assertTrue(packet.getData().readableBytes()<1048576,"Actual native packet construction stays below the vanilla limit even when compression cannot help");
            var buf=new FriendlyByteBuf(packet.getData().copy());try{buf.readUnsignedByte();result=assembler.accept(GunPackSync.decode(buf));packets++;}finally{buf.release();}
        }
        h.assertTrue(packets>1&&Arrays.equals(raw,result),"Multiple wire packets reassemble atomically and losslessly");h.succeed();
    }
}
