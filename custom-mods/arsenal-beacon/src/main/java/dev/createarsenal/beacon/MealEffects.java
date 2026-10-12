package dev.createarsenal.beacon;

import net.minecraft.world.effect.*;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * The vanilla potion effects that show a prepared meal in the normal effect display (inventory list and top-right icons).
 * They carry no gameplay of their own: the server-owned meal state in {@link PlayerMeals} applies the bonuses and keeps these
 * effects in step. The amplifier encodes strength and the stew pair bonus ({@link MealData.Bonus#amplifier()}).
 * While the player is at home the meal effects last forever (the field timer is frozen) and a Home Zone effect is shown as well; in the zone
 * of an ACTIVE Forward Operating Base (0.0.23) the same holds, shown as the FOB Zone effect instead.
 */
final class MealEffects {
    private MealEffects(){}
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,ArsenalBeacon.ID);
    private static final Map<MealRules.Effect,RegistryObject<MobEffect>> MEAL=new EnumMap<>(MealRules.Effect.class);
    static final RegistryObject<MobEffect> HOME,FOB;
    private static final int[] COLORS={0xe0484f,0x6aa0d8,0xb08a5a,0xd9a45a,0xff9a3d,0xf3d34a,0xd9603f,0x9aa4ad,0x8c4a2f,0xc9ced4,0x5fe0ea,0x7fd65a,0x8ef08e,0xff7a2a,0xa9c4dc,0x4aa8ff,0x82c9ef,0xf0daa0,0x7ce0cf,0xefad72,0xdb82d9,0x8db1ff,0xe4a477,0xffbc4e,0xb7bbff,0x9de4b5};
    static {
        for(var e:MealRules.Effect.values())MEAL.put(e,EFFECTS.register("meal_"+e.id,()->new Meal(e,COLORS[e.ordinal()])));
        HOME=EFFECTS.register("home_zone",()->new Home());
        FOB=EFFECTS.register("fob_zone",()->new FobZoneEffect());
    }
    /** The colour of an effect as opaque ARGB (accent of its icon, cards and the screen). */
    static int color(MealRules.Effect e){return 0xff000000|COLORS[e.ordinal()];}
    static net.minecraft.resources.ResourceLocation icon(MealRules.Effect e){return new net.minecraft.resources.ResourceLocation(ArsenalBeacon.ID,"textures/mob_effect/meal_"+e.id+".png");}
    static void register(IEventBus bus){EFFECTS.register(bus);}
    static MobEffect of(MealRules.Effect e){return MEAL.get(e).get();}
    static MealRules.Effect effectOf(MobEffect effect){for(var e:MealRules.Effect.values())if(MEAL.get(e).get()==effect)return e;return null;}
    static boolean isMeal(MobEffect effect){return effectOf(effect)!=null;}

    /** Display only: nothing ticks, nothing is applied by vanilla. */
    private static class Shown extends MobEffect {
        Shown(int color){super(MobEffectCategory.BENEFICIAL,color);}
        @Override public boolean isDurationEffectTick(int duration,int amplifier){return false;}
    }
    static final class Meal extends Shown {
        final MealRules.Effect effect;
        Meal(MealRules.Effect effect,int color){super(color);this.effect=effect;}
        @Override public void initializeClient(Consumer<IClientMobEffectExtensions> consumer){consumer.accept(new MealEffectClient(()->effect,false));}
    }
    static final class Home extends Shown {
        Home(){super(0xe8c17b);}
        @Override public void initializeClient(Consumer<IClientMobEffectExtensions> consumer){consumer.accept(new MealEffectClient(()->null,true));}
    }
    /** The FOB Zone: the Home Zone's bonus in an ACTIVE FOB's village, with its own name, text and icon. */
    static final class FobZoneEffect extends Shown {
        FobZoneEffect(){super(0x9cc26a);}
        @Override public void initializeClient(Consumer<IClientMobEffectExtensions> consumer){consumer.accept(new MealEffectClient(()->null,true));}
    }
    /** True while the player has the Home Zone or the FOB Zone effect (meal bonuses enhanced). */
    static boolean enhanced(net.minecraft.world.entity.player.Player p){return p.hasEffect(HOME.get())||p.hasEffect(FOB.get());}
}
