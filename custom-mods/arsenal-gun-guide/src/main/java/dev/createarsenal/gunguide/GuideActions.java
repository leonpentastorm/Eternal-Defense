package dev.createarsenal.gunguide;

import java.util.*;

/** Pure presentation rules; bindings and weapon state are supplied live by the client. */
public final class GuideActions {
    private static final Map<String,String> LABELS=Map.ofEntries(
        Map.entry("key.tacz.shoot.desc","Fire"),Map.entry("key.tacz.aim.desc","Aim"),Map.entry("key.tacz.reload.desc","Reload"),
        Map.entry("key.tacz.fire_select.desc","Fire mode"),Map.entry("key.tacz.melee.desc","Melee"),Map.entry("key.tacz.inspect.desc","Inspect"),
        Map.entry("key.tacz.refit.desc","Attachments"),Map.entry("key.tacz.crawl.desc","Prone"),Map.entry("key.tacz.interact.desc","Interact"),
        Map.entry("key.tacz.zoom.desc","Scope zoom"),Map.entry("key.tacz.open_config.desc","Gun settings"),
        Map.entry("key.tactical.leanleft","Lean left"),Map.entry("key.tactical.leanright","Lean right"));
    public static boolean relevant(String name){return name.startsWith("key.tacz.")||name.startsWith("key.lrtactical.")||name.startsWith("key.tactical.");}
    public static String label(String name,String translated,boolean jammed){return name.equals("key.tacz.inspect.desc")&&jammed?"Clear jam":LABELS.getOrDefault(name,translated);}
    public record Binding(String name,String key,String identity,boolean unbound,String translated){}
    public record Row(String name,String key,String label,boolean unbound,boolean conflict){}
    public static List<Row> rows(List<Binding> bindings,boolean jammed){
        List<Binding> relevant=bindings.stream().filter(b->relevant(b.name)).toList();var counts=new HashMap<String,Integer>();for(var b:relevant)if(!b.unbound)counts.merge(b.identity,1,Integer::sum);
        return relevant.stream().map(b->new Row(b.name,b.unbound?"Unbound":b.key,label(b.name,b.translated,jammed),b.unbound,!b.unbound&&counts.getOrDefault(b.identity,0)>1)).toList();
    }
}
