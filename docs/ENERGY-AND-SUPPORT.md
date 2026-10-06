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
| **Support Cannon** (150 energy at the platform) | A hulking 3 x 3 x 2 gun within 12 blocks of the platform. The turret is heavy: it spins up and brakes slowly (about 5 s for a half turn), settles for a second, then fires. It turns toward the **flare**, never toward the player. |
| **Support Flare** (30) | Throw it anywhere. The grid is emptied into a chest that arrives 12 s after the flare lands (parachuting in outdoors) or 7 s after (appearing at your feet underground). Chat counts the seconds down. |
| **Return Flare** (20) | Throw it. A purple 1 x 2 portal that always faces you opens where it lands (30 s, owner only). Step in to arrive in front of the platform. Works from any dimension. |
| **Fire Support Flare** (100) | A red box marks the area. Six shells land **exactly on the flare**, one every 2 s, each hurting every hostile mob touching the box (25 damage). Never hurts players or blocks. |

Throwing a flare costs nothing: the Ardent Energy is paid when you buy it. If anything is missing (no platform, no cannon, beacon gone,
empty grid) you get a chat message and keep the flare. Everyone sees **"[player] is calling [support]"**.

### The Support Cannon: fire support types and upgrades

Click your cannon to open its menu. **Everything on it is yours alone**: the fire support you choose and the upgrades you buy are stored with *you*,
not with the flare or the cannon block. A Fire Support Flare handed to you by another player still calls down **your** choice, and the call is announced to everyone
("Player one just called in NARUKAMI'S FAVOR").

| Fire support | What the shells do |
| --- | --- |
| **Explosion Barrage** | The original: 6 shells exploding on the flare, 25 damage to every hostile mob touching the box. |
| **Arrow Cluster Bomb** | Each shell showers the area with 16 arrows (hostile mobs only). 2 more volleys than the others (8). |
| **Narukami's Favor** | Lightning strikes every hostile mob in the area (up to 12 per volley). 1 fewer volley (5). |
| **Bunker Buster** | One huge bomb (radius 7, destroys terrain but never the beacon or support gear, never hurts players). Volley upgrades do not apply. |
| **Healing Barrage** | Like the barrage, but every shell bursts into Instant Health II and Regeneration for players in the box. |
| **Curse of Debilitation** | Hostile mobs in the box get Wither II, Poison II, Weakness II, Slowness III, Blindness and Glowing. |

Upgrades are bought with Ardent Energy and stay with you when you pick the cannon up with the shovel:

| Upgrade | Levels and price | Effect |
| --- | --- | --- |
| Faster traverse | 60 / 120 / 200 | Turret 40 percent faster per level: a shorter spin time. |
| Rate of fire | 80 / 160 / 260 | Ticks between shells 40, 33, 26, 20. |
| More volley | 100 / 200 / 320 | +2 shells per level (not the Bunker Buster). |
| Damage | 100 / 200 / 320 | +25 percent per level (curse durations too). |
| Quantum tunneling | 400 | Fire support flares work below ground. Without it a flare that lands underground is handed back. |
| Slowness field | 250 | A landed fire support flare slows every hostile mob in its whole area. |
| Lasting portal | 200 | The return portal stays 5 minutes and works twice: home, then back to where it was thrown (a second portal opens at the platform). |
| Healing aura | 200 | A supply flare heals everyone within 6 blocks, until 30 seconds after the parcel is emptied. |

The cannon is heavy and sounds like it: the traverse gear grinds while it turns, it locks with a clunk, and it only fires after sitting still on target for a second.

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

The 3D models are hand-made (source in `docs/art/Create-Arsenal-Support-Gear/`, Blockbench files with their textures embedded) and installed with
`python3 tools/ui-assets/import_support_gear.py`: Exchange Shop and Support Platform Mk-1 to Mk-4 (both two blocks tall, the upper block is an invisible structure part,
like the benches), the Support Cannon (base, turret and barrel), and the supply parcel. The script also paints the Ardent Energy crystal onto the shop's credit display,
builds the parachute and the cannon's inventory model. Sprites, loot, recipes and blockstates come from `tools/ui-assets/make_sprites.py`.
The turret is drawn at the modelled size, the barrel hinges at (8, 27.2, 8) of the turret, is pitched 50 degrees and slides back along its axis when it fires.

## What was tested

Everything below was exercised in a real Minecraft 1.20.1 integrated server driven from the client (screenshots in `docs/ui/screens/support/`).
Round 2 (50 checks) covered zones, ownership, buying, supply drops, the Return portal, Fire Support and shovel relocation. Round 3 (59 checks, all passing) added:
the two-block-tall platform and shop (upper half is a structure part; breaking it breaks the block), the cannon menu opening on click, buying upgrades (prices, refusals when
short, maxed levels, the traverse level reaching the turret), the chosen fire support being stored per player, the Quantum Tunneling gate (an underground fire flare is handed back until
the upgrade is bought), all six fire support types (shell counts 6 / 8 / 5 / 1 / 6 / 6, zombies killed or healed or cursed as described, terrain destroyed only by the Bunker Buster,
the caller never hurt), the slowness field, the 5 minute two-trip portal (home, then a second portal back to the throw spot), the healing aura (heals, stays while the parcel is full,
ends 30 seconds after it is emptied), and relocation keeping the upgrades and the traverse level.
Unit tests cover the upgrade and type rules, the turret motor with and without traverse levels, saving of the choice and levels, and the field guide quoting real values.

Not tested: real multiplayer (ownership and the "your flare, your choice" rule are exercised with a single player), other dimensions, TaCZ weapons inside a grid on a dedicated run
(tested by hand by the pack author), the cannon's grinding sound (audio cannot be checked here), and chunk-unload edge cases.
