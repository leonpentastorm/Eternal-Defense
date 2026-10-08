package dev.createarsenal.beacon;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import java.lang.reflect.*;

/** Shared bash and projectile adapters. Firepower and reload speed belong exclusively to TaCZ Attributes. */
public final class MealGunCompat {
    private MealGunCompat(){}
    private static Field explosive;
    static void register(){
        MealGunBackend.verifyReloadBridge();
        try{
            explosive=EntityKineticBullet.class.getDeclaredField("explosion");explosive.setAccessible(true);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Unsupported pinned meal projectile API",ex);}
        com.mojang.logging.LogUtils.getLogger().info("Meal backend: TaCZ Attributes 1.4 global damage/reload; native bash and explosive kinetic projectiles; LesRaisins adapter {} (installed: {})",MealOptionalCompat.lrtacticalSupported()?"active":"inactive",MealOptionalCompat.lrtacticalVersion());
    }
    public static double bonus(LivingEntity shooter,String effect){
        if(!(shooter instanceof ServerPlayer p))return 0;
        var e=MealRules.Effect.of(effect);return e==null?0:PlayerMeals.bonus(p,e);
    }
    public static float scale(LivingEntity shooter,String effect,float value){return (float)(value*(1+bonus(shooter,effect)));}
    private static float demolition(Entity owner,float value){
        if(!(owner instanceof ServerPlayer p)||!Float.isFinite(value)||value<=0)return value;
        float result=scale(p,"demolition",value);return Float.isFinite(result)?result:value;
    }
    public static float radius(Entity owner,Entity exploder,float radius){
        if(!(exploder instanceof EntityKineticBullet bullet)||owner!=bullet.getOwner()||explosive==null)return radius;
        try{return explosive.getBoolean(bullet)?demolition(bullet.getOwner(),radius):radius;}
        catch(IllegalAccessException ex){throw new IllegalStateException("Cannot read native TaCZ explosive behavior",ex);}
    }
    /** Called only by the optional onDeath getRadius call site; the stored grenade radius is never changed. */
    public static float grenadeRadius(Projectile shot,float radius){return shot.level().isClientSide?radius:demolition(shot.getOwner(),radius);}
}
