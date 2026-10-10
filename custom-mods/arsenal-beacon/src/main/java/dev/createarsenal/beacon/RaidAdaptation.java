package dev.createarsenal.beacon;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import java.util.*;

/** Damage-driven trap learning. No trap/block search: only this raid's registered attackers learn. */
public final class RaidAdaptation {
    private RaidAdaptation(){}
    public static final double STEP=.25;
    private static final TagKey<EntityType<?>> WEAPONS=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation(ArsenalBeacon.ID,"adaptation_exempt_sources"));
    private static final TagKey<DamageType> EXEMPT=TagKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(ArsenalBeacon.ID,"adaptation_exempt"));
    private static final ThreadLocal<Boolean> WEAPON_CALL=ThreadLocal.withInitial(()->false);
    private static final String FIRE_UNTIL="arsenalWeaponFireUntil",POISON_UNTIL="arsenalWeaponPoisonUntil",WITHER_UNTIL="arsenalWeaponWitherUntil",FREEZE_UNTIL="arsenalWeaponFreezeUntil";

    // Specific spikes precede broad vanilla fall tags (stalagmite belongs to both).
    enum Kind {
        FIRE("fire"),SPIKES("spikes"),FALL("fall"),DROWNING("drowning"),SUFFOCATION("suffocation"),
        CRUSHING("crushing"),FREEZING("freezing"),EXPLOSION("explosion"),PROJECTILE("projectile"),MAGIC("magic"),LIGHTNING("lightning");
        final String id;final TagKey<DamageType> tag;
        Kind(String id){this.id=id;tag=TagKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(ArsenalBeacon.ID,"trap/"+id));}
        static Kind of(String id){for(var k:values())if(k.id.equals(id))return k;return null;}
    }
    static double resistance(CampaignData d,Kind kind){
        var first=d.trapFirstWave.get(kind);return first==null?0:Math.min(1,Math.max(0,d.wave-first)*STEP);
    }
    static void begin(CampaignData d){d.trapFirstWave.clear();d.setDirty();}
    static void nextWave(ServerLevel level,CampaignData d){
        for(var kind:Kind.values()){
            var first=d.trapFirstWave.get(kind);if(first==null)continue;
            int steps=d.wave-first;if(steps<=0||steps>4)continue;
            var type=net.minecraft.network.chat.Component.translatable("gui.arsenal_beacon.adaptation.type."+kind.id);
            var message=steps==4?net.minecraft.network.chat.Component.translatable("gui.arsenal_beacon.adaptation.immune",type)
                :net.minecraft.network.chat.Component.translatable("gui.arsenal_beacon.adaptation.learned",type,steps*25);
            level.getServer().getPlayerList().broadcastSystemMessage(message.withStyle(net.minecraft.ChatFormatting.RED),false);
        }
    }
    private static CampaignData raid(LivingEntity target){
        if(!(target.level() instanceof ServerLevel l)||l.dimension()!=net.minecraft.world.level.Level.OVERWORLD)return null;
        var d=CampaignData.get(l);return d.phase.equals("raid")&&d.raiders.contains(target.getUUID())?d:null;
    }
    private static boolean weapon(Entity e){
        if(e==null)return false;
        if(e instanceof LivingEntity||e.getType().is(WEAPONS)||e.getPersistentData().getBoolean("arsenalDefensiveWeapon"))return true;
        if(e instanceof Projectile p)return p.getOwner()!=null;
        if(e instanceof net.minecraft.world.entity.AreaEffectCloud cloud)return cloud.getOwner()!=null;
        if(e instanceof net.minecraft.world.entity.item.PrimedTnt tnt)return tnt.getOwner()!=null;
        return false;
    }
    private static boolean weapon(DamageSource source){return WEAPON_CALL.get()||source.is(EXEMPT)||weapon(source.getEntity())||weapon(source.getDirectEntity());}
    private static Kind kind(LivingEntity target,DamageSource source){
        if(weapon(source))return null;
        long now=target.level().getGameTime();var n=target.getPersistentData();
        // A flame arrow's later ownerless burn ticks still belong to its weapon. Contact with lava/fire is a trap again.
        if(source.is(DamageTypes.ON_FIRE)&&n.getLong(FIRE_UNTIL)>now)return null;
        if(source.is(DamageTypes.MAGIC)&&n.getLong(POISON_UNTIL)>now)return null;
        if(source.is(DamageTypes.WITHER)&&n.getLong(WITHER_UNTIL)>now)return null;
        if(source.is(DamageTypes.FREEZE)&&n.getLong(FREEZE_UNTIL)>now)return null;
        for(var k:Kind.values())if(source.is(k.tag))return k;
        return null; // Void, commands, starvation and unknown combat types are never inferred as traps.
    }
    /** Optional weapon integrations can mark their ownerless shots with arsenalDefensiveWeapon, or call this narrow boundary. */
    public static boolean weaponDamage(LivingEntity target,DamageSource source,float amount){
        boolean old=WEAPON_CALL.get();WEAPON_CALL.set(true);
        try{return target.hurt(source,amount);}finally{if(old)WEAPON_CALL.set(true);else WEAPON_CALL.remove();}
    }
    public static void weaponFire(LivingEntity target,int ticks){
        target.getPersistentData().putLong(FIRE_UNTIL,target.level().getGameTime()+Math.max(0,ticks)+20);
    }
    /** A weapon froze the target (the Cryo Shell): the frost damage that follows belongs to the weapon, not to a trap. */
    public static void weaponFreeze(LivingEntity target,int ticks){
        target.getPersistentData().putLong(FREEZE_UNTIL,target.level().getGameTime()+Math.max(0,ticks)+20);
    }
    public static final class Events {
        @SubscribeEvent(priority=EventPriority.LOWEST) public void attack(LivingAttackEvent e){
            if(raid(e.getEntity())==null||!weapon(e.getSource()))return;
            if(e.getSource().is(DamageTypeTags.IS_FIRE)
                ||e.getSource().getDirectEntity()!=null&&e.getSource().getDirectEntity().isOnFire())
                weaponFire(e.getEntity(),Math.max(100,e.getEntity().getRemainingFireTicks()));
        }
        @SubscribeEvent public void effect(MobEffectEvent.Added e){
            if(raid(e.getEntity())==null)return;
            var inst=e.getEffectInstance();String key=inst.getEffect()==MobEffects.POISON?POISON_UNTIL:inst.getEffect()==MobEffects.WITHER?WITHER_UNTIL:null;
            if(key==null)return;
            long until=weapon(e.getEffectSource())||WEAPON_CALL.get()?(inst.isInfiniteDuration()?Long.MAX_VALUE:e.getEntity().level().getGameTime()+Math.max(0,inst.getDuration())+20):0;
            e.getEntity().getPersistentData().putLong(key,until);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void hurt(LivingHurtEvent e){
            var d=raid(e.getEntity());if(d==null||e.getAmount()<=0)return;
            var kind=kind(e.getEntity(),e.getSource());if(kind!=null)e.setAmount((float)(e.getAmount()*(1-resistance(d,kind))));
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e){
            var d=raid(e.getEntity());if(d==null||e.getAmount()<=0)return;
            var kind=kind(e.getEntity(),e.getSource());
            if(kind!=null&&d.trapFirstWave.putIfAbsent(kind,d.wave)==null)d.setDirty();
        }
    }
}
