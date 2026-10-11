package dev.createarsenal.beacon;

/**
 * Pack edition: the factory parts an ammo recipe takes for a batch of rounds. One source for both places that set them: the KubeJS
 * script that rewrites the gunpacks' recipes ({@code pack-integration/kubejs/server_scripts/20_guns_and_ammo.js}, through
 * {@code Java.loadClass}) and the Ammo Platform on a server without KubeJS ({@link WeaponPlatform#costs}).
 * <p>Until 0.0.21 a batch took one cartridge case per 8 rounds started and one propellant per 16. 0.0.22 (owner): twice the propellant,
 * 20 percent more cartridge cases, rounded up. The standalone edition's ammo costs vanilla materials ({@link StandaloneBalance}) and is not
 * affected.
 */
public final class AmmoInputs {
    private AmmoInputs(){}
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
