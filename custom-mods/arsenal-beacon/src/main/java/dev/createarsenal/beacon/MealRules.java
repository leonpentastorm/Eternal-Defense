package dev.createarsenal.beacon;

import java.util.*;

/** Balance and composition rules, independent of Minecraft registries. */
final class MealRules {
    private MealRules(){}
    /** The longest a meal can last (a Mk IV hall); every hall tier has its own length, see {@link #tier}. */
    static final int FIELD_TICKS=30*60*20,LINK_RANGE=8;
    static final double HOME_MULTIPLIER=2;
    /**
     * Every meal effect. Order is the tie-break when two effects score the same, so new effects are only ever appended. 0.0.35 (owner): three
     * were replaced in place (same position, button id and foods): Mobility became HARDY (slower hunger), Hearth BLAST_WARD (explosions only),
     * Springy Step PROJECTILE_WARD (arrows, bolts, fireballs, tridents; not gun bullets). Saved meals with the old ids just expire.
     */
    enum Effect {
        VITALITY("vitality",4,false,false),FORTITUDE("fortitude",2,false,false),STEADINESS("steadiness",.05,true,false),HARDY("hardy",.15,true,false),
        FIREPOWER("firepower",.08,true,false),QUICK_HANDS("quick_hands",.10,true,false),BRAWLER("brawler",.20,true,false),HEAVY_HAND("heavy_hand",.15,true,false),DEMOLITION("demolition",.10,true,false),
        MIGHT("might",.08,true,true),AGILITY("agility",.08,true,true),FORTUNE("fortune",1,false,false),RECOVERY("recovery",1,false,false),
        BLAST_WARD("blast_ward",.20,true,false),PROJECTILE_WARD("projectile_ward",.15,true,false),SWIM("swim",.10,true,true),
        RECOIL_CONTROL("recoil_control",.10,true,false),FOCUS("focus",.10,true,false),SNAP_AIM("snap_aim",.10,true,false),
        FAST_DRAW("fast_draw",.10,true,false),DEAD_EYE("dead_eye",.08,true,false),GUN_MOBILITY("gun_mobility",.08,true,false),
        IMPACT("impact",.25,false,false),RAPID_FIRE("rapid_fire",.05,true,false),SCOPED_FOCUS("scoped_focus",.15,true,false),HIP_FOCUS("hip_focus",.15,true,false);
        final String id;final double amount;final boolean percent,multiplier;
        Effect(String id,double amount,boolean percent,boolean multiplier){this.id=id;this.amount=amount;this.percent=percent;this.multiplier=multiplier;}
        static Effect of(String id){for(var e:values())if(e.id.equals(id))return e;return null;}
        /** Effects whose value is a share that must stay below one (damage taken). */
        boolean gun(){return this==FIREPOWER||this==QUICK_HANDS||this==BRAWLER||this==HEAVY_HAND||this==DEMOLITION||ordinal()>=RECOIL_CONTROL.ordinal();}
        boolean reduction(){return this==RECOIL_CONTROL;}
        double cap(){return this==BLAST_WARD||this==RECOIL_CONTROL?.8:this==PROJECTILE_WARD?.9:this==HARDY?.6:Double.MAX_VALUE;}
    }
    /** From Mk III a meal can double an effect with distinct foods; legendary effects need two from each group, which takes four food slots (Mk IV). */
    static final int PAIR_MULTIPLIER=2;
    record Mix(String first,String second,Effect effect){}
    /** Ordered matching: each group participates in at most one legendary mix. */
    static final List<Mix> MIXES=List.of(
        new Mix("protein","grain",Effect.FIREPOWER),new Mix("fruit","grain",Effect.QUICK_HANDS),
        new Mix("protein","fungi",Effect.BRAWLER),new Mix("protein","vegetables",Effect.HEAVY_HAND),
        new Mix("fish","fungi",Effect.DEMOLITION),new Mix("vegetables","grain",Effect.RECOIL_CONTROL),
        new Mix("fish","vegetables",Effect.FOCUS),new Mix("fish","fruit",Effect.SNAP_AIM),
        new Mix("fruit","fungi",Effect.FAST_DRAW),new Mix("protein","fish",Effect.DEAD_EYE),
        new Mix("protein","fruit",Effect.GUN_MOBILITY),new Mix("fish","grain",Effect.IMPACT),
        new Mix("vegetables","fruit",Effect.RAPID_FIRE),new Mix("vegetables","fungi",Effect.SCOPED_FOCUS),
        new Mix("grain","fungi",Effect.HIP_FOCUS));
    static List<Mix> mixes(Set<String> groups){
        var used=new HashSet<String>();var out=new ArrayList<Mix>();
        for(var mix:MIXES)if(groups.contains(mix.first)&&groups.contains(mix.second)&&!used.contains(mix.first)&&!used.contains(mix.second)){
            used.add(mix.first);used.add(mix.second);out.add(mix);
        }
        return List.copyOf(out);
    }
    static boolean doubles(int mk,int first,int second,boolean legendary){return tier(mk).doubling()&&first>=2&&(!legendary||second>=2);}
    /** Health restored by Recovery, in ticks between pulses. */
    static final int RECOVERY_PERIOD=100;
    /**
     * What a hall of this level can do. {@code slots}: food slots on the table (kinds of food). {@code minutes}: how long its meals last in the field.
     * Mk I makes sandwiches only; Mk II unlocks stew; Mk III lets one effect count twice; Mk IV opens all six slots.
     */
    record Tier(int mk,int pots,int servings,int ingredients,int slots,int minutes){
        boolean stew(){return mk>=2;}
        boolean doubling(){return mk>=3;}
        int ticks(){return minutes*60*20;}
    }
    /** 0.0.35 (owner): meals cost more, they do not give more: a stew batch takes three times the food (12, 21, 30, 36), the servings stay 3, 5, 7, 9. */
    static Tier tier(int mk){return switch(mk){case 2->new Tier(2,2,5,21,3,20);case 3->new Tier(3,3,7,30,3,25);case 4->new Tier(4,4,9,36,6,30);default->new Tier(1,1,3,12,3,15);};}
    /** 0.0.35 (owner): a sandwich takes two of each filling and two bread; one cook still makes one sandwich. */
    static final int SANDWICH_FILLING=2,SANDWICH_BREAD=2;
    /** Exactly {@code per} units of every type, from its stacks in order; null when a type has fewer. */
    static int[] planEach(int[] counts,int[] types,int per){
        var spent=new int[counts.length];var need=new HashMap<Integer,Integer>();
        for(int i=0;i<counts.length;i++)if(counts[i]>0)need.putIfAbsent(types[i],per);
        for(int i=0;i<counts.length;i++){if(counts[i]<=0)continue;int left=need.get(types[i]);int take=Math.min(left,counts[i]);spent[i]=take;need.put(types[i],left-take);}
        for(int left:need.values())if(left>0)return null;
        return spent;
    }
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
