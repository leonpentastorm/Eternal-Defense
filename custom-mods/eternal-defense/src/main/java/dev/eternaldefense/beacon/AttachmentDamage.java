package dev.eternaldefense.beacon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

/** Attachments of add-on gun packs that raise a gun's damage directly (its bullets, their blast, or explosive rounds); lowering it is fine. */
final class AttachmentDamage {
    private AttachmentDamage(){}
    private static final Map<String,Boolean> SEEN=new HashMap<>();
    /** Pure: a damage modifier that adds, raises by percent or multiplies up; a scripted one counts as raising */
    static boolean raises(double addend,double percent,double multiplier,String function){
        if(function!=null&&!function.isBlank())return true;
        return addend>0||percent>0||(multiplier>1);
    }
    /** Pure: an attachment from a pack whose damage attachments are kept (TaCZ's own by default) */
    static boolean kept(String attachmentId,java.util.Set<String> packs){int colon=attachmentId.indexOf(':');return packs.contains(colon<0?"minecraft":attachmentId.substring(0,colon));}
    /** Whether the attachment table may sell it: not when an add-on pack's attachment raises damage */
    static boolean sold(ItemStack output){
        var tag=output.getTag();if(tag==null||!tag.contains("AttachmentId"))return true;
        return kept(tag.getString("AttachmentId"),CraftBalance.settings().keptPacks())||!raises(output);
    }
    static boolean raises(ItemStack output){
        var tag=output.getTag();if(tag==null||!tag.contains("AttachmentId"))return false;
        return SEEN.computeIfAbsent(tag.getString("AttachmentId"),AttachmentDamage::raises);
    }
    private static boolean raises(String id){
        var rl=ResourceLocation.tryParse(id);if(rl==null)return false;
        return com.tacz.guns.api.TimelessAPI.getCommonAttachmentIndex(rl).map(index->{
            var modifiers=index.getData().getModifier();
            if(modifiers==null)return false;
            if(modifiers.get("damage")!=null&&modifiers.get("damage").getValue() instanceof com.tacz.guns.resource.pojo.data.attachment.Modifier m&&raises(m))return true;
            // Explosive rounds switched on add a blast to every hit
            var blast=modifiers.get("explosion");
            if(blast!=null&&blast.getValue() instanceof com.tacz.guns.resource.modifier.custom.ExplosionModifier.ExplosionModifierValue v){
                if(v.isExplode())return true;
                try{var f=v.getClass().getDeclaredField("damage");f.setAccessible(true);if(f.get(v) instanceof com.tacz.guns.resource.pojo.data.attachment.Modifier m&&raises(m))return true;}catch(ReflectiveOperationException ignored){}
            }
            return false;
        }).orElse(false);
    }
    private static boolean raises(com.tacz.guns.resource.pojo.data.attachment.Modifier m){return raises(m.getAddend(),m.getPercent(),m.getMultiplier(),m.getFunction());}
    /** Attachment data can change on a data reload: counted again for each recipe load */
    private static Object countedFor;
    static void fresh(Object recipes){if(countedFor!=recipes){countedFor=recipes;SEEN.clear();}}
}
