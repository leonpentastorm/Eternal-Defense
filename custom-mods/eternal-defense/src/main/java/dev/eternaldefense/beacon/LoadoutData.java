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
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Every Loadout Station's coin slots and locker, and every player's active slot and cooldown, kept with the world. */
final class LoadoutData extends SavedData {
    static final String NAME="eternal_defense_loadout";

    /** One station: where it stands, its 9 shared coin slots (each remembering its coin kind) and its 27-slot locker. */
    final class Machine {
        final UUID id;ResourceKey<Level> dimension=Level.OVERWORLD;BlockPos anchor=BlockPos.ZERO;Direction facing=Direction.NORTH;boolean placed;
        final ItemStack[] coins=new ItemStack[LoadoutRules.COIN_SLOTS],kinds=new ItemStack[LoadoutRules.COIN_SLOTS];
        final SimpleContainer locker=new SimpleContainer(LoadoutRules.LOCKER_SLOTS);
        /** The coin slots as a container for menus: a slot takes only coins of its kind. */
        final Container shelf=new Shelf();
        Machine(UUID id){this.id=id;Arrays.fill(coins,ItemStack.EMPTY);Arrays.fill(kinds,ItemStack.EMPTY);locker.addListener(c->setDirty());}
        boolean hasCoins(){for(var s:coins)if(!s.isEmpty())return true;return false;}
        /** True when slot i takes this coin: it is a coin and the slot has no kind yet or this is its kind. */
        boolean accepts(int i,ItemStack coin){return coin.is(EternalDefense.LOADOUT_COIN.get())&&LoadoutRules.accepts(kinds[i].isEmpty()?null:kinds[i].getTag(),coin.getTag()==null?new CompoundTag():coin.getTag());}
        /** The kind slot i wants: its remembered coin (count 1), or empty. */
        ItemStack kind(int i){return kinds[i];}
        /** Forgets the kind of an empty slot. */
        boolean forget(int i){if(!coins[i].isEmpty()||kinds[i].isEmpty())return false;kinds[i]=ItemStack.EMPTY;setDirty();return true;}
        /** Takes one coin from slot i (null when it is empty). */
        ItemStack takeOne(int i){if(coins[i].isEmpty())return null;var one=coins[i].split(1);if(coins[i].isEmpty())coins[i]=ItemStack.EMPTY;setDirty();return one;}
        private final class Shelf implements Container {
            @Override public int getContainerSize(){return LoadoutRules.COIN_SLOTS;}
            @Override public boolean isEmpty(){return !hasCoins();}
            @Override public ItemStack getItem(int i){return coins[i];}
            @Override public ItemStack removeItem(int i,int n){var s=coins[i];if(s.isEmpty()||n<=0)return ItemStack.EMPTY;var out=s.split(n);if(s.isEmpty())coins[i]=ItemStack.EMPTY;setDirty();return out;}
            @Override public ItemStack removeItemNoUpdate(int i){var s=coins[i];coins[i]=ItemStack.EMPTY;setDirty();return s;}
            @Override public void setItem(int i,ItemStack s){
                coins[i]=s==null?ItemStack.EMPTY:s;
                if(!coins[i].isEmpty()&&kinds[i].isEmpty())kinds[i]=coins[i].copyWithCount(1);
                setDirty();
            }
            @Override public int getMaxStackSize(){return LoadoutRules.COIN_STACK;}
            @Override public boolean canPlaceItem(int i,ItemStack s){return accepts(i,s);}
            @Override public void setChanged(){for(int i=0;i<coins.length;i++)if(!coins[i].isEmpty()&&kinds[i].isEmpty())kinds[i]=coins[i].copyWithCount(1);setDirty();}
            @Override public boolean stillValid(Player p){return placed;}
            @Override public void clearContent(){Arrays.fill(coins,ItemStack.EMPTY);setDirty();}
        }
    }

    private final Map<UUID,Machine> machines=new LinkedHashMap<>();
    /** Active slots and cooldowns by player. */
    final LoadoutPlayers players=new LoadoutPlayers();

