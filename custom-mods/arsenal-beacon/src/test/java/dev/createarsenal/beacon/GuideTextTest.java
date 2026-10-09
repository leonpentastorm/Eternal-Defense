package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GuideTextTest {
    private static String raw(Map<String,String> lang,String id,String part,boolean standalone){return GuideText.raw(id,part,standalone,lang::get);}

    @Test void editionLinesReachOnlyTheirOwnEdition(){
        var lang=Map.of("guide.a.body","# Card\n- Shared: yes\n[pack] - Pack only: yes\n[standalone] - Standalone only: yes\n- Shared again: yes");
        assertEquals("# Card\n- Shared: yes\n- Pack only: yes\n- Shared again: yes",raw(lang,"a","body",false));
        assertEquals("# Card\n- Shared: yes\n- Standalone only: yes\n- Shared again: yes",raw(lang,"a","body",true));
    }

    @Test void markersInTheMiddleOfALineAreLeftAlone(){
        assertEquals("- Press [pack] to start",GuideText.forEdition("- Press [pack] to start",true));
        assertEquals("a [x] b",GuideText.forEdition("a [x] b",false));
    }

    @Test void editionSuffixKeysStillWorkAndBulletsContinueTheList(){
        var lang=Map.of("guide.a.body","# Card\n- One: 1","guide.a.body.pack","- Two: 2","guide.a.body.standalone","# Other\n- Three: 3");
        assertEquals("# Card\n- One: 1\n- Two: 2",raw(lang,"a","body",false),"a bullet continues the list without a gap");
        assertEquals("# Card\n- One: 1\n\n# Other\n- Three: 3",raw(lang,"a","body",true),"a heading starts a new paragraph");
    }

    @Test void referencePageCollectsEveryDetailInTheViewersEdition(){
        var lang=Map.of("guide.reference.body","~ Small print","guide.start.title","Start","guide.start.detail","- Both\n[pack] - Pack\n[standalone] - Standalone");
        String pack=GuideText.page("reference",false,lang::get),standalone=GuideText.page("reference",true,lang::get);
        assertTrue(pack.contains("# Start\n- Both\n- Pack")&&!pack.contains("Standalone"));
        assertTrue(standalone.contains("# Start\n- Both\n- Standalone")&&!standalone.contains("Pack"));
    }
}
