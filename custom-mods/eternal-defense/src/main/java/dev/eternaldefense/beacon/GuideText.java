package dev.eternaldefense.beacon;

import java.util.function.Function;

/** Field-guide page assembly without any Minecraft types, so it can be unit tested. */
final class GuideText {
    private GuideText(){}
    static final String[] IDS={"start","zone","raids","control","upgrades","repair","stations","hub","energy","support","tactical","fob","kitchen","mixes","drinks","mre","gear","coop","reference"};
    static final String REFERENCE="reference";
    /** The text of one part of a card (body, detail, title); lookup maps a key below gui.eternal_defense. */
    static String raw(String id,String part,Function<String,String> lookup){
        String text=lookup.apply("guide."+id+"."+part);
        return text==null?"":text;
    }

    /** Page text: its own body, plus for the Reference tab every other page's detail section. */
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
