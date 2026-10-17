package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 1.1.1 list: attachment types, the Exchange's scroll bar, the Support shop's full texts, the pot's eating sound, the sandwich hint. */
final class Release111Test {
    static String source(String name) throws Exception{return Files.readString(Path.of("src/main/java/dev/eternaldefense/beacon/"+name));}
    static com.google.gson.JsonObject lang() throws Exception{return com.google.gson.JsonParser.parseString(Round0035Test.lang()).getAsJsonObject();}

    @Test void theAttachmentTableSortsByTaczsOwnTypes() throws Exception{
        assertEquals(List.of("all","sight","muzzle","stock","grip","laser","extended_mag","other"),AttachmentTypes.TYPES);
        assertEquals("sight",AttachmentTypes.of("SCOPE"));assertEquals("muzzle",AttachmentTypes.of("MUZZLE"));assertEquals("stock",AttachmentTypes.of("STOCK"));
        assertEquals("grip",AttachmentTypes.of("GRIP"));assertEquals("laser",AttachmentTypes.of("LASER"));assertEquals("extended_mag",AttachmentTypes.of("EXTENDED_MAG"));
        assertEquals("other",AttachmentTypes.of("NONE"));assertEquals("other",AttachmentTypes.of((String)null));
        assertEquals("all",AttachmentTypes.normalize("rifle"),"a gun type means nothing here");
        for(String t:AttachmentTypes.TYPES)assertTrue(t.length()<=16,"the request carries at most 16 characters: "+t);
        var json=lang();for(String t:AttachmentTypes.TYPES)assertTrue(json.has("gui.eternal_defense.type.attachment."+t),t);
        assertTrue(json.has("gui.eternal_defense.platform.type.attachment"));
        String platform=source("WeaponPlatform.java");
        assertTrue(platform.contains("attachments?AttachmentTypes.of(e.output)"),"the server counts and filters attachments by type");
    }
    @Test void theExchangeListHasAScrollBarAndSaysWhereYouAre() throws Exception{
        String screen=source("ExchangeScreen.java");
        assertTrue(screen.contains("private void scrollTo(double my)")&&screen.contains("dragging=true;scrollTo(my)"),"drag the thumb or click the track");
        assertFalse(screen.contains("top+38,Ui.MUTED"),"the count no longer hides behind the Buy and Sell tabs");
        String text=lang().get("gui.eternal_defense.exchange.scroll").getAsString();
        assertTrue(text.contains("%1$s")&&text.contains("%2$s")&&text.contains("%3$s"));
    }
    @Test void theSupportShopShowsAProductsWholeTextOnHover() throws Exception{
        String screen=source("SupportScreen.java");
        assertTrue(screen.contains("productTip(scroll+i)")&&screen.contains("support.product.\"+product.id()+\".note"),"the note in full, then the item's own lines");
    }
    @Test void everyFabricationCardHasANameAndADescription() throws Exception{
        var json=lang();
        for(String id:WorkshopFabrication.ITEMS){
            assertTrue(json.has("gui.eternal_defense.fabrication.name."+id),"the card of "+id+" needs a name (the raw key showed on the Loadout Station)");
            assertTrue(json.has("gui.eternal_defense.fabrication.desc."+id),"the card of "+id+" needs a description");
        }
    }
    @Test void eatingFromThePotSoundsLikeEating() throws Exception{
        assertTrue(source("CookPot.java").contains("SoundEvents.GENERIC_EAT"));
    }
    @Test void theSandwichHintClearsTheMakeButton() throws Exception{
        String screen=source("MessHallScreen.java");
        assertTrue(screen.contains("Ui.t(\"kitchen.sandwich_hint\"),leftPos+LX,topPos+ZONE_Y-1,LW,Ui.MUTED,2)"));
        // Two lines of 11 px from ZONE_Y-1 end before the Make button
        assertTrue(MessHallScreen.ZONE_Y-1+11+9<=MessHallScreen.PRIMARY_Y);
    }
}
