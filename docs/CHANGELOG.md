# Change log

## 1.0.0 — Release: Eternal Defense (2026-10-14, `dev` merged into `main`)

`dev` merged into `main` at the owner's request; tag `v1.0.0`. No packet changed: network protocol stays **41** (the channel is now `eternal_defense:control`, so a 0.0.x client cannot join). Rules: GDD 1.0.0; tests: `docs/ROUND-1.0.0-TESTING.md`.

* **One name** (owner): the project, the three mods and everything in them are Eternal Defense. Mod ids `eternal_defense`, `eternal_defense_gun_guide`, `eternal_defense_displays`; item, block, sound and data ids, config files (`eternal-defense-*.toml`, `eternal-defense-exchange.txt`), the command `/eternaldefense`, the pack's scripts, quest book and welcome message follow. All three jars are version 1.0.0. **Start a fresh world:** worlds and configs of 0.0.x builds are not converted (their blocks and items of the mod are gone); the old-default upgrades of the Exchange file, FOB specialties saved by number and 0.0.38 block entities were removed.
* **Two builds of the main mod** (owner): `eternal-defense-1.0.0.jar` with the radio voice lines, and `eternal-defense-1.0.0-novoice.jar`, where each of the 15 spoken lines plays a game sound (fire support calls: the beacon's select sound; supply calls: a note block bit; recall calls: the beacon's activate sound; Specialty Seller orders: a villager's yes). Install one of them.
* **Faster raids:** a raider without a path to the beacon no longer asks for one every tick; it asks every 10 ticks, walks straight on in between and heads for a waypoint 16 blocks ahead when the beacon is far. In the profiled raid the server tick fell from 13.1 ms to 9.1 ms on average and the mod's share of server time from 39 % to 19 %. Idle animations and the hard raid boss bar cost less. No rule changed.
* **Clean jars and code** (owner): the jars carry no tests, QA harnesses, test structures, readme or notes (GameTests moved to a development-only `gametest` source set); every code comment is one short neutral line; `mods.toml` files carry no comments.
* Tools: `make-testpack.py --novoice` and a migration of 0.0.x profile exports to the new ids; `package-dev.py` makes the release zip of each build; QA harnesses `QaProfile` and `QaVoice` (`QA_NOVOICE=1`).

## 0.0.43 — The owner's Exchange and mine list (2026-10-14, branch `feature/logistics-hub`)

On `feature/logistics-hub` after 0.0.42. No packet changed: network protocol stays **41**. Rules: GDD 0.0.43; tests: `docs/ROUND-0043-TESTING.md`.

* **Exchange** (owner): iron is sold as ore at twice the price, 16 Iron Ore for 2 Ardent Energy (was 16 iron ingots for 1; smelt it, or crush it with Create for about 1.75 ingots each); the bulk lot is 8 iron blocks for 8 (was 16 for 8); 16 andesite alloy cost 1 (was 2). A world whose offers file is still the untouched 0.0.41/0.0.42 default gets the new prices the next time the shop opens; an edited file is kept.
* **The FOB's mine** (owner: clay was missing): it can send clay, and stones for building that most bases have to travel for: calcite, dripstone, red sandstone, blackstone, and Create's asurine, crimsite, ochrum, veridium, limestone, scoria and scorchia. A clay block is worth as much as one redstone dust; a stone block half that, as andesite (so twice as many come). The new choices come after the first six, so a FOB's saved choice is unchanged. The mine's Choose button says so.
* QA: new harness `QaRound43` (Create running).

## 0.0.42 — The owner's list after 0.0.41: the corpse crash, placement, the return zone, magazines (2026-10-13, branch `feature/logistics-hub`)

On `feature/logistics-hub` after 0.0.41. No packet changed: network protocol stays **41**. Rules: GDD 0.0.42; tests: `docs/ROUND-0042-TESTING.md`.

* **Fix: the game crashed looking at a corpse** (the owner's "Athena and Corpse" crash; the 0.0.41 satellite log is the same one): a `StackOverflowError` in Player Animator drawing a Corpse mod body. The Corpse mod draws the body as a copy of the dead player, gun still in hand, that the world never ticks; TaCZ starts a Player Animator fade on a player holding a gun every frame, and only a tick ends a fade, so on the copy each new fade wrapped the last until the stack overflowed (reproduced: 217 frames, 217 nested fades). New `StandInAnimations`: before such a body is drawn, its animation layers are moved on once per game tick, as the world does for a real player (Player Animator's own API, by reflection; nothing without it). With it the chain stayed at 12 to 14. The Idle animations switch plays no part (checked with it on and off).
* **Placement never eats the item, and the preview turns red** (owner): a FOB Beacon used in a hole one block high or outside a village vanished from the hand until a chest-like screen was opened: the player's game placed it before the server answered, the server refused, and nothing sent the hand back. Now every block item of the mod refuses a spot with a block or a creature in the way of any of its cells on the player's side too (`PlacementGuard.blocked`: FOB Beacon, Exchange, platforms, cannon, satellite, Command and Enchanting Tables, Mess Halls, Drink Stations, pots, dispensers, MRE Station, weapon tables, Supply Catapult, Hub, gates), and every refusal from the server sends the hand slot back (also when the return zone or the raid lock cancels a placement). The placement previews (and the FOB Beacon's own) turn red, model and outline, with "Something is in the way" under the crosshair for the FOB. A block model's colour only reaches tinted faces, so the ghost is now tinted on every vertex.
* **Return zone: falling blocks** (owner): sand, gravel or anything solid that falls or is pushed into a Support Platform's return zone is broken out twice a second and drops as an item, with the KEEP RETURN ZONE CLEAR warning; placing there was already refused (and now gives the item back at once). A cannon whose body would reach into a return zone is refused.
* **Ammo table magazines** (owner: "used to be able to buy magazines"): rounds are shared by many guns, magazine families (one per round count) are not, so a gun's rounds could be listed an Age or more before its magazine: in the owner's pack 108 of 425 magazine guns (for example the HK45: .45 ACP at Age 2, its 10-round magazine at Age 3). A standard magazine now unlocks no later than the rounds it holds, as the table lists them; extended magazines stay one Age per level above. Scanned with all of the owner's gun packs and TaCZ Magazines config: 0 guns left with rounds before magazines.
* Guide: the zone page says the preview turns red; the FOB, Support Platform and weapon-table pages say what changed.
* QA: `tools/qa/setup-qa.sh` takes `QA_MODS=<dir>` (extra runtime mods: TaCZ Magazines, Corpse, Player Animator); new harnesses `QaRound42` and `QaMagazines`.

## 0.0.41 — One edition, the test pack, the owner's list (2026-10-13, branch `feature/logistics-hub`)

On `feature/logistics-hub` after 0.0.40. Packets changed (the beacon status carries `livesLeft` and `hubs`; the edition flag is gone): **network protocol 41**. Rules: GDD 0.0.41; tests: `docs/ROUND-0041-TESTING.md`.

* **The standalone edition is discontinued** (owner: "focus all on the pack version"). One jar per mod, no `-standalone.jar`; the standalone prices, Ardent rules, recipes, guide lines and tests are gone; `GuideText` has no edition markers; key hints for mods the pack may lack (emotes, shaders, backpacks, quests, map) drop out of a card when their key is missing.
* **Required mods** in the beacon's `mods.toml` (Forge names any that is missing): TaCZ, TaCZ Attributes 1.4, Create 6, KubeJS and KubeJS Create, LesRaisins Tactical Equipments 0.4.3+, and JEI on the client. Checked on a production dedicated server with the owner's pack: our KubeJS scripts load with 0 errors.
* **Fix: the Logistics Hub crashed the real game when placed** (0.0.40, `NoSuchMethodError` on `setChanged` of the Hub's Create part): the release jar's name mapping did not see Create's classes; it does now (`reobf` libraries), checked with `javap` and on the production server.
* **The test pack** (`tools/release/make-testpack.py`): the owner's exported Eternal Defense profile with this build's three jars and the repository's KubeJS scripts; Tactical Movement 1.0.0, a client-only mod in the pack's overrides that stopped a dedicated server from starting, moves to `client-overrides`. `tools/release/install-mrpack.py` installs a pack into a server or client folder, every download checked against its SHA-1 and SHA-512.
* **MRE Station:** the owner's new model: the press rod runs up into the hopper, so the press no longer floats when it comes down; a guide collar at the bay ceiling stays put.
* **Ammo and attachment tables:** holding a gun whose parts all wait in later Ages, the empty list says "There are 3 more attachments for this weapon in later Ages. Upgrade your table (Upgrade tab) to unlock them." (one: "There is 1 more ..."); "Hold a gun and press [key]" only shows with no gun in hand. The pop-up that used to say this stopped opening in the Mess Hall v5 UI work; its leftover code is removed.
* **Radio, one line at a time:** flare calls (all three kinds) and Specialty Seller orders share a player's radio; while a line plays, another call or order is carried out but adds no voice.
* **Reinforced Beacon Plating:** compacting 2 iron, 2 golden and 2 copper sheets gives 1 plating, no heat (was 4 hardened alloy and a brass sheet, heated, for 4).
* **Exchange:** a blaze rod costs 6 Ardent Energy (was 3). An untouched exchange file gets the new default; an edited one is kept.
* **Mess Hall and the Hubs:** the hall counts the food in the Home Zone's Logistics Hubs when it decides what can be cooked, so with the food only in a Hub you open the hall, pick effects, press Gather ingredients and cook (before, the button said the pack lacked food and stayed off). Take back still puts the table's food in your pack. Texts say "pack and Hubs".
* **Raid skull:** during a raid the beacon's health line (HUD and Overview) shows a skull and the defender deaths left before the next one costs the beacon a tenth of its health: 3, 2, 1 (red), then 3 again.
* **Logistics Hub Zone:** in the Home Zone of a base with a working Hub, players get a "Logistics Hub Zone" effect ("You are in the Logistics Hub zone."); the beacon's Overview says "This base has N active Logistics Hubs" or "This base does not have any active Logistics Hub".
* **Side quest: the mod's blocks are worth base score.** Each of the mod's 20 installations that can stand in your zone (Exchange Shop, Support Platform and Cannon, the four weapon tables, Mess Hall, Cook Pot, Bowl and Milk Dispensers, Drink Station, Brew Pot, Cup Dispenser, MRE Station, Logistics Center Hub, Command Table, Satellite Beacon, Supply Receiving Gate, Tactical Enchanting Table) adds 25 points; all 20 add 500, exactly one more reward tier (the owner chose "exactly +1 level" when asked how 1000 points should map to tiers, since a tier is 500 points). The Overview's score grid has five columns now, with Installations ("100 (4/20)") and Total.
* **Field Guide Reference:** the base zone and weapon station pages repeated one line 8 times (since 0.0.9); each line is there once now, and a test keeps it so.
* **Satellite crash report:** the owner's crash log shows a `StackOverflowError` inside Player Animator 1.0.2-rc1 while it renders a Corpse mod body, not in this mod; the ~1,900 `ClassCastException`s in Create's goggle overlay carry no stack trace (the JVM drops them after many repeats). See the testing record for what was checked.

## 0.0.40 — The Logistics Center Hub (2026-10-13, branch `feature/logistics-hub`)

On `feature/logistics-hub`, made from `feature/animation-pass` at 0.0.39 (which holds the Drink Station, Mess Hall and MRE work). No packet changed: **network protocol 40**. Rules: GDD 0.0.40 (Part K, section 5.11); tests: `docs/ROUND-0040-TESTING.md`.

* **Logistics Center Hub** (the owner's model): a 3 x 3 x 3 depot with 270 slots (5 pages of 54), sold at Workshop Fabrication, the dearest installation (proposed: 64 Reinforced Beacon Plating or 40 Ardent Energy, plus 64 iron, 16 chests, 8 hoppers). It works only inside the Home Zone; elsewhere it does nothing and says so. Right-click any of its 27 blocks to open it.
* **Its items are kept with the world**, not in the block: chunk borders, unloaded chunks and reloads cannot lose or copy them. It cannot be mined while it holds items ("Empty the Hub first"); creative breaks drop the contents; explosions, fire, pistons and mobs cannot touch it. The Recovery Shovel picks it up with its items waiting inside.
* **Automation:** every face of every block is the same inventory: hoppers and droppers, and in the pack Create's belts (straight in, no funnel needed), funnels with item or attribute filters, chutes, smart chutes and mechanical arms. Automation never posts a chat line.
* **The supply source:** every recipe, upgrade, purchase, repair, Exchange trade and Gather in the Home Zone now takes from your pack first, then from the Home Zone's Hubs, nearest first, all or nothing. Ardent Energy is an item, so a Hub can hold it and pay with it. FOB actions stay pack-only.
* **The Hub line:** when a Hub pays part of a request, you and everyone in the Home Zone read "Dev just pulled 54 Iron Ingot from the Hub" (or "... spent 12 Ardent Energy from the Hub"): one line per action, three kinds and then "and N more", your pulls within 2 seconds added up; also in the server log.
* **FOB Beacon:** its shop now builds a Supply Catapult (the same materials as at Workshop Fabrication, from your pack). Workshop Fabrication still sells it.
* **Command Table:** the FOB card's Contact Specialty Seller button moved up 3 px; it no longer touches the card's lower edge.
* Fixes found while testing: a Hub that was just placed never checked the Home Zone (an overflow), so it never worked; the Recovery Shovel opened the Hub's screen instead of picking it up; for up to a second after items went in, a Hub could still be mined (the break guard now reads the Hub's own inventory on the server).
* New Field Guide card: Logistics Center Hub.

## 0.0.39 — Idle animations (2026-10-13, branch `feature/animation-pass`)

On `feature/animation-pass`, made from `feature/drink-station` at 0.0.38 (the owner asked for a new branch with everything so far). No packet changed: **network protocol 40**. Rules: GDD 0.0.39 (section 7); tests: `docs/ROUND-0039-TESTING.md`.

* **The blocks move** (owner: "the flag in the FOB model or the satellite dish being able to bend up and down"): the FOB Beacon's flag waves as a cloth of four strips, its whip antenna sways, its emitter ring turns; the Satellite Beacon's dish picks a new heading every 9 seconds and turns and tilts to it (-110 to +50 degrees round, -15 to +20 up and down, never into its own antenna); the Command Table's antenna sways, its warning light turns and flashes, a sweep circles the radar screen; the Defense Beacon's rings (Mk I to IV) rise and fall up the core and turn a quarter at a time, its emitter ring turns; the Support Platform's emitter floats and turns; the Tactical Enchanting Table's book floats, turns and breathes, its sparks twinkle, its ring spins; the Mess Hall Mk I's roast turns on its spit over a flickering fire; the Cook Pot's and Brew Pot's flames flicker and the Brew Pot's paddle stirs; the MRE Station's press comes down and seals with its lever while its rollers turn; the cannon's crane hook swings.
* Only for show. Each block keeps its own rhythm (from its position), the game time drives it (pausing stops it), and at rest every part sits exactly where the owner's model has it. Item icons and the owner's model files are unchanged: `tools/art/split_animated.py` writes a model without the moving parts for the world and one model per part.
* **Settings > Idle animations** (beacon screen, on by default, saved per player): off, every block stands still in its normal pose. The music volume line under the music switch now shows only when the game's music volume is 0.
* The FOB Beacon, Satellite Beacon, Command Table and Tactical Enchanting Table have a block entity now (it holds no data). In a world from before 0.0.39 they get it when their chunk loads, so their parts are not missing.
* The placement ghost still shows the whole model, its moving parts at rest. The Field Guide's Settings section mentions the switch.

## 0.0.38 — The MRE Station (2026-10-13, branch `feature/drink-station`)

On `feature/drink-station`, after 0.0.37. No packet changed: **network protocol 40** (the station's buttons use the existing menu-button packet). Rules: GDD 0.0.38 (Part F3); tests: `docs/ROUND-0038-TESTING.md`.

* **MRE Station** (owner): a new kitchen block (the owner's model, 1 x 1 x 2), the 8th card of Workshop Fabrication's Kitchen group (right column, bottom): 4 Reinforced Beacon Plating (pack) or 4 Ardent Energy (standalone), 8 iron ingots and a smoker. One screen: six food slots, a Marines Fuel slot, a Paper slot, the output, **Make MRE** and **Take back**, and the live totals of one MRE (nutrition out of 20 with a mark per point, saturation). Holding a food over a slot previews it; one that would pass 20 is shown in red and the slot refuses it.
* **MRE**: no gain, no loss. Its nutrition is the sum of the foods' nutrition (at most 20, one food is enough); its saturation the sum of what each food adds when eaten alone (vanilla's nutrition x modifier x 2), stored as the plain sum, at most 20. Each MRE takes one of each food in the slots, 1 Marines Fuel and 1 Paper; the server checks everything again on Make and spends nothing when it refuses. Any food, modded too; every effect and always-edible are dropped (golden apples, honey, suspicious stew, pufferfish and the rest give only their food); bowls and bottles come back to the pack (or drop when it is full). Eaten through vanilla's own eating in 16 ticks (as fast as dried kelp), only when hungry, the whole amount at once, nothing left over. MREs with the same totals stack to 64.
* **Marines Fuel**: a wax crayon; a clay ball and any dye make 2. Not food.
* Field Guide page "MRE Station & MREs" (cards MRE Station, MRE, Marines Fuel, and the numbers in Reference). The Armor Platform's standalone gear list no longer picks up the mod's own items (it matched the MRE's id).

## 0.0.37 — Bowls and cups are used up; endless home effects stop blinking (2026-10-13, branch `feature/drink-station`)

On `feature/drink-station`, after 0.0.36. No packet changed: **network protocol 40**. Rules: GDD 0.0.37; tests: `docs/ROUND-0037-TESTING.md`.

* **Bowls and Paper Cups are used up** (owner): eating a serving at a Cook Pot uses up the bowl, drinking at a Brew Pot uses up the Paper Cup, so the Bowl and Cup Dispensers are worth keeping stocked. In creative they are kept, as vanilla does.
* **A drink's effect no longer comes and goes at home** (owner's report: Haste granted and taken away over and over). The mod's own trackers hold still (checked in the real client); another mod in the pack takes the endless effect away and the tracker puts it back. Now, when something takes one of the mod's endless home effects (drink, meal, Home or FOB Zone) away twice within 30 seconds, that effect shows at home as a 20-minute timer, topped up every minute, instead of endless. You see one chat line, and the server log names what took it away.

## 0.0.36 — Fire support rework: Ablaze, Spotter Arrows, a curse until death (2026-10-12, branch `feature/drink-station`)

On `feature/drink-station`, after 0.0.35. No packet changed: **network protocol 40** as in 0.0.35. Rules: GDD 0.0.36; tests: `docs/ROUND-0036-TESTING.md`. The three types keep their ids and places in the list, so saved choices stay.

* **Napalm Carpet: Ablaze** (owner): the burning ground and its damage are as before, and every hostile mob that can burn and stands in it is now set Ablaze until it dies: 4 damage a second (more with the Damage upgrade, half for a raid boss), even in water or rain. Blazes and other fire-proof mobs, and mobs with Fire Resistance, are not affected. A raid never learns it as a fire trap.
* **Spotter Arrows** replace the Arrow Cluster Bomb (owner): each volley hits every hostile mob in the box once for 4 (no knockback, no limit on how many) and marks it until it dies: it glows and takes 30% more from every kind of damage, guns included (a raid boss 15%). The falling arrows are only for show.
* **Curse of Debilitation** (owner): its curses (Wither II, Poison II, Weakness II, Slowness III, Blindness) now last until the mob dies; it no longer makes mobs glow, and the Damage upgrade no longer changes it.
* The cannon menu, the call announcement and the Field Guide's fire support page say what the three do now.

## 0.0.35 — Dearer meals, three new meal effects, the Drink Station (2026-10-12, branch `feature/drink-station`)

On `feature/drink-station` (from `dev`, which now holds 0.0.34). **Network protocol 40** (a new packet: the Drink Station screen's view). Rules: GDD 0.0.35; tests: `docs/ROUND-0035-TESTING.md`. The owner asked for "0.0.34"; that number already belongs to the Supply Catapult round, so this round is 0.0.35.

* **Meals cost more** (owner): a stew batch takes three times the food (12/21/30/36 units at Mk I to IV); a pot still holds 3/5/7/9 servings. A sandwich takes 2 of each filling and 2 bread. Effects, meal length and doubling are unchanged.
* **Three new meal effects** (owner), in the place of the old ones (same buttons, same foods): **Hardy** (you get hungry 15% slower) replaces Mobility, **Blast Ward** (20% less explosion damage) replaces Hearth, **Projectile Ward** (15% less damage from arrows, bolts, fireballs and tridents; not gun bullets) replaces Springy Step. Doubled at home, capped at 60/80/90%. A meal saved with an old effect simply runs out.
* **Drink Station Mk I to IV** (owner's models): a brewing stand with a 54-slot potion storage (64 bottles of one potion a slot); from Mk II it mixes potions into drinks with 1, 2 or 3 effects. Finished bottles move into the storage by themselves. More than 256 bottles inside: it cannot be broken until you take some out.
* **Brew Pot**, **Cup Dispenser**, **Paper Cup** and **Latte** (owner's models): a pot holds one drink, drunk with a Paper Cup (you keep the cup); a Latte is a drink to carry (10 minutes, up to 2 effects, stacks to 16).
* **Sweetener**: Regeneration potions fill a tank; 1,000 mB buys +10 minutes on a Brew Pot drink (up to +20).
* **The active drink** sits beside your meal: its timer stops in your base or an active FOB, logging out pauses it, dying or the Milk Dispenser and Milk Bottle end it (a milk bucket does not). It never takes away a potion effect you got elsewhere.
* Workshop Fabrication sells the Drink Station, the Brew Pot and the Cup Dispenser in the Kitchen group. The Field Guide has a Drink Station page and a card for the three new meal effects.
* **Drink Station screen tidied** (owner): the sweetener slot, its bar and the +0/+10/+20 minutes button sit next to the mixing slots; the Brew Pot buttons look like the Mess Hall's stew pot buttons (pot, number, the drink's colour, a bar); the effect tiles are one row with a page button; the station's notices are shorter and always fit in one line.

## 0.0.34 — The live catapult, the box as a supply drop, fishing and husbandry as one, a cap on the specialty (2026-10-12, branch `feature/supply-catapult`)

On `feature/supply-catapult`. **Network protocol 39** (a new packet: the catapults' live state for the Command Table). Rules: GDD 0.0.34; tests: `docs/ROUND-0034-TESTING.md`.

* **Watch the catapult from the Command Table** (owner): the specialty seller shows the FOB's Supply Catapult live, however far away: it turns toward your base, throws, the box flies out, and a line says what it is doing. The catapult now turns and throws the same whether anybody is near it or not, and turns back to rest when it has nothing to throw.
* **The box comes home as a supply drop** (owner): 12 seconds after the throw, counted down in your chat like the Supply Flare's, the parcel appears high over a spot beside your Defense Beacon and comes down by parachute.
* **Fishing & Animal Husbandry** (owner): one specialty with both lists and both icons (fishing alone was too thin). Existing FOBs with either keep their choice.
* **The FOB screen says if the Supply Catapult is ONLINE or OFFLINE** (owner), and why. The research tab's level 5 reads "Supply Catapult + Specialty Shop", and reaching it is announced.
* **The radio** (owner): Contact Specialty Seller has a handheld radio icon and plays the owner's radio static; every purchase plays one of the owner's four order lines.
* **Nerf** (owner, after a playtest): a specialty sends at most 4 S-tier items' worth an hour, however big the FOB, its research or the slots switched off (it could reach 12 an hour).
* **Fixed:** a research gift that passed several unlocks at once announced only the highest one.
* **War horn** (owner's recording): it sounds from the FOB Beacon when you plant a FOB and when a FOB's raid starts (the activation raid and an SOS raid).
* **No more totems of undying from the monster hunter guild** (owner: far too strong). A FOB that chose it sends rotten flesh until you choose again.

## 0.0.33 — The Supply Catapult and the specialty seller (2026-10-12, branch `feature/supply-catapult`)

On `feature/supply-catapult` (the owner asked for a feature branch). **Network protocol 38** (the seller's buttons and table data, a new entity). Rules: GDD 0.0.33; tests: `docs/ROUND-0033-TESTING.md`.

* **Supply Catapult** (owner's model): a wooden onager for a FOB's village, one per FOB, bought at Workshop Fabrication (16 logs, 8 iron, 16 string, plus 2 logistics modules in the pack or 8 Ardent Energy standalone).
* **Contact Specialty Seller** on the Command Table's FOBs tab (owner): open when the FOB is ACTIVE, its research at level 5 and its catapult placed. Every item of the FOB's specialty, for Ardent Energy: a lot is worth one S-tier item (1 ghast tear, 2 ender pearls, 16 bones ...) and costs 4 Ardent Energy standalone, 2 in the pack; buy 1 lot or 4; up to 4 boxes wait at the catapult.
* **The throw** (owner): the catapult turns toward your Defense Beacon, throws the box, and the box parachutes down beside the beacon (the Supply Flare's parcel). A catapult nobody is near still throws.
* **FOB zone outline** (owner): the three lines that followed you are gone; the FOB Beacon's ground level is marked with a tick every block, like the Defense Beacon's zone.
* **Placement preview for everything** (owner): every block of the mod shows a see-through copy and its outline where it would be placed; the Defense Beacon also shows the base zone it would get.
* **Fixed:** the Support Cannon's recoil was never drawn on screen (its block event never reached the clients); the catapult had the same gap and both now pass it on.

## 0.0.32 — Village research, the monster hunter guild, a 30-minute S-tier floor, icons (2026-10-12)

On `dev`. **Network protocol 37** (the gate and FOB states carry the research; new FOB buttons for research gifts). Rules: GDD 0.0.32; tests: `docs/ROUND-0032-TESTING.md`.

* **Dragon's breath is S tier** (owner; it was B) in the potion makers' list, which now runs D to S.
* **30 minutes of play for an S-tier item, even at the slowest** (owner): the tribute is counted in value an hour. The slowest FOB that makes anything (3 villagers, gates 128 blocks apart, all four slots on) makes 64 value an hour; with research at level 4 its specialty slot makes one S-tier item (worth 16) every 30 minutes. More villagers (up to x2 at 20), a longer line (up to x2 at 1,000 blocks) and the far bonus (+20 percent at 5,000) raise it: the best FOB makes 4.8 times the slowest. A trip carries the hours of the route's own period, never fewer than at a vanilla cart's speed. Worths: tiers D to S 1, 2, 4, 8, 16; an emerald 4; iron 2; a trader item 16/6 per emerald of its price.
* **Village research** (owner): each FOB has a research bar on its FOB Beacon's new Research tab, fed with emeralds (1 point), diamonds (4) or Ardent Energy (2), one or ten at a time, while ACTIVE. 5 levels (16, 48, 96, 160, 240 points). Level 2 opens the specialty's A-tier items, level 4 its S-tier items (the potion makers' A/S, the monster hunter guild's A/S, the wandering trader hub's endgame goods). Each level gives the specialty slot +25 percent and emeralds, the mine and the farm +5 percent each. A level reached is announced in chat.
* **The monster hunter guild** (owner): a fifth specialty with every vanilla monster drop but the bosses' (21 items, D to S; ender pearls, which the pack's recipes need in numbers, are A).
* **Icons** (owner): every loot slot, specialty and research has a round medallion with a villager at the job, in the style of the game's paintings (owner's follow-up; the first flat icons are kept unused in `textures/gui/tribute/flat/`). They show on the gate screen's slot cards, the FOB screen and the Command Table's FOBs tab. The gate's item grid shows each item's tier letter and a padlock on locked items.
* **Names switch on the Command Table's map** (owner): hides the FOBs' names and "(x blocks)"; the zones, flags and arrows stay.

## 0.0.31 — Tribute you choose, the Receiving Gate's storage, FOB management (2026-10-12)

On `dev`. **Network protocol 36** (a new packet, `BeaconNetwork.MenuButton`, id 22, C2S: menu buttons). Rules: GDD 0.0.31; tests: `docs/ROUND-0031-TESTING.md`.

* **Four loot slots per FOB** (owner) instead of the random pool of village goods: Emeralds (every village; at least 1 every 3 deliveries), The mine (redstone, iron, andesite, flint, gunpowder or charcoal), The farm (one of 13 plant foods) and the village's Specialty, rolled when it is planted: Fishing (every aquatic food), Animal husbandry (every meat and leather), Potion makers (every 1.20.1 brewing ingredient, priced by the owner's tier list D to S) or Wandering trader hub (the 70 items a wandering trader sells, priced by their emerald cost). Each slot sends the one item chosen on a gate's screen. Another specialty only by taking the FOB back with the shovel and winning a new one.
* **Switch slots off to boost the others** (owner): the slots still on share the whole trip; at least one stays on.
* **Far bonus** (owner): none up to 1,000 blocks between the gates, then up to +20 percent at 5,000. A trip's value: 1 per 200 blocks (1 to 50) times villagers / 10 (20 count) times the far bonus.
* **The Receiving Gate's storage** (owner): a double chest (54 slots) per FOB, take only; every delivery lands there, never in the reward chest; a full storage pauses deliveries (amber); taking the FOB back hands it to that player. Both gates cost 2 chests more. A 0.0.30 stockpile's whole items move into it once.
* **The gate screen takes the whole window** (owner): the storage and inventory (their own tab on a narrow window: 1920 x 1080 at GUI scale 4, 1280 x 720 at GUI scale 3), the light, a live countdown to the next delivery, the four slots (switch, item, "N per trip" or "1 every N trips", about how many an hour, Choose grid with tooltips) and the checklist. Refreshed every second. The FOB screen's middle column sums up the tribute.
* **The Command Table's FOBs tab** (owner): every FOB's name, distance and bearing from the base, villagers, state and route light; during an SOS the time left to answer and Reprint SOS map (one copy; it still burns when the SOS ends). Both maps show each FOB's zone and flag; a FOB beyond the edge is an arrow at the edge with its name and distance.
* **Balance guards.** Deliveries by themselves come at most every 5 minutes of play (the emerald minimum gives at most 4 emeralds an hour); a ridden round trip brings at most 2.5 times what the route would have sent by itself since the last delivery (a route's first delivery always the full two) (0.0.30's stockpile capped rides; without it a short loop ridden over and over would have made tribute out of nothing). An emerald is worth 12 tribute value, the potion tiers 3 to 48: neither emeralds nor ghast tears beat the other at the standalone Exchange.
* **Bug fix: an SOS answered as the village loads.** A player arriving (or teleporting) into the calling village could start its raid before the villagers had loaded, so it counted none: losing them never put the FOB DOWN and none was recorded for the repair. The answer now waits until the village's entities are loaded (found by the real-client QA).
* **The FOB screen refreshes every second** (its tribute countdown and storage), its buttons rebuilt only when they change; its tribute lines wrap on a narrow window.
* **Bug fix: menu buttons on a dedicated server.** The vanilla button packet carries a byte; the enchanting table's buy buttons (ids from 1,000) did nothing on a dedicated server since 0.0.27. Every screen of the mod now sends its buttons with the mod's own packet.
* **Tests.** New `Round0031Test`; `RouteTest` rewritten for the slots, the value tallies, the far bonus, the ride cap and the save format; FOB GameTests `fob_09` (the round trip into the storage), `fob_10` (a passive trip, take only), `fob_11` (a full storage). The real-client QA checks every text of the new screens for overlap at three window sizes.

## 0.0.30 — The owner's 4-wide gates, where a FOB counts, two screens (2026-10-11)

On `dev`. **Network protocol 35** (a new packet, `FobOutline.Sync`, id 21: the FOB zone outlines and the planting preview). Rules: GDD 0.0.30; tests: `docs/ROUND-0030-TESTING.md`.

* **Supply gates, the owner's 4 wide x 2 tall x 1 deep models** (`docs/art/Eternal-Defense-Supply-Rail-Gates-4wide/`, installed unchanged by `tools/fob/install_gates_4wide.py`): two side-by-side rail lanes, so a line runs out on one rail and home on the other through the same gates; one rail through either lane still works. Each gate is two half models drawn by two blocks: the anchor (the right column) and a new left-column block per gate; the beam is four invisible parts that collide only as the beam over the lanes (a seated rider passes under it). Using the item on a rail puts the gate across it and the cell beside it; a second straight rail beside the clicked one decides the side. Live lamps on both columns (twelve overlay models and `supply_gate_lamps.png`; the 0.0.24 overlay and the 3 x 3 models are gone). The FOB's name moved to the new nameplate. The pass plane spans both lanes; a loaded rider coming back through the Delivery Gate's other lane within 10 s gets no hint. A gate from before 0.0.30 is not converted: take it back with the shovel and place it again.
* **Both gates at Workshop Fabrication** (the Delivery Gate still also at the FOB shop; still one per FOB).
* **Where a FOB counts.** Holding a FOB Beacon: a see-through FOB where it would stand, the village part it would take outlined (green or orange) and a line under the crosshair with its villagers, beds and workstations or why not. A planted FOB's zone shows as an olive outline to players within 160 blocks; a Zone outline switch on the FOB screen turns it off for everyone (saved per FOB, on by default).
* **Tactical Enchanting Table screen** 440 x 260 (was 384 x 236), every line of the left column in its own band (the owner saw the empty slot's line run into "ACTIVE FOBs").
* **Workshop Fabrication scrolls** (mouse wheel, or drag its bar) instead of squeezing its rows.
* **Tests.** `RouteTest` rewritten for the new models, lanes and two-lane passes; new `Round0030Test`; FOB GameTests: `fob_09` rides a two-lane loop (out on lane A, home on lane B), `fob_12` places a gate across two rails and on a lone rail, new `fob_22` (preview and outline).

## 0.0.29 — Enchanting prices doubled (2026-10-11)

On `dev`. **No packet changed: protocol 34.** Rules: GDD 0.0.29; tests: `docs/ROUND-0029-TESTING.md`.

* **Tactical Enchanting Table** (owner: "double both pack and standalone cost"): standalone 4, 6, 10, 16 Ardent Energy per level by rarity (was 2, 3, 5, 8), the pack twice that, 8, 12, 20, 32 (was 4, 6, 10, 16); treasure enchants twice that again. Mk I, II, III still take 17, 33, 50 percent off: at Mk III a standalone table charges the 0.0.27 price, a pack table the standalone Mk 0 price. Examples, Sharpness V: standalone 20 (10 at Mk III), pack 40 (20 at Mk III).

## 0.0.28 — The owner's list after the four FOB slices (2026-10-11)

On `dev`. **No packet changed: the network protocol stays 34.** Rules: GDD 0.0.28; tests: `docs/ROUND-0028-TESTING.md`.

* **Tactical Enchanting Table:** the pack edition pays twice the standalone price (4, 6, 10, 16 Ardent Energy per level by rarity; it paid half before), and the standalone price stays (2, 3, 5, 8). Mk I, II and III now take 17, 33 and 50 percent off (were 10, 20, 30), so a pack table at Mk III charges the standalone price.
* **Warlord Seal** sells at the Exchange Shop: 4 Ardent Energy (pack), 8 (standalone). A world whose exchange file is still an untouched default gets the new default by itself; an edited file needs the line `eternal_defense:warlord_seal = 4` (or `= 8`) under `[sell]`.
* **Wood:** platform cost rows say "Wood (any log)" where any log pays. The owner's pack showed "Dark Oak Log": its logs tag holds logs the wood catalogue does not list, and the label wanted every one listed.
* **Patrol** pay doubled at every tier, the minimum included (tier 3: standalone 4 Ardent Energy and 18 coins, pack 2 and 8).
* **Pack, Beacon Restoration Matrix:** the recipe makes 8 (was 4) and takes a Brass Receiver (was a Hardened Receiver); every price in matrices is halved: the Restoration branch 4 to 64, the Vertical zone's share 1 to 16, Quantum tunneling 6, Healing aura 4, Dimensional link 10, the platform's last era 8. The pack bundle carries the updated `15_beacon_recipes.js`.
* **Pack: every won raid gives one Pressed Receiver.**
* **Fire support:** the Bunker Buster deals five Explosion Barrage shells' damage (125 before the Damage upgrade; was two), the Cluster Strike six over its 12 s (150; was 120), the Gravity Well lasts 6 s + 2 s per Volley level (12 s at the top), the Starshell burns 30 s + 10 s per Volley level (a full minute at the top) and gives Speed II instead of Haste II.
* **FOB tribute** adds emeralds: a quarter per villager-hour. The gate screen shows the nine goods in two rows of five.
* Unit tests: 276 (5 new in `OwnerList0028Test`; 13 updated to the new numbers). New GameTest `fob_21` (the wood label, a seal sold at the Exchange, a Patrol's pay).

## 0.0.27 — Forward Operating Bases, slice 4 of 4: the Tactical Enchanting Table (2026-10-11)

On `dev`. **Network protocol 34** (a new menu; the table-state packet also refreshes the enchanting screen). Rules: GDD 0.0.27 (Part J, section 5.10); tests: `docs/FOB-SLICE-4-TESTING.md`.

* **Tactical Enchanting Table** (new block, the owner's enchanting bench model and texture, unchanged; 2 wide, 2 tall, 1 deep like the weapon benches): one per base in the zone, bought at Workshop Fabrication (12 iron, 4 obsidian, 3 books, and 2 resonance coils in the pack or 8 Ardent Energy standalone). Put a weapon, tool or armor in its slot and buy any vanilla enchant that fits, level by level, for Ardent Energy: no experience, lapis, books or anvil. Curses are never sold.
* **The level gate:** level L of an enchant with M levels needs ceil(5 (L - 1) / (M - 1)) ACTIVE FOBs. Level I is always open, the top level needs all five, and a DOWN FOB does not count.
* **Prices:** the enchant's rarity times the level (2, 3, 5, 8 Ardent Energy per level standalone; the pack pays half, rounded up), twice that for treasure enchants. Mk I, II, III table upgrades (8, 16, 32 resonance coils, or 6, 12, 24 Ardent Energy) give 10, 20, 30 percent off for everyone.
* **Warlord Seal** (new item, our own icon): a cleared Warlord hunt adds one to its pay. Spent at the table, it unlocks a treasure enchant for the whole team (Mending, Frost Walker, Soul Speed, Swift Sneak; the last two take one more seal for each of levels II and III).
* Field Guide: the table's card on the FOB page and the seal's card on the Tactical Operations page, with both editions' prices. Vanilla enchanting, librarians and anvils are unchanged.
* The table's discount rounds down, never below 1, so every Mk takes something off (the pack's Sharpness V: 5, then 4, 4, 3). Rounded up, Mk I took nothing off the pack's small prices; the pack edition's real-client run found it.
* Unit tests: 271 (7 new in `EnchantTest`). New GameTests `fob_18` to `fob_20` (the table's placement and the shovel, the level gate, prices, conflicts, treasure and the Mk discount, a Warlord's seal). Real client, both editions: 20/20 FOB GameTests, 69/69 checks (`docs/FOB-SLICE-4-TESTING.md`).
* Bundle: `dist/Eternal-Defense-Dev-0.0.27-pack-test.zip` and `-standalone-test.zip`, with all four FOB slices and their playtest scripts.

## 0.0.26 — The owner's fixes: the Return Flare needs the cannon, ammo parts really apply (2026-10-11)

On `dev`. **No packet changed: the network protocol stays 33.** Rules: GDD 0.0.26 (sections 5.4 and 5.5); tests: `docs/ROUND-0026-TESTING.md`.

* **Return Flare** (owner: "the return flare also needs the cannon, only the return portal we spawn automatically when an operation fails because there is not enough room doesn't"): a handheld Return Flare is refused without the thrower's Support Cannon, like the other flares. The return gate of an operation with no ground opens as before, cannon or not. The FOB shop's note and the FOB guide page say so.
* **Ammo parts** (owner: the 0.0.22 change did not show on the Universal Ammo Platform and ammo was too cheap; flare ammo should stay at 1 case and 1 propellant): the platform now sets every ammo batch's cartridge cases and propellant itself from `AmmoInputs`, replacing what the recipe lists. The owner's pack still ran the older KubeJS script, which wrote the old amounts into the recipes, and the platform charged what the recipe said. Weak special rounds (an ammo id with flare, bean_bag, pepperball, water or medkit in it) keep one case per 8 rounds and one propellant per 16. The pack bundle now carries the updated KubeJS script (`pack/kubejs/server_scripts/20_guns_and_ammo.js`) so the recipe view shows the same amounts.
* Unit tests: 264. New GameTest `fob_17` (the Return Flare refused without the cannon, allowed with it, an operation's return gate opening without one).

## 0.0.25 — Forward Operating Bases, slice 3 of 4: SOS, DOWN, repair (2026-10-11)

On `dev`. **Network protocol 33** (was 32): the FOB screen's state and the status card carry the SOS and the repair; no new packet. Rules: GDD 0.0.25 (Part J, section 5.10); tests: `docs/FOB-SLICE-3-TESTING.md`.

* **SOS.** One clock for the server, in ticks of play: 10 to 12 hours of play after the last SOS, paused while no FOB is ACTIVE, during a raid at the beacon, in the day before one, and while an SOS is open. It calls one ACTIVE FOB at random: a chat line and a bell for everyone, a red line on every status card, and a map with a red X on the FOB for every player (and for anyone logging in while it is open; the maps burn when it ends).
* **The answer.** 7 days of play. The first player standing in the village starts a vanilla raid there (Bad Omen I; a Bad Omen they carry is replaced, never stacked); refused, with a message, while the beacon's own raid runs. Won: the FOB stays ACTIVE and the clock starts again. Lost (vanilla's loss or fewer than half of the villagers), or nobody came: DOWN. A raid with no result leaves the SOS open. The shovel on the FOB calls it off.
* **DOWN** stops the shop, tribute and the supply route (its inspection pauses; the gates are red and their screens say "repair it"); the villagers stay protected.
* **Lost villagers.** A FOB villager killed during a raid at its FOB is recorded: name (its name tag or a stable name), profession, level and its saved data.
* **Repair** on the FOB screen: each lost villager with its price, the base price, the total. Standalone 6 Ardent Energy plus 1 to 5 per villager by level; pack 3 plus 1 to 3. Paid: the villagers come back beside the FOB Beacon with their trades and experience, and the FOB is ACTIVE again, or PLANTED with its raid called again if it never won one. An ACTIVE FOB can bring back villagers lost in a won raid at their own price.
* Operator commands `/eternaldefense fob sos` and `/eternaldefense fob sos expire`; `list` shows the SOS clock. The FOB page of the Field Guide explains the SOS and the repair.
* Unit tests: 262 (new `SosTest`, 8). New opt-in GameTests `fob_13` to `fob_16`; the QA client script plays an SOS won, an SOS left alone and the repair in a real village.

## 0.0.24 — Forward Operating Bases, slice 2 of 4: the supply rail (2026-10-11)

On `dev`. **Network protocol 32** (was 31): the table-state packet also opens and refreshes the supply gate screen; no new packet. Rules: GDD 0.0.24 (Part J, section 5.10); tests: `docs/FOB-SLICE-2-TESTING.md`.

* **Supply Delivery Gate and Supply Receiving Gate** (the owner's gate models and textures, installed unchanged): 3 x 3 x 1 arches over a straight rail (an anchor and seven invisible parts with the model's own collision per cell; the opening over the rail is empty). 16 iron, 8 copper, 8 redstone, plus 1 logistics module (pack) or 4 Ardent Energy (standalone). The Delivery Gate is sold once per FOB at its FOB shop and placed in its village (it links itself); the Receiving Gate comes from Workshop Fabrication, goes in the base zone and asks which FOB it serves. Both sign plates show the FOB's name (UNLINKED once it is gone); the Recovery Shovel takes either back and it remembers its FOB.
* **The light** (red, amber, green, a lit overlay on the owner's lamp housing: a new texture and three overlay models, the owner's files untouched): one rule for both gates and the screens.
* **Tribute.** An ACTIVE FOB's villagers fill a stockpile (redstone, gunpowder, flint, bread, carrots, potatoes, leather, paper; 6.5 items per villager and hour of play, 20 villagers at most, 8 hours of stock). One minecart ride from the Receiving Gate through the Delivery Gate (128 blocks apart at least) opens the route and measures T; the route then delivers by itself every 2T / 0.8 (round(d / 200) items, 1 to 20) into the beacon's reward chest, with no cart or rail. A round trip ridden by one player in one cart carries twice that and renews the inspection clock (8 hours of play). Overdue: red, nothing by itself. 200 stacks waiting in the reward chest: deliveries wait (amber).
* **Rides** answer every pass on the action bar with a sound; a ride ends with a message on dismount, logout, death, another dimension, a gate removed, the FOB not ACTIVE, a restart, or a leg longer than max(10 minutes, 3 T).
* **Gate screen**: a picture strip of what to do, the light and its reason, a checklist with the fix beside every cross, the stockpile, the next delivery and its size, what a ride carries, the last delivery; the FOB list on a Receiving Gate.
* A click on the FOB Beacon or a gate always opens a fresh screen (a screen the client had lost stayed shut). Operator commands `/eternaldefense fob deliver` and `/eternaldefense fob overdue`; `list` and `status` show the route.
* Unit tests: 254 (new `RouteTest`, 13). New opt-in GameTests `fob_09` to `fob_12` (a ridden plain minecart through both gates, a chest minecart counting for nothing, passive tribute and the clock, the backlog, the shop, placement, the shovel); the QA client script places both gates in a real village and at home.

## 0.0.23 — Forward Operating Bases, slice 1 of 4 (2026-10-11)

On `dev`. **Network protocol 31** (was 30): the State packet opens the FOB removal confirmation and the table-state packet refreshes the FOB screen; no new packet. Rules: GDD 0.0.23 (Part J, section 5.10); tests: `docs/FOB-SLICE-1-TESTING.md`.

* **FOB Beacon** (the owner's FOB station model, installed unchanged; 1 x 2 x 1, its upper cell a structure part): Workshop Fabrication and the creative tab; 16 iron, 16 copper, 8 redstone, 4 glass, plus 2 logistics modules and 2 reinforced plating (pack) or 8 Ardent Energy (standalone). Unbreakable; only the Recovery Shovel takes it back, after the beacon's confirmation screen.
* **Planting** in a vanilla village in the Overworld, refused (with the reason) with no beacon planted, with 5 FOBs placed, on Peaceful or with raids off, outside a village, in a village that has a FOB, with fewer than 3 villagers, or with no room. The zone is vanilla's village (sections near beds, workstations and bells), worked out on demand and never saved.
* **The activation raid** is a vanilla raid called at once with Bad Omen I. Won: ACTIVE. Lost (vanilla's loss, or fewer than half of the villagers counted at its start alive, our own rule): DOWN. No result: still PLANTED, with Call the raid on the FOB screen.
* **Villagers** of a FOB cannot be hurt outside its own raid (a damage event; `/kill` and the void still work), and lightning does not turn them into witches.
* **ACTIVE:** the FOB shop (Supply, Return and Fire Support Flares at the Support Platform's prices; the screen notes a missing platform or cannon and sells anyway) and the meal timer frozen in its village with the home bonus, shown as the new FOB Zone effect (`MealZone`; `BaseZone.problem` unchanged).
* **Raiders of vanilla village raids drop no Ardent Energy** (a raider with a wave and without the beacon's raid tag); the beacon's raid mobs still do.
* **Names:** generated ("FOB IRON HOUND"), renamed with a Name Tag renamed at an anvil, at most 32 characters, unique in any case. **FOB screen**, **status card line**, Field Guide page **FOBs**, tooltips, chat lines for planting, the raid, the result, renaming and removal. Operator commands `/eternaldefense fob list|status|complete|reset`.
* The owner's two art bundles (`Eternal-Defense-FOB-Mixer-Enchanting`, `Eternal-Defense-Supply-Rail-Gates`) are kept in `docs/art/`; only the FOB station is used in this slice.
* Unit tests: 241 (new `FobTest`, 15, among them a check that no two event handlers share the class Forge generates for them: the first client run crashed on exactly that, fixed before release). New opt-in GameTests `fob_01` to `fob_08` (`-Deternaldefense.fobTests=true`, `/fob-test`), a QA client script `tools/qa/QaFob.java.txt`.

## 0.0.22 — Your supply and recall radio lines, ammo parts, hunt pay fixed (2026-10-11)

On `dev`. **No packet changed: the network protocol stays 30.** Rules: GDD 0.0.22 (Part D, Part E, section 5.9, section 7); tests: `docs/ROUND-0022-TESTING.md`.

* **Supply and recall radio lines:** the owner's `More flare sound` lines (`tools/audio/import_chatter.py`: -18 LUFS, mono, 48 kHz). A supply flare answers with one of two supply lines (it borrowed fire support lines 1 and 4 before); a return flare answers with one of three recall lines (it had none). Fire support flares keep their six. Each flare kind keeps its own last line per player, so no line plays twice in a row. Subtitles "Supply radio answers" and "Recall radio answers"; a Field Guide line.
* **Ammo parts (pack edition):** every ammo recipe takes twice the propellant and 20 percent more cartridge cases, rounded up: a batch of 8 rounds or fewer 2 cases and 2 propellant (was 1 and 1), 32 rounds 5 and 4 (was 4 and 2), 64 rounds 10 and 8 (was 8 and 4). One place sets them (`AmmoInputs`), used by the KubeJS script that rewrites the gunpacks' recipes and by the Ammo Platform where KubeJS did not run. The standalone edition's ammo (vanilla materials) is unchanged.
* **Bug fix (owner's report: "operation no matter the reward level or the satellite beacon level only give 0 ardent energy and 1 coin"):** a hunt paid, and fought, at the tier of the last raid *won* (`CampaignData.rewardTier`, the beacon's "Paid: Tier"), not at the beacon's *Next payout* tier; a campaign that has only won its introduction raid stays at tier 0 there, and a pack Patrol at tier 0 pays exactly 0 Ardent Energy and 1 coin. Hunts now use the Next payout tier (`Hunts.tier`, the same `BeaconNetwork.prospectiveTier` the Overview shows) for the pay, the offer cards, the danger pips and the warband's strength. The satellite's Mk never raised the pay of a class: it unlocks Garrison (Mk II) and Warlord (Mk III), which pay more. GameTest `hunt_14`; the QA checks a Warlord's pay against the Next payout tier.
* **Tactical Operations:** a mission always pays at least 1 Ardent Energy and 2 ammo coins, at any reward tier (only a pack Patrol at tier 0, which paid 0 and 1, changes). Field Guide line.
* **Branches:** the five feature branches merged into `dev` in 0.0.17 (`feature/beautify-path`, `music-player`, `optimize-path`, `raider-gates`, `stuck-raiders`) were deleted locally; deleting them on GitHub was refused by this session's git proxy (HTTP 403), so they are still on the remote.
* Unit tests: 226 (new `FlareVoiceAndAmmoTest`; `RadioAndWhistleTest` follows the new lines). Hunt GameTests 14 (new `hunt_14`).

## 0.0.21 — Armed skeletons, night raids, one-block arrival, the shop gets iron (2026-10-11)

On `dev`. **Network protocol 30** (was 29): `MissionSync` (17) now carries the mission id, so the client knows which map in hand belongs to the running mission. Rules: GDD 0.0.21 (Part B, Part C, Part I, sections 5.2, 5.4, 5.5, 5.9, 7); tests: `docs/RAID-ROUND-0021-TESTING.md`.

**Raids:**
* **Raid skeletons get their bow, pillagers their crossbow.** Raid mobs were made without the game's spawn equipment, so skeletons had no bow: they got the ranged attack goal, could not use it, walked like zombies, stopped short and were sent through gate after gate as "stuck". `RaidBalance.arm` gives the weapon before the mob joins the world; a skeleton or pillager without its weapon now gets the melee beacon attack instead of a ranged one it cannot use.
* **Raids only at night** (day time 13000 to 21000), every raid but the introduction. A due raid waits for the night. **Start raid** by day books the raid for nightfall: the button reads "Raid at nightfall" and greys out; the raid starts at dusk if someone is near the beacon.
* **Raid interval** 10 active days at reward tier 0, one day sooner per tier, down to 3 days from tier 7 (was 7 down to 2).
* **During a raid cover no longer hides the beacon:** every hostile mob within 48 blocks of the zone's edge comes for it, so a raid also draws the night's ordinary monsters. Between raids the rule stays: within 24 blocks and in sight.
* **Alarm:** the clip is cut to its first double beep (0.6 s) and starts again after a 0.5 s pause while the red warning shows ("beep beep - pause - beep beep").

**Tactical Operations:**
* The mission line on the status card shows only while a player holds the mission map or the Recovery Shovel (either hand).
* The mission map **burns** when the mission is cleared or found unreachable (and any stale map at login or within 5 s).
* Command Table: the selected X is red; the classes are blue (Patrol), purple (Garrison) and gold (Warlord), like item rarities.
* Objectives never lie on an ocean or a river (the generator's biome at the spot and 12 blocks around, read without loading a chunk). Old offers on water are replaced, one a minute.
* **Arrival on one block:** the warband needs only one block to stand on within 16 blocks of the X. A small glowing zone (6 blocks) forms around it; enemies come out of red gates 8 to 12 blocks out, one a tick, and rush to its middle. This replaces the 240-try spot search that ran when players arrived (the likely cause of the lag on arrival).
* **No ground at all:** every player there gets a return gate home (5 minutes, owner only, no support cannon needed); the offer is replaced at once.
* Warband enemies carry the red exclamation mark, like raid enemies.

**Side quests:**
* **Starshell:** Haste II now lasts 30 s from each pulse (it lasted 3 s and was renewed every 2 s, so the icon blinked as if ending and the effect was gone 3 s after leaving the light). Nothing in this repository removes it; another mod in a full pack could.
* **Exchange:** the pack edition also sells the standalone edition's rare materials (gold x8 for 2, blaze rod 3, ender pearl 3, diamond 6) and 16 iron ingots for 1; both editions sell **16 iron blocks for 8** Ardent Energy (144 ingots, one energy cheaper than nine lots of 16 ingots). A shop file that is still exactly the 0.0.20 default of its edition is replaced by the new default; an edited file is kept.
* Unit tests: 219 (new `RaidRoundTest`). Hunt GameTests 13/13 (four new: one block is enough, no ground opens a return gate and burns the map, armed raiders walk at the beacon, raid cover). Real client of each edition on a normal world: 68/68 (`docs/RAID-ROUND-0021-TESTING.md`).


## 0.0.20 — Tactical Operations; gate spawns, the incoming sound back, the cannon menu card (2026-10-10)

On `dev`. **Network protocol 29** (was 28): four new server-to-client packets (`MissionSync` 17, `ScanImage` 18, `ScanProgress` 19, `TableState` 20); no new client-to-server packet. Rules: GDD 0.0.20 (Part I, sections 5.2, 5.5, 5.9, 7); tests: `docs/TACTICAL-OPERATIONS-TESTING.md`.

**Tactical Operations (new):**
* **Command Table and Satellite Beacon** (the owner's models): bought at Workshop Fabrication, inside the zone only, one of each per base, moved free with the shovel. The satellite links when it stands within 8 blocks of the table under open sky; clicking it says why not. Satellite Mk I, II, III: 2, 3, 4 offers and 256, 512, 1024 blocks of reach; upgrade at the table (pack 16 and 32 resonance coils, standalone 10 and 20 Ardent Energy).
* **Hunting missions:** Patrol (8 to 12 enemies), Garrison (14 to 20 around a real vanilla structure, from Mk II; the structure's loot is never touched), Warlord (a glowing named captain with twice the health and 10 to 14 escorts, Mk III); +2 per extra player online, at most +8. One shared mission; accepted at the table, never during a raid or the day before one. Everyone gets a map with a red X and the base on the same sheet, and one Return Flare. The warband comes out of red gates when someone gets within 64 blocks; the last kill pays Ardent Energy and ammo coins into the beacon's reward chest, once. Left alone for 5 minutes it scatters; Abandon (click twice) puts the offer back. A finished offer is replaced at the next dawn.
* **Satellite scan** on the table: a radar of the land (Base and Region), live where chunks are loaded, older scans dimmed, the rest a hatched biome preview; never loads a chunk.
* **Status card:** "OP IRON HOUND | Garrison | 640 m NE | 7/14 left", also out in the field.
* **Sounds:** table confirm and refuse, scan sweep and done, contact alert (`tools/audio/tactical.py`). The mission-cleared jingle is a placeholder (the Victory Fanfare) until the owner's song comes.
* Operator commands `/eternaldefense hunt status|reset|complete`; opt-in GameTests `-Deternaldefense.huntTests=true` and `/hunt-test`.

**The owner's raid and fire support list:**
* **No more repeating "Reinforcements are waiting..." line.** When there is no natural ground far out for 10 s, enemies come out of **red gates** closer in (never inside the zone or within 12 blocks of its edge, never within 12 blocks of a player, outdoors, on ground nobody built); chat says so once a wave.
* **Every enemy the mod places anywhere but a natural spawn comes out of a red gate** (gate spawns, hunt warbands; stuck raiders already used gates). The gate stands just behind the enemy (seen from the base, or from the nearest player for a warband), 1.6 times the size of a stuck raider's gate, and stays open 5 seconds after the last one: on the spot, the enemy hid most of it (found in this round's screenshots). `/eternaldefense test-gate` shows one any time.
* **Speed burst:** every raid enemy starts with Speed II for 10 seconds.
* **The incoming sound is back** (your 0.0.18 scream, 5.15 s to the impact): it plays where the round lands, so it is heard whichever way you look. The 0.0.19 whistle is removed.
* **Cannon menu:** the card now explains each fire support in three parts, Effect, Damage and Area, with the numbers of your own cannon, and scrolls. Texts checked against the code (the Healing Barrage does not hurt mobs; napalm fire is real and burns players).
* **Cryo Shell:** 3 blocks further every way (7.8 instead of 4.8), not higher.
* **Starshell:** the code gives players Haste II, and only the Healing Barrage and the parcel's Healing aura give Regeneration; the real-client run checked the effects a player gets in a Starshell (see the testing doc).
* Unit tests: 202 (new `TacticalOperationsTest`, `RaidFixesTest`; `RadioAndWhistleTest` and `HandTunedRoundTest` follow the incoming sound back).

## 0.0.19 — Your songs only, no repeats, the whistle back, the radio answers (2026-10-10)

On `dev`. **No packet was added or changed: the network protocol stays 28.** Rules: GDD 0.0.19 (Part A "Raid music and the music player", section 7 "UI and audio"); tests: `docs/NEW-SOUND-TESTING.md`.

* **Raid music is all yours:** four more songs (Acid Redeemer, Assault Loop, Breach Core, Rolling Wave) join the ordinary ones, so there are eight, as many as the longest raid has waves. The three themes and the victory fanfare made for 0.0.16 (Hold the Line, Iron Tyrant, Strange Signals, Laurels) are removed. Winning plays your Victory Fanfare.
* **No song plays twice in one raid:** the waves go through the eight ordinary songs in a shuffled order. A special raid opens with Anomaly Protocol and goes on with ordinary songs; a hard raid's boss wave plays Boss Battle. Every player of the base still hears the same song.
* **The falling round whistles again** (the 0.0.16 shell and bomb whistles): the whistle rides the round for the last 1.55 s of its fall and ends as it lands. Your 0.0.18 incoming sound is removed; the 5.15 s from shot to impact stays.
* **No more flare siren.** A landing flare pops (the game's firework blast), and when its call is taken your radio answers with one of your six fire support lines, picked at random and never the same twice in a row. A supply flare gets "payload incoming" or "stay out of the drop zone"; a return flare and a refused call (under cover without Quantum tunneling, a Bunker Buster at home) get none. The line follows you on the Voice/Speech volume slider.
* Unit tests: 173 (new `RadioAndWhistleTest`; `MusicPlayerTest` checks that no song repeats in any raid of 3 to 8 waves).

## 0.0.18 — Your hand-tuned sounds, a heavier cannon, reworked fire supports (2026-10-10)

On `dev`. **No packet was added or changed: the network protocol stays 28** (new sound events and an entity data field are registry and entity data, not packets). Rules: GDD 0.0.18 (Part A upgrade table, section 5.5, section 7); tests: `docs/HAND-TUNED-ROUND-TESTING.md`.

* **Your sounds** (`tools/audio/import_sfx.py`: made mono where they play in the world, trimmed, loop made seamless): the cannon's turning sound loops while the turret moves, then its lock clunk plays and the cannon fires the moment that clunk ends (0.9 s); its shot; the incoming round, played at the impact point so it ends exactly when the round lands; the Bunker Buster's blast; the cluster bomblets, many quick ones at once (the sound is 6 dB quieter than your file); the shockwave; and an alarm with the red BEACON IS BEING ATTACKED warning (again every 8 s while it stays up). **Every other explosion now uses Minecraft's own explosion sound** (barrage shells, the Bunker Buster's digging, the cluster burst, the gravity well closing). The 0.0.16 synthesized sounds they replace are removed.
* **Rounds land 5.15 s after the shot** (was 1.5 s), when your incoming sound hits; they fall from 80 blocks, faster and faster.
* **Heavier turret:** turning takes 1.5 times as long without Faster traverse (a half turn 7.4 s instead of 4.9 s); the top Faster traverse level is exactly the old unupgraded speed.
* **Quantum tunneling** is needed only when 3 or more solid blocks are above the flare. An open pit, however deep, or a spot under a thin roof or a tree is not "underground" any more.
* **Beacon upgrades:** Defense is paid in reinforced plating (was resonance coils). The Vertical zone costs 2 of each of the four parts at first, then 4, 8, 16, 32 (was 8, 16... logistics modules) in the pack edition. Reconnaissance has three levels: without it raiders glow 5 minutes into a wave (was 1 minute), then 3 minutes, 1 minute, and from the start of every wave.
* **Fire supports:**
  * Cluster Strike: one shell (Volley does nothing), then bomblets rain on the area for 12 s; a mob that stays inside takes 80 percent of a full Explosion Barrage (120).
  * Napalm Carpet: one shell; the ground burns with real fire for 10 s (+4 s per Volley level) in a flat 2-high box 1.2 times as wide; mobs in it take 5 a second and burn. No fire near the beacon zone; the fire is taken away when it ends.
  * Cryo Shell: one shell; snow covers the same kind of box for 15 s (+5 s per Volley level); hostile mobs in it nearly stop. No damage any more. The snow is taken away when it ends.
  * Gravity Well: one shell; twice as wide (8 blocks each way), 5 s (+2 s per Volley level), with a turning vortex sprite on the ground. It only pulls: no damage, so the crowd in the middle is yours to shoot.
  * Shockwave: as wide as a level 1 beacon zone (8 blocks each way); every hostile mob inside is shoved out past the edge, bosses too. No damage.
  * Starshell: lights its whole area with hidden light blocks (taken away when it goes out) and gives players in it Haste II instead of Night Vision.
  * Arrow Cluster Bomb: tipped arrows (poison, slowness, weakness, harming; undead get healing arrows, which hurt them), shown in their potion colour; 6 volleys (was 8).
  * Bunker Buster: the view shake near the impact now triggers reliably (the client could miss the landing tick).
* Field Guide, cannon menu descriptions, the Reconnaissance card and the refusal message follow the new rules. Unit tests: 169 (new `HandTunedRoundTest`). Checked in a real client of both editions with the game's audio recorded (`docs/HAND-TUNED-ROUND-TESTING.md`).

## dev — all new features merged (2026-10-10)

At the owner's request `dev` was fast-forwarded to `feature/music-player`: it now contains 0.0.13 (stuck raiders), 0.0.14 (Raider Gates), 0.0.15 (Optimize Path), 0.0.16 (Beautify Path) and 0.0.17 (raid music player) below. No code changed in the merge; the project version stays 0.0.17. Test bundles: `dist/Eternal-Defense-Dev-0.0.17-pack-test.zip` and `dist/Eternal-Defense-Dev-0.0.17-standalone-test.zip`.

## 0.0.17 — Raid music player: your songs, a new song every wave, a player under the beacon card (2026-10-10)

Branch `feature/music-player` (from `feature/beautify-path`, 0.0.16). **No packet was added or changed: the network protocol stays 28.** Rules: GDD section 7 and Part B (*Raid music and the music player*); tests: `docs/MUSIC-PLAYER-TESTING.md`.

* **Your songs.** Barren Gap, Line Holder, Phase One Assault and Silent Trigger (ordinary raids), Boss Battle (boss wave) and Anomaly Protocol (special raids) are converted to Ogg Vorbis, trimmed of silence at both ends and brought to the same loudness (-16 LUFS) by `tools/audio/import_songs.py`. The three original themes of 0.0.16 stay in the pools (Hold the Line, Iron Tyrant, Strange Signals), so the boss and special pools also have two songs each to alternate.
* **A new song every wave.** Each wave starts a song from its pool and loops it (seamless, no gap) until the next wave, which fades to another song of the pool (3 s out, 1.5 s in); a wave never repeats the song of the wave before. The order is random but fixed per raid and base, so every player hears the same song. The boss wave of a hard raid plays a boss song (0.0.16 played the boss theme for the whole hard raid). The music now plays for every player in the Overworld during a raid, not only near the beacon. Winning still plays the fanfare.
* **The player.** A strip right under the beacon's status card: the song's name, a moving level meter, the time and a progress bar. It stays for the whole raid, also away from the beacon where the status card hides; it says "muted" when the Music slider is at 0.
* **Settings.** The beacon panel's Settings tab now has two personal switches on the right, *Raid music* and *Music player*, saved on your own computer (`config/eternal-defense-client.toml`). With Raid music off the game's own music keeps playing during raids and the fanfare is replaced by the old toast sound.
* The game's music manager is now held while a raid song plays (a client mixin on `MusicManager.tick`, replacing the 0.0.16 mixin on `Minecraft.getSituationalMusic`), so the raid music can fade and loop by itself.
* Field Guide: the Raids card says every wave starts a new song, where its name shows, and where to turn it off.

## 0.0.16 — Beautify Path: raid music, falling shells, bigger blasts, exit gates, six new fire supports (2026-10-10)

Branch `feature/beautify-path` (from `feature/optimize-path`, 0.0.15). **No packet was added or changed: the network protocol stays 28** (new sounds and the new entity are registry entries; an older client is refused by the registry check). Rules: GDD section 5.5 and section 7; tests: `docs/BEAUTIFY-PATH-TESTING.md`.

* **Raid music.** Three original themes (`tools/audio/compose.py`): *Hold the Line* (ordinary raids), *Strange Signals* (special raids) and *Iron Tyrant* (hard raids, from the first wave). They replace the game's music while a raid runs and you are near the beacon, and loop. Winning plays an original fanfare (*Laurels*) at once; losing or walking away stops the theme. The Music volume slider controls all of it (a client mixin on `Minecraft.getSituationalMusic`, since Forge 47 has no music event).
* **Cannon and flare sounds.** A new shot (heard 128 blocks away), gear grinding and pawl clanks while it turns, a clank when it locks on. A landing flare cracks loudly with a flash, sparks and a signal plume.
* **Falling rounds.** The cannon fires into the sky and each round is seen falling from 48 blocks above the flare: a 3D shell banded in the fire support's colour, or the Bunker Buster's bomb, with smoke and a whistle that follows it down. Nothing flies from the cannon to the target, and a flare underground is reached through the rock. **Every round now takes 1.5 s to land (was 0.5 s; the Bunker Buster was already 1.5 s).**
* **Bigger blasts.** Explosion Barrage shells and the Bunker Buster have far bigger flashes, fireballs, pressure rings, smoke columns and thrown earth, seen up to 160 blocks away, and echoing blast sounds; the bomb keeps rumbling as it bores down. Nearby players' view shakes (scaled by the Screen Effect Scale option, off at 0).
* **Exit gates.** A channeling raider's destination now gets its own red gate when it is shown (3 s before the jump); the raider steps out of it and it closes 2 s later.
* **Six new fire support types:** Cluster Strike, Cryo Shell, Napalm Carpet, Gravity Well, Shockwave and Starshell (numbers in the GDD and the Field Guide Reference tab), each with an icon, a colour, a sound and a guide line. Saved choices keep their meaning (the new types are appended).
* **Cannon menu.** The fire support list scrolls (mouse wheel) and keeps the chosen type in view; the three-level upgrades, Area of effect included, are on top; a maxed upgrade can still be hovered and says "Fully upgraded".
* Field Guide: six new type lines and their numbers, the falling rounds, the second red gate, the raid music. Subtitles for every new sound.
* Tests: 154 unit tests pass (new `BeautifyPathTest`); the gate GameTest also checks the exit gate. Both editions build. **Not yet run in a real client** in this round (see the testing document).

## 0.0.15 — Optimize Path: the same game, less work per tick (2026-10-10)

Branch `feature/optimize-path` (from `feature/raider-gates`, 0.0.14). **No feature, number or packet changed: the network protocol stays 28.** Audit and changes: `docs/OPTIMIZE-PATH.md`; tests and measurements: `docs/OPTIMIZE-PATH-TESTING.md`.

* **One listener no longer sees every event of the game.** The TaCZ block-impact hook of the beacon was subscribed to Forge's base event class, so Forge called it for every event posted on the main bus (entity ticks, render stages, input, sounds) on server and client; it now subscribes to TaCZ's `AmmoHitBlockEvent` itself (TaCZ is required in both editions) and reads the hit without reflection.
* **Base score:** what a block counts as is worked out once per block type instead of once per scored block, and the "light or furniture within 12 blocks" test uses a 12-block grid instead of scanning the whole list for every floor candidate. Same blocks in the same order, same score.
* **Resource loading:** the gun-pack resource path check is a character loop instead of a regular expression compiled on every call.
* **Special Forces:** the once-a-second sweep over every Overworld entity is skipped when the optional Special Forces mod is not installed (no soldier can exist).
* **Deaths:** the entity type id is only looked up for deaths inside the zone during a raid.
* **Zone lookups:** `Rules` builds its small lookup tables (radius, height, health, platform score) once instead of on every call; `CampaignData.inside(x,y,z)` tests the zone without a position object.
* Measured (real server, synthetic 49,221-block Mk-4 base): base score about 64 to 66 ms before, 21 to 26 ms after, identical on 73 bases; about 3,400 events per tick no longer reach the old listener (about 17 ns each); the Special Forces sweep 12 to 14 microseconds to 0.2 without the mod. The 11 Raider Gates GameTests still pass.
* Tests: `OptimizePathTest` and an extended `SoundResourcePathsTest` (unit); `OptimizePathGameTests` (opt-in, real server) run the new code next to a verbatim copy of the old one and require the same answers. Rendering and HUD code was not changed (listed as candidates for a later round).

## 0.0.14 — Raider Gates, replacing stew in a pot, a lit Cook Pot board, fewer servings (2026-10-09)

Branch `feature/raider-gates` (from `feature/stuck-raiders`, 0.0.13). **No packet was added or changed: the network protocol stays 28.** Rules: GDD section 5.2 (*Raider Gates*); tests: `docs/RAIDER-GATES-TESTING.md`.

* **Raider Gates replace the silent rescue of 0.0.13.** A ground raider that walks up to lava or magma, a drop of 6 or more blocks or 8 or more blocks of water in a row (a short pond is waded), or that makes no progress for 25 seconds (and is not fighting, shooting or digging), stops and channels for 8 seconds beside a red gate, drawn with the Return portal sprite tinted red and at half the speed. The channeler cannot move (a high-priority goal; `setNoAi` is not used) and can be shot: each second it is hurt delays it by 3 seconds, at most 10 in total; killing it cancels the gate. Three seconds before it reappears the destination shows portal particles and a portal sound; it comes out at the edge of the staging ring, 24 or more blocks from every player, and marches again after a 10-second grace window. Raiders stuck at the same place share one gate (8 channel at a time, the rest wait). After three gates a raider is withdrawn (no drop, one chat line per wave); a boss is gated again and never withdrawn.
* **Shared probe.** `TerrainProbe` is one pure helper used for the look-ahead in front of a raider and for checking the whole way from a gate's destination to the zone. `RaidMarch` holds every number as a named constant; the progress timer, busy rules and UUID-keyed, pruned trackers of 0.0.13 are reused.
* **Opt-in diagnostics** (`-Deternaldefense.stuckLog=true`) now log every trigger (probe or timer), channel start, interruption, teleport, wait, cancel and withdrawal with the terrain around the raider and the gate id.
* **Mess Hall:** a stew can be cooked over the stew still in a pot: the screen asks "Replace the stew?" (Replace stew / Keep it / Escape), the server refuses an unconfirmed cook into a pot with stew (new menu button 7, no new packet). **Stew servings per pot are lower: 3/5/7/9 instead of 4/8/12/16** (Mk IV 16 to 9; you asked for the Mk IV number, the rest of the ladder was scaled so Mk IV is not worse than Mk III; one line in `MealRules.tier`). **The Cook Pot's lamp bar gives light** (block light 10) and its board text is drawn full-bright, so the menu can be read in the dark.
* Field Guide: the Raids card explains the red gate in one line; the Cook Pot card says the old stew is thrown away after you confirm, and quotes 5 to 9 servings.
* Tests: unit tests for the probe, the channel clock, the damage-delay cap, gate assignment and the ladder; 11 GameTests (the 7 of 0.0.13 reworked for gates plus the new ones) pass in a real client's integrated server; the kitchen QA run checks the replace flow, the new servings and the lamp. Not run: the legacy v2 to v6 GameTests (their stew assertions were adjusted to compile), a dedicated server, two players, real terrain, the standalone edition's kitchen after this change.

## 0.0.13 — Stuck raiders no longer lose raids (2026-10-09)

Branch `feature/stuck-raiders` (from `dev` 0.0.12). Network protocol unchanged (28). Rules: GDD section 5.2 (*Stuck raiders*, *Marchable corridors*); tests: `docs/STUCK-RAIDERS-TESTING.md`.

* **A raider that cannot reach the beacon no longer decides the raid.** A ground raider with no 3-block gain in distance to the beacon for 25 seconds (and not fighting, shooting or digging) is moved back to the staging ring up to three times and then withdrawn (discarded: no drop, one chat line per wave). A raid boss is moved again each time and is never withdrawn. Raiders still march straight; `approach()` and the pathfinding are unchanged.
* **Spawn directions are checked once per raid** for water, lava, magma, steep rises and drops on the straight line to the base (12 world-aligned sectors, one probe each, never loading a chunk); `RaidSpawns.find` uses clean sectors first, then the fewest violations.
* `RaidMarch` now keys everything by UUID and prunes it every cycle (the old map was keyed by entity id and never pruned); everything is cleared when a raid starts or ends and when the server stops.
* Opt-in diagnostics: `-Deternaldefense.stuckLog=true` logs every stuck, rescue and withdraw as one `[stuck]` line (block ids, fluid, biome, whether `approach()` had to push the mob straight at its goal). `-Deternaldefense.stuckTests=true` registers the stuck-raider GameTests.
* Field Guide Raids card: one line telling players that stuck raiders are moved and, after a few tries, sent away.
* Tests: 124 beacon unit tests (28 new); 7 real-server GameTests pass in the real client's integrated server (`tools/qa/QaStuck.java.txt`). Not run: the legacy v2 to v6 GameTests, a dedicated server, two players, real terrain.

## 0.0.12 — Feature guide for designers, branches merged into dev (2026-10-09)

* **`docs/GAME-DESIGN-DOCUMENT.md` has a new section, *Feature guide for designers*,** right after the core loop. Every feature in plain words: what it is, what the player does, how it behaves, its job in the game, the dials that change it and what has not been played yet; a feature-dependency table; a playtest-first list. Section 5 is now labelled as the rulebook with exact numbers. A stale protocol note was corrected (protocol 28).
* The Mess Hall v6 line (0.0.7 to 0.0.11) was pushed onto `dev` at the owner's request (fast-forward from 0.0.2's `1a839c0`), and the branches whose commits are all in `dev` were deleted. `CLAUDE.md`, `README.md` and `docs/HANDOFF.md` no longer say that work lives on `feature/messhall-ver-6`.
* No rules, numbers or code changed in this entry.

## 0.0.11 — Mess Hall levels, two-column kitchen, one-button table upgrade (2026-10-09)

Developed on `feature/messhall-ver-6` (owner override), merged into `dev` in 0.0.12. Network protocol raised to **28-pack / 28-standalone** (the open food slots change what the kitchen menu does on both sides). Rules: `docs/MESS-HALL-TIERS.md`.

* **Mess Hall levels rebalanced (owner request).** Mk I: sandwiches only, meals last 15 minutes, 3 food slots. Mk II: unlocks stew, 20 minutes. Mk III: 25 minutes, unlocks ×2 doubling (sandwiches and stew). Mk IV: 30 minutes, all 6 food slots, and the only level that can double a legendary effect (four food slots). Pots, servings and prices are unchanged. A meal remembers how long its hall made it last.
* **The kitchen screen is two columns (376 × 238).** Left: mode tabs, the table, Gather/Make/Cook, Take back, Clear order and Upgrade. Right: the 26 effects to choose (dimmed unless chosen, so the palette stops shouting), the order cards, the next-step line and three chips: meal length, food slots, ×2. Plain explanations of ×2 and of each level in the chips, the recipe tooltips, the badge and the Upgrade tooltip. Needs a GUI at least 376 wide (every 16:9 window; lower the GUI scale on 4:3).
* **Weapon, Ammo, Attachment and Armor table Upgrade view rebuilt.** A full-width card: the Age ladder (done, you are here, next, later), what the next Age unlocks, a grid of materials with what you hold, and one **Upgrade to <Age>** button. No more blank left half.
* Field Guide: the Mess Hall page lists the four levels and has a *Doubling (×2)* card; the Legendary mixes page and the fabrication blurb follow the new rules.
* Tests: 96 beacon unit tests (tier table, doubling from Mk III, slots, meal length). Real client: `tools/qa/QaKitchen.java.txt` gained a `tiers` part and a table-upgrade click. The server GameTests of v2 to v6 were edited to compile and still were not run.

## 0.0.10 — Field Guide side menu with item icons (2026-10-09)

Feature `feature/messhall-ver-6` (owner override). UI only: no rules, numbers or packets changed (protocol stays **27**).

* **The side menu is back, and it stays.** The old rule hid it whenever 14 rows did not fit (a window around 1600 x 900 at GUI scale 3 lost it once the guide grew to 14 pages), so the book looked bare. The menu is now a full-height strip beside the page in every window.
* **An item icon per page**: Field Guide (Start here), Defense Beacon (Your base zone), Zombie Head (Raids), Recovery Shovel (Raid control), Reinforced Beacon Plating (Upgrades), Restoration Matrix (Repair or pack up), Weapon Platform, Ardent Energy, Support Cannon, Mess Hall, Prepared Sandwich (Legendary mixes), a Gun Displays stand (Extra gun mods; a crossbow if that mod is missing), Player Head (Co-op) and Writable Book (Reference). Icons sit on a lighter tile so dark items stay readable.
* **Two forms.** Tall enough: a list with icon and title (16 px icons when the rows are 19 px or more, 12 px otherwise). Too short, such as the smallest GUI (427 x 240): a two-column rail of icons with the page name in a tooltip. The page buttons and header sit beside the menu; the guide panel may now be up to 330 px tall.
* Tests: `LangKeysTest.everyGuidePageHasItsOwnMenuIcon` (96 beacon unit tests). `tools/qa/QaGuide.java.txt` checks the menu geometry; `tools/qa/setup-qa.sh` takes `QA_WIDTH` / `QA_HEIGHT`.

## 0.0.9 — Field Guide for new players (2026-10-09)

Feature `feature/messhall-ver-6` (owner override). No rules, numbers or packets changed (protocol stays **27**). Writing rules: `docs/GUIDE-STYLE.md`.

* **The Field Guide is rewritten for somebody who has just spawned in.** Fourteen pages of feature cards: *What it is, You get, How it works, How to unlock*, plus red *Careful* notes for the catches (the beacon only comes off with the Recovery Shovel and that resets every upgrade, win and reward level; one Support Platform and Cannon per player; the Milk Dispenser clears good effects and your meal; a Raid break makes the next raid tougher; and so on). It is not the design document: exact numbers stay in the Reference tab.
* **Gaps filled.** New *Extra gun mods* page (Gun Guide and Gun Displays); starter kit, Recovery Shovel and Field Guide cards; raid warnings, trap learning, Special Forces and the reward chest; Workshop Fabrication, Ammo Coins, Supplies and Ages; every Mess Hall block and what a meal does; Settings.
* **Stale text corrected.** Standalone upgrades are paid in Ardent Energy, not crafted parts (the guide and the upgrade hover said otherwise); repair prices are read from the button instead of being quoted wrongly for one edition.
* **Other prompts.** The plant confirmation and the Defense Beacon tooltip now say that only the shovel removes the beacon and that this resets the campaign. The Field Guide tooltip is plainer.
* **Raid timing worded correctly (owner correction).** Raids come about weekly at reward tier 0 and more often as the tier rises, down to every two days; a boss raid is just a stronger raid and has nothing to do with the timing. The plant confirmation, the Raids, Base zone and Upgrades pages say so. "Reward tier" is the one term used (the Overview already says Tier).
* **Mechanism.** A line starting with `[pack] ` or `[standalone] ` is shown to that edition only, so an edition difference sits inside its card. Bullet labels (`Careful:` in red, others in brass) are coloured by `RichText`.
* **Tests.** 95 beacon unit tests: card format, short sentences, every registered block/item has a card (`LangKeysTest`), edition-line rules (`GuideTextTest`). New real-client scenario `tools/qa/QaGuide.java.txt` pages through every screen of the guide.

## 0.0.8 — Mess Hall v6 UI pass (2026-10-09)

Feature `feature/messhall-ver-6` (owner override), on top of v6 `cfba0ab`. Protocol **27-pack / 27-standalone**. Details: `docs/MESS-HALL-V7-UI.md`.

* **Kitchen on one screen.** The Mix guide cookbook is gone. Every recipe is an icon on the Mess Hall screen: click to choose, hover for values, feeding foods and whether your pack can make it. **Gather ingredients** fetches the right foods from the table and the pack (Mk IV doubling included), **Take back** returns the table, one primary button cooks. A two-recipe sandwich takes four clicks instead of about twenty. The order and mode are stored with the hall. Gameplay rules and numbers are unchanged.
* **Dispenser feedback.** Taking a bowl and using the Milk Dispenser now show what happened (bowls left, what the milk cleared, milk left), with sound and particles. The Milk Dispenser and Milk Bottle no longer spend milk when you have no effects to clear.
* **UI restyle of the v4-v6 screens.** Bowl and Milk Dispenser screens, Workshop Fabrication (grouped, short names, descriptions), the weapon-table Upgrade view, and the Kitchen, Legendary mixes and Stations guide pages.
* New pure `MealPlanner` shared by preparation and Gather; 12 new unit tests (88 beacon unit tests in total). New real-client scenario `tools/qa/QaKitchen.java.txt`.

## 0.0.7 — Mess Hall v6 (2026-10-09)

Feature `feature/messhall-ver-6`, based on v5 `5eebf94`. Ordinary meals remain ordinary unless a legendary recipe is selected in the new detailed Mix guide. Separate bread-only sandwich base contributes no Grain; all eleven ordinary effects now have vanilla ×2 pairs, supporting any three chosen ordinary effects within six mixing slots. Milk Dispenser adds an 8,000 mB Forge-fluid tank, container returns, fill bar and 250 mB complete effect/meal cleanup; Milk Bottles are craftable and drinkable. Both dispenser stock menus require empty main/off hands. Imported owner shovel/sandwich/dispenser art is protected from regeneration. Beacon Settings moves beside Guide; four main tabs and eleven compact fabrication cards show costs without paging. Field Guide/GDD updated; protocol 26; both-edition test bundle includes separate TaCZ Attributes 1.4 JARs. Actual validation: `MESS-HALL-V6-TESTING.md`.


Newest first. One entry per delivered round; the detail of each lives in `docs/ENERGY-AND-SUPPORT.md` and the git history.

* **0.0.6 / Mess Hall v5 feature test build (protocol 25)** — Beacon Workshop Fabrication sells all ten starter installations. Each equipment table upgrades itself; current-table Age badge has a larger highlight. Material icons accompany upgrade hover costs. Fuel/Field Radio guns (Apocalypse Einstossflammenwerfer 46 and Wex, plus any indexed FUEL weapon) are excluded from Universal crafting, including direct requests. Mess Hall uses one Forge ingredient tooltip, fits a 240-pixel GUI, and hides/inactivates sandwich output in Stew. Vanilla foods provide ordinary effects; fifteen legendary pairings cover all pairs of six groups and replace paired groups’ ordinary bonuses. Animated colored links and matching buff icons identify paired ingredient slots; hover explains the groups and resulting buff. Ten new gun effects include recoil reduction. Category doubling unlocks only for Mk IV stew: two distinct foods for ordinary bonuses, four (two per group) for legendary bonuses; zero strength bars below Mk IV and exactly two at Mk IV. Bowl Dispenser: one bowl-only stack, right-click dispenses, crouch-use stocks, two tall. Recovery shovel deals zero damage with intrinsic Knockback II. Creative inventory has stable categories and only one generic Mess Hall starter. Field Guide now includes v3–v5 gameplay and a legendary recipe page; resource generation no longer overwrites guide prose. Build/test evidence and limitations: `MESS-HALL-V5-TESTING.md`. Required TaCZ Attributes 1.4 JAR remains a separate included file in each test-bundle edition. Feature branch only; no merge or public release.
* **0.0.5 / Mess Hall v4 test build (protocol 24 unchanged)** - Sunlight protection prevents daylight ignition instead of clearing combat fire. Raids learn eleven trap damage classes after their first damaging wave, gain 25% resistance per following wave up to immunity, announce each step, save progress and reset learning for each raid; attributed weapons/turrets/support attacks are excluded. Firepower and Quick Hands move to required leopoko TaCZ Attributes 1.4 global attributes, replacing the old direct damage/reload accelerator. Reload rate transitions preserve progress and native staged timestamp adjustments; reload clip names drive client speed and cleanup. Demolition follows actual explosive TaCZ kinetic behavior across gun categories, with a safely gated optional LesRaisins 0.4.3 grenade path. Recipes, costs, servings, models, pairs, bonuses, display and meal timer/save lifecycle are retained. Built both editions and ran production standalone tests; fixed the injected-field bridge and pointed dripstone classification. The test ZIP includes the required TaCZ Attributes JAR separately, licenses, checksums and validation notes. Delivery is authorized to `feature/messhall-ver-4` only, based on v3 `2ecd127`; no merge or public release. Fresh worlds per feature until 1.0; no legacy footprint migration. See `MESS-HALL.md`, `RAID-ADAPTATION.md` and `MESS-HALL-V4-IMPLEMENTATION.md`.
* **0.0.4 / Mess Hall v3 feature preview (protocol 24)** - Painted 2 wide x 2 tall x 1 deep Mess Halls and 1 x 1 x 2 Cook Pot; meals are shown as vanilla potion effects with a Home Zone effect (custom HUD and packet removed); sixteen effects and a trait for every vanilla food; pair bonus (x2) for two foods of one family in a stew; redesigned kitchen screen (tier badge, effect icons, effect cards, pot meters, animation); raiders march straight at the beacon and dig only when blocked (the 40-tick breach check could never fire on most worlds), spawn on open surface ground at or above the beacon, carry a red exclamation mark and are immune to sunlight; vexes and phantoms are steered straight in; siege creepers dig only at walls near the base; every paratrooper wears its parachute; platform sign post visible from every angle; gun meal effects raised. Delivered on `feature/messhall-ver-3` only.
* **0.0.3 / Mess Hall v2 feature preview (protocol 23)** - Stew batches consume 4/7/10/12 units for 4/8/12/16 servings; sandwiches use one staple and one or two fillings. Distinct item types score composition once, independent of stack quantity. Add vanilla ingredient sources and scoped TaCZ Firepower, Quick Hands, Brawler, Heavy Hand and Demolition hooks; show costs, consumption and actual effects in the kitchen/HUD. Delivered on `feature/messhall-ver-2` only, per owner instruction; see `docs/MESS-HALL.md` and v2 validation record.
* **0.0.2 / Round 6 (protocol 22)** - Mess Hall Mk I–IV (2×2×1), Cook Pot (2×1×1), separate Sandwich/Stew menus, datapack ingredient traits, portable composed sandwiches and persistent communal servings; owned long-duration player meals with 2× home strength and a frozen/resumable field timer; capacity/serving progression, edition-specific kitchen upgrade prices, live pot board and meal HUD. Added targeted unit/server/UI checks and updated GDD, field guide and handoff. Functional models use vanilla textures; painted art and long co-op balance remain playtest work.
* **0.0.1** (first release on `dev`; contains everything below) - Eternal Defense game design document (`docs/GAME-DESIGN-DOCUMENT.md`); update it with every gameplay change.
* **Round 5 (protocol 21)** - Beacon is the reward chest; Bunker Buster spares obsidian, crying obsidian, bedrock; Ardent Energy about 1 kill in 15; Exchange with Sell and Buy tabs;
  per-edition economy (`Economy`); platform sign above the platform; return zone (no build) and safe return spot; special raids (air, paratroopers, siege, swarm);
  zone outline stale-limit fix (best guess); operator commands `/eternaldefense next-raid <type>` and `/eternaldefense test-raid <type>`.
* **Round 4 (protocol 20)** - Cannon turns before firing (client sync bug fixed), Barrage in process / Cannon preparing messages, Bunker Buster digs its whole box and hurts players,
  Area of Effect and Dimensional Link upgrades, fire support icons and HUD banner, owner nameplates, workshop sells platform and shop, wider placement.
* **Round 3** - Two-block-tall gear models, cannon menu, ten upgrades, six fire support types, per-player choice, healing aura, two-trip portal.
* **Round 2** - Per-player bases and zones, relocation with the shovel, flares, supply parcels, Return portal, Fire Support.
* **Round 1** - UI redesign (beacon screens, Weapon Platform browser, Gun Guide card, field guide), Ardent Energy and Exchange Shop, base support.
