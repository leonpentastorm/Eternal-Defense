package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/**
 * 0.0.40: the line the Logistics Center Hub sends when it pays part of a request ("Dev just pulled 54 Iron Ingot from the Hub", "Dev just
 * spent 12 Ardent Energy from the Hub"). Only the part the Hub paid is named; a request the pack covered sends nothing; hoppers and Create
 * never send anything (they do not go through {@link Supply}). One action is one line naming at most three kinds of item, then "and N
 * more"; the same player's pulls within 2 seconds add up to one running total, sent when they stop. It goes to the player and to everyone in
 * the Home Zone, and to the server log.
 */
final class HubNotices {
    private HubNotices(){}
    private static final class Running {
        final UUID player;final String name;List<SupplyRules.Pulled> items=new ArrayList<>();final Map<String,ItemStack> examples=new HashMap<>();int ardent;long last;
        Running(UUID player,String name){this.player=player;this.name=name;}
    }
    private static final Map<UUID,Running> RUNNING=new LinkedHashMap<>();

    /** True when no line is waiting to go out (every running total has been sent). */
    static boolean idle(){return RUNNING.isEmpty();}
    /** A request the Hubs paid part of: added to this player's running total (nothing for an empty list). */
    static void pulled(ServerPlayer p,List<Supply.Pull> pulls){
        if(pulls.isEmpty())return;
        long now=p.server.getTickCount();var r=RUNNING.get(p.getUUID());
        if(r!=null&&!SupplyRules.sameRun(r.last,now)){flush(p.server,r);r=null;}
        if(r==null){r=new Running(p.getUUID(),p.getGameProfile().getName());RUNNING.put(p.getUUID(),r);}
        var more=new ArrayList<SupplyRules.Pulled>();
        for(var pull:pulls){
            if(pull.count()<=0)continue;
            if(pull.key().item==EternalDefense.ARDENT_ENERGY.get()){r.ardent+=pull.count();continue;}
            var example=pull.key().stack(1);String key=BuiltInRegistries.ITEM.getKey(example.getItem())+(example.hasTag()?example.getTag().toString():"");
            r.examples.putIfAbsent(key,example);more.add(new SupplyRules.Pulled(key,pull.count()));
        }
        r.items=SupplyRules.merge(r.items,more);r.last=now;
    }

    /** The line for a running total. */
    static Component line(String name,List<SupplyRules.Pulled> items,Map<String,ItemStack> examples,int ardent){
        var line=SupplyRules.line(items);MutableComponent list=null;
        for(var named:line.named()){
            var example=examples.get(named.key());var item=Component.translatable("gui.eternal_defense.hub.line.item",named.count(),example==null?Component.literal(named.key()):example.getHoverName());
            list=list==null?item:Component.translatable("gui.eternal_defense.hub.line.sep",list,item);
        }
        if(list!=null&&line.more()>0)list=Component.translatable("gui.eternal_defense.hub.line.more",list,line.more());
        Component who=Component.literal(name);
        if(list!=null&&ardent>0)return Component.translatable("gui.eternal_defense.hub.line.both",who,list,ardent).withStyle(ChatFormatting.AQUA);
        if(list!=null)return Component.translatable("gui.eternal_defense.hub.line.pulled",who,list).withStyle(ChatFormatting.AQUA);
        return Component.translatable("gui.eternal_defense.hub.line.spent",who,ardent).withStyle(ChatFormatting.AQUA);
    }
    private static void flush(MinecraftServer server,Running r){
        if(r.items.isEmpty()&&r.ardent<=0)return;
        var line=line(r.name,r.items,r.examples,r.ardent);
        var to=new LinkedHashSet<ServerPlayer>();var actor=server.getPlayerList().getPlayer(r.player);if(actor!=null)to.add(actor);
        var d=CampaignData.get(server.overworld());
        if(d.installed())for(var p:server.overworld().players())if(d.inside(p.blockPosition()))to.add(p);
        for(var p:to)p.sendSystemMessage(line);
        com.mojang.logging.LogUtils.getLogger().info("[Logistics Hub] {}",line.getString());
    }

    public static final class NoticeEvents {
        @SubscribeEvent public void hubLines(TickEvent.ServerTickEvent e){
            if(e.phase!=TickEvent.Phase.END||RUNNING.isEmpty())return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
            long now=server.getTickCount();
            for(var it=RUNNING.values().iterator();it.hasNext();){var r=it.next();if(SupplyRules.due(r.last,now)){flush(server,r);it.remove();}}
        }
        @SubscribeEvent public void hubLinesStopped(ServerStoppedEvent e){RUNNING.clear();}
    }
}
