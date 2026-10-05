package dev.createarsenal.gunguide;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class GuideActionsTest {
    @Test void remappingAndModifiersAreReadFromTheCurrentBinding(){var old=new GuideActions.Binding("key.tacz.reload.desc","R","NONE:R",false,"Reload");var edited=new GuideActions.Binding(old.name(),"Ctrl + F","CONTROL:F",false,"Reload");assertEquals("R",GuideActions.rows(List.of(old),false).get(0).key());assertEquals("Ctrl + F",GuideActions.rows(List.of(edited),false).get(0).key());}
    @Test void actualJamStateChangesInspectToRepairAndBack(){var key=new GuideActions.Binding("key.tacz.inspect.desc","N","NONE:N",false,"Inspect");assertEquals("Clear jam",GuideActions.rows(List.of(key),true).get(0).label());assertEquals("Inspect",GuideActions.rows(List.of(key),false).get(0).label());}
    @Test void unboundAndWeaponConflictsAreVisibleWithoutVanillaFalsePositives(){var reload=new GuideActions.Binding("key.tacz.reload.desc","R","NONE:R",false,"Reload");var inspect=new GuideActions.Binding("key.tacz.inspect.desc","R","NONE:R",false,"Inspect");var vanilla=new GuideActions.Binding("key.use","R","NONE:R",false,"Use");assertTrue(GuideActions.rows(List.of(reload,inspect),false).stream().allMatch(GuideActions.Row::conflict));assertFalse(GuideActions.rows(List.of(reload,vanilla),false).get(0).conflict());var unbound=new GuideActions.Binding(inspect.name(),"?","NONE:UNKNOWN",true,"Inspect");assertEquals("Unbound",GuideActions.rows(List.of(unbound),false).get(0).key());assertFalse(GuideActions.rows(List.of(unbound),false).get(0).conflict());}
}
