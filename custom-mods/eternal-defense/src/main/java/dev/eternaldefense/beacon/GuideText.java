package dev.eternaldefense.beacon;

import java.util.function.Function;

/** Field-guide page assembly without any Minecraft types, so it can be unit tested. (0.0.41: one edition only; the [pack]/[standalone] line markers and .pack/.standalone keys are gone.) */
final class GuideText {
    private GuideText(){}
    static final String[] IDS={"start","zone","raids","control","upgrades","repair","stations","hub","energy","support","tactical","fob","kitchen","mixes","drinks","mre","gear","coop","reference"};
    static final String REFERENCE="reference";
    /** The text of one part of a card ({@code body}, {@code detail}, {@code title}); {@code lookup} maps a key below {@code gui.eternal_defense.} to its text, or null. */
    static String raw(String id,String part,Function<String,String> lookup){
        String text=lookup.apply("guide."+id+"."+part);
        return text==null?"":text;
    }

    /**
     * Page text: its own body, plus for the Reference tab every other page's detail section.
     * Details used to hide behind a toggle; they now live together in one tab.
     */
    static String page(String id,Function<String,String> lookup){
        String body=raw(id,"body",lookup);
        if(!id.equals(REFERENCE))return body;
        var out=new StringBuilder(body);
        for(String other:IDS){
            if(other.equals(REFERENCE))continue;
            String detail=raw(other,"detail",lookup);
            if(detail.isEmpty())continue;
            String title=lookup.apply("guide."+other+".title");
            out.append("\n\n# ").append(title==null?other:title).append("\n").append(detail);
        }
        return out.toString();
    }
}
