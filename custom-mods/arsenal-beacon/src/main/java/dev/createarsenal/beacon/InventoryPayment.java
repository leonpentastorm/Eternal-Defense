package dev.createarsenal.beacon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Maximum-flow allocation prevents overlapping tags and repeated inputs from double-counting slots. */
final class InventoryPayment {
    record Plan(List<ItemStack> inventory,int[] allocated,boolean payable){}
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
    static Plan plan(ServerPlayer p,List<WeaponPlatform.Cost> costs){
        var inventory=new ArrayList<>(p.getInventory().items.stream().map(ItemStack::copy).toList());p.getInventory().offhand.forEach(s->inventory.add(s.copy()));
        int slots=inventory.size(),count=costs.size(),sink=1+slots+count,n=sink+1,total=0;int[][] capacity=new int[n][n];
        for(int s=0;s<slots;s++){capacity[0][1+s]=inventory.get(s).getCount();for(int c=0;c<count;c++)if(matches(costs.get(c),inventory.get(s)))capacity[1+s][1+slots+c]=inventory.get(s).getCount();}
        for(int c=0;c<count;c++){if(costs.get(c).count()<=0||costs.get(c).ingredient().isEmpty())return new Plan(inventory,new int[count],false);capacity[1+slots+c][sink]=costs.get(c).count();total+=costs.get(c).count();}
        int flow=0;while(true){int[] parent=new int[n];Arrays.fill(parent,-1);parent[0]=0;var q=new ArrayDeque<Integer>();q.add(0);
            while(!q.isEmpty()&&parent[sink]<0){int u=q.remove();for(int v=1;v<n;v++)if(parent[v]<0&&capacity[u][v]>0){parent[v]=u;q.add(v);}}
            if(parent[sink]<0)break;int amount=Integer.MAX_VALUE;for(int v=sink;v!=0;v=parent[v])amount=Math.min(amount,capacity[parent[v]][v]);
            for(int v=sink;v!=0;v=parent[v]){capacity[parent[v]][v]-=amount;capacity[v][parent[v]]+=amount;}flow+=amount;
        }
        int[] allocated=new int[count];for(int c=0;c<count;c++)allocated[c]=costs.get(c).count()-capacity[1+slots+c][sink];
        for(int s=0;s<slots;s++)inventory.get(s).shrink(capacity[1+s][0]);return new Plan(inventory,allocated,flow==total);
    }
    static boolean commit(ServerPlayer p,List<WeaponPlatform.Cost> costs,ItemStack output){
        var plan=plan(p,p.isCreative()?List.of():costs);if(!plan.payable())return false;var slots=plan.inventory();var rest=output.copy();int main=p.getInventory().items.size();
        for(int i=0;i<main&&!rest.isEmpty();i++){var stack=slots.get(i);if(!stack.isEmpty()&&ItemStack.isSameItemSameTags(stack,rest)){int fit=Math.min(rest.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount()));stack.grow(fit);rest.shrink(fit);}}
        for(int i=0;i<main&&!rest.isEmpty();i++)if(slots.get(i).isEmpty()){int amount=Math.min(rest.getCount(),rest.getMaxStackSize());slots.set(i,rest.copyWithCount(amount));rest.shrink(amount);}
        if(!rest.isEmpty())return false;
        for(int i=0;i<main;i++)p.getInventory().items.set(i,slots.get(i));for(int i=0;i<p.getInventory().offhand.size();i++)p.getInventory().offhand.set(i,slots.get(main+i));
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();if(p.containerMenu!=p.inventoryMenu)p.containerMenu.broadcastChanges();return true;
    }
    static String failure(ServerPlayer p,List<WeaponPlatform.Cost> costs){
        var plan=plan(p,costs);if(p.isCreative()||plan.payable())return "Inventory full. Make room for the output. Nothing consumed.";
        for(int i=0;i<costs.size();i++)if(plan.allocated()[i]<costs.get(i).count()){var choices=costs.get(i).ingredient().getItems();String name=choices.length==0?"unavailable ingredient":choices[0].getHoverName().getString();return "Need "+(costs.get(i).count()-plan.allocated()[i])+" more "+name+". Nothing consumed.";}
        return "Missing required materials. Nothing consumed.";
    }
}
