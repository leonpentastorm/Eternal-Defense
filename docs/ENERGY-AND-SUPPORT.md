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
The platform and the cannon can stand **anywhere inside the zone**, however far apart: design your base around them. Each of them shows a brass **plate with its owner's name** on the back
(the cabinet's back for the platform, the south edge of the cannon's base; the plate text is drawn by a block-entity renderer, the owner name travels in the block entity's update tag).
The **Support Platform and the Exchange Shop are also sold in the Weapon Platform's Workshop tab** (iron, copper, redstone and glass; `WeaponPlatform.purchaseCosts`).

| Piece | What it does |
| --- | --- |
| **Support Platform** (craftable) | Holds the supply grid (3x3 at Mk-1), sells the cannon and flares, upgrades Mk-1 to Mk-4 (grid 4x4, 5x5, 6x6) for 16 / 32 / 64 Reinforced Beacon Plating. The block in front of it must stay clear. Items in the grid never move when it grows. |
| **Support Cannon** (150 energy at the platform) | A hulking 3 x 3 x 2 gun anywhere in the zone. The turret is heavy: it spins up and brakes slowly (about 5 s for a half turn), settles for a second, then fires. It turns toward the **flare**, never toward the player. |
| **Support Flare** (30) | Throw it anywhere. The cannon turns onto it and fires; **12 s after that shot** (outdoors, the chest parachuting in) or **7 s** (underground, at your feet) the grid's contents arrive. The turning time comes on top. Chat counts the seconds down after the shot. |
| **Return Flare** (20) | Throw it. Once the cannon has turned onto it and fired, a purple 1 x 2 portal that always faces you opens where it landed (30 s, owner only). Step in to arrive in front of the platform. Works from any dimension. |
| **Fire Support Flare** (100) | A box of edges marks the area. After the cannon has turned and settled, six shells land **exactly on the flare**, one every 2 s, each hurting every hostile mob touching the box (25 damage). Never hurts players or blocks (except the Bunker Buster, below). |

**Every call goes through the cannon**, one at a time per player: the cannon turns onto the flare (a client-side motor with a grinding sound), settles for a second and only then fires.
If a second Fire Support Flare lands while a barrage is running, the player sees **"Barrage in process"** and the flare waits for its turn. A player who is more than 48 blocks from their cannon
(or in another dimension) is told **"Cannon preparing..."** when it starts to turn and **"You heard cannon fire roaring"** when it fires. When the cannon's chunk is not ticking (or the flare is in another dimension)
the call waits the same turn time without the visible turret.

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
| **Bunker Buster** | One huge bomb. Its box is a **cube 8 blocks each way** (it digs down as well as up) and **every block in it is dug out** over about 12 ticks, top layer first, except bedrock, the beacon and the base's structure blocks. It is the **only support that hurts players**: every player in the box loses **half their maximum health, armor ignored**. If its box touches the beacon zone the flare is handed back with *"Don't throw bunker buster in your own bunker, moron."* Volley upgrades do not apply. |
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
| Area of effect | 120 / 220 / 340 | The box of every fire support grows by 1.5 blocks each way per level (4 -> 5.5 -> 7 -> 8.5; the Bunker Buster's cube by 1 per level, 8 -> 11). The red box, the damage box and the dug cube are always the same box. |
| Dimensional link | 500 | Fire support flares can be called in the Nether, the End and other dimensions (the cannon at home still spins for it; underground rules do not apply where there is no sky). Supply and Return flares are unchanged. |

The cannon is heavy and sounds like it: the traverse gear grinds while it turns, it locks with a clunk, and it only fires after sitting still on target for a second.

**Fire support icons.** Each type has a 64 x 64 painted icon (`textures/gui/fire_support/<type>.png`, plus 32 and 16 pixel versions for crisp small drawing), generated by `tools/ui-assets/fire_icons.py`.
They sit beside the names in the cannon menu, in a big banner at the top of that menu, in the Fire Support Flare's tooltip, and as a banner (icon and the type's name in large letters)
above the hotbar while a Fire Support Flare is in hand. The client learns the choice from a small `SupportHud.Sync` packet sent on login and whenever it changes.

The supply parcel is a real chest: right-click to open it, take what you need, and it folds away once emptied and closed.

## For developers

* Server logic: `SupportCalls` (validation, payment, delivery, return), `SupportData` (who owns which platform and cannon), `BaseZone` (zone rules, placement item, shovel relocation), `SupportShop` (purchases, upgrades), `SupportFlares` (items and the thrown flare), `SupportCrate` (parcel chest entity), `SupportCannon`, `SupportPlatform` (block, block entity, menu), `ArdentEnergy`, `ExchangeShop`.
* Pure, unit-tested rules: `SupportRules` (every number), `SupportCannon.spin` (turret motor), `TextTable`, `RaidWarnings.bucket`, `ArdentEnergy.amount`.
  `SupportTest` also fails if the field guide quotes a number that no longer matches `SupportRules`.
* Network: protocol version **20** (clients and servers must match). Messages: 6 `Announce`, 7 `Exchange open`, 8 `Exchange buy`, 9 `Support buy`, 10 `Support upgrade`, 11 `Cannon open`, 12 `Cannon act`, 13 `Fire support choice` (`SupportHud.Sync`).
* Client: `SupportClient` (renderers, model registration), `SupportScreen`, `ExchangeScreen`, `BeaconPopups`.

### Art

Items use 64 x 64 painted sprites in the style of `industrial_atlas.png` (the three flares each have their own: cyan, purple and red canisters, drawn in flight and on the ground
with the same sprite). The Return Flare portal is a 4-frame 16 x 32 sheet (`textures/entity/return_portal.png`) drawn as a 1 x 2 block billboard that always faces the viewer.

The 3D models are hand-made (source in `docs/art/Create-Arsenal-Support-Gear/`, Blockbench files with their textures embedded) and installed with
`python3 tools/ui-assets/import_support_gear.py`: Exchange Shop and Support Platform Mk-1 to Mk-4 (both two blocks tall, the upper block is an invisible structure part,
like the benches), the Support Cannon (base, turret and barrel), and the supply parcel. The script also paints the Ardent Energy crystal onto the shop's credit display,
builds the cannon's inventory model, and splits the shop into a body and a glass pane (below). Sprites, loot, recipes and blockstates come from `tools/ui-assets/make_sprites.py`.
The shop is a `forge:composite` model of two children: the body (cutout) and the glass pane (translucent). One translucent model sorts all its quads by distance, and from some angles the big pane was drawn first and hid the crates behind it.
The red area box is drawn as edges only: a filled translucent box writes depth and hid the platform and shop standing inside it.
The parachute is no longer a block model: `SupportClient.ParcelRenderer` draws a 12-panel dome (5 blocks across) with twelve cords from vertices (`textures/entity/parachute.png`), so its size is not limited by the model size.
The turret is drawn at the modelled size, the barrel hinges at (8, 27.2, 8) of the turret, is pitched 50 degrees and slides back along its axis when it fires.

## What was tested

Everything below was exercised in a real Minecraft 1.20.1 integrated server driven from the client (screenshots in `docs/ui/screens/support/`).
Round 2 (50 checks) covered zones, ownership, buying, supply drops, the Return portal, Fire Support and shovel relocation. Round 3 (59 checks) added the two-block-tall gear, the cannon menu,
upgrades, the per-player choice, all six fire support types, the slowness field, the two-trip portal, the healing aura and relocation keeping upgrades.

**Round 4 (54 checks in one full run, all passing; a few parts were re-run afterwards to retake screenshots):**

* *Cannon turning, seen from the client:* the turret's yaw on the **client** was sampled every tick during a supply call. It turned (peak 2.2 degrees per tick, never a larger step, so no snap), the grinding sound was triggered 10 times,
  it was on target when it fired, and the shot came 82 ticks after the flare landed. The chat countdown started at the shot (not at landing), the parcel arrived 241 ticks after the shot (12 s plus one tick) and 323 ticks after landing (turn time plus 12 s).
  The bug behind "the cannon snaps toward the flare" was in `CannonEntity`: the update packet reached `load()`, which restarted the turret at its target. It now goes through the same path as the first sync.
* *Messages:* two Fire Support Flares thrown a few ticks apart gave exactly one "Barrage in process", ran as 6 + 6 shots one after the other (smallest gap between shots 39 ticks), and a player 80 blocks away (and one in the Nether)
  got "Cannon preparing..." and then "You heard cannon fire roaring"; a player near the cannon got neither.
* *Bunker Buster:* in a 25-block stone cube the whole 16-block box was dug out (3,179 blocks, obsidian included, only the bedrock block left, stone just outside the box untouched); the player standing inside lost exactly half their health (20 to 10)
  in full diamond armor; thrown inside the beacon zone the flare came back with the message and the cannon never fired; boxes that overlap the zone's edge count as inside. (The flat test world is only a few blocks deep, so its bottom bedrock layer was excluded from the count.)
  In the last bunker-only re-run the harness read the health after natural regeneration (12.5) and reported a tolerance failure; the logged drop was again exactly 20 to 10.
* *Area of effect:* two levels bought for 340 in total; the client draws a radius 7 box; zombies at 6.5 blocks died and zombies at 8.3 lived.
* *Dimensional link:* in the Nether a Fire Support Flare was refused without the upgrade (and kept), allowed with it, killed the zombies in its box, and the home cannon turned and fired for it; a Supply Flare in the Nether was still refused.
* *Placement and purchase:* a cannon 20 blocks from the platform (inside the zone) works; the workshop sold a Support Platform and an Exchange Shop for the listed materials and sold nothing without them.
* *Looks (screenshots):* the owner's nameplate on the back of the platform and the cannon, the Exchange Shop's crates from five angles with the glass in front, the platform and shop inside a flare's box with only its edges drawn,
  the parachute from the side and from below, the Bunker Buster crater, the cannon menu with its icons and big banner, the banner above the hotbar, and the workshop's two new rows.

Unit tests cover the upgrade and type rules (area of effect radius, ten upgrades and the loading of older saves with eight), the cannon hold (one call at a time, a barrage is detected, a stale holder loses the cannon), the Bunker Buster's cube,
the turret motor, saving of the choice and levels, and the field guide quoting real values.

Not tested: real multiplayer (ownership and the "your flare, your choice" rule are exercised with a single player), the End and modded dimensions, TaCZ weapons inside a grid on a dedicated run
(tested by hand by the pack author in an earlier round), the grinding sound itself (the client's sound calls are counted, but audio cannot be heard here), and chunk-unload edge cases.
