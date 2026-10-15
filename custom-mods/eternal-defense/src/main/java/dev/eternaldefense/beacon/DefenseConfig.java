package dev.eternaldefense.beacon;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side tuning in config/eternal-defense-common.toml. */
final class DefenseConfig {
    private DefenseConfig(){}
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.DoubleValue ENERGY_CHANCE,LOOTING_BONUS,MARKED_MULTIPLIER;
    static final ForgeConfigSpec.IntValue BOSS_ENERGY;
    static final ForgeConfigSpec.ConfigValue<java.util.List<? extends Double>> TIER_HEALTH,TIER_DAMAGE;
    static final ForgeConfigSpec.IntValue ORDINARY_CAP,ELITE_CAP,BOSS_CAP;
    static final ForgeConfigSpec.IntValue LOADOUT_COOLDOWN,LOADOUT_KIT_LIMIT_KB;
    static final ForgeConfigSpec.ConfigValue<String> LOADOUT_DOOR_SOUND,LOADOUT_STEAM_SOUND;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("Ardent Energy: the currency hostile mobs sometimes drop.").push("energy");
        ENERGY_CHANCE=b.comment("Base chance (0-1) behind the drop of a red-marked mob (raids, operations); other mobs drop none, big bosses one.").defineInRange("baseDropChance",0.0667,0.0,1.0);
        MARKED_MULTIPLIER=b.comment("Multiplier on the base chance for red-marked mobs; raid modifiers add their percent of the base chance on top, then Luck.").defineInRange("markedDropMultiplier",1.5,0.0,15.0);
        LOOTING_BONUS=b.comment("Extra chance per Looting level.").defineInRange("lootingBonusPerLevel",0.01,0.0,1.0);
        BOSS_ENERGY=b.comment("Energy dropped by a raid's boss (always).").defineInRange("bossDrop",1,1,256);
        b.pop();
        b.comment("Factory tiers: hostile mobs get tougher as the world's best receiver improves (none, Brass, Hardened, Exotic). Raiders are balanced by the raid instead.").push("factoryTiers");
        TIER_HEALTH=b.comment("Max health multiplier per factory tier 0 to 3.").defineList("healthMultipliers",java.util.List.of(3.0,4.0,6.0,8.0),v->v instanceof Double d&&d>=1&&d<=100);
        TIER_DAMAGE=b.comment("Attack damage multiplier per factory tier 0 to 3.").defineList("damageMultipliers",java.util.List.of(1.1,1.2,1.35,1.5),v->v instanceof Double d&&d>=1&&d<=100);
        ORDINARY_CAP=b.comment("Health cap for ordinary mobs (base health under 50).").defineInRange("ordinaryHealthCap",240,1,100000);
        ELITE_CAP=b.comment("Health cap for elite mobs (base health 50 to 149).").defineInRange("eliteHealthCap",900,1,100000);
        BOSS_CAP=b.comment("Health cap for bosses (base health 150 or more).").defineInRange("bossHealthCap",4000,1,100000);
        b.pop();
        b.comment("The Loadout Station.").push("loadout");
        LOADOUT_COOLDOWN=b.comment("Seconds a player waits between two prints (kept over a relog).").defineInRange("printCooldownSeconds",LoadoutRules.COOLDOWN_SECONDS,0,3600);
        LOADOUT_KIT_LIMIT_KB=b.comment("Largest kit a coin may hold, in kilobytes of saved item data.").defineInRange("kitLimitKilobytes",LoadoutRules.KIT_LIMIT_KB,1,1024);
        LOADOUT_DOOR_SOUND=b.comment("Sound of the doors (placeholder: a vanilla piston).").define("doorSound","minecraft:block.piston.extend");
        LOADOUT_STEAM_SOUND=b.comment("Sound of the steam and the print (placeholder: vanilla fire being put out).").define("steamSound","minecraft:block.fire.extinguish");
        b.pop();
        SPEC=b.build();
    }
}
