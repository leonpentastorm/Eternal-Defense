package dev.createarsenal.beacon;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side tuning in config/arsenal-beacon-common.toml. Exchange offers and support costs are separate text files. */
final class ArsenalConfig {
    private ArsenalConfig(){}
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.DoubleValue ENERGY_CHANCE,LOOTING_BONUS;
    static final ForgeConfigSpec.IntValue BOSS_ENERGY;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("Ardent Energy: the currency hostile mobs sometimes drop.").push("energy");
        ENERGY_CHANCE=b.comment("Chance (0-1) that a hostile mob killed by a player drops Ardent Energy.").defineInRange("dropChance",0.12,0.0,1.0);
        LOOTING_BONUS=b.comment("Extra chance per Looting level.").defineInRange("lootingBonus",0.03,0.0,1.0);
        BOSS_ENERGY=b.comment("Energy dropped by bosses (always).").defineInRange("bossAmount",12,1,256);
        b.pop();
        SPEC=b.build();
    }
}
