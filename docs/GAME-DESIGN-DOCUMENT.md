# Eternal Defense: Game Design Document

| | |
| --- | --- |
| **Title** | Eternal Defense |
| **Document version** | 0.0.1 |
| **Platform** | Minecraft 1.20.1, Forge 47.x, Java 17 |
| **Status** | Living document, written from the state of the code at the end of round 5 |

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
| 0.0.1 | 2026-10-07 | Claude | First version, covering rounds 1 to 5 |

---

## 1. Vision

Eternal Defense turns a TaCZ gun-pack world into a **shared base-defense campaign**. Players plant one Defense Beacon, build a base worth defending around it, and survive raids that grow with the base and the team.
Industry (Create) feeds the war effort; every player also owns a personal support gun that answers their call. The fantasy: *you hold the line, the factory keeps you supplied, and the cannon behind you is yours.*

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
| Needs | TaCZ, Create, KubeJS and the pack recipes | TaCZ only |
| Upgrade currency | The mod's own factory parts (reinforced plating, logistics modules, resonance coils, restoration matrices) | Ardent Energy |
| Ardent Energy used for | Flares, the cannon purchase, Exchange goods | Everything, including beacon and cannon upgrades |
| Ardent drop rate | about 1 kill in 15 | about 1 kill in 12 (factor 1.3) |
| Exchange sells (you hand in) | Create factory surplus | Rare vanilla materials (diamond, blaze rod, ender pearl, ...) |
| Exchange buys (you pay energy) | Create supplies and ammo coins at a premium | Ammo coins and basic materials |

One source tree builds both (`/arsenal-build.properties` flavor in the jar). Every price is in `Economy`. Display furniture artwork is All Rights Reserved by its creator.

## 3. Mods in the package

* **Defense Beacon** (`arsenal_beacon`): the campaign, raids, support system, Weapon Platform, Exchange.
* **Gun Guide**: client HUD showing the real controls of the held gun, with the player's own key bindings, key-conflict warnings and a jam alert.
* **Gun Displays**: eleven stands, wall racks and glass cases; a trophy room counts toward base score.

## 4. Core loop

1. **Prepare** (preparation phase, daytime): build, craft, trade, upgrade, set the raid level.
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
* **Victory** restores everything the raid broke (damage journal). **Defeat** leaves it.
* Warnings: chat one day ahead, then every six hours; popups for raid start, each wave, VICTORY (confetti) and DEFEAT.

### 5.3 Special raid types (from the fourth raid on, 50 percent chance, also in boss raids)
| Type | What it asks of the base |
| --- | --- |
| **Air raid** | Vexes, phantoms, blazes ignore walls; build anti-air, not thicker walls |
| **Paratroopers** | Heavies at half count drop from 46 blocks up under red parachutes (slow fall, can be shot, no fall damage if not shot) |
| **Siege** | Ranged-heavy waves; creepers (30 percent) dig into walls, shooters fire through the holes |
| **They are thousands** | Only zombies and husks, double count, spawning every 8 ticks instead of 20, up to 96 alive; no heavy or ranged |

A won special raid adds **3 + 2 x tier** Ardent Energy. The next raid's type is shown at the end of each raid and in the Overview. Design intent: a type that rewards a counter-build (anti-air, thick walls, killing zones). Special raids start at raid 4 so support is affordable first.

### 5.4 Ardent Energy and the Exchange
* **Drops:** about 1 kill in 15 (1 in 12 standalone) for hostile mobs killed by a player or raid attackers; +1 percent per Looting level; mobs with 60+ health drop two; **bosses always drop one**. No credited player means no drop. Config in `arsenal-beacon-common.toml`: `baseDropChance`, `lootingBonusPerLevel`, `bossDrop`.
* **Raid income:** a guaranteed payout per won raid of **1 + t** (pack) or **2 + 2t** (standalone), plus drops (a tier 0 raid has about 20 attackers, tier 3 about 80), plus the special raid bonus.
* **Exchange Shop** (placed inside the zone; 3D vending booth): **Sell** tab (hand in materials, receive energy) and **Buy** tab (pay energy, receive goods). Buying always costs more than selling so loops never pay (a unit test checks the defaults). Offers live in `config/arsenal-beacon-exchange.txt` (`[sell]`, `[buy]`).
* Late-game factory players sell surplus for ammunition; players without a factory farm energy and buy parts at a premium.

