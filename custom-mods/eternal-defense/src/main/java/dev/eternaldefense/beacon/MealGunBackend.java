package dev.eternaldefense.beacon;

import com.github.leopoko.tacz_attributes.attribute.CustomAttributes;
import com.github.leopoko.tacz_attributes.util.GunTypeResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;

/** The required TaCZ Attributes 1.4 API. */
public final class MealGunBackend {
    private MealGunBackend(){}
    static Attribute damage(){return CustomAttributes.GUN_DAMAGE.get();}
    static Attribute reload(){return CustomAttributes.RELOAD_SPEED.get();}
    public static double reloadRate(LivingEntity shooter){
        double rate=shooter.getAttributes().hasAttribute(reload())?shooter.getAttributeValue(reload()):1;
        var type=GunTypeResolver.resolveFromItem(shooter.getMainHandItem());
        if(type!=null){var attribute=type.getReloadSpeedAttribute().get();if(shooter.getAttributes().hasAttribute(attribute))rate*=shooter.getAttributeValue(attribute);}
        return Double.isFinite(rate)&&rate>0?rate:1;
    }
    // Backend 1.4 adds this private field during transformation.
    private static final class TimestampField {
        static final java.lang.reflect.Field VALUE=find();
        private static java.lang.reflect.Field find(){
            try{
                var field=com.tacz.guns.entity.shooter.LivingEntityReload.class.getDeclaredField("tacz_attributes$originalTimestamp");
                if(field.getType()!=long.class){Compat.warn("TaCZ Attributes reload bridge",null);return null;}
                field.setAccessible(true);return field;
            }catch(ReflectiveOperationException|RuntimeException ex){Compat.warn("TaCZ Attributes reload bridge",ex);return null;}
        }
    }
    static boolean verifyReloadBridge(){return TimestampField.VALUE!=null;}
    /** The backend's own reload start, or -1 when it has none (TaCZ's timing is used) */
    public static long backendTimestamp(Object reload){
        var field=TimestampField.VALUE;if(field==null)return -1;
        try{return field.getLong(reload);}catch(IllegalAccessException|RuntimeException ex){Compat.warn("TaCZ Attributes reload bridge",ex);return -1;}
    }
    public static void backendTimestamp(Object reload,long timestamp){
        var field=TimestampField.VALUE;if(field==null)return;
        try{field.setLong(reload,timestamp);}catch(IllegalAccessException|RuntimeException ex){Compat.warn("TaCZ Attributes reload bridge",ex);}
    }
    /** Rebase only when rate changes. */
    public static final class ReloadTransition {
        private ItemStack weapon;private double previousRate;private boolean active;
        public void reset(){weapon=null;previousRate=1;active=false;}
        public void begin(ItemStack gun,double rate){weapon=gun;previousRate=rate;active=true;}
        public long rebase(ItemStack gun,long original,double rate,long now){
            if(!active||weapon!=gun){begin(gun,rate);return original;}
            long changed=original;
            if(Double.compare(rate,previousRate)!=0){
                double progress=(now-original)*previousRate;
                changed=now-Math.round(progress/rate);
            }
            previousRate=rate;return changed;
        }
    }
}
