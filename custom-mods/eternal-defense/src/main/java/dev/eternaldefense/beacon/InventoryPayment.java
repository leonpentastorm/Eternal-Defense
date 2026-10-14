package dev.eternaldefense.beacon;

import net.minecraft.world.item.ItemStack;
import java.util.*;

/**
 * Pays a request's costs. 0.0.40: from the player's pack first, then from the Home Zone's Logistics Center Hubs, nearest first
 * ({@link Supply}). The allocation is a minimum-cost maximum flow ({@link SupplyRules#allocate}), so overlapping tags and repeated inputs never
 * count a slot twice and never block a request that could be paid. Planned on copies, carried out only when everything is there.
 */
final class InventoryPayment {
    /** {@code inventory}: copies of the pack's slots (main, then off hand) after paying; {@code pulls}: what the Hubs pay. */
    record Plan(List<ItemStack> inventory,int[] allocated,boolean payable,List<Supply.Pull> pulls){}
    static boolean matches(WeaponPlatform.Cost cost,ItemStack stack){
        if(stack.isEmpty())return false;
        // Gun conversions accept a used weapon of the exact indexed model, never any generic gun.
        for(var example:cost.ingredient().getItems())if(example.hasTag()&&example.getTag().contains("GunId"))
            return Arrays.stream(cost.ingredient().getItems()).anyMatch(s->s.getItem()==stack.getItem()&&s.hasTag()&&stack.hasTag()&&s.getTag().getString("GunId").equals(stack.getTag().getString("GunId")));
        return cost.ingredient().test(stack);
    }
    static List<WeaponPlatform.Cost> consolidate(List<WeaponPlatform.Cost> costs){
        var groups=new LinkedHashMap<String,WeaponPlatform.Cost>();
        for(var c:costs){String key=c.ingredient().toJson().toString();var old=groups.get(key);groups.put(key,new WeaponPlatform.Cost(c.ingredient(),c.count()+(old==null?0:old.count())));}
        return new ArrayList<>(groups.values());
    }
    static Plan plan(Supply.Where w,List<WeaponPlatform.Cost> costs){
        var p=w.player();
        var inventory=new ArrayList<>(p.getInventory().items.stream().map(ItemStack::copy).toList());p.getInventory().offhand.forEach(s->inventory.add(s.copy()));
        // the sources: every pack slot (tier 0), then every kind of item each Hub holds that some cost accepts (tier 1 = nearest Hub)
        var stacks=new ArrayList<ItemStack>(inventory);var supply=new ArrayList<Integer>();var tier=new ArrayList<Integer>();var kinds=new ArrayList<Supply.Pull>();
        for(var s:inventory){supply.add(s.getCount());tier.add(0);kinds.add(null);}
        if(!costs.isEmpty()){var hubs=Supply.hubs(w);
            for(int r=0;r<hubs.size();r++)for(var e:hubs.get(r).storage.kinds().entrySet()){
                var example=e.getKey().stack(1);if(costs.stream().noneMatch(c->matches(c,example)))continue;
                stacks.add(example);supply.add(e.getValue());tier.add(r+1);kinds.add(new Supply.Pull(hubs.get(r),e.getKey(),e.getValue()));
            }}
        int sources=stacks.size(),count=costs.size();var accepts=new boolean[sources][count];var demand=new int[count];
        for(int c=0;c<count;c++){demand[c]=costs.get(c).ingredient().isEmpty()?0:costs.get(c).count();for(int s=0;s<sources;s++)accepts[s][c]=matches(costs.get(c),stacks.get(s));}
        var alloc=SupplyRules.allocate(supply.stream().mapToInt(Integer::intValue).toArray(),tier.stream().mapToInt(Integer::intValue).toArray(),demand,accepts);
        var pulls=new ArrayList<Supply.Pull>();var firstCost=new IdentityHashMap<Supply.Pull,Integer>();
        for(int s=0;s<sources;s++){int n=alloc.from(s);if(n<=0)continue;if(kinds.get(s)==null){inventory.get(s).shrink(n);continue;}
            var pull=new Supply.Pull(kinds.get(s).hub(),kinds.get(s).key(),n);pulls.add(pull);
            int c=0;while(c<count-1&&alloc.taken()[s][c]<=0)c++;firstCost.put(pull,c);}
        // the Hub line names what it paid in the request's own order (the recipe's), not in the order the Hub happens to store it
        pulls.sort(Comparator.comparingInt(firstCost::get));
        return new Plan(inventory,alloc.got(),alloc.payable(),pulls);
    }
    /** Pays and hands over {@code output}: everything or nothing (the output must fit in the pack). Creative players pay nothing. */
    static boolean commit(Supply.Where w,List<WeaponPlatform.Cost> costs,ItemStack output){
        var p=w.player();
        var plan=plan(w,p.isCreative()?List.of():costs);if(!plan.payable())return false;var slots=plan.inventory();var rest=output.copy();int main=p.getInventory().items.size();
        for(int i=0;i<main&&!rest.isEmpty();i++){var stack=slots.get(i);if(!stack.isEmpty()&&ItemStack.isSameItemSameTags(stack,rest)){int fit=Math.min(rest.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount()));stack.grow(fit);rest.shrink(fit);}}
        for(int i=0;i<main&&!rest.isEmpty();i++)if(slots.get(i).isEmpty()){int amount=Math.min(rest.getCount(),rest.getMaxStackSize());slots.set(i,rest.copyWithCount(amount));rest.shrink(amount);}
        if(!rest.isEmpty())return false;
        for(var pull:plan.pulls())if(pull.hub().storage.count(pull.key())<pull.count())return false;   // planned this tick on the server thread: always there
        for(int i=0;i<main;i++)p.getInventory().items.set(i,slots.get(i));for(int i=0;i<p.getInventory().offhand.size();i++)p.getInventory().offhand.set(i,slots.get(main+i));
        var paid=new ArrayList<Supply.Pull>();for(var pull:plan.pulls()){int n=pull.hub().storage.extract(pull.key(),pull.count(),false);if(n>0)paid.add(new Supply.Pull(pull.hub(),pull.key(),n));}
        HubNotices.pulled(p,paid);
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();if(p.containerMenu!=p.inventoryMenu)p.containerMenu.broadcastChanges();return true;
    }
    static String failure(Supply.Where w,List<WeaponPlatform.Cost> costs){
        var p=w.player();var plan=plan(w,costs);if(p.isCreative()||plan.payable())return "Inventory full. Make room for the output. Nothing consumed.";
        for(int i=0;i<costs.size();i++)if(plan.allocated()[i]<costs.get(i).count()){var choices=costs.get(i).ingredient().getItems();String name=choices.length==0?"unavailable ingredient":choices[0].getHoverName().getString();return "Need "+(costs.get(i).count()-plan.allocated()[i])+" more "+name+". Nothing consumed.";}
        return "Missing required materials. Nothing consumed.";
    }
}
