package dev.createarsenal.beacon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.*;
import java.lang.reflect.*;
import java.util.Optional;

/** Native TaCZ hooks read the same server-owned meal as the HUD and vanilla attributes. */
public final class MealGunCompat {
    private MealGunCompat(){}
    private static Method attacker,amount,setAmount,operator,holder,index,gunId;
    private static Field reloadTimestamp;
    @SuppressWarnings({"unchecked","rawtypes"})
    static void register(){
        if(!net.minecraftforge.fml.ModList.get().isLoaded("tacz"))return;
        try{
            var event=Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            attacker=event.getMethod("getAttacker");amount=event.getMethod("getBaseAmount");setAmount=event.getMethod("setBaseAmount",float.class);
            var api=Class.forName("com.tacz.guns.api.entity.IGunOperator");operator=api.getMethod("fromLivingEntity",LivingEntity.class);holder=api.getMethod("getDataHolder");
            reloadTimestamp=Class.forName("com.tacz.guns.entity.shooter.ShooterDataHolder").getField("reloadTimestamp");
            index=Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex",ResourceLocation.class);
            gunId=Class.forName("com.tacz.guns.entity.EntityKineticBullet").getMethod("getGunId");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,false,(Class)event,(Event e)->shot(e));
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Unsupported TaCZ meal API",ex);}
    }
    static void shot(Event event){
        try{if(attacker.invoke(event) instanceof ServerPlayer p){float base=((Number)amount.invoke(event)).floatValue();setAmount.invoke(event,scale(p,"firepower",base));}}
        catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot apply meal firearm damage",ex);}
    }
    public static double bonus(LivingEntity shooter,String effect){
        if(!(shooter instanceof ServerPlayer p))return 0;
        var s=PlayerMeals.get(p.serverLevel()).players.get(p.getUUID());if(s==null||s.remaining<=0)return 0;
        for(var b:s.meal.bonuses())if(b.effect().id.equals(effect))return s.home?b.home():b.field();return 0;
    }
    public static float scale(LivingEntity shooter,String effect,float value){return (float)(value*(1+bonus(shooter,effect)));}
    /** This is the same timestamp adjustment exposed by TaCZ's script API adjustReloadTime. */
    public static final class ReloadClock {
        private long last;private double fraction;
        public void advance(LivingEntity shooter){advance(shooter,System.currentTimeMillis());}
        void advance(LivingEntity shooter,long now){
            if(!(shooter instanceof ServerPlayer)||holder==null)return;
            try{
                var data=holder.invoke(operator.invoke(null,shooter));long stamp=reloadTimestamp.getLong(data);
                if(stamp<0){last=0;fraction=0;return;}
                long elapsed=Math.max(0,now-(last==0||stamp>=last?stamp:last));last=now;
                double extra=elapsed*bonus(shooter,"quick_hands")+fraction;long whole=(long)extra;fraction=extra-whole;
                reloadTimestamp.setLong(data,stamp-whole);
            }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot advance native TaCZ reload clock",ex);}
        }
    }
    public static float radius(Entity owner,Entity exploder,float radius){
        if(!(owner instanceof ServerPlayer p)||gunId==null||!gunId.getDeclaringClass().isInstance(exploder))return radius;
        try{
            var nativeIndex=((Optional<?>)index.invoke(null,gunId.invoke(exploder))).orElse(null);
            if(nativeIndex==null||!"rpg".equals(nativeIndex.getClass().getMethod("getType").invoke(nativeIndex)))return radius;
            return scale(p,"demolition",radius);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot resolve native TaCZ explosive launcher",ex);}
    }
}
