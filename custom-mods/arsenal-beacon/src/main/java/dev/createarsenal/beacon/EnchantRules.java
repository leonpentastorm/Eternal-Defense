package dev.createarsenal.beacon;

import java.util.List;
import java.util.Map;

/**
 * The Tactical Enchanting Table (0.0.27, FOB slice 4): the pure rules, without Minecraft types, so they can be unit tested. Vanilla enchanting,
 * librarians and anvils are untouched; this is an opt-in alternative paid in Ardent Energy, whose highest levels need ACTIVE FOBs.
 */
final class EnchantRules {
    private EnchantRules(){}
    /** The level gate: ACTIVE FOBs needed for level {@code level} of an enchant whose highest level is {@code max}: ceil(5 (L-1) / (M-1)); level I and one-level enchants need none, the top level all five. */
    static int fobsFor(int level,int max){
        if(max<=1||level<=1)return 0;
        int l=Math.min(level,max);
        return (int)Math.ceil(5.0*(l-1)/(max-1));
    }
    /** Pure: the highest level of an enchant with highest level {@code max} that {@code activeFobs} ACTIVE FOBs allow (at least 1). */
    static int maxLevel(int max,int activeFobs){int best=1;for(int l=2;l<=max;l++)if(fobsFor(l,max)<=activeFobs)best=l;return best;}

    // ---- prices ----------------------------------------------------------------------------------------------------------------------------------
    /** Vanilla's rarity of an enchant (its weight on the enchanting table): COMMON 10, UNCOMMON 5, RARE 2, VERY_RARE 1. */
    enum Rarity{COMMON,UNCOMMON,RARE,VERY_RARE}
    /**
     * Standalone Ardent Energy per level, by rarity; treasure enchants (unlocked by Warlord Seals) cost twice that. 0.0.29 (owner): doubled in
     * both editions (2, 3, 5, 8 before), so a table at Mk III charges the 0.0.27 standalone price.
     */
    static final int[] PER_LEVEL_STANDALONE={4,6,10,16};
    static final int TREASURE_FACTOR=2;
    /**
     * The pack edition pays twice the standalone price (owner, 0.0.28): there Ardent Energy pays for little else, Create parts buy the other
     * upgrades; in the standalone edition it pays for everything. Until 0.0.27 the pack paid half.
     */
    static final int PACK_FACTOR=2;
    /** Pure: the full price of level {@code level} (before a table upgrade): rarity x level (x2 for treasure), x2 in the pack edition. */
    static int price(Rarity rarity,int level,boolean treasure,boolean standalone){
        int p=PER_LEVEL_STANDALONE[rarity.ordinal()]*Math.max(1,level)*(treasure?TREASURE_FACTOR:1);
        return standalone?p:p*PACK_FACTOR;
    }
    /** Levels and the table's Mk as players read them. */
    static final String[] ROMAN={"I","II","III","IV","V","VI","VII"};
    static String roman(int n){return n>=1&&n<=ROMAN.length?ROMAN[n-1]:String.valueOf(n);}
    /**
     * Table upgrades: Mk I to III, each a permanent discount on every enchant (percent). 0.0.28 (owner): Mk III takes half off, so a pack table
     * at Mk III charges exactly the standalone price (the 0.0.27 price), and Mk I and II are evenly between.
     */
    static final int MAX_MK=3;
    static final int[] DISCOUNT={0,17,33,50};
    /**
     * Pure: the price after the table's discount, rounded down, never below 1. Rounded down so every Mk takes at least 1 off any price of 2 or
     * more (rounded up, Mk I took nothing off the pack's small prices: Sharpness V, 5, stayed 5).
     */
    static int discounted(int price,int mk){int d=DISCOUNT[Math.max(0,Math.min(MAX_MK,mk))];return Math.max(1,price*(100-d)/100);}

    // ---- treasure ------------------------------------------------------------------------------------------------------------------------------
    /** The treasure enchants a Warlord Seal unlocks for the whole team (never the curses). */
    static final List<String> TREASURE=List.of("minecraft:mending","minecraft:frost_walker","minecraft:soul_speed","minecraft:swift_sneak");
    /** The ones whose levels II and III each cost one more seal (the first seal opens level I). */
    static final List<String> SEALED_LEVELS=List.of("minecraft:soul_speed","minecraft:swift_sneak");
    /** Pure: the highest level of treasure enchant {@code id} that {@code seals} seals spent on it unlock (0: locked). */
    static int unlockedLevel(String id,int seals,int max){
        if(seals<=0)return 0;
        return SEALED_LEVELS.contains(id)?Math.min(max,seals):max;
    }
    /** Pure: seals needed for the next level of {@code id}, or 0 when it is fully unlocked. */
    static int sealsForNext(String id,int seals,int max){return unlockedLevel(id,seals,max)>=max?0:1;}

    // ---- what the table offers --------------------------------------------------------------------------------------------------------------------
    /** Why a level cannot be bought (a key under {@code enchant.locked.}), or null. Checked in this order. */
    static String problem(boolean applies,boolean compatible,boolean treasure,int unlocked,int level,int max,int have,int activeFobs){
        if(!applies)return "applies";
        if(!compatible)return "conflict";
        if(treasure&&unlocked<level)return unlocked<=0?"seal":"seal_level";
        if(level>max)return "max";
        if(have>=level)return "have";
        if(fobsFor(level,max)>activeFobs)return "fobs";
        return null;
    }
}
