package dev.createarsenal.beacon;

import java.util.*;

/** Balance and composition rules, independent of Minecraft registries. */
final class MealRules {
    private MealRules(){}
    static final int FIELD_TICKS=30*60*20,LINK_RANGE=8;
    static final double HOME_MULTIPLIER=2;
    /** Every meal effect. Order is the tie-break when two effects score the same, so new effects are only ever appended. */
    enum Effect {
        VITALITY("vitality",4,false,false),FORTITUDE("fortitude",2,false,false),STEADINESS("steadiness",.05,true,false),MOBILITY("mobility",.04,true,true),
        FIREPOWER("firepower",.08,true,false),QUICK_HANDS("quick_hands",.10,true,false),BRAWLER("brawler",.20,true,false),HEAVY_HAND("heavy_hand",.15,true,false),DEMOLITION("demolition",.10,true,false),
        MIGHT("might",.08,true,true),AGILITY("agility",.08,true,true),FORTUNE("fortune",1,false,false),RECOVERY("recovery",1,false,false),
        HEARTH("hearth",.10,true,false),SPRINGY("springy",.12,true,false),SWIM("swim",.10,true,true);
        final String id;final double amount;final boolean percent,multiplier;
        Effect(String id,double amount,boolean percent,boolean multiplier){this.id=id;this.amount=amount;this.percent=percent;this.multiplier=multiplier;}
        static Effect of(String id){for(var e:values())if(e.id.equals(id))return e;return null;}
        /** Effects whose value is a share that must stay below one (damage taken). */
        double cap(){return this==HEARTH?.8:this==SPRINGY?.9:Double.MAX_VALUE;}
    }
    /** Two different foods of one family feeding the same effect in a stew double that effect. */
    static final int PAIR_MULTIPLIER=2;
    /** Health restored by Recovery, in ticks between pulses. */
    static final int RECOVERY_PERIOD=100;
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
