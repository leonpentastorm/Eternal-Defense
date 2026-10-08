package dev.createarsenal.beacon;

import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/** Early loading metadata is available while mixins are selected; ModList is not initialized then. */
public final class MealOptionalCompat {
    private MealOptionalCompat(){}
    public static String lrtacticalVersion(){
        var list=FMLLoader.getLoadingModList();if(list==null)return "absent";
        return list.getMods().stream().filter(mod->mod.getModId().equals("lrtactical")).map(mod->mod.getVersion().toString()).findFirst().orElse("absent");
    }
    public static boolean lrtacticalSupported(){
        var list=FMLLoader.getLoadingModList();if(list==null)return false;
        return list.getMods().stream().anyMatch(mod->mod.getModId().equals("lrtactical")
            &&mod.getVersion().compareTo(new DefaultArtifactVersion("0.4.3"))>=0
            &&mod.getVersion().compareTo(new DefaultArtifactVersion("0.4.4"))<0);
    }
}
