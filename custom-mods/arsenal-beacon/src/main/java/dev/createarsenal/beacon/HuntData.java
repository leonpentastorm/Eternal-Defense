package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.*;

/**
 * Where the truth of Tactical Operations lives (0.0.20): the board, the shared mission, the Command Table and Satellite Beacon of the base,
 * and the table's scan images. Server only; item NBT is never trusted (a map or flare only points here). The Home raid state stays in
 * {@link CampaignData}, which this only reads.
 */
final class HuntData extends net.minecraft.world.level.saveddata.SavedData {
    static final String NAME="arsenal_beacon_hunts_v1";
    final HuntBoard board=new HuntBoard();
    /** The base's one table (its anchor) and one satellite, or null. */
    BlockPos table,satellite;
    /** The satellite's Mk (1 to 3), kept here and not on the item: a satellite moved with the shovel, or bought again, comes back at it. */
    int satelliteMk=1;
    /** The mission's map, made once at accept time and copied for every player (vanilla filled map with a red X), and its map id. */
    CompoundTag missionMap;
    /** The two scan images (Base and Region), see {@link ScanSampler}. */
    final Image base=new Image(),region=new Image();
    /** Game time the last scan finished (-1: never), the time it may start again, and a version that changes with every finished scan. */
    long scannedAt=-1,scanReady;int scanVersion;
    /** Satellite Mk and range the Region image was made with (its scale). */
    int scannedRange=TacticalRules.RANGE[0];
    /** The beacon the images are centred on: a beacon moved elsewhere starts them over. */
    BlockPos scanCenter;

    /** One 256 x 256 image: packed vanilla map colour and freshness ({@link ScanSampler#LIVE}, STALE, BIOME) per pixel. */
    static final class Image {
        final byte[] colors=new byte[TacticalRules.SCAN_SIZE*TacticalRules.SCAN_SIZE],fresh=new byte[TacticalRules.SCAN_SIZE*TacticalRules.SCAN_SIZE];
    }

    static HuntData get(ServerLevel any){return any.getServer().overworld().getDataStorage().computeIfAbsent(HuntData::load,HuntData::new,NAME);}