    static LoadoutData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(LoadoutData::load,LoadoutData::new,NAME);}
    static LoadoutData get(ServerLevel level){return get(level.getServer());}

    Machine machine(UUID id){return id==null?null:machines.get(id);}
    Collection<Machine> all(){return Collections.unmodifiableCollection(machines.values());}
    Machine place(ResourceKey<Level> dimension,BlockPos anchor,Direction facing){
        var m=new Machine(UUID.randomUUID());m.dimension=dimension;m.anchor=anchor.immutable();m.facing=facing;m.placed=true;machines.put(m.id,m);setDirty();return m;
    }
    void remove(Machine m){machines.remove(m.id);setDirty();}
    /** Placed stations in level's dimension, nearest to pos first. */
    List<Machine> near(ServerLevel level,BlockPos pos){
        var out=new ArrayList<Machine>();for(var m:machines.values())if(m.placed&&m.dimension==level.dimension())out.add(m);
        out.sort(Comparator.comparingDouble((Machine m)->m.anchor.distSqr(pos)).thenComparing(m->m.id));return out;
    }
    @Override public void setDirty(){super.setDirty();}

    @Override public CompoundTag save(CompoundTag tag){
        var list=new ListTag();
        for(var m:machines.values()){
            var t=new CompoundTag();t.putUUID("Id",m.id);t.putString("Dimension",m.dimension.location().toString());t.putLong("Anchor",m.anchor.asLong());
            t.putString("Facing",m.facing.getSerializedName());t.putBoolean("Placed",m.placed);
            var coins=new ListTag();
            for(int i=0;i<LoadoutRules.COIN_SLOTS;i++){if(m.coins[i].isEmpty()&&m.kinds[i].isEmpty())continue;var c=new CompoundTag();c.putByte("Slot",(byte)i);
                if(!m.coins[i].isEmpty())c.put("Coins",m.coins[i].save(new CompoundTag()));if(!m.kinds[i].isEmpty())c.put("Kind",m.kinds[i].save(new CompoundTag()));coins.add(c);}
            t.put("Shelf",coins);
            var locker=new ListTag();for(int i=0;i<m.locker.getContainerSize();i++)if(!m.locker.getItem(i).isEmpty()){var c=m.locker.getItem(i).save(new CompoundTag());c.putByte("Slot",(byte)i);locker.add(c);}
            t.put("Locker",locker);list.add(t);
        }
        tag.put("Machines",list);tag.put("Players",players.save());return tag;
    }
    static LoadoutData load(CompoundTag tag){
        var d=new LoadoutData();
        for(Tag x:tag.getList("Machines",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)x;if(!t.hasUUID("Id"))continue;var m=d.new Machine(t.getUUID("Id"));
            var dim=ResourceLocation.tryParse(t.getString("Dimension"));if(dim!=null)m.dimension=ResourceKey.create(Registries.DIMENSION,dim);
            m.anchor=BlockPos.of(t.getLong("Anchor"));var f=Direction.byName(t.getString("Facing"));if(f!=null&&f.getAxis().isHorizontal())m.facing=f;m.placed=t.getBoolean("Placed");
            for(Tag y:t.getList("Shelf",Tag.TAG_COMPOUND)){var c=(CompoundTag)y;int i=c.getByte("Slot");if(i<0||i>=LoadoutRules.COIN_SLOTS)continue;
                if(c.contains("Coins"))m.coins[i]=ItemStack.of(c.getCompound("Coins"));if(c.contains("Kind"))m.kinds[i]=ItemStack.of(c.getCompound("Kind"));}
            for(Tag y:t.getList("Locker",Tag.TAG_COMPOUND)){var c=(CompoundTag)y;int i=c.getByte("Slot");if(i>=0&&i<LoadoutRules.LOCKER_SLOTS)m.locker.setItem(i,ItemStack.of(c));}
            d.machines.put(m.id,m);
        }
        d.players.load(tag.getCompound("Players"));
        return d;
    }

    /** Each player's active coin slot (0..8) and the game time their next print may start, by UUID. */
    static final class LoadoutPlayers {
        private final Map<UUID,Integer> active=new HashMap<>();private final Map<UUID,Long> ready=new HashMap<>();
        int active(UUID p){return active.getOrDefault(p,0);}
        void setActive(UUID p,int slot){active.put(p,Math.max(0,Math.min(LoadoutRules.COIN_SLOTS-1,slot)));}
        long readyAt(UUID p){return ready.getOrDefault(p,0L);}
        void setReadyAt(UUID p,long at){ready.put(p,at);}
        CompoundTag save(){
            var t=new CompoundTag();var list=new ListTag();
            var ids=new TreeSet<UUID>();ids.addAll(active.keySet());ids.addAll(ready.keySet());
            for(var id:ids){var c=new CompoundTag();c.putUUID("Player",id);c.putInt("Active",active(id));c.putLong("Ready",readyAt(id));list.add(c);}
            t.put("List",list);return t;
        }
        void load(CompoundTag t){
            active.clear();ready.clear();
            for(Tag x:t.getList("List",Tag.TAG_COMPOUND)){var c=(CompoundTag)x;if(!c.hasUUID("Player"))continue;var id=c.getUUID("Player");setActive(id,c.getInt("Active"));setReadyAt(id,c.getLong("Ready"));}
        }
    }
}
