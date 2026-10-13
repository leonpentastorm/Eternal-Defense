package dev.createarsenal.beacon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/**
 * 0.0.37: the effects the mod keeps on a player "endless" at home: the active drink's, the meal's and the Home / FOB Zone effect. Some
 * mods take an endless effect away (they read its endless time, -1, as run out); the trackers then put it back half a second later and the
 * player sees it come and go, over and over. After {@link #LOSSES} such losses of one effect within {@link #WINDOW} ticks, that effect is
 * shown at home with a long timer instead ({@link #STEADY}, topped up in place every {@link #REFRESH} ticks, so it never blinks), the
 * player is told once and the server log names the code that took it away.
 */
final class EndlessEffects {
    private EndlessEffects(){}
    static final int STEADY=24000,REFRESH=1200,WINDOW=600,LOSSES=2;
    /** Above zero while the mod itself takes effects off (a tracker's own change, the Milk Dispenser's cleanse): those are not losses. */
    static int own;
    /** What took an endless effect away last (also in the server log). */
    static String lastBy="";
    private static final Map<UUID,Map<MobEffect,Deque<Long>>> LOST=new HashMap<>();
    private static final Map<UUID,Set<MobEffect>> STEADY_FOR=new HashMap<>();

    /** Pure: an effect at home with this time is the long-timer stand-in for an endless one (topped up before it drops out of the window). */
    static boolean steadyTime(int duration){return duration>STEADY-REFRESH-PlayerDrinks.TOLERANCE&&duration<=STEADY;}
    /** Pure: a steady effect that is due for its top-up. */
    static boolean due(int duration){return duration<=STEADY-REFRESH;}
    /** Pure: one more loss at {@code now}; true when {@link #LOSSES} of them fall within {@link #WINDOW} ticks. */
    static boolean trips(Deque<Long> times,long now){
        times.addLast(now);while(!times.isEmpty()&&now-times.peekFirst()>WINDOW)times.removeFirst();
        return times.size()>=LOSSES;
    }
    static boolean steady(ServerPlayer p,MobEffect e){var set=STEADY_FOR.get(p.getUUID());return set!=null&&set.contains(e);}
    /** The time the effect gets at home: endless, or the long timer once something keeps taking the endless one away. */
    static int home(ServerPlayer p,MobEffect e){return steady(p,e)?STEADY:MobEffectInstance.INFINITE_DURATION;}
    /** True when the effect at home has the time the trackers want it to have (no top-up, no change needed). */
    static boolean settled(ServerPlayer p,MobEffectInstance current){
        return steady(p,current.getEffect())?!current.isInfiniteDuration()&&steadyTime(current.getDuration())&&!due(current.getDuration()):current.isInfiniteDuration();
    }
    static void run(Runnable r){own++;try{r.run();}finally{own--;}}

    /** Something else took one of the endless effects away. */
    static void lost(ServerPlayer p,MobEffect e,String by){
        var times=LOST.computeIfAbsent(p.getUUID(),k->new HashMap<>()).computeIfAbsent(e,k->new ArrayDeque<>());
        if(steady(p,e)||!trips(times,p.serverLevel().getGameTime()))return;
        var set=STEADY_FOR.computeIfAbsent(p.getUUID(),k->new HashSet<>());boolean first=set.isEmpty();set.add(e);lastBy=by;
        com.mojang.logging.LogUtils.getLogger().warn("[Eternal Defense] {}'s endless {} was taken away {} times in {} s, last by {}: at home it now has a {}-minute timer instead.",
            p.getGameProfile().getName(),e.getDisplayName().getString(),LOSSES,WINDOW/20,by,STEADY/1200);
        // one line for the player, however many effects follow (each one is in the log)
        if(first)p.sendSystemMessage(Component.translatable("gui.arsenal_beacon.endless.fallback",STEADY/1200).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    /** The first frame of the call that is not Minecraft's, Forge's, Java's or this mod's own. */
    static String culprit(){
        for(var f:Thread.currentThread().getStackTrace()){
            var c=f.getClassName();
            if(c.startsWith("java.")||c.startsWith("jdk.")||c.startsWith("sun.")||c.startsWith("net.minecraft.")||c.startsWith("net.minecraftforge.")||c.startsWith("com.mojang.")||c.startsWith("cpw.mods.")||c.startsWith("dev.createarsenal.beacon.EndlessEffects")||c.contains("__"))continue;
            return c+"."+f.getMethodName()+":"+f.getLineNumber();
        }
        return "an unknown caller";
    }
    /** Is this effect one the mod keeps endless on this player at home right now? */
    static boolean tracked(ServerPlayer p,MobEffectInstance i){
        if(i==null||!(i.isInfiniteDuration()||steadyTime(i.getDuration())))return false;
        var e=i.getEffect();
        if(e==MealEffects.HOME.get()||e==MealEffects.FOB.get()||MealEffects.isMeal(e))return true;
        var s=PlayerDrinks.of(p);if(s!=null)for(var part:s.drink.parts())if(part.mobEffect()==e)return true;
        return false;
    }
    static void forget(ServerPlayer p){LOST.remove(p.getUUID());STEADY_FOR.remove(p.getUUID());}

    static final class EndlessEvents {
        @SubscribeEvent public void endlessRemoved(MobEffectEvent.Remove e){
            if(own==0&&e.getEntity() instanceof ServerPlayer p&&!e.isCanceled()&&MealZone.at(p).home()&&tracked(p,e.getEffectInstance()))lost(p,e.getEffectInstance().getEffect(),culprit());
        }
        @SubscribeEvent public void endlessExpired(MobEffectEvent.Expired e){
            // an endless effect never runs out by itself: its time was set to 0 by someone else, who is no longer on the stack
            if(own==0&&e.getEntity() instanceof ServerPlayer p&&MealZone.at(p).home()&&e.getEffectInstance()!=null&&e.getEffectInstance().getEffect()!=null&&tracked(p,withTime(e.getEffectInstance())))lost(p,e.getEffectInstance().getEffect(),"its time set to 0 (another mod)");
        }
        @SubscribeEvent public void endlessLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)forget(p);}
    }
    /** An expired instance has time 0: judge it as the endless effect it was meant to be. */
    private static MobEffectInstance withTime(MobEffectInstance i){return new MobEffectInstance(i.getEffect(),MobEffectInstance.INFINITE_DURATION,i.getAmplifier());}
}
