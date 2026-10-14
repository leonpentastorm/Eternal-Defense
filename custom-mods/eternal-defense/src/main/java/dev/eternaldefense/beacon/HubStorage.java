package dev.eternaldefense.beacon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import java.util.*;

/**
 * 0.0.40: one Logistics Center Hub's 270 slots (normal stacking; an unstackable item takes a slot of its own). It lives in the world's saved
 * data ({@link HubData}), never in a block entity, so a Hub across chunk borders or with unloaded chunks cannot lose or copy items.
 * <p>An index is kept with every change: how many of each kind of item it holds, which slots hold each kind and which slots are empty. So
 * "how many X", inserting and the supply's pulls never scan the 270 slots, and the item handler's per-slot calls (which Create and hoppers
 * make constantly) are a single lookup with no copy.
 */
final class HubStorage implements IItemHandler {
    static final int SLOTS=270,PAGE=54,PAGES=SLOTS/PAGE;

    /** A kind of item: the item and its tag (as {@link ItemStack#isSameItemSameTags}). The hash is worked out once. */
    static final class Key {
        final Item item;final CompoundTag tag;private final int hash;
        private Key(Item item,CompoundTag tag){this.item=item;this.tag=tag;this.hash=31*System.identityHashCode(item)+(tag==null?0:tag.hashCode());}
        static Key of(ItemStack s){return new Key(s.getItem(),s.getTag()==null?null:s.getTag().copy());}
        /** A stack of this kind ({@code count} items). */
        ItemStack stack(int count){var s=new ItemStack(item,count);if(tag!=null)s.setTag(tag.copy());return s;}
        @Override public boolean equals(Object o){return o instanceof Key k&&k.item==item&&Objects.equals(k.tag,tag);}
        @Override public int hashCode(){return hash;}
    }

    private final ItemStack[] slots=new ItemStack[SLOTS];
    /** What the index last saw in each slot (a menu may change a stack in place and only then say so). */
    private final Key[] seenKey=new Key[SLOTS];private final int[] seenCount=new int[SLOTS];
    private final Map<Key,Integer> counts=new HashMap<>();
    private final Map<Key,TreeSet<Integer>> where=new HashMap<>();
    private final TreeSet<Integer> empty=new TreeSet<>();
    /** Told after every change (the saved data marks itself dirty). */
    Runnable onChange=()->{};

    HubStorage(){Arrays.fill(slots,ItemStack.EMPTY);for(int i=0;i<SLOTS;i++)empty.add(i);}

    // ---- the index --------------------------------------------------------------------------------------------------------------------
    private void unindex(int slot){
        var key=seenKey[slot];if(key==null)return;
        int left=counts.getOrDefault(key,0)-seenCount[slot];if(left>0)counts.put(key,left);else counts.remove(key);
        var set=where.get(key);if(set!=null){set.remove(slot);if(set.isEmpty())where.remove(key);}
        seenKey[slot]=null;seenCount[slot]=0;empty.add(slot);
    }
    private void index(int slot){
        var s=slots[slot];if(s.isEmpty()){slots[slot]=ItemStack.EMPTY;return;}
        var key=Key.of(s);seenKey[slot]=key;seenCount[slot]=s.getCount();
        counts.merge(key,s.getCount(),Integer::sum);where.computeIfAbsent(key,k->new TreeSet<>()).add(slot);empty.remove(slot);
    }
    /** Brings the index up to date with one slot whose stack may have been changed in place. True if it had changed. */
    boolean resync(int slot){
        var s=slots[slot];var key=seenKey[slot];
        if(s.isEmpty()?key==null:key!=null&&seenCount[slot]==s.getCount()&&key.item==s.getItem()&&Objects.equals(key.tag,s.getTag()))return false;
        unindex(slot);index(slot);return true;
    }
    /** Re-checks a range of slots (a menu page after a click); tells the saved data once if anything changed. */
    void resync(int from,int count){boolean changed=false;for(int i=Math.max(0,from);i<Math.min(SLOTS,from+count);i++)changed|=resync(i);if(changed)onChange.run();}
    void resyncAll(){resync(0,SLOTS);}

    // ---- reading -------------------------------------------------------------------------------------------------------------------------
    ItemStack get(int slot){return slots[slot];}
    /** How many of this kind it holds. */
    int count(Key key){return counts.getOrDefault(key,0);}
    /** How many of this item it holds, whatever their tags. */
    int count(Item item){int n=0;for(var e:counts.entrySet())if(e.getKey().item==item)n+=e.getValue();return n;}
    /** The kinds it holds, with their counts (a copy, safe to keep while the Hub changes). */
    Map<Key,Integer> kinds(){return new LinkedHashMap<>(counts);}
    boolean isEmpty(){return empty.size()==SLOTS;}
    int usedSlots(){return SLOTS-empty.size();}

