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

Hostile mobs killed by a player (or raid attackers) have a **6.67 %** chance (about one kill in fifteen) to drop one Ardent Energy, +1 % per Looting level;
mobs with 60+ max health drop two when they do; a boss always drops one. The standalone edition multiplies the chance by 1.3 (about one in twelve) because it has no factory.
Tune these in `config/arsenal-beacon-common.toml` (`baseDropChance`, `lootingBonusPerLevel`, `bossDrop`; renamed in round 5, so old values are not carried over). Deaths without a credited player (fire, `/kill`, falling) drop nothing.

### Exchange Shop

A placeable trader's booth (3D model, right-click to open), sold in the weapon table's Workshop tab. It only works **inside the zone of a standing Defense Beacon**. Its menu has two tabs:
**Sell** (hand in materials, receive Ardent Energy) and **Buy** (pay Ardent Energy, receive supplies). Buying always costs more than selling, so trading in circles never pays (a unit test checks the defaults).
Offers come from **`config/arsenal-beacon-exchange.txt`** (`[sell]` and `[buy]` sections, `item_id [x count] = energy`), created on first use and re-read every time a shop opens.
An older single-list file is kept as `.old.txt` and replaced. Bad lines are skipped and logged.

| | Sell (you get) | Buy (you pay) |
| --- | --- | --- |
| **Pack** | factory surplus: 2 plating, 2 logistics modules, 2 resonance coils, 1 restoration matrix, 16 brass, 32 andesite alloy, 2 precision mechanisms, 8 electron tubes, 32 cartridge cases or 32 propellant: **1 energy each** | 16 Ammo Coins 3; plating 2, logistics module 2, resonance coil 3, restoration matrix 4; precision mechanism 3; 8 brass 3; 16 andesite alloy, cartridge cases, propellant 2 each |
| **Standalone** | rare vanilla: diamond 2, blaze rod 1, ender pearl 1, ghast tear 2, netherite scrap 3, shulker shell 4, nether star 20, 2 emeralds 1, 8 gold 1, 16 quartz 1 | 16 Ammo Coins 3; 16 iron, copper, gunpowder or redstone 1; 8 gold 2; blaze rod 3, ender pearl 3, diamond 6 |

So a late-game factory sells what it does not need and buys ammunition, while a player without a factory farms Ardent Energy and buys parts at a premium. Purchases are validated on the server (distance, price, inventory room, beacon zone) and are free in Creative.

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
| **Support Cannon** (12 energy at the platform) | A hulking 3 x 3 x 2 gun anywhere in the zone. The turret is heavy: it spins up and brakes slowly (about 5 s for a half turn), settles for a second, then fires. It turns toward the **flare**, never toward the player. |
| **Support Flare** (3) | Throw it anywhere. The cannon turns onto it and fires; **12 s after that shot** (outdoors, the chest parachuting in) or **7 s** (underground, at your feet) the grid's contents arrive. The turning time comes on top. Chat counts the seconds down after the shot. |
| **Return Flare** (2) | Throw it. Once the cannon has turned onto it and fired, a purple 1 x 2 portal that always faces you opens where it landed (30 s, owner only). Step in to arrive in front of the platform. Works from any dimension. |
| **Fire Support Flare** (6) | A box of edges marks the area. After the cannon has turned and settled, six shells land **exactly on the flare**, one every 2 s, each hurting every hostile mob touching the box (25 damage). Never hurts players or blocks (except the Bunker Buster, below). |

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
| **Bunker Buster** | One huge bomb. Its box is a **cube 8 blocks each way** (it digs down as well as up) and **every block in it is dug out** over about 12 ticks, top layer first, except the beacon and the base's structure blocks and anything super hard: **obsidian, crying obsidian, bedrock** and any block with an explosion resistance of 1200 or more (reinforced deepslate, respawn anchors, ancient debris). It is the **only support that hurts players**: every player in the box loses **half their maximum health, armor ignored**. If its box touches the beacon zone the flare is handed back with *"Don't throw bunker buster in your own bunker, moron."* Volley upgrades do not apply. |
| **Healing Barrage** | Like the barrage, but every shell bursts into Instant Health II and Regeneration for players in the box. |
| **Curse of Debilitation** | Hostile mobs in the box get Wither II, Poison II, Weakness II, Slowness III, Blindness and Glowing. |

