package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * The Command Table's satellite scan (0.0.20): two 256 x 256 images centred exactly on the beacon, Base (one block a pixel) and Region (the
 * satellite's range). A scan runs on the server a couple of rows a tick ({@link TacticalRules#ROWS_PER_TICK}); a column is read only from a
 * chunk that is loaded right now ({@code getChunkNow}), the rest keeps its last scan or shows the generator's biome (see {@link ScanSampler}).
 * One scan at a time for the whole server, then a cooldown. The finished images go to the players who have the table open, once, through
 * {@link ChunkedPayload} (a world of grass and water packs to a few kilobytes); while a scan runs they get a small progress message for the
 * sweep line. The images are saved in {@link HuntData}.
 */
final class TacticalScan {
    private TacticalScan(){}

    /** The scan in progress (one at a time). */
    private static final class Job {
        final int centerX,centerZ,regionScale;final int[] north=new int[TacticalRules.SCAN_SIZE],biomes=new int[TacticalRules.SCAN_SIZE/TacticalRules.BIOME_STEP];
        boolean region;int row;
        Job(int centerX,int centerZ,int regionScale){this.centerX=centerX;this.centerZ=centerZ;this.regionScale=regionScale;Arrays.fill(north,Integer.MIN_VALUE);}
    }
    private static Job job;
    /** The scan version each connection last received (a player who relogs gets it again). */
    private static final Map<Connection,Integer> SENT=new WeakHashMap<>();
    static boolean running(){return job!=null;}
    static void forget(){job=null;SENT.clear();}

    /** The SCAN MAP button: a message key (started, or why not). */
    static String start(ServerPlayer p,ServerLevel l,CampaignData d,HuntData h){
        var mk=Hunts.uplink(l,d,h);if(mk==null)return "tactical.result.no_uplink";
        if(job!=null)return "tactical.result.scan_busy";
        if(l.getGameTime()<h.scanReady)return "tactical.result.scan_cooldown";
        int range=TacticalRules.range(mk);
        // a beacon moved elsewhere, or a satellite of another range, starts the images over (old pixels would sit in the wrong place)
        if(h.scanCenter==null||!h.scanCenter.equals(d.beacon)){Arrays.fill(h.base.fresh,ScanSampler.BIOME);Arrays.fill(h.region.fresh,ScanSampler.BIOME);h.scanCenter=d.beacon.immutable();}
        if(h.scannedRange!=range){Arrays.fill(h.region.fresh,ScanSampler.BIOME);h.scannedRange=range;}
        job=new Job(d.beacon.getX(),d.beacon.getZ(),TacticalRules.regionScale(mk));h.setDirty();
        for(var viewer:viewers(l))Hunts.sound(viewer,ArsenalSounds.TACTICAL_SCAN.get());
        progress(l,h);
        return "tactical.result.scan_started";
    }

