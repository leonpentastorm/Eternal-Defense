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
    static final int DEPOT_SLOTS=54;

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
        /**
         * 0.0.33: the FOB's Supply Catapult (null: none) and the specialty seller's shipments waiting for it or in the air, each a compound:
         * {@code items} (the stacks), {@code buyer}/{@code buyerName}, {@code stage} ("wait": not thrown yet, "flying": thrown) and {@code due}
         * (overworld game time: the throw when not loaded, the landing when flying).
         */
        BlockPos catapult;final List<CompoundTag> shipments=new ArrayList<>();
        /** The gate pair of this FOB (null: not placed). */
        BlockPos deliveryGate,receivingGate;
        /** The route was opened by a ride; its one-way time T in ticks; the inspection clock (ticks of play left). */
        boolean commissioned;long baseline,clock;
        /** Ticks of play (FobData.onlineTicks) at which the next passive delivery comes (0: not scheduled), and of the last chat line. */
        long nextPassive,lastNoteAt;
        /** Items delivered by passive trips since the last chat line (item id to count). */
        final Map<String,Integer> note=new LinkedHashMap<>();
        /** The last delivery, for the screens: what it was ({@code passive}/{@code ride}), how many items, when (ticks of play). */
        String lastKind="";int lastItems;long lastAt;
        /** 0.0.31: when the last delivery came, passive or ridden, in ticks of play (a ride brings at most 2.5 times the time since; 0: never). */
        long lastTrip;
        /** The villagers counted the last time the FOB looked (they set what a trip is worth). */
        int workers;
        /**
         * 0.0.31 (owner): the four loot slots. Which are on, the item chosen in the mine, the farm and the specialty slot, the specialty rolled
         * at planting, and each slot's tally (tribute value carried over between trips; whole items leave it).
         */
        final boolean[] slotOn={true,true,true,true};
        int mine,farm,special;RouteRules.Specialty specialty=RouteRules.Specialty.FISHING;
        final double[] tally=new double[RouteRules.Slot.values().length];
        /**
         * The Receiving Gate's tribute storage, a double chest's worth; players only take from it. Made on first use (an item stack needs the
         * game's registries), until then its saved list is kept as it was read.
         */
        private net.minecraft.world.SimpleContainer depot;private net.minecraft.nbt.ListTag depotSaved;Runnable dirty=()->{};
        net.minecraft.world.SimpleContainer depot(){
            if(depot==null){depot=new net.minecraft.world.SimpleContainer(DEPOT_SLOTS);if(depotSaved!=null)depot.fromTag(depotSaved);depotSaved=null;depot.addListener(c->dirty.run());}
            return depot;
        }
        net.minecraft.nbt.ListTag depotTag(){return depot!=null?depot.createTag():depotSaved!=null?depotSaved:new net.minecraft.nbt.ListTag();}
        /** The last zone the FOB had, as 16-block columns (x, z packed as in ChunkPos), for the Command Table's map. */
        long[] zoneColumns=new long[0];
        /** 0.0.32 (owner): the village's research, in points (emeralds, diamonds and Ardent Energy given at its FOB Beacon); its level is worked out. */
        int researchXp;
        int level(){return RouteRules.level(researchXp);}
        int choice(RouteRules.Slot slot){return switch(slot){case MINE->mine;case FARM->farm;case SPECIALTY->special;default->0;};}
        /** The item the slot sends now: its choice, or while research has not opened that one yet, the slot's first open item. */
        RouteRules.Loot loot(RouteRules.Slot slot){return RouteRules.loot(slot,specialty,RouteRules.effectiveChoice(slot,specialty,choice(slot),level()));}
        /** Everything a trip of this FOB depends on. */
        RouteRules.Yield yield(){return new RouteRules.Yield(slotOn,separation(),workers,baseline>0?RouteRules.period(baseline):0,level());}
        RouteRules.Loot[] loots(){var out=new RouteRules.Loot[RouteRules.Slot.values().length];for(var slot:RouteRules.Slot.values())out[slot.ordinal()]=loot(slot);return out;}
        /** 0.0.30: the zone outline is shown to players near the FOB (its screen turns it off for everyone). */
        boolean showZone=true;
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
        f.specialty=RouteRules.Specialty.of(new Random().nextInt(RouteRules.Specialty.values().length));   // 0.0.31: rolled once, at planting
        f.dirty=this::setDirty;
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
                f.raidId=c.getInt("raid");f.raidVillagers=c.getInt("raidVillagers");f.showZone=!c.contains("showZone")||c.getBoolean("showZone");
                String name=FobRules.clean(c.getString("name"));
                f.name=name.isEmpty()||FobRules.contains(d.names(f.id),name)?FobRules.freeCodename(f.id,d.names(f.id)):name;
                if(c.contains("dead",Tag.TAG_LIST))f.deadVillagers=c.getList("dead",Tag.TAG_COMPOUND);
                if(c.contains("stockpile",Tag.TAG_COMPOUND))f.stockpile=c.getCompound("stockpile");
                if(c.contains("route",Tag.TAG_COMPOUND))f.route=c.getCompound("route");
                f.dirty=d::setDirty;readRoute(f);
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
        f.lastKind=r.getString("lastKind");f.lastItems=r.getInt("lastItems");f.lastAt=r.getLong("lastAt");f.lastTrip=r.getLong("lastTrip");
        f.workers=f.stockpile.getInt("workers");
        f.catapult=r.contains("catapult")?BlockPos.of(r.getLong("catapult")):null;f.shipments.clear();
        for(var t:r.getList("shipments",Tag.TAG_COMPOUND))f.shipments.add((CompoundTag)t);
        var note=r.getCompound("noted");for(String id:note.getAllKeys())if(note.getInt(id)>0)f.note.put(id,note.getInt(id));
        // 0.0.31: the loot slots (a FOB from before gets its specialty from its id, every slot on, the first choice of each)
        var t=r.getCompound("tribute");
        f.specialty=t.contains("specialty")?RouteRules.Specialty.of(t.getInt("specialty")):RouteRules.Specialty.of((int)f.id);
        if(t.contains("on")){byte[] on=t.getByteArray("on");for(int i=0;i<f.slotOn.length&&i<on.length;i++)f.slotOn[i]=on[i]!=0;}
        if(RouteRules.enabled(f.slotOn)==0)f.slotOn[0]=true;
        f.mine=t.getInt("mine");f.farm=t.getInt("farm");f.special=t.getInt("special");
        long[] tally=t.getLongArray("tally");for(int i=0;i<f.tally.length&&i<tally.length;i++)f.tally[i]=Math.max(0,Double.longBitsToDouble(tally[i]));
        f.zoneColumns=t.getLongArray("zone");f.researchXp=Math.max(0,t.getInt("research"));
        if(t.contains("depot",Tag.TAG_LIST))f.depotSaved=t.getList("depot",Tag.TAG_COMPOUND);
        // 0.0.30 and before kept a stockpile of village goods: its whole items move into the storage once (nothing is lost)
        var goods=f.stockpile.getCompound("goods");
        for(String id:List.copyOf(goods.getAllKeys())){
            int whole=(int)Math.floor(goods.getDouble(id));if(whole<=0)continue;
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(id));
            if(item!=net.minecraft.world.item.Items.AIR)f.depot().addItem(new net.minecraft.world.item.ItemStack(item,whole));
        }
        f.stockpile.remove("goods");
    }
    static void writeRoute(Fob f){
        var r=f.route;
        if(f.deliveryGate!=null)r.putLong("delivery",f.deliveryGate.asLong());else r.remove("delivery");
        if(f.receivingGate!=null)r.putLong("receiving",f.receivingGate.asLong());else r.remove("receiving");
        r.putBoolean("commissioned",f.commissioned);r.putLong("baseline",f.baseline);r.putLong("clock",f.clock);
        r.putLong("nextPassive",f.nextPassive);r.putLong("lastNoteAt",f.lastNoteAt);
        r.putString("lastKind",f.lastKind);r.putInt("lastItems",f.lastItems);r.putLong("lastAt",f.lastAt);r.putLong("lastTrip",f.lastTrip);
        f.stockpile.putInt("workers",f.workers);
        if(f.catapult!=null)r.putLong("catapult",f.catapult.asLong());else r.remove("catapult");
        var ships=new ListTag();for(var c:f.shipments)ships.add(c.copy());r.put("shipments",ships);
        var note=new CompoundTag();f.note.forEach(note::putInt);r.put("noted",note);r.remove("note");
        var t=new CompoundTag();t.putInt("specialty",f.specialty.ordinal());
        byte[] on=new byte[f.slotOn.length];for(int i=0;i<on.length;i++)on[i]=(byte)(f.slotOn[i]?1:0);t.putByteArray("on",on);
        t.putInt("mine",f.mine);t.putInt("farm",f.farm);t.putInt("special",f.special);
        long[] tally=new long[f.tally.length];for(int i=0;i<tally.length;i++)tally[i]=Double.doubleToLongBits(f.tally[i]);t.putLongArray("tally",tally);
        t.putLongArray("zone",f.zoneColumns);t.putInt("research",f.researchXp);t.put("depot",f.depotTag());
        r.put("tribute",t);
    }
    /** The FOB whose Delivery or Receiving Gate stands at {@code anchor}, or null. */
    Fob byCatapult(BlockPos pos){for(var f:fobs.values())if(pos.equals(f.catapult))return f;return null;}
    Fob byGate(BlockPos anchor){for(var f:fobs.values())if(anchor.equals(f.deliveryGate)||anchor.equals(f.receivingGate))return f;return null;}
    @Override public CompoundTag save(CompoundTag n){
        n.putLong("nextId",nextId);n.putLong("onlineTicks",onlineTicks);
        n.putLong("sosClock",sosClock);n.putLong("sosFob",sosFob);n.putLong("sosLeft",sosLeft);n.putLong("sosSerial",sosSerial);if(sosMap!=null)n.put("sosMap",sosMap);
        var list=new ListTag();
        for(var f:fobs.values()){
            var c=new CompoundTag();c.putLong("id",f.id);c.putString("name",f.name);c.putLong("pos",f.pos.asLong());c.putLong("village",f.villageSection);
            c.putInt("state",f.state.ordinal());c.putBoolean("activated",f.activated);c.putLong("placed",f.placedAt);
            c.putInt("raid",f.raidId);c.putInt("raidVillagers",f.raidVillagers);c.putBoolean("showZone",f.showZone);
            writeRoute(f);c.put("dead",f.deadVillagers);c.put("stockpile",f.stockpile);c.put("route",f.route);
            list.add(c);
        }
        n.put("fobs",list);return n;
    }
}
