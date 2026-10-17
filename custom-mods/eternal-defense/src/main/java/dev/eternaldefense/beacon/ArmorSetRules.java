package dev.eternaldefense.beacon;

import java.util.List;

/** Every number of the Age armor sets in one place */
final class ArmorSetRules {
    private ArmorSetRules(){}
    /** The sets in Age order */
    static final List<String> SETS=List.of("frontier","worldwars","modern","advanced","exotic");
    /** Armor points per piece (helmet, chestplate, leggings, boots): Frontier as iron, Modern and later as diamond */
    static final int[][] DEFENSE={{2,6,5,2},{2,6,6,2},{3,8,6,3},{3,8,6,3},{3,8,6,3}};
    static final float[] TOUGHNESS={0,1,2,2,2};
    /** Durability multipliers: iron 15, diamond 33 */
    static final int[] DURABILITY={15,20,33,33,33};
    static final int[] ENCHANTABILITY={9,9,10,10,10};
    /** Quick Draw's reload speed, the gun damage of Stormtrooper and Night Operator, Exo Bulwark's cut of bullets and explosions */
    static final double QUICK_DRAW=.15,GUN_DAMAGE=.10,WARDED=.25;
    /** Ardent Ward: fires below this share of health, then waits; its Absorption (level 1 is 4 hearts) and push */
    static final double WARD_BELOW=.3,WARD_RADIUS=6,WARD_PUSH=1.4;
    static final int WARD_COOLDOWN=60*20,WARD_ABSORPTION_TICKS=10*20,WARD_ABSORPTION_LEVEL=1,EXOTIC_HEALTH=4;
    static int age(String set){return SETS.indexOf(set)+1;}
    /** Pure: the share of a hit the set takes away (bullets and explosions on Advanced) */
    static double warded(String set,String source){return set.equals("advanced")&&(source.equals("bullet")||source.equals("explosion"))?WARDED:0;}
    /** Pure: Ardent Ward fires when a hit leaves the wearer alive below the threshold and the cooldown is over */
    static boolean wardFires(float health,float max,float damage,long now,long readyAt){float left=health-damage;return left>0&&left<max*WARD_BELOW&&now>=readyAt;}
}
