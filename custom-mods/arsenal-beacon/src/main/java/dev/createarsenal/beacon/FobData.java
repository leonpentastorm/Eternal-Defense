package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/**
 * Where the truth of the Forward Operating Bases lives (0.0.23): one record per FOB Beacon placed, shared by every player of the server. The
 * Home beacon's {@link CampaignData} is never touched. The FOB zone (the vanilla village) is worked out when needed and never saved.
 * Slice 2 (0.0.24) fills the stockpile and the route, slice 3 (0.0.25) the dead-villager records and the server's SOS; loading keeps whatever is there.
 */
final class FobData extends net.minecraft.world.level.saveddata.SavedData {
    static final String NAME="arsenal_beacon_fobs_v1";

    static final class Fob {
        final long id;String name;BlockPos pos;
        /** The section the village was found in when the FOB was planted (its identity; the shape itself is never saved). */
        long villageSection;
        FobRules.State state=FobRules.State.PLANTED;
        /** True once a raid at the FOB was won (a FOB that went DOWN before its first win has to win one again). */
        boolean activated;
        long placedAt;
        /** The vanilla raid this FOB started and follows (0: none), and the villagers living there when it began. */
        int raidId,raidVillagers;
        /** Slice 3: the villagers lost in raids. Slice 2: the stockpile and the rail route. Kept as they are until then. */
        ListTag deadVillagers=new ListTag();CompoundTag stockpile=new CompoundTag(),route=new CompoundTag();
        Fob(long id){this.id=id;}
        boolean raidRunning(){return raidId!=0;}

        // ---- slice 2 (0.0.24): the supply rail; saved inside the route and stockpile tags above ----------------------------------------
        /** The gate pair of this FOB (null: not placed). */
        BlockPos deliveryGate,receivingGate;
        /** The route was opened by a ride; its one-way time T in ticks; the inspection clock (ticks of play left). */
        boolean commissioned;long baseline,clock;
        /** Ticks of play (FobData.onlineTicks) at which the next passive delivery comes (0: not scheduled), and of the last chat line. */
        long nextPassive,lastNoteAt;
        /** Goods delivered by passive trips since the last chat line (one count per good). */
        int[] note=new int[RouteRules.GOODS.size()];
        /** The last delivery, for the screens: what it was ({@code passive}/{@code ride}), how many items, when (ticks of play). */
        String lastKind="";int lastItems;long lastAt;
        /** The stockpile (whole and part items per good), when it was last brought up to date (ticks of play), the villagers counted then. */
        double[] stock=new double[RouteRules.GOODS.size()];long accruedAt;int workers;
        boolean hasRoute(){return deliveryGate!=null&&receivingGate!=null;}
        double separation(){return deliveryGate==null||receivingGate==null?0:Math.sqrt(Math.pow(deliveryGate.getX()-receivingGate.getX(),2)+Math.pow(deliveryGate.getZ()-receivingGate.getZ(),2));}
    }

    long nextId=1;
    final Map<Long,Fob> fobs=new LinkedHashMap<>();
    /** Ticks of play: grows by 20 a second while at least one player is online (the stockpile, the clocks and passive deliveries use it). */
    long onlineTicks;
    /**
     * Slice 3 (0.0.25), one SOS for the server ({@link FobSos}): ticks of play until the next one (0: not set yet), the FOB that calls now (0:
     * none) and the ticks of play left to answer it, a serial that marks its maps, and its map (made once per SOS).
     */
    long sosClock,sosFob,sosLeft,sosSerial;CompoundTag sosMap;

    static FobData get(ServerLevel any){return any.getServer().overworld().getDataStorage().computeIfAbsent(FobData::load,FobData::new,NAME);}

    Fob byId(long id){return fobs.get(id);}
    Fob at(BlockPos anchor){for(var f:fobs.values())if(f.pos.equals(anchor))return f;return null;}
    List<String> names(long except){var out=new ArrayList<String>();for(var f:fobs.values())if(f.id!=except)out.add(f.name);return out;}
    int count(FobRules.State state){int n=0;for(var f:fobs.values())if(f.state==state)n++;return n;}
    Fob add(BlockPos pos,long villageSection,long now){
        var f=new Fob(nextId++);f.pos=pos.immutable();f.villageSection=villageSection;f.placedAt=now;f.name=FobRules.freeCodename(f.id,names(f.id));
        fobs.put(f.id,f);setDirty();return f;
    }

