# Eternal Defense: Game Design Document

| | |
| --- | --- |
| **Title** | Eternal Defense |
| **Document / project version** | 0.0.10 (see `VERSION`; 0.0.x per update on `dev`, 0.1.0 when the owner calls it, 1.0.0 on merge to `main`) |
| **Platform** | Minecraft 1.20.1, Forge 47.x, Java 17 |
| **Status** | Mess Hall v6 feature test build on `feature/messhall-ver-6`, based on v5 `5eebf94`; fresh worlds per feature until 1.0; validation scope in MESS-HALL-V6-TESTING.md |

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

## 5. Systems

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
* **Support Cannon** (3x3, heavy; brass owner plate). It turns with a grinding sound and only fires when settled on the target.
* **Flares** (throwing is free; buying costs energy): **Supply** 3, **Return** 2, **Fire Support** 6; cannon purchase 12.
  * *Supply Flare:* the cannon fires and a supply chest parachutes in with the platform's contents (12 s outside, 7 s underground, counted from the shot).
  * *Return Flare:* a portal (30 s; 5 min and usable twice with the upgrade). The player arrives at the front cell, a nearby safe cell, or the return is cancelled and the flare not consumed.
  * *Fire Support Flare:* a red box marks the target; the cannon shells it. A second flare mid-barrage waits ("Barrage in process").
