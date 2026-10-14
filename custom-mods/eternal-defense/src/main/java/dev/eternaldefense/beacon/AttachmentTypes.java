package dev.eternaldefense.beacon;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/** The attachment table's types: TaCZ's own attachment type of each item. */
final class AttachmentTypes {
    private AttachmentTypes(){}
    static final List<String> TYPES=List.of("all","sight","muzzle","stock","grip","laser","extended_mag","other");
    static String normalize(String type){return TYPES.contains(type)?type:"all";}
    /** Pure: the list's name for a TaCZ attachment type name (SCOPE, MUZZLE, ...); anything else is other. */
    static String of(String taczType){
        return switch(taczType==null?"":taczType){case "SCOPE"->"sight";case "MUZZLE"->"muzzle";case "STOCK"->"stock";case "GRIP"->"grip";case "LASER"->"laser";case "EXTENDED_MAG"->"extended_mag";default->"other";};
    }
    static String of(ItemStack stack){var a=com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(stack);return a==null?"other":of(a.getType(stack).name());}
}
