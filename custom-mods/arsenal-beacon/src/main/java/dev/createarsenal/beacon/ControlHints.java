package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
/** Read live mappings each frame: changing Controls never leaves stale shortcut labels. */
final class ControlHints {
    static String key(String name){
        for(var binding:Minecraft.getInstance().options.keyMappings)if(binding.getName().equals(name))return binding.isUnbound()?Ui.plain("unbound"):binding.getTranslatedKeyMessage().getString();
        return Ui.plain("unbound");
    }
    static String interact(){return key("key.tacz.interact.desc");}
    static String jei(){
        if(!net.minecraftforge.fml.ModList.get().isLoaded("jei"))return Ui.plain("jei.missing");
        return ArsenalJei.runtime==null?Ui.plain("jei.unavailable"):Ui.plain("jei.shortcuts",ArsenalJei.runtime.getKeyMappings().getShowRecipe().getTranslatedKeyMessage().getString(),ArsenalJei.runtime.getKeyMappings().getShowUses().getTranslatedKeyMessage().getString());
    }
    /** Replaces {tokens} in language-file copy with the player's current bindings. Nothing is baked in. */
    static String guide(String text){return text.replace("{interact}",interact()).replace("{jei}",jei())
        .replace("{reload}",key("key.tacz.reload.desc")).replace("{emotes}",key("keybind.name.EMOTE_WHEEL"))
        .replace("{shaders}",key("iris.keybind.reload")).replace("{backpack}",key("key.sophisticatedbackpacks.open_backpack"))
        .replace("{quests}",key("key.ftbquests.quests")).replace("{map}",key("gui.xaero_open_map"));}
}
