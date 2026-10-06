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

A placeable trader's booth (3D model, right-click to open). Like all support gear it only works **inside the zone of a standing Defense Beacon**.
Offers come from **`config/arsenal-beacon-exchange.txt`**, created on first use and re-read every time a shop opens:

```
# item_id [x count] = energy cost
arsenal_beacon:universal_ammo_coin = 4       # the default: 4 energy per Ammo Coin
minecraft:arrow x16 = 20
```

Bad lines are skipped and logged. A file with no valid offers falls back to the default coin offer. Purchases are validated on the
server (distance, price, inventory room, beacon zone) and are free in Creative.

## Base support

**Every player builds their own** Support Platform and Support Cannon (one of each per player; only the owner can open them).
All support gear can only be placed inside the zone of an active Defense Beacon, stops working while the beacon is gone, and is
moved for free by right-clicking it with the **Beacon Recovery Shovel** (a platform keeps its Mk level and grid contents).

| Piece | What it does |
| --- | --- |
| **Support Platform** (craftable) | Holds the supply grid (3x3 at Mk-1), sells the cannon and flares, upgrades Mk-1 to Mk-4 (grid 4x4, 5x5, 6x6) for 16 / 32 / 64 Reinforced Beacon Plating. The block in front of it must stay clear. Items in the grid never move when it grows. |
| **Support Cannon** (150 energy at the platform) | A hulking 3 x 3 x 2 gun within 12 blocks of the platform. The turret is heavy: it spins up and brakes slowly (about 5 s for a half turn), then fires. It turns toward the **flare**, never toward the player. |
| **Support Flare** (30) | Throw it anywhere. The grid is emptied into a chest that arrives 12 s after the flare lands (parachuting in outdoors) or 7 s after (appearing at your feet underground). Chat counts the seconds down. |
| **Return Flare** (20) | Throw it. A purple 1 x 2 portal that always faces you opens where it lands (30 s, owner only). Step in to arrive in front of the platform. Works from any dimension. |
| **Fire Support Flare** (100) | A red box marks the area. Six shells land **exactly on the flare**, one every 2 s, each hurting every hostile mob touching the box (25 damage). Never hurts players or blocks. |

Throwing a flare costs nothing: the Ardent Energy is paid when you buy it. If anything is missing (no platform, no cannon, beacon gone,
empty grid) you get a chat message and keep the flare. Everyone sees **"[player] is calling [support]"**.

The supply parcel is a real chest: right-click to open it, take what you need, and it folds away once emptied and closed.

## For developers

* Server logic: `SupportCalls` (validation, payment, delivery, return), `SupportData` (who owns which platform and cannon), `BaseZone` (zone rules, placement item, shovel relocation), `SupportShop` (purchases, upgrades), `SupportFlares` (items and the thrown flare), `SupportCrate` (parcel chest entity), `SupportCannon`, `SupportPlatform` (block, block entity, menu), `ArdentEnergy`, `ExchangeShop`.
* Pure, unit-tested rules: `SupportRules` (every number), `SupportCannon.spin` (turret motor), `TextTable`, `RaidWarnings.bucket`, `ArdentEnergy.amount`.
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
