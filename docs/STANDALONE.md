# Arsenal standalone mods — 0.21.0

Three independent mods for **Minecraft Java 1.20.1 / Forge 47.4.x**. The older 0.21.0 baseline used Forge 47.4.20 and TaCZ 1.1.8-hotfix2. The current **0.0.5 / Mess Hall v4 test build** is on `feature/messhall-ver-4`; use `Arsenal-MessHall-v4-0.0.5-test.zip` and its [validation notes](MESS-HALL-V4-TESTING.md). Until final 1.0, use a fresh world for each feature.

| Included file | Purpose | Install location |
| --- | --- | --- |
| `arsenal-beacon-0.21.0-standalone.jar` | Defense campaign, station progression, generic recipes and automatic TaCZ catalogue | Clients and server |
| `arsenal-gun-guide-0.21.0-standalone.jar` | Live weapon controls, collapsing guide and supported gun-jam warning | Client; server installation unnecessary |
| `arsenal-displays-1.1.0-standalone.jar` | Eleven weapon stands, racks and cases | Clients and server |

For v4, install **TaCZ 1.1.8-hotfix2** ([project 1028108/file 9037989](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989)) and **leopoko TaCZ Attributes 1.4** (`tacz_attributes`, [project 1113285/file 8470731](https://www.curseforge.com/minecraft/mc-mods/tacz-attributes/files/8470731)) on **both clients and server** with the beacon. Both are required; the v4 test ZIP includes TaCZ Attributes separately, while TaCZ remains an author download. Gun Guide/Displays used without the beacon do not acquire the backend requirement. LesRaisins Tactical Equipements (`lrtactical`) is optional; 0.4.3 enables the scoped grenade adapter. TAA, GunsmithLib, Apotheosis, Apothic Attributes and TaCZ:Accel are not required. See [Mess Hall compatibility](MESS-HALL.md) and [v4 integration and validation](MESS-HALL-V4-IMPLEMENTATION.md).

## How the standalone beacon differs

Create, KubeJS, JEI and Arsenal Gun Displays are optional. The beacon, shovel, guide, weapon station and upgrade components have vanilla-material recipes. Ammo, attachment and armor stations are purchased and upgraded in the weapon station's Workshop. Raid coins remain raid rewards, with no crafting recipe.

The catalogue imports valid loaded TaCZ gun, ammo and attachment indexes, including weapons without a native gunsmith recipe. It preserves native IDs and item tags. Native magazine and consumable entries retain their tagged recipe outputs when the relevant addon supplies those recipes. Gun compatibility is checked through TaCZ and the installed magazine addon.

Identical ammo outputs appear once even when several addon stations provide recipes. Magazine families, capacities and other meaningful item tags remain distinct.

Known weapons retain their curated eras. Unknown weapons receive an estimated Age from their identity, capacity and bullet power; unknown modern weapons normally enter Age 3. Generic costs use vanilla metals, redstone, gunpowder, quartz and later-game resources. Ammo pricing considers the strongest compatible gun's per-shot damage. It does not simulate recoil, accuracy, fire rate or every custom script. Treat automatic balance as a starting point, especially for unusual third-party guns.

Visit the Nether to unlock modern station progression. Creative crafting, station purchases and upgrades remain free for testing. Rewards use vanilla resources and Ammo Coins instead of missing pack components; installed native medical and explosive supplies can appear in rewards. Without optional boss mods, raid bosses use the vanilla fallback.

The standalone edition leaves other mods' welcome messages and your existing key mappings alone. The built-in guide explains its own recipes. JEI is optional; if absent, recipe shortcuts say so. Platform material costs are authoritative: an addon's existing JEI recipe may describe its original gunsmith recipe instead.

## Configure the generic catalogue

After starting a server or opening a world, edit `config/arsenal-beacon-standalone.json`, then restart. In multiplayer, edit the server's file. `BALANCE-EXAMPLE.json` is an example; it is not installed automatically.

Keys use `kind/namespace:id`, for example `gun/tacz:ak47`, `ammo/tacz:762x39` or `attachment/tacz:extended_mag_1`. Check the actual IDs of installed packs before adding entries. `ages` accepts integers 1–5. `costs` overrides an entry's materials with real item IDs and counts 1–4096. These overrides apply to indexed weapon/ammo/attachment and generic native-supply entries. Native armor fabrication uses its separate generic material formula. Invalid configuration fails with a clear error rather than installing an Air ingredient.

Adding a gun pack is detected at world/server load; no catalogue regeneration script is needed. Restart after changes. There is no guarantee that all third-party packs are compatible with each other or contain valid indexes.

## Using the other two mods

Gun Guide expands briefly when a gun is drawn and then collapses to its live shortcut badge. Its key can be changed in Controls; the guide shows current bindings. Jam integration requires the supported TaCZ Durability addon. Without that addon, the controls guide still works.

Gun Displays alone has eleven vanilla crafting recipes with general wood tags. When **either flavor of Defense Beacon** is installed, those recipes are disabled and displays move to the weapon station's **Supplies** category. Displayed weapons keep their item tags when saved, swapped or retrieved.

## Keep flavors separate

Pack and standalone JARs have the **same mod IDs**. Install only one flavor of each. Do not put both JARs in `mods`. All players and the server must use the same beacon flavor/version. Pack and standalone beacon networking deliberately reject a mismatched flavor.

Use a separate instance/world for standalone play. Switching an existing campaign between editions changes recipe and progression assumptions; this is not an automatic world-conversion release. Copying this ZIP onto an existing full Create Arsenal pack is not the pack update.

See `ARTIST-BRIEF.md` for player-facing descriptions and the UI redesign brief, and `VALIDATION.json` for checks and their limits. Internet co-op through Essential has not been retested in this release.
