package dev.createarsenal.beacon;

import net.minecraft.nbt.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Server-owned field timer and meal modifiers. Offline time never consumes a meal; death clears it. */
final class PlayerMeals extends SavedData {
    static final String NAME="arsenal_beacon_meals_v1";
    static final class State {
        final MealData meal;int remaining;boolean home;boolean applied;
        State(MealData meal,int remaining){this.meal=meal;this.remaining=remaining;}
    }
    final Map<UUID,State> players=new HashMap<>();
    static PlayerMeals get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(PlayerMeals::load,PlayerMeals::new,NAME);}
    static PlayerMeals load(CompoundTag n){
        var d=new PlayerMeals();for(var t:n.getList("Players",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;var meal=MealData.load(c.getCompound("Meal"));int ticks=c.getInt("Remaining");if(c.hasUUID("Player")&&meal!=null&&ticks>0)d.players.put(c.getUUID("Player"),new State(meal,Math.min(ticks,meal.duration())));}return d;
    }
    @Override public CompoundTag save(CompoundTag n){var list=new ListTag();players.forEach((id,s)->{var c=new CompoundTag();c.putUUID("Player",id);c.put("Meal",s.meal.save());c.putInt("Remaining",s.remaining);list.add(c);});n.put("Players",list);return n;}
    static Attribute attribute(MealRules.Effect e){return switch(e){case VITALITY->Attributes.MAX_HEALTH;case FORTITUDE->Attributes.ARMOR;case STEADINESS->Attributes.KNOCKBACK_RESISTANCE;case MOBILITY->Attributes.MOVEMENT_SPEED;
        case MIGHT->Attributes.ATTACK_DAMAGE;case AGILITY->Attributes.ATTACK_SPEED;case FORTUNE->Attributes.LUCK;case SWIM->net.minecraftforge.common.ForgeMod.SWIM_SPEED.get();
        case FIREPOWER->MealGunBackend.damage();case QUICK_HANDS->MealGunBackend.reload();default->null;};}
    static UUID modifier(MealRules.Effect e){return UUID.nameUUIDFromBytes((ArsenalBeacon.ID+":meal/"+e.id).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static AttributeInstance instance(ServerPlayer p,MealRules.Effect e){var attribute=attribute(e);return attribute==null?null:p.getAttribute(attribute);}
    static void removeModifiers(ServerPlayer p){for(var e:MealRules.Effect.values()){var a=instance(p,e);if(a!=null)a.removeModifier(modifier(e));}p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));}
    static void applyModifiers(ServerPlayer p,State s){
        // Remove our UUIDs only; brewing and other mods keep ownership of their bonuses.
        for(var e:MealRules.Effect.values()){var a=instance(p,e);if(a!=null)a.removeModifier(modifier(e));}
        for(var b:s.meal.bonuses()){var a=instance(p,b.effect());if(a!=null)a.addTransientModifier(new AttributeModifier(modifier(b.effect()),"Prepared meal",s.home?b.home():b.field(),operation(b.effect())));}
        p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));s.applied=true;
    }
    static AttributeModifier.Operation operation(MealRules.Effect e){
        return e==MealRules.Effect.FIREPOWER||e==MealRules.Effect.QUICK_HANDS?AttributeModifier.Operation.MULTIPLY_TOTAL:e.multiplier?AttributeModifier.Operation.MULTIPLY_BASE:AttributeModifier.Operation.ADDITION;
    }
    /** The bonus of one effect for this player right now (0 without a meal); at home it is doubled. */
    static double bonus(ServerPlayer p,MealRules.Effect effect){
        var s=get(p.serverLevel()).players.get(p.getUUID());if(s==null||s.remaining<=0)return 0;
        for(var b:s.meal.bonuses())if(b.effect()==effect)return s.home?b.home():b.field();return 0;
    }
    /** Keeps the vanilla effects in step with the meal: finite and counting down in the field, endless while at home, plus the Home Zone effect. */
    static void syncEffects(ServerPlayer p,State s){
        for(var e:MealRules.Effect.values()){
            var effect=MealEffects.of(e);var current=p.getEffect(effect);MealData.Bonus bonus=null;
            if(s!=null)for(var b:s.meal.bonuses())if(b.effect()==e)bonus=b;
            if(bonus==null){if(current!=null)p.removeEffect(effect);continue;}
            boolean ok=current!=null&&current.getAmplifier()==bonus.amplifier()&&(s.home?current.isInfiniteDuration():!current.isInfiniteDuration()&&Math.abs(current.getDuration()-s.remaining)<=40);
            if(!ok){p.removeEffect(effect);var shown=new MobEffectInstance(effect,s.home?MobEffectInstance.INFINITE_DURATION:s.remaining,bonus.amplifier(),false,true,true);shown.setCurativeItems(List.of());p.addEffect(shown);}
        }
        var home=MealEffects.HOME.get();boolean wanted=s!=null&&s.home;
        if(wanted&&!p.hasEffect(home)){var shown=new MobEffectInstance(home,MobEffectInstance.INFINITE_DURATION,0,false,true,true);shown.setCurativeItems(List.of());p.addEffect(shown);}
        else if(!wanted&&p.hasEffect(home))p.removeEffect(home);
    }
    static void eat(ServerPlayer p,MealData meal){var d=get(p.serverLevel());var s=new State(meal,meal.duration());s.home=BaseZone.problem(p.level(),p.blockPosition())==null;d.players.put(p.getUUID(),s);applyModifiers(p,s);d.setDirty();syncEffects(p,s);}
    static void clear(ServerPlayer p){var d=get(p.serverLevel());d.players.remove(p.getUUID());removeModifiers(p);d.setDirty();syncEffects(p,null);}
    static void refresh(ServerPlayer p){var s=get(p.serverLevel()).players.get(p.getUUID());if(s!=null){s.home=BaseZone.problem(p.level(),p.blockPosition())==null;applyModifiers(p,s);}else removeModifiers(p);syncEffects(p,s);}
    static void tick(ServerPlayer p){
        var d=get(p.serverLevel());var s=d.players.get(p.getUUID());if(s==null)return;
        boolean home=BaseZone.problem(p.level(),p.blockPosition())==null,changed=s.home!=home||!s.applied;s.home=home;
        s.remaining=MealRules.remaining(s.remaining,home);
        if(!home)d.setDirty();
        if(s.remaining==0){clear(p);return;}if(changed)applyModifiers(p,s);
        if(changed||p.tickCount%20==0){d.setDirty();syncEffects(p,s);}
        if(p.tickCount%MealRules.RECOVERY_PERIOD==0&&p.getHealth()<p.getMaxHealth()){double heal=bonus(p,MealRules.Effect.RECOVERY);if(heal>0)p.heal((float)heal);}
    }
    static final class MealEvents {
        @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.isAlive())PlayerMeals.tick(p);}
        @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
        @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)get(p.serverLevel()).setDirty();}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)clear(p);}
        @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
        @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
        /** Hearth: less fire and explosion damage. */
        @SubscribeEvent public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){
            if(!(e.getEntity() instanceof ServerPlayer p))return;
            var source=e.getSource();if(!source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)&&!source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))return;
            double share=bonus(p,MealRules.Effect.HEARTH);if(share>0)e.setAmount((float)(e.getAmount()*(1-share)));
        }
        /** Springy Step: less fall damage. */
        @SubscribeEvent public void fall(net.minecraftforge.event.entity.living.LivingFallEvent e){
            if(!(e.getEntity() instanceof ServerPlayer p))return;
            double share=bonus(p,MealRules.Effect.SPRINGY);if(share>0)e.setDamageMultiplier((float)(e.getDamageMultiplier()*(1-share)));
        }
    }
}
