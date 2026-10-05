package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/**
 * World-wide state of the base support system: where the one Support Platform is, a snapshot of its supply grid, and the
 * cannons. The snapshot lets a call work from anywhere in the world without loading the base's chunks.
 */
final class SupportData extends SavedData {
    static final String NAME="arsenal_beacon_support";
    BlockPos platform;Direction facing=Direction.NORTH;int mk=1;
    final ItemStack[] grid=new ItemStack[36];
    final Set<Long> cannons=new HashSet<>();
    SupportData(){Arrays.fill(grid,ItemStack.EMPTY);}
    static SupportData get(ServerLevel any){
        ServerLevel overworld=any.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(SupportData::load,SupportData::new,NAME);
    }
    boolean hasPlatform(){return platform!=null;}
    /** Cannons close enough to the platform to count as connected. */
    List<BlockPos> connectedCannons(){
        var out=new ArrayList<BlockPos>();if(platform==null)return out;
        double max=SupportRules.CANNON_RANGE*(double)SupportRules.CANNON_RANGE;
        for(long l:cannons){BlockPos p=BlockPos.of(l);if(p.distSqr(platform)<=max)out.add(p);}
        return out;
    }
    /** Active grid cells (the top-left n x n corner of the 6 x 6 backing store), as stacks. */
    List<ItemStack> activeStacks(){
        var out=new ArrayList<ItemStack>();int n=SupportRules.grid(mk);
        for(int r=0;r<n;r++)for(int c=0;c<n;c++){var s=grid[r*6+c];if(!s.isEmpty())out.add(s.copy());}
        return out;
    }
    static SupportData load(CompoundTag n){
        var d=new SupportData();
        if(n.contains("platform"))d.platform=BlockPos.of(n.getLong("platform"));
        d.facing=Direction.from3DDataValue(n.getInt("facing"));if(d.facing.getAxis().isVertical())d.facing=Direction.NORTH;
        d.mk=Math.max(1,Math.min(4,n.getInt("mk")));
        for(var tag:n.getList("grid",Tag.TAG_COMPOUND)){var c=(CompoundTag)tag;int slot=c.getInt("Slot");if(slot>=0&&slot<36)d.grid[slot]=ItemStack.of(c);}
        for(long l:n.getLongArray("cannons"))d.cannons.add(l);
        return d;
    }
    @Override public CompoundTag save(CompoundTag n){
        if(platform!=null)n.putLong("platform",platform.asLong());
        n.putInt("facing",facing.get3DDataValue());n.putInt("mk",mk);
        var list=new ListTag();
        for(int i=0;i<36;i++)if(!grid[i].isEmpty()){var c=new CompoundTag();grid[i].save(c);c.putInt("Slot",i);list.add(c);}
        n.put("grid",list);n.putLongArray("cannons",cannons.stream().mapToLong(Long::longValue).toArray());
        return n;
    }
}
