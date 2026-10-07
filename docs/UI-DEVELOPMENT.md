# UI development handoff

## Scope

Redesign the interface for an unfamiliar player. Keep the tested gameplay and both build editions. The artist brief describes the intended player experience; this document identifies the implementation.

Both flavors belong in this repository. Share screen layouts and common guide pages. Keep pack-specific instructions conditional: Create factories, FTB Teams and Essential should not be presented as standalone requirements. Standalone pages explain vanilla recipes and `arsenal-beacon-standalone.json` instead. Move player-facing text into language keys during the redesign, including the hardcoded guide pages; neither flavor should lose its relevant instructions.

## Screens and renderers

Paths below are relative to the repository root. Beacon Java classes are under `custom-mods/arsenal-beacon/src/main/java/dev/createarsenal/beacon/`.

| File / class | Responsibility |
| --- | --- |
| `BeaconClient.java` → `PanelScreen` | Shared panel drawing and widgets |
| `BeaconClient.java` → `ControlScreen` | Beacon Overview, Upgrades, Raid break and Settings tabs |
| `BeaconClient.java` → `ConfirmScreen` | Beacon placement and removal confirmation |
| `BeaconClient.java` → `GuideScreen` | Seven current guide pages; pack and standalone page arrays |
| `PlatformScreen.java` | Weapon, ammo, attachment and armor catalogue; filters, recipe detail and Workshop |
| `RaidRewardScreen.java` | Reward tier information |
| `BeaconClient.java`, `BeaconAlerts.java` | Raid HUD, attacks and world-space overlays |
| `MessHallScreen.java`, `MessHallMenu.java` | Server-authoritative Sandwich/Stew tabs, shared ingredients/output, pot selection and paid tier upgrades |
| `MealClient.java`, `KitchenRenderer.java`, `MealEffects.java`, `MealEffectClient.java` | Kitchen registration, live Cook Pot menu board / stew surface, and the vanilla potion effects (icons in `textures/mob_effect/`, custom inventory text) that show a meal |
| `ControlHints.java`, `PlatformKeys.java` | Live key labels and table interaction binding |
| `custom-mods/arsenal-gun-guide/src/main/java/dev/createarsenal/gunguide/GuideClient.java` | Expanded control card, collapsed shortcut and JAM indicator |
| `custom-mods/arsenal-gun-guide/src/main/java/dev/createarsenal/gunguide/GuideState.java` | Guide visibility and five-second collapse state |
| `custom-mods/arsenal-displays/src/main/java/dev/createarsenal/displays/DisplayRackClient.java` | Displayed weapon rendering |

These screens are drawn with `GuiGraphics` and widgets. There is no existing GUI texture sheet to reskin. New UI textures can be added under the corresponding `src/main/resources/assets/<namespace>/textures/gui/` directory. Models, blockstates, textures and language files are already in the assets directories. Some display art deliberately retains the `arsenal_beacon` namespace for compatibility.

## Keep these behaviors intact

- Separate prominent Weapon, Ammo, Attachment and Armor Ages. Special Create Armory, Turrets and Supplies categories remain separate from the normal Age filter.
- Search, weapon-type and Age filtering; scroll/page navigation; full localized item names and icons. Keep the browser usable at smaller resolutions and multiple GUI scales.
- Ammo and attachments are filtered against the held gun and current table progression. Preserve the locked-compatible count and the equip/upgrade explanations.
- Material counts and crafting results come from the server. Keep JEI shortcuts on material icons where JEI is installed, and a readable fallback where it is absent.
- Use current `KeyMapping` labels, including mouse buttons and unbound keys. Do not bake H, R, U or the guide shortcut into textures or prose.
- Stable missing-material feedback: one tooltip at a time, rendered after widgets. Avoid repeated persistent popup layers when clicking or moving the cursor.
- Raid level controls, score progress and tier rewards must describe the actual selected payout, including logistics and building contributions.
- Preserve placement/removal confirmation, reward-chest collection, damage alerts for all players, and world overlay toggles.
- Gun Guide expands for five seconds when first drawn, then collapses to its live shortcut badge. Preserve its explicit hide/open controls and supported jam-clearing action.

## Gameplay and compatibility boundary

Keep registry IDs, item NBT, saved campaign fields, display inventory persistence and packet schemas stable for a visual-only change. `BeaconNetwork.java` currently uses `22-pack` / `22-standalone`; do not alter packet fields without updating and validating both sides. Server-authoritative crafting, spending, refunds and creative mode should not be reimplemented in screens.

`AmmoCatalogue.java` deduplicates identical output item and NBT independently of recipe batch size. Preserve magazine family, capacity and meaningful tags; a cosmetic change must not merge different magazines. `InventoryPayment.java` handles complete inventory payment rather than slot-by-slot partial spending.

`BuildFlavor.java` selects the edition through `arsenal-build.properties`. `standaloneJar` replaces metadata and beacon recipe resources. Standalone generic pricing and administrator overrides live in `StandaloneBalance.java`; pack recipes retain the curated Create progression. Display recipes are conditional on the beacon being absent. Do not flatten these into one recipe set.

Base repair journals must never restore spent ammunition, fuel or durability, duplicate drops, or erase unprocessed repairs. The UI redesign does not require changes to raid balance, enemy spawning or this journal.

## Verify the redesign

1. Build all projects and both editions with `./gradlew --no-daemon build releaseJars` using JDK 17.
2. Review the edited screens in a separate normal Minecraft instance, including small/large GUI scales, long names, insufficient materials, empty searches, unbound/remapped controls and optional JEI absence.
3. Check both editions' guides and crafting dependencies. Standalone must remain usable without Create/KubeJS/JEI.
4. If packet, catalogue, save or payment behavior changes, run the relevant real GameTests as well. Existing GameTest registration is opt-in through system properties; tests bundled in the production sources do not run during normal play. Read the test's registration method before enabling it. A default `runGameTestServer` without the required runtime mods is not a full-pack check.
5. Record what was actually tested. An automated build is not an in-game visual check, and a single-player test is not an Essential co-op test.

Work on a branch based on the source handoff commit. Keep implementation notes and screenshots with the UI change so the owner can review and playtest it.

Mess Hall v3 (protocol 24): the meal HUD is gone (potion effects instead). The kitchen screen was redesigned (`MessHallScreen`): tier badge, effect icons under every ingredient (sent as `SlotFx` in the server preview), effect cards with strength pips, field and home values and the x2 pair marker, pot meters, food bar, animated Prepare button. Effect icons: `tools/kitchen/make_effect_icons.py`. Captures: `docs/ui/screens/kitchen-v3/`.
