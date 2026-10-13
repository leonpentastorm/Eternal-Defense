package dev.createarsenal.beacon;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Each player's own choices, in {@code config/arsenal-beacon-client.toml} on their computer (never sent to the server). Set from the
 * beacon's Settings tab.
 */
final class ArsenalClientConfig {
    private ArsenalClientConfig(){}
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.BooleanValue RAID_MUSIC,MUSIC_PLAYER,IDLE_ANIMATIONS;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("Raid music: the raid songs, changing with every wave, and the victory fanfare.").push("music");
        RAID_MUSIC=b.comment("Play the raid songs and the victory fanfare. Off: the game's own music keeps playing during raids.").define("raidMusic",true);
        MUSIC_PLAYER=b.comment("Show the name of the raid song under the beacon's status card while a raid runs.").define("musicPlayer",true);
        b.pop();
        b.comment("0.0.39: the idle animations of the mod's blocks (the FOB flag, the satellite dish, the beacon rings, flames and the rest).").push("visuals");
        IDLE_ANIMATIONS=b.comment("Animate the moving parts. Off: they stand still where the model puts them.").define("idleAnimations",true);
        b.pop();
        SPEC=b.build();
    }
    /** The value, or the default before the config file is loaded. */
    static boolean get(ForgeConfigSpec.BooleanValue value){try{return value.get();}catch(IllegalStateException notLoaded){return value.getDefault();}}
    static void set(ForgeConfigSpec.BooleanValue value,boolean on){try{value.set(on);value.save();}catch(IllegalStateException notLoaded){}}
}
