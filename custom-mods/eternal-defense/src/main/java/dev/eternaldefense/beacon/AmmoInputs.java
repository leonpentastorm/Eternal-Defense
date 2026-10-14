package dev.eternaldefense.beacon;

/** Pack edition: the factory parts an ammo recipe takes for a batch of rounds. */
public final class AmmoInputs {
    private AmmoInputs(){}
    /** Weak special rounds keep the price they had: an ammo id whose name contains one of these words. */
    static final java.util.List<String> SPECIAL=java.util.List.of("flare","bean_bag","pepperball","water","medkit");
    /** Pure: is this ammo (an id like hamster:flares_ammo) one of the weak specials? */
    public static boolean special(String ammoId){
        String path=ammoId==null?"":ammoId.toLowerCase(java.util.Locale.ROOT);int colon=path.indexOf(':');if(colon>=0)path=path.substring(colon+1);
        for(String word:SPECIAL)if(path.contains(word))return true;
        return false;
    }
    /** Pure: cartridge cases for a batch of this ammo: a special keeps one case per 8 rounds */
    public static int casings(int batch,String ammoId){return special(ammoId)?before(batch,CASE_ROUNDS):casings(batch);}
    /** Pure: propellant for a batch of this ammo: a special keeps one per 16 rounds; everything else propellant(int). */
    public static int propellant(int batch,String ammoId){return special(ammoId)?before(batch,PROPELLANT_ROUNDS):propellant(batch);}
    /** Rounds per cartridge case and per propellant. */
    static final int CASE_ROUNDS=8,PROPELLANT_ROUNDS=16;
    /** Propellant x2; cartridge cases x6/5 (+20 percent), rounded up. */
    static final int PROPELLANT_FACTOR=2,CASE_NUMERATOR=6,CASE_DENOMINATOR=5;
    /** Pure: one part per per rounds started, at least one. */
    static int before(int batch,int per){return Math.max(1,(Math.max(1,batch)+per-1)/per);}
    /** Pure: cartridge cases for a batch of batch rounds: 20 percent more than rounded up (1 becomes 2, 4 becomes 5). */
    public static int casings(int batch){int n=before(batch,CASE_ROUNDS);return (n*CASE_NUMERATOR+CASE_DENOMINATOR-1)/CASE_DENOMINATOR;}
    /** Pure: propellant for a batch of batch rounds: twice the amount. */
    public static int propellant(int batch){return before(batch,PROPELLANT_ROUNDS)*PROPELLANT_FACTOR;}
}
