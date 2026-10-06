package dev.createarsenal.beacon;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The supply grid: a fixed 6 x 6 backing store of which the top-left n x n corner is active (n = 3..6 by Mk level).
 * Upgrading therefore never moves an item.
 */
final class GridContainer implements Container {
    final ItemStack[] items=new ItemStack[36];
    final Runnable changed;
    GridContainer(Runnable changed){java.util.Arrays.fill(items,ItemStack.EMPTY);this.changed=changed;}
    @Override public int getContainerSize(){return 36;}
    @Override public boolean isEmpty(){for(var s:items)if(!s.isEmpty())return false;return true;}
    @Override public ItemStack getItem(int slot){return items[slot];}
    @Override public ItemStack removeItem(int slot,int amount){var out=items[slot].split(amount);if(!out.isEmpty())setChanged();return out;}
    @Override public ItemStack removeItemNoUpdate(int slot){var out=items[slot];items[slot]=ItemStack.EMPTY;return out;}
    @Override public void setItem(int slot,ItemStack stack){items[slot]=stack;setChanged();}
    @Override public void setChanged(){if(changed!=null)changed.run();}
    @Override public boolean stillValid(Player p){return true;}
    /** Empties the grid without notifying anyone (used when the platform is picked up with its contents moved elsewhere). */
    void clearQuietly(){java.util.Arrays.fill(items,ItemStack.EMPTY);}
    @Override public void clearContent(){java.util.Arrays.fill(items,ItemStack.EMPTY);setChanged();}
}
