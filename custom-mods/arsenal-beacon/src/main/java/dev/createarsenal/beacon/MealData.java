package dev.createarsenal.beacon;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The same bounded composition is saved on sandwiches, pots and active player meals. */
record MealData(boolean stew,List<Bonus> bonuses,int duration) {
    /** {@code pair}: a stew whose two foods of one family fed this effect (value doubles). */
    record Bonus(MealRules.Effect effect,int strength,boolean pair){
        Bonus(MealRules.Effect effect,int strength){this(effect,strength,false);}
        double field(){return Math.min(effect.cap(),effect.amount*(1+.25*(strength-1))*(pair?MealRules.PAIR_MULTIPLIER:1));}
        double home(){return Math.min(effect.cap(),field()*MealRules.HOME_MULTIPLIER);}
        /** The amplifier of the vanilla effect that shows this bonus: strength 0-2 plus 4 for a pair. */
        int amplifier(){return strength-1+(pair?4:0);}
        static Bonus ofAmplifier(MealRules.Effect effect,int amplifier){return new Bonus(effect,Math.max(1,Math.min(3,(amplifier&3)+1)),(amplifier&4)!=0);}
        String amountText(boolean enhanced){
            double value=enhanced?home():field();
            return (effect.reduction()?"−":"+")+java.math.BigDecimal.valueOf(value*(effect.percent?100:1)).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+(effect.percent?"%":"");
        }
        Component description(boolean enhanced){
            var text=Component.translatable("gui.arsenal_beacon.meal.bonus."+effect.id,amountText(enhanced));
            return pair?text.append(Component.literal(" \u00d72").withStyle(net.minecraft.ChatFormatting.GOLD)):text;
        }
    }
    MealData {bonuses=List.copyOf(bonuses);}
    Component name(){return Component.translatable("gui.arsenal_beacon.meal.name."+(stew?"stew":"sandwich"),Component.translatable("gui.arsenal_beacon.meal.effect."+bonuses.get(0).effect.id));}
    CompoundTag save(){
        var n=new CompoundTag();n.putBoolean("Stew",stew);n.putInt("Duration",duration);var list=new ListTag();
        for(var b:bonuses){var t=new CompoundTag();t.putString("Effect",b.effect.id);t.putInt("Strength",b.strength);if(b.pair)t.putBoolean("Pair",true);list.add(t);}n.put("Bonuses",list);return n;
    }
    static MealData load(CompoundTag n){
        boolean stew=n.getBoolean("Stew");var out=new ArrayList<Bonus>();var seen=EnumSet.noneOf(MealRules.Effect.class);
        for(var t:n.getList("Bonuses",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;var e=MealRules.Effect.of(c.getString("Effect"));if(e!=null&&seen.add(e)&&out.size()<(stew?3:2))out.add(new Bonus(e,Math.max(1,Math.min(3,c.getInt("Strength"))),stew&&c.getBoolean("Pair")));}
        return out.isEmpty()?null:new MealData(stew,out,Math.max(20,Math.min(MealRules.FIELD_TICKS,n.getInt("Duration"))));
    }
}
