package dev.createarsenal.beacon;

import java.util.ArrayList;
import java.util.function.Function;

/** Field-guide page assembly without any Minecraft types, so both editions can be unit tested. */
final class GuideText {
    private GuideText(){}
    static final String[] IDS={"start","zone","raids","control","upgrades","repair","stations","energy","support","tactical","kitchen","mixes","gear","coop","reference"};
    static final String REFERENCE="reference";
    static final String PACK_ONLY="[pack] ",STANDALONE_ONLY="[standalone] ";
    /**
     * Shared text first, then the edition-specific part, so each flavor keeps only its own instructions.
     * {@code lookup} maps a key below {@code gui.arsenal_beacon.} to its text, or null when absent.
     * A line that starts with {@code [pack] } or {@code [standalone] } is kept for that edition only, which lets one
     * feature card carry its own edition difference instead of pushing it to the end of the page.
     */
    static String raw(String id,String part,boolean standalone,Function<String,String> lookup){
        var out=new StringBuilder();
        for(String suffix:new String[]{"",standalone?".standalone":".pack"}){
            String text=lookup.apply("guide."+id+"."+part+suffix);
            if(text!=null&&!text.isEmpty()){
                // A bullet or note that continues the list above stays in it; anything else starts a new paragraph.
                if(out.length()>0)out.append(text.startsWith("- ")||text.startsWith("~ ")?"\n":"\n\n");
                out.append(text);
            }
        }
        return forEdition(out.toString(),standalone);
    }

    /** Drops the lines meant for the other edition and strips the marker from the ones that stay. */
    static String forEdition(String text,boolean standalone){
        if(text.indexOf('[')<0)return text;
        var kept=new ArrayList<String>();
        for(String line:text.split("\n",-1)){
            if(line.startsWith(PACK_ONLY)){if(!standalone)kept.add(line.substring(PACK_ONLY.length()));}
            else if(line.startsWith(STANDALONE_ONLY)){if(standalone)kept.add(line.substring(STANDALONE_ONLY.length()));}
            else kept.add(line);
        }
        return String.join("\n",kept);
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
