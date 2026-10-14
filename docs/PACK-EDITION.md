# Eternal Defense — the pack edition

## 1.0.0: one edition, two builds of the main mod

Every build is the pack edition (the standalone edition was discontinued in 0.0.41). `build releaseJars` makes:

| Jar | What it is | Side |
| --- | --- | --- |
| `eternal-defense-1.0.0.jar` | the main mod, with the radio voice lines | both |
| `eternal-defense-1.0.0-novoice.jar` | the same mod; each of the 15 spoken radio lines plays a game sound instead | both |
| `eternal-defense-gun-guide-1.0.0.jar` | the weapon controls HUD | client |
| `eternal-defense-displays-1.0.0.jar` | weapon stands, racks and glass cases | both |

Install **one** of the two main jars (they share the mod id `eternal_defense`). Both register the same sounds and the client picks the sound files, so what a player hears depends on the jar in that player's game; a server and its players mixing the two builds was not tested, so use the same one everywhere to be safe. The no-voice build differs only in `assets/eternal_defense/sounds.json`, the 15 voice files it leaves out and one sentence in its `mods.toml` description; its game sounds are listed in `custom-mods/eternal-defense/src/novoice/voice-replacements.json`.

The main mod's `mods.toml` requires, and Forge refuses to start without:

| Mod | Version range | Side | Why |
| --- | --- | --- | --- |
| TaCZ (`tacz`) | `[1.1.8,1.2)` (the pack: 1.1.8-hotfix2) | both | the guns |
| TaCZ Attributes (`tacz_attributes`) | `[1.4]` | both | meal and drink gun bonuses |
| Create (`create`) | `[6.0,7)` (the pack: 6.0.8) | both | factory parts, belts into the Logistics Hub |
| KubeJS (`kubejs`) and KubeJS Create (`kubejs_create`) | `[2001,)` each | both | the pack's recipes (`pack-integration/kubejs`), `event.recipes.create.*` |
| LesRaisins Tactical Equipments (`lrtactical`) | `[0.4.3,)` | both | raid rewards hand out its grenades |
| JEI (`jei`) | `[15,)` | client | recipe look-ups from the mod's screens |

Rhino and Architectury come with KubeJS. Born in Chaos, Cataclysm, the qkl gun pack, the gear mods of the Armor Platform and the TaCZ add-ons stay optional: the mod falls back when they are missing.

**Worlds.** 1.0.0 renamed every id. A world or config made with a 0.0.x build is not converted: the mod's blocks and items in it are gone. Start a fresh world.

## The test pack

The owner's own Modrinth profile, exported as `.mrpack`, rebuilt by `tools/release/make-testpack.py --base <export> [--novoice]`: our three jars in `overrides/mods` (with `--novoice` the no-voice main jar), the repository's KubeJS scripts, the jars that only work on a client moved to `client-overrides` (Tactical Movement 1.0.0 stops a dedicated server), and a check that every required mod above is in it. A profile exported from a 0.0.x build is moved to the 1.0.0 ids on the way: its old-named pack scripts and the old exchange file are dropped, its config files renamed, and the old item ids, class names and titles in its quests, configs and scripts rewritten; the script stops if any old name is left. `tools/release/install-mrpack.py` installs such a pack into a server or client folder, checking every download against the pack's SHA-1 and SHA-512. Results of each round: `docs/*-TESTING.md`.

## Gameplay notes

The Armor Platform contains only worn armor and protection. Grenades, food, healing items, tools and gear materials are in the Weapon Platform's **Supplies** category. Native armor and native supply fabrication each have a JEI category and the correct station catalyst.

The Ammo Platform collapses repeated recipes for the exact same item and tags into one entry, regardless of batch size. It keeps the earliest valid Age, then prefers the ammo owner's recipe at the same Age. Different cartridge ids, magazine families, capacities and loaded-item tags stay distinct. The kept recipe supplies the displayed cost, crafting output and coin-purchase batch.

Gun Guide is a client HUD; the main mod and the displays belong on both clients and the server. Display artwork is All Rights Reserved by its creator.
