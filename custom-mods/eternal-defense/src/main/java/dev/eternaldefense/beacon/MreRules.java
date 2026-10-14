package dev.eternaldefense.beacon;

import java.util.List;

/** The numbers of the MRE, with no Minecraft classes so they can be tested alone. */
final class MreRules {
    private MreRules(){}
    /** The most nutrition one MRE holds (a full hunger bar), the most saturation it stores, its food slots */
    static final int MAX_NUTRITION=20,FOOD_SLOTS=6,STACK=64,EAT_TICKS=16;
    static final float MAX_SATURATION=20f;

    /** One food as vanilla sees it: its nutrition and its saturation modifier. */
    record Food(int nutrition,float modifier){
        /** A food the MRE can hold: no negative nutrition, a real modifier */
        boolean valid(){return nutrition>=0&&Float.isFinite(modifier)&&modifier>=0;}
        float saturation(){return MreRules.saturation(nutrition,modifier);}
    }
    /** What an MRE holds. */
    record Totals(int nutrition,float saturation){
        /** An MRE that can be eaten: something to restore, within the limits */
        boolean valid(){return nutrition>=1&&nutrition<=MAX_NUTRITION&&Float.isFinite(saturation)&&saturation>=0&&saturation<=MAX_SATURATION;}
    }

    /** What eating one food adds to saturation before vanilla's cap */
    static float saturation(int nutrition,float modifier){return (float)nutrition*modifier*2.0F;}
    static int nutrition(List<Food> foods){int n=0;for(var f:foods)n+=f.nutrition();return n;}
    /** The MRE's totals: nutrition summed, saturation summed exactly */
    static Totals sum(List<Food> foods){
        int n=0;double s=0;
        for(var f:foods){n+=f.nutrition();s+=f.saturation();}
        return new Totals(n,(float)Math.min(s,MAX_SATURATION));
    }
    /** May a food of adding nutrition join foods that already give current? */
    static boolean fits(int current,int adding){return adding>=0&&current+adding<=MAX_NUTRITION;}
    /** The saturation modifier to hand vanilla's FoodData.eat(nutrition, modifier) so it adds saturation */
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
