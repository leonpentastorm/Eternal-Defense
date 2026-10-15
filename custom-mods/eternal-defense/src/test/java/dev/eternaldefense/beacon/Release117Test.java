package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 1.1.7 list: elites fit the mob's way of fighting; Vampiric, Frostbite and Berserker get 50% more health. */
final class Release117Test {
    static String text(String key) throws Exception{return Release114Test.text(key);}
    static Set<Elites.Kind> kinds(boolean bow,boolean shooter){var out=EnumSet.noneOf(Elites.Kind.class);for(double r=0;r<1;r+=.0005)out.add(Elites.choose(r,bow,shooter));return out;}

    @Test void eliteKindsFitTheMob(){
        var archer=kinds(true,true);
        assertTrue(archer.contains(Elites.Kind.LONG_SHOT),"an archer can be a Long Shot");
        for(var k:List.of(Elites.Kind.VAMPIRIC,Elites.Kind.FROSTBITE,Elites.Kind.BERSERKER))assertFalse(archer.contains(k),"an archer is never "+k);
        var blaze=kinds(false,true);
        assertFalse(blaze.contains(Elites.Kind.LONG_SHOT),"a fireball shooter is no Long Shot");
        for(var k:List.of(Elites.Kind.VAMPIRIC,Elites.Kind.FROSTBITE,Elites.Kind.BERSERKER))assertFalse(blaze.contains(k),"a fireball shooter is never "+k);
        var melee=kinds(false,false);
        assertFalse(melee.contains(Elites.Kind.LONG_SHOT),"a melee mob is never a Long Shot");
        assertTrue(melee.containsAll(List.of(Elites.Kind.VAMPIRIC,Elites.Kind.FROSTBITE,Elites.Kind.BERSERKER)));
        for(var k:List.of(Elites.Kind.BADASS,Elites.Kind.SPEEDSTER,Elites.Kind.HEALER,Elites.Kind.MATRIOSKA,Elites.Kind.FLAK_ARMOR,Elites.Kind.BODY_ARMOR))
            assertTrue(archer.contains(k)&&blaze.contains(k)&&melee.contains(k),k+" fits every mob");
    }
    @Test void theHitKindsHaveHalfAgainTheirHealth() throws Exception{
        for(var k:List.of(Elites.Kind.VAMPIRIC,Elites.Kind.FROSTBITE,Elites.Kind.BERSERKER))assertEquals(.5,Elites.healthBonus(k,1),1e-9,k+" +50% health");
        assertEquals(2.0,Elites.berserk(0,30),1e-9,"a Berserker still hits up to twice as hard");
        String body=text("guide.raids.body");
        assertTrue(body.contains("- Vampiric: 50%% more health")&&body.contains("- Frostbite: 50%% more health")&&body.contains("- Berserker: 50%% more health"));
        assertTrue(body.contains("Only mobs that fight up close become Vampiric, Frostbite or Berserker. Only archers become a Long Shot."));
    }
}
