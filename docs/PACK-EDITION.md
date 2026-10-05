# Arsenal custom mods — pack edition, 0.21.0

These three JARs are the **pack edition** for Create Arsenal 0.21.0:

- `arsenal-beacon-0.21.0.jar`
- `arsenal-gun-guide-0.21.0.jar`
- `arsenal-displays-1.1.0.jar`

They retain the curated Create factory materials, station progression and pack balance. They require the full pack's matching scripts, recipes and configuration. This small ZIP is intended for mod development or assembling the exact pack; it is not the complete pack updater.

To update Create Arsenal 0.20.0, use `Create-Arsenal-Update-0.20.0-to-0.21.0.zip`. For a fresh instance, import `Create-Arsenal-0.21.0.mrpack` through Modrinth. Neither route installs a new launcher or changes your account.

## Changes

The Armor Platform now contains only worn armor and protection. Grenades, food, healing items, tools and gear materials move to the Weapon Platform's existing **Supplies** category. Native armor and native supply fabrication each have a JEI category and the correct station catalyst. Quest and guide wording matches the separation. Existing factory cost templates are preserved.

The Ammo Platform collapses repeated recipes for the exact same item and tags into one entry, regardless of batch size. It preserves the earliest valid Age, then prefers the ammo owner's recipe at the same Age. Search cannot select an alternate price. Different cartridge IDs, magazine families, capacities and loaded-item tags stay distinct. The retained recipe supplies the displayed cost, crafting output and coin-purchase batch.

The Beacon, Gun Guide and Gun Displays also have separate standalone releases with vanilla-based acquisition. Optional displays no longer create a dependency cycle. Pack and standalone networking are distinct, preventing mismatched beacon editions from silently joining.

Do not install a pack JAR and its standalone counterpart together: they share mod IDs. Gun Guide is a client HUD; the beacon and displays belong on both clients and server.

`ARTIST-BRIEF.md` describes all three mods in player-facing language and identifies the screens, icons, states and HUD elements an artist can redesign. `VALIDATION.json` records completed checks and their limits.
