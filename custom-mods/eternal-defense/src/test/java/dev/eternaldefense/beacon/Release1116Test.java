package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.16: add-on damage attachments are sold in the first two Ages; from Age 3 on they stay off the table. */
final class Release1116Test {
    @Test void damageAttachmentsOfTheFirstTwoAgesAreSold(){
        assertEquals(2,CraftBalance.settings().keptUpToAge(),"Frontier and World Wars");
        assertTrue(AttachmentDamage.keptAge(1,2));assertTrue(AttachmentDamage.keptAge(2,2));
        assertFalse(AttachmentDamage.keptAge(3,2),"Modern and later stay off");assertFalse(AttachmentDamage.keptAge(5,2));
        assertFalse(AttachmentDamage.keptAge(1,0),"0 keeps none");
    }
    @Test void wiring() throws Exception{
        String platform=Release114Test.source("WeaponPlatform.java");
        int aged=platform.indexOf("list=new ArrayList<>(CraftBalance.aged(list,catalogue().keySet(),kind,recipes));"),removed=platform.indexOf("if(kind.equals(\"attachment\"))list.removeIf(e->!AttachmentDamage.sold(e.output,e.gate.age));");
        assertTrue(aged>0&&removed>aged,"judged at the Age the table shows, hand-set Ages included");
        assertFalse(platform.contains("AttachmentDamage.sold(output)"),"no check before the Age is known");
        assertTrue(Release114Test.source("AttachmentDamage.java").contains("return keptAge(age,s.keptUpToAge())||kept(tag.getString(\"AttachmentId\"),s.keptPacks())||!raises(output);"));
    }
}