Upgrades stay with you when you pick the cannon up with the shovel:

Prices differ by edition. The **pack never charges Ardent Energy for an upgrade**: cannon upgrades, beacon upgrades and repairs are paid in the mod's factory parts (so a Create factory is the fast route; farmers can buy parts at the Exchange).
The **standalone** edition has no factory, so it pays Ardent Energy for cannon upgrades, beacon upgrades and repairs.

| Upgrade | Standalone (energy) | Pack (parts) | Effect |
| --- | --- | --- | --- |
| Faster traverse | 3 / 6 / 10 | 6 / 12 / 24 plating | Turret 40 percent faster per level: a shorter spin time. |
| Rate of fire | 4 / 8 / 12 | 5 / 10 / 20 coils | Ticks between shells 40, 33, 26, 20. |
| More volley | 5 / 10 / 16 | 6 / 12 / 24 logistics modules | +2 shells per level (not the Bunker Buster). |
| Damage | 5 / 10 / 16 | 6 / 12 / 24 coils | +25 percent per level (curse durations too). |
| Quantum tunneling | 14 | 12 restoration matrices | Fire support flares work below ground. Without it a flare that lands underground is handed back. |
| Slowness field | 8 | 10 coils | A landed fire support flare slows every hostile mob in its whole area. |
| Lasting portal | 6 | 10 logistics modules | The return portal stays 5 minutes and works twice: home, then back to where it was thrown. |
| Healing aura | 6 | 8 restoration matrices | A supply flare heals everyone within 6 blocks, until 30 seconds after the parcel is emptied. |
| Area of effect | 5 / 9 / 14 | 5 / 10 / 20 plating | The box of every fire support grows by 1.5 blocks each way per level (the Bunker Buster's cube by 1). |
| Dimensional link | 18 | 20 restoration matrices | Fire support flares can be called in the Nether, the End and other dimensions. Supply and Return flares are unchanged. |

Beacon upgrades cost 8 / 16 / 32 / 64 / 128 parts per level in the pack (halved in round 5 so the first one arrives quickly) and 4 / 8 / 14 / 22 / 32 Ardent Energy in the standalone edition; a repair is 8 plating or 6 energy.

The cannon is heavy and sounds like it: the traverse gear grinds while it turns, it locks with a clunk, and it only fires after sitting still on target for a second.

**Fire support icons.** Each type has a 64 x 64 painted icon (`textures/gui/fire_support/<type>.png`, plus 32 and 16 pixel versions for crisp small drawing), generated by `tools/ui-assets/fire_icons.py`.
They sit beside the names in the cannon menu, in a big banner at the top of that menu, in the Fire Support Flare's tooltip, and as a banner (icon and the type's name in large letters)
above the hotbar while a Fire Support Flare is in hand. The client learns the choice from a small `SupportHud.Sync` packet sent on login and whenever it changes.

The supply parcel is a real chest: right-click to open it, take what you need, and it folds away once emptied and closed.

## Economy and pacing (round 5)

The numbers are tuned against this income, in Ardent Energy per raid:

* a guaranteed payout with every won raid: **2 + 2t** standalone, **1 + t** pack (t is the reward tier, 0 to 10; a boss raid pays 50 percent more);
* about one drop per 12 (standalone) or 15 (pack) kills of its attackers: a tier 0 raid has about 20, a tier 3 raid about 80, so 1 to 7 energy;
* a special raid (below) adds **3 + 2t**;
* target farming on top, and the Exchange: a pack factory can also sell its surplus.

What that buys: basic support (cannon 12, supply 3, return 2, fire 6) is affordable after the first two or three raids, and **special raids only start at the fourth raid**, so support is there before the raids that need it.
The first cannon upgrades cost 3 to 5 energy (about one raid each); the whole cannon is 185 energy (about 25 average raids); the first beacon upgrade costs 4. A bad raid still pays its guaranteed share only if won, but
ordinary play (night mobs, farms) and the Exchange sell-offs always add up, and repairs are cheap. A unit test (`upgradePricesAreSane`) keeps every level dearer than the last and the sums inside these bounds.

## Special raids

From the fourth raid on, every raid has a **50 percent** chance to be special (boss raids too: the boss still arrives in the last wave). The kind of the **next** raid is decided and announced (chat and toast) when a raid ends, and shown in the Overview.
A won special raid adds **3 + 2 x tier** Ardent Energy to the reward.

