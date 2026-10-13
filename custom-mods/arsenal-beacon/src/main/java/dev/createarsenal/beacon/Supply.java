package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.Predicate;

/**
 * 0.0.40: the supply source, the one way the mod takes items from a player: "take these items for this player, here". The player's pack
 * pays first, then the Logistics Center Hubs of the Home Zone, pooled, nearest first. Every request is planned first and only then
 * carried out: if the whole amount is not there, nothing is taken (see {@link InventoryPayment}).
 * <p>A request made inside the Home Zone may use the Hubs ({@link #at}); a request made anywhere else, a FOB's for instance, uses the pack
 * alone ({@link #pack}). When a Hub pays part of a request, the player and everyone in the Home Zone are told ({@link HubNotices}).
 */
final class Supply {
    private Supply(){}

    /** Where a request is made: the player, the spot (a station, the beacon, the player's feet) and whether the Home Zone's Hubs may pay. */
    record Where(ServerPlayer player,BlockPos pos,boolean hubs){}
    /** A request at {@code pos}: the Hubs may pay when that spot is inside the Home Zone of a standing beacon. */
    static Where at(ServerPlayer p,BlockPos pos){
        boolean home=pos!=null&&p.level().dimension()==Level.OVERWORLD&&BaseZone.problem(p.level(),pos)==null;
        return new Where(p,pos==null?p.blockPosition():pos.immutable(),home);
    }
    /** A request where the player stands. */
    static Where here(ServerPlayer p){return at(p,p.blockPosition());}
    /** A request outside the Home Zone (a FOB's): the pack alone. */
    static Where pack(ServerPlayer p){return new Where(p,p.blockPosition(),false);}

    /** The Hubs that may pay for this request, nearest first (each brought up to date first, so their counts are exact). */
    static List<HubData.Hub> hubs(Where w){
        if(!w.hubs())return List.of();
        var level=w.player().serverLevel();var list=HubData.get(level).working(level,w.pos());
        for(var h:list)h.storage.resyncAll();
        return list;
    }

    /** How many of this item the request can reach: the pack, then the Hubs. */
    static int count(Where w,Item item){
        var p=w.player();int n=0;
        for(var s:p.getInventory().items)if(s.is(item))n+=s.getCount();
        for(var s:p.getInventory().offhand)if(s.is(item))n+=s.getCount();
        for(var h:hubs(w))n+=h.storage.count(item);
        return n;
    }

    /** Part of a request a Hub paid: so many of one kind. */
    record Pull(HubData.Hub hub,HubStorage.Key key,int count){}

    // ---- moves that are not payments (the kitchens' Gather): the Hubs' items as stand-ins the caller may take from -----------------------
    /** One kind of item in a Hub, as a stack the caller may shrink; {@link #settle} then takes what was used from the Hub. */
    static final class Borrowed {
        final HubData.Hub hub;final HubStorage.Key key;final ItemStack stack;final int had;
        Borrowed(HubData.Hub hub,HubStorage.Key key,int had){this.hub=hub;this.key=key;this.had=had;this.stack=key.stack(had);}
        int used(){return had-stack.getCount();}
    }
    /** Every kind the Hubs hold that {@code wanted} accepts, nearest Hub first. */
    static List<Borrowed> borrow(Where w,Predicate<ItemStack> wanted){
        var out=new ArrayList<Borrowed>();
        for(var h:hubs(w))for(var e:h.storage.kinds().entrySet())if(e.getValue()>0&&wanted.test(e.getKey().stack(1)))out.add(new Borrowed(h,e.getKey(),e.getValue()));
        return out;
    }
    /** Takes from the Hubs what the caller used of the stand-ins, and tells the Home Zone. */
    static void settle(Where w,List<Borrowed> borrowed){
        var pulls=new ArrayList<Pull>();
        for(var b:borrowed){int used=b.used();if(used<=0)continue;int taken=b.hub.storage.extract(b.key,used,false);if(taken>0)pulls.add(new Pull(b.hub,b.key,taken));}
        HubNotices.pulled(w.player(),pulls);
    }
}
