package dev.eternaldefense.gunguide;

import java.util.*;

/** Pure presentation rules; bindings and weapon state are supplied live by the client. */
public final class GuideActions {
    private static final Map<String,String> LABELS=Map.ofEntries(
        Map.entry("key.tacz.shoot.desc","Fire"),Map.entry("key.tacz.aim.desc","Aim"),Map.entry("key.tacz.reload.desc","Reload"),
        Map.entry("key.tacz.fire_select.desc","Fire mode"),Map.entry("key.tacz.melee.desc","Melee"),Map.entry("key.tacz.inspect.desc","Inspect"),
        Map.entry("key.tacz.refit.desc","Attachments"),Map.entry("key.tacz.crawl.desc","Prone"),Map.entry("key.tacz.interact.desc","Interact"),
        Map.entry("key.tacz.zoom.desc","Scope zoom"),Map.entry("key.tacz.open_config.desc","Gun settings"),
        Map.entry("key.tactical.leanleft","Lean left"),Map.entry("key.tactical.leanright","Lean right"),
        Map.entry("key.lrtactical.sp_attack.desc","Special attack"),Map.entry("key.taczadditions.toggle_laser","Toggle laser"),Map.entry("key.taczmagazines.unload_mag","Unload magazine"));
    /** Each kind of key and the mod it belongs to: listed only while that mod is loaded */
    private static final Map<String,String> OWNERS=Map.of("key.tacz.","tacz","key.lrtactical.","lrtactical","key.tactical.","tacticalmovement","key.taczadditions.","taczadditions","key.taczmagazines.","taczmagazines");
    /** The plain attack button is not a gun control */
    private static final Set<String> LEFT_OUT=Set.of("key.lrtactical.normal_attack.desc");
    public static String owner(String name){for(var e:OWNERS.entrySet())if(name.startsWith(e.getKey()))return e.getValue();return null;}
    public static boolean relevant(String name){return owner(name)!=null&&!LEFT_OUT.contains(name);}
    public static boolean relevant(String name,java.util.function.Predicate<String> loaded){return relevant(name)&&loaded.test(owner(name));}
    public static String label(String name,String translated,boolean jammed){return name.equals("key.tacz.inspect.desc")&&jammed?"Clear jam":LABELS.getOrDefault(name,translated);}
    public record Binding(String name,String key,String identity,boolean unbound,String translated){}
    public record Row(String name,String key,String label,boolean unbound,boolean conflict){}
    public static List<Row> rows(List<Binding> bindings,boolean jammed){
        List<Binding> relevant=bindings.stream().filter(b->relevant(b.name)).toList();var counts=new HashMap<String,Integer>();for(var b:relevant)if(!b.unbound)counts.merge(b.identity,1,Integer::sum);
        return relevant.stream().map(b->new Row(b.name,b.unbound?"Unbound":b.key,label(b.name,b.translated,jammed),b.unbound,!b.unbound&&counts.getOrDefault(b.identity,0)>1)).toList();
    }
}
