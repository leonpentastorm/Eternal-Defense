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
    /** Keys of mods a pack may or may not have: a guide line naming one is left out when that mod's key does not exist (0.0.41). */
    static final java.util.Map<String,String> OPTIONAL_KEYS=java.util.Map.of("{emotes}","keybind.name.EMOTE_WHEEL","{shaders}","iris.keybind.reload",
        "{backpack}","key.sophisticatedbackpacks.open_backpack","{quests}","key.ftbquests.quests","{map}","gui.xaero_open_map");
    static boolean exists(String name){for(var binding:Minecraft.getInstance().options.keyMappings)if(binding.getName().equals(name))return true;return false;}
    /** Pure: the lines of {@code text} without those that name a token whose key {@code present} says is missing. */
    static String withoutMissing(String text,java.util.function.Predicate<String> present){
        if(text.indexOf('{')<0)return text;
        var kept=new java.util.ArrayList<String>();
        for(String line:text.split("\n",-1)){boolean keep=true;for(var e:OPTIONAL_KEYS.entrySet())if(line.contains(e.getKey())&&!present.test(e.getValue()))keep=false;if(keep)kept.add(line);}
        return String.join("\n",kept);
    }
    /** Replaces {tokens} in language-file copy with the player's current bindings. Nothing is baked in. */
    static String guide(String text){return withoutMissing(text,ControlHints::exists).replace("{interact}",interact()).replace("{jei}",jei())
        .replace("{reload}",key("key.tacz.reload.desc")).replace("{emotes}",key("keybind.name.EMOTE_WHEEL"))
        .replace("{shaders}",key("iris.keybind.reload")).replace("{backpack}",key("key.sophisticatedbackpacks.open_backpack"))
        .replace("{quests}",key("key.ftbquests.quests")).replace("{map}",key("gui.xaero_open_map"));}
}
