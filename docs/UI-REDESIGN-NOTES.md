# UI redesign notes (0.21.0 source, branch `claude/minecraft-mod-ui-guidebook-ak57k4`)

Read with [ARTIST-BRIEF.md](../ARTIST-BRIEF.md), [UI-DEVELOPMENT.md](UI-DEVELOPMENT.md) and [ui/DESIGN-SPEC.md](ui/DESIGN-SPEC.md).

## What changed

**Shared kit (new).** `Ui.java` holds the palette, panel/card/bar/pip/chip drawing and one `UiButton` class that renders
every state in the brief (normal, hover, pressed, selected, disabled, locked, warning, danger, keyboard focus).
`RichText.java` lays out the small markup (`# heading`, `- bullet`, `~ note`) used by the field guide and confirmations.
`GuideText.java` assembles guide pages from language keys without touching Minecraft types, so both editions can be unit tested.

| Surface | File | Main changes |
| --- | --- | --- |
| Beacon Overview | `BeaconClient.ControlScreen` | Status card (Mk, phase, HP bar, raid state), reward card with segmented score bar and exact "need N for Tier T", 8-cell score breakdown when there is room, three clear actions |
| Beacon Upgrades | `ControlScreen` | One card per branch: pips for level, exact next effect, material icon with `have / need`, Buy button that turns into an orange **Need N more** warning; one tooltip at a time |
| Raid break | `ControlScreen` | Raid level control with large numeral, next payout, break length, escalating price with icons, start-now action; the old icon hover hitbox bug (tooltip over the wrong spot) is fixed |
| Settings | `ControlScreen` | ON/OFF switches with one-line explanations and the zone size; separate red **Remove & reset** action |
| Placement / removal | `BeaconClient.ConfirmScreen` | Bullet list of consequences; Cancel is the default focus so Enter can never reset a campaign; removal uses a hazard-stripe banner |
| Rewards & tiers | `RaidRewardScreen` | Tier list marks *your next payout*, flags tiers above your raid cap, compares with the tier below (`+N`) |
| Weapon / Ammo / Attachment / Armor | `PlatformScreen` | Four station cards with pips and written Age (current table outlined), selected categories lit, rows show icon, full name, Age, padlock; "How to unlock" in orange; craft button shows **Craft: missing materials** instead of silently failing |
| Raid HUD / attack banner | `BeaconClient.drawHud`, `BeaconAlerts.draw` | HUD moved to the left edge (clear of chat, hotbar, TaCZ HUD); red pulsing banner with hazard edges and a triangle icon |
| Field guide | `BeaconClient.GuideScreen` | Page list, short pages, *Show details* toggle, keyboard paging and scrolling |
| Gun Guide | `GuideClient` | Key-cap control card, conflict marks, collapsed key-cap badge, JAM alert with triangle and `[key] Clear jam` |
| Displays | `DisplayRacks.RackItem`, lang | Consistent names (`Gun Stand`, `Wide Wall Gun Rack`, `Glass Gun Case`...), short interaction tooltips from language keys |

**Text.** All client-originated text is in `assets/arsenal_beacon/lang/en_us.json` (Gun Guide and Displays have their own).
Edition-specific copy uses `.pack` / `.standalone` key suffixes, e.g. `guide.stations.detail.pack`. The guide shows
`<id>.body`, then `<id>.body.<edition>`; hidden extras live in `.detail`. `LangKeysTest` fails the build if a key used by
the code is missing, a `%` could break formatting, the standalone guide mentions FTB, Essential, Create machines, brass or
KubeJS, or the pack guide loses its Essential, FTB Team, JEI and Create instructions.

**Unchanged on purpose:** packets and `16-pack` / `16-standalone`, registry IDs, NBT, saved campaign data, payment and
catalogue logic, raid balance, recipes (the pack and standalone recipe sets are untouched). `Rules`, `WeaponBrowser.layout`
and `GuideActions` behave exactly as before and their existing tests still pass.

## Known limits

* **Server-authored strings stay English.** Recipe requirements ("How to unlock: ..."), progression labels, the server
  feedback line and some catalogue names are produced on the server and sent as text. Translating them needs a packet or
  data change, which the handoff forbids for a visual-only update. Only two platform notices are recognised and localized client-side.
* Item and recipe names come from TaCZ and the game.
* Only English is provided. The code never builds sentences from fragments, so translators can reorder freely.
* The Ammo Coin, station block art and display art were not redrawn; the display art is All Rights Reserved.

## What was actually tested

| Check | Result |
| --- | --- |
| `./gradlew --no-daemon build releaseJars`, JDK 17 | See final run in the PR/commit message; baseline on `ef80290` passed before any change |
| Unit tests (beacon 30, gun guide 7) | Pass, including `LangKeysTest` (keys, `%` safety, edition separation) and a new compact-layout test |
| **Real Minecraft 1.20.1 client, software GL, standalone edition** | 45 screen/scale scenarios rendered with fabricated data and **no exceptions**: Overview, Upgrades, Raid break, Settings, placement and removal confirmation, rewards, guide pages (with and without details), Weapon / Ammo / Armor browser, raid HUD and attack banner, Gun Guide open / collapsed / jammed. GUI scales 2, 3 and 4 on a 1280 x 720 window (427 x 240 is Minecraft's default window). Screenshots in `docs/ui/screens/`. |
| Pack edition guide text | Unit-tested through `GuideText`; not rendered in a client (pack needs Create and KubeJS) |
| Not done | Real server round trips, TaCZ weapons and real catalogue data, JEI shortcuts, co-op/Essential, real-world gameplay of raids, Attachment-table and Workshop tabs, other languages. Please playtest these. |

The screenshot harness (`QaShots`) was a throwaway class kept out of the repository; it fed hand-made state into the screens, so numbers in
the screenshots are fabricated.

**Correction:** an earlier revision redrew five 16 x 16 component icons. Those models actually use `industrial_atlas.png`, so the
redraws never appeared in game; they have been reverted.
