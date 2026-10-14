package dev.eternaldefense.beacon;

/**
 * 0.0.35 (owner): the numbers of the Drink Station, the Mess Hall's sibling for potions. Pure, so the unit tests pin them.
 * <ul>
 * <li>Mk I is a brewing stand with a 54-slot potion storage; mixing opens at Mk II. A drink carries 1, 2 or 3 effects (Mk II, III, IV),
 *     one potion each, and the station keeps 1, 2 or 3 Brew Pots within 8 blocks.</li>
 * <li>A drink lasts 10 minutes in the field. The sweetener (Regeneration potions turned into mB) buys +10 or +20 minutes at 1,000 mB a step.</li>
 * <li>A Latte is the portable drink: a Paper Cup plus up to 2 potions, 10 minutes, no sweetener, stacks to 16.</li>
 * </ul>
 */
final class DrinkRules {
    private DrinkRules(){}
    /** A drink's field time without sweetener: 10 minutes. Each sweetener step adds 10 more. */
    static final int BASE_TICKS=12000,STEP_TICKS=12000,STEP_MB=1000,MAX_STEPS=2;
    /** The sweetener tank: 4,000 mB; Regeneration I (and its long version) adds 250 mB, Regeneration II adds 750 mB. */
    static final int TANK_MB=4000,REGEN_I_MB=250,REGEN_II_MB=750;
    /** The potion storage: 54 slots of up to 64 bottles of one kind, counted inside the station. Over 256 bottles it cannot be broken by hand. */
    static final int STORAGE_SLOTS=54,PER_SLOT=64,BREAK_GUARD=256;
    static final int LINK_RANGE=8,LATTE_MAX=2,LATTE_STACK=16,CUP_STOCK=64;
    /** Brewing stand numbers, as vanilla: one blaze powder gives 20 brews, a brew takes 400 ticks. */
    static final int FUEL_USES=20,BREW_TICKS=400;
    /** A station tier: {@code effects} per drink (0 = no mixing) and linked Brew Pots. */
    record Tier(int mk,int effects,int pots){boolean mixing(){return effects>0;}}
    static Tier tier(int mk){return switch(mk){case 2->new Tier(2,1,1);case 3->new Tier(3,2,2);case 4->new Tier(4,3,3);default->new Tier(1,0,0);};}
    /** Effects a Latte can carry at this tier: the tier's own count, at most 2. */
    static int latteEffects(int mk){return Math.min(LATTE_MAX,tier(mk).effects());}
    static int clampSteps(int steps){return Math.max(0,Math.min(MAX_STEPS,steps));}
    /** Field ticks of a drink with {@code steps} of sweetener (a Latte never has any). */
    static int duration(int steps){return BASE_TICKS+clampSteps(steps)*STEP_TICKS;}
    /** mB that one drinkable Regeneration potion of this amplifier adds to the tank. */
    static int sweetener(int amplifier){return amplifier>=1?REGEN_II_MB:REGEN_I_MB;}
    static int sweetenerCost(int steps){return clampSteps(steps)*STEP_MB;}
    static boolean guarded(int bottles){return bottles>BREAK_GUARD;}
    /**
     * Why a potion cannot go into a mixing slot, or "" when it can. Checked in this order, so the player hears the first thing to fix:
     * thrown (splash or lingering), plain (no effect: water, awkward), multi (more than one effect, like Turtle Master), regeneration
     * (it goes in the sweetener), harmful, instant (no duration), duplicate (that effect is already in another slot).
     */
    static String refusal(boolean drinkable,int effects,boolean regeneration,boolean beneficial,boolean instant,boolean duplicate){
        if(!drinkable)return "thrown";
        if(effects==0)return "plain";
        if(effects>1)return "multi";
        if(regeneration)return "regeneration";
        if(!beneficial)return "harmful";
        if(instant)return "instant";
        if(duplicate)return "duplicate";
        return "";
    }
    /** Minutes, for texts: 12,000 ticks are 10 minutes. */
    static int minutes(int ticks){return ticks/1200;}
}
