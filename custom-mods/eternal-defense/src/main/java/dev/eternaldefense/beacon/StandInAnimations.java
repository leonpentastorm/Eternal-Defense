package dev.eternaldefense.beacon;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Player Animator moves a player's animation layers on only when the world ticks that player. */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
public final class StandInAnimations {
    private StandInAnimations(){}
    /** Last game tick each stand-in was advanced (weak keys). */
    static final Map<Player,Long> MOVED=new WeakHashMap<>();
    /** Most ticks caught up at once. */
    static final int MOST_AT_ONCE=20;
    private static Method stack,tick;private static boolean resolved,available;

    /** Before the body is drawn (and TaCZ adds this frame's fade) */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void render(RenderPlayerEvent.Pre e){
        var player=e.getEntity();var level=player.level();
        if(level==null||level.getEntity(player.getId())==player||!available())return;   // A real player: the world ticks it
        long now=level.getGameTime();Long last=MOVED.put(player,now);
        if(last==null)return;
        for(long i=0,n=ticks(last,now);i<n;i++)if(!tick(player))return;
    }
    /** How many ticks a stand-in last moved on at last is moved on at now. */
    static long ticks(long last,long now){return Math.max(0,Math.min(MOST_AT_ONCE,now-last));}
    private static boolean available(){
        if(!resolved){
            resolved=true;
            if(ModList.get().isLoaded("playeranimator"))try{
                stack=Class.forName("dev.kosmx.playerAnim.api.IPlayer").getMethod("getAnimationStack");
                tick=Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation").getMethod("tick");
                available=true;
            }catch(ReflectiveOperationException ex){com.mojang.logging.LogUtils.getLogger().warn("Player Animator is loaded but its animation stack was not found; stand-in bodies are left alone",ex);}
        }
        return available;
    }
    private static boolean tick(Player player){
        try{if(!stack.getDeclaringClass().isInstance(player))return false;var layers=stack.invoke(player);if(layers!=null)tick.invoke(layers);return true;}
        catch(ReflectiveOperationException ex){available=false;com.mojang.logging.LogUtils.getLogger().warn("Could not move a stand-in body's animations on; stopped trying",ex);return false;}
    }
}
