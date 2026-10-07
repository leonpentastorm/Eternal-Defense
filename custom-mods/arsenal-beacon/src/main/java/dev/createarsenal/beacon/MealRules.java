package dev.createarsenal.beacon;

import java.util.*;

/** Balance and composition rules, independent of Minecraft registries. */
final class MealRules {
    private MealRules(){}
    static final int FIELD_TICKS=30*60*20,LINK_RANGE=8;
    static final double HOME_MULTIPLIER=2;
    enum Effect {
        VITALITY("vitality",4),FORTITUDE("fortitude",2),STEADINESS("steadiness",.05),MOBILITY("mobility",.04);
        final String id;final double amount;
        Effect(String id,double amount){this.id=id;this.amount=amount;}
        static Effect of(String id){for(var e:values())if(e.id.equals(id))return e;return null;}
    }
    record Tier(int pots,int servings){}
    static Tier tier(int mk){return switch(mk){case 2->new Tier(2,8);case 3->new Tier(3,12);case 4->new Tier(4,16);default->new Tier(1,4);};}
    /** Ingredient scores choose a bounded, deterministic set; quantity in a stack never buys extra effects. */
    static List<Effect> select(Map<Effect,Integer> scores,boolean stew){
        return scores.entrySet().stream().filter(e->e.getValue()>0)
            .sorted(Comparator.<Map.Entry<Effect,Integer>>comparingInt(Map.Entry::getValue).reversed().thenComparingInt(e->e.getKey().ordinal()))
            .limit(stew?3:2).map(Map.Entry::getKey).toList();
    }
    static int remaining(int ticks,boolean home){return home?Math.max(0,ticks):Math.max(0,ticks-1);}
}