* **Six fire support types** (the owner's choice applies even to a borrowed flare): Explosion Barrage (6 shells), Arrow Cluster (8), Narukami's Favor (lightning, 5), Bunker Buster (one bomb digging an 8-block-half-width cube; hurts players for half their health; refuses inside the zone; spares obsidian, crying obsidian, bedrock and anything with resistance 1,200 or more), Healing Barrage, Curse of Debilitation.
* **Ten cannon upgrades:** Traverse, Fire Rate, Volley, Damage, Quantum Tunneling (works underground), Slowness field, lasting two-way Portal, Healing Aura, Area of Effect, Dimensional Link. Standalone energy costs total about 185; the pack pays plating, coils, logistics modules and restoration matrices per `Economy.PACK_AMOUNTS`.

### 5.6 Weapon Platform
A workshop with four stations: **Weapons** (search, filter by type and era, craft), **Ammo** (matching ammo and magazines; pay in materials or Universal Ammo Coins), **Attachments** (compatible with the held gun, locked count), **Armor**. Five eras take the player from frontier rifles to futuristic hardware; modern pistols are a modern unlock. The beacon’s Workshop Fabrication tab sells all starter installations; each table upgrades itself.

### 5.7 Field guide and onboarding
New players get a starter weapon with ammo, the field guide and the beacon tools. The guide is written for somebody who has just spawned in and is not the design document: fourteen pages with a side menu that shows an item icon per page, each a set of feature cards (*What it is, You get, How it works, How to unlock*, then red *Careful* notes for catches such as the beacon only coming off with the shovel and resetting the campaign). Pack and standalone differ by `[pack]` / `[standalone]` lines inside a card or by `key.pack` / `key.standalone` suffixes. The **Reference** tab holds the exact numbers. Tests fail if a card is out of format, a registered block or item has no card, or the guide quotes a number that no longer matches the code. Writing rules: `docs/GUIDE-STYLE.md`.

### 5.8 Mess Hall and prepared meals
**A fortress should have a kitchen feeding the war effort.** A farmer/cook supports the group with portable expedition rations and communal pre-raid meals. Casual cooking is useful; a dedicated cook supports more people without being mandatory.

* **Mess Hall Mk I–IV:** a rotating **2 wide × 1 deep × 2 tall** installation (painted models from the artist package), entirely inside the existing beacon zone, with six shared persistent mixing slots, a physical sandwich output and a bread-only base slot. Each tier has distinct functional model resources: improvised wooden spit, copper field station, stone fortified ovens, enclosed iron/green canteen. Initial craft uses vanilla materials in both editions; paid in-place upgrades preserve food and links.
* **Cook Pot:** **1 × 1 × 2**, hollow pot with a conditional stew surface and, on a chalkboard in its upper block, a live menu board displaying stew, up to three bonuses and remaining servings. State, strengths, remaining servings and hall identity persist in its block entity.
* **Two preparation tabs:** Sandwich produces one portable item with **1–2 effects**, stored in NBT, stacking to 16 and edible normally at full hunger. Stew holds **up to 3 effects**, assigned to one selected empty linked pot. Sandwich consumes one separate base bread plus two or three distinct fillings; base bread contributes no Grain or effect. Stew consumes **4/7/10/12 ingredient units** at Mk I/II/III/IV, spread across stacked inputs, including one of every composition type. Mk I supports up to four ingredient types; later tiers up to six. Container remainders return for every consumed item. Failed preparation consumes nothing, and a full pot cannot be overwritten.
* **Bowl serving:** interact with a filled pot while holding a normal bowl to eat immediately. The server removes one serving; the bowl remains empty and reusable. Sandwich nutrition is 8 (saturation modifier 0.6); stew nutrition is 10 (0.8).
* **Tier hooks:** Mk I/II/III/IV link **1/2/3/4 pots** and produce **4/8/12/16 stew servings**. All tiers retain the same effect-slot limits. Higher tiers improve servings per ingredient without multiplying a tiny batch. Next upgrades cost **8/16/32 Reinforced Plating** in the pack or **4/8/14 Ardent Energy** standalone (`Economy`). Creative upgrades are free.
* **Composition:** six slots count distinct item types once; at most two sandwich or three stew effects. Vanilla ingredients give only ordinary Minecraft bonuses. Ordinary is the default. Players click recipe icons on the kitchen screen to choose effects; **Gather ingredients** then fetches the foods from the pack and **Take back** returns them. The order and mode are stored with the hall. Fifteen selected legendary recipes cover pairs of six food groups and replace those groups’ ordinary bonuses; selected recipes cannot overlap groups. Every ordinary effect has a vanilla ×2 pair, allowing any three selected ordinary effects in six slots. Weights rank ordinary effects without extra strength tiers. **Doubling unlocks ONLY at Mk IV stew**: two distinct foods in one group contributing the same ordinary effect, or two from EACH legendary group (four slots). Split stacks never qualify. Exact matrix: `MESS-HALL-V6-IMPLEMENTATION.md`.
* **Field bonuses:** ordinary health +4, armor +2, knockback resistance +5%, movement +4%, melee damage/speed +8%, luck +1, healing 1 health/5 s, fire/explosion protection 10%, fall protection 12%, swim +10%. Legendary Firepower +8%, Quick Hands +10%, Brawler +20%, Heavy Hand +15%, Demolition +10%; new Recoil Control −10%, Focus +10% accuracy, Snap Aim +10%, Fast Draw +10%, Dead Eye +8%, Gun Mobility +8%, Impact +0.25 knockback, Rapid Fire +5%, Scoped/Hip Focus +15%. Backend TaCZ Attributes 1.4 remains required. Owned transient UUID modifiers preserve other mods; recoil uses a negative multiplier, Impact adds to neutral zero, Focus updates aimed and hip-fire accuracy. Native bash/radius/reload adapters retain v4 behavior.
* **Home enhancement:** `BaseZone` determines valid home territory. Meal strength is **2× at home**, and remaining field duration **freezes**. Outside, strength normalizes and the timer resumes. Returning home freezes the existing remainder and never refreshes it. New meals replace the old state and start **30 minutes / 36,000 field ticks**.
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

Tuning knobs: `Economy`, `CannonUpgrades`, `SupportRules`, `RaidRewards`, `StandaloneBalance`, `ArdentEnergy`/`ArsenalConfig`, `RaidTypes` (`CHANCE_PERCENT`, `FIRST_SPECIAL_RAID`, count factors).

## 7. UI and audio
* Shared UI kit (`Ui`): consistent panels, tabs, rows, cards, pips; icon-first with plain-language requirements.
* Meals: vanilla effect icons and inventory values; kitchen height 238 fits the minimum 240-pixel GUI and its 316 px width the minimum 320 px. One screen: recipe palette (26 icon tiles; hover for values, feeding foods and pack status), slot row, order cards, one primary button that reads Gather ingredients, Make sandwich or Cook stew, and a one-line coach under the slots. Server messages appear as a four-second toast. One Forge ingredient tooltip, no overlapping second tooltip. Stew hides and locks sandwich output. Animated colored links and shared buff icons connect the server-selected legendary ingredient slots; hover names both food groups and the resulting buff. Doubled legendary recipes link all four distinct contributors. No effect-strength bars below Mk IV; Mk IV stew shows exactly two bars for single/doubled category strength. Home doubling remains independent and is labeled in the tooltip.
* HUD: banner above the hotbar showing the chosen fire support while a Fire Support Flare is held; toasts for support calls.
* Zone: glowing grid and a red target box drawn as edges only (so nothing behind it is hidden). The outline's stale limit is 30 s (the cause of an earlier flicker bug is unproven).
* Audio: cannon grinding on turning; chat lines "Cannon preparing..." and "You heard cannon fire roaring" for far players. **(TBD: dedicated music and raid stingers.)**
* Art: hand-made 3D models (shop, platform Mk-1 to Mk-4, cannon, parcel) and painted 64x64 sprites; red parachute canopy drawn procedurally.

## 8. Technical notes for designers
* Network protocol **26** (message 14: raid marks, exclamation marks and parachutes; 16: open kitchen preview; the v2 meal HUD message 15 was removed); bump on any packet change.
* Server owns all rules; clients render. Campaign state in `CampaignData`; per-player support state in `SupportData`; prepared meals in `PlayerMeals` SavedData, kitchen inventories/links and communal servings in block entities.
* Raid persistent flags are not synced automatically, so marks (red exclamation mark, red parachute) use their own idempotent packet sent every second. Meal MobEffects and backend attributes use native synchronization; v4 adds no packet.
* Operator test commands: `/arsenal test-raid <type>`, `/arsenal next-raid <type>`.
* V4 builds and standalone runtime checks passed: 70 beacon unit tests (7 guide tests remain Gradle up-to-date), 20 kitchen/gun/raid/effect server cases, optional LesRaisins radius/ownership and a real save/restart. Exact artifacts, client status and remaining playtests: `docs/MESS-HALL-V4-TESTING.md` and validation JSON.

* Until final **1.0**, the owner starts a fresh world with each feature; old kitchen footprint migration is outside current scope.

## 9. Known risks and open questions
1. Zone outline flicker during cannon fire: fix is a best guess, unconfirmed.
2. Ground spawn for Air, Siege and Swarm raids not exercised in a real world.
3. Two-human co-op, the End and modded dimensions untested; pack prices untested with real Create/KubeJS items.
4. Balance is theoretical until a team plays several raids.
5. V4 runtime coverage and remaining client/custom gunpack scenarios are listed in `docs/MESS-HALL-V4-TESTING.md`; broad native-path support is not empirical certification for every custom script/addon.
6. Special-raid fairness: is a 50 percent chance too frequent? Does Air raid need a stronger anti-air toolkit than the player has? **(TBD, needs playtest data.)**

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
