package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
/** Read live mappings each frame: changing Controls never leaves stale shortcut labels. */
final class ControlHints {
    static String key(String name){
        for(var binding:Minecraft.getInstance().options.keyMappings)if(binding.getName().equals(name))return binding.isUnbound()?"Unbound":binding.getTranslatedKeyMessage().getString();
        return "Unbound";
    }
    static String interact(){return key("key.tacz.interact.desc");}
    static String jei(){if(!net.minecraftforge.fml.ModList.get().isLoaded("jei"))return "Install JEI for recipe shortcuts";return ArsenalJei.runtime==null?"JEI shortcuts unavailable":"JEI: Recipes ["+ArsenalJei.runtime.getKeyMappings().getShowRecipe().getTranslatedKeyMessage().getString()+"] / Uses ["+ArsenalJei.runtime.getKeyMappings().getShowUses().getTranslatedKeyMessage().getString()+"]";}
    static String guide(String text){return text.replace("press H","press ["+interact()+"]").replace("(default R / U)","("+jei()+")")
        .replace("{reload}",key("key.tacz.reload.desc")).replace("{emotes}",key("keybind.name.EMOTE_WHEEL"))
        .replace("{shaders}",key("iris.keybind.reload")).replace("{backpack}",key("key.sophisticatedbackpacks.open_backpack"))
        .replace("{quests}",key("key.ftbquests.quests")).replace("{map}",key("gui.xaero_open_map"));}
}
