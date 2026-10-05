package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persist station locations for cheap live score previews; the raid still audits actual blocks. */
final class PlatformRegistry extends SavedData {
    private final Map<Long,Integer> stations=new HashMap<>();
    boolean netherVisited;
    final Set<String> createMilestones=new HashSet<>();
    static PlatformRegistry get(ServerLevel level){return level.getDataStorage().computeIfAbsent(PlatformRegistry::load,PlatformRegistry::new,"arsenal_weapon_platforms");}
    void track(BlockPos pos,BlockState state){
        if(state.getBlock() instanceof WeaponPlatform.Station station){int kind=station.kind.equals("gun")?0:station.kind.equals("ammo")?1:station.kind.equals("attachment")?2:3;stations.put(pos.asLong(),kind*8+state.getValue(WeaponPlatform.AGE));}
        else stations.remove(pos.asLong());setDirty();
    }
    int score(CampaignData d){int[] highest=new int[4];for(var e:stations.entrySet())if(d.inside(BlockPos.of(e.getKey()))){int kind=e.getValue()/8,age=e.getValue()%8;if(kind>=0&&kind<4)highest[kind]=Math.max(highest[kind],age);}return Arrays.stream(highest).map(Rules::platformScore).sum();}
    List<BlockPos> nearby(ServerLevel level,BlockPos weapon){
        return stations.keySet().stream().map(BlockPos::of).filter(pos->pos.distSqr(weapon)<=64&&level.hasChunkAt(pos)&&level.getBlockState(pos).getBlock() instanceof WeaponPlatform.Station station&&!station.kind.equals("gun")).sorted(Comparator.comparingDouble(pos->pos.distSqr(weapon))).limit(24).toList();
    }
    List<BlockPos> nearbyAll(ServerLevel level,BlockPos center){return stations.keySet().stream().map(BlockPos::of).filter(pos->pos.distSqr(center)<=64&&level.hasChunkAt(pos)&&level.getBlockState(pos).getBlock() instanceof WeaponPlatform.Station).sorted(Comparator.comparingDouble(pos->pos.distSqr(center))).toList();}
    static PlatformRegistry load(CompoundTag n){var data=new PlatformRegistry();data.netherVisited=n.getBoolean("netherVisited");for(var v:n.getList("createMilestones",Tag.TAG_STRING))data.createMilestones.add(v.getAsString());for(var entry:n.getList("stations",Tag.TAG_COMPOUND)){var row=(CompoundTag)entry;data.stations.put(row.getLong("pos"),row.getInt("value"));}return data;}
    @Override public CompoundTag save(CompoundTag n){n.putBoolean("netherVisited",netherVisited);var milestones=new ListTag();createMilestones.stream().sorted().forEach(v->milestones.add(StringTag.valueOf(v)));n.put("createMilestones",milestones);var list=new ListTag();stations.forEach((pos,value)->{var row=new CompoundTag();row.putLong("pos",pos);row.putInt("value",value);list.add(row);});n.put("stations",list);return n;}
}
