package dev.createarsenal.beacon;

import java.util.*;

/** Shared bounds keep catalogue navigation responsive without sending the whole arsenal. */
final class WeaponBrowser {
    static final int DEFAULT_SIZE=12,MAX_SIZE=40;
    static final List<String> TYPES=List.of("all","pistol","rifle","smg","shotgun","sniper","mg","rpg","other");
    static String normalize(String type){return TYPES.contains(type)?type:"all";}
    static String label(String type){return switch(type){case "pistol"->"Pistols";case "rifle"->"Rifles";case "smg"->"SMGs";case "shotgun"->"Shotguns";case "sniper"->"Snipers";case "mg"->"Machine guns";case "rpg"->"Launchers";case "other"->"Other weapons";default->"All weapon types";};}
    static String nativeType(String type){String lower=type==null?"":type.toLowerCase(Locale.ROOT);return TYPES.contains(lower)&&!lower.equals("all")?lower:"other";}
    static int size(int requested){return Math.max(1,Math.min(MAX_SIZE,requested));}
    static int lastPage(int total,int size){return Math.max(0,(total-1)/size(size));}
    static int page(int requested,int total,int size){return Math.max(0,Math.min(requested,lastPage(total,size)));}
    record Layout(int width,int height,int listWidth,int columns,int rowHeight,int listTop,int capacity,boolean compact){
        int cellWidth(){return (listWidth-12-(columns-1)*4)/columns;}
    }
    static Layout layout(int screenWidth,int screenHeight,boolean weapons){return layout(screenWidth,screenHeight,weapons,false);}
    /**
     * Below 300 px of panel height (the default 854 x 480 window at GUI scale 2) the filter rows are merged so
     * the list keeps room: Age and gear type share a row, and the "browsing" line is dropped.
     */
    static Layout layout(int screenWidth,int screenHeight,boolean weapons,boolean armor){
        int width=Math.min(1120,screenWidth-16),height=Math.min(680,screenHeight-16),listWidth=(width-34)*3/5;
        boolean compact=height<300;
        int columns=listWidth>=310?2:1,rowHeight=22,listTop=compact?(weapons&&!armor?134:armor?112:112):(weapons?166:126);
        int rows=Math.max(1,(height-listTop-42)/rowHeight);
        return new Layout(width,height,listWidth,columns,rowHeight,listTop,Math.min(MAX_SIZE,rows*columns),compact);
    }
}
