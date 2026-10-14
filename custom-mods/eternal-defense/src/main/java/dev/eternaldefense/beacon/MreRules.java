package dev.eternaldefense.beacon;

import java.util.List;

/**
 * 0.0.38 (owner): the numbers of the MRE, with no Minecraft classes so they can be tested alone. An MRE is the foods packed into it, no
 * gain and no loss: its nutrition is the exact sum of the foods' nutrition, its saturation the exact sum of what each food adds to
 * saturation when eaten alone (vanilla's own formula, {@link #saturation}), stored as the plain sum and never above 20. Eaten, it goes
 * through vanilla's {@code FoodData.eat} with the modifier that gives back exactly that saturation ({@link #modifier}).
 */
final class MreRules {
    private MreRules(){}
    /** The most nutrition one MRE holds (a full hunger bar), the most saturation it stores, its food slots, its stack and how fast it is eaten. */
    static final int MAX_NUTRITION=20,FOOD_SLOTS=6,STACK=64,EAT_TICKS=16;
    static final float MAX_SATURATION=20f;

    /** One food as vanilla sees it: its nutrition and its saturation modifier. */
    record Food(int nutrition,float modifier){
        /** A food the MRE can hold: no negative nutrition, a real modifier (a modded food with neither would make the sums meaningless). */
        boolean valid(){return nutrition>=0&&Float.isFinite(modifier)&&modifier>=0;}
        float saturation(){return MreRules.saturation(nutrition,modifier);}
    }
    /** What an MRE holds. */
    record Totals(int nutrition,float saturation){
        /** An MRE that can be eaten: something to restore, within the limits (a stack with other numbers was not made by a station). */
        boolean valid(){return nutrition>=1&&nutrition<=MAX_NUTRITION&&Float.isFinite(saturation)&&saturation>=0&&saturation<=MAX_SATURATION;}
    }

    /** What eating one food adds to saturation before vanilla's cap: {@code FoodData.eat}'s {@code nutrition * modifier * 2.0F}, in float as vanilla does it. */
    static float saturation(int nutrition,float modifier){return (float)nutrition*modifier*2.0F;}
    static int nutrition(List<Food> foods){int n=0;for(var f:foods)n+=f.nutrition();return n;}
    /**
     * The MRE's totals: nutrition summed, saturation summed exactly (each food's float value added up in double, which holds the sum of a
     * few floats exactly, so the order of the slots never changes the result) and stored as the nearest float, at most 20.
     */
    static Totals sum(List<Food> foods){
        int n=0;double s=0;
        for(var f:foods){n+=f.nutrition();s+=f.saturation();}
        return new Totals(n,(float)Math.min(s,MAX_SATURATION));
    }
    /** May a food of {@code adding} nutrition join foods that already give {@code current}? Never past 20. */
    static boolean fits(int current,int adding){return adding>=0&&current+adding<=MAX_NUTRITION;}
    /**
     * The saturation modifier to hand vanilla's {@code FoodData.eat(nutrition, modifier)} so it adds {@code saturation}: saturation /
     * (2 x nutrition), then the float within two steps of it whose product in vanilla's float formula lands nearest without passing it:
     * exactly, when a float can; some totals sit a float step away from every product, and then the step below is taken, so an MRE never
     * gives more than it holds.
     */
    static float modifier(int nutrition,float saturation){
        if(nutrition<=0)return 0f;
        float m=saturation/(2f*nutrition),best=m;float bestMiss=Float.MAX_VALUE;
        for(float candidate:new float[]{m,Math.nextDown(m),Math.nextUp(m),Math.nextDown(Math.nextDown(m)),Math.nextUp(Math.nextUp(m))}){
            float got=saturation(nutrition,candidate);
            if(got<=saturation&&saturation-got<bestMiss){best=candidate;bestMiss=saturation-got;}
        }
        return best;
    }
    /** The two output checks: empty, or MREs with the same totals and room for one more. */
    static boolean outputTakes(Totals inOutput,int count,Totals made){return count==0||inOutput!=null&&inOutput.equals(made)&&count<STACK;}
    /** A number for the screen and the tooltip: up to two decimals, no trailing zeros (16.8, 12, 0.35). */
    static String number(float value){
        var s=String.format(java.util.Locale.ROOT,"%.2f",value);
        if(s.contains(".")){s=s.replaceAll("0+$","");if(s.endsWith("."))s=s.substring(0,s.length()-1);}
        return s;
    }
}
