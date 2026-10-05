package dev.createarsenal.gunguide;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Standalone, client-optional mod. Common entry point references no client classes. */
@Mod(GunGuide.ID)
public final class GunGuide {
    public static final String ID="arsenal_gun_guide";
    public static final ForgeConfigSpec.BooleanValue CONTROLS,JAM;
    public static final ForgeConfigSpec.DoubleValue SCALE;
    private static final ForgeConfigSpec CONFIG;
    static {var b=new ForgeConfigSpec.Builder();CONTROLS=b.comment("Show the live weapon shortcut panel. Toggle also available in Controls.").define("showControls",true);JAM=b.comment("Show JAM beside the crosshair using Gun Durability's synchronized weapon state.").define("showJam",true);SCALE=b.defineInRange("panelScale",0.8,0.5,1.2);CONFIG=b.build();}
    public GunGuide(){ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,CONFIG);}
}
