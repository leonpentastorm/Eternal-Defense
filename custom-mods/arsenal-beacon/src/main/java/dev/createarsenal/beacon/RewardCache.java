package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/** Rewards leave the saved queue only after insertion into the beacon's own 54-slot reward chest. */
final class RewardCache {
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
    /** The beacon itself is the chest: claiming moves queued rewards into its 54 slots and opens them. */
    static String claim(ServerPlayer p,CampaignData d){
        if(!d.installed()||!ArsenalBeacon.near(p,d))return "Stand near your planted beacon to collect rewards.";
        if(d.rewards.isEmpty()&&d.rewardBox.isEmpty())return "No queued rewards. Win a wave or raid first.";
        deposit(d,d.rewardBox);
        p.openMenu(new SimpleMenuProvider((id,inv,pl)->new ChestMenu(MenuType.GENERIC_9x6,id,inv,new BoxView(d,pl),6),Component.translatable("gui.arsenal_beacon.rewards.box")));
        return d.rewards.isEmpty()?"Beacon reward chest opened. All rewards deposited.":"Beacon chest full: "+d.rewards.size()+" stacks remain queued. Empty it, then collect again.";
    }
    /** The beacon's box as one menu sees it: it is only valid while the player stands near the beacon, and every change marks the campaign dirty. */
    static final class BoxView implements Container {
        private final CampaignData d;private final Player player;
        BoxView(CampaignData d,Player player){this.d=d;this.player=player;}
        private SimpleContainer box(){return d.rewardBox;}
        @Override public int getContainerSize(){return 54;}
        @Override public boolean isEmpty(){return box().isEmpty();}
        @Override public ItemStack getItem(int i){return box().getItem(i);}
        @Override public ItemStack removeItem(int i,int n){var r=box().removeItem(i,n);d.setDirty();return r;}
        @Override public ItemStack removeItemNoUpdate(int i){var r=box().removeItemNoUpdate(i);d.setDirty();return r;}
        @Override public void setItem(int i,ItemStack s){box().setItem(i,s);d.setDirty();}
        @Override public void setChanged(){d.setDirty();}
        @Override public boolean stillValid(Player p){return d.installed()&&p.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+.5,d.beacon.getZ()+.5)<=12*12;}
        @Override public void clearContent(){box().clearContent();d.setDirty();}
    }
}
