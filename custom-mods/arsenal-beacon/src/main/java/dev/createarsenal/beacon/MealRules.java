package dev.createarsenal.beacon;

import java.util.*;

/** Balance and composition rules, independent of Minecraft registries. */
final class MealRules {
    private MealRules(){}
    static final int FIELD_TICKS=30*60*20,LINK_RANGE=8;
    static final double HOME_MULTIPLIER=2;
    enum Effect {
        VITALITY("vitality",4),FORTITUDE("fortitude",2),STEADINESS("steadiness",.05),MOBILITY("mobility",.04),
        FIREPOWER("firepower",.05),QUICK_HANDS("quick_hands",.06),BRAWLER("brawler",.15),HEAVY_HAND("heavy_hand",.12),DEMOLITION("demolition",.05);
        final String id;final double amount;
        Effect(String id,double amount){this.id=id;this.amount=amount;}
        static Effect of(String id){for(var e:values())if(e.id.equals(id))return e;return null;}
    }
    record Tier(int pots,int servings,int ingredients){}
    static Tier tier(int mk){return switch(mk){case 2->new Tier(2,8,7);case 3->new Tier(3,12,10);case 4->new Tier(4,16,12);default->new Tier(1,4,4);};}
    /** Reserve one unit of each composition type, then spread the remaining batch cost across stacks. */
    static int[] plan(int[] counts,int[] types,int required){
        var spent=new int[counts.length];var seen=new HashSet<Integer>();int left=required;
        for(int i=0;i<counts.length;i++)if(counts[i]>0&&seen.add(types[i])){spent[i]++;left--;}
        if(left<0||Arrays.stream(counts).sum()<required)return null;
        while(left>0){boolean progress=false;for(int i=0;i<counts.length&&left>0;i++)if(spent[i]<counts[i]){spent[i]++;left--;progress=true;}if(!progress)return null;}
        return spent;
    }
    /** Ingredient scores choose a bounded, deterministic set; quantity in a stack never buys extra effects. */
    static List<Effect> select(Map<Effect,Integer> scores,boolean stew){
        return scores.entrySet().stream().filter(e->e.getValue()>0)
            .sorted(Comparator.<Map.Entry<Effect,Integer>>comparingInt(Map.Entry::getValue).reversed().thenComparingInt(e->e.getKey().ordinal()))
            .limit(stew?3:2).map(Map.Entry::getKey).toList();
    }
    static int remaining(int ticks,boolean home){return home?Math.max(0,ticks):Math.max(0,ticks-1);}
}
