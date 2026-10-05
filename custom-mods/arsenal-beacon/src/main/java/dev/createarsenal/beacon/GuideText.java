package dev.createarsenal.beacon;

import java.util.function.Function;

/** Field-guide page assembly without any Minecraft types, so both editions can be unit tested. */
final class GuideText {
    private GuideText(){}
    static final String[] IDS={"start","zone","raids","control","upgrades","repair","stations","coop"};
    /**
     * Shared text first, then the edition-specific part, so each flavor keeps only its own instructions.
     * {@code lookup} maps a key below {@code gui.arsenal_beacon.} to its text, or null when absent.
     */
    static String raw(String id,String part,boolean standalone,Function<String,String> lookup){
        var out=new StringBuilder();
        for(String suffix:new String[]{"",standalone?".standalone":".pack"}){
            String text=lookup.apply("guide."+id+"."+part+suffix);
            if(text!=null&&!text.isEmpty()){if(out.length()>0)out.append("\n\n");out.append(text);}
        }
        return out.toString();
    }
}