    // ---- save / load ----------------------------------------------------------------------------------------------------------------------
    static HuntData load(CompoundTag n){
        var d=new HuntData();var b=d.board;
        if(n.contains("table"))d.table=BlockPos.of(n.getLong("table"));
        if(n.contains("satellite"))d.satellite=BlockPos.of(n.getLong("satellite"));
        d.satelliteMk=TacticalRules.mk(n.getInt("satelliteMk"));
        b.nextId=Math.max(1,n.getLong("nextId"));b.lastDay=n.contains("lastDay")?n.getLong("lastDay"):Long.MIN_VALUE;b.filled=n.getBoolean("filled");b.capacity=n.getInt("capacity");
        for(var t:n.getList("offers",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;b.offers.add(offer(c));}
        for(var t:n.getList("flared",Tag.TAG_COMPOUND)){
            var c=(CompoundTag)t;long id=c.getLong("offer");if(b.offer(id)==null)continue;
            for(var u:c.getList("players",Tag.TAG_INT_ARRAY))b.flared(id).add(NbtUtils.loadUUID(u));
        }
        if(n.contains("mission")){
            var c=n.getCompound("mission");var offer=b.offer(c.getLong("offer"));
            if(offer!=null){
                var m=new HuntBoard.Mission(offer,b.flared(offer.id));m.state=HuntBoard.State.values()[Math.max(0,Math.min(3,c.getInt("state")))];
                m.serial=c.getLong("serial");m.total=c.getInt("total");m.remaining=c.getInt("remaining");m.spawned=c.getInt("spawned");m.awayTicks=c.getInt("away");
                // an ENGAGED mission stays engaged across a restart: its mobs are saved with their chunks (persistent), and a warband cut short
                // mid-spawn finishes spawning ("spawned" is saved) when a player comes near again
                if(m.state!=HuntBoard.State.CLEARED)b.mission=m;
            }
        }
        if(n.contains("missionMap"))d.missionMap=n.getCompound("missionMap");
        if(n.contains("scanCenter"))d.scanCenter=BlockPos.of(n.getLong("scanCenter"));
        d.scannedAt=n.contains("scannedAt")?n.getLong("scannedAt"):-1;d.scanVersion=n.getInt("scanVersion");d.scannedRange=n.contains("scannedRange")?n.getInt("scannedRange"):TacticalRules.RANGE[0];
        inflate(n.getByteArray("baseColors"),d.base.colors);inflate(n.getByteArray("baseFresh"),d.base.fresh);
        inflate(n.getByteArray("regionColors"),d.region.colors);inflate(n.getByteArray("regionFresh"),d.region.fresh);
        return d;
    }
    @Override public CompoundTag save(CompoundTag n){
        var b=board;
        if(table!=null)n.putLong("table",table.asLong());if(satellite!=null)n.putLong("satellite",satellite.asLong());n.putInt("satelliteMk",satelliteMk);
        n.putLong("nextId",b.nextId);n.putLong("lastDay",b.lastDay);n.putBoolean("filled",b.filled);n.putInt("capacity",b.capacity);
        var list=new ListTag();for(var o:b.offers)list.add(offer(o));n.put("offers",list);
        var flared=new ListTag();
        for(var e:b.flared.entrySet()){
            if(b.offer(e.getKey())==null||e.getValue().isEmpty())continue;
            var c=new CompoundTag();c.putLong("offer",e.getKey());var players=new ListTag();for(var u:e.getValue())players.add(NbtUtils.createUUID(u));c.put("players",players);flared.add(c);
        }
        n.put("flared",flared);
        if(b.mission!=null&&b.mission.state!=HuntBoard.State.CLEARED){
            var m=b.mission;var c=new CompoundTag();c.putLong("offer",m.offer.id);c.putInt("state",m.state.ordinal());c.putLong("serial",m.serial);
            c.putInt("total",m.total);c.putInt("remaining",m.remaining);c.putInt("spawned",m.spawned);c.putInt("away",m.awayTicks);n.put("mission",c);
        }
        if(missionMap!=null)n.put("missionMap",missionMap);
        if(scanCenter!=null)n.putLong("scanCenter",scanCenter.asLong());
        n.putLong("scannedAt",scannedAt);n.putInt("scanVersion",scanVersion);n.putInt("scannedRange",scannedRange);
        n.putByteArray("baseColors",deflate(base.colors));n.putByteArray("baseFresh",deflate(base.fresh));
        n.putByteArray("regionColors",deflate(region.colors));n.putByteArray("regionFresh",deflate(region.fresh));
        return n;
    }
    static CompoundTag offer(HuntBoard.Offer o){
        var c=new CompoundTag();c.putLong("id",o.id);c.putString("kind",o.kind.id);c.putString("codename",o.codename);c.putInt("x",o.x);c.putInt("z",o.z);c.putInt("distance",o.distance);c.putDouble("bearing",o.bearing);return c;
    }
    static HuntBoard.Offer offer(CompoundTag c){return new HuntBoard.Offer(c.getLong("id"),TacticalRules.Kind.of(c.getString("kind")),c.getString("codename"),c.getInt("x"),c.getInt("z"),c.getInt("distance"),c.getDouble("bearing"));}
    /** The images are saved deflated (a world map of grass and water packs very small). */
    static byte[] deflate(byte[] raw){
        var out=new ByteArrayOutputStream();try(var z=new DeflaterOutputStream(out,new Deflater(Deflater.BEST_COMPRESSION))){z.write(raw);}catch(java.io.IOException e){return new byte[0];}return out.toByteArray();
    }
    static void inflate(byte[] packed,byte[] into){
        if(packed.length==0)return;
        try(var z=new InflaterInputStream(new java.io.ByteArrayInputStream(packed))){byte[] raw=z.readNBytes(into.length);System.arraycopy(raw,0,into,0,Math.min(raw.length,into.length));}catch(java.io.IOException e){Arrays.fill(into,(byte)0);}
    }
}