    // ---- save / load: one bad entry is skipped, never the whole list -----------------------------------------------------------------------
    static FobData load(CompoundTag n){
        var d=new FobData();d.nextId=Math.max(1,n.getLong("nextId"));d.onlineTicks=Math.max(0,n.getLong("onlineTicks"));
        d.sosClock=Math.max(0,n.getLong("sosClock"));d.sosFob=n.getLong("sosFob");d.sosLeft=Math.max(0,n.getLong("sosLeft"));d.sosSerial=n.getLong("sosSerial");
        if(n.contains("sosMap",Tag.TAG_COMPOUND))d.sosMap=n.getCompound("sosMap");
        for(var t:n.getList("fobs",Tag.TAG_COMPOUND)){
            try{
                var c=(CompoundTag)t;if(!c.contains("id")||!c.contains("pos"))continue;
                var f=new Fob(c.getLong("id"));if(f.id<=0||d.fobs.containsKey(f.id))continue;
                f.pos=BlockPos.of(c.getLong("pos"));f.villageSection=c.getLong("village");
                f.state=FobRules.State.of(c.getInt("state"));f.activated=c.getBoolean("activated");f.placedAt=c.getLong("placed");
                f.raidId=c.getInt("raid");f.raidVillagers=c.getInt("raidVillagers");
                String name=FobRules.clean(c.getString("name"));
                f.name=name.isEmpty()||FobRules.contains(d.names(f.id),name)?FobRules.freeCodename(f.id,d.names(f.id)):name;
                if(c.contains("dead",Tag.TAG_LIST))f.deadVillagers=c.getList("dead",Tag.TAG_COMPOUND);
                if(c.contains("stockpile",Tag.TAG_COMPOUND))f.stockpile=c.getCompound("stockpile");
                if(c.contains("route",Tag.TAG_COMPOUND))f.route=c.getCompound("route");
                readRoute(f);
                d.fobs.put(f.id,f);d.nextId=Math.max(d.nextId,f.id+1);
            }catch(RuntimeException ex){com.mojang.logging.LogUtils.getLogger().warn("[fob] skipped a broken FOB record: {}",ex.toString());}
        }
        if(d.sosFob!=0&&(!d.fobs.containsKey(d.sosFob)||d.sosLeft<=0)){d.sosFob=0;d.sosLeft=0;d.sosMap=null;}   // an SOS of a FOB that is gone is dropped
        return d;
    }
    /** Slice 2 fields from the route and stockpile tags (missing keys read as nothing; goods are keyed by item id, so the list may grow). */
    static void readRoute(Fob f){
        var r=f.route;
        if(r.contains("delivery"))f.deliveryGate=BlockPos.of(r.getLong("delivery"));
        if(r.contains("receiving"))f.receivingGate=BlockPos.of(r.getLong("receiving"));
        f.commissioned=r.getBoolean("commissioned");f.baseline=Math.max(0,r.getLong("baseline"));f.clock=Math.max(0,Math.min(RouteRules.CLOCK,r.getLong("clock")));
        f.nextPassive=Math.max(0,r.getLong("nextPassive"));f.lastNoteAt=r.getLong("lastNoteAt");
        f.lastKind=r.getString("lastKind");f.lastItems=r.getInt("lastItems");f.lastAt=r.getLong("lastAt");
        var s=f.stockpile;f.accruedAt=s.getLong("accruedAt");f.workers=s.getInt("workers");
        CompoundTag goods=s.getCompound("goods"),note=r.getCompound("note");
        for(int i=0;i<RouteRules.GOODS.size();i++){String id=RouteRules.GOODS.get(i).id();f.stock[i]=Math.max(0,goods.getDouble(id));f.note[i]=Math.max(0,note.getInt(id));}
    }
    static void writeRoute(Fob f){
        var r=f.route;
        if(f.deliveryGate!=null)r.putLong("delivery",f.deliveryGate.asLong());else r.remove("delivery");
        if(f.receivingGate!=null)r.putLong("receiving",f.receivingGate.asLong());else r.remove("receiving");
        r.putBoolean("commissioned",f.commissioned);r.putLong("baseline",f.baseline);r.putLong("clock",f.clock);
        r.putLong("nextPassive",f.nextPassive);r.putLong("lastNoteAt",f.lastNoteAt);
        r.putString("lastKind",f.lastKind);r.putInt("lastItems",f.lastItems);r.putLong("lastAt",f.lastAt);
        var s=f.stockpile;s.putLong("accruedAt",f.accruedAt);s.putInt("workers",f.workers);
        CompoundTag goods=new CompoundTag(),note=new CompoundTag();
        for(int i=0;i<RouteRules.GOODS.size();i++){String id=RouteRules.GOODS.get(i).id();goods.putDouble(id,f.stock[i]);if(f.note[i]>0)note.putInt(id,f.note[i]);}
        s.put("goods",goods);r.put("note",note);
    }
    /** The FOB whose Delivery or Receiving Gate stands at {@code anchor}, or null. */
    Fob byGate(BlockPos anchor){for(var f:fobs.values())if(anchor.equals(f.deliveryGate)||anchor.equals(f.receivingGate))return f;return null;}
    @Override public CompoundTag save(CompoundTag n){
        n.putLong("nextId",nextId);n.putLong("onlineTicks",onlineTicks);
        n.putLong("sosClock",sosClock);n.putLong("sosFob",sosFob);n.putLong("sosLeft",sosLeft);n.putLong("sosSerial",sosSerial);if(sosMap!=null)n.put("sosMap",sosMap);
        var list=new ListTag();
        for(var f:fobs.values()){
            var c=new CompoundTag();c.putLong("id",f.id);c.putString("name",f.name);c.putLong("pos",f.pos.asLong());c.putLong("village",f.villageSection);
            c.putInt("state",f.state.ordinal());c.putBoolean("activated",f.activated);c.putLong("placed",f.placedAt);
            c.putInt("raid",f.raidId);c.putInt("raidVillagers",f.raidVillagers);
            writeRoute(f);c.put("dead",f.deadVillagers);c.put("stockpile",f.stockpile);c.put("route",f.route);
            list.add(c);
        }
        n.put("fobs",list);return n;
    }
}
