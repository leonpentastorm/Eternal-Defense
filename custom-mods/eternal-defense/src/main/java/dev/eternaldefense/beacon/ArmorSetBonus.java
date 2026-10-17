package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** The bonus each Age armor set gives when all four pieces are worn. */
final class ArmorSetBonus {
    private ArmorSetBonus(){}
    private static final String WARD_KEY=EternalDefense.ID+":ardent_ward";
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};

    /** Pieces of this set the player wears (0 to 4) */
    static int worn(Player p,ArmorSetMaterials set){
        if(p==null)return 0;int n=0;
        for(var slot:SLOTS)if(p.getItemBySlot(slot).getItem() instanceof ArmorSetItem a&&a.set()==set)n++;
        return n;
    }
    /** The set the entity wears in full, or null */
    static ArmorSetMaterials full(LivingEntity e){
        ArmorSetMaterials set=null;
        for(var slot:SLOTS){if(!(e.getItemBySlot(slot).getItem() instanceof ArmorSetItem a))return null;if(set==null)set=a.set();else if(set!=a.set())return null;}
        return set;
    }
    static boolean wears(LivingEntity e,ArmorSetMaterials set){return full(e)==set;}

    record Bonus(ArmorSetMaterials set,java.util.function.Supplier<Attribute> attribute,double amount,AttributeModifier.Operation op){
        UUID id(){return UUID.nameUUIDFromBytes((EternalDefense.ID+":armor_set/"+set.key()+"/"+amount+"/"+op).getBytes(StandardCharsets.UTF_8));}
    }
    static final List<Bonus> BONUSES=List.of(
        new Bonus(ArmorSetMaterials.FRONTIER,MealGunBackend::reload,ArmorSetRules.QUICK_DRAW,AttributeModifier.Operation.MULTIPLY_TOTAL),
        new Bonus(ArmorSetMaterials.WORLDWARS,MealGunBackend::damage,ArmorSetRules.GUN_DAMAGE,AttributeModifier.Operation.MULTIPLY_TOTAL),
        new Bonus(ArmorSetMaterials.MODERN,MealGunBackend::damage,ArmorSetRules.GUN_DAMAGE,AttributeModifier.Operation.MULTIPLY_TOTAL),
        new Bonus(ArmorSetMaterials.ADVANCED,()->Attributes.KNOCKBACK_RESISTANCE,1,AttributeModifier.Operation.ADDITION),
        new Bonus(ArmorSetMaterials.EXOTIC,()->Attributes.KNOCKBACK_RESISTANCE,1,AttributeModifier.Operation.ADDITION),
        new Bonus(ArmorSetMaterials.EXOTIC,()->Attributes.MAX_HEALTH,ArmorSetRules.EXOTIC_HEALTH,AttributeModifier.Operation.ADDITION));
    /** The endless effect a set keeps on its wearer, or null */
    static MobEffect effect(ArmorSetMaterials set){return set==ArmorSetMaterials.MODERN?MobEffects.NIGHT_VISION:set==ArmorSetMaterials.EXOTIC?MobEffects.REGENERATION:null;}
    /** True when the set keeps this effect off its wearer */
    static boolean immune(ArmorSetMaterials set,MobEffect e){return set==ArmorSetMaterials.WORLDWARS&&e==MobEffects.POISON||set==ArmorSetMaterials.ADVANCED&&e==MobEffects.MOVEMENT_SLOWDOWN;}
    /** Puts the worn set's bonuses on and takes every other set's off */
    static void refresh(ServerPlayer p){
        var set=full(p);boolean healthChanged=false;
        for(var b:BONUSES){
            Attribute attribute;try{attribute=b.attribute().get();}catch(RuntimeException|LinkageError ex){continue;}
            var a=p.getAttribute(attribute);if(a==null)continue;
            boolean want=b.set()==set,has=a.getModifier(b.id())!=null;
            if(want&&!has)a.addTransientModifier(new AttributeModifier(b.id(),"Age armor set",b.amount(),b.op()));
            else if(!want&&has)a.removeModifier(b.id());
            if(want!=has&&attribute==Attributes.MAX_HEALTH)healthChanged=true;
        }
        if(healthChanged)p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));
        for(var s:ArmorSetMaterials.values()){
            var e=effect(s);if(e==null)continue;var current=p.getEffect(e);
            // A potion's own effect is left alone; the set's comes back when it runs out
            if(s==set){if(current==null){var own=new MobEffectInstance(e,MobEffectInstance.INFINITE_DURATION,0,true,false,true);own.setCurativeItems(List.of());p.addEffect(own);}}
            else if(current!=null&&ours(current))p.removeEffect(e);
        }
        if(set!=null)for(var e:List.of(MobEffects.POISON,MobEffects.MOVEMENT_SLOWDOWN))if(immune(set,e)&&p.hasEffect(e))p.removeEffect(e);
    }
    /** The set's own effect: endless and ambient (potions and beacons never are both) */
    static boolean ours(MobEffectInstance i){return i.isInfiniteDuration()&&i.isAmbient();}

    /** Ardent Ward: throws nearby enemies back and gives Absorption */
    static void ward(ServerPlayer p){
        var level=p.serverLevel();var c=p.position();
        for(var mob:level.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(ArmorSetRules.WARD_RADIUS),m->m.isAlive()&&(BeaconCombat.hostile(m)||m.getTarget()==p))){
            Vec3 away=mob.position().subtract(c);away=new Vec3(away.x,0,away.z);if(away.lengthSqr()<1e-4)away=new Vec3(1,0,0);
            away=away.normalize().scale(ArmorSetRules.WARD_PUSH);mob.push(away.x,.45,away.z);mob.hurtMarked=true;
        }
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,ArmorSetRules.WARD_ABSORPTION_TICKS,ArmorSetRules.WARD_ABSORPTION_LEVEL,false,true,true));
        level.sendParticles(ParticleTypes.END_ROD,c.x,c.y+1,c.z,40,ArmorSetRules.WARD_RADIUS/3,.4,ArmorSetRules.WARD_RADIUS/3,.08);
        level.sendParticles(ParticleTypes.SONIC_BOOM,c.x,c.y+1,c.z,1,0,0,0,0);
        level.playSound(null,c.x,c.y,c.z,SoundEvents.AMETHYST_BLOCK_RESONATE,SoundSource.PLAYERS,1.2f,.7f);
        level.playSound(null,c.x,c.y,c.z,SoundEvents.TOTEM_USE,SoundSource.PLAYERS,.5f,1.6f);
        p.displayClientMessage(Component.translatable("gui.eternal_defense.armor_set.ward",ArmorSetRules.WARD_COOLDOWN/20).withStyle(ChatFormatting.LIGHT_PURPLE),true);
    }

    static final class SetEvents {
        @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.isAlive()&&p.tickCount%20==0)refresh(p);}
        @SubscribeEvent public void equip(net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent e){if(e.getEntity() instanceof ServerPlayer p&&e.getSlot().getType()==EquipmentSlot.Type.ARMOR)refresh(p);}
        @SubscribeEvent public void applicable(MobEffectEvent.Applicable e){
            if(e.getEntity() instanceof Player p){var set=full(p);if(set!=null&&immune(set,e.getEffectInstance().getEffect()))e.setResult(Event.Result.DENY);}
        }
        @SubscribeEvent public void hurt(LivingHurtEvent e){
            if(!(e.getEntity() instanceof ServerPlayer p))return;var set=full(p);if(set==null)return;
            double share=ArmorSetRules.warded(set.key(),Elites.source(e.getSource()));if(share>0)e.setAmount((float)(e.getAmount()*(1-share)));
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damaged(LivingDamageEvent e){
            if(e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p)||!wears(p,ArmorSetMaterials.EXOTIC))return;
            long now=p.serverLevel().getGameTime(),ready=p.getPersistentData().getLong(WARD_KEY);
            if(ready>now+ArmorSetRules.WARD_COOLDOWN)ready=0;
            if(!ArmorSetRules.wardFires(p.getHealth(),p.getMaxHealth(),e.getAmount(),now,ready))return;
            p.getPersistentData().putLong(WARD_KEY,now+ArmorSetRules.WARD_COOLDOWN);ward(p);
        }
    }
}
