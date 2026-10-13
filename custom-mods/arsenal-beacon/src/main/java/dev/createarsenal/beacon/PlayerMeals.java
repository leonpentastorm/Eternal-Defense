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
        /** Which zone made {@link #home} true (0.0.23: the Home Zone or an ACTIVE FOB's zone; shown as its own effect). */
        MealZone.Kind zone=MealZone.Kind.NONE;
        State(MealData meal,int remaining){this.meal=meal;this.remaining=remaining;}
    }
    final Map<UUID,State> players=new HashMap<>();
    static PlayerMeals get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(PlayerMeals::load,PlayerMeals::new,NAME);}
    static PlayerMeals load(CompoundTag n){
        var d=new PlayerMeals();for(var t:n.getList("Players",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;var meal=MealData.load(c.getCompound("Meal"));int ticks=c.getInt("Remaining");if(c.hasUUID("Player")&&meal!=null&&ticks>0)d.players.put(c.getUUID("Player"),new State(meal,Math.min(ticks,meal.duration())));}return d;
    }
    @Override public CompoundTag save(CompoundTag n){var list=new ListTag();players.forEach((id,s)->{var c=new CompoundTag();c.putUUID("Player",id);c.put("Meal",s.meal.save());c.putInt("Remaining",s.remaining);list.add(c);});n.put("Players",list);return n;}
    static Attribute attribute(MealRules.Effect e){return switch(e){case VITALITY->Attributes.MAX_HEALTH;case FORTITUDE->Attributes.ARMOR;case STEADINESS->Attributes.KNOCKBACK_RESISTANCE;
        case MIGHT->Attributes.ATTACK_DAMAGE;case AGILITY->Attributes.ATTACK_SPEED;case FORTUNE->Attributes.LUCK;case SWIM->net.minecraftforge.common.ForgeMod.SWIM_SPEED.get();
        case FIREPOWER->MealGunBackend.damage();case QUICK_HANDS->MealGunBackend.reload();
        case RECOIL_CONTROL->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.RECOIL.get();
        case FOCUS->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.ADS_ACCURACY.get();
        case SNAP_AIM->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.ADS_SPEED.get();
        case FAST_DRAW->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.DRAW_SPEED.get();
        case DEAD_EYE->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.HEADSHOT_MULTIPLIER.get();
        case GUN_MOBILITY->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.GUN_MOVEMENT_SPEED.get();
        case IMPACT->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.KNOCKBACK_BASE.get();
        case RAPID_FIRE->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.RPM_MULTIPLIER.get();
        case SCOPED_FOCUS->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.ADS_ACCURACY.get();
        case HIP_FOCUS->com.github.leopoko.tacz_attributes.attribute.CustomAttributes.HIP_FIRE_ACCURACY.get();default->null;};}
    static UUID modifier(MealRules.Effect e){return UUID.nameUUIDFromBytes((ArsenalBeacon.ID+":meal/"+e.id).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static AttributeInstance instance(ServerPlayer p,MealRules.Effect e){var attribute=attribute(e);return attribute==null?null:p.getAttribute(attribute);}
    static List<Attribute> attributes(MealRules.Effect e){
        if(e==MealRules.Effect.FOCUS)return List.of(com.github.leopoko.tacz_attributes.attribute.CustomAttributes.ADS_ACCURACY.get(),com.github.leopoko.tacz_attributes.attribute.CustomAttributes.HIP_FIRE_ACCURACY.get());
        var a=attribute(e);return a==null?List.of():List.of(a);
    }
    static void removeModifiers(ServerPlayer p){for(var e:MealRules.Effect.values())for(var attribute:attributes(e)){var a=p.getAttribute(attribute);if(a!=null)a.removeModifier(modifier(e));}p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));}
    static void applyModifiers(ServerPlayer p,State s){
        // Remove our UUIDs only; brewing and other mods keep ownership of their bonuses.
        for(var e:MealRules.Effect.values())for(var attribute:attributes(e)){var a=p.getAttribute(attribute);if(a!=null)a.removeModifier(modifier(e));}
        for(var b:s.meal.bonuses())for(var attribute:attributes(b.effect())){var a=p.getAttribute(attribute);if(a!=null)a.addTransientModifier(new AttributeModifier(modifier(b.effect()),"Prepared meal",(s.home?b.home():b.field())*(b.effect().reduction()?-1:1),operation(b.effect())));}
        p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));s.applied=true;
    }
    static AttributeModifier.Operation operation(MealRules.Effect e){
        return e.gun()&&e!=MealRules.Effect.IMPACT?AttributeModifier.Operation.MULTIPLY_TOTAL:e.multiplier?AttributeModifier.Operation.MULTIPLY_BASE:AttributeModifier.Operation.ADDITION;
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
            if(bonus==null){if(current!=null)EndlessEffects.run(()->p.removeEffect(effect));continue;}
            boolean ok=current!=null&&current.getAmplifier()==bonus.amplifier()&&(s.home?EndlessEffects.settled(p,current):!current.isInfiniteDuration()&&Math.abs(current.getDuration()-s.remaining)<=40);
            if(!ok){
                // 0.0.37: the long timer that stands in for endless (see EndlessEffects) is topped up in place, never taken off first
                boolean extend=s.home&&current!=null&&current.getAmplifier()==bonus.amplifier()&&!current.isInfiniteDuration()&&EndlessEffects.steady(p,effect);
                if(!extend)EndlessEffects.run(()->p.removeEffect(effect));
                var shown=new MobEffectInstance(effect,s.home?EndlessEffects.home(p,effect):s.remaining,bonus.amplifier(),false,true,true);shown.setCurativeItems(List.of());p.addEffect(shown);
            }
        }
        // the Home Zone effect at home, the FOB Zone effect (0.0.23) in an ACTIVE FOB's zone: same bonus, its own name and icon
        zoneEffect(p,MealEffects.HOME.get(),s!=null&&s.home&&s.zone!=MealZone.Kind.FOB);
        zoneEffect(p,MealEffects.FOB.get(),s!=null&&s.home&&s.zone==MealZone.Kind.FOB);
    }
    static void zoneEffect(ServerPlayer p,net.minecraft.world.effect.MobEffect effect,boolean wanted){
        var current=p.getEffect(effect);
        if(wanted&&(current==null||!EndlessEffects.settled(p,current))){
            if(current!=null&&current.isInfiniteDuration())EndlessEffects.run(()->p.removeEffect(effect));   // endless turned into the long timer
            var shown=new MobEffectInstance(effect,EndlessEffects.home(p,effect),0,false,true,true);shown.setCurativeItems(List.of());p.addEffect(shown);
        }
        else if(!wanted&&current!=null)EndlessEffects.run(()->p.removeEffect(effect));
    }
    static void place(State s,MealZone.Kind zone){s.zone=zone;s.home=zone.home();}
    static void eat(ServerPlayer p,MealData meal){EXHAUSTION.remove(p.getUUID());var d=get(p.serverLevel());var s=new State(meal,meal.duration());place(s,MealZone.at(p));d.players.put(p.getUUID(),s);applyModifiers(p,s);d.setDirty();syncEffects(p,s);}
    static void clear(ServerPlayer p){EXHAUSTION.remove(p.getUUID());var d=get(p.serverLevel());d.players.remove(p.getUUID());removeModifiers(p);d.setDirty();syncEffects(p,null);}
    static void refresh(ServerPlayer p){var s=get(p.serverLevel()).players.get(p.getUUID());if(s!=null){place(s,MealZone.at(p));applyModifiers(p,s);}else removeModifiers(p);syncEffects(p,s);}
    static void tick(ServerPlayer p){
        var d=get(p.serverLevel());var s=d.players.get(p.getUUID());if(s==null)return;
        var zone=MealZone.at(p);boolean home=zone.home(),changed=s.home!=home||s.zone!=zone||!s.applied;place(s,zone);
        s.remaining=MealRules.remaining(s.remaining,home);
        if(!home)d.setDirty();
        if(s.remaining==0){clear(p);return;}if(changed)applyModifiers(p,s);
        if(changed||p.tickCount%20==0){d.setDirty();syncEffects(p,s);}
        if(p.tickCount%MealRules.RECOVERY_PERIOD==0&&p.getHealth()<p.getMaxHealth()){double heal=bonus(p,MealRules.Effect.RECOVERY);if(heal>0)p.heal((float)heal);}
        hardy(p);
    }
    /** The exhaustion each player had at the end of the last tick (after Hardy's refund): what was added since is this tick's hunger. */
    private static final Map<UUID,Float> EXHAUSTION=new HashMap<>();
    /**
     * 0.0.35 (owner) Hardy: slower hunger. Each tick the exhaustion gained since the last one is compared and the effect's share of it is given
     * back. When the hunger bar ticked down in between (exhaustion past 4 is turned into lost saturation or food), only what is left can be
     * refunded that tick.
     */
    static void hardy(ServerPlayer p){
        var food=p.getFoodData();float now=food.getExhaustionLevel();Float last=EXHAUSTION.get(p.getUUID());
        double share=bonus(p,MealRules.Effect.HARDY);
        if(last!=null&&share>0){float gained=now>=last?now-last:now+4f-last;float refund=Math.min(now,(float)hardyRefund(gained,share));if(refund>0){now-=refund;food.setExhaustion(now);}}
        EXHAUSTION.put(p.getUUID(),now);
    }
    /** Pure: the share of {@code gained} exhaustion Hardy gives back. */
    static double hardyRefund(double gained,double share){return Math.max(0,gained)*Math.max(0,Math.min(MealRules.Effect.HARDY.cap(),share));}
    /** 0.0.35 (owner) Projectile Ward: arrows and crossbow bolts, tridents and fireballs; never TaCZ gun bullets. */
    static boolean wardedProjectile(net.minecraft.world.damagesource.DamageSource source){
        if(source.typeHolder().unwrapKey().map(k->k.location().getNamespace().equals("tacz")).orElse(false))return false;
        if(source.getDirectEntity()!=null&&net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(source.getDirectEntity().getType())!=null
            &&net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(source.getDirectEntity().getType()).getNamespace().equals("tacz"))return false;
        return source.is(net.minecraft.world.damagesource.DamageTypes.ARROW)||source.is(net.minecraft.world.damagesource.DamageTypes.TRIDENT)
            ||source.is(net.minecraft.world.damagesource.DamageTypes.FIREBALL)||source.is(net.minecraft.world.damagesource.DamageTypes.UNATTRIBUTED_FIREBALL);
    }
    static final class MealEvents {
        @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.isAlive()){PlayerMeals.tick(p);HubZone.tick(p);}}
        @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
        @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)get(p.serverLevel()).setDirty();}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)clear(p);}
        @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
        @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
        /** 0.0.35 (owner): Blast Ward takes a share off explosion damage (no fire); Projectile Ward off arrows, bolts, fireballs and tridents. */
        @SubscribeEvent public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){
            if(!(e.getEntity() instanceof ServerPlayer p))return;
            var source=e.getSource();
            double share=source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)?bonus(p,MealRules.Effect.BLAST_WARD):wardedProjectile(source)?bonus(p,MealRules.Effect.PROJECTILE_WARD):0;
            if(share>0)e.setAmount((float)(e.getAmount()*(1-share)));
        }
        @SubscribeEvent public void gone(PlayerEvent.PlayerLoggedOutEvent e){EXHAUSTION.remove(e.getEntity().getUUID());}
    }
}
