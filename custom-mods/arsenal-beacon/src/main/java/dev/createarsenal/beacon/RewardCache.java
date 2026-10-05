package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Rewards leave the saved queue only after insertion into a real, shared double chest. */
final class RewardCache {
    static Container container(ServerLevel l,CampaignData d){
        if(d.rewardChest==null||!l.hasChunkAt(d.rewardChest)||!l.hasChunkAt(d.rewardChest.east()))return null;
        var a=l.getBlockState(d.rewardChest);var b=l.getBlockState(d.rewardChest.east());
        if(!a.is(Blocks.CHEST)||!b.is(Blocks.CHEST)||a.getValue(ChestBlock.TYPE)!=ChestType.RIGHT||b.getValue(ChestBlock.TYPE)!=ChestType.LEFT||a.getValue(ChestBlock.FACING)!=Direction.SOUTH||b.getValue(ChestBlock.FACING)!=Direction.SOUTH)return null;
        return ChestBlock.getContainer((ChestBlock)Blocks.CHEST,a,l,d.rewardChest,true);
    }
    static boolean create(ServerLevel l,CampaignData d){
        if(d.active())return false; // New blocks cannot appear while the raid journal is locked.
        for(int r=3;r<=Math.min(12,d.radius());r++)for(int z=-r;z<=r;z++)for(int x=-r;x<r;x++){
            if(Math.max(Math.abs(x),Math.abs(z))!=r)continue;
            BlockPos a=d.beacon.offset(x,0,z),b=a.east();
            if(!d.inside(b)||!l.hasChunkAt(a)||!l.hasChunkAt(b)||!l.getWorldBorder().isWithinBounds(a)||!l.getWorldBorder().isWithinBounds(b))continue;
            if(!l.isEmptyBlock(a)||!l.isEmptyBlock(b)||!l.isEmptyBlock(a.above())||!l.isEmptyBlock(b.above()))continue;
            if(!l.getBlockState(a.below()).isFaceSturdy(l,a.below(),Direction.UP)||!l.getBlockState(b.below()).isFaceSturdy(l,b.below(),Direction.UP))continue;
            boolean adjacent=false;for(Direction side:Direction.Plane.HORIZONTAL)if(l.getBlockState(a.relative(side)).is(Blocks.CHEST)||l.getBlockState(b.relative(side)).is(Blocks.CHEST))adjacent=true;
            if(adjacent)continue; // Never merge with or take ownership of a player's existing chest.
            var state=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH);
            l.setBlock(a,state.setValue(ChestBlock.TYPE,ChestType.RIGHT),3);l.setBlock(b,state.setValue(ChestBlock.TYPE,ChestType.LEFT),3);
            d.rewardChest=a;d.setDirty();return container(l,d)!=null;
        }
        return false;
    }
    static void deposit(CampaignData d,Container chest){
        for(var it=d.rewards.iterator();it.hasNext();){
            CompoundTag entry=it.next();ItemStack remaining=ItemStack.of(entry);
            // Merge before filling empty slots. Work on copies; container stacks never alias reward NBT.
            for(int pass=0;pass<2&&!remaining.isEmpty();pass++)for(int slot=0;slot<chest.getContainerSize()&&!remaining.isEmpty();slot++){
                ItemStack old=chest.getItem(slot);if(pass==0?old.isEmpty()||!ItemStack.isSameItemSameTags(old,remaining):!old.isEmpty())continue;
                int room=Math.min(chest.getMaxStackSize(),remaining.getMaxStackSize())-old.getCount();if(room<=0)continue;
                int take=Math.min(room,remaining.getCount());ItemStack next=remaining.copy();next.setCount(old.getCount()+take);chest.setItem(slot,next);remaining.shrink(take);
            }
            if(remaining.isEmpty())it.remove();else{entry.merge(remaining.save(new CompoundTag()));}
        }
        chest.setChanged();d.setDirty();
    }
    static String claim(ServerPlayer p,CampaignData d){
        if(!d.installed()||!ArsenalBeacon.near(p,d))return "Stand near your planted beacon to collect rewards.";
        ServerLevel l=p.server.overworld();Container chest=container(l,d);
        if(chest==null){
            if(d.rewards.isEmpty())return "No queued rewards. Win a wave or raid first.";
            if(!create(l,d))return d.active()?"Create the reward chest between raids. Current rewards remain safely queued.":"Clear two adjacent ground-level blocks with headroom inside the base zone, at least three blocks from the beacon. Rewards remain queued.";
            chest=container(l,d);
        }
        deposit(d,chest);
        // Native chest menu gives all players the same persistent 54 slots, including shift-click.
        var provider=Blocks.CHEST.getMenuProvider(l.getBlockState(d.rewardChest),l,d.rewardChest);
        if(provider!=null)p.openMenu(provider);
        return "Reward chest: "+d.rewardChest.toShortString()+". "+(d.rewards.isEmpty()?"All rewards deposited.":"Chest full: "+d.rewards.size()+" stacks remain queued. Empty it, then collect again.");
    }
}
