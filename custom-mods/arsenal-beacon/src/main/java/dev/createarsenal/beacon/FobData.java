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
 * Slices 2 and 3 fill the stockpile, route and dead-villager records; loading keeps whatever is there.
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
    }

    long nextId=1;
    final Map<Long,Fob> fobs=new LinkedHashMap<>();

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
        var d=new FobData();d.nextId=Math.max(1,n.getLong("nextId"));
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
                d.fobs.put(f.id,f);d.nextId=Math.max(d.nextId,f.id+1);
            }catch(RuntimeException ex){com.mojang.logging.LogUtils.getLogger().warn("[fob] skipped a broken FOB record: {}",ex.toString());}
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag n){
        n.putLong("nextId",nextId);var list=new ListTag();
        for(var f:fobs.values()){
            var c=new CompoundTag();c.putLong("id",f.id);c.putString("name",f.name);c.putLong("pos",f.pos.asLong());c.putLong("village",f.villageSection);
            c.putInt("state",f.state.ordinal());c.putBoolean("activated",f.activated);c.putLong("placed",f.placedAt);
            c.putInt("raid",f.raidId);c.putInt("raidVillagers",f.raidVillagers);
            c.put("dead",f.deadVillagers);c.put("stockpile",f.stockpile);c.put("route",f.route);
            list.add(c);
        }
        n.put("fobs",list);return n;
    }
}
