package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.17: the Support Platform's shop list scrolls, with rows tall enough for the name and the note. */
final class Release1117Test {
    @Test void fullRowsAndAScrollingList(){
        assertEquals(26,SupportScreen.ROW,"name at +4 and note at +14 fit in a 24-pixel row");
        assertEquals(4,SupportScreen.visibleRows());
        assertTrue(SupportScreen.LIST_Y+SupportScreen.visibleRows()*SupportScreen.ROW+2+9<=SupportScreen.H-30,"the scroll line sits above the Buy button");
        assertEquals(SupportShop.PRODUCTS.size()-4,SupportScreen.maxScroll(),"every product can be reached");
        assertTrue(SupportShop.PRODUCTS.size()>SupportScreen.visibleRows(),"six products, four rows: the list scrolls");
    }
    @Test void wiring() throws Exception{
        String screen=Release114Test.source("SupportScreen.java");
        assertTrue(screen.contains("b->selected=scroll+index")&&screen.contains("paintProduct(g,self,scroll+index)"),"rows show and pick the product at the scroll position");
        assertTrue(screen.contains("@Override public boolean mouseScrolled(")&&screen.contains("dragging=true;scrollTo(my)"),"the wheel, the thumb and the track");
        assertFalse(screen.contains("rowStep()"),"rows no longer shrink to fit every product");
        String text=Release114Test.text("support.scroll");
        assertTrue(text.contains("%1$s")&&text.contains("%2$s")&&text.contains("%3$s"));
    }
}