| Raid | What comes |
| --- | --- |
| **Air raid** | Vexes, phantoms (fire-resistant) and blazes. They ignore walls and do not dig, so the base needs anti-air coverage, not thicker walls. |
| **Paratroopers** | Heavies (specialists below tier 3) at half the usual count, spawned 46 blocks above the beacon with slow falling: they sink at 0.16 blocks per tick (about 12 seconds for the whole drop) under a **red and white parachute**, can be shot dead on the way and take no fall damage. The chute is drawn from the same canopy code as the supply drop; a small packet tells clients which mobs wear one (mob effects are not synced). |
| **Siege** | Creepers (30 percent), skeletons, pillagers (from tier 1) and strays (from tier 3). A creeper stuck at a wall ignites there; the explosion goes through the usual damage journal, so everything is restored after the raid, and the shooters fire through the holes. |
| **They are thousands** | Only zombies and husks, twice the count, spawning every 8 instead of 20 ticks with twice the alive-limit (up to 96). No heavy, ranged or Special Forces mobs. |

### Testing special raids (operators)

* `/arsenal next-raid <normal|air|paratroopers|siege|swarm>` sets what the next raid will be (shown in the Overview).
* `/arsenal test-raid <type>` sets that type and starts the raid right away (during preparation only). `/arsenal test-raid` alone starts the raid that is already scheduled.
* The introduction raid is always ordinary, whatever is set; finish it (or `/arsenal test-raid` through it) first.

## Other round 5 changes

* **The beacon is the reward chest.** Reward Chest in the Overview opens a 54-slot chest menu backed by the campaign data; no block is placed. Overflow stays queued, the contents are saved, and they spill out if the beacon is destroyed.
* **Return zone.** The two blocks in front of a platform (two tall) cannot be built on while the platform stands ("KEEP RETURN ZONE CLEAR"); placing the platform needs them clear. A Return Flare delivers to the front cell or the nearest safe cell within 3 blocks; with none, the flare is refused before it is used.
* **Owner sign.** The platform's name plate is a crooked sign on a post above it, drawn by the block entity renderer only, so there is no hitbox and the platform is still two blocks tall. The cannon keeps its plate.
* **Zone outline flicker.** The client treated the campaign state as stale after 4 seconds without a packet, so a lag spike hid the outline until the next one. The limit is now 30 seconds and the outline has its own vertex buffer (it no longer shares one with other line drawing). I could not reproduce the flicker here, so this is a fix for the most likely causes, not a confirmed one.

## For developers

* Server logic: `SupportCalls` (validation, payment, delivery, return), `SupportData` (who owns which platform and cannon), `BaseZone` (zone rules, placement item, shovel relocation), `SupportShop` (purchases, upgrades), `SupportFlares` (items and the thrown flare), `SupportCrate` (parcel chest entity), `SupportCannon`, `SupportPlatform` (block, block entity, menu), `ArdentEnergy`, `ExchangeShop`.
* Pure, unit-tested rules: `SupportRules` (every number), `SupportCannon.spin` (turret motor), `TextTable`, `RaidWarnings.bucket`, `ArdentEnergy.amount`.
  `SupportTest` also fails if the field guide quotes a number that no longer matches `SupportRules`.
* Network: protocol version **21** (clients and servers must match). Messages: 6 `Announce`, 7 `Exchange open`, 8 `Exchange buy`, 9 `Support buy`, 10 `Support upgrade`, 11 `Cannon open`, 12 `Cannon act`, 13 `Fire support choice` (`SupportHud.Sync`), 14 `Parachute worn` (`RaidTypes.Chute`).
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
* *Bunker Buster:* in a 25-block stone cube the whole 16-block box was dug out (3,179 blocks, obsidian included at the time, only the bedrock block left, stone just outside the box untouched; since round 5 obsidian, crying obsidian, bedrock and anything with a blast resistance of 1,200 or more is spared, see below); the player standing inside lost exactly half their health (20 to 10)
  in full diamond armor; thrown inside the beacon zone the flare came back with the message and the cannon never fired; boxes that overlap the zone's edge count as inside. (The flat test world is only a few blocks deep, so its bottom bedrock layer was excluded from the count.)
  In the last bunker-only re-run the harness read the health after natural regeneration (12.5) and reported a tolerance failure; the logged drop was again exactly 20 to 10.
