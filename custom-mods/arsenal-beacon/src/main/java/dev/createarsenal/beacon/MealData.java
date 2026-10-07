package dev.createarsenal.beacon;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The same bounded composition is saved on sandwiches, pots and active player meals. */
record MealData(boolean stew,List<Bonus> bonuses,int duration) {
    record Bonus(MealRules.Effect effect,int strength){
        double field(){return effect.amount*(1+.25*(strength-1));}
        double home(){return field()*MealRules.HOME_MULTIPLIER;}
        Component description(boolean enhanced){
            double value=enhanced?home():field();
            boolean percent=effect!=MealRules.Effect.VITALITY&&effect!=MealRules.Effect.FORTITUDE;
            String amount="+"+java.math.BigDecimal.valueOf(value*(percent?100:1)).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+(percent?"%":"");
            return Component.translatable("gui.arsenal_beacon.meal.bonus."+effect.id,amount);
        }
    }
    MealData {bonuses=List.copyOf(bonuses);}
    Component name(){return Component.translatable("gui.arsenal_beacon.meal.name."+(stew?"stew":"sandwich"),Component.translatable("gui.arsenal_beacon.meal.effect."+bonuses.get(0).effect.id));}
    CompoundTag save(){
        var n=new CompoundTag();n.putBoolean("Stew",stew);n.putInt("Duration",duration);var list=new ListTag();
        for(var b:bonuses){var t=new CompoundTag();t.putString("Effect",b.effect.id);t.putInt("Strength",b.strength);list.add(t);}n.put("Bonuses",list);return n;
    }
    static MealData load(CompoundTag n){
        boolean stew=n.getBoolean("Stew");var out=new ArrayList<Bonus>();var seen=EnumSet.noneOf(MealRules.Effect.class);
        for(var t:n.getList("Bonuses",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;var e=MealRules.Effect.of(c.getString("Effect"));if(e!=null&&seen.add(e)&&out.size()<(stew?3:2))out.add(new Bonus(e,Math.max(1,Math.min(3,c.getInt("Strength")))));}
        return out.isEmpty()?null:new MealData(stew,out,Math.max(20,Math.min(MealRules.FIELD_TICKS,n.getInt("Duration"))));
    }
}