    static void tick(ServerLevel l,CampaignData d,HuntData h){
        if(job==null)return;
        if(!d.installed()){job=null;return;}
        var terrain=new WorldTerrain(l);
        for(int i=0;i<TacticalRules.ROWS_PER_TICK&&job!=null;i++){
            var image=job.region?h.region:h.base;int scale=job.region?job.regionScale:1;
            ScanSampler.row(terrain,job.centerX,job.centerZ,scale,job.row,image.colors,image.fresh,job.north,job.biomes);
            if(++job.row<TacticalRules.SCAN_SIZE)continue;
            if(!job.region){job.region=true;job.row=0;Arrays.fill(job.north,Integer.MIN_VALUE);continue;}
            finish(l,h);
        }
        if(job!=null&&l.getGameTime()%10==0)progress(l,h);
    }
    private static void finish(ServerLevel l,HuntData h){
        job=null;long now=l.getGameTime();
        h.scannedAt=now;h.scanReady=now+TacticalRules.SCAN_COOLDOWN_TICKS;h.scanVersion++;h.setDirty();
        for(var viewer:viewers(l)){sendImage(viewer,h);Hunts.sound(viewer,ArsenalSounds.TACTICAL_SCAN_DONE.get());}
        progress(l,h);Hunts.refresh(l);
        com.mojang.logging.LogUtils.getLogger().info("[hunts] satellite scan {} finished",h.scanVersion);
    }
    /** Players looking at the Command Table right now. */
    static java.util.List<ServerPlayer> viewers(ServerLevel l){
        return l.getServer().getPlayerList().getPlayers().stream().filter(p->p.containerMenu instanceof TacticalBlocks.TableMenu).toList();
    }
    private static void progress(ServerLevel l,HuntData h){
        var msg=new ScanProgress(h.scanVersion,job!=null,job!=null&&job.region,job==null?0:job.row);
        for(var viewer:viewers(l))BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),msg);
    }

    /** Sends the saved images to one player, unless that connection already has this version. */
    static void sendImage(ServerPlayer p,HuntData h){
        if(h.scannedAt<0)return;
        var connection=p.connection.connection;
        if(Integer.valueOf(h.scanVersion).equals(SENT.get(connection)))return;
        for(var part:ChunkedPayload.split(encode(h)))BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new ScanImage(part));
        SENT.put(connection,h.scanVersion);
    }
    /** The raw image transfer: version, Region scale, centre, scan time, then Base colours and freshness, Region colours and freshness. */
    static byte[] encode(HuntData h){
        var bytes=new ByteArrayOutputStream(16+4*TacticalRules.SCAN_SIZE*TacticalRules.SCAN_SIZE);
        try(var out=new DataOutputStream(bytes)){
            out.writeInt(h.scanVersion);out.writeInt(Math.max(1,2*h.scannedRange/TacticalRules.SCAN_SIZE));
            out.writeInt(h.scanCenter==null?0:h.scanCenter.getX());out.writeInt(h.scanCenter==null?0:h.scanCenter.getZ());out.writeLong(h.scannedAt);
            out.write(h.base.colors);out.write(h.base.fresh);out.write(h.region.colors);out.write(h.region.fresh);
        }catch(IOException e){throw new IllegalStateException(e);}
        return bytes.toByteArray();
    }
    static final int HEADER=24;

    // ---- the world under the images ---------------------------------------------------------------------------------------------------------
    /** Reads a column only from a loaded chunk (vanilla's map colours: the first block with a map colour, water depth over the floor). */
    record WorldTerrain(ServerLevel l) implements ScanSampler.Terrain {
        @Override public long column(int x,int z){
            var chunk=l.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)return -1;
            int min=l.getMinBuildHeight();
            var pos=new BlockPos.MutableBlockPos(x,chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15),z);
            var state=chunk.getBlockState(pos);
            while(state.getMapColor(chunk,pos)==MapColor.NONE&&pos.getY()>min){pos.move(Direction.DOWN);state=chunk.getBlockState(pos);}
            int top=pos.getY(),depth=0;
            if(!state.getFluidState().isEmpty()){
                var below=new BlockPos.MutableBlockPos(x,top,z);
                while(depth<255&&below.getY()>min){below.move(Direction.DOWN);depth++;if(chunk.getBlockState(below).getFluidState().isEmpty())break;}
            }
            return ScanSampler.column(top,depth,state.getMapColor(chunk,pos).id);
        }
        /** The generator's biome where nothing is loaded (vanilla's biome lookup without loading a chunk), as a plain map colour. */
        @Override public int biome(int x,int z){return biomeColor(l.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(l.getSeaLevel()),QuartPos.fromBlock(z)));}
    }
    static int biomeColor(Holder<Biome> b){
        if(b.is(BiomeTags.IS_DEEP_OCEAN))return ScanSampler.pack(MapColor.WATER.id,ScanSampler.LOW);
        if(b.is(BiomeTags.IS_OCEAN)||b.is(BiomeTags.IS_RIVER))return ScanSampler.pack(MapColor.WATER.id,ScanSampler.NORMAL);
        if(b.is(BiomeTags.IS_BEACH)||b.is(net.minecraftforge.common.Tags.Biomes.IS_DESERT))return ScanSampler.pack(MapColor.SAND.id,ScanSampler.NORMAL);
        if(b.is(BiomeTags.IS_BADLANDS))return ScanSampler.pack(MapColor.COLOR_ORANGE.id,ScanSampler.NORMAL);
        if(b.is(net.minecraftforge.common.Tags.Biomes.IS_SNOWY))return ScanSampler.pack(MapColor.SNOW.id,ScanSampler.NORMAL);
        if(b.is(net.minecraftforge.common.Tags.Biomes.IS_PEAK)||b.is(BiomeTags.IS_MOUNTAIN))return ScanSampler.pack(MapColor.STONE.id,ScanSampler.NORMAL);
        if(b.is(net.minecraftforge.common.Tags.Biomes.IS_SWAMP))return ScanSampler.pack(MapColor.COLOR_GREEN.id,ScanSampler.LOW);
        if(b.is(net.minecraftforge.common.Tags.Biomes.IS_MUSHROOM))return ScanSampler.pack(MapColor.COLOR_PURPLE.id,ScanSampler.NORMAL);
        if(b.is(BiomeTags.IS_JUNGLE))return ScanSampler.pack(MapColor.PLANT.id,ScanSampler.LOW);
        if(b.is(BiomeTags.IS_FOREST)||b.is(BiomeTags.IS_TAIGA))return ScanSampler.pack(MapColor.PLANT.id,ScanSampler.NORMAL);
        if(b.is(BiomeTags.IS_SAVANNA))return ScanSampler.pack(MapColor.GRASS.id,ScanSampler.HIGH);
        return ScanSampler.pack(MapColor.GRASS.id,ScanSampler.NORMAL);
    }

    // ---- packets ----------------------------------------------------------------------------------------------------------------------------
    /** S2C (id 18): one part of the saved images, sent when a player opens the table with an older version, and to viewers when a scan ends. */
    record ScanImage(ChunkedPayload.Part part){
        static void encode(ScanImage m,FriendlyByteBuf b){var p=m.part;ChunkedPayload.validate(p);b.writeUUID(p.transfer());b.writeVarInt(p.rawBytes());b.writeVarInt(p.packedBytes());b.writeVarInt(p.index());b.writeByteArray(p.bytes());}
        static ScanImage decode(FriendlyByteBuf b){var p=new ChunkedPayload.Part(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(ChunkedPayload.CHUNK_BYTES));ChunkedPayload.validate(p);return new ScanImage(p);}
        static void handle(ScanImage m,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->TacticalClient.image(m.part)));ctx.get().setPacketHandled(true);}
    }
    /** S2C (id 19): the scan's progress for the sweep line on the table (a few bytes, twice a second to viewers only). */
    record ScanProgress(int version,boolean running,boolean region,int row){
        static void encode(ScanProgress m,FriendlyByteBuf b){b.writeVarInt(m.version);b.writeBoolean(m.running);b.writeBoolean(m.region);b.writeVarInt(m.row);}
        static ScanProgress decode(FriendlyByteBuf b){return new ScanProgress(b.readVarInt(),b.readBoolean(),b.readBoolean(),Math.max(0,Math.min(TacticalRules.SCAN_SIZE,b.readVarInt())));}
        static void handle(ScanProgress m,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->TacticalClient.progress(m)));ctx.get().setPacketHandled(true);}
    }
}
