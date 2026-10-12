package dev.createarsenal.beacon;

import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/**
 * 0.0.35 (owner): the active drink, a slot of its own beside the meal. One drink at a time (a new one replaces the old); its field timer
 * runs only outside the Home Zone and ACTIVE FOB zones ({@link MealZone}, the meal's test), where it stops and the effects show as endless.
 * There is no doubling at home. Logging out pauses it, death clears it, the Milk Dispenser and the Milk Bottle clear it; the vanilla milk
 * bucket does not (the tracker puts the effects back).
 * <p>
 * The effects are real vanilla effects, and the tracker never removes or extends an effect it did not apply. It recognises its own by the
 * level and the time it expects (endless at home, its remaining field time outside). An equal or stronger effect of the same kind that is
 * not its own is left alone while the drink keeps counting down; the drink re-applies when that one is gone. A weaker one is covered the way
 * vanilla covers it (kept underneath and back when the drink's ends).
 */
final class PlayerDrinks extends SavedData {
    static final String NAME="arsenal_beacon_drinks_v1";
    /** How far (ticks) an effect's time may drift from the drink's and still be the drink's own. */
    static final int TOLERANCE=40;
    static final class State {
        final DrinkData drink;int remaining;boolean home;
        State(DrinkData drink,int remaining){this.drink=drink;this.remaining=remaining;}
    }
    final Map<UUID,State> players=new HashMap<>();
    static PlayerDrinks get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(PlayerDrinks::load,PlayerDrinks::new,NAME);}
    static PlayerDrinks load(CompoundTag n){
        var d=new PlayerDrinks();
        for(var t:n.getList("Players",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;var drink=DrinkData.load(c.getCompound("Drink"));int ticks=c.getInt("Remaining");if(c.hasUUID("Player")&&drink!=null&&ticks>0){var s=new State(drink,Math.min(ticks,drink.duration()));s.home=c.getBoolean("Home");d.players.put(c.getUUID("Player"),s);}}
        return d;
    }
    @Override public CompoundTag save(CompoundTag n){var list=new ListTag();players.forEach((id,s)->{var c=new CompoundTag();c.putUUID("Player",id);c.put("Drink",s.drink.save());c.putInt("Remaining",s.remaining);c.putBoolean("Home",s.home);list.add(c);});n.put("Players",list);return n;}
    static State of(ServerPlayer p){return get(p.serverLevel()).players.get(p.getUUID());}

    /** Pure: is an effect of this level and time the drink's own? Endless while the drink is at home, else within {@link #TOLERANCE} of its time. */
    static boolean ours(int amplifier,boolean infinite,int duration,int wantedAmplifier,boolean home,int remaining){
        if(amplifier!=wantedAmplifier)return false;
        return infinite||!home&&Math.abs(duration-remaining)<=TOLERANCE;
    }
    /** Pure: may the drink put its effect on now? Only over nothing or over a weaker effect; an equal or stronger one is left to run out. */
    static boolean mayApply(boolean present,int amplifier,int wantedAmplifier){return !present||amplifier<wantedAmplifier;}
    private static boolean ours(MobEffectInstance current,DrinkData.Part part,State s){return ours(current.getAmplifier(),current.isInfiniteDuration(),current.getDuration(),part.amplifier(),s.home,s.remaining);}

    /** Drinks: replaces the active drink (taking off what the old one put on) and puts this one's effects on. */
    static void drink(ServerPlayer p,DrinkData drink){
        var d=get(p.serverLevel());var old=d.players.get(p.getUUID());if(old!=null)takeOff(p,old);
        var s=new State(drink,drink.duration());s.home=MealZone.at(p).home();d.players.put(p.getUUID(),s);d.setDirty();sync(p,s);
    }
    /** Ends the active drink and takes off its own effects (never anything else). */
    static void clear(ServerPlayer p){var d=get(p.serverLevel());var s=d.players.remove(p.getUUID());if(s!=null){takeOff(p,s);d.setDirty();}}
    /** Forgets the drink without touching effects: milk has just removed every effect, death took them. */
    static void forget(ServerPlayer p){var d=get(p.serverLevel());if(d.players.remove(p.getUUID())!=null)d.setDirty();}
    static void takeOff(ServerPlayer p,State s){
        for(var part:s.drink.parts()){var e=part.mobEffect();if(e==null)continue;var current=p.getEffect(e);if(current!=null)strip(p,e,part.amplifier(),ours(current,part,s));}
    }
    static void tick(ServerPlayer p){
        var d=get(p.serverLevel());var s=d.players.get(p.getUUID());if(s==null)return;
        boolean home=MealZone.at(p).home();
        if(home!=s.home){s.home=home;d.setDirty();sync(p,s);}
        if(!home){s.remaining--;if(p.tickCount%20==0)d.setDirty();}
        if(s.remaining<=0){
            // the timed effects run out with the drink; only an endless copy of its own (left underneath a stronger potion) is taken off
            d.players.remove(p.getUUID());d.setDirty();
            for(var part:s.drink.parts()){var e=part.mobEffect();if(e!=null&&p.getEffect(e)!=null)strip(p,e,part.amplifier(),false);}
            return;
        }
        if(p.tickCount%10==0)sync(p,s);
    }
    /** Puts the drink's effects on where the rules allow and turns its own endless or timed to match the zone. */
    static void sync(ServerPlayer p,State s){
        for(var part:s.drink.parts()){
            var e=part.mobEffect();if(e==null)continue;var current=p.getEffect(e);
            if(current!=null&&ours(current,part,s)){
                if(s.home&&!current.isInfiniteDuration())apply(p,e,part.amplifier(),MobEffectInstance.INFINITE_DURATION); // same level, longer: vanilla extends it in place
                else if(!s.home&&current.isInfiniteDuration()){strip(p,e,part.amplifier(),true);apply(p,e,part.amplifier(),s.remaining);}
                continue;
            }
            // in the field an endless copy of the drink's own may wait underneath a stronger potion: it would come back endless, so it goes
            if(current!=null&&!s.home)strip(p,e,part.amplifier(),false);
            current=p.getEffect(e);
            if(mayApply(current!=null,current==null?0:current.getAmplifier(),part.amplifier()))apply(p,e,part.amplifier(),s.home?MobEffectInstance.INFINITE_DURATION:s.remaining);
        }
    }
    static void apply(ServerPlayer p,MobEffect effect,int amplifier,int duration){
        var shown=new MobEffectInstance(effect,duration,amplifier,false,true,true);shown.setCurativeItems(new ArrayList<>());p.addEffect(shown);
    }
    /** One layer of a vanilla effect: the one shown, or one of those stacked underneath it (its "hidden" effects). */
    record Layer(int duration,int amplifier,boolean ambient,boolean particles,boolean icon){boolean infinite(){return duration==MobEffectInstance.INFINITE_DURATION;}}
    /** Pure: which layers stay when the drink's own are taken off: the top one if it is the drink's, and any endless copy at its level. */
    static List<Layer> kept(List<Layer> layers,int amplifier,boolean topIsOurs){
        var out=new ArrayList<Layer>();
        for(int i=0;i<layers.size();i++){var l=layers.get(i);if(i==0&&topIsOurs||l.amplifier()==amplifier&&l.infinite())continue;out.add(l);}
        return out;
    }
    static List<Layer> layers(MobEffectInstance current){
        var out=new ArrayList<Layer>();var tag=current.save(new CompoundTag());
        while(true){
            out.add(new Layer(tag.getInt("Duration"),tag.getByte("Amplifier"),tag.getBoolean("Ambient"),!tag.contains("ShowParticles")||tag.getBoolean("ShowParticles"),!tag.contains("ShowIcon")||tag.getBoolean("ShowIcon")));
            if(!tag.contains("HiddenEffect",Tag.TAG_COMPOUND))return out;tag=tag.getCompound("HiddenEffect");
        }
    }
    /**
     * Takes the drink's own layers off one effect and keeps every other layer with its own level and remaining time (a weaker potion that
     * vanilla stacked underneath, or a stronger one on top). Nothing changes when no layer is the drink's.
     */
    static void strip(ServerPlayer p,MobEffect effect,int amplifier,boolean topIsOurs){
        var current=p.getEffect(effect);if(current==null)return;
        var layers=layers(current);var kept=kept(layers,amplifier,topIsOurs);if(kept.size()==layers.size())return;
        p.removeEffect(effect);
        for(var l:kept)if(l.duration()!=0)p.addEffect(new MobEffectInstance(effect,l.duration(),l.amplifier(),l.ambient(),l.particles(),l.icon()));
    }
    static final class DrinkEvents {
        @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.isAlive())PlayerDrinks.tick(p);}
        @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){var s=of(p);if(s!=null){s.home=MealZone.at(p).home();sync(p,s);}}}
        @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)get(p.serverLevel()).setDirty();}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p&&!e.isCanceled())forget(p);}
        @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p){var s=of(p);if(s!=null)sync(p,s);}}
    }
}
