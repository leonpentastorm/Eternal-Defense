# Eternal Defense: Game Design Document

| | |
| --- | --- |
| **Title** | Eternal Defense |
| **Document / project version** | 0.0.18 (see `VERSION`; 0.0.x per update on `dev`, 0.1.0 when the owner calls it, 1.0.0 on merge to `main`) |
| **Platform** | Minecraft 1.20.1, Forge 47.x, Java 17 |
| **Status** | Active development on `dev`, which since 2026-10-10 includes everything up to 0.0.17 (stuck raiders, Raider Gates, Optimize Path, Beautify Path, raid music player); fresh worlds per feature until 1.0; what was tested is listed per round in `docs/*-TESTING.md` |

> ## KEEP THIS DOCUMENT CURRENT
> **Every developer (human or Claude) who changes gameplay, numbers, UI flow, economy, raids, controls or editions MUST update this document in the same commit**, then:
> 1. bump the document version below (patch for corrections, minor for new systems),
> 2. add a row to the *Revision history*,
> 3. update `docs/CHANGELOG.md` and the status in `docs/HANDOFF.md`.
>
> Numbers here mirror the code (`Economy`, `SupportRules`, `CannonUpgrades`, `Rules`, `RaidTypes`, `ArsenalConfig`); if code and this document disagree, the code is right and this document is a bug. Items marked **(TBD)** are not designed yet; items marked **(unverified)** exist in code but were never playtested.

## Revision history

| Doc version | Date | Author | Change |
| --- | --- | --- | --- |
| 0.0.18 | 2026-10-10 | Development | **Hand-tuned sounds and fire support rework.** The owner's sounds for the cannon (turning loop, lock, shot), the incoming round, the Bunker Buster, the cluster bomblets, the shockwave and a beacon attack alarm; every other explosion uses the game's own explosion sound. The cannon fires the moment its lock sound ends; rounds land 5.15 s after the shot (was 1.5 s), when the incoming sound hits. Turret 1.5 times slower (Faster traverse 3 = the old unupgraded speed). Quantum tunneling is needed only under 3 or more solid blocks of cover. Beacon: Defense is paid in reinforced plating, the Vertical zone in 2 of each of the four parts (pack), Reconnaissance has three levels (raiders glow after 5 minutes without it, then 3, 1, at once). Fire supports: Cluster Strike, Napalm Carpet, Cryo Shell and Gravity Well are one round each (Volley makes the last three last longer); napalm lights real fire, cryo lays snow, the starshell places hidden light; Gravity Well (wider, a vortex sprite) and Shockwave (beacon-zone wide, pushes to the edge) do no damage, nor does Cryo; Arrow Cluster fires tipped arrows, 6 volleys. No new packet (protocol stays 28). |
| 0.0.17 | 2026-10-10 | Development | **Raid music player.** The owner's six songs (four ordinary, one boss, one special) join the three original themes of 0.0.16 in three pools. Every wave starts a song from its pool and loops it until the next wave, which fades (3 s out, 1.5 s in) to another song; the order is random but fixed per raid and base, so every player hears the same song. The boss wave of a hard raid plays a boss song (0.0.16 played the boss theme for the whole hard raid). The music now plays for every player in the Overworld while a raid runs, not only near the beacon. A player strip under the beacon status card shows the song's name, a level meter, the time and a progress bar for the whole raid, also where the card is hidden. Beacon Settings: two personal switches, Raid music and Music player (client config). No new packet (protocol stays 28). |
| 0.0.16 | 2026-10-10 | Development | **Beautify Path.** Raid music (three original themes: ordinary, special and boss raids; a hard raid plays the boss theme throughout) for players near the beacon, and an original victory fanfare. New cannon, flare, shell, bomb and blast sounds heard across the base; flares land with a flash and a signal plume. Fire support rounds are now seen: a 3D shell or Bunker Buster bomb falls from 48 blocks above the flare with a whistle and lands when the shot resolves (**flight time 1.5 s for every type, was 0.5 s**; the Bunker Buster was already 1.5 s); bigger blasts with screen shake (honours Screen Effect Scale). A Raider Gate now also opens at the destination: the raider steps out of a second red gate that closes 2 s later. **Six new fire support types** (Cluster Strike, Cryo Shell, Napalm Carpet, Gravity Well, Shockwave, Starshell). Cannon menu: scrolling type list, three-level upgrades (Area of effect included) on top, maxed upgrades still explain themselves. No new packet (protocol stays 28). |
| 0.0.15 | 2026-10-10 | Development | **Optimize Path (no gameplay change).** Code that runs while the game is played was made cheaper without changing any rule, number, UI flow, raid behaviour or edition difference: the beacon's TaCZ block-impact hook listens to TaCZ's own event instead of every event; the base score caches per-block classifications and finds nearby lights and furniture through a grid (same score, same floor cap); the gun-pack resource path check no longer compiles a regular expression per path; the Special Forces corpse sweep is skipped without that mod; deaths outside a raid skip an id lookup. Small rule lookup tables are built once. This document's rules are unchanged; see `docs/OPTIMIZE-PATH.md`. |
| 0.0.14 | 2026-10-09 | Development | **Raider Gates.** A ground raider that walks up to lava, a chasm or a wide lake, or makes no progress for 25 seconds, no longer disappears silently: it stops, channels for 8 seconds beside a red gate (the Return portal sprite, tinted red and slower), and reappears at the edge of the staging ring. The channeler can be shot (each hurt second delays it 3 seconds, at most 10 in total) and a kill cancels it; one gate serves a cluster of raiders (8 at a time). Three gates, then the raider is withdrawn; bosses are re-gated and never withdrawn. Kitchen: a stew can be cooked over the stew still in a pot after a confirmation; stew servings per pot are lowered from 4/8/12/16 to 3/5/7/9 (Mk IV: 16 to 9); the Cook Pot's lamp gives light and its board text is always readable. Field Guide Raids card line. No new packet (protocol stays 28). |
| 0.0.13 | 2026-10-09 | Development | **Stuck raiders no longer lose raids.** A raider that makes no real headway toward the beacon for 25 seconds (and is not fighting, shooting or digging) is moved back to the staging ring, up to three times, and then withdrawn; bosses are moved again and never withdrawn. Spawn directions are now checked once per raid for a marchable corridor (water, lava, cliffs) and clean directions are preferred. Raiders still march straight; no detour pathfinding. Opt-in diagnostics log (`-Darsenal.stuckLog=true`). Field Guide Raids card gets one line. No packet changes (protocol stays 28). |
| 0.0.12 | 2026-10-09 | Development | **Feature guide for designers** added (new section after the core loop): every feature explained in plain words (what it is, what the player does, how it behaves, its job in the game, the dials, what has not been played yet), a feature-dependency table and a playtest-first list. Section 5 is now labelled as the rulebook. Stale technical note corrected (protocol 28). No rules or numbers changed. |
| 0.0.11 | 2026-10-09 | Development | **Mess Hall levels rebalanced** (Mk I sandwiches only, 15-minute meals, 3 food slots; Mk II unlocks stew, 20 minutes; Mk III 25 minutes and unlocks ×2 doubling, in sandwiches and stew; Mk IV 30 minutes and all 6 food slots, the only level that can double a legendary effect); meals remember their length; protocol 28. Kitchen screen re-laid out in two columns (376 × 238): actions on the left, effects to choose on the right, calmer recipe tiles, chips for meal length, food slots and ×2. Weapon/Ammo/Attachment/Armor table Upgrade view rebuilt as an Age ladder, a materials grid and one Upgrade button. |
| 0.0.10 | 2026-10-09 | Development | Field Guide side menu restored for every window size, with an item icon per page (list with titles when tall enough, two-column icon rail with hover names when short). UI only. |
| 0.0.9 | 2026-10-09 | Development | Field Guide rewritten for a player who has just spawned in: every feature (including Gun Guide and Gun Displays) is a card with what it is, what it gives, how it works, how to unlock it and what can go wrong; red "Careful" notes for resets and other catches; edition-only lines inside one card; plant confirmation and item tooltips warn that only the shovel removes the beacon and that this resets the campaign; standalone upgrade hint corrected to Ardent Energy. No rules or numbers changed. |
| 0.0.8 | 2026-10-09 | Development | Kitchen UI pass: one-screen recipe palette with Gather and Take back (replaces the Mix guide cookbook), order and mode stored with the hall, pack-aware recipe statuses; Bowl and Milk Dispenser feedback (milk is no longer spent when there is nothing to clear); dispenser, Workshop Fabrication and table-upgrade screens restyled; kitchen guide pages rewritten; protocol 27. |
| 0.0.7 | 2026-10-09 | Development | Explicit ordinary/legendary selection and cookbook; independent base bread; every ordinary ×2 triple fits six slots; milk fluid tank and complete cleanse; empty-hand dispenser stock; compact eleven-item fabrication; header Settings; owner kitchen/shovel/sandwich models; protocol 26. |
| 0.0.6 | 2026-10-08 | Development | Workshop Fabrication and independent upgrades; Field Radio weapon exclusion; Mk IV-only doubling; fifteen legendary gun recipes and ten new icons; bowl dispenser; tooltip and compact-layout fixes; zero-damage recovery shovel; creative categories; guide v3–v5 coverage. |
| 0.0.5 | 2026-10-08 | Development | Sunlight-only protection; raid-local trap adaptation (+25% per later wave to immunity); required TaCZ Attributes 1.4 damage/reload backend with progress/animation continuity; behavior-based explosive projectiles and optional LesRaisins 0.4.3 grenades; fresh worlds per feature until 1.0; v4 test bundle includes the separate required backend; production tests correct dripstone classification and the backend timestamp bridge; protocol 24 unchanged |
| 0.0.4 | 2026-10-07 | Development | Mess Hall v3: 2 wide x 2 tall x 1 deep halls and 1 x 2 pots with the painted models; meals shown as vanilla potion effects (HUD removed); sixteen effects and every vanilla food; stew pair bonus; juiced kitchen screen; raiders marched straight at the beacon, marked with a red exclamation mark, immune to sunlight, spawned on open surface ground at or above the beacon; flyers steered straight in; siege creepers dig; every paratrooper wears a parachute; protocol 24 |
| 0.0.3 | 2026-10-07 | Development | Mess Hall v2: 4/7/10/12 stew costs, distinct-type scoring, five scoped TaCZ buffs, cost/effect UI; protocol 23; feature branch only |
| 0.0.2 | 2026-10-07 | Development | Mess Hall Mk I–IV, ingredient traits, portable rations, communal stew, persistent home-enhanced meals; protocol 22 |
| 0.0.1 | 2026-10-07 | Claude | First version, covering rounds 1 to 5 |

