# Ardent Energy and base support

New gameplay on top of the UI redesign. Read [UI-REDESIGN-NOTES.md](UI-REDESIGN-NOTES.md) for the screens. Everything here works
in both editions (pack and standalone) and uses vanilla-material crafting recipes; the pack's curated Create progression
does not cover the new blocks yet.

## Quality of life

* **Field guide:** the page list follows Back/Next and the arrow keys. "Show details" is gone; every page's extra rules live
  together in a **Reference** tab (page 11). New pages: *Ardent Energy* and *Base support*.
* **Raid warnings in chat:** one day before a scheduled raid, then every 6 in-game hours (24, 18, 12, 6), and once when the
  countdown is over and the raid is waiting for night. Intro raids and raid breaks stay silent. (`RaidWarnings`)
* **Centre-screen popups:** raid start (`HARD RAID | BOSS` for boss raids), each wave, a colourful rainbow **VICTORY!** with
  confetti, and a **DEFEAT** banner. Support calls appear as small toasts under them. (`BeaconPopups`, new `Announce` packet)

## Ardent Energy

Hostile mobs killed by a player (or raid attackers) have a **12 %** chance to drop one Ardent Energy, +3 % per Looting level;
mobs with 60+ max health drop two; bosses always drop 12. Tune these in `config/arsenal-beacon-common.toml`
(`dropChance`, `lootingBonus`, `bossAmount`). Deaths without a credited player (fire, `/kill`, falling) drop nothing.

### Exchange Shop

A placeable kiosk (3D model, right-click to open). Offers come from **`config/arsenal-beacon-exchange.txt`**, created on first
use and re-read every time a shop opens:

```
# item_id [x count] = energy cost
arsenal_beacon:universal_ammo_coin = 4       # the default: 4 energy per Ammo Coin
minecraft:arrow x16 = 20
```

Bad lines are skipped and logged. A file with no valid offers falls back to the default coin offer. Purchases are validated on the
server (distance, price, inventory room) and are free in Creative.

## Base support

| Piece | What it does |
| --- | --- |
| **Support Platform** (craftable) | One per world, Overworld only, the block in front of it must stay clear. Holds the supply grid (3x3 at Mk-1), sells the cannon and flares, and upgrades Mk-1 to Mk-4 (grid 4x4, 5x5, 6x6) for 16 / 32 / 64 Reinforced Beacon Plating. Items already in the grid never move when it grows. |
| **Support Cannon** (bought at the platform) | Must be within 6 blocks of the platform to count. Turns toward whoever calls support and fires; during Fire Support it fires at every blast. Separate turret and barrel models are drawn by a renderer; the barrel recoils. |
| **Support Flare** (15) | Throw it anywhere. If you are outdoors and the sky is open above the flare a parcel parachutes down to it; otherwise it appears at your feet. Opening it gives an exact copy of the supply grid as it was when you threw. |
| **Return Flare** (8) | Hold use for 3 seconds to light; takes you to the front of the platform from anywhere, including other dimensions. |
| **Fire Support Flare** (40) | After landing it shells the surroundings every 2 seconds for 12 seconds (6 blasts, within 8 blocks). Each blast hurts hostile mobs within 4 blocks for 25 damage; it never hurts players and never breaks blocks. |

Calls cost Ardent Energy on top of the gear: supply drop = 5 + the grid's contents, Return 10, Fire Support 60. Everyone on the server sees
**"[player] is calling [support]"** and the cannon turns and fires.

### Supply drop prices

Per item: common 1, uncommon 3, rare 8, epic 20; bulk blocks (stone, dirt, planks, logs, glass, wool...) a tenth of that; a table of
well-known items (iron 1, gold 2, diamond 6, netherite ingot 16, totem 40, elytra 50, nether star 60...); TaCZ guns 45, attachments 12,
rounds 0.35. Override anything in **`config/arsenal-beacon-support-costs.txt`** (same line format as the exchange file; `minecraft:arrow x16 = 4` means a
quarter each). The platform shows the live total before you throw.

## For developers

* Server logic: `SupportCalls` (validation, payment, delivery, return), `SupportData` (saved world state, including a snapshot of the grid so calls work from anywhere without loading the base), `SupportShop` (purchases, upgrades), `SupportFlares` (items and the thrown flare), `SupportCrate` (parcel entity), `SupportCannon`, `SupportPlatform` (block, block entity, menu), `ArdentEnergy`, `ExchangeShop`.
* Pure, unit-tested rules: `SupportRules` (every number), `SupportCosts`, `TextTable`, `RaidWarnings.bucket`, `ArdentEnergy.amount`.
  `SupportTest` also fails if the field guide quotes a number that no longer matches `SupportRules`.
* Network: protocol version bumped to **17** (clients and servers must match). New messages 6 `Announce`, 7 `Exchange open`, 8 `Exchange buy`, 9 `Support buy`, 10 `Support upgrade`.
* Client: `SupportClient` (renderers, model registration), `SupportScreen`, `ExchangeScreen`, `BeaconPopups`.

### Art

Items use 64 x 64 painted sprites in the style of `industrial_atlas.png`; placeables are JSON element models over one shared 128 x 128 tile kit
(`textures/block/support_kit.png`). Regenerate everything with `python3 tools/ui-assets/make_sprites.py` (needs Pillow); previews are in `docs/ui/sprites/`.
Models: `exchange_shop`, `support_platform_mk1..4` (the pad shows the grid size), `support_cannon` (+ `_turret`, `_barrel`), `support_parcel` (+ `_chute`).

## What was tested

See the table in UI-REDESIGN-NOTES.md. Support and energy were exercised end to end in a real Minecraft 1.20.1 integrated server driven from the client:
drops (credited and uncredited kills), exchange purchases (including overspending and out-of-range), platform registration and the 6-block cannon rule, grid
pricing, all four shop products, Mk-2 to Mk-4 upgrades with items staying put, supply flare payment, cannon aim, parcel fall and delivery, the underground path,
refusals without energy, Return Flare, and Fire Support (payment, 6 cannon shots, all test zombies killed, caller unhurt). The run found and fixed one real bug: paying Ardent Energy rebuilt the inventory, so a flare was not consumed in survival. Not tested: multiplayer, other dimensions, TaCZ weapons in the grid,
and chunk-unload edge cases. Please playtest those.
