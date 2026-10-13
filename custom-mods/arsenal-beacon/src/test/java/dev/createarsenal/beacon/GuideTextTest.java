package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 0.0.41: one edition only; a card's text is its key's text, nothing is filtered or appended. */
class GuideTextTest {
    @Test void aCardIsItsKeysTextAndAMissingKeyIsEmpty(){
        var lang=Map.of("guide.a.body","# Card\n- One: 1\n- Two: 2");
        assertEquals("# Card\n- One: 1\n- Two: 2",GuideText.raw("a","body",lang::get));
        assertEquals("",GuideText.raw("a","detail",lang::get));
    }

    @Test void referencePageCollectsEveryDetail(){
        var lang=Map.of("guide.reference.body","~ Small print","guide.start.title","Start","guide.start.detail","- Both\n- Pack");
        String page=GuideText.page("reference",lang::get);
        assertTrue(page.startsWith("~ Small print")&&page.contains("# Start\n- Both\n- Pack"));
        assertEquals("~ Small print",GuideText.page("zone",Map.of("guide.zone.body","~ Small print")::get),"other pages are their body alone");
    }

    @Test void noGuideTextCarriesTheOldEditionMarkersOrKeys() throws Exception{
        String lang=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/assets/arsenal_beacon/lang/en_us.json"));
        assertFalse(lang.contains("[pack] ")||lang.contains("[standalone] "),"no line is marked for an edition");
        assertFalse(lang.contains(".pack\":")||lang.contains(".standalone\":"),"no key has an edition suffix");
    }

    @Test void keyHintsForModsThePackDoesNotHaveAreLeftOut(){
        String text="# Pack keys\n- {reload}: reload\n- {emotes}: emote wheel\n- {map}: world map";
        assertEquals("# Pack keys\n- {reload}: reload\n- {map}: world map",ControlHints.withoutMissing(text,key->!key.equals("keybind.name.EMOTE_WHEEL")));
        assertEquals("# Pack keys\n- {reload}: reload",ControlHints.withoutMissing(text,key->false),"TaCZ's reload is always there");
        assertEquals(text,ControlHints.withoutMissing(text,key->true));
    }
}
