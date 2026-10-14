package dev.eternaldefense.beacon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** The Loadout Flare's side of the station: which coin a call uses, one call at a time, and the coin taken when the cannon fires. */
final class LoadoutCall {
    private LoadoutCall(){}
    /** The station, slot and coin kind a call was made with. */
    record Reserved(UUID machine,int slot,ItemStack kind){
        CompoundTag save(){var t=new CompoundTag();t.putUUID("Machine",machine);t.putInt("Slot",slot);if(!kind.isEmpty())t.put("Kind",kind.save(new CompoundTag()));return t;}
        static Reserved load(CompoundTag t){return t.hasUUID("Machine")?new Reserved(t.getUUID("Machine"),t.getInt("Slot"),t.contains("Kind")?ItemStack.of(t.getCompound("Kind")):ItemStack.EMPTY):null;}
    }
    private record Pending(UUID flare,int since){}
    /** Each player's Loadout call in flight (not saved: a flare that reloads registers itself again). */
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static int now(){var s=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();return s==null?0:s.getTickCount();}
    /** A call's flare is alive (refreshed every tick it runs, so a flare frozen in an unloaded chunk goes stale). */
    static void pending(UUID player,UUID flare){PENDING.put(player,new Pending(flare,now()));}
    static void done(UUID player,UUID flare){var p=PENDING.get(player);if(p!=null&&p.flare().equals(flare))PENDING.remove(player);}
    static void clear(){PENDING.clear();}

    /** The base's station: the placed one nearest the beacon, inside the Home Zone, or null. */
    static LoadoutData.Machine station(MinecraftServer server){
        var overworld=server.overworld();var d=CampaignData.get(overworld);if(!d.installed())return null;
        for(var m:LoadoutData.get(overworld).near(overworld,d.beacon))if(m.dimension==Level.OVERWORLD&&BaseZone.problem(overworld,m.anchor)==null)return m;
        return null;
    }
    /** The Home Zone's Hubs that hold a coin identical to kind, nearest the station first. */
    private static HubData.Hub hub(ServerLevel overworld,LoadoutData.Machine m,ItemStack kind){
        if(kind.isEmpty())return null;var key=HubStorage.Key.of(kind);
        for(var h:HubData.get(overworld).working(overworld,m.anchor))if(h.storage.count(key)>0)return h;
        return null;
    }
    /** Null when p may call a kit in; otherwise a key suffix under support.refused. */
    static String problem(ServerPlayer p){
        var pending=PENDING.get(p.getUUID());
        if(pending!=null&&now()-pending.since()<LoadoutRules.CALL_STALE)return "loadout_pending";
        var m=station(p.server);if(m==null)return "no_station";
        int slot=LoadoutData.get(p.server).players.active(p.getUUID());
        if(!m.coins[slot].isEmpty())return null;
        return hub(p.server.overworld(),m,m.kind(slot))!=null?null:"no_coin";
    }
    /** What the call will use: the active slot's coin (its kind for the Hub). Nothing is taken yet. */
    static Reserved reserve(ServerPlayer p){
        var m=station(p.server);if(m==null)return null;int slot=LoadoutData.get(p.server).players.active(p.getUUID());
        var kind=!m.coins[slot].isEmpty()?m.coins[slot].copyWithCount(1):m.kind(slot).copy();
        return new Reserved(m.id,slot,kind);
    }
    /** The cannon fires: one coin from the slot, else an identical one from a Hub; its kit as a parcel list (each stack carries LoadoutSlot), or null. */
    static ListTag take(ServerLevel any,UUID owner,Reserved r){
        var overworld=any.getServer().overworld();var m=LoadoutData.get(overworld).machine(r.machine());if(m==null||!m.placed)return null;
        ItemStack coin=m.takeOne(r.slot());
        if(coin==null){
            var h=hub(overworld,m,r.kind());if(h==null)return null;var key=HubStorage.Key.of(r.kind());
            if(h.storage.extract(key,1,false)<1)return null;coin=r.kind().copyWithCount(1);
            var p=owner==null?null:any.getServer().getPlayerList().getPlayer(owner);if(p!=null)HubNotices.pulled(p,List.of(new Supply.Pull(h,key,1)));
        }
        var list=new ListTag();var kit=LoadoutCoin.kit(coin);
        for(int i=0;i<kit.size();i++)if(!kit.get(i).isEmpty()){var t=kit.get(i).save(new CompoundTag());t.putByte("LoadoutSlot",(byte)i);list.add(t);}
        com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] a Loadout Flare used a coin of slot {} ({} stacks)",r.slot()+1,list.size());
        return list;
    }
    /** The kit slot of a parcel list entry, or -1 when it has none. */
    static int slotOf(CompoundTag stack){return stack.contains("LoadoutSlot")?stack.getByte("LoadoutSlot"):-1;}
}
