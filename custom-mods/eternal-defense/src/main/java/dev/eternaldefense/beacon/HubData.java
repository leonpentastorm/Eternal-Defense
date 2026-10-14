package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** Every Logistics Center Hub's inventory, kept with the world */
final class HubData extends SavedData {
    static final String NAME="eternal_defense_logistics_hubs";

    /** One Hub: where it stands (its anchor, the middle block), which way it faces, and its 270 slots. */
    static final class Hub {
        final UUID id;ResourceKey<Level> dimension=Level.OVERWORLD;BlockPos anchor=BlockPos.ZERO;Direction facing=Direction.NORTH;
        /** False while it is picked up with the Recovery Shovel (its items stay here, waiting for it to be placed again). */
        boolean placed;
        final HubStorage storage=new HubStorage();
        // Not saved: whether it may work (inside the Home Zone), checked once a second (SupplyRules.recheck)
        private long checkedAt=SupplyRules.NEVER;private boolean working;private LazyOptional<IItemHandler> handler=LazyOptional.empty();
        Hub(UUID id){this.id=id;}
        /** True when it is placed in the Home Zone of a standing Defense Beacon. */
        boolean working(ServerLevel level){
            long now=level.getGameTime();
            if(SupplyRules.recheck(checkedAt,now)){checkedAt=now;boolean was=working;working=placed&&level.dimension()==dimension&&inZone(level);if(was&&!working)invalidate();}
            return working;
        }
        /** Inside the Home Zone of a planted beacon. */
        private boolean inZone(ServerLevel level){
            if(level.dimension()!=Level.OVERWORLD)return false;
            var d=CampaignData.get(level.getServer().overworld());
            if(!d.installed()||!d.inside(anchor))return false;
            return !level.hasChunkAt(d.beacon)||BaseZone.problem(level,anchor)==null;
        }
        /** The item handler for hoppers and Create: the same one on every face of all 27 blocks */
        LazyOptional<IItemHandler> handler(ServerLevel level){
            if(!working(level))return LazyOptional.empty();
            if(!handler.isPresent())handler=LazyOptional.of(()->storage);
            return handler;
        }
        void invalidate(){handler.invalidate();handler=LazyOptional.empty();}
        double distanceSq(BlockPos pos){return anchor.distSqr(pos);}
    }

    private final Map<UUID,Hub> hubs=new LinkedHashMap<>();

    static HubData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(HubData::load,HubData::new,NAME);}
    static HubData get(ServerLevel level){return get(level.getServer());}

    Hub hub(UUID id){return id==null?null:hubs.get(id);}
    Collection<Hub> all(){return Collections.unmodifiableCollection(hubs.values());}
    /** A new, empty Hub (or the one a relocated Hub item names, if it is waiting to be placed again). */
    Hub place(UUID wanted,ResourceKey<Level> dimension,BlockPos anchor,Direction facing){
        var hub=wanted==null?null:hubs.get(wanted);
        if(hub==null||hub.placed){hub=new Hub(UUID.randomUUID());hubs.put(hub.id,hub);hub.storage.onChange=this::setDirty;}
        hub.dimension=dimension;hub.anchor=anchor.immutable();hub.facing=facing;hub.placed=true;hub.checkedAt=SupplyRules.NEVER;setDirty();return hub;
    }
    /** Picked up with the Recovery Shovel: its items wait for it. */
    void pickUp(Hub hub){hub.placed=false;hub.invalidate();setDirty();}
    /** Broken (it was empty, or a creative player broke it and its items were dropped): forgotten. */
    void remove(Hub hub){hub.invalidate();hubs.remove(hub.id);setDirty();}

    /** Hubs that are placed, work (inside the Home Zone) and are in level, nearest to pos first. */
    List<Hub> working(ServerLevel level,BlockPos pos){
        var out=new ArrayList<Hub>();for(var h:hubs.values())if(h.placed&&h.dimension==level.dimension()&&h.working(level))out.add(h);
        out.sort(Comparator.comparingDouble((Hub h)->h.distanceSq(pos)).thenComparing(h->h.id));return out;
    }

    @Override public CompoundTag save(CompoundTag tag){
        var list=new ListTag();
        for(var h:hubs.values()){var t=new CompoundTag();t.putUUID("Id",h.id);t.putString("Dimension",h.dimension.location().toString());t.putLong("Anchor",h.anchor.asLong());
            t.putString("Facing",h.facing.getSerializedName());t.putBoolean("Placed",h.placed);t.put("Items",h.storage.save());list.add(t);}
        tag.put("Hubs",list);return tag;
    }
    static HubData load(CompoundTag tag){
        var d=new HubData();
        for(Tag x:tag.getList("Hubs",Tag.TAG_COMPOUND)){var t=(CompoundTag)x;if(!t.hasUUID("Id"))continue;var h=new Hub(t.getUUID("Id"));
            var dim=ResourceLocation.tryParse(t.getString("Dimension"));if(dim!=null)h.dimension=ResourceKey.create(Registries.DIMENSION,dim);
            h.anchor=BlockPos.of(t.getLong("Anchor"));var f=Direction.byName(t.getString("Facing"));if(f!=null&&f.getAxis().isHorizontal())h.facing=f;h.placed=t.getBoolean("Placed");
            h.storage.load(t.getList("Items",Tag.TAG_COMPOUND));h.storage.onChange=d::setDirty;d.hubs.put(h.id,h);}
        return d;
    }
}
