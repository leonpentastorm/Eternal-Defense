package dev.createarsenal.beacon;

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

/**
 * 0.0.42 (the owner's crash with the Corpse mod): Player Animator moves a player's animation layers on only when the world ticks that
 * player. A body drawn as a player that the world never ticks (the Corpse mod's corpse: a copy of the dead player with the gun still in
 * hand) keeps every fade TaCZ starts for a held gun, and TaCZ starts one each frame, each wrapped round the one before, until drawing the
 * body overflows the stack ("Rendering entity in world", {@code StackOverflowError} in {@code AbstractFadeModifier}). This moves such a
 * stand-in's layers on once per game tick, as the world does for a real player, so each fade ends and drops off after its eight ticks.
 * Nothing changes for players the world ticks, and without Player Animator this does nothing.
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class StandInAnimations {
    private StandInAnimations(){}
    /** The game tick each stand-in was last moved on in (weak: the Corpse mod drops its copy a few seconds after it is last drawn). */
    static final Map<Player,Long> MOVED=new WeakHashMap<>();
    /** Never more ticks at once than a fade lasts twice over (a stand-in out of sight for a while is caught up, not replayed). */
    static final int MOST_AT_ONCE=20;
    private static Method stack,tick;private static boolean resolved,available;

    /** Before the body is drawn (and TaCZ adds this frame's fade): the stand-in's layers, moved on by the ticks since it was last moved. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void render(RenderPlayerEvent.Pre e){
        var player=e.getEntity();var level=player.level();
        if(level==null||level.getEntity(player.getId())==player||!available())return;   // a real player: the world ticks it
        long now=level.getGameTime();Long last=MOVED.put(player,now);
        if(last==null)return;
        for(long i=0,n=ticks(last,now);i<n;i++)if(!tick(player))return;
    }
    /** How many ticks a stand-in last moved on at {@code last} is moved on at {@code now}. */
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
