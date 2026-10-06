package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/**
 * World-wide registry of base support gear. Every player owns at most one Support Platform and one Support Cannon;
 * the blocks themselves hold the grid and Mk level, this only remembers where each player's gear is.
 */
final class SupportData extends net.minecraft.world.level.saveddata.SavedData {
    static final String NAME="arsenal_beacon_support_v2";
    static final class Base{BlockPos platform,cannon;}
    final Map<UUID,Base> bases=new LinkedHashMap<>();
    static SupportData get(ServerLevel any){
        return any.getServer().overworld().getDataStorage().computeIfAbsent(SupportData::load,SupportData::new,NAME);
    }
    Base of(UUID owner){return bases.get(owner);}
    private Base ensure(UUID owner){return bases.computeIfAbsent(owner,k->new Base());}
    void setPlatform(UUID owner,BlockPos pos){ensure(owner).platform=pos.immutable();setDirty();}
    void setCannon(UUID owner,BlockPos pos){ensure(owner).cannon=pos.immutable();setDirty();}
    void clearPlatform(UUID owner,BlockPos pos){var b=bases.get(owner);if(b!=null&&pos.equals(b.platform)){b.platform=null;prune(owner);setDirty();}}
    void clearCannon(UUID owner,BlockPos pos){var b=bases.get(owner);if(b!=null&&pos.equals(b.cannon)){b.cannon=null;prune(owner);setDirty();}}
    private void prune(UUID owner){var b=bases.get(owner);if(b!=null&&b.platform==null&&b.cannon==null)bases.remove(owner);}
    static boolean near(BlockPos platform,BlockPos cannon){
        double max=SupportRules.CANNON_RANGE*(double)SupportRules.CANNON_RANGE;
        return platform!=null&&cannon!=null&&platform.distSqr(cannon)<=max;
    }
    static SupportData load(CompoundTag n){
        var d=new SupportData();
        for(var t:n.getList("bases",Tag.TAG_COMPOUND)){
            var c=(CompoundTag)t;if(!c.hasUUID("owner"))continue;var b=new Base();
            if(c.contains("platform"))b.platform=BlockPos.of(c.getLong("platform"));
            if(c.contains("cannon"))b.cannon=BlockPos.of(c.getLong("cannon"));
            d.bases.put(c.getUUID("owner"),b);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag n){
        var list=new ListTag();
        bases.forEach((owner,b)->{
            var c=new CompoundTag();c.putUUID("owner",owner);
            if(b.platform!=null)c.putLong("platform",b.platform.asLong());
            if(b.cannon!=null)c.putLong("cannon",b.cannon.asLong());
            list.add(c);
        });
        n.put("bases",list);return n;
    }
}
