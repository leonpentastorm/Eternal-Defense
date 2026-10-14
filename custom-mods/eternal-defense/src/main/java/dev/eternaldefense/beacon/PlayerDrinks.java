package dev.eternaldefense.beacon;

import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** The active drink, a slot of its own beside the meal. */
final class PlayerDrinks extends SavedData {
    static final String NAME="eternal_defense_drinks_v1";
    /** How far (ticks) an effect's time may drift from the drink's and still be the drink's own. */
    static final int TOLERANCE=40;
    static final class State {
        final DrinkData drink;int remaining;boolean home;
        /** Vanilla effects hidden under the drink's own, restored when it ends. */
        final List<Covered> covered=new ArrayList<>();
        State(DrinkData drink,int remaining){this.drink=drink;this.remaining=remaining;}
        void cover(net.minecraft.resources.ResourceLocation effect,Layer l){
            if(l.duration()==0)return;
            for(var c:covered)if(c.effect.equals(effect)&&c.amplifier==l.amplifier()){if(l.infinite()||c.duration!=MobEffectInstance.INFINITE_DURATION&&l.duration()>c.duration)c.duration=l.duration();return;}
            covered.add(new Covered(effect,l.amplifier(),l.duration(),l.ambient(),l.particles(),l.icon()));
        }
    }
    static final class Covered {
        final net.minecraft.resources.ResourceLocation effect;final int amplifier;int duration;final boolean ambient,particles,icon;
        Covered(net.minecraft.resources.ResourceLocation effect,int amplifier,int duration,boolean ambient,boolean particles,boolean icon){this.effect=effect;this.amplifier=amplifier;this.duration=duration;this.ambient=ambient;this.particles=particles;this.icon=icon;}
        CompoundTag save(){var t=new CompoundTag();t.putString("Effect",effect.toString());t.putInt("Amplifier",amplifier);t.putInt("Duration",duration);t.putBoolean("Ambient",ambient);t.putBoolean("Particles",particles);t.putBoolean("Icon",icon);return t;}
        static Covered load(CompoundTag t){var id=net.minecraft.resources.ResourceLocation.tryParse(t.getString("Effect"));return id==null?null:new Covered(id,t.getInt("Amplifier"),t.getInt("Duration"),t.getBoolean("Ambient"),t.getBoolean("Particles"),t.getBoolean("Icon"));}
    }
    final Map<UUID,State> players=new HashMap<>();
    static PlayerDrinks get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(PlayerDrinks::load,PlayerDrinks::new,NAME);}
    static PlayerDrinks load(CompoundTag n){
        var d=new PlayerDrinks();
        for(var t:n.getList("Players",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;var drink=DrinkData.load(c.getCompound("Drink"));int ticks=c.getInt("Remaining");if(c.hasUUID("Player")&&drink!=null&&ticks>0){var s=new State(drink,Math.min(ticks,drink.duration()));s.home=c.getBoolean("Home");for(var k:c.getList("Covered",Tag.TAG_COMPOUND)){var cv=Covered.load((CompoundTag)k);if(cv!=null)s.covered.add(cv);}d.players.put(c.getUUID("Player"),s);}}
        return d;
    }
    @Override public CompoundTag save(CompoundTag n){var list=new ListTag();players.forEach((id,s)->{var c=new CompoundTag();c.putUUID("Player",id);c.put("Drink",s.drink.save());c.putInt("Remaining",s.remaining);c.putBoolean("Home",s.home);var cov=new ListTag();s.covered.forEach(k->cov.add(k.save()));c.put("Covered",cov);list.add(c);});n.put("Players",list);return n;}
    static State of(ServerPlayer p){return get(p.serverLevel()).players.get(p.getUUID());}

    /** Pure: is an effect of this level and time the drink's own? */
    static boolean ours(int amplifier,boolean infinite,int duration,int wantedAmplifier,boolean home,int remaining){
        if(amplifier!=wantedAmplifier)return false;
        return infinite||EndlessEffects.steadyTime(duration)||Math.abs(duration-remaining)<=TOLERANCE;
    }
    /** Pure: may the drink put its effect on now? */
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
        for(var part:s.drink.parts()){var e=part.mobEffect();if(e==null)continue;var current=p.getEffect(e);strip(p,e,part.amplifier(),current!=null&&ours(current,part,s),s);}
    }
    static void tick(ServerPlayer p){
        var d=get(p.serverLevel());var s=d.players.get(p.getUUID());if(s==null)return;
        boolean home=MealZone.at(p).home();
        if(home!=s.home){s.home=home;d.setDirty();sync(p,s);}
        if(!home){s.remaining--;if(p.tickCount%20==0)d.setDirty();}
        // What the drink covers ticks down as it would have on the player
        s.covered.removeIf(c->c.duration!=MobEffectInstance.INFINITE_DURATION&&--c.duration<=0);
        if(s.remaining<=0){
            // The timed effects run out with the drink; only an endless copy
            d.players.remove(p.getUUID());d.setDirty();
            for(var part:s.drink.parts()){var e=part.mobEffect();if(e!=null)strip(p,e,part.amplifier(),false,s);}
            return;
        }
        if(p.tickCount%10==0)sync(p,s);
    }
    /** Puts the drink's effects on where the rules allow and turns its own endless or timed to match the zone. */
    static void sync(ServerPlayer p,State s){
        for(var part:s.drink.parts()){
            var e=part.mobEffect();if(e==null)continue;var current=p.getEffect(e);
            if(current!=null&&ours(current,part,s)){
                if(s.home&&!EndlessEffects.settled(p,current)){
                    // Endless at home (or, when another mod keeps taking endless effects away, the long timer)
                    if(current.isInfiniteDuration())strip(p,e,part.amplifier(),true,s);
                    apply(p,e,part.amplifier(),EndlessEffects.home(p,e));
                }
                else if(!s.home&&(current.isInfiniteDuration()||EndlessEffects.steadyTime(current.getDuration()))){strip(p,e,part.amplifier(),true,s);apply(p,e,part.amplifier(),s.remaining);}
                continue;
            }
            // In the field an endless copy of the drink's own may wait underneath a stronger potion
            if(current!=null&&!s.home)strip(p,e,part.amplifier(),false,null);
            current=p.getEffect(e);
            if(mayApply(current!=null,current==null?0:current.getAmplifier(),part.amplifier())){
                // A weaker potion goes underneath: remember it, in case vanilla's stacking drops it under the drink's longer
                if(current!=null)for(var l:layers(current))s.cover(part.effect(),l);
                apply(p,e,part.amplifier(),s.home?EndlessEffects.home(p,e):s.remaining);
            }
        }
    }
    /** True while the tracker itself adds an effect (its own, or a layer it gives back) */
    private static boolean applying;
    static void apply(ServerPlayer p,MobEffect effect,int amplifier,int duration){
        var shown=new MobEffectInstance(effect,duration,amplifier,false,true,true);shown.setCurativeItems(new ArrayList<>());
        applying=true;try{p.addEffect(shown);}finally{applying=false;}
    }
    /** One layer of a vanilla effect: the one shown, or one of those stacked underneath it (its "hidden" effects). */
    record Layer(int duration,int amplifier,boolean ambient,boolean particles,boolean icon){boolean infinite(){return duration==MobEffectInstance.INFINITE_DURATION;}}
    /** Pure: which layers stay when the drink's own are taken off */
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
    /** Takes the drink's own layers off one effect and keeps every other layer with its own level and remaining time */
    static void strip(ServerPlayer p,MobEffect effect,int amplifier,boolean topIsOurs,State s){
        var current=p.getEffect(effect);var id=DrinkData.id(effect);
        boolean owed=s!=null&&s.covered.stream().anyMatch(c->c.effect.equals(id));
        if(current==null&&!owed)return;
        var layers=current==null?List.<Layer>of():layers(current);var kept=kept(layers,amplifier,topIsOurs);
        if(kept.size()==layers.size()&&!owed)return;
        applying=true;EndlessEffects.own++;
        try{
            if(current!=null&&kept.size()!=layers.size()){p.removeEffect(effect);for(var l:kept)if(l.duration()!=0)p.addEffect(new MobEffectInstance(effect,l.duration(),l.amplifier(),l.ambient(),l.particles(),l.icon()));}
            // And the potions it covered come back with their own level and remaining time
            if(s!=null)for(var c:s.covered)if(c.effect.equals(id)&&c.duration!=0&&!(c.amplifier==amplifier&&c.duration==MobEffectInstance.INFINITE_DURATION))p.addEffect(new MobEffectInstance(effect,c.duration,c.amplifier,c.ambient,c.particles,c.icon));
        }finally{applying=false;EndlessEffects.own--;}
    }
    /** A potion drunk over the drink's own effect: vanilla drops a weaker or equal one that the drink's longer */
    static void added(ServerPlayer p,MobEffectInstance old,MobEffectInstance added){
        if(applying||old==null||added==null)return;var s=of(p);if(s==null)return;
        for(var part:s.drink.parts())if(part.mobEffect()==added.getEffect()&&ours(old,part,s)&&added.getAmplifier()<=old.getAmplifier()){
            s.cover(part.effect(),new Layer(added.getDuration(),added.getAmplifier(),added.isAmbient(),added.isVisible(),added.showIcon()));get(p.serverLevel()).setDirty();
        }
    }
    static final class DrinkEvents {
        @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.isAlive())PlayerDrinks.tick(p);}
        @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){var s=of(p);if(s!=null){s.home=MealZone.at(p).home();sync(p,s);}}}
        @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)get(p.serverLevel()).setDirty();}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p&&!e.isCanceled())forget(p);}
        @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p){var s=of(p);if(s!=null)sync(p,s);}}
        @SubscribeEvent public void effectAdded(net.minecraftforge.event.entity.living.MobEffectEvent.Added e){if(e.getEntity() instanceof ServerPlayer p)added(p,e.getOldEffectInstance(),e.getEffectInstance());}
    }
}
