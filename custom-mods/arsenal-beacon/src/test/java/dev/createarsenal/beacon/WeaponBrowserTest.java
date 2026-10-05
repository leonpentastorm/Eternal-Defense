package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class WeaponBrowserTest {
    @Test void largeWindowsExposeMoreRecipesWithoutLeavingTheViewport(){
        for(int[] viewport:new int[][]{{320,240},{640,360},{854,480},{1280,720},{1920,1080}}){
            for(boolean weapons:new boolean[]{true,false}){var l=WeaponBrowser.layout(viewport[0],viewport[1],weapons);assertTrue(l.width()<=viewport[0]-16&&l.height()<=viewport[1]-16);assertTrue(l.capacity()<=40&&l.capacity()>=1);assertTrue(l.listTop()+((l.capacity()+l.columns()-1)/l.columns())*l.rowHeight()<=l.height()-34,"Visible cards stay above navigation at every supported GUI scale");assertTrue(l.cellWidth()>=120);}
        }
        assertEquals(2,WeaponBrowser.layout(854,480,true).columns());assertTrue(WeaponBrowser.layout(854,480,true).capacity()>=20);assertEquals(40,WeaponBrowser.layout(1280,720,true).capacity());
    }
    @Test void EmptyListsAndUntrustedNavigationCannotOverflow(){assertEquals(0,WeaponBrowser.page(Integer.MAX_VALUE,0,40));assertEquals(0,WeaponBrowser.page(-4,598,12));assertEquals(49,WeaponBrowser.page(Integer.MAX_VALUE,598,12));assertEquals(40,WeaponBrowser.size(Integer.MAX_VALUE));assertEquals(1,WeaponBrowser.size(-10));}
    @Test void typeNamesFollowNativeCategoriesAndInvalidFiltersFallBack(){assertEquals("mg",WeaponBrowser.nativeType("MG"));assertEquals("other",WeaponBrowser.nativeType("unfamiliar_author_type"));assertEquals("all",WeaponBrowser.normalize("bad"));assertEquals("Machine guns",WeaponBrowser.label("mg"));assertEquals("Launchers",WeaponBrowser.label("rpg"));}
}
