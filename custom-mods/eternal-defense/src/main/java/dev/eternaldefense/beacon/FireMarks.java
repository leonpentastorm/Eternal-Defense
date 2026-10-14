package dev.eternaldefense.beacon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Marks two fire supports leave on a hostile mob until it dies (0.0.36, owner). Napalm Carpet sets every mob that can burn Ablaze: it
 * burns by our own timer whatever water, rain or anything else does, and every burn counts as the weapon's (never a fire trap the raid
 * learns). Spotter Arrows mark a mob: it glows and takes more from every kind of damage. Both live in the mob's persistent data.
 */
final class FireMarks {
    private FireMarks(){}
    static final String ABLAZE="eternalAblaze",MARKED="eternalMarked";
    /** Ablaze: damage a second before the Damage upgrade; a raid boss takes half. */
    static final float ABLAZE_DAMAGE=4f,BOSS_ABLAZE_SHARE=.5f;
    static final int ABLAZE_INTERVAL=20,ABLAZE_FIRE_TICKS=60;
    /** Spotter Arrows: the hit each volley deals before the Damage upgrade, and how much more a Marked mob takes (a raid boss less). */
    static final float SPOTTER_HIT=4f,MARKED_BONUS=.30f,BOSS_MARKED_BONUS=.15f;

    /** Pure: what an Ablaze mob takes each second. */
    static float ablaze(float perSecond,boolean boss){return perSecond*(boss?BOSS_ABLAZE_SHARE:1f);}
    /** Pure: the burn a napalm with the Damage upgrade's {@code multiplier} marks a mob with. */
    static float ablazeFor(float multiplier){return ABLAZE_DAMAGE*multiplier;}
    /** Pure: what a Marked mob takes from a hit of {@code amount}. */
    static float marked(float amount,boolean boss){return amount*(1+(boss?BOSS_MARKED_BONUS:MARKED_BONUS));}
    /** Pure: a mob can be set Ablaze only when fire hurts it: not fire immune (blazes, Nether mobs) and without Fire Resistance. */
    static boolean canBurn(boolean fireImmune,boolean fireResistance){return !fireImmune&&!fireResistance;}
    /** Pure: the burn a mob keeps when it is set Ablaze again: the stronger one, never the sum. */
    static float rekindle(float had,float perSecond){return Math.max(had,perSecond);}

    static boolean ablaze(LivingEntity e){return e.getPersistentData().contains(ABLAZE);}
    static boolean marked(LivingEntity e){return e.getPersistentData().getBoolean(MARKED);}
    private static boolean boss(LivingEntity e){return e.getPersistentData().getBoolean("eternalBoss");}

    /** Sets a mob in the burning napalm Ablaze for good, if fire can hurt it. */
    static void ignite(LivingEntity e,float perSecond){
        if(!canBurn(e.fireImmune(),e.hasEffect(MobEffects.FIRE_RESISTANCE)))return;
        var n=e.getPersistentData();n.putFloat(ABLAZE,rekindle(n.getFloat(ABLAZE),perSecond));
        RaidAdaptation.weaponFire(e,ABLAZE_FIRE_TICKS);
        if(e.getRemainingFireTicks()<ABLAZE_FIRE_TICKS)e.setRemainingFireTicks(ABLAZE_FIRE_TICKS);
    }
    /** Marks a mob for good: it glows (an endless effect) and takes more damage; marks do not stack. */
    static void mark(LivingEntity e,Entity source){
        e.getPersistentData().putBoolean(MARKED,true);
        var glow=e.getEffect(MobEffects.GLOWING);
        if(glow==null||!glow.isInfiniteDuration())e.addEffect(new MobEffectInstance(MobEffects.GLOWING,MobEffectInstance.INFINITE_DURATION,0,false,false),source);
    }

    private static final ThreadLocal<Boolean> SPOTTING=ThreadLocal.withInitial(()->false),BURNING=ThreadLocal.withInitial(()->false);
    /** A Spotter Arrows hit: the weapon's damage, without knockback. */
    static boolean spot(LivingEntity e,DamageSource source,float amount){
        SPOTTING.set(true);
        try{return RaidAdaptation.weaponDamage(e,source,amount);}finally{SPOTTING.remove();}
    }
    /**
     * One second of Ablaze: the weapon's fire damage, on our own timer. It is on top of everything else: it ignores the half second of
     * invulnerability after a hit and leaves it as it was, so it never cuts the carpet's damage or the next sword or arrow hit short.
     */
    private static void burn(LivingEntity e,float amount){
        int invulnerable=e.invulnerableTime;e.invulnerableTime=0;BURNING.set(true);
        try{RaidAdaptation.weaponDamage(e,e.damageSources().onFire(),amount);}finally{BURNING.remove();e.invulnerableTime=invulnerable;}
    }

    public static final class MarkEvents {
        /** Ablaze: keeps the flames on (out of water) and burns once a second. Every other living thing leaves at the first test. */
        @SubscribeEvent public void ablazeTick(LivingEvent.LivingTickEvent e){
            var mob=e.getEntity();
            if(!(mob instanceof Enemy)||!(mob.level() instanceof ServerLevel level)||!ablaze(mob)||!mob.isAlive())return;
            RaidAdaptation.weaponFire(mob,ABLAZE_FIRE_TICKS);
            if(!mob.isInWater()){
                // rain puts the flames out (with a hiss) every tick, so there the fire shows as flame particles; elsewhere it is lit again
                if(mob.isInWaterRainOrBubble()){if(level.getGameTime()%4==0)level.sendParticles(ParticleTypes.FLAME,mob.getX(),mob.getY()+mob.getBbHeight()*.5,mob.getZ(),3,mob.getBbWidth()*.3,mob.getBbHeight()*.3,mob.getBbWidth()*.3,.01);}
                else if(mob.getRemainingFireTicks()<ABLAZE_INTERVAL)mob.setRemainingFireTicks(ABLAZE_FIRE_TICKS);
            }
            if((level.getGameTime()+mob.getId())%ABLAZE_INTERVAL==0)burn(mob,ablaze(mob.getPersistentData().getFloat(ABLAZE),boss(mob)));
        }
        /** Vanilla's own burning (1 a second) is replaced by Ablaze's while a mob carries it. */
        @SubscribeEvent public void ablazeAttack(LivingAttackEvent e){
            if(!BURNING.get()&&e.getSource().is(DamageTypes.ON_FIRE)&&e.getEntity() instanceof Enemy&&ablaze(e.getEntity()))e.setCanceled(true);
        }
        /** Marked: every hit counts this much more, whatever it is (guns, explosions, fire, poison, wither); not what ignores invulnerability. */
        @SubscribeEvent public void markedHurt(LivingHurtEvent e){
            if(e.getAmount()<=0||e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
            var mob=e.getEntity();if(!(mob instanceof Enemy)||!marked(mob))return;
            e.setAmount(marked(e.getAmount(),boss(mob)));
        }
        @SubscribeEvent public void spotterKnockBack(LivingKnockBackEvent e){if(SPOTTING.get())e.setCanceled(true);}
        @SubscribeEvent public void marksDeath(LivingDeathEvent e){
            var n=e.getEntity().getPersistentData();
            if(n.contains(ABLAZE)||n.contains(MARKED)){n.remove(ABLAZE);n.remove(MARKED);}
        }
    }
}