* *Area of effect:* two levels bought for 340 in total; the client draws a radius 7 box; zombies at 6.5 blocks died and zombies at 8.3 lived.
* *Dimensional link:* in the Nether a Fire Support Flare was refused without the upgrade (and kept), allowed with it, killed the zombies in its box, and the home cannon turned and fired for it; a Supply Flare in the Nether was still refused.
* *Placement and purchase:* a cannon 20 blocks from the platform (inside the zone) works; the workshop sold a Support Platform and an Exchange Shop for the listed materials and sold nothing without them.
* *Looks (screenshots):* the owner's nameplate on the back of the platform and the cannon, the Exchange Shop's crates from five angles with the glass in front, the platform and shop inside a flare's box with only its edges drawn,
  the parachute from the side and from below, the Bunker Buster crater, the cannon menu with its icons and big banner, the banner above the hotbar, and the workshop's two new rows.

Unit tests cover the upgrade and type rules (area of effect radius, ten upgrades and the loading of older saves with eight), the cannon hold (one call at a time, a barrage is detected, a stale holder loses the cannon), the Bunker Buster's cube,
the turret motor, saving of the choice and levels, and the field guide quoting real values.

**Round 5 (real client, integrated server, checks logged as they ran; the pack edition 34 of 34, the standalone edition 33 of 33, and the raid-type part 11 of 11):**

* *Reward chest:* the Reward Chest button opened a 54-slot chest menu backed by the beacon; 54 slots filled from a queue of 56 stacks and the 2 overflow stacks stayed queued; no chest block was placed; the contents survived a save and load.
* *Return zone:* building inside the two cells in front of the platform was refused, building next to it still worked; with a clear front the return lands on the front cell, with the front blocked it lands on a nearby safe cell, and with every cell blocked the return is cancelled and the flare is refused before it is used.
* *Exchange:* Buy and Sell tabs open; selling 2 reinforced plating paid 1 Ardent Energy; buying spent energy and gave the goods; a plating costs 2 to buy and pays 0.5 to sell.
* *Pack prices:* a beacon upgrade is refused when one resonance coil short, the first costs 8 coils and no energy; repair costs 8 plating; the first Traverse level costs 6 reinforced plating, never Ardent Energy. The standalone run checked the same upgrades in Ardent Energy.
* *Bunker Buster:* obsidian, crying obsidian, bedrock and reinforced deepslate stay; the stone around them is gone.
* *Raid types:* a Paratrooper spawned about 46 blocks over the base wearing slow falling, was a heavy at tier 3, and landed with its full 76 health (no fall damage). In the client screenshot it hangs under a red and white canopy (`paratrooper_red_parachute.png`).
  Air, Siege and They are thousands each produced only their own mobs (Siege with creepers and ranged mobs, They are thousands with no heavy and no ranged), and the announcement appeared in chat and was stored for the Overview (`info_next_raid.png`).
* *Looks (screenshots):* the crooked sign on its post above the platform (`sign_on_platform.png`), the Exchange in both tabs (`exchange_buy_tab.png`, `exchange_sell_tab.png`).

Unit tests (58 in the module) cover the Economy tables (every level dearer than the last, sums inside the bounds above), the ardent drop chance, the special-raid roll and mixes, the new lang keys, and the field guide quoting real values.

Not tested in round 5: the zone outline flicker itself (I could not make it happen, so the fix is a best guess, see above); a ground spawn of Air, Siege and They are thousands raids in a running world (the spawn finder found no valid spot in the small test arena, so those three were checked through their mob mixes, not through a full raid);
the pack prices with the real Create and KubeJS items (the test world has only this mod's parts, so the tests exercised those); migration of an old `arsenal-beacon-exchange.txt` from before round 5 (reasoned through, not run on a real old file); and every price and drop number is a design estimate that has not been played through over many raids.

Not tested: real multiplayer (ownership and the "your flare, your choice" rule are exercised with a single player), the End and modded dimensions, TaCZ weapons inside a grid on a dedicated run
(tested by hand by the pack author in an earlier round), the grinding sound itself (the client's sound calls are counted, but audio cannot be heard here), and chunk-unload edge cases.