    // ---- changing ------------------------------------------------------------------------------------------------------------------------
    void set(int slot,ItemStack stack){slots[slot]=stack==null?ItemStack.EMPTY:stack;unindex(slot);index(slot);onChange.run();}
    /** Puts a stack in: first onto stacks of the same kind that have room, then into the first empty slots. Returns what did not fit. */
    ItemStack insert(ItemStack stack,boolean simulate){
        if(stack.isEmpty())return ItemStack.EMPTY;
        int left=stack.getCount(),limit=Math.min(64,stack.getMaxStackSize());boolean changed=false;
        var same=where.get(Key.of(stack));
        if(same!=null&&stack.isStackable())for(int slot:List.copyOf(same)){
            var s=slots[slot];if(!ItemHandlerHelper.canItemStacksStack(s,stack))continue;
            int room=Math.min(limit,s.getMaxStackSize())-s.getCount();if(room<=0)continue;
            int moved=Math.min(room,left);left-=moved;if(!simulate){s.grow(moved);resync(slot);changed=true;}if(left<=0)break;
        }
        if(left>0)for(int slot:List.copyOf(empty)){
            int moved=Math.min(limit,left);left-=moved;
            if(!simulate){slots[slot]=stack.copyWithCount(moved);index(slot);changed=true;}if(left<=0)break;
        }
        if(changed)onChange.run();
        return left<=0?ItemStack.EMPTY:stack.copyWithCount(left);
    }
    /** Takes up to {@code amount} of one kind, from its last slots first (the first ones stay put). Returns how many were taken. */
    int extract(Key key,int amount,boolean simulate){
        if(amount<=0)return 0;var set=where.get(key);if(set==null)return 0;
        if(simulate)return Math.min(amount,count(key));
        int taken=0;
        for(int slot:new ArrayList<>(set.descendingSet())){
            var s=slots[slot];int n=Math.min(amount-taken,s.getCount());s.shrink(n);if(s.isEmpty())slots[slot]=ItemStack.EMPTY;resync(slot);taken+=n;if(taken>=amount)break;
        }
        if(taken>0)onChange.run();
        return taken;
    }
    /** Everything out (a Hub broken while it still holds items drops them). */
    List<ItemStack> drain(){var out=new ArrayList<ItemStack>();for(int i=0;i<SLOTS;i++)if(!slots[i].isEmpty()){out.add(slots[i]);slots[i]=ItemStack.EMPTY;unindex(i);}onChange.run();return out;}

    // ---- the item handler (hoppers, droppers, Create's funnels, chutes and arms): one slot at a time --------------------------------------
    @Override public int getSlots(){return SLOTS;}
    /** The slot's own stack, not a copy (the handler's contract: callers do not change it). */
    @Override public ItemStack getStackInSlot(int slot){return slot<0||slot>=SLOTS?ItemStack.EMPTY:slots[slot];}
    @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
        if(stack.isEmpty()||slot<0||slot>=SLOTS)return stack;
        var s=slots[slot];int limit=Math.min(64,stack.getMaxStackSize());
        if(!s.isEmpty()){if(!ItemHandlerHelper.canItemStacksStack(s,stack))return stack;limit=Math.min(limit,s.getMaxStackSize());}
        int room=limit-s.getCount();if(room<=0)return stack;
        int moved=Math.min(room,stack.getCount());
        if(!simulate){if(s.isEmpty())slots[slot]=stack.copyWithCount(moved);else s.grow(moved);resync(slot);onChange.run();}
        return moved>=stack.getCount()?ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-moved);
    }
    @Override public ItemStack extractItem(int slot,int amount,boolean simulate){
        if(amount<=0||slot<0||slot>=SLOTS)return ItemStack.EMPTY;
        var s=slots[slot];if(s.isEmpty())return ItemStack.EMPTY;
        int n=Math.min(amount,Math.min(s.getCount(),s.getMaxStackSize()));
        if(simulate)return s.copyWithCount(n);
        var out=s.split(n);if(s.isEmpty())slots[slot]=ItemStack.EMPTY;resync(slot);onChange.run();return out;
    }
    /** Every slot holds a normal stack: at most the item's own stack size, and never more than 64. */
    @Override public int getSlotLimit(int slot){var s=getStackInSlot(slot);return s.isEmpty()?64:Math.min(64,s.getMaxStackSize());}
    @Override public boolean isItemValid(int slot,ItemStack stack){return true;}

    // ---- saving ---------------------------------------------------------------------------------------------------------------------------
    ListTag save(){var list=new ListTag();for(int i=0;i<SLOTS;i++)if(!slots[i].isEmpty()){var t=slots[i].save(new CompoundTag());t.putShort("Slot",(short)i);list.add(t);}return list;}
    void load(ListTag list){
        Arrays.fill(slots,ItemStack.EMPTY);Arrays.fill(seenKey,null);Arrays.fill(seenCount,0);counts.clear();where.clear();empty.clear();for(int i=0;i<SLOTS;i++)empty.add(i);
        for(Tag t:list){var c=(CompoundTag)t;int slot=c.getShort("Slot");if(slot<0||slot>=SLOTS)continue;var s=ItemStack.of(c);if(s.isEmpty())continue;slots[slot]=s;unindex(slot);index(slot);}
    }
}
