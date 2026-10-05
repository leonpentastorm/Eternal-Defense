package dev.createarsenal.beacon;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

/** One-time pack defaults, followed by a readable inventory of every registered control. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class ClientDefaults {
    private static boolean checked;
    private static final String REVISION="playtest-1";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private record Binding(KeyModifier modifier,String key) {
        String id(){return modifier+":"+key;}
        void apply(KeyMapping mapping){mapping.setKeyModifierAndCode(modifier,InputConstants.getKey(key));}
    }
    private static Binding key(String key){return new Binding(KeyModifier.NONE,key);}
    private static Binding keyboard(String key){return key("key.keyboard."+key);}
    private static final Map<String,Binding> PREFERRED=new LinkedHashMap<>();
    static {
        PREFERRED.put("key.tacz.reload.desc",keyboard("r"));
        PREFERRED.put("key.tacz.fire_select.desc",keyboard("v"));
        PREFERRED.put("key.tacz.melee.desc",keyboard("g"));
        PREFERRED.put("key.tacz.inspect.desc",keyboard("n"));
        PREFERRED.put("key.tacz.refit.desc",keyboard("j"));
        PREFERRED.put("key.tacz.crawl.desc",keyboard("z"));
        PREFERRED.put("key.tacz.interact.desc",keyboard("h"));
        PREFERRED.put("key.tacz.zoom.desc",keyboard("c"));
        PREFERRED.put("key.tacz.open_config.desc",new Binding(KeyModifier.CONTROL,"key.keyboard.k"));
        PREFERRED.put("key.tactical.leanleft",new Binding(KeyModifier.ALT,"key.keyboard.q"));
        PREFERRED.put("key.tactical.leanright",new Binding(KeyModifier.ALT,"key.keyboard.e"));
        PREFERRED.put("key.advancements",keyboard("f7"));
    }
    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent e) {
        if(BuildFlavor.STANDALONE||checked||e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();if(mc.options==null||mc.options.keyMappings.length<20)return;
        checked=true;Path directory=FMLPaths.CONFIGDIR.get();Path marker=directory.resolve("arsenal-controls-"+REVISION+".applied");
        try {
            List<KeyMapping> mappings=new ArrayList<>(Arrays.asList(mc.options.keyMappings));
            mappings.sort(Comparator.comparing(KeyMapping::getName));
            JsonArray original=describe(mappings);
            if(!Files.exists(marker)) {
                Map<String,KeyMapping> names=new LinkedHashMap<>();for(KeyMapping m:mappings)names.put(m.getName(),m);
                Map<KeyMapping,Binding> selected=new LinkedHashMap<>();Set<String> occupied=new HashSet<>();
                // Reserve screenshot, debug, HUD and fullscreen shortcuts handled outside KeyMapping.
                for(String reserved:List.of("f1","f2","f3","f4","f11"))occupied.add(keyboard(reserved).id());
                for(var entry:PREFERRED.entrySet())if(names.containsKey(entry.getKey()))assign(selected,occupied,names.get(entry.getKey()),entry.getValue());
                for(KeyMapping m:mappings) {
                    String name=m.getName().toLowerCase(Locale.ROOT);Binding desired=null;
                    if(name.contains("emote")&&(name.contains("wheel")||name.contains("open")))desired=keyboard("k");
                    else if((name.contains("iris")||name.contains("shader"))&&name.contains("reload"))desired=keyboard("f8");
                    else if(name.contains("backpack")&&name.contains("open"))desired=keyboard("b");
                    else if(name.contains("ftbquests")&&(name.contains("open")||name.endsWith("quests")))desired=keyboard("l");
                    if(desired!=null&&!selected.containsKey(m)&&!occupied.contains(desired.id()))assign(selected,occupied,m,desired);
                }
                // Vanilla movement/inventory keeps its familiar keys. Mouse combat bindings are
                // intentionally shared with weapon handlers, which cancel the vanilla action.
                Set<String> vanilla=new HashSet<>(List.of("key.attack","key.use","key.forward","key.back","key.left","key.right","key.jump","key.sneak","key.sprint","key.inventory","key.drop","key.swapOffhand","key.pickItem","key.chat","key.command","key.playerlist","key.togglePerspective","key.smoothCamera","key.socialInteractions","key.spectatorOutlines","key.fullscreen","key.screenshot"));
                for(int i=1;i<=9;i++)vanilla.add("key.hotbar."+i);
                for(KeyMapping m:mappings)if(vanilla.contains(m.getName())&&!selected.containsKey(m)) {
                    Binding desired=key(m.getDefaultKey().getName());
                    if(!occupied.contains(desired.id())||m.getName().equals("key.fullscreen")||m.getName().equals("key.screenshot"))assign(selected,occupied,m,desired);
                }
                List<Binding> candidates=new ArrayList<>();
                for(KeyModifier mod:List.of(KeyModifier.NONE,KeyModifier.CONTROL,KeyModifier.ALT,KeyModifier.SHIFT))
                    for(String candidate:List.of("f6","f9","f10","f12","u","i","o","p","x","y","m","b","l","k","h","j","n","g","v","c","z","q","e","r","left.bracket","right.bracket","semicolon","apostrophe","comma","period","slash","backslash","minus","equal","grave.accent","home","end","page.up","page.down","insert","delete"))
                        candidates.add(new Binding(mod,"key.keyboard."+candidate));
                for(KeyMapping m:mappings)if(!selected.containsKey(m)) {
                    Binding current=new Binding(m.getKeyModifier(),m.getKey().getName());
                    if(m.getName().equals("key.tacz.shoot.desc")||m.getName().equals("key.lrtactical.normal_attack.desc")){selected.put(m,key("key.mouse.left"));continue;}
                    if(m.getName().equals("key.tacz.aim.desc")){selected.put(m,key("key.mouse.right"));continue;}
                    if(current.key.equals("key.keyboard.unknown")){selected.put(m,current);continue;}
                    if(!occupied.contains(current.id()))assign(selected,occupied,m,current);
                    else {
                        Binding replacement=candidates.stream().filter(b->!occupied.contains(b.id())).findFirst().orElseThrow(()->new IllegalStateException("No free key for "+m.getName()));
                        assign(selected,occupied,m,replacement);
                    }
                }
                selected.forEach((m,b)->b.apply(m));KeyMapping.resetMapping();mc.options.save();
                Files.writeString(directory.resolve("arsenal-controls-original.json"),JSON.toJson(original));
                Files.writeString(marker,"Applied Create Arsenal defaults once. Later player edits are preserved.\n");
                com.mojang.logging.LogUtils.getLogger().info("[Create Arsenal] Applied conflict-free keyboard defaults to {} registered controls",mappings.size());
            }
            Files.writeString(directory.resolve("arsenal-controls.json"),JSON.toJson(describe(mappings)));
        } catch(Exception failure) {com.mojang.logging.LogUtils.getLogger().error("Create Arsenal could not install client controls",failure);}
    }
    private static void assign(Map<KeyMapping,Binding> selected,Set<String> occupied,KeyMapping mapping,Binding binding){selected.put(mapping,binding);occupied.add(binding.id());}
    private static JsonArray describe(List<KeyMapping> mappings) {
        JsonArray array=new JsonArray();for(KeyMapping m:mappings){JsonObject obj=new JsonObject();obj.addProperty("name",m.getName());obj.addProperty("category",m.getCategory());obj.addProperty("key",m.getKey().getName());obj.addProperty("modifier",m.getKeyModifier().name());array.add(obj);}return array;
    }
    @SubscribeEvent public static void welcome(ClientChatReceivedEvent e) {
        if(BuildFlavor.STANDALONE||!e.isSystem())return;
        String text=e.getMessage().getString().toLowerCase(Locale.ROOT);
        if(text.contains("create arsenal"))return;
        if(text.contains("thank you for downloading")||text.contains("thanks for downloading")||text.startsWith("welcome to ")||text.contains("stardust labs discord"))e.setCanceled(true);
    }
}