### 5.5 Support system (per player)
Each player owns **one Support Platform** and **one Support Cannon**, placed anywhere inside the beacon zone, bought at the Weapon Platform's Workshop tab; relocated for free with the Beacon Recovery Shovel (keeps upgrades and contents). Only the owner can use them. They stop working if the beacon is gone.
* **Support Platform** Mk-1 to Mk-4 (grids 3x3 to 6x6; upgrade plating 16, 32, 64). Two blocks tall, owner sign on a post above it (render-only, no hitbox). The two blocks in front are a **no-build return zone** ("KEEP RETURN ZONE CLEAR").
* **Support Cannon** (3x3, heavy; brass owner plate). It turns with a grinding sound and only fires when settled on the target.
* **Flares** (throwing is free; buying costs energy): **Supply** 3, **Return** 2, **Fire Support** 6; cannon purchase 12.
  * *Supply Flare:* the cannon fires and a supply chest parachutes in with the platform's contents (12 s outside, 7 s underground, counted from the shot).
  * *Return Flare:* a portal (30 s; 5 min and usable twice with the upgrade). The player arrives at the front cell, a nearby safe cell, or the return is cancelled and the flare not consumed.
  * *Fire Support Flare:* a red box marks the target; the cannon shells it. A second flare mid-barrage waits ("Barrage in process").
* **Six fire support types** (the owner's choice applies even to a borrowed flare): Explosion Barrage (6 shells), Arrow Cluster (8), Narukami's Favor (lightning, 5), Bunker Buster (one bomb digging an 8-block-half-width cube; hurts players for half their health; refuses inside the zone; spares obsidian, crying obsidian, bedrock and anything with resistance 1,200 or more), Healing Barrage, Curse of Debilitation.
* **Ten cannon upgrades:** Traverse, Fire Rate, Volley, Damage, Quantum Tunneling (works underground), Slowness field, lasting two-way Portal, Healing Aura, Area of Effect, Dimensional Link. Standalone energy costs total about 185; the pack pays plating, coils, logistics modules and restoration matrices per `Economy.PACK_AMOUNTS`.

### 5.6 Weapon Platform
A workshop with four stations: **Weapons** (search, filter by type and era, craft), **Ammo** (matching ammo and magazines; pay in materials or Universal Ammo Coins), **Attachments** (compatible with the held gun, locked count), **Armor**. Five eras take the player from frontier rifles to futuristic hardware; modern pistols are a modern unlock. The Workshop tab sells the Support Platform and Exchange Shop.

### 5.7 Field guide and onboarding
New players get a starter weapon with ammo, a field guide (pack and standalone texts differ via `key.pack` / `key.standalone` suffixes) and the beacon tools. Every screen uses icons and a "How to unlock" line on locked entries. A **Reference** tab holds the exact rules. A test fails if the guide quotes a number that no longer matches the code.

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
* HUD: banner above the hotbar showing the chosen fire support while a Fire Support Flare is held; toasts for support calls.
* Zone: glowing grid and a red target box drawn as edges only (so nothing behind it is hidden). The outline's stale limit is 30 s (the cause of an earlier flicker bug is unproven).
* Audio: cannon grinding on turning; chat lines "Cannon preparing..." and "You heard cannon fire roaring" for far players. **(TBD: dedicated music and raid stingers.)**
* Art: hand-made 3D models (shop, platform Mk-1 to Mk-4, cannon, parcel) and painted 64x64 sprites; red parachute canopy drawn procedurally.

## 8. Technical notes for designers
* Network protocol **21** (messages 6 to 14); bump on any packet change.
* Server owns all rules; clients render. Campaign state in `CampaignData`; per-player support state in `SupportData`.
* Mob effects are not synced to clients, so special visuals (red parachute) use their own packet.
* Operator test commands: `/arsenal test-raid <type>`, `/arsenal next-raid <type>`.
* QA: 58 unit tests; real-client scripted QA in `tools/qa/`. See `docs/HANDOFF.md`.

## 9. Known risks and open questions
1. Zone outline flicker during cannon fire: fix is a best guess, unconfirmed.
2. Ground spawn for Air, Siege and Swarm raids not exercised in a real world.
3. Multiplayer, the End and modded dimensions untested; pack prices untested with real Create/KubeJS items.
4. Balance is theoretical until a team plays several raids.
5. Special-raid fairness: is a 50 percent chance too frequent? Does Air raid need a stronger anti-air toolkit than the player has? **(TBD, needs playtest data.)**

## 10. Roadmap candidates (not committed) **(TBD)**
* More anti-air tools for the air raid counter-build.
* Raid-type specific rewards or cosmetics.
* Music and raid audio.
* Difficulty presets for solo play.
* Balance pass from real playtest logs.