---

## 1. Vision

Eternal Defense turns a TaCZ gun-pack world into a **shared base-defense campaign**. Players plant one Defense Beacon, build a base worth defending around it, and survive raids that grow with the base and the team.
Industry (Create) feeds the war effort; farming and a tiered kitchen feed the defenders; every player also owns a personal support gun that answers their call. The fantasy: *you hold the line, the factory keeps you supplied, and the cannon behind you is yours.*

**Pillars**
1. **The base is the objective.** Lose the beacon and the campaign ends; hold it and everything improves.
2. **A reason to build well.** Base quality raises rewards and makes raids bigger, so beauty and function are the same investment.
3. **Progress, not tedium.** A won raid replenishes ammunition and moves the player forward; a bad raid is recoverable.
4. **Two ways to power:** a factory (fast, big) or target farming (slower, hands-on). Both stay practical.
5. **Readable and teachable.** Clear icons, plain requirements, a field guide, and a warning before every raid.

**Target players:** a small co-op team on a modded server (the pack), or any TaCZ world (standalone). Multiplayer shared campaign; each player owns their own support gear.

## 2. Editions

| | Pack edition | Standalone edition |
| --- | --- | --- |
| Needs | TaCZ 1.1.8-hotfix2, TaCZ Attributes 1.4, Create, KubeJS and the pack recipes | TaCZ 1.1.8-hotfix2 and TaCZ Attributes 1.4 |
| Upgrade currency | The mod's own factory parts (reinforced plating, logistics modules, resonance coils, restoration matrices) | Ardent Energy |
| Ardent Energy used for | Flares, the cannon purchase, Exchange goods | Everything, including beacon and cannon upgrades |
| Ardent drop rate | about 1 kill in 15 | about 1 kill in 12 (factor 1.3) |
| Exchange sells (you hand in) | Create factory surplus | Rare vanilla materials (diamond, blaze rod, ender pearl, ...) |
| Exchange buys (you pay energy) | Create supplies and ammo coins at a premium | Ammo coins and basic materials |

One source tree builds both (`/arsenal-build.properties` flavor in the jar). Every price is in `Economy`. Display furniture artwork is All Rights Reserved by its creator.

## 3. Mods in the package

* **Defense Beacon** (`arsenal_beacon`): the campaign, raids, support system, Weapon Platform, Exchange.
* **Gun Guide**: client HUD showing the real controls of the held gun, with the player's own key bindings, key-conflict warnings and a jam alert.
* **Gun Displays**: eleven stands, wall racks and glass cases for showing guns. Decoration only; they count like any other placed block for base score.

## 4. Core loop

1. **Prepare** (preparation phase, daytime): build, farm, prepare sandwiches and stock communal stew, craft, trade, upgrade, set the raid level.
2. **Scan:** when the raid starts, the base is scored and snapshotted.
3. **Raid:** waves of attackers come from beyond the zone, breach, dig and shoot; the team defends the beacon.
4. **Resolve:** victory repairs all damage and pays rewards (beacon is the reward chest, plus Ardent Energy); defeat leaves the ruins.
5. **Announce next type:** the kind of the next raid is shown at once.
6. **Spend:** upgrades, flares, Exchange; loop back, stronger.

## Feature guide for designers

**How to read this.** Section 5 is the rulebook with the exact numbers and the code names. This section goes feature by feature and says, in plain words: what the feature is, what the player does with it, how it behaves, what job it does in the game, and which dials change it. It is written for someone who has not read the code. A line marked *Not played yet* has only been checked by unit tests, a real-client run or a code read, never by a team playing several raids (see section 9). Where the pack and standalone editions differ, both are named. If this guide and the code disagree, the code is right.

### The game in one minute

A team plants one **Defense Beacon**. It makes a protected **zone** around it. Inside the zone the team builds; the beacon **scores the base**, and the score sets a **reward tier** from 0 to 10. At night, **raids** march on the beacon, harder and more often at higher tiers. Winning pays loot and **Ardent Energy** (the soft currency) and puts back everything the raiders broke. Energy and materials buy **guns, ammo and gear** at the workshop tables, a personal **support cannon** that delivers supplies, rescues and bombardments, and **beacon upgrades**. A **kitchen** turns food into short combat buffs. Everything is taught by an in-game **Field Guide**. The loop is: prepare, survive, spend, get stronger, face a bigger raid.

### How the features feed each other

| Feature | Gives the player | Needs | Feeds |
| --- | --- | --- | --- |
| Defense Beacon and zone | A place to build and defend; the raid clock | Planting once | Everything below; all placed gear must sit inside the zone |
| Base score | Reward tier (0 to 10) | Building inside the zone | Raid size, raid frequency, reward size |
| Raids | Loot, Ardent Energy, Ammo Coins, the first Weapon Platform | A planted beacon and a defended base | Energy and ammo supply; pressure to build and prepare |
| Ardent Energy | Spending power | Kills and won raids | Flares, cannon, Exchange goods, standalone upgrades |
| Exchange Shop | Converts spare materials to energy and back | Zone, a few materials | Lets factory players sell surplus and non-factory players buy parts |
| Weapon, Ammo, Attachment, Armor tables | Guns, ammo, attachments, armor | Materials (or coins for ammo), Ages | The fighting power of the team |
| Support Platform and Cannon | Supplies by parachute, a ride home, bombardments | Energy, one platform and one cannon per player | Survivability and burst damage in raids |
| Mess Hall, Cook Pot, dispensers | Meals with 15 to 30 minute buffs | Food, a hall level | Gun and survival stats during raids |
| Raid control (cap, start, break) | Control over pace and difficulty | Nothing | How much risk the team takes |
| Field Guide, tooltips, confirmations | Understanding | Nothing | Onboarding and fewer irreversible mistakes |

### Part A. The campaign

#### Defense Beacon
* **What it is.** The heart of the campaign: a glowing crystal block. One per world, Overworld only, shared by the whole team. It cannot be broken or moved by normal play.
* **What the player does.** Plants it once (a confirmation screen explains what cannot be undone). Right-clicks it to open four tabs: Overview, Upgrades, Workshop Fabrication and Raid break. Settings and the Field Guide sit in the header.
* **How it behaves.** Planting starts the campaign and a short first raid at once. The beacon has health. A bigger Core raises it. Raiders hitting it, and defenders dying near it (three deaths), lower it. At zero it switches off: that is a defeat. Wild hostile mobs can also hit an exposed beacon they can see from 24 blocks, even during a raid break, so players cover it with solid blocks. Repairing (Overview, Repair) gives +250 health and restarts the campaign. The cost is 6 Ardent Energy (standalone) or 8 of the pack part.
* **Job in the game.** It gives the fight a place and the team one thing to lose (pillar 1). The planting spot is the biggest permanent decision of the game.
* **Dials.** Health and radius per Core level in `Rules`; repair price in `Economy`.
* **Designer watch-out.** The only exit is the full reset (see Recovery Shovel). New players are warned in the plant confirmation, the beacon tooltip and the Field Guide.

#### Base zone
* **What it is.** The area around the beacon that belongs to the team, drawn as a moving cyan grid (it can be switched off in Settings).
* **What the player does.** Builds inside it. Places the kitchen, Exchange Shop and support gear only inside it.
* **How it behaves.** Radius grows with Core: **8, 12, 18, 24** blocks. The Vertical branch makes it taller: it starts 3 blocks down and 8 up and reaches 16 down and 48 up at the fifth level. The zone is centred on the beacon. A clear 3 × 3 space, two blocks tall, around the beacon is needed for the largest Core level. Weapon tables placed inside count toward the base score.
* **Job in the game.** It is the design boundary: everything the player owns that matters is inside, and the raid spawns are outside. It also defines "home" for meal doubling (see Mess Hall).
* **Dials.** Radii per Core level; vertical limits; the zone grid toggle is a client setting.

#### Base score and reward tier
* **What it is.** A grade for the base. When a raid starts the beacon scans the zone: structure, material palette, detail blocks (stairs, slabs, fences, doors), lighting, furnishings (beds, bookshelves, barrels, chests, tables), usable closed rooms, machinery and weapon stations.
* **What the player sees.** The Overview shows the score and the reward tier.
* **How it behaves.** The tier runs 0 to 10. Each of the five Logistics levels adds one tier; a good base adds up to five more. A higher tier makes the next raids bigger, makes them come more often (about weekly at tier 0, down to every two days at the top) and makes the rewards bigger. Natural terrain scores nothing, and ore blocks score no more than plain blocks.
* **Job in the game.** It makes building well and playing well the same investment (pillar 2): a beautiful, functional base is both the reward engine and the difficulty setter. The raid level cap is the escape valve for a team that grew faster than its gear.
* **Dials.** Scoring weights in the base-scoring code; tier from Logistics in `Rules`; schedule intervals per tier.
* **Not played yet.** Whether the scoring weights reward the right things, and whether the tier curve feels fair.

#### Beacon upgrades (six branches)
Bought in the Upgrades tab with the team standing near the beacon, between raids. In Creative everything is free. Packing up the beacon wipes every upgrade.

| Branch | What it does | Levels |
| --- | --- | --- |
| Core | Wider zone, tougher beacon (radius 8, 12, 18, 24) | 3 upgrades (Mk-1 to Mk-4) |
| Logistics | Each level adds one reward tier | 5 |
| Defense | 15, 30, 45 and 60 percent less damage to the beacon | 4 |
| Restoration | Faster rebuilding after a raid; the beacon heals 5, 10, 15, 20, 25 percent of its health after a victory | 5 |
| Vertical | Zone height: it starts 3 blocks down and 8 up and ends 16 down and 48 up | 5 |
| Reconnaissance | Raiders still alive glow through walls sooner: after 5 minutes of a wave without it, then 3, 1, and from the start of the wave (0.0.18; it was one purchase and the base was one minute) | 3 |

* **Prices.** Standalone, in Ardent Energy: **4, 8, 14, 22, 32** by level. Pack: **8, 16, 32, 64, 128** of the branch's own factory part: Core and Defense reinforced plating (Defense was resonance coils before 0.0.18), Logistics logistics modules, Restoration restoration matrices, Reconnaissance resonance coils; the Vertical zone takes a quarter of that of every one of the four parts (2, 4, 8, 16, 32 each; before 0.0.18 it was logistics modules only).
* **Job in the game.** The permanent sink for energy and factory output, and the only way to raise the reward tier by spending.
* **Designer watch-out.** Logistics raises both rewards and danger. The Field Guide says so in red on that card.

