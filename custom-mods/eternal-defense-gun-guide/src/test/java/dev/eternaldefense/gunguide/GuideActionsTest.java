package dev.eternaldefense.gunguide;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class GuideActionsTest {
    @Test void remappingAndModifiersAreReadFromTheCurrentBinding(){var old=new GuideActions.Binding("key.tacz.reload.desc","R","NONE:R",false,"Reload");var edited=new GuideActions.Binding(old.name(),"Ctrl + F","CONTROL:F",false,"Reload");assertEquals("R",GuideActions.rows(List.of(old),false).get(0).key());assertEquals("Ctrl + F",GuideActions.rows(List.of(edited),false).get(0).key());}
    @Test void actualJamStateChangesInspectToRepairAndBack(){var key=new GuideActions.Binding("key.tacz.inspect.desc","N","NONE:N",false,"Inspect");assertEquals("Clear jam",GuideActions.rows(List.of(key),true).get(0).label());assertEquals("Inspect",GuideActions.rows(List.of(key),false).get(0).label());}
    @Test void unboundAndWeaponConflictsAreVisibleWithoutVanillaFalsePositives(){var reload=new GuideActions.Binding("key.tacz.reload.desc","R","NONE:R",false,"Reload");var inspect=new GuideActions.Binding("key.tacz.inspect.desc","R","NONE:R",false,"Inspect");var vanilla=new GuideActions.Binding("key.use","R","NONE:R",false,"Use");assertTrue(GuideActions.rows(List.of(reload,inspect),false).stream().allMatch(GuideActions.Row::conflict));assertFalse(GuideActions.rows(List.of(reload,vanilla),false).get(0).conflict());var unbound=new GuideActions.Binding(inspect.name(),"?","NONE:UNKNOWN",true,"Inspect");assertEquals("Unbound",GuideActions.rows(List.of(unbound),false).get(0).key());assertFalse(GuideActions.rows(List.of(unbound),false).get(0).conflict());}
    @Test void addOnKeysShowOnlyWhileTheirModIsLoadedAndTheAttackButtonNever(){
        java.util.function.Predicate<String> none=id->id.equals("tacz"),all=id->true;
        for(String name:List.of("key.taczadditions.toggle_laser","key.taczmagazines.unload_mag","key.tactical.leanleft","key.tactical.leanright")){assertTrue(GuideActions.relevant(name,all),name);assertFalse(GuideActions.relevant(name,none),name+" without its mod");}
        assertEquals("taczadditions",GuideActions.owner("key.taczadditions.toggle_laser"));assertEquals("taczmagazines",GuideActions.owner("key.taczmagazines.unload_mag"));assertEquals("tacticalmovement",GuideActions.owner("key.tactical.leanleft"));assertEquals("tacz",GuideActions.owner("key.tacz.reload.desc"));
        assertFalse(GuideActions.relevant("key.lrtactical.normal_attack.desc",all),"Normal Attack is the plain attack button");assertTrue(GuideActions.relevant("key.lrtactical.sp_attack.desc",all));assertEquals("Special attack",GuideActions.label("key.lrtactical.sp_attack.desc","Special Attack/Action",false));
        assertFalse(GuideActions.relevant("key.use",all));
        var laser=new GuideActions.Binding("key.taczadditions.toggle_laser","L","NONE:L",false,"Toggle Laser");var unload=new GuideActions.Binding("key.taczmagazines.unload_mag","U","NONE:U",false,"Unload Magazine");var attack=new GuideActions.Binding("key.lrtactical.normal_attack.desc","Left Button","NONE:MOUSE0",false,"Normal Attack");
        var rows=GuideActions.rows(List.of(laser,unload,attack),false);assertEquals(List.of("Toggle laser","Unload magazine"),rows.stream().map(GuideActions.Row::label).toList());
    }
}
