# Eternal Defense custom mods — pack edition

## Since 0.0.41: the only edition

The standalone edition is discontinued; every build is the pack edition (`build releaseJars` makes `eternal-defense-0.21.0.jar`, `eternal-defense-gun-guide-0.21.0.jar` and `eternal-defense-displays-1.1.0.jar`). The beacon's `mods.toml` requires, and Forge refuses to start without:

| Mod | Version range | Side | Why |
| --- | --- | --- | --- |
| TaCZ (`tacz`) | `[1.1.8,1.2)` (the pack: 1.1.8-hotfix2) | both | the guns |
| TaCZ Attributes (`tacz_attributes`) | `[1.4]` | both | meal and drink gun bonuses |
| Create (`create`) | `[6.0,7)` (the pack: 6.0.8) | both | factory parts, belts into the Logistics Hub |
| KubeJS (`kubejs`) and KubeJS Create (`kubejs_create`) | `[2001,)` each | both | the pack's recipes (`pack-integration/kubejs`), `event.recipes.create.*` |
| LesRaisins Tactical Equipments (`lrtactical`) | `[0.4.3,)` | both | raid rewards hand out its grenades |
| JEI (`jei`) | `[15,)` | client | recipe look-ups from the mod's screens |

Rhino and Architectury come with KubeJS. Born in Chaos, Cataclysm, the qkl gun pack, the gear mods of the Armor Platform and the TaCZ add-ons stay optional: the mod falls back when they are missing.

**The test pack** is the owner's own Modrinth profile (Eternal Defense 0.22.1, exported as `.mrpack`) rebuilt by `tools/release/make-testpack.py`: our three jars in `overrides/mods`, the repository's KubeJS scripts, the jars that only work on a client moved to `client-overrides` (Tactical Movement 1.0.0 stops a dedicated server), and a check that every required mod above is in it. `tools/release/install-mrpack.py` installs such a pack into a server or client folder, checking every download against the pack's SHA-1 and SHA-512. Results of each round: `docs/ROUND-00xx-TESTING.md`.

## The 0.21.0 release (history)

The historical release names below are the **pack edition** for Eternal Defense 0.21.0. Current **0.0.5 / Mess Hall v4** work on `feature/messhall-ver-4` has a test ZIP containing both editions and the newly required backend JAR. Pack artifacts build successfully; full Create/KubeJS pack runtime remains unverified. See [test details](MESS-HALL-V4-TESTING.md). Older update archives do not include v4. Until final 1.0 the owner starts a fresh world per feature.

The v4 beacon build requires **TaCZ 1.1.8-hotfix2** ([file 9037989](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989)) and **leopoko TaCZ Attributes 1.4** (`tacz_attributes`, [project 1113285/file 8470731](https://www.curseforge.com/minecraft/mc-mods/tacz-attributes/files/8470731)) on clients and server, alongside Create/KubeJS and the matching pack. The test ZIP supplies TaCZ Attributes separately; no addon is embedded in our mod JARs. LesRaisins `lrtactical` 0.4.3 is optional for the shared grenade adapter. Other gun/affix frameworks are not meal requirements. See [v4 integration scope](MESS-HALL.md).

Historical file names:

- `eternal-defense-0.21.0.jar`
- `eternal-defense-gun-guide-0.21.0.jar`
- `eternal-defense-displays-1.1.0.jar`

They retain the curated Create factory materials, station progression and pack balance. They require the full pack's matching scripts, recipes and configuration. This small ZIP is intended for mod development or assembling the exact pack; it is not the complete pack updater.

To update Eternal Defense 0.20.0, use `Eternal-Defense-Update-0.20.0-to-0.21.0.zip`. For a fresh instance, import `Eternal-Defense-0.21.0.mrpack` through Modrinth. Neither route installs a new launcher or changes your account.

## Changes

The Armor Platform now contains only worn armor and protection. Grenades, food, healing items, tools and gear materials move to the Weapon Platform's existing **Supplies** category. Native armor and native supply fabrication each have a JEI category and the correct station catalyst. Quest and guide wording matches the separation. Existing factory cost templates are preserved.

The Ammo Platform collapses repeated recipes for the exact same item and tags into one entry, regardless of batch size. It preserves the earliest valid Age, then prefers the ammo owner's recipe at the same Age. Search cannot select an alternate price. Different cartridge IDs, magazine families, capacities and loaded-item tags stay distinct. The retained recipe supplies the displayed cost, crafting output and coin-purchase batch.

The Beacon, Gun Guide and Gun Displays also have separate standalone releases with vanilla-based acquisition. Optional displays no longer create a dependency cycle. Pack and standalone networking are distinct, preventing mismatched beacon editions from silently joining.

Do not install a pack JAR and its standalone counterpart together: they share mod IDs. Gun Guide is a client HUD; the beacon and displays belong on both clients and server.

`ARTIST-BRIEF.md` describes all three mods in player-facing language and identifies the screens, icons, states and HUD elements an artist can redesign. `VALIDATION.json` records completed checks and their limits.
