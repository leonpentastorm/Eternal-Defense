package dev.createarsenal.beacon;

/**
 * Pack edition: the factory parts an ammo recipe takes for a batch of rounds. One source for both places that set them: the KubeJS
 * script that rewrites the gunpacks' recipes ({@code pack-integration/kubejs/server_scripts/20_guns_and_ammo.js}, through
 * {@code Java.loadClass}) and the Ammo Platform on a server without KubeJS ({@link WeaponPlatform#costs}).
 * <p>Until 0.0.21 a batch took one cartridge case per 8 rounds started and one propellant per 16. 0.0.22 (owner): twice the propellant,
 * 20 percent more cartridge cases, rounded up. 0.0.26: the Ammo Platform sets these amounts itself, replacing whatever the recipe lists (the
 * owner's pack still ran the older KubeJS script, so the platform showed the old amounts), and weak special rounds keep the old price. The standalone edition's ammo costs vanilla materials ({@link StandaloneBalance}) and is not
 * affected.
 */
public final class AmmoInputs {
    private AmmoInputs(){}
    /**
     * Weak special rounds keep the price they had before 0.0.22 (owner, 0.0.26: "the flare ammo being 1 case one propellant should be kept
     * because that bullet is weak"): an ammo id whose name contains one of these words.
     */
    static final java.util.List<String> SPECIAL=java.util.List.of("flare","bean_bag","pepperball","water","medkit");
    /** Pure: is this ammo (an id like {@code hamster:flares_ammo}) one of the weak specials? */
    public static boolean special(String ammoId){
        String path=ammoId==null?"":ammoId.toLowerCase(java.util.Locale.ROOT);int colon=path.indexOf(':');if(colon>=0)path=path.substring(colon+1);
        for(String word:SPECIAL)if(path.contains(word))return true;
        return false;
    }
    /** Pure: cartridge cases for a batch of this ammo: a special keeps one case per 8 rounds; everything else {@link #casings(int)}. */
    public static int casings(int batch,String ammoId){return special(ammoId)?before(batch,CASE_ROUNDS):casings(batch);}
    /** Pure: propellant for a batch of this ammo: a special keeps one per 16 rounds; everything else {@link #propellant(int)}. */
    public static int propellant(int batch,String ammoId){return special(ammoId)?before(batch,PROPELLANT_ROUNDS):propellant(batch);}
    /** Rounds per cartridge case and per propellant before 0.0.22. */
    static final int CASE_ROUNDS=8,PROPELLANT_ROUNDS=16;
    /** 0.0.22: propellant x2; cartridge cases x6/5 (+20 percent), rounded up. */
    static final int PROPELLANT_FACTOR=2,CASE_NUMERATOR=6,CASE_DENOMINATOR=5;
    /** Pure: one part per {@code per} rounds started, at least one (the amount before 0.0.22). */
    static int before(int batch,int per){return Math.max(1,(Math.max(1,batch)+per-1)/per);}
    /** Pure: cartridge cases for a batch of {@code batch} rounds: 20 percent more than before 0.0.22, rounded up (1 becomes 2, 4 becomes 5). */
    public static int casings(int batch){int n=before(batch,CASE_ROUNDS);return (n*CASE_NUMERATOR+CASE_DENOMINATOR-1)/CASE_DENOMINATOR;}
    /** Pure: propellant for a batch of {@code batch} rounds: twice the amount before 0.0.22. */
    public static int propellant(int batch){return before(batch,PROPELLANT_ROUNDS)*PROPELLANT_FACTOR;}
}
