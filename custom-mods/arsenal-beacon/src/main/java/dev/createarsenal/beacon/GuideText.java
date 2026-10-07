package dev.createarsenal.beacon;

import java.util.function.Function;

/** Field-guide page assembly without any Minecraft types, so both editions can be unit tested. */
final class GuideText {
    private GuideText(){}
    static final String[] IDS={"start","zone","raids","control","upgrades","repair","stations","energy","support","kitchen","coop","reference"};
    static final String REFERENCE="reference";
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

    /**
     * Page text: its own body, plus for the Reference tab every other page's detail section.
     * Details used to hide behind a toggle; they now live together in one tab.
     */
    static String page(String id,boolean standalone,Function<String,String> lookup){
        String body=raw(id,"body",standalone,lookup);
        if(!id.equals(REFERENCE))return body;
        var out=new StringBuilder(body);
        for(String other:IDS){
            if(other.equals(REFERENCE))continue;
            String detail=raw(other,"detail",standalone,lookup);
            if(detail.isEmpty())continue;
            String title=lookup.apply("guide."+other+".title");
            out.append("\n\n# ").append(title==null?other:title).append("\n").append(detail);
        }
        return out.toString();
    }
}