#### Reward chest
* **What it is.** The beacon itself is the chest. A 54-slot chest for the whole team, opened from the Overview.
* **How it behaves.** Prizes are put in each time the screen opens; whatever does not fit waits in a queue. If the beacon is destroyed, the contents spill out. If the campaign is packed up, unclaimed prizes are thrown away.
* **Job in the game.** Keeps raid loot out of the pack so it cannot be lost on death, and gives raids a clear "collect" moment.

#### Recovery Shovel
* **What it is.** A special iron shovel and the player's remote control for the beacon. Everyone starts with one; it is craftable (iron above and below, copper left and right, redstone in the middle).
* **What the player does.** Right-clicks the air to open the beacon panel from far away; hits a monster to knock it back (it deals no damage, whatever the player's strength); uses it on their own Support Platform or Cannon to pick it up free of charge, keeping upgrades and contents; uses it on the beacon to pack the whole campaign up.
* **How it behaves.** Using it on the beacon asks for confirmation. The result is a full reset: every upgrade, win and reward tier returns to zero, raids stop, unclaimed prizes are thrown away, the player gets one fresh beacon back and the buildings stay.
* **Job in the game.** The only way to relocate or abandon a base. It makes the plant decision weighty without making it a trap.
* **Designer watch-out.** Anyone with a shovel can reset the world for everyone. The game asks first; there is no permission system.

### Part B. Raids

#### Raid schedule and control
* **What it is.** The pace of the game: when raids come and how hard they are allowed to be.
* **How it behaves.** An introduction raid starts the moment the beacon is planted: short, gentle, always ordinary. Winning it hands over the first Weapon Platform. After that raids come at night, about once a week at reward tier 0, more often as the tier rises. The clock counts **active days** only (days when someone is online).
* **What the player controls (Raid break tab).**
  * *Raid level cap*, a dial from 0 to 10. Lower it for an easier raid and a smaller prize; it never touches base score or upgrades. The whole team shares it.
  * *Start raid now.* Starts a raid at the shown level at a time of the team's choosing. The base is scanned first and any bought break ends. It cannot be undone.
  * *Raid break.* Pay to push the next raid back five active days. Each purchase costs more than the last and purchases add up. Every break also makes the next raid tougher and richer (up to three tiers, never above the cap). Pack price: brass, precision mechanisms and gold. Standalone: copper, diamonds and gold.
* **Job in the game.** Three ways to trade risk against reward without ever stalling the game: wait, lower, or push on. The break is a deliberate "pay for time, get a bigger raid" trade.
* **Dials.** Intervals per tier and break prices in `Rules` and `Economy`.

#### Anatomy of a raid
* **What happens.** The base is scored and snapshotted. Waves then come from outside the zone: **3 + tier** waves, with at most five extra. Raiders scale with the number of defenders. They spawn on open surface ground at or above the beacon's level, never underground (the rule relaxes after 30 and 90 seconds if no such spot exists), walk **straight at the beacon**, and only stop to dig through doors and walls when they stop making progress. Each raider wears a **red exclamation mark** and is immune to sunlight. Ranged raiders hold their distance. Any raider left alive after one minute glows.
* **A raider that cannot arrive never decides the raid.** Raiders are not given detours (they march straight, by design), so the game watches for two things. If the straight line ahead (10 blocks) shows lava or magma, a drop of 6 or more blocks or 8 or more blocks of water in a row (a short pond is waded), or if a raider has not got 3 blocks closer to the beacon for 25 seconds while it is not fighting, shooting or digging, it **opens a red gate**: it stops and channels for 8 seconds beside the gate, which anybody can see, and then steps out of a second red gate at the edge of the staging ring (it opens 3 seconds before, so players near the exit see where the raider will come out, and closes 2 seconds after), away from every player, and marches again. While it channels it can be shot: each second it is hurt delays it by 3 seconds (never more than 10 in all), and killing it cancels the gate. Raiders stuck at the same place share one gate (8 channel at a time, the rest wait). After three gates a raider is withdrawn (no drop; chat says "N stuck raiders withdrew" once per wave); a raid boss is gated again each time and never withdrawn. Flyers, parachutists in the air and Special Forces soldiers are left alone. The spawn directions are also checked once per raid for water, lava and cliffs, and clean directions are used first.
* **How it ends.** Victory: everything the raid broke is put back (a damage journal), and the prizes go to the reward chest, with Ardent Energy and Ammo Coins. Defeat: the beacon switches off and the damage stays until repaired. Spent ammo, fuel and tool wear are never refunded either way.
* **Job in the game.** The pressure the whole game pushes against. Straight-line marching makes the fight readable and the base's geometry matter.
* **Dials.** Wave count, spawn rules and attacker counts in the raid code (`Rules`, `RaidTypes`); stall time, progress distance, the probe limits, channel time, damage delay, gate count and capacity and the busy rules in `RaidMarch`; corridor rules in `RaidSpawns`.
* **Not played yet.** Ground spawning for Air, Siege and Swarm raids in a real world; two-human co-op; whether 25 seconds, 3 blocks, 8 seconds of channel and 3 gates feel fair on real terrain, and how the red gate reads in play (the diagnostics log shows what a stuck raider stood on).

#### Boss raids
* **What it is.** Every third raid is a boss raid. It is simply a stronger raid. It has nothing to do with how often raids come.
* **How it behaves.** 25 percent more attackers, a boss arrives with the final wave, completion rewards are 50 percent larger. In the pack the boss is a modded boss with a red boss bar, and beating it also gives bonus medical kits and grenades; in standalone, without boss mods, it is a vanilla Ravager. The Overview announces when the next raid is a boss raid.
* **Job in the game.** A rhythm: two raids to recover and prepare, a third that tests the build.

#### Special raids
* **What it is.** From the fourth raid on, half of all raids (also boss raids) carry a twist, each asking for a different counter-build. The next raid's type is shown at once after each raid and in the Overview.

| Type | What it asks of the base |
| --- | --- |
| Air raid | Flying mobs ignore walls. Build anti-air, not thicker walls. |
| Paratroopers | Heavy mobs drop from high up on red parachutes; shoot them while they float. |
| Siege | Creepers dig into walls and shooters fire through the holes. |
| They are thousands | Only zombies and husks, double count, spawning faster, up to 96 alive. |

* **Reward.** A won special raid adds 3 + 2 × tier Ardent Energy.
* **Job in the game.** Variety. Each type punishes one defensive habit (wall-only bases, one weapon type, no area damage).
* **Dials.** `RaidTypes`: `CHANCE_PERCENT`, `FIRST_SPECIAL_RAID`, count factors.
* **Not played yet.** Is 50 percent too frequent? Does the air raid need a stronger anti-air toolkit than the player has?

#### Special Forces
* **What it is.** Armed soldiers who join raids once the reward tier is 4 or more.
* **How it behaves.** A warning comes first. One soldier per wave at tiers 4 to 6, heavy soldiers from tier 7. They shoot from far away, so cover (sandbags) matters. Their bodies can be looted for about ten minutes.
* **Job in the game.** An answer to teams that ignore ranged play, and a loot source for the high tiers.

#### Trap learning
* **What it is.** Raiders get used to the same kind of trap.
* **How it behaves.** The first wave takes full damage from a trap class. Each later wave takes 25 percent less from it, until after four steps it does nothing. Eleven classes (fire, fall, drowning, suffocation, spikes, crushing, freezing, explosion, projectile without an owner, magic, lightning). Chat announces each step. The learning resets at the next raid. Turrets, player weapons, projectiles fired by players and the support cannon are exempt and never get weaker. Raiders are protected from sunlight only: flame arrows, lava and fire traps still hurt, and fire traps adapt like any other class.
* **Job in the game.** Stops one-trap fortresses and nudges the team toward mixed defences and active fighting.

#### Raid music and the music player (0.0.17)
* **What the player sees and hears.** When a raid starts, the game's own music stops and a raid song plays for every player in the Overworld. Each wave starts a new song and loops it until the next wave; the change is a 3-second fade. The boss wave of a hard raid has its own song, and special raids have theirs. A small strip under the beacon's status card shows the song's name with a moving level meter, the time and a progress bar, for the whole raid (the status card itself still hides away from the beacon). Winning plays a short fanfare.
* **Dials.** The pools (`RaidPlaylist.SONGS`), the fades and the quiet after a raid (`RaidMusic`), the loudness of each file (normalised to -16 LUFS by `tools/audio/import_songs.py`; volumes in `sounds.json`). Each player can turn the raid music or the strip off in the beacon's Settings (saved on their computer).
* **Job in the game.** Pillar 5: a raid should feel like an event, and the change of song tells everyone a new wave has started, even far from the beacon.
* **Not played yet.** Nothing was listened to in a real game (the test machine has no sound device); whether a 2-minute song looped through a long wave becomes tiresome; whether the fade between waves is too long or too short.

#### Warnings and announcements
* Chat warns a day ahead, then every six hours, then once more when the raid is only waiting for night. Big banners mark the start, each wave, VICTORY (with confetti) and DEFEAT. A red BEACON IS BEING ATTACKED warning reaches every player in every dimension, with no tool in hand needed.
* **Job in the game.** Pillar 5: readable and teachable. A player should never be surprised by a raid they could have prepared for.

### Part C. Money and trade

#### Ardent Energy
* **What it is.** The soft currency. A glowing item won by fighting; it cannot be crafted.
* **How it behaves.** About one hostile kill in 15 drops one (1 in 12 in standalone). Looting adds 1 percent per level, mobs with 60 or more health drop two, bosses always drop one. A kill needs a player or a raider behind it. Every won raid pays a fixed amount (1 + tier in the pack, 2 + 2 × tier in standalone) plus the drops.
* **What it buys.** Pack: flares, the cannon, Exchange goods. Standalone: all of that plus beacon upgrades, repairs and Mess Hall upgrades.
* **Job in the game.** The meter of progress. In the pack, factory parts pay for upgrades, so energy stays for flares, the cannon and trade. In standalone, energy is the only currency for everything.
* **Dials.** `baseDropChance`, `lootingBonusPerLevel`, `bossDrop` in `arsenal-beacon-common.toml`; `Economy`, `RaidRewards`.

#### Exchange Shop
* **What it is.** A shop booth placed inside the zone with two tabs: Sell (hand in materials for energy) and Buy (pay energy for goods).
* **How it behaves.** Buying always costs more than selling the same thing, and a unit test checks that no loop pays. Offers live in `config/arsenal-beacon-exchange.txt` (`[sell]`, `[buy]`), so a server can change them. Pack: the player hands in factory surplus and buys supplies and ammo coins at a premium. Standalone: the player hands in rare vanilla materials (diamond, blaze rod, ender pearl and similar) and buys ammo coins and basic materials.
* **Job in the game.** The bridge between a factory player and a non-factory player: one sells surplus, the other buys parts at a premium. Factory is faster by design.

#### Ammo Coins
* **What it is.** A special coin won only from raids; it cannot be crafted.
* **How it behaves.** At the Ammo Platform, Buy with coins pays for the whole batch shown. The coin price grows with the Age and the strength of the gun. Coins buy empty magazines, never filled ones.
* **Job in the game.** Makes ammunition a raid reward, so a won raid replenishes the team (pillar 3).

#### Workshop Fabrication
* **What it is.** A beacon tab selling the starter version of every station: eleven installations: the four tables, Mess Hall, Cook Pot, Bowl Dispenser, Milk Dispenser, Support Platform, Support Cannon and Exchange Shop.
* **How it behaves.** The price comes out of the player's pack at once, with a check that the output fits. Prices live in `Economy`. Each table upgrades itself at its own screen.
* **Job in the game.** One place to learn what exists and what it costs.

### Part D. The workshop tables

#### Weapon, Ammo, Attachment and Armor Platforms
* **What they are.** Four workbench stations. **Weapons**: search by name, filter by type and era, craft any unlocked gun. **Ammo**: only ammo that fits the held gun, paid in materials or Ammo Coins. **Attachments**: scopes, grips, magazines, only parts that fit the held gun; a counter shows how many are locked. **Armor**: helmets, vests, boots, pouches, plates, vision gear. A **Supplies** category in the Weapon table holds food, medical kits, grenades, tools and gear materials.
* **How they behave.** The first Weapon Platform comes from winning the first raid; the others are bought at Workshop Fabrication. A magazine crafted at the Ammo Platform is empty and is filled with loose rounds using the magazine addon. Guns that use the FUEL reload type are excluded from crafting (they need Field Radio refills) as a stability fallback.
* **Job in the game.** Converts materials into combat power, with eras gating the pace.
* **Dials.** Recipes and costs in the pack scripts and `Economy`; per-gun Age in the data.

#### Ages and table upgrades
* **What they are.** Five Ages each table can reach: Frontier, World Wars, Modern, Advanced, Exotic. Each of the four tables has its own Age. A modern pistol stays a Modern unlock.
* **What the player does.** Opens a table, picks the Upgrade tab and presses one big **Upgrade to <Age>** button. A ladder shows the Ages done, the current one and the next; a grid shows the materials needed and what the player holds. The button stays dark until the materials are present.
* **How it behaves.** Modern and later Ages need the team to have visited the Nether (and, in the pack, the Create precision parts). Locks apply in Survival. Creative is free.
* **Job in the game.** The long progression spine of the gun side of the game, in five steps.
* **Not played yet.** The pace of the Age upgrades against raids.

### Part E. The support system (one per player)

#### Support Platform
* **What it is.** A player's own landing pad and supply box, up to four levels (a supply grid from 3 × 3 up to 6 × 6; the pack upgrades cost 16, 32 and 64 plating, standalone prices are in `Economy`).
* **How it behaves.** One per player, only the owner can open it, placed inside the zone, two blocks tall with an owner sign above it. The two blocks in front of it are a no-build **return zone** that must stay clear. It stops working while the beacon is gone. The shovel moves it free.
* **Job in the game.** Personal ownership (every player has something that is theirs) and the anchor for deliveries and returns.

#### Support Cannon
* **What it is.** A big 3 × 3 gun that belongs to its owner. Everything the owner calls goes through it. Bought for 12 Ardent Energy.
* **How it behaves.** It turns toward the flare with a looping turning sound, locks on with a clunk and fires the moment that sound ends (0.0.18). It handles one call at a time. Far-away players read "Cannon preparing" and "You heard cannon fire roaring". Clicking it opens the choice of fire support and the upgrades.
* **Job in the game.** It gives each player a "button" that pays off for energy, and makes the base feel defended by something big.

#### Flares
Flares are bought at the platform's Shop tab; throwing them is free. If something goes wrong the flare is handed back.

| Flare | Price (energy) | What it does |
| --- | --- | --- |
| Supply | 3 | The cannon fires and a chest with everything in the platform's grid parachutes in (about 12 s outside, 7 s underground). The grid empties when sent. |
| Return | 2 | A portal (30 s, 5 min and usable twice with the upgrade) takes the owner to the platform from any dimension. It needs a free cell in front of the platform, or it is cancelled. |
| Fire Support | 6 | A red box marks the target and the cannon shells it with the fire support the owner chose. A second flare during a barrage waits ("Barrage in process"). |

* **Job in the game.** The three things a player in a raid wants most: a resupply, an escape, and a big explosion, each at a small price that is affordable after two or three raids.

#### Twelve fire support types and ten cannon upgrades
* **Types.** Explosion Barrage (6 shells), Arrow Cluster (8), Narukami's Favor (lightning on every hostile, 5), Bunker Buster (one bomb digging a large cube; it hurts players inside its box for half their health and is handed back if its box would touch the beacon zone), Healing Barrage, Curse of Debilitation, and since 0.0.16: Cluster Strike (bomblets over a much wider area, 5), Cryo Shell (freezes hostiles nearly in place, 4), Napalm Carpet (burning ground, 4), Gravity Well (drags hostiles together, then collapses, 3), Shockwave (throws hostiles away from the flare, 4) and Starshell (one light for 30 s: hostiles glow, players see in the dark). The owner's choice applies even to a borrowed flare.
* **How a shot looks (0.0.16).** The cannon fires up into the sky; nothing flies from it to the target. Each round is seen falling from 48 blocks above the flare (a shell banded in the type's colour, or the Bunker Buster's bomb) with a whistle, and lands when the flare resolves the shot; a flare underground (Quantum tunneling) is reached the same way, through the rock. Big blasts are seen and heard up to 160 blocks away and shake the view of nearby players.
* **Upgrades.** Traverse, Fire Rate, Volley, Damage, Quantum Tunneling (works underground), Slowness field, a lasting two-way Portal, Healing Aura, Area of Effect and Dimensional Link (works in other dimensions). About 185 energy in total in standalone; the pack pays the factory parts listed in `Economy.PACK_AMOUNTS`.
* **Job in the game.** The long energy sink after the first purchases, and a way to specialise a cannon (damage dealer, healer, controller).

### Part F. The kitchen

#### The idea
A fortress should have a kitchen feeding the war effort. A farmer or cook supports the team with portable sandwiches and big pots of stew before a raid. Casual cooking is useful; a dedicated cook supports more people without being mandatory.

#### Mess Hall (four levels)
* **What it is.** The cooking station, a 2 × 1 × 2 block placed inside the zone. It has up to six shared food slots, a bread-only **Base** slot and a sandwich output slot.
* **What the player does.** Opens it, clicks the effects to choose on the right, presses Gather ingredients, then Make sandwich or Cook stew. Buttons that do things are on the left and the effects to choose are on the right.
* **What each level adds.**

| Level | Makes | Meals last | Food slots | Doubling (×2) | Pots linked | Stew servings |
| --- | --- | --- | --- | --- | --- | --- |
| Mk I | sandwiches only | 15 minutes | 3 | no | 1 | 3 |
| Mk II | sandwiches and stew | 20 minutes | 3 | no | 2 | 5 |
| Mk III | sandwiches and stew | 25 minutes | 3 | yes | 3 | 7 |
| Mk IV | sandwiches and stew | 30 minutes | 6 | yes, and legendary effects too | 4 | 9 |

* **Prices.** The hall itself is crafted from vanilla materials or bought at Workshop Fabrication. Upgrades cost 8 / 16 / 32 Reinforced Beacon Plating (pack) or 4 / 8 / 14 Ardent Energy (standalone); Creative is free; food and pot links stay.
* **What the screen says.** Three chips under the order show the meal length, the open food slots and whether ×2 is unlocked (a padlock until Mk III). The Stew tab shows a padlock below Mk II. The Upgrade button's tooltip says what the next level adds.
* **Job in the game.** A visible ladder of four clear steps, each unlocking one thing the player can feel: a first meal, stew for the team, double effects, and the full six-slot table.
* **Dials.** `MealRules.tier` (minutes, slots, stew, doubling, pots, servings), `Economy` for prices.
* **Not played yet.** Whether 15, 20, 25 and 30 minutes and the Mk III doubling step feel right in play.

#### Cook Pot and stew
* **What it is.** A 1 × 1 × 2 pot with a chalkboard menu on it. One selected pot, linked to the hall within 8 blocks, holds a batch of stew with up to three effects and 3 to 9 servings. A lamp bar above the chalkboard gives some light (block light 10) and the board text is drawn full-bright, so the menu can be read in a dark kitchen.
* **How it behaves.** A cook consumes food units (4 / 7 / 10 / 12 at Mk I to IV) spread over stacks, including one of every food type used. A bowl right-click takes one serving and eats it at once; the bowl stays empty and reusable. Cooking into a pot that still has stew is allowed after a confirmation ("Replace the stew?": the old stew is thrown away, *Keep it* changes nothing, Escape keeps it); the server refuses a plain cook into a pot with stew, only the confirmed button replaces it. A Mk I hall cannot cook stew.
* **Job in the game.** Communal pre-raid meals: one cook feeds the whole team.

#### Sandwich
* **What it is.** A portable meal for one player, stacking to 16, eaten at any hunger level.
* **How it behaves.** One bread in the Base slot plus two or three different fillings. It gives one or two effects. The base bread gives nothing.
* **Job in the game.** The casual, personal option for solo play and for cooks without a pot.

#### Ingredients, groups and effects
* **Food groups.** Six: Protein and dairy, Grain and staples, Fruit and sweets, Fish, Vegetables, Fungi and fermented. Every vanilla food has a group and traits; the kitchen tooltips list which foods feed which effect.
* **Everyday effects (11).** Vitality (+4 health), Fortitude (+2 armor), Steadiness (+5 percent knockback resistance), Mobility (+4 percent movement), Might (+8 percent melee damage), Agility (+8 percent attack speed), Fortune (+1 luck), Recovery (heals 1 health every 5 seconds), Hearth (10 percent fire and explosion protection), Springy Step (12 percent fall protection), Strong Swimmer (+10 percent swim speed).
* **Legendary effects (15).** Each needs foods from two specific groups, one recipe for every pair of the six groups: Firepower, Quick Hands, Brawler, Heavy Hand, Demolition, Recoil Control, Focus, Snap Aim, Fast Draw, Dead Eye, Gun Mobility, Impact, Rapid Fire, Scoped Focus and Hip Focus. They improve gun damage, reload, recoil, accuracy, aiming and draw speed, bash damage and knockback, grenade radius, headshots, movement with a gun and rate of fire (the exact percentages are in section 5.8 and in the guide).
* **Rules.** A legendary effect uses up both of its groups, so those groups cannot also feed everyday effects, and two legendary picks cannot share a group. A sandwich holds at most two effects, a stew at most three. A Mk IV stew with three separate pairs, which uses all six groups and all six food slots, makes three legendary effects at once.
* **Job in the game.** Ordinary effects are survival stats; legendary effects are gun stats that ask the cook to plan around groups. The two together make the cook a real specialist.

#### Doubling (×2)
* **What it is.** A way to make one effect count twice.
* **How it behaves.** Put two different foods of the same group on the table that both feed the effect (for example cooked chicken and cooked mutton for Vitality). One food is not enough, and two stacks of the same food do not count. Unlocked at Mk III, in sandwiches and in stew. A legendary effect needs two different foods from each of its two groups, four food slots, so only Mk IV can double one. A doubled effect shows ×2 on its order card.
* **Job in the game.** The reason to invest in a higher hall and to vary the pantry. It rewards cooks who stock a wider range of food.
* **Designer watch-out.** The Mk IV legendary doubling was the hardest rule to explain; the screen now shows the ×2 requirement per recipe in its tooltip (open, locked at Mk III, or locked at Mk IV).

#### Gather, Take back, Clear order
* **What they do.** Gather lays out exactly the foods that make the chosen effects, taking them from the pack (it spends nothing; only cooking does). Take back puts the whole table back in the pack. Clear order empties the choices. Choosing nothing lets the hall pick effects from whatever food is on the table.
* **Job in the game.** The player thinks in effects ("I want Vitality and Fortitude"), not in recipes and stacks.

#### The active meal
* **How it behaves.** A player has one active meal. A new meal replaces the old one. Eaten meals show as ordinary potion effects (value and time), plus a Home Zone effect. A meal lasts as long as the hall that made it allows (15 to 30 field minutes). Logging out pauses it. **Death clears it**, and milk clears it too. Inside the base zone ("home") every value doubles and the timer stops; stepping out resumes it. Returning home never refreshes the time.
* **Job in the game.** Makes the base the best place to fight (the cook's buffs are strongest there) while meals still travel with the player, for a limited time.

#### Bowl Dispenser and Milk Dispenser
* **What they are.** Two utility blocks. The Bowl Dispenser (1 × 1 × 2) gives out bowls: right-click takes one, and crouching with empty hands opens its stock (up to 64 bowls). The Milk Dispenser holds 8,000 mB of milk (buckets add 1,000, bottles 250) and a normal use spends 250 mB, clearing every potion effect and the active meal.
* **How they behave.** Each announces what it did (bowls left, what milk cleared, milk left). Milk is not spent if there is nothing to clear. Milk Bottles carry the same cleanse.
* **Job in the game.** Bowls remove tedium from serving stew; milk is the "reset" for a meal the player no longer wants.

### Part G. The two gun add-on mods

#### Gun Guide
* **What it is.** A client-only card that lists the controls of the gun in the player's hand, with the player's own key bindings.
* **How it behaves.** Draw a gun and the card opens for five seconds, then shrinks to a badge; a key (configurable) toggles it. A warning appears if two actions share a key. A red JAM alert appears when the gun jams (this needs the TaCZ Durability addon).
* **Job in the game.** Removes the biggest barrier to using many different guns: not knowing the controls.

#### Gun Displays
* **What it is.** Eleven decoration blocks for guns: stands, wall racks, glass cases.
* **How it behaves.** Right-click with a gun to display it, again with another gun to swap, with empty hands to take it back. With the Defense Beacon installed, get them from the Weapon Platform's Supplies category. Wall racks need a solid wall. They count like any other placed block for the base score; there is no special rule for them.
* **Job in the game.** A trophy room. Decoration with no gameplay effect.
* **Not played yet.** Whether the owner wants displays to count more toward the base score.

### Part H. Teaching and teamwork

#### Starter kit
A new player receives a Defense Beacon, the Field Guide, the Recovery Shovel and one starter gun with ammo (pack: an FK15P flintlock and 64 round balls; standalone: the weakest installed gun and 64 matching rounds), once, the first time they join.

#### Field Guide
* **What it is.** The in-game book. Fourteen pages with a side menu that shows an item icon per page; a Reference tab holds the exact numbers.
* **How it behaves.** Every feature is a card: what it is, what it gives, how it works, how to unlock it, and red *Careful* notes for catches (the beacon only comes off with the shovel and that resets the campaign). It is written for a player who has just spawned in and is not the design document. The same tone applies to tooltips, confirmations and hints. Tests fail if a card is out of format, a registered block or item has no card, or a number in the guide stops matching the code.
* **Job in the game.** Pillar 5. Writing rules: `docs/GUIDE-STYLE.md`.

#### Playing together
* **What is shared.** The beacon, its upgrades, the raid level cap and the reward chest.
* **What is personal.** Each player's Support Platform, Cannon, fire support choice and active meal.
* **Designer watch-out.** One player's shovel can reset everyone's campaign. Two-human co-op has not been played yet.

#### Operator commands
`/arsenal claim`, `upgrade <branch>`, `repair`; operators also have `boundary`, `test-raid [type]` and `next-raid <type>` to test raid types.

### What to playtest first

1. The tier curve: does a first-week team survive tier 0 and want to raise the cap?
   (Also: run one raid with `-Darsenal.stuckLog=true` on rough terrain and read the `[stuck]` lines; they name the biome, the blocks under and in front of every stuck raider.)
2. Special raid frequency (50 percent from raid 4) and whether the air raid has a counter.
3. Mess Hall levels: do 15 / 20 / 25 / 30 minutes and the Mk III doubling step each feel like a real upgrade?
4. Whether energy income is enough for the cannon purchase and first upgrades within two or three raids.
5. Two players on one server: shared beacon, personal cannons, the shovel reset.

---

## 5. Systems (rulebook with exact numbers)

### 5.1 Defense Beacon and base zone
* One beacon per world; its zone is a glowing grid. Core level sets the radius: **8, 12, 18, 24** blocks (Mk-1 to Mk-4).
* Upgrade branches: **Core, Logistics, Defense, Restoration, Reconnaissance, Vertical.** Standalone upgrade prices in Ardent Energy: **4, 8, 14, 22, 32** by level. Pack prices are factory parts (`Rules.upgradeCost`: 8, 16, 32, 64, 128 of the branch's part). Repair costs 6 (standalone) or 8 (pack).
* Commands for players: `/arsenal claim`, `upgrade <branch>`, `repair`; operators: `boundary`, `test-raid [type]`, `next-raid <type>`.
* **Reward chest:** the beacon is the chest (54-slot menu from the Overview's reward button). No block is placed; overflow stays queued; contents spill out if the beacon is destroyed.
* **Base score** (structure, palette, details, lighting, furnishings, layout, machinery, weapon stations) sets the reward tier (0 to 10).

### 5.2 Raids
* Introduction raid first (always ordinary and gentle), then raids on a sparse night schedule; the player can pick a lower level, call early, or pay to push back.
* Waves: **3 + tier (capped at 5 extra)**. Every third raid is a **boss raid** (the boss arrives in the last wave; 50 percent larger cache, bonus kits and grenades).
* Attackers scale with the number of defenders, spawn outside the zone, target doors first and dig through walls; ranged enemies hold distance.
* **Marching (v3):** raiders walk **straight at the beacon** (no detours around terrain) and only stop to dig when they stop making progress (less than a block in a second). They spawn on **open surface ground at or above the beacon's level** (never in a crevice or below ground; if no such place exists for 30 s the rule relaxes by 4 blocks, after 90 s by 10). Every raider carries a **red exclamation mark** above its head and is **immune to sunlight**.
* **Sunlight protection (v4):** prevents daylight ignition only. Flame arrows, lava and fire traps still deal damage; native fire-immune species retain their own immunity.
* **Raider Gates (0.0.14, replaces the silent rescue of 0.0.13):** `RaidRescue.tick` looks at every ground raider about once a second. Two triggers start a channel. (a) *Look-ahead probe* (`RaidSpawns.lookAhead`): `LOOKAHEAD_BLOCKS` (10) samples, one per block, along the raider's straight line to the beacon, read from the `MOTION_BLOCKING_NO_LEAVES` height map without loading a chunk; it triggers on lava or magma, on a drop of `GATE_DROP_BLOCKS` (6) between neighbouring samples, or on `GATE_FLUID_SPAN` (8) wet samples in a row (a shorter pond is waded). Unloaded columns, tree trunks, player-placed blocks and everything inside the zone are *unknown*: never blocked, and they break a run of water. (b) *Progress timer* (`RaidMarch.stuck`): no `PROGRESS_BLOCKS` (3) gain in the 3D distance to the beacon for `STUCK_SECONDS` (25) while not busy. Busy means: within melee reach of the beacon with line of sight; a ranged raider within `FIRING_DISTANCE` (14) with line of sight; it broke or damaged a block in the last `BREACH_BUSY_SECONDS` (5); a player within `ENGAGED_RADIUS` (12) while something hurt the raider in the last 5 seconds; or its chunk is not entity-ticking. Busy time restarts the 25-second clock. Both triggers wait out the 10-second grace window after a gate. A raider that triggers either one asks `RaidSpawns.findRescue` for a destination and opens a gate (`RaiderGates.open`).
  * *Destination.* On the staging ring at the zone radius plus `CLEARANCE` (64) plus the usual spread (up to 16); every spawn rule (`safe`: outside the zone, entity-ticking chunk, inside the world border, at the beacon's level, open level ground) applies; the whole straight way from there to the zone edge passes the same probe (`RaidSpawns.lineProbe`, one sample every `DESTINATION_SPACING` (2) blocks); at least 24 blocks from every player (checked again when the destination is shown and when the raider appears); another sector than the one the raider was stuck in when any other will do. Never inside the zone.
  * *Channel.* `RaiderGates`: the raider stands next to a `RaiderGate` (1.6 blocks to its side) for `CHANNEL_SECONDS` (8). It cannot move: `ChannelGoal`, priority -1 with MOVE, LOOK and JUMP flags, holds it (it does not use `setNoAi`, which belongs to defeated Special Forces soldiers); a ring of red dust particles shows on it every second (the vanilla particle packet). Every second in which it lost health delays the end by `DAMAGE_DELAY_SECONDS` (3), at most `MAX_DELAY_SECONDS` (10) in total, so a tough raider under long-range fire cannot stall the wave into the raid timeout; death cancels the channel. `TELEGRAPH_SECONDS` (3) before the end the destination shows portal particles and a portal sound. On arrival: navigation stopped, fall distance and motion reset, the tracker reset with the 10-second grace window.
  * *One gate per obstacle.* A raider that gets stuck within `GATE_JOIN_RADIUS` (8) of a live gate uses it; a gate has room for `GATE_CAPACITY` (8) channelers, the rest wait for a free place. The gate (`RaiderGate`, an entity that is never saved, cannot be entered, hit, pushed or targeted, drawn with the Return portal sprite in red at about half its speed so it cannot be taken for a player's Return portal) goes away when no raider is assigned to it, and when a raid starts or ends, the campaign is packed up or the server stops. Nothing is persisted: after a reload the raiders fall back to the progress timer.
  * *Exit gate (0.0.16).* When the destination is shown (3 s before the jump) a second `RaiderGate` opens on the destination block; nobody joins an exit gate. If the destination has to change at the last check, the exit moves with it. It closes `EXIT_LINGER_SECONDS` (2) after the raider stepped out, at once if the channel is cancelled, and when its raider is gone. Not saved, like the gate.
  * *Escalation.* The gate count is kept on the mob (`arsenalGates`, so it survives chunk reloads). After `MAX_GATES` (3) the next trigger withdraws the raider: discarded, removed from the raid, no drop, no Ardent Energy; one chat line per wave ("N stuck raiders withdrew"). If no destination exists the attempt is retried after the grace window and an ordinary raider is withdrawn after `DRY_LIMIT` (3) failed attempts. A raid boss (`arsenalBoss`) gets another gate every time and is never withdrawn (that would fail the raid with "Boss was not defeated"); each is logged.
  * *Not covered, on purpose.* Flyers and gliders (vex, phantom, blaze), paratroopers while their parachute is on, Special Forces soldiers; siege-creeper ignition is unchanged. Raiders still march straight: no detour pathfinding and `BeaconCombat.approach` is unchanged (it only records when its forced-move fallback ran, for the log).
* **Marchable corridors (0.0.13):** the staging ring is cut into 12 world-aligned wedges of 30 degrees (wedge 0 starts due east). Once per raid, the first time a wedge is needed, its centre ray is sampled every 4 blocks from 8 blocks beyond the zone to the farthest staging ring, using the heights of the `MOTION_BLOCKING_NO_LEAVES` map and no chunk loading. A surface of water, lava or magma, a rise of more than 2 blocks or a drop of more than 3 between neighbouring samples is a violation; a sample in an unloaded chunk, or one whose top block is a tree trunk or a player's block, is unknown and never a violation. A spawn at distance D is judged by the samples between D and the zone. `RaidSpawns.find` tries the same rings and 12 candidates per ring as before (random wedge to start, random angle inside it), but candidates in clean wedges come first and then the fewest violations; every candidate is checked at most once. Heuristic: the cached ray is the wedge's centre, a candidate's real line can differ by up to 15 degrees. Diagnostics: `-Darsenal.stuckLog=true` logs every stuck, rescue and withdraw as one `[stuck] event=... key=value` line (mob type, role, short id, wave, position, distance, blocks at feet, below and ahead, fluid, biome, rescue number, destination, and whether `approach()` had to push the mob straight at its goal because it found no path).
* **Trap adaptation (v4):** each class first causing positive trap damage gets one full-damage wave; subsequent waves gain 25/50/75/100% resistance to that class. Eleven classes cover fire, fall, drowning, suffocation, spikes, crushing/cramming, freezing, explosion, ownerless projectile, magic and lightning traps. Announcements mark each step. Learning is saved for the current raid and resets at the next raid. Attributed weapons, turrets and support cannon attacks are excluded; source-erasing addons need integration tags/markers. See `docs/RAID-ADAPTATION.md`.
* **Victory** restores everything the raid broke (damage journal). **Defeat** leaves it.
* Warnings: chat one day ahead, then every six hours; popups for raid start, each wave, VICTORY (confetti) and DEFEAT.

### 5.3 Special raid types (from the fourth raid on, 50 percent chance, also in boss raids)
| Type | What it asks of the base |
| --- | --- |
| **Air raid** | Vexes, phantoms, blazes ignore walls; build anti-air, not thicker walls. Vexes and phantoms are steered straight at the closest defender within 7 blocks, otherwise at the beacon, and hit it on arrival; blazes keep their own ranged attack |
| **Paratroopers** | Heavies at half count drop from 46 blocks up under red parachutes (slow fall, can be shot, no fall damage if not shot); every one of them wears the parachute |
| **Siege** | Ranged-heavy waves; creepers (30 percent) dig into walls, shooters fire through the holes. A creeper only blows up when it is stuck at a wall within 24 blocks of the zone (or reaches the beacon), never where it spawns |
| **They are thousands** | Only zombies and husks, double count, spawning every 8 ticks instead of 20, up to 96 alive; no heavy or ranged |

A won special raid adds **3 + 2 x tier** Ardent Energy. The next raid's type is shown at the end of each raid and in the Overview. Design intent: a type that rewards a counter-build (anti-air, thick walls, killing zones). Special raids start at raid 4 so support is affordable first.

### 5.4 Ardent Energy and the Exchange
* **Drops:** about 1 kill in 15 (1 in 12 standalone) for hostile mobs killed by a player or raid attackers; +1 percent per Looting level; mobs with 60+ health drop two; **bosses always drop one**. No credited player means no drop. Config in `arsenal-beacon-common.toml`: `baseDropChance`, `lootingBonusPerLevel`, `bossDrop`.
* **Raid income:** a guaranteed payout per won raid of **1 + t** (pack) or **2 + 2t** (standalone), plus drops (a tier 0 raid has about 20 attackers, tier 3 about 80), plus the special raid bonus.
* **Exchange Shop** (placed inside the zone; 3D vending booth): **Sell** tab (hand in materials, receive energy) and **Buy** tab (pay energy, receive goods). Buying always costs more than selling so loops never pay (a unit test checks the defaults). Offers live in `config/arsenal-beacon-exchange.txt` (`[sell]`, `[buy]`).
* Late-game factory players sell surplus for ammunition; players without a factory farm energy and buy parts at a premium.

### 5.5 Support system (per player)
Each player owns **one Support Platform** and **one Support Cannon**, placed anywhere inside the beacon zone, bought at the beacon’s Workshop Fabrication tab; relocated for free with the Beacon Recovery Shovel (keeps upgrades and contents). Only the owner can use them. They stop working if the beacon is gone.
* **Support Platform** Mk-1 to Mk-4 (grids 3x3 to 6x6; upgrade plating 16, 32, 64). Two blocks tall, owner sign on a post above it (render-only, no hitbox). The two blocks in front are a **no-build return zone** ("KEEP RETURN ZONE CLEAR").
* **Support Cannon** (3x3, heavy; brass owner plate). It turns with a looping turning sound, locks on and fires when the lock sound ends (0.0.18).
* **Flares** (throwing is free; buying costs energy): **Supply** 3, **Return** 2, **Fire Support** 6; cannon purchase 12.
  * *Supply Flare:* the cannon fires and a supply chest parachutes in with the platform's contents (12 s outside, 7 s underground, counted from the shot).
  * *Return Flare:* a portal (30 s; 5 min and usable twice with the upgrade). The player arrives at the front cell, a nearby safe cell, or the return is cancelled and the flare not consumed.
  * *Fire Support Flare:* a red box marks the target; the cannon shells it. A second flare mid-barrage waits ("Barrage in process").
* **Twelve fire support types** (the owner's choice applies even to a borrowed flare; `CannonUpgrades.FireType`, saved and sent by ordinal, so new types are only ever appended): Explosion Barrage (6 shells), Arrow Cluster (6 since 0.0.18, was 8: tipped arrows, see below), Narukami's Favor (lightning, 5), Bunker Buster (one bomb digging an 8-block-half-width cube; hurts players for half their health; refuses inside the zone; spares obsidian, crying obsidian, bedrock and anything with resistance 1,200 or more), Healing Barrage, Curse of Debilitation, and (0.0.16, reworked in 0.0.18; damage is a share of the 25-damage shell, times the Damage upgrade):
  * *Cluster Strike* (one round; Volley does nothing): the shell bursts 6 blocks over the flare, then for 12 s bomblets go off all over a box 1.75 times as wide (one every 3 ticks, each with the owner's cluster sound, quieter than a shell); every 10 ticks every hostile mob in the box takes 5 (120 over the strike, 80 percent of what the six shells of a barrage deal).
  * *Napalm Carpet* (one round; +4 s per Volley level): for 10 s a flat box (2 blocks high, 1.2 times as wide) burns. Real fire is put on every free spot of its ground (relit each second, removed when it ends; never within 4 blocks of the beacon zone); once a second every hostile mob in the box takes 5 and burns for 4 s.
  * *Cryo Shell* (one round; +5 s per Volley level): for 15 s snow layers cover the same flat box (removed when it ends); hostile mobs in it get Slowness V and Mining Fatigue III (a raid boss Slowness III). No damage.
  * *Gravity Well* (one round; +2 s per Volley level): for 5 s hostile mobs in a box twice as wide (8 blocks each way) are pulled toward the flare (0.16 blocks per tick, never past the centre; less with knockback resistance, a raid boss 35 percent). A vortex sprite as wide as the box turns on the ground. No damage.
  * *Shockwave* (4 shells): its box is as wide as a level 1 beacon zone (8 blocks each way, +1.5 per Area of effect level); for 16 ticks after each shell every hostile mob inside is shoved outward (0.5 to 1.6 blocks per tick) until it is past the edge, whatever its knockback resistance, a raid boss too. No damage.
  * *Starshell* (one round): bursts 14 blocks over the flare and burns 30 s while sinking; hidden light blocks (level 15, every 4 blocks, 2 above the ground) light its whole cube and are removed when it goes out; every 2 s hostile mobs in it glow for 5 s and players in it get Haste II. No damage.
  * *Arrow Cluster* (0.0.18): every arrow is a tipped arrow. The three aimed at each mob carry harming, poison, slowness or weakness, or for an undead mob healing (which hurts it), slowness or weakness; the stray ones slowness or weakness.
  * Blocks put in the world (fire, snow, light) are listed on the flare (saved with it) and removed when the effect ends or the flare is destroyed, only where they are still there.
* **Rounds and timing (0.0.18):** the cannon fires `SupportCannon.DONE_TICKS` (18, 0.9 s) after the turret settles: the length of the lock sound. Every round then takes `BLAST_FLIGHT_TICKS` (103, 5.15 s; 30 in 0.0.16, 10 before) from the shot to the impact: the moment the incoming sound (played at the impact point when the round appears) hits. The round falls from 80 blocks, faster and faster. The flare still resolves all damage and effects itself.
* **Quantum tunneling (0.0.18):** needed only when 3 or more solid blocks stand in the column above the flare (`SupportRules.COVER_BLOCKS`; leaves do not count). An open pit is never "underground", nor is a spot under a thin roof. Before, any spot the sky could not fully light counted.
* **Turret speed (0.0.18):** `CannonUpgrades.turnSpeed(level)` = 0.617^(1 - level/3): with no Faster traverse a half turn takes 147 ticks (1.5 times the old 98); at level 3 exactly the old unupgraded 98.
* **Ten cannon upgrades:** Traverse, Fire Rate, Volley, Damage, Quantum Tunneling (works underground), Slowness field, lasting two-way Portal, Healing Aura, Area of Effect, Dimensional Link. Standalone energy costs total about 185; the pack pays plating, coils, logistics modules and restoration matrices per `Economy.PACK_AMOUNTS`.

### 5.6 Weapon Platform
A workshop with four stations: **Weapons** (search, filter by type and era, craft), **Ammo** (matching ammo and magazines; pay in materials or Universal Ammo Coins), **Attachments** (compatible with the held gun, locked count), **Armor**. Five eras take the player from frontier rifles to futuristic hardware; modern pistols are a modern unlock. The beacon’s Workshop Fabrication tab sells all starter installations; each table upgrades itself.

### 5.7 Field guide and onboarding
New players get a starter weapon with ammo, the field guide and the beacon tools. The guide is written for somebody who has just spawned in and is not the design document: fourteen pages with a side menu that shows an item icon per page, each a set of feature cards (*What it is, You get, How it works, How to unlock*, then red *Careful* notes for catches such as the beacon only coming off with the shovel and resetting the campaign). Pack and standalone differ by `[pack]` / `[standalone]` lines inside a card or by `key.pack` / `key.standalone` suffixes. The **Reference** tab holds the exact numbers. Tests fail if a card is out of format, a registered block or item has no card, or the guide quotes a number that no longer matches the code. Writing rules: `docs/GUIDE-STYLE.md`.

### 5.8 Mess Hall and prepared meals
**A fortress should have a kitchen feeding the war effort.** A farmer/cook supports the group with portable expedition rations and communal pre-raid meals. Casual cooking is useful; a dedicated cook supports more people without being mandatory.

* **Mess Hall Mk I–IV:** a rotating **2 wide × 1 deep × 2 tall** installation (painted models from the artist package), entirely inside the existing beacon zone, with up to six shared persistent mixing slots (three open at Mk I to III, all six at Mk IV), a physical sandwich output and a bread-only base slot.
* **What each level adds (0.0.11):** Mk I: sandwiches only, meals last **15** minutes, **3** food slots. Mk II: unlocks **stew**, **20** minutes. Mk III: **25** minutes, unlocks **×2 doubling**. Mk IV: **30** minutes, **6** food slots. Pots (1/2/3/4) and stew batch units (4/7/10/12) are unchanged; stew servings per pot are 3/5/7/9 since 0.0.14 (they were 4/8/12/16). The screen shows the current level's meal length, food slots and ×2 status as three chips, and the Upgrade button's tooltip says what the next level adds. Each tier has distinct functional model resources: improvised wooden spit, copper field station, stone fortified ovens, enclosed iron/green canteen. Initial craft uses vanilla materials in both editions; paid in-place upgrades preserve food and links.
* **Cook Pot:** **1 × 1 × 2**, hollow pot with a conditional stew surface and, on a chalkboard in its upper block, a live menu board displaying stew, up to three bonuses and remaining servings. State, strengths, remaining servings and hall identity persist in its block entity.
* **Two preparation tabs:** Sandwich produces one portable item with **1–2 effects**, stored in NBT, stacking to 16 and edible normally at full hunger. Stew holds **up to 3 effects**, assigned to one selected empty linked pot. Sandwich consumes one separate base bread plus two or three distinct fillings; base bread contributes no Grain or effect. Stew consumes **4/7/10/12 ingredient units** at Mk I/II/III/IV, spread across stacked inputs, including one of every composition type. A hall holds as many kinds of food as it has open food slots (3, or 6 at Mk IV). Container remainders return for every consumed item. Failed preparation consumes nothing, and a pot that still has stew is only replaced after the cook confirms it.
* **Bowl serving:** interact with a filled pot while holding a normal bowl to eat immediately. The server removes one serving; the bowl remains empty and reusable. Sandwich nutrition is 8 (saturation modifier 0.6); stew nutrition is 10 (0.8).
* **Tier hooks:** Mk I/II/III/IV link **1/2/3/4 pots** and produce **3/5/7/9 stew servings** (lowered from 4/8/12/16 in 0.0.14). All tiers retain the same effect-slot limits. Next upgrades cost **8/16/32 Reinforced Plating** in the pack or **4/8/14 Ardent Energy** standalone (`Economy`). Creative upgrades are free.
* **Composition:** six slots count distinct item types once; at most two sandwich or three stew effects. Vanilla ingredients give only ordinary Minecraft bonuses. Ordinary is the default. Players click recipe icons on the kitchen screen to choose effects; **Gather ingredients** then fetches the foods from the pack and **Take back** returns them. The order and mode are stored with the hall. Fifteen selected legendary recipes cover pairs of six food groups and replace those groups’ ordinary bonuses; selected recipes cannot overlap groups. Every ordinary effect has a vanilla ×2 pair, allowing three selected ordinary effects at Mk II or III (one food each) and all of them doubled at Mk IV (six slots). Weights rank ordinary effects without extra strength tiers. **Doubling unlocks at Mk III, in sandwiches and in stew**: two distinct foods in one group contributing the same ordinary effect. A legendary effect needs two from EACH of its groups (four slots), so only Mk IV can double one. Split stacks never qualify. Exact matrix: `MESS-HALL-V6-IMPLEMENTATION.md`.
* **Field bonuses:** ordinary health +4, armor +2, knockback resistance +5%, movement +4%, melee damage/speed +8%, luck +1, healing 1 health/5 s, fire/explosion protection 10%, fall protection 12%, swim +10%. Legendary Firepower +8%, Quick Hands +10%, Brawler +20%, Heavy Hand +15%, Demolition +10%; new Recoil Control −10%, Focus +10% accuracy, Snap Aim +10%, Fast Draw +10%, Dead Eye +8%, Gun Mobility +8%, Impact +0.25 knockback, Rapid Fire +5%, Scoped/Hip Focus +15%. Backend TaCZ Attributes 1.4 remains required. Owned transient UUID modifiers preserve other mods; recoil uses a negative multiplier, Impact adds to neutral zero, Focus updates aimed and hip-fire accuracy. Native bash/radius/reload adapters retain v4 behavior.
* **Home enhancement:** `BaseZone` determines valid home territory. Meal strength is **2× at home**, and remaining field duration **freezes**. Outside, strength normalizes and the timer resumes. Returning home freezes the existing remainder and never refreshes it. New meals replace the old state and start the length of the hall that made them: **15 / 20 / 25 / 30 minutes** (18,000 / 24,000 / 30,000 / 36,000 field ticks) at Mk I / II / III / IV. The length is saved on the sandwich, the stew and the active meal.
* **Lifecycle:** server-owned UUID-keyed SavedData persists composition and exact ticks. Logout/restart pause time. Dimension changes preserve the meal; other dimensions count as field. Non-death respawn retains it. **Death clears meals and their owned modifiers.** A meal is shown with ordinary vanilla potion effects (no custom HUD): one effect per bonus with its current value and time in the inventory list, endless while at home, plus a **Home Zone** effect ("your food buffs double and don't decay"). The kitchen shows available/required food units and per-slot consumption tooltips; preview tooltips list field/home effects.
* **Linking:** nearby anchors link within **8 blocks in 3D**, respecting tier capacity. Existing valid links are not stolen. Hall UUID + persisted pot reservations prevent same-position replacement and chunk-unload reassignment. Unloaded pots retain reserved capacity. Broken anchors clean loaded links; unloaded partners validate later. Orphaned stew remains stored until the pot reconnects. No general logistics system exists.

Balance is an initial estimate. Extended co-op balance and full-pack integration remain playtest work. Exact extension format, lifecycle and ownership rules: `docs/MESS-HALL.md`.

## 6. Economy and pacing targets

Goals the numbers are tuned against:
* A won raid by a reasonably efficient team replenishes ammunition and moves progress.
* Basic support (cannon 12, supply 3, return 2, fire 6) is affordable after the first two or three raids, before special raids appear.
* The first upgrades arrive in about one raid each (cannon 3 to 5 energy; beacon 4).
* A poor raid is recoverable through ordinary play (night mobs, farms, Exchange sell-offs; cheap repairs).
* Factory and target farming both work; **factory is clearly faster**.
* Rough income: standalone tier 3 raid is about 8 + drops of 80/12 + bonus; the whole cannon is about 25 average raids of standalone income. **(unverified: all numbers are design estimates; no long playtest yet.)**

Tuning knobs: `Economy`, `CannonUpgrades`, `SupportRules`, `RaidRewards`, `StandaloneBalance`, `ArdentEnergy`/`ArsenalConfig`, `RaidTypes` (`CHANCE_PERCENT`, `FIRST_SPECIAL_RAID`, count factors), `RaidMarch` (`PROGRESS_BLOCKS`, `STUCK_SECONDS`, `GRACE_SECONDS`, `BREACH_BUSY_SECONDS`, `MAX_GATES`, `DRY_LIMIT`, `FIRING_DISTANCE`, `ENGAGED_RADIUS`, `LOOKAHEAD_BLOCKS`, `GATE_DROP_BLOCKS`, `GATE_FLUID_SPAN`, `DESTINATION_SPACING`, `CHANNEL_SECONDS`, `DAMAGE_DELAY_SECONDS`, `MAX_DELAY_SECONDS`, `TELEGRAPH_SECONDS`, `GATE_JOIN_RADIUS`, `GATE_CAPACITY`), `RaidSpawns` (`WEDGES`, `PROBE_STEP`, `PROBE_FROM`, `MAX_STEP`, `MAX_DROP`, `RESCUE_TRIES`, `RESCUE_PLAYER_DISTANCE`).

## 7. UI and audio
* Shared UI kit (`Ui`): consistent panels, tabs, rows, cards, pips; icon-first with plain-language requirements.
* Meals: vanilla effect icons and inventory values; the kitchen is 376 × 238 in two columns (it needs a GUI at least 376 wide, which every 16:9 window has; 4:3 screens should lower the GUI scale). Left: the mode tabs, the table, one primary button that reads Gather ingredients, Make sandwich or Cook stew, Take back, Clear order and Upgrade. Right: a status line with the next step, the 26 effect tiles (hover for values, feeding foods, ×2 rules and pack status), the order cards and three chips for meal length, food slots and ×2. Server messages appear as a four-second toast. One Forge ingredient tooltip, no overlapping second tooltip. Stew hides and locks sandwich output. Animated colored links and shared buff icons connect the server-selected legendary ingredient slots; hover names both food groups and the resulting buff. Doubled legendary recipes link all four distinct contributors. No effect-strength bars; a doubled effect shows ×2. Home doubling remains independent and is labeled in the tooltip.
* HUD: banner above the hotbar showing the chosen fire support while a Fire Support Flare is held; toasts for support calls.
* Zone: glowing grid and a red target box drawn as edges only (so nothing behind it is hidden). The outline's stale limit is 30 s (the cause of an earlier flicker bug is unproven).
* Raid music (0.0.17, replaces the 0.0.16 rule): pools in `RaidPlaylist`: *normal* (the owner's Barren Gap, Line Holder, Phase One Assault, Silent Trigger, and Hold the Line), *special* (Anomaly Protocol, Strange Signals), *boss* (Boss Battle, Iron Tyrant). A raid wave plays from the pool of its kind; the last wave of a hard raid (the boss wave) plays from *boss*. Each wave picks a song with a random order seeded by the beacon position and the raid number (`RaidPlaylist.seed`, `pick`): every player of the base hears the same song, and a wave never repeats the song of the wave before. The song loops natively (no gap) until the wave changes: the old song fades out over 3 s while the new one fades in over 1.5 s. It plays for every player in the Overworld whose beacon state is fresh, near the beacon or not. The game's music manager is held while a raid song or the fanfare plays and for 30 s after (`mixin.RaidMusicMixin` on `MusicManager.tick`). Winning stops the song (0.5 s fade) and plays the victory fanfare for everyone who sees the VICTORY banner; defeat fades the song out. All follow the Music volume slider; the files are normalised to -16 LUFS. Per-player switches in the beacon's Settings tab (client config `config/arsenal-beacon-client.toml`): *Raid music* (off: no raid songs and no fanfare, the game's music plays on) and *Music player* (the strip under the status card: song name, level meter, time, progress bar; shown for the whole raid while a song plays, below where the status card is even when the card is hidden). Settings tab layout: the base switches on the left, the music switches on the right.
* Sound effects (0.0.18): the owner's hand-tuned sounds (`tools/audio/import_sfx.py`, mono where they are placed in the world): the cannon's turning loop (while the turret moves), its lock clunk (the shot follows the moment it ends) and its shot; the incoming round (played at the impact point and always at its own pitch, so it ends as the round lands, the Bunker Buster's bomb included); the Bunker Buster's blast; the cluster bomblets (one every 3 ticks; the file itself is 6 dB quieter, because Minecraft caps a sound's loudness at volume 1 and a higher volume only makes it carry farther); the shockwave; and the beacon attack alarm (with the red warning, again every 8 s while it stays up, heard anywhere). Every other explosion (barrage shells, the Bunker Buster's digging, the cluster burst, the gravity well closing) is the game's own explosion sound. Flares, napalm, frost, the gravity hum and the starshell keep the 0.0.16 sounds. Chat lines "Cannon preparing..." and "You heard cannon fire roaring" for far players. Every sound has a subtitle. Sources: `tools/audio/README.md`.
* Cannon menu (0.0.16): the fire support list scrolls (mouse wheel over the list) and keeps the chosen type in view; upgrades with three levels (Area of effect included) are listed first; a maxed upgrade can still be hovered to read what it does.
* Art: hand-made 3D models (shop, platform Mk-1 to Mk-4, cannon, parcel) and painted 64x64 sprites; red parachute canopy drawn procedurally.

## 8. Technical notes for designers
* Network protocol **28** (raised from 26 by the 0.0.8 kitchen preview rework, 27, and the 0.0.11 open-food-slots menu change, 28); bump on any packet or menu-behaviour change. Message 14 carries raid marks, exclamation marks and parachutes; the kitchen preview is its own message; the v2 meal HUD message 15 was removed.
* Server owns all rules; clients render. Campaign state in `CampaignData`; per-player support state in `SupportData`; prepared meals in `PlayerMeals` SavedData, kitchen inventories/links and communal servings in block entities.
* Raid persistent flags are not synced automatically, so marks (red exclamation mark, red parachute) use their own idempotent packet sent every second. Meal MobEffects and backend attributes use native synchronization; v4 adds no packet.
* Operator test commands: `/arsenal test-raid <type>`, `/arsenal next-raid <type>`.
* Opt-in switches (system properties, all off by default): `-Darsenal.stuckLog=true` (gate diagnostics: every trigger, channel, interruption, teleport and withdrawal as one `[stuck]` line), `-Darsenal.stuckTests=true` (registers the raider-gate GameTests and `/stuck-raider-test`), `-Darsenal.optimizeTests=true` (registers the Optimize Path GameTests and `/optimize-path-test`, 0.0.15; they borrow the campaign of a world with no beacon, so use a throwaway world).
* V4 builds and standalone runtime checks passed: 70 beacon unit tests (7 guide tests remain Gradle up-to-date), 20 kitchen/gun/raid/effect server cases, optional LesRaisins radius/ownership and a real save/restart. Exact artifacts, client status and remaining playtests: `docs/MESS-HALL-V4-TESTING.md` and validation JSON.

* Until final **1.0**, the owner starts a fresh world with each feature; old kitchen footprint migration is outside current scope.

## 9. Known risks and open questions
1. Zone outline flicker during cannon fire: fix is a best guess, unconfirmed.
2. Ground spawn for Air, Siege and Swarm raids not exercised in a real world.
3. Two-human co-op, the End and modded dimensions untested; pack prices untested with real Create/KubeJS items.
4. Balance is theoretical until a team plays several raids.
5. V4 runtime coverage and remaining client/custom gunpack scenarios are listed in `docs/MESS-HALL-V4-TESTING.md`; broad native-path support is not empirical certification for every custom script/addon.
6. Stuck-raider thresholds (25 seconds, 3 blocks, 8-second channel, 3 gates), the probe limits (drop 6, 8 blocks of water) and the spawn-corridor limits (rise 2, drop 3) are estimates; the spawn corridor judges a wedge's centre ray, not each spawn's exact line; a wide shallow lake that a raider could wade is still a gate. **(unverified on real terrain; use the diagnostics log.)**
7. Special-raid fairness: is a 50 percent chance too frequent? Does Air raid need a stronger anti-air toolkit than the player has? **(TBD, needs playtest data.)**

## 10. Roadmap candidates (not committed) **(TBD)**
* More anti-air tools for the air raid counter-build.
* Raid-type specific rewards or cosmetics.
* Music and raid audio.
* Difficulty presets for solo play.
* Balance pass from real playtest logs.

## Fabrication, compatibility and recovery

Workshop Fabrication is the third beacon tab (Overview → Upgrades → Workshop → Raid break); Settings moves beside Guide in the header. Compact cards show all eleven installations and material icons/counts on one page. It sells the Weapon, Ammo, Attachment and Armor Platforms, Mess Hall Mk I, Cook Pot, Bowl Dispenser, Milk Dispenser, Support Platform, Support Cannon and Exchange Shop. Prices are centralized in `Economy`, with atomic payment/output-space validation. The weapon table no longer exposes remote purchase/upgrade actions. Each of the four tables upgrades at its own interface and shows all four nearby Age badges; the current badge has a larger cyan frame. Upgrade hover costs show the material icon beside its name.

The Bowl Dispenser occupies **1 wide × 1 deep × 2 tall** inside the beacon zone. Right-click consumes one stored bowl; empty-both-hands crouch-right-click opens one inventory slot accepting at most 64 bowls. Upper-half interaction forwards to the anchor; breaking either half drops stock once. Stock persists in the block entity. There is no unlimited bowl creation.

The recovery shovel cancels attack damage before normal combat and applies intrinsic **Knockback II** (native strength 1.0 before target resistance). Might, Strength or other damage bonuses cannot turn it into a damaging weapon. Creative contents list starter installations by category, with one item named **Mess Hall**. Upgraded hall blocks remain accessible by upgrades/commands.

All Universal crafting paths reject TaCZ **FUEL** reload guns, including Apocalypse **bf1:ef46** and **bf1:wex**. These require Field Radio refills and use custom inspect/animation handling. The reported crash was not reproduced with the owner’s exact addon set; exclusion is the authorized fallback. Normal magazine/single-round reload weapons remain available. No generic catch around gun combat hides unrelated crashes.

## V6 kitchen additions

The Milk Dispenser uses Forge milk in an 8,000 mB tank with a fill bar and two inventory slots: filled containers and returned empties. Full buckets add 1,000 mB; Milk Bottles add 250 mB. Full tanks or blocked output reject the entire transfer. Normal right-click consumes 250 mB and clears every effect, prepared-meal SavedData/timer and owned native/gun modifiers; unrelated attribute owners survive. Empty-hand crouch interaction opens stock on both dispensers. Milk is available to Forge fluid pipes. Craft one milk bucket + four glass bottles into four drinkable Milk Bottles, returning the bucket. Breaking loses liquid and drops inventory. Fabrication price is 8 iron + 4 glass + 4 copper in both editions.

New owner-supplied shovel, sandwich and dispenser models are preserved by resource generation. Separate base bread has a gray ghost icon and cannot create Grain; manual bread in mixing slots can. Apple gains Fortune and melon gains Springy Step. The recipe tooltips and Field Guide explain all ordinary ingredient pairs, selected legendary recipes, limits, Mk IV ×2 and independent HOME ×2. Detailed current rules: `MESS-HALL-V6-IMPLEMENTATION.md`; actual checks: `MESS-HALL-V6-TESTING.md`.

## V6 UI pass (project 0.0.8)

The kitchen is one screen (`MESS-HALL-V7-UI.md`): choose recipes with one click each, press Gather, press Cook. Dispensers announce what they did (bowls left, what the milk cleared and how much milk remains) and the Milk Dispenser never spends a dose when the player has no effects. The Dispenser, Workshop Fabrication and weapon-table Upgrade screens follow the shared UI kit. Rules and numbers are unchanged.
