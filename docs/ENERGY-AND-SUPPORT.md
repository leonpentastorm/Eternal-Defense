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
* Network: protocol version bumped to **18** (17 was the first support build) (clients and servers must match). New messages 6 `Announce`, 7 `Exchange open`, 8 `Exchange buy`, 9 `Support buy`, 10 `Support upgrade`.
* Client: `SupportClient` (renderers, model registration), `SupportScreen`, `ExchangeScreen`, `BeaconPopups`.

### Art

Items use 64 x 64 painted sprites in the style of `industrial_atlas.png` (the three flares each have their own: cyan, purple and red canisters, drawn in flight and on the ground
with the same sprite). The Return Flare portal is a 4-frame 16 x 32 sheet (`textures/entity/return_portal.png`) drawn as a 1 x 2 block billboard that always faces the viewer.

Placeables are JSON element models over one shared 512 x 512 tile kit (`textures/block/support_kit.png`) painted in the same palette as `defense_beacon_level4.png`:
charcoal brushed metal, gold plates, cyan light strips, hazard stripes, tiny screens with real text. Boxes wider than 16 units are cut into pieces so the texel density stays
at 64 px per block everywhere. Regenerate everything with `python3 tools/ui-assets/make_sprites.py` (needs Pillow); previews are in `docs/ui/sprites/`.
Models: `exchange_shop`, `support_platform_mk1..4` (the pad shows the grid size, each Mk adds hardware), `support_cannon` (base, 3 x 3 x 2) with `_turret` and `_barrel`
drawn by a renderer (the turret is drawn 25% larger, the barrel is pitched 50 degrees and slides back along its axis when it fires), `support_parcel` (+ `_chute`).

## What was tested

Everything below was exercised in a real Minecraft 1.20.1 integrated server driven from the client (screenshots in `docs/ui/screens/support/`), 50 checks, all passing on the final run:
zone rules (inside, outside, beacon removed), placing and refusing a second platform, cannon and shop, owner-only access, buying all four products (300 energy),
throwing a supply flare (flare used up, no energy spent, grid emptied, cannon aimed at the flare rather than the player, countdown in chat, 12 s outdoors and about 7 s underground,
chest opens with exactly the grid contents and folds away when emptied), refusals that keep the flare (empty grid, beacon gone), the Return Flare portal (client and server agree, owner teleported, portal used up),
Fire Support (red box, every zombie inside died, zombies outside survived, six shots, caller unhurt), and shovel relocation (Mk and contents kept, cannon picked up from any cell).
Unit tests cover the numbers, the turret motor, the red box and the field guide quoting real values.

Not tested: real multiplayer (the ownership rules are only exercised with a single player and a forged owner id), other dimensions, TaCZ weapons inside a grid on a dedicated run
(the user tested TaCZ guns and ammo in a supply parcel by hand), and chunk-unload edge cases.
