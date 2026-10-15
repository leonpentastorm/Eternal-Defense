package dev.eternaldefense.beacon;

import java.util.*;

/** Features that call another mod: when that mod changed, the feature is skipped and the log says so once */
final class Compat {
    private Compat(){}
    private static final Set<String> BROKEN=Collections.synchronizedSet(new HashSet<>());
    static void warn(String what,Throwable cause){
        if(BROKEN.add(what))com.mojang.logging.LogUtils.getLogger().warn("[Eternal Defense] {} is unavailable and skipped: {}",what,cause==null?"not found":String.valueOf(cause));
    }
    static boolean broken(String what){return BROKEN.contains(what);}
}
