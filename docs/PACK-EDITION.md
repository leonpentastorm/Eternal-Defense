# Eternal Defense — the pack edition

## 1.0.1: one edition, two builds of the main mod, the displays on their own

Every build is the pack edition (the standalone edition was discontinued in 0.0.41). `build releaseJars` makes:

| Jar | What it is | Side |
| --- | --- | --- |
| `eternal-defense-1.0.1.jar` | the main mod, with the radio voice lines | both |
| `eternal-defense-1.0.1-novoice.jar` | the same mod; each of the 15 spoken radio lines plays a game sound instead | both |
| `eternal-defense-gun-guide-1.0.1.jar` | the weapon controls HUD | client |
| `eternal-defense-displays-1.0.1.jar` | weapon stands, racks and glass cases: a separate mod that needs only TaCZ | both |

Install **one** of the two main jars (they share the mod id `eternal_defense`). Both register the same sounds and the client picks the sound files, so what a player hears depends on the jar in that player's game; a server and its players mixing the two builds was not tested, so use the same one everywhere to be safe. The no-voice build differs only in `assets/eternal_defense/sounds.json`, the 15 voice files it leaves out and one sentence in its `mods.toml` description; its game sounds are listed in `custom-mods/eternal-defense/src/novoice/voice-replacements.json`.

The main mod's `mods.toml` requires, and Forge refuses to start without:

| Mod | Version range | Side | Why |
| --- | --- | --- | --- |
| TaCZ (`tacz`) | `[1.1.8,1.2)` (the pack: 1.1.8-hotfix2) | both | the guns |
| TaCZ Attributes (`tacz_attributes`) | `[1.4]` | both | meal and drink gun bonuses |
| Create (`create`) | `[6.0,7)` (the pack: 6.0.8) | both | the factory parts' recipes, belts into the Logistics Hub |
| LesRaisins Tactical Equipments (`lrtactical`) | `[0.4.3,)` | both | raid rewards hand out its grenades |
| JEI (`jei`) | `[15,)` | client | recipe look-ups from the mod's screens |

**Since 1.0.1 no KubeJS script is needed** (KubeJS and KubeJS Create are no longer required): the factory parts (receivers, hardened alloy, cartridge cases, propellant) are the mod's own items with their Create recipes in its data, and the rules the pack scripts applied (factory gates on TaCZ gun smith recipes, universal wood, reduced conversions, no native gun smith tables) run inside the mod while recipes load (`RecipeRules`, `mixin/RecipeManagerMixin`). The factory tier and the welcome line moved in too (`FactoryTiers`). Born in Chaos, Cataclysm, the qkl gun pack, the gear mods of the Armor Platform, the TaCZ add-ons and the displays stay optional: the mod falls back when they are missing.

**The displays** (`eternal_defense_displays`, its own ids since 1.0.1) need only TaCZ and Forge. Every stand is made at the crafting table. With Eternal Defense installed the stands are also sold in the Weapon Platform's Supplies category; without the displays mod Supplies lists none.

**Worlds.** 1.0.0 renamed every id. A world or config made with a 0.0.x build is not converted: the mod's blocks and items in it are gone. Start a fresh world. From 1.0.0 to 1.0.1 the factory parts changed id (`kubejs:*` to `eternal_defense:*`) and the stands too (`eternal_defense:display_*` to `eternal_defense_displays:display_*`): a 1.0.0 world would lose those items and placed stands (the game drops ids it no longer knows; not tried), so start a fresh world here too.

## The test pack

The owner's own Modrinth profile, exported as `.mrpack`, rebuilt by `tools/release/make-testpack.py --base <export> [--novoice]`: our three jars in `overrides/mods` (with `--novoice` the no-voice main jar), the repository's `pack-integration/kubejs` files (data and asset fixes for other mods in the pack; no script since 1.0.1), the jars that only work on a client moved to `client-overrides` (Tactical Movement 1.0.0 stops a dedicated server), and a check that every required mod above is in it. A profile exported from an earlier build is moved to the current ids on the way: its old pack scripts (all of Eternal Defense's KubeJS scripts and configs since 1.0.1) and the old exchange file are dropped, `kubejs:` factory part ids become `eternal_defense:`, its config files renamed, and the old item ids, class names and titles in its quests, configs and scripts rewritten; the script stops if any old name is left. `tools/release/install-mrpack.py` installs such a pack into a server or client folder, checking every download against the pack's SHA-1 and SHA-512. Results of each round: `docs/*-TESTING.md`.

## Gameplay notes

The Armor Platform contains only worn armor and protection. Grenades, food, healing items, tools and gear materials are in the Weapon Platform's **Supplies** category. Native armor and native supply fabrication each have a JEI category and the correct station catalyst.

The Ammo Platform collapses repeated recipes for the exact same item and tags into one entry, regardless of batch size. It keeps the earliest valid Age, then prefers the ammo owner's recipe at the same Age. Different cartridge ids, magazine families, capacities and loaded-item tags stay distinct. The kept recipe supplies the displayed cost, crafting output and coin-purchase batch.

Gun Guide is a client HUD; the main mod and the displays belong on both clients and the server. Display artwork is All Rights Reserved by its creator.
