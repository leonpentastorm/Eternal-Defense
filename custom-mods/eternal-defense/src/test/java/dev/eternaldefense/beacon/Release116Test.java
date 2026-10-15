package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 1.1.6 list: bigger Badass mobs are slower (creepers more so) and a Badass creeper's blast grows with its size. */
final class Release116Test {
    static String source(String name) throws Exception{return Release114Test.source(name);}
    static String text(String key) throws Exception{return Release114Test.text(key);}

    @Test void badassSlowsDownALevelAtATime(){
        double[] plain={.025,.05,.075,.10,.125},creeper={.0375,.075,.1125,.15,.1875};   // Halved in 1.1.9
        for(int l=1;l<=5;l++){
            assertEquals(plain[l-1],Elites.slowdown(l,false),1e-9,"Badass "+l);
            assertEquals(creeper[l-1],Elites.slowdown(l,true),1e-9,"Badass creeper "+l);
            assertEquals(1.5*Elites.slowdown(l,false),Elites.slowdown(l,true),1e-9,"a creeper loses 1.5 times as much");
        }
        assertEquals(.125,Elites.slowdown(9,false),1e-9,"never slower than Badass V");
    }
    @Test void badassCreeperBlastGrowsWithItsSize(){
        int[] radius={5,7,9,12,15};
        for(int l=1;l<=5;l++)assertEquals(radius[l-1],Elites.blast(3,Elites.scale(Elites.Kind.BADASS,l)),"Badass creeper "+l);
        assertEquals(6,Elites.blast(6,1),"a creeper that is not bigger keeps its blast");
    }
    @Test void makeAppliesBothAndTheGuideQuotesThem() throws Exception{
        String elites=source("Elites.java");
        assertTrue(elites.contains("if(kind==Kind.BADASS)modifier(mob,Attributes.MOVEMENT_SPEED,SPEED,\"Elite weight\",-slowdown(level,mob instanceof Creeper)"),"the slowdown uses the elite speed id the raid's balancing keeps");
        assertTrue(Elites.OURS.contains(Elites.SPEED));
        assertTrue(elites.contains("if(kind==Kind.BADASS&&mob instanceof Creeper creeper)setBlast(creeper,blast(blast(creeper),scale(kind,level)));"));
        assertTrue(elites.contains("findField(Creeper.class,\"f_32272_\")"),"the creeper's own blast radius field (saved with it)");
        String detail=text("guide.raids.detail");
        assertTrue(detail.contains("2.5%% slower a level (12.5%% at V); a creeper 3.75%% a level (18.75%% at V)"),detail);
        assertTrue(detail.contains("blast radius: 5, 7, 9, 12 and 15 blocks"),detail);
        assertTrue(text("guide.raids.body").contains("The bigger it is, the slower it walks. A Badass creeper is even slower, but its blast grows with its size."));
        assertTrue(source("OperatorCommands.java").contains("% slower")&&source("OperatorCommands.java").contains("blast radius "),"the elite command says both");
    }
}
