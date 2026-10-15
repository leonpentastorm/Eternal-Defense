package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.9: red health bars and ring colours that survive shader packs, giants' heads where they are drawn, half the Badass slowdown, an elite in the first raid. */
final class Release119Test {
    static String source(String name) throws Exception{return Release114Test.source(name);}
    static String text(String key) throws Exception{return Release114Test.text(key);}
    static final Path ELITE=Path.of("src/main/resources/assets/eternal_defense/textures/entity/elite");
    static java.awt.image.BufferedImage image(String name) throws Exception{return javax.imageio.ImageIO.read(ELITE.resolve(name).toFile());}

    @Test void everyRingTextureCarriesItsKindsColour() throws Exception{
        for(var kind:Elites.Kind.values())for(int level=1;level<=5;level++){
            int code=Elites.code(kind,level);var ring=image("ring_"+Elites.ring(code)+".png");
            assertEquals(64,ring.getWidth());assertEquals(72,ring.getHeight());
            int ringPixel=ring.getRGB(32,2)&0xFFFFFF,spark=ring.getRGB(32,67)&0xFFFFFF;
            assertEquals(Elites.color(code),ringPixel,kind+" "+level+": the ring's own pixels are its colour");
            assertEquals(Elites.color(code),spark,kind+" "+level+": and so is the spark strip");
            assertEquals(0,ring.getRGB(0,0)>>>24,"the corners are clear");
        }
        assertEquals("badass_top",Elites.ring(Elites.code(Elites.Kind.BADASS,4)));assertEquals("badass",Elites.ring(Elites.code(Elites.Kind.BADASS,3)));
    }
    @Test void theTextureToolKnowsTheSameColours() throws Exception{
        String tool=Files.readString(Path.of("../../tools/art/elite_textures.py"));
        for(var kind:Elites.Kind.values()){
            var m=Pattern.compile("\""+kind.id+"\": 0x([0-9A-Fa-f]{6})").matcher(tool);
            assertTrue(m.find(),kind.id);assertEquals(kind.color,Integer.parseInt(m.group(1),16),kind.id);
        }
        assertTrue(tool.contains("\"badass_top\": 0xFF3B30"));
    }
    @Test void theHealthBarIsRedAndNoColourDependsOnATint() throws Exception{
        var fill=image("bar_fill.png").getRGB(8,1);int red=fill>>16&255,green=fill>>8&255,blue=fill&255;
        assertTrue(red>200&&green<60&&blue<60,"red fill: "+Integer.toHexString(fill));
        var track=image("bar_track.png").getRGB(8,1);assertTrue((track>>16&255)<60&&(track>>>24)==255,"dark, solid track");
        String client=source("EliteClient.java");
        assertTrue(client.contains("RenderType.entityCutoutNoCull(TRACK)")&&client.contains("RenderType.entityCutoutNoCull(FILL)"),"drawn like the raid mark, which shaders keep red");
        assertFalse(client.contains("Elites.color("),"no kind colour passed as a vertex tint");
        assertFalse(Files.exists(Path.of("src/main/resources/assets/eternal_defense/textures/entity/elite_ring.png")),"the white ring is gone");
    }
    @Test void badassSlowdownIsHalved(){
        assertEquals(.125,Elites.slowdown(5,false),1e-9,"12.5% at V");assertEquals(.1875,Elites.slowdown(5,true),1e-9,"a creeper 18.75% at V");
        assertEquals(.025,Elites.slowdown(1,false),1e-9);assertEquals(.0375,Elites.slowdown(1,true),1e-9);
    }
    @Test void aGiantsHeadIsWhereItIsDrawn() throws Exception{
        String elites=source("Elites.java");
        assertTrue(elites.contains("float eye=e.getNewEyeHeight();e.setNewSize(e.getNewSize().scale(s),false);e.setNewEyeHeight(eye*s);"),"the eyes grow with the body");
        float eye=1.74f*5;   // a Badass V zombie
        assertTrue(GiantHeads.band(8.0,eye,5),"the giant's face");assertTrue(GiantHeads.band(9.8,eye,5),"the top of its head");
        assertFalse(GiantHeads.band(1.74,eye,5),"its shins are no head");assertFalse(GiantHeads.band(7.3,eye,5),"nor its chest");
        assertTrue(GiantHeads.band(1.74,1.74f,1));assertFalse(GiantHeads.band(2.0,1.74f,1),"TaCZ's own quarter block for a normal zombie");
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/eternal_defense.mixins.json"))).getAsJsonObject();
        assertTrue(config.getAsJsonArray("mixins").toString().contains("\"GiantHeadMixin\""),"server side, where hits are judged");
        String mixin=Release118Test.mixin("GiantHeadMixin.java");
        assertTrue(mixin.contains("targets=\"com.tacz.guns.util.EntityUtil\"")&&mixin.contains("method=\"getHitResult\"")&&mixin.contains("require=0"));
        assertTrue(source("GiantHeads.java").contains("box.contains(rel.scale(1/s))"),"a head box from TaCZ's config grows too");
    }
    @Test void theFirstRaidHasOneEliteAndOnlyBadassI() throws Exception{
        for(int chosen=1;chosen<=Rules.waves(0);chosen++){int total=0;for(int w=1;w<=Rules.waves(0);w++)total+=Elites.introCount(w,chosen);assertEquals(1,total,"one elite in the whole first raid");}
        for(double roll=0;roll<1;roll+=.01)assertEquals(1,Elites.badassLevel(true,10,roll),"Badass I only, whatever the level");
        assertEquals(5,Elites.badassLevel(false,10,.999),"other raids keep their odds");
        assertTrue(source("EternalDefense.java").contains("d.eliteLeft=d.introRaid?Elites.introCount(d.wave,d.eliteWave):Elites.count("));
        String roll=source("Elites.java");
        assertTrue(roll.contains("if(!pick(d.eliteLeft,d.spawnRemaining,l.random.nextDouble()))return null;"),"no longer skipped in the first raid");
        assertTrue(roll.contains("badassLevel(d.introRaid,d.raidTier,l.random.nextDouble())"));
        assertTrue(text("guide.raids.body").contains("Automatic in every raid. In the first raid a Badass is always level I."));
        assertTrue(text("guide.raids.detail").contains("2.5%% slower a level (12.5%% at V); a creeper 3.75%% a level (18.75%% at V)"));
    }
}
